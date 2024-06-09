package com.pnp

import cats.effect.{ExitCode, IO, IOApp}
import izumi.logstage.api.IzLogger
import logstage.LogIO

object Main extends IOApp {
  private val createLogger: IO[LogIO[IO]] = IO {
    val logger = IzLogger()
    LogIO.fromLogger[IO](logger)
  }

  override def run(args: List[String]): IO[ExitCode] = {
    val resources = for {
      config <- Config.load.toResource
      log <- createLogger.toResource
    } yield (config, log)
    resources.use { case (config, log) => Telegram.run(using config, log) }
      .as(ExitCode.Success)
  }
}

