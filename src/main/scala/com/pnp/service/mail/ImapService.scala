package com.pnp.service.mail

import cats.effect.syntax.all.*
import cats.effect.{IO, Resource}
import com.pnp.domain.*
import com.pnp.service.mail.ImapServiceImpl.*
import jakarta.mail.*
import jakarta.mail.internet.*
import jakarta.mail.search.FlagTerm
import logstage.LogIO
import org.jsoup.*
import org.jsoup.safety.*

import java.io.InputStream
import java.util.{Objects, Properties}
import scala.annotation.tailrec

trait ImapService {
  def getUnseenMailInboxInfos(imapConfig: ImapConfig): IO[List[MailInfo]]
  def setMessageSeen(imapConfig: ImapConfig, message: Message): IO[Unit]
}

object ImapService {
  def make(using logIO: LogIO[IO]): IO[ImapService] = IO { new ImapServiceImpl }
}

class ImapServiceImpl(using log: LogIO[IO]) extends ImapService {
  def getUnseenMailInboxInfos(imapConfig: ImapConfig): IO[List[MailInfo]] = {
    val folderResource: Resource[IO, Folder] = for {
      store <- makeStoreResource(imapConfig)
      folder <- makeFolderResource(store, "INBOX")
    } yield folder
    folderResource.use { inbox =>
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
                message,
                parseContent(message).map(content => getCleanedContent(content.contentType, content.content)),
                message.getFrom.map { case ia:InternetAddress => ia.getAddress }.mkString(","),
                message.getAllRecipients.map { case ia:InternetAddress => ia.getAddress }.mkString(","),
                message.getSubject,
              )
            }.toList
          }
        }
    }
  }

  override def setMessageSeen(imapConfig: ImapConfig, message: Message): IO[Unit] = {
    val folderResource: Resource[IO, Folder] = for {
      store <- makeStoreResource(imapConfig)
      folder <- makeFolderResource(store, "INBOX")
    } yield folder
    folderResource.use { inbox =>
      IO {
        inbox.open(Folder.READ_WRITE)
        val search = inbox.search(FlagTerm(Flags(Flags.Flag.SEEN), false))
        if (search.isEmpty) {
          List.empty
        } else {
          inbox.fetch(search, fetchContentProfile)
          message match
            case m: MimeMessage =>
               search.find { case mif: MimeMessage => m.getMessageID == mif.getMessageID }
                 .fold(IO.raiseError(RuntimeException("No mail to set seen")))(_.setFlag(Flags.Flag.SEEN, true))
        }
      }
    }
  }

  private def makeStoreResource(imapConfig: ImapConfig): Resource[IO, Store] =
    Resource.make(IO {
        val store = Session.getInstance(imapProperties).getStore
        store.connect(imapConfig.host, imapConfig.port, imapConfig.user, imapConfig.pass)
        store
    })(store => IO(store.close()))

  private def makeFolderResource(store: Store, name: String): Resource[IO, Folder] =
    Resource.make(IO {
      store.getFolder(name)
    })(folder => IO(folder.close()))
}

object ImapServiceImpl {
  private val imapProperties: Properties = {
    val props = new Properties
    props.put("mail.debug", "false")
    props.put("mail.store.protocol", "imaps")
    props
  }

  private val fetchContentProfile: FetchProfile = {
    val profile = new FetchProfile
    profile.add(FetchProfile.Item.ENVELOPE)
    profile.add(FetchProfile.Item.CONTENT_INFO)
    profile.add(FetchProfile.Item.FLAGS)
    profile
  }

  private def cleanHtmlContent(content: String): String =
    Jsoup.clean(Jsoup.parse(content).html, "", Safelist.none)

  @tailrec private def parseContent(part: Part): List[MailContent] = {
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

  private def getCleanedContent(contentType: String, content: String): String = {
    if (contentType == null || content == null) return content
    cleanHtmlContent(content)
  }

  private def parseContentBodyPart(multipart: Multipart, i: Int) =
    parseContent(multipart.getBodyPart(i))

  private val MIME_TEXT_TYPE = "TEXT"
  private val MIME_MULTIPART_ALTERNATIVE = "multipart/alternative"
  private val MIME_TEXT_HTML = "TEXT/HTML"
}
