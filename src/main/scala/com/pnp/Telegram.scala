package com.pnp

import canoe.api.*
import cats.effect.*
import fs2.Stream
import logstage.LogIO

object Telegram {
  def run(using smtp: Smtp, imap: Imap, config: BotConfig, log: LogIO[IO]): IO[Unit] = {
    Stream
      .resource(TelegramClient[IO](config.apiKey))
      .flatMap { case given TelegramClient[IO] =>
        Stream.eval(Ref.of[IO, Map[String, List[MailInfo]]](Map.empty))
          .flatMap(ref => new MailTelegram(ref).stream)
      }
      .compile
      .drain
  }
}
