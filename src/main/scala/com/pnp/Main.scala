package com.pnp

import cats.effect.{ExitCode, IO, IOApp, Resource}
import com.pnp.domain.Config
import com.pnp.mail.{Imap, Smtp}
import com.pnp.service.{InteractionService, UserService}
import com.pnp.telegram.Telegram
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

  def createSession(using config: Config): Resource[IO, Session[IO]] =
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
      userService <- UserService.make(using session, log).toResource
      interaction <- InteractionService.from(userService).toResource
    } yield (config, log, interaction)
    resources.use { case (config, log, interaction) =>
        val smtp = Smtp(using config.smtp, log)
        val imap = Imap(using config.imap, log)
        Telegram.run(using smtp, imap, config.bot, log, interaction)
      }.as(ExitCode.Success)
  }
}

