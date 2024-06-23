package com.pnp.domain

import jakarta.mail.Message

object Mail {
  case class MailInfo(id: Int, content: List[String], from: String, to: String, subject: String)
  case class MailContent(content: String, contentType: String)
}
