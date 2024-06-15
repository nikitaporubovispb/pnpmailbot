package com.pnp

import canoe.api.*
import canoe.models.messages.TextMessage
import canoe.syntax.*
import cats.effect.{Async, IO}
import fs2.Stream
import logstage.LogIO

object Telegram {
  def run(using smtp: Smtp, config: BotConfig, log: LogIO[IO]): IO[Unit] = {
    Stream
      .resource(TelegramClient[IO](config.apiKey))
      .flatMap { case given TelegramClient[IO] => Bot.polling[IO].follow(sendMail) }
      .compile
      .drain
  }

  private def sendMail[F[_]](using c: TelegramClient[IO], l: LogIO[IO], smtp: Smtp): Scenario[IO, Unit] =
    for {
      _       <- Scenario.eval(LogIO[IO].info("Send mail scenario started."))
      chat    <- Scenario.expect(command("send_mail").chat)
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
}
