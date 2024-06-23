package com.pnp

import cats.effect.IO
import cats.effect.kernel.Resource
import com.pnp.Imap.*
import com.pnp.domain.Mail.{MailContent, MailInfo}
import jakarta.mail.search.FlagTerm
import jakarta.mail.*
import jakarta.mail.internet.*
import logstage.LogIO

import java.io.InputStream
import java.util.{Objects, Properties}
import scala.annotation.tailrec
import org.jsoup.*
import org.jsoup.safety.*

class Imap(using imapConfig: ImapConfig, log: LogIO[IO]) {
  def getUnseenMailInboxInfos: IO[List[MailInfo]] = {
    makeStoreResource().use { store =>
      Resource.fromAutoCloseable(IO(store.getFolder("INBOX"))).use { inbox =>
        IO {
          inbox.open(Folder.READ_ONLY)
          val search = inbox.search(FlagTerm(Flags(Flags.Flag.SEEN), false))
          if (search.isEmpty) {
            List.empty
          } else {
            inbox.fetch(search, fetchContentProfile)
            search.zipWithIndex.map { (message, index) =>
              MailInfo(
                index,
                parseContent(message).map(content => getCleanedContent(content.contentType, content.content)),
                message.getFrom.map { address => address.asInstanceOf[InternetAddress].getAddress }.mkString(","),
                message.getAllRecipients.map { address => address.asInstanceOf[InternetAddress].getAddress }.mkString(","),
                message.getSubject,
              )
            }.toList
          }
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

object Imap {
  private val imapProperties: Properties = {
    val props = new Properties
    props.put("mail.debug", "false")
    props.put("mail.store.protocol", "imaps")
    props
  }

  private val fetchInfoProfile: FetchProfile = {
    val profile = new FetchProfile
    profile.add(FetchProfile.Item.ENVELOPE)
    profile.add(FetchProfile.Item.FLAGS)
    profile
  }

  private val fetchContentProfile: FetchProfile = {
    val profile = new FetchProfile
    profile.add(FetchProfile.Item.ENVELOPE)
    profile.add(FetchProfile.Item.CONTENT_INFO)
    profile.add(FetchProfile.Item.FLAGS)
    profile
  }

  private def cleanHtmlContent(content: String): String = {
    Jsoup.clean(Jsoup.parse(content).html, "", Safelist.none)
  }

  @tailrec
  private def parseContent(part: Part): List[MailContent] = {
    val content = part.getContent
    if (content == null) {
      List.empty
    } else {
      content match {
        case str : String => List(MailContent(str, ContentType(part.getContentType).getBaseType))
        case part: Part => parseContent(part)
        case multipart: Multipart => parseContent(multipart)
        case inputStream: InputStream => List.empty // todo attachment
        case _ => List.empty
      }
    }
  }

  private def parseContent(multipart: Multipart): List[MailContent] = {
    if (ContentType(multipart.getContentType).`match`(MIME_MULTIPART_ALTERNATIVE)) {
      extractLessRichAlternativeContent(multipart)
    } else {
      extractContentAll(multipart)
    }
  }

  private def extractContentAll(multipart: Multipart): List[MailContent] = {
    (0 until multipart.getCount)
      .flatMap(i => parseContentBodyPart(multipart, i))
      .toList
  }

  private def extractLessRichAlternativeContent(multipart: Multipart): List[MailContent] = {
    List((0 until multipart.getCount)
      .flatMap(i => parseContentBodyPart(multipart, i))
      .filter(Objects.nonNull)
      .head)
  }

  def getCleanedContent(contentType: String, content: String): String = {
    if (contentType == null || content == null) return content
    cleanHtmlContent(content)
  }

  private def parseContentBodyPart(multipart: Multipart, i: Int) =
    parseContent(multipart.getBodyPart(i))

  private val MIME_TEXT_TYPE = "TEXT"
  private val MIME_MULTIPART_ALTERNATIVE = "multipart/alternative"
  private val MIME_TEXT_HTML = "TEXT/HTML"
}
