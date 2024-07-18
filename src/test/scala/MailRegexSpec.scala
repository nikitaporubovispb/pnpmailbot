import com.pnp.telegram.MailTelegram
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class MailRegexSpec extends AnyFlatSpec with Matchers {

  "Mail server regex" should "match valid mail server URLs" in {
    val validUrls = Seq(
      "mail.site78.ru",
      "example.com",
      "site78.mail.co.uk",
      "example-site78.com",
    )

    validUrls.foreach { url =>
      withClue(s"URL: $url") {
        MailTelegram.mailServerUrl.matches(url) should be (true)
      }
    }
  }

  it should "not match invalid mail server URLs" in {
    val invalidUrls = Seq(
      "site78.mail.",
      ".example.com",
      "site78..co.uk",
      "example-site78",
      "fsd"
    )

    invalidUrls.foreach { url =>
      withClue(s"URL: $url") {
        MailTelegram.mailServerUrl.matches(url) should be (false)
      }
    }
  }

  "Email regex" should "match valid email addresses" in {
    val validEmails = Seq(
      "bot@site78.ru",
      "user@example.com",
      "admin@site.mail.co.uk",
      "example-site78@mail.com"
    )

    validEmails.foreach { email =>
      withClue(s"Email: $email") {
        MailTelegram.userMailName.matches(email) should be(true)
      }
    }
  }

  it should "not match invalid email addresses" in {
    val invalidEmails = Seq(
      "invalid-email@com",
      "userexample.com",
      "admin@site@.co.uk",
      "example@site78"
    )

    invalidEmails.foreach { email =>
      withClue(s"Email: $email") {
        MailTelegram.userMailName.matches(email) should be(false)
      }
    }
  }
}
