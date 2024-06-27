package com.pnp.domain

import cats.effect.IO
import com.pnp.*
import pureconfig.generic.derivation.default.*
import pureconfig.{ConfigReader, ConfigSource}

case class BotConfig(
  apiKey: String
)

case class SmtpConfig(
  host: String,
  port: Int,
  user: String,
  pass: String,
)

case class ImapConfig(
 host: String,
 port: Int,
 user: String,
 pass: String,
)

case class DbConfig(
 host: String,
 port: Int,
 database: String,
 user: String,
 password: String
)

case class Config(
  bot: BotConfig,
  smtp: SmtpConfig,
  imap: ImapConfig,
  database: DbConfig,
) derives ConfigReader

object Config {
  def load: IO[Config] = IO {
    ConfigSource.default.loadOrThrow[Config]
  }
}
