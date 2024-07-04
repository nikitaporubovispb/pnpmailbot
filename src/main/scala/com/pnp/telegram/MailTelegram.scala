package com.pnp.telegram

import canoe.api.*
import canoe.models.{Chat, Update}
import canoe.syntax.*
import cats.effect.{IO, Ref}
import cats.syntax.all.*
import com.pnp.domain.{ConfigType, *}
import com.pnp.mail.{Imap, Smtp}
import com.pnp.service.InteractionService
import fs2.Stream
import jakarta.mail.Message
import logstage.LogIO

import scala.util.Try

class MailTelegram(chatsData: Ref[IO, Map[String, ChatData]], config: Config)
                  (using log: LogIO[IO], tc: TelegramClient[IO], imap: Imap, smtp: Smtp, interaction: InteractionService) {
  def stream: Stream[IO, Update] =
    Bot.polling[IO]
      .follow(start(), sendMail(), fetchUnseen(), showMailContent(), forwardMail(), replyMail())

  private def start(): Scenario[IO, Unit] =
    for {
      chat <- Scenario.expect(command("start").chat)
      _ <- Scenario.eval(LogIO[IO].info("Start  scenario started."))
      _ <- Scenario.eval(chat.send("/get_mails - fetch unseen mails"))
      _ <- Scenario.eval(chat.send("/send_mail - send mail"))
    } yield ()

  private def sendMail(): Scenario[IO, Unit] =
    for {
      chat <- Scenario.expect(command("send_mail").chat)
      _ <- Scenario.eval(LogIO[IO].info("Send mail scenario started."))
      user <- getUser(chat)
      _ <- Scenario.eval(chat.send("to?"))
      to <- Scenario.expect(text)
      _ <- Scenario.eval(chat.send("subject?"))
      subject <- Scenario.expect(text)
      _ <- Scenario.eval(chat.send("text?"))
      content <- Scenario.expect(text)
      smtpConfig <- Scenario.eval(getSmtpConfig(user))
      result <- Scenario.eval(smtp.sendMail(smtpConfig, smtpConfig.user, to, subject, content))
      _ <- result match {
        case Right(_) => Scenario.eval(chat.send("Successful sent!!!"))
        case Left(error) => handleError(chat, error.msg)
      }
    } yield ()

  private def fetchUnseen(): Scenario[IO, Unit] =
    for {
      chat <- Scenario.expect(command("get_mails").chat)
      user <- getUser(chat)
      _ <- Scenario.eval(LogIO[IO].info("Fetch unseen mail scenario started."))
      _ <- Scenario.eval(chat.send("Start fetching INBOX..."))
      imapConfig <- Scenario.eval(getImapConfig(user))
      mailInfos <- Scenario.eval(imap.getUnseenMailInboxInfos(imapConfig))
      _ <- mailInfos match
        case Right(mails) =>
          if (mails.isEmpty) Scenario.eval(chat.send("No mails..."))
          else Scenario.eval(
            addMails(chatsData, chat.id.toString, mails) >>
              chat.send(s"Unseen mail count: ${mails.size}")
          ) >> showMailInfos(chat, mails)
        case Left(error) => handleError(chat, error.msg)
    } yield ()

  private def showMailContent(): Scenario[IO, Unit] =
    for {
      textMessage <- Scenario.expect(command("show_mail"))
      _ <- Scenario.eval(LogIO[IO].info("show_mail"))
      mailOption <- Scenario.eval(getMail(chatsData, textMessage.chat.id.toString, getMailIndex(textMessage.text)))
      _ <- mailOption match {
        case Some(mailInfo) =>
          Scenario.eval(addLastMessage(chatsData, textMessage.chat.id.toString, mailInfo.message))
            >> sendMailContent(textMessage.chat, mailInfo.content.mkString("\n=====\n"))
        case None => Scenario.eval(textMessage.chat.send("Mail not found"))
      }
    } yield ()

  private def forwardMail(): Scenario[IO, Unit] =
    for {
      chat <- Scenario.expect(command("forward").chat)
      _ <- Scenario.eval(LogIO[IO].info("forward"))
      _ <- Scenario.eval(chat.send("To"))
      to <- Scenario.expect(text)
      _ <- Scenario.eval(chat.send("Text"))
      text <- Scenario.expect(text)
      lastMessage <- Scenario.eval(getLastMessage(chatsData, chat.id.toString))
      user <- getUser(chat)
      smtpConfig <- Scenario.eval(getSmtpConfig(user))
      result <- Scenario.eval(smtp.sendForward(smtpConfig, to, text, lastMessage.get))
      _ <- result match {
        case Right (_) => Scenario.eval(chat.send ("Successful sent!!!"))
        case Left(error) => handleError(chat, error.msg)
      }
    } yield ()

  private def replyMail(): Scenario[IO, Unit] =
    for {
      chat <- Scenario.expect(command("reply").chat)
      _ <- Scenario.eval(LogIO[IO].info("reply"))
      _ <- Scenario.eval(chat.send("To"))
      to <- Scenario.expect(text)
      _ <- Scenario.eval(chat.send("Text"))
      text <- Scenario.expect(text)
      lastMessage <- Scenario.eval(getLastMessage(chatsData, chat.id.toString))
      user <- getUser(chat)
      smtpConfig <- Scenario.eval(getSmtpConfig(user))
      result <- Scenario.eval(smtp.sendReply(smtpConfig, to, text, lastMessage.get))
      _ <- result match {
        case Right (_) => Scenario.eval (chat.send ("Successful sent!!!"))
        case Left(error) => handleError(chat, error.msg)
      }
    } yield ()

  private def configMail(): Scenario[IO, Unit] =
    for {
      chat <- Scenario.expect(command("config").chat)
      user <- Scenario.eval(interaction.getUser(chat.id.toString))
      _    <- configMail(chat, user.get)
    } yield ()

  private def configMail(chat: Chat, user: DbUser): Scenario[IO, Unit] =
    for {
      _ <- Scenario.eval(LogIO[IO].info("config"))
      _ <- Scenario.eval(chat.send("Imap config:"))
      _ <- Scenario.eval(chat.send("Imap host?"))
      imapHost <- Scenario.expect(text)
      _ <- Scenario.eval(chat.send("Imap port?"))
      imapPort <- Scenario.expect(text)
      _ <- Scenario.eval(chat.send("Imap user?"))
      imapUser <- Scenario.expect(text)
      _ <- Scenario.eval(chat.send("Imap pass?"))
      imapPass <- Scenario.expect(text)
      _ <- Scenario.eval(chat.send("Smtp config:"))
      _ <- Scenario.eval(chat.send("Smtp host?"))
      smtpHost <- Scenario.expect(text)
      _ <- Scenario.eval(chat.send("Smtp port?"))
      smtpPort <- Scenario.expect(text)
      _ <- Scenario.eval(chat.send("Smtp user?"))
      smtpUser <- Scenario.expect(text)
      _ <- Scenario.eval(chat.send("Smtp pass?"))
      smtpPass <- Scenario.expect(text)
      _ <- Scenario.eval(interaction.addMailConfig(DbMailConfig(-1L, user.id, ConfigType.Imap.id, imapHost, imapPort.toInt, imapUser, imapPass))
                      >> interaction.addMailConfig(DbMailConfig(-1L, user.id, ConfigType.Smtp.id, smtpHost, smtpPort.toInt, smtpUser, smtpPass)))
    } yield ()

  private def getUser(chat: Chat): Scenario[IO, Option[DbUser]] =
    for {
      userOpt <- Scenario.eval(interaction.getUser(chat.id.toString))
      user    <- userOpt match
        case None => addNewUser(chat)
        case u@Some(user) => Scenario.pure(u)
    } yield user

  private def addNewUser(chat: Chat): Scenario[IO, Option[DbUser]] =
    for {
      _ <- Scenario.eval(chat.send("New user!!!"))
      _ <- Scenario.eval(chat.send("Should you use shared mail config(type anything/'false') or own(type 'true')?"))
      isExternalConfigText <- Scenario.expect(text)
      isExternalConfig = Try(isExternalConfigText.toBoolean).fold(th => false, b => b)
      _ <- Scenario.eval(interaction.register(chat.id.toString, isExternalConfig))
      userOpt <- Scenario.eval(interaction.getUser(chat.id.toString))
      - <- userOpt match
        case Some(user) if isExternalConfig => configMail(chat, user)
        case _ => Scenario.done
    } yield userOpt

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
        if (content.length <= 4096) { Scenario.eval(chat.send(content)) >> Scenario.eval(chat.send("/forward | /reply")) }
        else Scenario.eval(chat.send(content.substring(0, 4096))) >> sendMailContent(chat, content.substring(4096))
    } yield ()

  private def handleError(chat: Chat, errorMsg: String): Scenario[IO, Unit] =
    for {
      _ <- Scenario.eval(LogIO[IO].error(s"Chat id = ${chat.id}, error =$errorMsg"))
      _ <- Scenario.eval(chat.send(s"Error=( $errorMsg"))
    } yield ()

  private def updateField[A](ref: Ref[IO, Map[String, ChatData]], chatId: String, updateFunc: ChatData => ChatData): IO[Unit] = {
    ref.update { chatMap =>
      val chatData = chatMap.getOrElse(chatId, ChatData(Nil, None))
      chatMap.updated(chatId, updateFunc(chatData))
    }
  }

  private def addMails(ref: Ref[IO, Map[String, ChatData]], chatId: String, mailInfos: List[MailInfo]): IO[Unit] = {
    updateField(ref, chatId, chatData => chatData.copy(fetchedMailInfos = mailInfos))
  }

  private def addLastMessage(ref: Ref[IO, Map[String, ChatData]], chatId: String, message: Message): IO[Unit] = {
    updateField(ref, chatId, chatData => chatData.copy(lastMessage = Some(message)))
  }

  private def getField[A](ref: Ref[IO, Map[String, ChatData]], chatId: String, extractField: ChatData => Option[A]): IO[Option[A]] = {
    ref.get.map { chatMap =>
      chatMap.get(chatId).flatMap(extractField)
    }
  }

  private def getMail(ref: Ref[IO, Map[String, ChatData]], chatId: String, mailIndex: Int): IO[Option[MailInfo]] = {
    getField(ref, chatId, _.fetchedMailInfos.lift(mailIndex))
  }

  private def getLastMessage(ref: Ref[IO, Map[String, ChatData]], chatId: String): IO[Option[Message]] = {
    getField(ref, chatId, _.lastMessage)
  }

  private def getMailIndex(text: String): Int = {
    val lastIndex = text.lastIndexOf('_')
    text.substring(lastIndex + 1).toInt
  }

  private def getSmtpConfig(userOpt: Option[DbUser]): IO[SmtpConfig] = {
      userOpt match
        case Some(DbUser(userId, _, _)) => interaction.getMailConfig(userId, ConfigType.Smtp)
          .map { dbConfigOpt => dbConfigOpt.fold(config.smtp)(dbSmtp => SmtpConfig(dbSmtp.host, dbSmtp.port, dbSmtp.user, dbSmtp.password)) }
        case None => IO(config.smtp)
    }

  private def getImapConfig(userOpt: Option[DbUser]): IO[ImapConfig] = {
    userOpt match
      case Some(DbUser(userId, _, _)) => interaction.getMailConfig(userId, ConfigType.Imap)
        .map { dbConfigOpt => dbConfigOpt.fold(config.imap)(dbImap => ImapConfig(dbImap.host, dbImap.port, dbImap.user, dbImap.password)) }
      case None => IO(config.imap)
  }
}
