package com.pnp.service

import cats.effect.IO
import com.pnp.dao.MailConfigDao.ConfigType
import com.pnp.dao.{MailConfigDao, UserDao}
import com.pnp.domain.MailConfig.*
import com.pnp.domain.{MailConfig, *}


trait InteractionService {
  def createUser(telegramId: String, isExternalConfig: Boolean, mailConfigs: Option[(MailConfig, MailConfig)]): IO[Long]
  def updateUser(id: Long, isExternalConfig: Boolean, mailConfigs: Option[(MailConfig, MailConfig)]): IO[Unit]
  def getUser(telegramId: String): IO[Option[DbUser]]
  def getMailImapConfig(userId: Long): IO[Option[ImapMailConfig]]
  def getMailSmtpConfig(userId: Long): IO[Option[SmtpMailConfig]]
}

class InteractionServiceImpl(userDao: UserDao, mailConfigDao: MailConfigDao) extends InteractionService {
  override def createUser(telegramId: String, isExternalConfig: Boolean, mailConfigs: Option[(MailConfig, MailConfig)]): IO[Long] =
    for {
      userId <- userDao.createUser(telegramId, isExternalConfig)
      _ <- if (isExternalConfig && mailConfigs.isDefined) {
        mailConfigDao.createConfig(createDbMailConfig(userId, mailConfigs.get._1))
          >> mailConfigDao.createConfig(createDbMailConfig(userId, mailConfigs.get._2))
      } else {
        IO.none
      }
    } yield userId

  override def updateUser(userId: Long, isExternalConfig: Boolean, mailConfigs: Option[(MailConfig, MailConfig)]): IO[Unit] =
    for {
      _ <- userDao.updateUser(userId, isExternalConfig)
      _ <- mailConfigDao.removeConfigByUserId(userId)
      _ <- if (isExternalConfig && mailConfigs.nonEmpty) {
        mailConfigDao.createConfig(createDbMailConfig(userId, mailConfigs.get._1))
          >> mailConfigDao.createConfig(createDbMailConfig(userId, mailConfigs.get._2))
      } else {
        IO.none
      }
    } yield ()

  override def getUser(telegramId: String): IO[Option[DbUser]] =
    userDao.getUserByTelegramId(telegramId)

  override def getMailImapConfig(userId: Long): IO[Option[MailConfig.ImapMailConfig]] =
      mailConfigDao.getConfig(userId, MailConfigDao.ConfigType.Imap.id)
        .map(_.map(dbConfig => ImapMailConfig(dbConfig.host, dbConfig.port, dbConfig.user, dbConfig.password)))

  override def getMailSmtpConfig(userId: Long): IO[Option[MailConfig.SmtpMailConfig]] =
    mailConfigDao.getConfig(userId, MailConfigDao.ConfigType.Smtp.id)
      .map(_.map(dbConfig => SmtpMailConfig(dbConfig.host, dbConfig.port, dbConfig.user, dbConfig.password)))

  private def createDbMailConfig(userId: Long, mailConfig: MailConfig): DbMailConfig = mailConfig match
    case ImapMailConfig(host, port, user, password) => DbMailConfig(None, userId, MailConfigDao.ConfigType.Imap.id, host, port, user, password)
    case SmtpMailConfig(host, port, user, password) => DbMailConfig(None, userId, MailConfigDao.ConfigType.Smtp.id, host, port, user, password)
}

object InteractionService {
  def from(userService: UserDao, mailConfigDao: MailConfigDao): IO[InteractionService] = IO {
    new InteractionServiceImpl(userService, mailConfigDao)
  }
}
