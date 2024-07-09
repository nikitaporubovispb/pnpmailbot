package com.pnp.mail

import cats.effect.syntax.all.*
import cats.syntax.all.*
import cats.effect.{IO, Resource}
import Imap.*
import com.pnp.domain.*
import com.pnp.domain.DomainError.*
import jakarta.mail.*
import jakarta.mail.internet.*
import jakarta.mail.search.FlagTerm
import logstage.LogIO
import org.jsoup.*
import org.jsoup.safety.*

import java.io.InputStream
import java.util.{Objects, Properties}
import scala.annotation.tailrec

class Imap(log: LogIO[IO]) {
  def getUnseenMailInboxInfos(imapConfig: ImapConfig): IO[Either[DomainError, List[MailInfo]]] = {
    val folderResource: Resource[IO, Either[DomainError, Folder]] = for {
      storeEither <- makeStoreResource(imapConfig)
      folderEither <- storeEither match {
        case Right(store) => makeFolderResource(store, "INBOX")
        case Left(error) => Resource.pure[IO, Either[DomainError, Folder]](Either.left[DomainError, Folder](error))
      }
    } yield folderEither
    folderResource.use {
      case Right(inbox) =>
        IO {
          inbox.open(Folder.READ_ONLY)
          val search = inbox.search(FlagTerm(Flags(Flags.Flag.SEEN), false))
          if (search.isEmpty) {
            List.empty.asRight
          } else {
            inbox.fetch(search, fetchContentProfile)
            search.zipWithIndex.map { (message, index) =>
              MailInfo(
                index,
                message,
                parseContent(message).map(content => getCleanedContent(content.contentType, content.content)),
                message.getFrom.map { address => address.asInstanceOf[InternetAddress].getAddress }.mkString(","),
                message.getAllRecipients.map { address => address.asInstanceOf[InternetAddress].getAddress }.mkString(","),
                message.getSubject,
              )
            }.toList.asRight
          }
        }
      case Left(error) => IO.pure(Either.left[DomainError, List[MailInfo]](error))
    }
  }

  private def makeStoreResource(imapConfig: ImapConfig): Resource[IO, Either[DomainError, Store]] =
    Resource.make(IO {
        Either.catchNonFatal {
          val store = Session.getInstance(imapProperties).getStore
          store.connect(imapConfig.host, imapConfig.port, imapConfig.user, imapConfig.pass)
          store
      }.leftMap { th => ImapGetMessageError(th.getMessage) }
    })(either => IO { Either.catchNonFatal(either.foreach(_.close)) })

  private def makeFolderResource(store:Store, name: String): Resource[IO, Either[DomainError, Folder]] =
    Resource.make(IO {
      Either.catchNonFatal {
        store.getFolder(name)
      }.leftMap { th => ImapGetMessageError(th.getMessage) }
    })(either => IO { Either.catchNonFatal(either.foreach(_.close)) })
}

object Imap {
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

  private def cleanHtmlContent(content: String): String = {
    Jsoup.clean(Jsoup.parse(content).html, "", Safelist.none)
  }

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
