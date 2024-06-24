package com.pnp.domain

import jakarta.mail.Message

case class MailInfo(id: Int, message: Message, content: List[String], from: String, to: String, subject: String)
case class MailContent(content: String, contentType: String)
case class ChatData(fetchedMailInfos: List[MailInfo], lastMessage: Option[Message])


