package com.pnp

import canoe.api.*
import canoe.models.messages.TextMessage
import canoe.syntax.*
import cats.effect.{Async, IO}
import fs2.Stream
import logstage.LogIO

object Telegram {
  def run(using config: Config, log: LogIO[IO]): IO[Unit] = {
    Stream
      .resource(TelegramClient[IO](config.bot.apiKey))
      .flatMap { case given TelegramClient[IO] => Bot.polling[IO].follow(hi[IO]) }
      .compile
      .drain
  }

  private def hi[F[_] : TelegramClient: LogIO]: Scenario[F, Unit] =
    for {
      _     <- Scenario.eval(LogIO[F].info("Greetings scenario started."))
      chat  <- Scenario.expect(command("hi").chat)
      _     <- Scenario.eval(chat.send("Hello. What's your name?"))
      name  <- Scenario.expect(text)
      _     <- Scenario.eval(chat.send(s"Nice to meet you, $name"))
    } yield ()
}
