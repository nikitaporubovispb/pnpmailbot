package com.pnp.service.mail

import cats.effect.{IO, Resource}
import cats.syntax.all.*
import com.pnp.domain.*
import jakarta.mail.*
import jakarta.mail.internet.*
import logstage.LogIO

import java.util.Properties

trait SmtpService {
  def sendMail(smtpConfig: SmtpConfig, from: String, to: String, subject: String, content: String): IO[Unit]
  def sendForward(smtpConfig: SmtpConfig, to: String, content: String, message: Message): IO[Unit]
  def sendReply(smtpConfig: SmtpConfig, to: String, content: String, message: Message): IO[Unit]
}

object SmtpService {
  def make(using logIO: LogIO[IO]): IO[SmtpService] = IO { new SmtpServiceImpl }
}

class SmtpServiceImpl(using log: LogIO[IO]) extends SmtpService {
  private def smtpProps(host: String, port: Int): Properties = {
    val props = new Properties
    props.put("mail.smtp.auth", "true")
    props.put("mail.smtp.starttls.enable", "true")
    props.put("mail.smtp.host", host)
    props.put("mail.smtp.port", port)
    props
  }

  override def sendMail(smtpConfig: SmtpConfig, from: String, to: String, subject: String, content: String): IO[Unit] =
    send(smtpConfig) {
      createMessage(_, from, to, subject, content)
    }

  override def sendForward(smtpConfig: SmtpConfig, to: String, content: String, message: Message): IO[Unit] =
    send(smtpConfig) {
      createForwardMessage(_, to, content, message)
    }

  override def sendReply(smtpConfig: SmtpConfig, to: String, content: String, message: Message): IO[Unit] =
    send(smtpConfig) {
      createReplyMessage(_, to, content, message)
    }


  private def send(smtpConfig: SmtpConfig)(messageCreator: Session => IO[Message]) : IO[Unit] = {
    val session = Session.getInstance(smtpProps(smtpConfig.host, smtpConfig.port))
    for {
      message <- messageCreator(session)
      result <- makeTransportResource(session, smtpConfig.user, smtpConfig.pass).use { sendMessage(message, _) }
    } yield result
  }

  private def createMessage(session: Session, from: String, to: String, subject: String, content: String): IO[Message] = {
    IO {
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
    }
  }

  private def createForwardMessage(session: Session, to: String, content: String, message: Message): IO[Message] = {
    IO {
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
    }
  }

  private def createReplyMessage(session: Session, to: String, content: String, message: Message): IO[Message] = {
    IO {
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
    }
  }

  private def makeTransportResource(session: Session, user: String, pass:String): Resource[IO, Transport] =
    Resource.make(IO {
        val transport = session.getTransport("smtp")
        transport.connect(user, pass)
        transport
    })(transport => IO(transport.close()))

  private def sendMessage(message: Message, transport: Transport) : IO[Unit] =
    IO.blocking {
      transport.sendMessage(message, message.getAllRecipients)
    }
}

object SmtpServiceImpl {
}
