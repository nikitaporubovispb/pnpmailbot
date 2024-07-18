package com.pnp

import cats.effect.{ExitCode, IO, IOApp, Ref, Resource}
import com.pnp.dao.{MailConfigDao, UserDao}
import com.pnp.domain.{ChatData, Config, DbMailConfig}
import com.pnp.service.{InteractionService, MailRepositoryService}
import com.pnp.service.mail.{ImapService, SmtpService}
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
      smtp <- SmtpService.make(using log).toResource
      imap <- ImapService.make(using log).toResource
      encryptionUtils <- EncryptionUtils.make(config.encryptionConfig).toResource
      mailRepository <- Ref.of[IO, Map[String, ChatData]](Map.empty).flatMap(MailRepositoryService.make).toResource
    } yield (config, log, interaction, mailRepository, smtp, imap, encryptionUtils)
    resources.use { case (config, log, interaction, mailRepository, smtp, imap, encryptionUtils) =>
        Telegram.run(using mailRepository, smtp, imap, config, log, interaction, encryptionUtils)
      }.as(ExitCode.Success)
  }
}

