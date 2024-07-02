package com.pnp.domain

enum DomainError(val msg: String):
  case SmtpCreateMessageError(override val msg: String) extends DomainError(msg)
  case SmtpSendMessageError(override val msg: String) extends DomainError(msg)
  case ImapGetMessageError(override val msg: String) extends DomainError(msg)
  case TelegramProcessError(override val msg: String) extends DomainError(msg)



