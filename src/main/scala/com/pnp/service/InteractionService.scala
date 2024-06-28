package com.pnp.service

import cats.effect.IO
import cats.syntax.all.*
import com.pnp.dao.{MailConfigDao, UserDao}
import com.pnp.domain.{ConfigType, DbMailConfig, DbUser}
import com.pnp.service.InteractionService.RegisterResult
import com.pnp.service.InteractionService.RegisterResult.*

trait InteractionService {
  def isRegistered(telegramId: String): IO[Boolean]
  def register(telegramId: String, isExternalConfig: Boolean): IO[RegisterResult]
  def getUser(telegramId: String): IO[Option[DbUser]]
  def addMailConfig(mailConfig: DbMailConfig): IO[Unit]
  def getMailConfig(userId: Long, configType: ConfigType): IO[Option[DbMailConfig]]
}

class InteractionServiceImpl(userDao: UserDao, mailConfigDao: MailConfigDao) extends InteractionService {
  override def isRegistered(telegramId: String): IO[Boolean] =
    userDao.getUserByTelegramId(telegramId).map(_.isDefined)

  override def register(telegramId: String, isExternalConfig: Boolean): IO[RegisterResult] = {
    isRegistered(telegramId).ifM(
      IO.pure(AlreadyRegistered),
      userDao.createUser(telegramId, isExternalConfig).as(Ok)
    )
  }

  override def getUser(telegramId: String): IO[Option[DbUser]] = {
    userDao.getUserByTelegramId(telegramId)
  }

  override def addMailConfig(mailConfig: DbMailConfig): IO[Unit] = {
    mailConfigDao.createConfig(mailConfig)
  }

  override def getMailConfig(userId: Long, configType: ConfigType): IO[Option[DbMailConfig]] = {
    mailConfigDao.getConfig(userId, configType.id)
  }
}

object InteractionService {
  enum RegisterResult { case AlreadyRegistered, Ok }
  
  def from(userService: UserDao, mailConfigDao: MailConfigDao): IO[InteractionService] = IO {
    new InteractionServiceImpl(userService, mailConfigDao)
  }
}
