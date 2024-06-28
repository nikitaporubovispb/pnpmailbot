package com.pnp.domain

case class DbUser(id: Long, telegramId: String, isExternalConfig: Boolean)
case class DbMailConfig(id: Long, userId: Long, configType: Int, host: String, port: Int, user: String, password: String)
enum ConfigType(val id: Int) {
    case Imap extends ConfigType(1)
    case Smtp extends ConfigType(2)
  }
