package com.pnp.dao

import cats.effect.IO
import com.pnp.domain.DbMailConfig
import logstage.LogIO
import skunk.*
import skunk.codec.all.*
import skunk.implicits.*

trait MailConfigDao {
  def getConfig(userId: Long, configType: Int): IO[Option[DbMailConfig]]
  def createConfig(mailConfig: DbMailConfig): IO[Unit]
  def removeConfigByUserId(userId: Long): IO[Unit]
}

class MailConfigDaoImpl(using session: Session[IO], log: LogIO[IO]) extends MailConfigDao {
  private val selectMailConfig = session.execute(
    sql"""
      SELECT id, user_id, config_type, host, port, user_name, pass
      FROM mailConfig
      WHERE user_id = $int8 and config_type = $int8
    """.query(int8.opt *: int8 *: int4 *: varchar(256) *: int4 *: varchar(128) *: varchar(128)).to[DbMailConfig]
  )

  private val insertMailConfig = session.execute(
    sql"""
      INSERT INTO mailConfig (user_id, config_type, host, port, user_name, pass)
      VALUES ($int8, $int4, $varchar, $int4, $varchar, $varchar)
     """.command
  )

  private val deleteByUserId = session.execute(
    sql"""
      DELETE FROM mailConfig WHERE user_id = $int8;
    """.command
  )

  override def getConfig(userId: Long, configType: Int): IO[Option[DbMailConfig]] = {
    for {
      _ <- log.info(s"Getting config from DB")
      r <- selectMailConfig(userId, configType).map(_.headOption)
    } yield r
  }

  override def createConfig(mailConfig: DbMailConfig): IO[Unit] = 
    for {
      _ <- log.info(s"Creating config in DB")
      _ <- insertMailConfig(mailConfig.userId, mailConfig.configType, 
            mailConfig.host, mailConfig.port, mailConfig.user, mailConfig.password)
    } yield ()

  override def removeConfigByUserId(userId: Long): IO[Unit] =
    for {
      _ <- log.info(s"Remove config by user id")
      _ <- deleteByUserId(userId)
    } yield ()

}

object MailConfigDao {
  enum ConfigType(val id: Int) {
    case Imap extends ConfigType(1)
    case Smtp extends ConfigType(2)
  }

  val createIfNotExists =
    sql"""CREATE TABLE IF NOT EXISTS mailConfig
          (id BIGSERIAL PRIMARY KEY, user_id BIGINT, config_type INT, host VARCHAR(256), port INT, user_name VARCHAR(128), pass VARCHAR(128),
             CONSTRAINT fk_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE);
         """.command

  def make(using session: Session[IO], logIO: LogIO[IO]): IO[MailConfigDao] =
    for {
      _ <- session.execute(createIfNotExists)
    } yield new MailConfigDaoImpl
}
