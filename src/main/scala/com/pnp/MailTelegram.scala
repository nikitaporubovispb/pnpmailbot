package com.pnp

import canoe.api.*
import canoe.models.{Chat, Update}
import canoe.syntax.*
import cats.effect.{IO, Ref}
import com.pnp.domain.{ChatData, MailInfo}
import fs2.Stream
import jakarta.mail.Message
import logstage.LogIO

class MailTelegram(chatsData: Ref[IO, Map[String, ChatData]])
                  (using log: LogIO[IO], tc: TelegramClient[IO], imap: Imap, smtp: Smtp) {
  def stream: Stream[IO, Update] =
    Bot.polling[IO]
      .follow(sendMail, fetchUnseen, showMailContent, forwardMail)

  private def sendMail[F[_]](using c: TelegramClient[IO], l: LogIO[IO], smtp: Smtp): Scenario[IO, Unit] =
    for {
      chat <- Scenario.expect(command("send_mail").chat)
      _ <- Scenario.eval(LogIO[IO].info("Send mail scenario started."))
      _ <- Scenario.eval(chat.send("from?"))
      from <- Scenario.expect(text)
      _ <- Scenario.eval(chat.send("to?"))
      to <- Scenario.expect(text)
      _ <- Scenario.eval(chat.send("subject?"))
      subject <- Scenario.expect(text)
      _ <- Scenario.eval(chat.send("text?"))
      content <- Scenario.expect(text)
      result <- Scenario.eval(smtp.sendMail(from, to, subject, content))
      _ <- result match {
        case Left(value) => Scenario.eval(chat.send(s"Error =( $value"))
        case Right(_) => Scenario.eval(chat.send("Successful sent!!!"))
      }
    } yield ()

  private def fetchUnseen[F[_]](using c: TelegramClient[IO], l: LogIO[IO], imap: Imap): Scenario[IO, Unit] =
    for {
      chat <- Scenario.expect(command("get_mails").chat)
      _ <- Scenario.eval(LogIO[IO].info("Fetch unseen mail scenario started."))
      _ <- Scenario.eval(chat.send("Start fetching INBOX..."))
      mails <- Scenario.eval(imap.getUnseenMailInboxInfos)
      _ <- Scenario.eval(addMails(chatsData, chat.id.toString, mails))
      _ <-
        if (mails.isEmpty) Scenario.eval(chat.send("No mails..."))
        else Scenario.eval(chat.send(s"Unseen mail count: ${mails.size}"))
          >> showMailInfos(chat, mails)
    } yield ()

  private def showMailContent[F[_]](using c: TelegramClient[IO], l: LogIO[IO], imap: Imap): Scenario[IO, Unit] =
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

  private def forwardMail[F[_]](using c: TelegramClient[IO], l: LogIO[IO], imap: Imap): Scenario[IO, Unit] =
    for {
      chat <- Scenario.expect(command("forward").chat)
      _ <- Scenario.eval(LogIO[IO].info("forward"))
      _ <- Scenario.eval(chat.send("To"))
      to <- Scenario.expect(text)
      _ <- Scenario.eval(chat.send("Text"))
      text <- Scenario.expect(text)
      lastMessage <- Scenario.eval(getLastMessage(chatsData, chat.id.toString))
      result <- Scenario.eval(smtp.sendForward(to, text, lastMessage.get))
      _ <- result match {
        case Left (value) => Scenario.eval (chat.send (s"Error =( $value"))
        case Right (_) => Scenario.eval (chat.send ("Successful sent!!!"))
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
        if (content.length <= 4096) { Scenario.eval(chat.send(content)) >> Scenario.eval(chat.send("/forward | /reply")) }
        else Scenario.eval(chat.send(content.substring(0, 4096))) >> sendMailContent(chat, content.substring(4096))
    } yield ()

  private def addMails(ref: Ref[IO, Map[String, ChatData]], chatId: String, mailInfos: List[MailInfo]): IO[Unit] = {
    ref.update { chatMap =>
      val chatData = chatMap.getOrElse(chatId, ChatData(null, null))
      chatMap.updated(chatId, chatData.copy(fetchedMailInfos = mailInfos))
    }
  }

  private def addLastMessage(ref: Ref[IO, Map[String, ChatData]], chatId: String, message: Message): IO[Unit] = {
    ref.update { chatMap =>
      val chatData = chatMap.getOrElse(chatId, ChatData(null, null))
      chatMap.updated(chatId, chatData.copy(lastMessage = Some(message)))
    }
  }

  private def getMail(ref: Ref[IO, Map[String, ChatData]], chatId: String, mailIndex: Int): IO[Option[MailInfo]] = {
    ref.get.map { chatMap =>
      chatMap.get(chatId).flatMap(_.fetchedMailInfos.lift(mailIndex))
    }
  }

  private def getLastMessage(ref: Ref[IO, Map[String, ChatData]], chatId: String): IO[Option[Message]] = {
    ref.get.map { chatMap =>
      chatMap.get(chatId).flatMap(_.lastMessage)
    }
  }

  private def getMailIndex(text: String): Int = {
    val lastIndex = text.lastIndexOf('_')
    text.substring(lastIndex + 1).toInt
  }
}
