package com.pnp.telegram

import canoe.api.*
import cats.effect.*
import com.pnp.domain.{BotConfig, ChatData}
import com.pnp.mail.{Imap, Smtp}
import com.pnp.service.InteractionService
import com.pnp.telegram.MailTelegram
import fs2.Stream
import logstage.LogIO

object Telegram {
  def run(using smtp: Smtp, imap: Imap, config: BotConfig, log: LogIO[IO], interaction: InteractionService): IO[Unit] = {
    Stream
      .resource(TelegramClient[IO](config.apiKey))
      .flatMap { case given TelegramClient[IO] =>
        Stream.eval(Ref.of[IO, Map[String, ChatData]](Map.empty))
          .flatMap(ref => new MailTelegram(ref).stream)
      }
      .compile
      .drain
  }
}
