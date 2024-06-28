package com.pnp.mail

import cats.data.EitherT
import cats.effect.{IO, Resource}
import cats.syntax.all.*
import com.pnp.domain.*
import com.pnp.domain.DomainError.*
import jakarta.mail.*
import jakarta.mail.internet.*
import logstage.LogIO

import java.util.Properties

class Smtp(log: LogIO[IO]) {
  private def smtpProps(host: String, port: Int): Properties = {
    val props = new Properties
    props.put("mail.smtp.auth", "true")
    props.put("mail.smtp.starttls.enable", "true")
    props.put("mail.smtp.host", host)
    props.put("mail.smtp.port", port)
    props
  }

  def sendMail(smtpConfig: SmtpConfig, from: String, to: String, subject: String, content: String): IO[Either[DomainError, Unit]] = {
    val session = Session.getInstance(smtpProps(smtpConfig.host, smtpConfig.port))
    (for {
      message <- EitherT.fromEither[IO](createMessage(session, from, to, subject, content))
      result <- EitherT(makeTransportResource(session, smtpConfig.user, smtpConfig.pass).use { transport =>
        sendMessage(message, transport)
      })
    } yield result).value
  }

  def sendForward(smtpConfig: SmtpConfig, to: String, content: String, message: Message): IO[Either[DomainError, Unit]] = {
    val session = Session.getInstance(smtpProps(smtpConfig.host, smtpConfig.port))
    (for {
      message <- EitherT.fromEither[IO](createForwardMessage(session, to, content, message))
      result <- EitherT(makeTransportResource(session, smtpConfig.user, smtpConfig.pass).use { transport =>
        sendMessage(message, transport)
      })
    } yield result).value
  }

  def sendReply(smtpConfig: SmtpConfig, to: String, content: String, message: Message): IO[Either[DomainError, Unit]] = {
    val session = Session.getInstance(smtpProps(smtpConfig.host, smtpConfig.port))
    (for {
      message <- EitherT.fromEither[IO](createReplyMessage(session, to, content, message))
      result <- EitherT(makeTransportResource(session, smtpConfig.user, smtpConfig.pass).use { transport =>
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

      val messageBodyPart = MimeBodyPart()
      messageBodyPart.setText(content)
      
      val multiPart = new MimeMultipart()
      multiPart.addBodyPart(messageBodyPart)

      message.setContent(multiPart)
      message.saveChanges()

      message
    }.leftMap { th => SmtpCreateMessageError(th.getMessage) }
  }

  private def createForwardMessage(session: Session, to: String, content: String, message: Message): Either[DomainError, Message] = {
    Either.catchNonFatal {
      val forward = MimeMessage(session)
      forward.setFrom(message.getRecipients(Message.RecipientType.TO)(0))
      forward.setSubject("Fwd: " + message.getSubject)
      forward.setRecipients(Message.RecipientType.TO,
        InternetAddress.parse(to).asInstanceOf[Array[Address]])

      val contentBodyPart = MimeBodyPart()
      contentBodyPart.setText(content)

      val messageBodyPart = MimeBodyPart()
      messageBodyPart.setContent(message, "message/rfc822")

      val multiPart = new MimeMultipart()
      multiPart.addBodyPart(contentBodyPart)
      multiPart.addBodyPart(messageBodyPart)

      forward.setContent(multiPart)
      forward.saveChanges()

      forward
    }.leftMap { th => SmtpCreateMessageError(th.getMessage) }
  }

  private def createReplyMessage(session: Session, to: String, content: String, message: Message): Either[DomainError, Message] = {
    Either.catchNonFatal {
      val forward = MimeMessage(session)
      forward.setFrom(message.getRecipients(Message.RecipientType.TO)(0))
      forward.setSubject("Re: " + message.getSubject)
      forward.setRecipients(Message.RecipientType.TO,
        InternetAddress.parse(to).asInstanceOf[Array[Address]])

      val contentBodyPart = MimeBodyPart()
      contentBodyPart.setText(content)

      val multiPart = new MimeMultipart()
      multiPart.addBodyPart(contentBodyPart)

      forward.setContent(multiPart)
      forward.saveChanges()

      forward
    }.leftMap { th => SmtpCreateMessageError(th.getMessage) }
  }

  private def makeTransportResource(session: Session, user: String, pass:String): Resource[IO, Transport] =  Resource.fromAutoCloseable {
      IO {
        val transport = session.getTransport("smtp")
        transport.connect(user, pass)
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
