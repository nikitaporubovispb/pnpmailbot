package com.pnp.domain

enum DomainError:
  case SmtpCreateMessageError(msg: String)
  case SmtpTransportConnectError(msg: String)
  case SmtpSendMessageError(msg: String)



