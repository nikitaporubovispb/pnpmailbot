package com.pnp.domain

enum DomainError(msg: String):
  case SmtpCreateMessageError(msg: String) extends DomainError(msg)
  case SmtpSendMessageError(msg: String) extends DomainError(msg)
  case TelegramProcessError(msg: String) extends DomainError(msg)



