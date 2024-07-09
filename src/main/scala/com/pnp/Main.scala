package com.pnp

import cats.effect.{ExitCode, IO, IOApp, Resource}
import com.pnp.dao.{MailConfigDao, UserDao}
import com.pnp.domain.{Config, DbMailConfig}
import com.pnp.mail.{Imap, Smtp}
import com.pnp.service.InteractionService
import com.pnp.telegram.Telegram
import com.pnp.utils.EncryptionUtils
import org.typelevel.otel4s.trace.Tracer
import izumi.logstage.api.IzLogger
import logstage.LogIO
import skunk.Session

object Main extends IOApp {
  given Tracer[IO] = Tracer.noop[IO]

  private val createLogger: IO[LogIO[IO]] = IO {
    val logger = IzLogger()
    LogIO.fromLogger[IO](logger)
  }

  private def createSession(using config: Config): Resource[IO, Session[IO]] =
    Session.single(
      host = config.database.host,
      port = config.database.port,
      user = config.database.user,
      database = config.database.database,
      password = Some(config.database.password)
    )

  override def run(args: List[String]): IO[ExitCode] = {
    val resources = for {
      config      <- Config.load.toResource
      log         <- createLogger.toResource
      session     <- createSession(using config)
      userDao <- UserDao.make(using session, log).toResource
      mailConfigDao <- MailConfigDao.make(using session, log).toResource
      interaction <- InteractionService.from(userDao, mailConfigDao).toResource
    } yield (config, log, interaction)
    resources.use { case (config, log, interaction) =>
        val smtp = Smtp(log)
        val imap = Imap(log)
        val encryptionUtils = EncryptionUtils(config.encryptionConfig)
        Telegram.run(using smtp, imap, config, log, interaction, encryptionUtils)
      }.as(ExitCode.Success)
  }
}

