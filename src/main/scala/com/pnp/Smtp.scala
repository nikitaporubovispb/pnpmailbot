package com.pnp

import cats.data.EitherT
import cats.effect.IO
import cats.effect.kernel.Resource
import cats.syntax.all.*
import com.pnp.domain.*
import com.pnp.domain.DomainError.{SmtpCreateMessageError, SmtpSendMessageError, SmtpTransportConnectError}
import jakarta.mail.*
import jakarta.mail.internet.*
import logstage.LogIO

import java.util.Properties

class Smtp(using smtpConfig: SmtpConfig, log: LogIO[IO]) {
  private val smtpProps: Properties = createSmtpProperties(smtpConfig)

  def sendMail(from: String, to: String, subject: String, message: String): IO[Either[DomainError, Unit]] = {
    (for {
      session <- EitherT.right[DomainError](IO(Session.getInstance(smtpProps)))
      mail <- EitherT.fromEither[IO](createMessage(session, from, to, subject, message))
      result <- EitherT(makeTransportResource(session).use { transport =>
        sendMessage(mail, transport)
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

  private def makeTransportResource(session: Session): Resource[IO, Transport] =
    Resource.make {
      IO {
        val transport = session.getTransport("smtp")
        transport.connect(smtpConfig.user, smtpConfig.pass)
        transport
      }
    } { transport =>
      IO {
        transport.close()
      }.handleErrorWith(_ => IO.unit)
    }

  private def sendMessage(mail: Message, transport: Transport) : IO[Either[DomainError, Unit]] = IO {
    Either.catchNonFatal { transport.sendMessage(mail, mail.getAllRecipients) }
      .leftMap { th => SmtpSendMessageError(th.getMessage) }
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
