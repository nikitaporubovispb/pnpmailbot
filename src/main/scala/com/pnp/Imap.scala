package com.pnp

import cats.effect.IO
import cats.effect.kernel.Resource
import jakarta.mail.search.FlagTerm
import jakarta.mail.*
import jakarta.mail.internet.*
import logstage.LogIO

import java.util.Properties


case class MailInfo(from: String, to: String, subject: String)

class Imap(using imapConfig: ImapConfig, log: LogIO[IO]) {
  private val imapProperties: Properties = {
    val props = new Properties
    props.put("mail.debug", "false")
    props.put("mail.store.protocol", "imaps")
    props
  }

  private val fetchProfile: FetchProfile = {
    val profile = new FetchProfile
    profile.add(FetchProfile.Item.ENVELOPE)
    profile.add(FetchProfile.Item.CONTENT_INFO)
    profile.add(FetchProfile.Item.FLAGS)
    profile
  }
  
  def getUnseenMailInfos: IO[List[MailInfo]] = {
    makeStoreResource().use { store => IO {
        val inbox = store.getFolder("INBOX")
        inbox.open(Folder.READ_WRITE)
        val search = inbox.search(FlagTerm(Flags(Flags.Flag.SEEN), false))
        if (search.isEmpty) {
          List.empty
        } else {
          inbox.fetch(search, fetchProfile)
          search.map { message =>
            MailInfo(
              message.getFrom.map { address => address.asInstanceOf[InternetAddress].getAddress }.mkString(","),
              message.getAllRecipients.map { address => address.asInstanceOf[InternetAddress].getAddress }.mkString(","),
              message.getSubject,
            )
          }.toList
        }
      }
    }
  }

  private def makeStoreResource(): Resource[IO, Store] = Resource.fromAutoCloseable {
    IO {
      val store = Session.getInstance(imapProperties).getStore
      store.connect(imapConfig.host, imapConfig.port, imapConfig.user, imapConfig.pass)
      store
    }
  }
}
