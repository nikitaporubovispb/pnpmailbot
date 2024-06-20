package com.pnp

import canoe.api.*
import canoe.models.*
import canoe.models.messages.TextMessage
import canoe.syntax.*
import cats.effect.{Async, IO}
import fs2.Stream
import logstage.LogIO

object Telegram {
  def run(using smtp: Smtp, imap: Imap, config: BotConfig, log: LogIO[IO]): IO[Unit] = {
    Stream
      .resource(TelegramClient[IO](config.apiKey))
      .flatMap { case given TelegramClient[IO] => Bot.polling[IO].follow(sendMail, fetchUnseen) }
      .compile
      .drain
  }

  private def sendMail[F[_]](using c: TelegramClient[IO], l: LogIO[IO], smtp: Smtp): Scenario[IO, Unit] =
    for {
      chat    <- Scenario.expect(command("send_mail").chat)
      _       <- Scenario.eval(LogIO[IO].info("Send mail scenario started."))
      _       <- Scenario.eval(chat.send("from?"))
      from    <- Scenario.expect(text)
      _       <- Scenario.eval(chat.send("to?"))
      to      <- Scenario.expect(text)
      _       <- Scenario.eval(chat.send("subject?"))
      subject <- Scenario.expect(text)
      _       <- Scenario.eval(chat.send("text?"))
      content <- Scenario.expect(text)
      result  <- Scenario.eval(smtp.sendMail(from, to, subject, content))
      _       <- result match {
        case Left(value) => Scenario.eval(chat.send(s"Error =( $value"))
        case Right(_) => Scenario.eval(chat.send("Successful sent!!!"))
      }
    } yield ()

  private def fetchUnseen[F[_]](using c: TelegramClient[IO], l: LogIO[IO], imap: Imap): Scenario[IO, Unit] =
    for {
      chat <- Scenario.expect(command("fetch_mail").chat)
      _ <- Scenario.eval(LogIO[IO].info("Fetch unseen mail scenario started."))
      _ <- Scenario.eval(chat.send("Start fetching INBOX..."))
      mails <- Scenario.eval(imap.getUnseenMailInboxInfos)
      _ <- if (mails.isEmpty) Scenario.eval(chat.send("No mails..."))
      else Scenario.eval(chat.send(s"Unseen mail count: ${mails.size}")) >> showMail(chat, mails)
    } yield ()

  private def showMail[F[_] : TelegramClient](chat: Chat, messages: List[MailInfo]): Scenario[F, Unit] =
    for {
      _ <- messages match
        case Nil => Scenario.eval(chat.send("No more mails"))
        case head :: tail => Scenario.eval(chat.send(s"Message from '${head.from}', to '${head.to}' with subject '${head.subject}'")) >> showMail(chat, tail)
    } yield ()
}
