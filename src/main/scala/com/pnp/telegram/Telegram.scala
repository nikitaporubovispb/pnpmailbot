package com.pnp.telegram

import canoe.api.*
import cats.effect.*
import com.pnp.domain.*
import com.pnp.service.{InteractionService, MailRepositoryService}
import com.pnp.service.mail.{ImapService, SmtpService}
import com.pnp.telegram.MailTelegram
import com.pnp.utils.EncryptionUtils
import fs2.Stream
import logstage.LogIO

object Telegram {
  def run(using mailRepository: MailRepositoryService, smtp: SmtpService, imap: ImapService, config: Config, log: LogIO[IO], interaction: InteractionService, encryptionUtils: EncryptionUtils): IO[Unit] = {
    Stream
      .resource(TelegramClient[IO](config.bot.apiKey))
      .flatMap { case given TelegramClient[IO] =>
        new MailTelegram().stream
      }
      .compile
      .drain
  }
}
