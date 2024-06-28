package com.pnp.telegram

import canoe.api.*
import cats.effect.*
import com.pnp.domain.*
import com.pnp.mail.{Imap, Smtp}
import com.pnp.service.InteractionService
import com.pnp.telegram.MailTelegram
import fs2.Stream
import logstage.LogIO

object Telegram {
  def run(using smtp: Smtp, imap: Imap, config: Config, log: LogIO[IO], interaction: InteractionService): IO[Unit] = {
    Stream
      .resource(TelegramClient[IO](config.bot.apiKey))
      .flatMap { case given TelegramClient[IO] =>
        Stream.eval(Ref.of[IO, Map[String, ChatData]](Map.empty))
          .flatMap(ref => new MailTelegram(ref, config).stream)
      }
      .compile
      .drain
  }
}
