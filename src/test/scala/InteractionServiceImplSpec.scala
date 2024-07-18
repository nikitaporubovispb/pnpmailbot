import cats.effect.IO
import cats.effect.testing.scalatest.AsyncIOSpec
import com.pnp.dao.*
import com.pnp.dao.MailConfigDao.ConfigType
import com.pnp.domain.*
import com.pnp.service.InteractionServiceImpl
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec

class InteractionServiceImplSpec extends AsyncWordSpec with AsyncIOSpec with Matchers {

    case class TestUserDao(var users: Map[Long, DbUser] = Map.empty) extends UserDao {
        override def getUser(id: Long): IO[Option[DbUser]] =
            IO.pure(users.get(id))

        override def getUserByTelegramId(idTelegram: String): IO[Option[DbUser]] =
            IO.pure(users.values.find(_.telegramId == idTelegram))

        override def createUser(telegramId: String, isExternalConfig: Boolean): IO[Long] = IO {
            val newUser = DbUser(users.size + 1, telegramId, isExternalConfig)
            users = users.updated(newUser.id, newUser)
            newUser.id
        }

        override def updateUser(id: Long, isExternalConfig: Boolean): IO[Unit] = IO {
            val user = users.get(id)
            user.fold
                { IO.raiseError(RuntimeException("No user!!")) }
                { user => users = users.updated(user.id, user.copy(isExternalConfig = isExternalConfig)) }
        }
    }

    case class TestMailConfigDao(var configs: Map[(Long, Int), DbMailConfig] = Map.empty) extends MailConfigDao {
        override def getConfig(userId: Long, configType: Int): IO[Option[DbMailConfig]] =
            IO.pure(configs.get(userId, configType))

        override def createConfig(mailConfig: DbMailConfig): IO[Unit] = IO {
            configs = configs.updated((mailConfig.userId, mailConfig.configType), mailConfig)
        }

        override def removeConfigByUserId(userId: Long): IO[Unit] = IO {
            configs = configs.view.filterKeys((id, _) => id != userId).toMap
        }
    }

    val userId: Long = 0L
    val testUserDao: TestUserDao = TestUserDao(Map(userId -> DbUser(userId, "existingTelegramId", false)))

    val imapConfig: DbMailConfig = DbMailConfig(Some(0L), userId, ConfigType.Imap.id, "imap.example.com", 993, "user", "password")
    val smtpConfig: DbMailConfig = DbMailConfig(Some(0L), userId, ConfigType.Smtp.id, "smtp.example.com", 587, "user", "password")

    val testMailConfigDao: TestMailConfigDao = TestMailConfigDao(Map(
        (0L, ConfigType.Imap.id) -> imapConfig,
        (0L, ConfigType.Smtp.id) -> smtpConfig
    ))

    val service = new InteractionServiceImpl(testUserDao, testMailConfigDao)

    "InteractionService" should {

        "create a new user" in {
            for {
                userId <- service.createUser("newTelegramId", false, None)
                user <- testUserDao.getUser(userId)
            } yield {
                user shouldBe defined
                user.get.telegramId shouldBe "newTelegramId"
                user.get.isExternalConfig shouldBe false
            }
        }

        "create a new user with mail configs" in {
            val imapConfig = MailConfig.ImapMailConfig("imap.example.com", 993, "user", "password")
            val smtpConfig = MailConfig.SmtpMailConfig("smtp.example.com", 587, "user", "password")
            val mailConfigs = Some((imapConfig, smtpConfig))

            for {
                userId <- service.createUser("newTelegramIdWithConfigs", true, mailConfigs)
                user <- testUserDao.getUser(userId)
                imapConfigFromDb <- testMailConfigDao.getConfig(userId, ConfigType.Imap.id)
                smtpConfigFromDb <- testMailConfigDao.getConfig(userId, ConfigType.Smtp.id)
            } yield {
                user shouldBe defined
                user.get.telegramId shouldBe "newTelegramIdWithConfigs"
                user.get.isExternalConfig shouldBe true

                imapConfigFromDb shouldBe defined
                imapConfigFromDb.get.host shouldBe "imap.example.com"

                smtpConfigFromDb shouldBe defined
                smtpConfigFromDb.get.host shouldBe "smtp.example.com"
            }
        }

        "update an existing user with new mail configs" in {
            val imapConfig = MailConfig.ImapMailConfig("imap.example.com", 993, "user", "password")
            val smtpConfig = MailConfig.SmtpMailConfig("smtp.example.com", 587, "user", "password")
            val mailConfigs = Some((imapConfig, smtpConfig))

            for {
                _ <- service.updateUser(userId, true, mailConfigs)
                user <- testUserDao.getUser(userId)
                imapConfigFromDb <- testMailConfigDao.getConfig(userId, ConfigType.Imap.id)
                smtpConfigFromDb <- testMailConfigDao.getConfig(userId, ConfigType.Smtp.id)
            } yield {
                user shouldBe defined
                user.get.isExternalConfig shouldBe true

                imapConfigFromDb shouldBe defined
                imapConfigFromDb.get.host shouldBe "imap.example.com"
                imapConfigFromDb.get.user shouldBe "user"
                imapConfigFromDb.get.password shouldBe "password"

                smtpConfigFromDb shouldBe defined
                smtpConfigFromDb.get.host shouldBe "smtp.example.com"
                smtpConfigFromDb.get.user shouldBe "user"
                smtpConfigFromDb.get.password shouldBe "password"
            }
        }

        "get an existing user by telegramId" in {
            for {
                userOpt <- service.getUser("existingTelegramId")
            } yield {
                userOpt shouldBe defined
                userOpt.get.id shouldBe userId
            }
        }

        "return None for non-existing user" in {
            for {
                userOpt <- service.getUser("nonExistingTelegramId")
            } yield {
                userOpt shouldBe empty
            }
        }

        "get IMAP mail configuration for a user" in {
            for {
                configOpt <- service.getMailImapConfig(userId)
            } yield {
                configOpt shouldBe defined
                configOpt.get.host shouldBe "imap.example.com"
            }
        }

        "get SMTP mail configuration for a user" in {
            for {
                configOpt <- service.getMailSmtpConfig(userId)
            } yield {
                configOpt shouldBe defined
                configOpt.get.host shouldBe "smtp.example.com"
            }
        }
    }
}
