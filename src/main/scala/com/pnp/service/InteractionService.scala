package com.pnp.service

import cats.effect.IO
import cats.syntax.all.*
import com.pnp.service.InteractionService.RegisterResult
import com.pnp.service.InteractionService.RegisterResult.*

trait InteractionService {
  def isRegistered(telegramId: String): IO[Boolean]
  def register(telegramId: String): IO[RegisterResult]
}

class InteractionServiceImpl(userService: UserService) extends InteractionService {
  override def isRegistered(telegramId: String): IO[Boolean] =
    userService.getUserByTelegramId(telegramId).map(_.isDefined)

  override def register(telegramId: String): IO[RegisterResult] = {
    isRegistered(telegramId).ifM(
      IO.pure(AlreadyRegistered),
      userService.createUser(telegramId, false).as(Ok)
    )
  }
}

object InteractionService {
  enum RegisterResult { case AlreadyRegistered, Ok }
  
  def from(userService: UserService): IO[InteractionService] = IO {
    new InteractionServiceImpl(userService)
  }
}
