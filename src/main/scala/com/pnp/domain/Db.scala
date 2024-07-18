package com.pnp.domain

case class DbUser(id: Long, telegramId: String, isExternalConfig: Boolean)
case class DbMailConfig(id: Option[Long], userId: Long, configType: Int, host: String, port: Int, user: String, password: String)