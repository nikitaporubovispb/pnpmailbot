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

  private def send(smtpConfig: SmtpConfig)(messageCreator: Session => Either[DomainError, Message]) : IO[Either[DomainError, Unit]] = {
    val session = Session.getInstance(smtpProps(smtpConfig.host, smtpConfig.port))
    (for {
      message <- EitherT.fromEither[IO](messageCreator(session))
      result <- EitherT(makeTransportResource(session, smtpConfig.user, smtpConfig.pass).use {
          case Right(transport) => sendMessage(message, transport)
          case Left(error) => IO(Either.left[DomainError, Unit](error))
      })
    } yield result).value
  }

  def sendMail(smtpConfig: SmtpConfig, from: String, to: String, subject: String, content: String): IO[Either[DomainError, Unit]] =
    send(smtpConfig){ createMessage(_, from, to, subject, content) }

  def sendForward(smtpConfig: SmtpConfig, to: String, content: String, message: Message): IO[Either[DomainError, Unit]] =
    send(smtpConfig){ createForwardMessage(_, to, content, message) }

  def sendReply(smtpConfig: SmtpConfig, to: String, content: String, message: Message): IO[Either[DomainError, Unit]] =
    send(smtpConfig){ createReplyMessage(_, to, content, message) }

  private def createMessage(session: Session, from: String, to: String, subject: String, content: String): Either[DomainError, Message] = {
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

  private def makeTransportResource(session: Session, user: String, pass:String): Resource[IO, Either[DomainError, Transport]] =
    Resource.make(IO {
      Either.catchNonFatal {
        val transport = session.getTransport("smtp")
        transport.connect(user, pass)
        transport
      }.leftMap { th => SmtpSendMessageError(th.getMessage) }
    })(either => IO { Either.catchNonFatal(either.foreach(_.close)) })

  private def sendMessage(message: Message, transport: Transport) : IO[Either[DomainError, Unit]] = IO.blocking {
    Either.catchNonFatal { transport.sendMessage(message, message.getAllRecipients) }
      .leftMap { th => SmtpSendMessageError(th.getMessage) }
  }
}

object Smtp {
}
