package com.pnp.service

import cats.effect.{IO, Ref}
import com.pnp.domain.{ChatData, MailInfo}
import jakarta.mail.Message

trait MailRepositoryService {
  def addMails(chatId: String, mailInfos: List[MailInfo]): IO[Unit]
  def getMail(chatId: String, mailIndex: Int): IO[Option[MailInfo]]
  def addLastMessage(chatId: String, message: Message): IO[Unit]
  def getLastMessage(chatId: String): IO[Option[Message]]
}

class MailRepositoryServiceImpl(chatsData: Ref[IO, Map[String, ChatData]]) extends MailRepositoryService {
  override def addMails(chatId: String, mailInfos: List[MailInfo]): IO[Unit] = {
    updateField(chatId, chatData => chatData.copy(fetchedMailInfos = mailInfos))
  }

  override def getMail(chatId: String, mailIndex: Int): IO[Option[MailInfo]] = {
    getField(chatId, _.fetchedMailInfos.lift(mailIndex))
  }

  override def addLastMessage(chatId: String, message: Message): IO[Unit] = {
    updateField(chatId, chatData => chatData.copy(lastMessage = Some(message)))
  }

  override def getLastMessage(chatId: String): IO[Option[Message]] = {
    getField(chatId, _.lastMessage)
  }

  private def updateField[A](chatId: String, updateFunc: ChatData => ChatData): IO[Unit] = {
    chatsData.update { chatMap =>
      val chatData = chatMap.getOrElse(chatId, ChatData(Nil, None))
      chatMap.updated(chatId, updateFunc(chatData))
    }
  }

  private def getField[A](chatId: String, extractField: ChatData => Option[A]): IO[Option[A]] = {
    chatsData.get.map { chatMap =>
      chatMap.get(chatId).flatMap(extractField)
    }
  }
}

object MailRepositoryService {
  def make(chatsData: Ref[IO, Map[String, ChatData]]): IO[MailRepositoryService] = IO {
    new MailRepositoryServiceImpl(chatsData)
  }
}
