package com.pnp

import cats.effect.IO
import pureconfig.{ConfigReader, ConfigSource}
import pureconfig.generic.derivation.default.*

case class BotConfig(
  apiKey: String
)

case class SmtpConfig(
  host: String,
  port: Int,
  user: String,
  pass: String,
)

case class Config(
  bot: BotConfig,
  smtp: SmtpConfig,
) derives ConfigReader

object Config {
  def load: IO[Config] = IO {
    ConfigSource.default.loadOrThrow[Config]
  }
}
