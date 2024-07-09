import cats.effect.IO
import cats.effect.testing.scalatest.AsyncIOSpec
import com.pnp.dao.*
import com.pnp.domain.*
import com.pnp.service.InteractionService.RegisterResult.*
import com.pnp.service.InteractionServiceImpl
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec

class InteractionServiceImplSpec extends AsyncWordSpec with AsyncIOSpec with Matchers {

    case class TestUserDao(var users: Map[Long, DbUser] = Map.empty) extends UserDao {
        override def getUser(id: Long): IO[Option[DbUser]] =
            IO.pure(users.get(id))

        override def getUserByTelegramId(idTelegram: String): IO[Option[DbUser]] =
            IO.pure(users.values.find(_.telegramId == idTelegram))

        override def createUser(telegramId: String, isExternalConfig: Boolean): IO[Unit] = IO {
            val newUser = DbUser(users.size + 1, telegramId, isExternalConfig)
            users = users.updated(newUser.id, newUser)
        }
    }

    case class TestMailConfigDao(var configs: Map[(Long, Int), DbMailConfig] = Map.empty) extends MailConfigDao {
        override def getConfig(userId: Long, configType: Int): IO[Option[DbMailConfig]] =
            IO.pure(configs.get(userId, configType))

        override def createConfig(mailConfig: DbMailConfig): IO[Unit] = IO {
            configs = configs.updated((mailConfig.userId, mailConfig.configType), mailConfig)
        }
    }

    val testUserDao: TestUserDao = TestUserDao(Map(1L -> DbUser(1L, "existingTelegramId", isExternalConfig = false)))
    val testMailConfigDao: TestMailConfigDao = TestMailConfigDao()

    val service = new InteractionServiceImpl(testUserDao, testMailConfigDao)

    "InteractionServiceImpl" should {

        "return true if the user is registered" in {
            service.isRegistered("existingTelegramId").asserting(_ shouldBe true)
        }

        "return false if the user is not registered" in {
            service.isRegistered("newTelegramId").asserting(_ shouldBe false)
        }

        "register a new user if not already registered" in {
            val telegramId = "newTelegramId"
            val isExternalConfig = false
            for {
                result <- service.register(telegramId, isExternalConfig)
                _ = result shouldBe Ok
                _ = testUserDao.users.values.map(_.telegramId) should contain(telegramId)
            } yield succeed
        }

        "not register a user if already registered" in {
            val telegramId = "existingTelegramId"
            val isExternalConfig = false
            for {
                result <- service.register(telegramId, isExternalConfig)
                _ = result shouldBe AlreadyRegistered
                _ = testUserDao.users.values.map(_.telegramId) should contain (telegramId)
            } yield succeed
        }

        "return user if exists" in {
            val telegramId = "existingTelegramId"
            service.getUser(telegramId).asserting(_ shouldBe Some(DbUser(1, telegramId, false)))
        }

        "return None if not exists" in {
            val telegramId = "nonExistingTelegramId"
            service.getUser(telegramId).asserting(_ shouldBe None)
        }

        "add mail configuration" in {
            val mailConfig = DbMailConfig(1, 1, ConfigType.Imap.id, "imap.example.com", 993, "user", "password")
            for {
                _ <- service.addMailConfig(mailConfig)
                _ = testMailConfigDao.configs should contain((mailConfig.userId, mailConfig.configType) -> mailConfig)
            } yield succeed
        }

        "get mail configuration if exists" in {
            val userId = 1L
            val configType = ConfigType.Imap
            val mailConfig = DbMailConfig(1, userId, configType.id, "imap.example.com", 993, "user", "password")
            val testMailConfigDaoWithConfig = TestMailConfigDao(Map((userId, configType.id) -> mailConfig))
            val serviceWithConfig = new InteractionServiceImpl(testUserDao, testMailConfigDaoWithConfig)
            serviceWithConfig.getMailConfig(userId, configType).asserting(_ shouldBe Some(mailConfig))
        }

        "get None if not exists" in {
            val userId = 1L
            val configType = ConfigType.Imap
            val testMailConfigDaoWithConfig = TestMailConfigDao(Map.empty)
            val serviceWithConfig = new InteractionServiceImpl(testUserDao, testMailConfigDaoWithConfig)
            serviceWithConfig.getMailConfig(userId, configType).asserting(_ shouldBe None)
        }
    }
}
