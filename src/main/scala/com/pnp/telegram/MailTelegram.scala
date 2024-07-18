package com.pnp.telegram

import canoe.api.*
import canoe.models.messages.TextMessage
import canoe.models.{Chat, Update}
import canoe.syntax.*
import cats.data.*
import cats.effect.IO
import cats.syntax.all.*
import com.pnp.domain.*
import com.pnp.domain.MailConfig.*
import com.pnp.service.{InteractionService, MailRepositoryService}
import com.pnp.service.mail.*
import com.pnp.telegram.MailTelegram.{mailServerUrl, userMailName}
import com.pnp.utils.EncryptionUtils
import fs2.Stream
import jakarta.mail.Message
import logstage.LogIO

import scala.util.Try
import scala.util.matching.Regex

class MailTelegram(using config: Config, log: LogIO[IO], tc: TelegramClient[IO], mailRepository: MailRepositoryService, imap: ImapService,
                   smtp: SmtpService, interaction: InteractionService, cipher: EncryptionUtils) {
  def stream: Stream[IO, Update] =
    Bot.polling[IO]
      .follow(start(), configMail(), sendMail(), fetchUnseen(), setMessageAsSeen(), showMailContent(), forwardMail(), replyMail())

  private def start(): Scenario[IO, Unit] =
    for {
      chat <- Scenario.expect(command("start").chat)
      _ <- Scenario.eval(log.info("Start scenario started."))
      _ <- checkUser(chat)
      _ <- Scenario.eval(chat.send("/get_mails - fetch unseen mails"))
      _ <- Scenario.eval(chat.send("/send_mail - send mail"))
      _ <- Scenario.eval(chat.send("/config - config mail server"))
    } yield ()

  private def fetchUnseen(): Scenario[IO, Unit] =
    for {
      chat <- Scenario.expect(command("get_mails").chat)
      _ <- Scenario.eval(log.info("Fetch unseen mail scenario started."))
      _ <- doWithUser(chat) { user =>
        for {
          _ <- Scenario.eval(chat.send("Start fetching INBOX..."))
          imapConfig <- Scenario.eval(getImapConfig(user))
          _ <- showMailInfos(chat, imap.getUnseenMailInboxInfos(imapConfig))
        } yield ()
      }
    } yield ()

  private def setMessageAsSeen(): Scenario[IO, Unit] =
    for {
      chat <- Scenario.expect(command("as_seen").chat)
      _ <- Scenario.eval(log.info("as_seen"))
      _ <- doWithUser(chat) { user =>
        for {
          imapConfig <- Scenario.eval(getImapConfig(user))
          lastMessageOpt <- Scenario.eval(mailRepository.getLastMessage(chat.id.toString))
          _ <- lastMessageOpt match {
            case Some(lastMessage) => send(chat, imap.setMessageSeen(imapConfig, lastMessage), "Set seen successful!!")
            case None => handleError(chat, "No last message")
          }
        } yield ()
      }
    } yield ()


  private def showMailInfos(chat: Chat, f: IO[List[MailInfo]]): Scenario[IO, Unit] = {
    val result: Scenario[IO, Unit] = for {
      mails <- Scenario.eval(f)
      _ <- if (mails.isEmpty) Scenario.eval(chat.send("No mails..."))
      else Scenario.eval(mailRepository.addMails(chat.id.toString, mails) >>
        chat.send(s"Unseen mail count: ${mails.size}")) >>
        showMailInfos(chat, mails)
    } yield ()
    result.handleErrorWith(th => handleError(chat, th.getMessage))
  }

  private def showMailContent(): Scenario[IO, Unit] =
    for {
      textMessage <- Scenario.expect(command("show_mail"))
      _ <- Scenario.eval(log.info("show_mail"))
      _ <- doWithUser(textMessage.chat) { _ =>
        for {
          mailOption <- Scenario.eval((for {
            index <- OptionT.fromOption[IO](getMailIndex(textMessage.text))
            mail <- OptionT(mailRepository.getMail(textMessage.chat.id.toString, index))
          } yield mail).value)
          _ <- mailOption match {
            case Some(mailInfo) =>
              Scenario.eval(mailRepository.addLastMessage(textMessage.chat.id.toString, mailInfo.message))
                >> sendMailContent(textMessage.chat, mailInfo.content.mkString("\n=====\n"))
            case None => Scenario.eval(textMessage.chat.send("Mail not found"))
          }
        } yield ()
      }
    } yield ()

  private def sendMail(): Scenario[IO, Unit] =
    for {
      chat <- Scenario.expect(command("send_mail").chat)
      _ <- Scenario.eval(log.info("Send mail scenario started."))
      _ <- doWithUser(chat) { user =>
        for {
          _ <- Scenario.eval(chat.send("to?"))
          to <- Scenario.expect(text)
          _ <- Scenario.eval(chat.send("subject?"))
          subject <- Scenario.expect(text)
          _ <- Scenario.eval(chat.send("text?"))
          content <- Scenario.expect(text)
          smtpConfig <- Scenario.eval(getSmtpConfig(user))
          _ <- send(chat, smtp.sendMail(smtpConfig, smtpConfig.user, to, subject, content))
        } yield ()
      }
    } yield ()

  private def forwardMail(): Scenario[IO, Unit] =
    for {
      chat <- Scenario.expect(command("forward").chat)
      _ <- Scenario.eval(log.info("forward"))
      _ <- doWithUser(chat) { user =>
        for {
          lastMessageOpt <- Scenario.eval(mailRepository.getLastMessage(chat.id.toString))
          _ <- lastMessageOpt match {
            case Some(lastMessage) => for {
              _ <- Scenario.eval(chat.send("To"))
              to <- Scenario.expect(text)
              _ <- Scenario.eval(chat.send("Text"))
              text <- Scenario.expect(text)
              smtpConfig <- Scenario.eval(getSmtpConfig(user))
              _ <- send(chat, smtp.sendForward(smtpConfig, to, text, lastMessage))
            } yield ()
            case None => handleError(chat, "No last message")
          }
        } yield ()
      }
    } yield ()

  private def replyMail(): Scenario[IO, Unit] =
    for {
      chat <- Scenario.expect(command("reply").chat)
      _ <- Scenario.eval(log.info("reply"))
      _ <- doWithUser(chat) { user =>
        for {
          lastMessageOpt <- Scenario.eval(mailRepository.getLastMessage(chat.id.toString))
          _ <- lastMessageOpt match {
            case Some(lastMessage) => for {
              _ <- Scenario.eval(chat.send("To"))
              to <- Scenario.expect(text)
              _ <- Scenario.eval(chat.send("Text"))
              text <- Scenario.expect(text)
              smtpConfig <- Scenario.eval(getSmtpConfig(user))
              _ <- send(chat, smtp.sendReply(smtpConfig, to, text, lastMessage))
            } yield ()
            case None => handleError(chat, "No last message")
          }
        } yield ()
      }
    } yield ()

  private def send(chat: Chat, f: IO[Unit], text: String = "Successful sent!!!"): Scenario[IO, Unit] = {
    val result: Scenario[IO, Unit] = for {
      _ <- Scenario.eval(f)
      _ <- Scenario.eval(chat.send(text))
    } yield ()
    result.handleErrorWith(th => handleError(chat, th.getMessage))
  }

  private def configMail(): Scenario[IO, Unit] =
    for {
      chat <- Scenario.expect(command("config").chat)
      _ <- doWithUser(chat) { user =>
        for {
          - <- if (user.isExternalConfig) {
            for {
              _ <- Scenario.eval(chat.send("Disable external config? Type anything/'false' for No or type +/'true' for Yes"))
              isDisable <- getUserYesOrNo
              - <- if (isDisable) {
                Scenario.eval(interaction.updateUser(user.id, false, None)) >> Scenario.eval(chat.send("Update successful!!!"))
              } else {
                configMailFull(chat).flatMap {
                  case configs@Some(imapConfig, smtpConfig) => Scenario.eval(interaction.updateUser(user.id, true, configs))
                    >> Scenario.eval(chat.send("Update successful!!!"))
                  case None => Scenario.eval(chat.send("No config entered"))
                }
              }
            } yield ()
          } else {
            for {
              _ <- Scenario.eval (chat.send ("Enable external config? Type anything/'false' for No or type +/'true' for Yes"))
              isEnable <- getUserYesOrNo
              - <- if (isEnable) {
                configMailFull(chat).flatMap {
                  case configs@Some(_) => Scenario.eval(interaction.updateUser(user.id, true, configs))
                    >> Scenario.eval(chat.send("Update successful!!!"))
                  case None => Scenario.eval(chat.send("No config entered"))
                }
              } else {
                Scenario.eval(interaction.updateUser(user.id, false, None)) >> Scenario.eval(chat.send("Update successful!!!"))
              }
            } yield()
          }
        } yield ()
      }
    } yield ()

  private def configMailFull(chat: Chat): Scenario[IO, Option[(MailConfig, MailConfig)]] =
    (for {
      _ <- Scenario.eval(log.info("configMailFull"))
      _ <- Scenario.eval(chat.send("Imap config:"))
      (imapHost, _) <- makeParsedValue(using chat, TitleAndError("Imap host?", "Expected valid url"), { textValue => Option.when(mailServerUrl.matches(textValue))(textValue) })
      (imapPort, _) <- makeParsedValue(using chat, TitleAndError("Imap port?", "Expected positive integer value"), { textValue => Try(textValue.toInt).toOption.filter(_ > 0) })
      (imapUser, _) <- makeParsedValue(using chat, TitleAndError("Imap user?", "Expected user name"), { textValue => Option.when(userMailName.matches(textValue))(textValue) })
      (imapPass, imapPassMes) <- makeParsedValue(using chat, TitleAndError("Imap pass? After you send the message it will be deleted", ""), { Some(_) })
      _ <- Scenario.eval(imapPassMes.delete) >> Scenario.eval(chat.send("Your password is stored."))
      _ <- Scenario.eval(chat.send("Smtp config:"))
      (smtpHost, _) <- makeParsedValue(using chat, TitleAndError("Smtp host", "Expected valid url"), { textValue => Option.when(mailServerUrl.matches(textValue))(textValue) })
      (smtpPort, _) <- makeParsedValue(using chat, TitleAndError("Smtp port?", "Expected positive integer value"),  { textValue => Try(textValue.toInt).toOption.filter(_ > 0) })
      (smtpUser, _) <- makeParsedValue(using chat, TitleAndError("Smtp user?", "Expected user name"), { textValue => Option.when(userMailName.matches(textValue))(textValue) })
      (smtpPass, smtpPassMes) <- makeParsedValue(using chat, TitleAndError("Smtp pass? After you send the message it will be deleted", ""), { Some(_) })
      _ <- Scenario.eval(smtpPassMes.delete) >> Scenario.eval(chat.send("Your password is stored."))
      imapConfig <- Scenario.pure(ImapMailConfig(imapHost, imapPort, imapUser, cipher.encrypt(imapPass.text)))
      smtpConfig <- Scenario.pure(SmtpMailConfig(smtpHost, smtpPort, smtpUser, cipher.encrypt(smtpPass.text)))
    } yield Some(imapConfig, smtpConfig)).handleErrorWith { th => Scenario.eval(chat.send(th.getMessage)) >> Scenario.pure(None) }

  private def configMailPartial(chat: Chat, user: DbUser): Scenario[IO, Unit] =
    (for {
      _ <- Scenario.eval(log.info("configMailPartial"))
      _ <- Scenario.eval(chat.send("Imap config:"))
      _ <- Scenario.eval(chat.send("Config saved!!!"))
    } yield ()).handleErrorWith { th => Scenario.eval(chat.send(th.getMessage)) >> Scenario.done }

  private def checkUser(chat: Chat): Scenario[IO, Unit] =
    for {
      userOpt <- Scenario.eval(interaction.getUser(chat.id.toString))
      _    <- userOpt match
        case None => addNewUser(chat)
        case _ => Scenario.done
    } yield ()

  private def doWithUser[A](chat: Chat)(f: DbUser => Scenario[IO, A]): Scenario[IO, Unit] =
    for {
      user <- getUser(chat).attempt
      _ <- user match {
        case Left(error) => handleError(chat, error.getMessage)
        case Right(userOpt) => f(userOpt.get)
      }
    } yield ()

  private def getUser(chat: Chat): Scenario[IO, Option[DbUser]] =
    for {
      userOpt <- Scenario.eval(interaction.getUser(chat.id.toString))
      user    <- userOpt match
        case None => Scenario.eval(chat.send("I do not know you! Do /start again"))
                      >> Scenario.raiseError(RuntimeException("No user"))
        case u@Some(user) => Scenario.pure(u)
    } yield user

  private def addNewUser(chat: Chat): Scenario[IO, Unit] =
    for {
      _ <- Scenario.eval(chat.send("New user!!!"))
      _ <- Scenario.eval(chat.send("Should you use shared mail config(type anything/'false') or external(type +/'true')?"))
      isExternalConfig <- getUserYesOrNo
      - <- if (isExternalConfig) {
        configMailFull(chat).flatMap {
          case configs@Some(imapConfig, smtpConfig) =>
            Scenario.eval(interaction.createUser(chat.id.toString, true, configs))
              >> Scenario.eval(chat.send("Create successful!!!"))
          case None => Scenario.eval(chat.send("No config entered"))
        }
      } else {
        Scenario.eval(interaction.createUser(chat.id.toString, false, None))
          >> Scenario.eval(chat.send("Create successful!!!"))
      }
    } yield ()

  private def showMailInfos[F[_] : TelegramClient](chat: Chat, messages: List[MailInfo]): Scenario[F, Unit] =
    for {
      _ <- messages match
        case Nil => Scenario.eval(chat.send("No more mails"))
        case head :: tail => Scenario.eval(chat.send(
          s"""Message from '${head.from}', to '${head.to}' with subject '${head.subject}'
             |/show_mail_${head.id}""".stripMargin))
          >> showMailInfos(chat, tail)
    } yield ()

  private def sendMailContent[F[_] : TelegramClient](chat: Chat, content: String): Scenario[F, Unit] =
    for {
      _ <-
        if (content.length <= 4096) { Scenario.eval(chat.send(content)) >> Scenario.eval(chat.send("/as_seen | /forward | /reply")) }
        else Scenario.eval(chat.send(content.substring(0, 4096))) >> sendMailContent(chat, content.substring(4096))
    } yield ()

  private def handleError(chat: Chat, errorMsg: String): Scenario[IO, Unit] =
    for {
      _ <- Scenario.eval(log.error(s"Chat id = ${chat.id}, error = $errorMsg"))
      _ <- Scenario.eval(chat.send(s"Error=( $errorMsg"))
    } yield ()

  case class TitleAndError(title: String, error: String)

  private def makeParsedValue[A](using chat: Chat, strings: TitleAndError, f: String => Option[A]): Scenario[IO, (A, TextMessage)] = for {
    _ <- Scenario.eval(chat.send(strings.title + " [type '...' - to cancel]"))
    message <- Scenario.expect(textMessage)
    value <- message.text match {
      case "..." => Scenario.raiseError(RuntimeException("Canceled by user"))
      case _ => f(message.text) match {
        case Some(v) => Scenario.pure(v, message)
        case None => Scenario.eval(chat.send(strings.error))
          >> makeParsedValue
      }
    }
  } yield value

  private def getUserYesOrNo: Scenario[IO, Boolean] =
    for {
      booleanText <- Scenario.expect(text)
      b = Try(booleanText match
        case "+" => true
        case _ => booleanText.toBoolean
      ).fold(th => false, b => b)
    } yield b

  private def getMailIndex(text: String): Option[Int] = {
    val lastIndex = text.lastIndexOf('_')
    Try(text.substring(lastIndex + 1).toInt).toOption
  }

  private def getSmtpConfig(user: DbUser): IO[SmtpConfig] =
      interaction.getMailSmtpConfig(user.id)
        .map { dbConfigOpt => dbConfigOpt.fold(config.smtp)(dbSmtp => SmtpConfig(dbSmtp.host, dbSmtp.port, dbSmtp.user, cipher.decrypt(dbSmtp.password))) }

  private def getImapConfig(user: DbUser): IO[ImapConfig] =
      interaction.getMailImapConfig(user.id)
          .map { dbConfigOpt => dbConfigOpt.fold(config.imap)(dbImap => ImapConfig(dbImap.host, dbImap.port, dbImap.user, cipher.decrypt(dbImap.password))) }
}

object MailTelegram {
  val mailServerUrl: Regex = "^([a-zA-Z0-9-]+\\.)*([a-zA-Z0-9-]+)+\\.[a-zA-Z]+$".r
  val userMailName: Regex = "^[a-zA-Z0-9._%+-]+@([a-zA-Z0-9-]+\\.)*([a-zA-Z0-9-]+)+\\.[a-zA-Z]+$".r
}
