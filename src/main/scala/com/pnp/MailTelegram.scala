package com.pnp

import canoe.api.*
import canoe.models.{Chat, Update}
import canoe.syntax.*
import cats.effect.{IO, Ref}
import com.pnp.MailInfo
import fs2.Stream
import logstage.LogIO

class MailTelegram(chats: Ref[IO, Map[String, List[MailInfo]]])
                  (using log: LogIO[IO], tc: TelegramClient[IO], imap: Imap, smtp: Smtp) {
  def stream: Stream[IO, Update] =
    Bot.polling[IO]
      .follow(sendMail, fetchUnseen, showMail)

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
      _ <- Scenario.eval(addMails(chats, chat.id.toString, mails))
      _ <- if (mails.isEmpty) Scenario.eval(chat.send("No mails..."))
      else Scenario.eval(chat.send(s"Unseen mail count: ${mails.size}"))
        >> showMail(chat, mails)
    } yield ()

  private def showMail[F[_] : TelegramClient](chat: Chat, messages: List[MailInfo]): Scenario[F, Unit] =
    for {
      _ <- messages match
        case Nil => Scenario.eval(chat.send("No more mails"))
        case head :: tail => Scenario.eval(chat.send(
          s"""Message from '${head.from}', to '${head.to}' with subject '${head.subject}'
             |/show_mail_${head.id}""".stripMargin))
          >> showMail(chat, tail)
    } yield ()

  private def showMail[F[_]](using c: TelegramClient[IO], l: LogIO[IO], imap: Imap): Scenario[IO, Unit] =
    for {
      textMessage <- Scenario.expect(command("show_mail"))
      _ <- Scenario.eval(LogIO[IO].info("show_mail"))
      mailOption <- Scenario.eval(getMail(chats, textMessage.chat.id.toString, getMailIndex(textMessage.text)))
      _ <- mailOption match {
        case Some(mail) => showMail(textMessage.chat, List(mail))
        case None => Scenario.eval(textMessage.chat.send("Mail not found"))
      }
    } yield ()

  private def addMails(ref: Ref[IO, Map[String, List[MailInfo]]], chatId: String, mailInfos: List[MailInfo]): IO[Unit] = {
    ref.update { chatMap =>
      val updatedMails = chatMap.getOrElse(chatId, List.empty) ++ mailInfos
      chatMap.updated(chatId, updatedMails)
    }
  }

  private def getMail(ref: Ref[IO, Map[String, List[MailInfo]]], chatId: String, mailIndex: Int): IO[Option[MailInfo]] = {
    ref.get.map { chatMap =>
      chatMap.get(chatId).flatMap(_.lift(mailIndex))
    }
  }

  private def getMailIndex(text: String): Int = {
    val lastIndex = text.lastIndexOf('_')
    text.substring(lastIndex + 1).toInt
  }
}
