package com.pnp.dao

import cats.effect.IO
import com.pnp.dao.{UserDao, UserDaoImpl}
import com.pnp.domain.DbUser
import logstage.LogIO
import skunk.*
import skunk.codec.all.*
import skunk.implicits.*

trait UserDao {
  def getUser(id: Long): IO[Option[DbUser]]
  def getUserByTelegramId(idTelegram: String): IO[Option[DbUser]]
  def createUser(telegramId: String, isExternalConfig: Boolean): IO[Long]
  def updateUser(id: Long, isExternalConfig: Boolean): IO[Unit]
}

class UserDaoImpl(using session: Session[IO], log: LogIO[IO]) extends UserDao {
  private val selectUser = session.execute(
    sql"""
      SELECT id, telegram_id, is_external_config
      FROM users
      WHERE id = $int8
    """.query(int8 *: text *: bool).to[DbUser]
  )

  private val selectByTelegramId = session.execute(
    sql"""
      SELECT id, telegram_id, is_external_config
      FROM users
      WHERE telegram_id = $text
    """.query(int8 *: varchar(32) *: bool).to[DbUser]
  )

  private val insertUser = session.execute(
    sql"""
      INSERT INTO users (telegram_id, is_external_config)
      VALUES ($varchar, $bool)
     """.command
  )

  private val getCurrId =
    sql"""
      SELECT currval(pg_get_serial_sequence('users', 'id'))
    """.query(int8)

  private val updateIsExternalConfigUser = session.execute(
    sql"""
      UPDATE users
      SET is_external_config = $bool
      WHERE id = $int8;
     """.command
  )

  override def getUser(id: Long): IO[Option[DbUser]] = {
    for {
      _ <- log.info(s"Getting user from DB")
      r <- selectUser(id).map(_.headOption)
    } yield r
  }

  override def getUserByTelegramId(idTelegram: String): IO[Option[DbUser]] = {
    for {
      _ <- log.info(s"Getting user from DB by telegramId")
      r <- selectByTelegramId(idTelegram).map(_.headOption)
    } yield r
  }

  override def createUser(telegramId: String, isExternalConfig: Boolean): IO[Long] =
    for {
      _ <- log.info(s"Creating user in DB")
      _ <- insertUser(telegramId, isExternalConfig)
      id: Long <- session.unique(getCurrId)
    } yield id

  override def updateUser(id: Long, isExternalConfig: Boolean): IO[Unit] =
    for {
      _ <- log.info(s"Update user isExternalConfig in DB")
      _ <- updateIsExternalConfigUser(isExternalConfig, id)
    } yield ()
}

object UserDao {
  val createIfNotExists =
    sql"""CREATE TABLE IF NOT EXISTS users (id BIGSERIAL PRIMARY KEY, telegram_id VARCHAR(32), is_external_config BOOLEAN);""".command

  def make(using session: Session[IO], logIO: LogIO[IO]): IO[UserDao] =
    for {
      _ <- session.execute(createIfNotExists)
    } yield new UserDaoImpl
  
}
