package com.pnp.domain

enum DomainError:
  case SmtpCreateMessageError(msg: String)
  case SmtpSendMessageError(msg: String)



