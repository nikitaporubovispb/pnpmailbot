package com.pnp

import cats.effect.IO
import cats.syntax.all._
import com.pnp.domain.*
import jakarta.mail.*
import jakarta.mail.internet.*
import logstage.LogIO

import java.util.Properties

class Smtp(using smtpConfig: SmtpConfig, log: LogIO[IO]) {
  private val smtpProps: Properties = createSmtpProperties(smtpConfig)

  def sendMail(from: String, to: String, subject: String, message: String): IO[Either[DomainError, Unit]] = {
    IO {
      Either.catchNonFatal {
        val session = Session.getInstance(smtpProps)
        val mail = createMessage(session, from, to, subject, message)
        val transport = session.getTransport("smtp")
        connectSmtp(transport, smtpConfig)
        transport.sendMessage(mail, mail.getAllRecipients)
      }.leftMap { th => DomainError.SmtpError(th.getMessage) }
    }
  }

  private def connectSmtp(transport: Transport, smtpConfig: SmtpConfig) : Unit = {
    if (!transport.isConnected) {
      transport.connect(smtpConfig.user, smtpConfig.pass)
    }
  }

  private def createMessage(session: Session, from: String, to: String,
                            subject: String, content: String): Message = {
    val message = MimeMessage(session)
    message.setFrom(InternetAddress(from))
    message.setSubject(subject)
    message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to).asInstanceOf[Array[Address]])

    val multiPart = new MimeMultipart()
    val messageBodyPart = MimeBodyPart()
    messageBodyPart.setText(content)
    multiPart.addBodyPart(messageBodyPart)

    message.setContent(multiPart)
    message.saveChanges()

    message
  }

  private def createSmtpProperties(smtpConfig: SmtpConfig) = {
    val props = new Properties
    props.put("mail.smtp.auth", "true")
    props.put("mail.smtp.starttls.enable", "true")
    props.put("mail.smtp.host", smtpConfig.host)
    props.put("mail.smtp.port", smtpConfig.port)
    props
  }
}

object Smtp {
}
