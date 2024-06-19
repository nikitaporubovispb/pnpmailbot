package com.pnp

import cats.data.EitherT
import cats.effect.{IO, Resource}
import cats.syntax.all._
import com.pnp.domain.*
import com.pnp.domain.DomainError.*
import jakarta.mail.*
import jakarta.mail.internet.*
import logstage.LogIO

import java.util.Properties

class Smtp(using smtpConfig: SmtpConfig, log: LogIO[IO]) {
  private val smtpProps: Properties = {
    val props = new Properties
    props.put("mail.smtp.auth", "true")
    props.put("mail.smtp.starttls.enable", "true")
    props.put("mail.smtp.host", smtpConfig.host)
    props.put("mail.smtp.port", smtpConfig.port)
    props
  }

  def sendMail(from: String, to: String, subject: String, content: String): IO[Either[DomainError, Unit]] = {
    val session = Session.getInstance(smtpProps)
    (for {
      message <- EitherT.fromEither[IO](createMessage(session, from, to, subject, content))
      result <- EitherT(makeTransportResource(session).use { transport =>
        sendMessage(message, transport)
      })
    } yield result).value
  }

  private def createMessage(session: Session, from: String, to: String,
                            subject: String, content: String): Either[DomainError, Message] = {
    Either.catchNonFatal {
      val message = MimeMessage(session)
      message.setFrom(InternetAddress(from))
      message.setSubject(subject)
      message.setRecipients(Message.RecipientType.TO,
        InternetAddress.parse(to).asInstanceOf[Array[Address]])

      val multiPart = new MimeMultipart()
      val messageBodyPart = MimeBodyPart()
      messageBodyPart.setText(content)
      multiPart.addBodyPart(messageBodyPart)

      message.setContent(multiPart)
      message.saveChanges()

      message
    }.leftMap { th => SmtpCreateMessageError(th.getMessage) }
  }

  private def makeTransportResource(session: Session): Resource[IO, Transport] =  Resource.fromAutoCloseable {
      IO {
        val transport = session.getTransport("smtp")
        transport.connect(smtpConfig.user, smtpConfig.pass)
        transport
      }
    }

  private def sendMessage(message: Message, transport: Transport) : IO[Either[DomainError, Unit]] = IO.blocking {
    Either.catchNonFatal { transport.sendMessage(message, message.getAllRecipients) }
      .leftMap { th => SmtpSendMessageError(th.getMessage) }
  }
}

object Smtp {
}
