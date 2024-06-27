package com.pnp.service

import cats.effect.IO
import com.pnp.service.UserService.User
import logstage.LogIO
import skunk.Session
import skunk.*
import skunk.implicits.*
import skunk.codec.all.*

trait UserService {
  def getUser(id: Long): IO[Option[User]]
  def getUserByTelegramId(idTelegram: String): IO[Option[User]]
  def createUser(telegramId: String, isExternalConfig: Boolean): IO[Unit]
}

class UserServiceImpl(using session: Session[IO], log: LogIO[IO]) extends UserService {
  private val selectUser = session.execute(
    sql"""
      SELECT id, telegramId, isExternalConfig
      FROM users
      WHERE id = $int8
    """.query(int8 *: text *: bool).to[User]
  )

  private val selectByTelegarmId = session.execute(
    sql"""
      SELECT id, telegramId, isExternalConfig
      FROM users
      WHERE telegramId = $text
    """.query(int8 *: text *: bool).to[User]
  )

  private val insertUser = session.execute(
    sql"""
      INSERT INTO users (telegramId, isExternalConfig)
      VALUES ($text, $bool)
     """.command
  )

  override def getUser(id: Long): IO[Option[User]] = {
    for {
      _ <- log.info(s"Getting user from DB")
      r <- selectUser(id).map(_.headOption)
    } yield r
  }

  override def getUserByTelegramId(idTelegram: String): IO[Option[User]] = {
    for {
      _ <- log.info(s"Getting user from DB by telegramId")
      r <- selectByTelegarmId(idTelegram).map(_.headOption)
    } yield r
  }

  override def createUser(telegramId: String, isExternalConfig: Boolean): IO[Unit] = {
    for {
      _ <- log.info(s"Creating user in DB")
      _ <- insertUser(telegramId, isExternalConfig)
    } yield ()
  }
}

object UserService {
  val createIfNotExists =
    sql"""CREATE TABLE IF NOT EXISTS users (id BIGSERIAL PRIMARY KEY, telegramId TEXT, isExternalConfig BOOLEAN);""".command

  def make(using session: Session[IO], logIO: LogIO[IO]): IO[UserService] =
    for {
      _ <- session.execute(createIfNotExists)
    } yield new UserServiceImpl

  case class User(
                   id: Long,
                   telegramId: String,
                   isExternalConfig: Boolean,
                 )
}
