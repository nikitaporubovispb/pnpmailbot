import com.pnp.domain.EncryptionConfig
import com.pnp.utils.EncryptionUtilsImpl
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class CipherImplSpec extends AnyFlatSpec with Matchers {

  val encryptionConfig = EncryptionConfig("0123456789abcdef", "abcdef9876543210")
  val cipher = new EncryptionUtilsImpl(encryptionConfig)

  "Cipher" should "encrypt and decrypt data correctly" in {
    val originalText = "This is a secret message"
    val encryptedText = cipher.encrypt(originalText)
    val decryptedText = cipher.decrypt(encryptedText)

    encryptedText should not equal originalText
    decryptedText should equal(originalText)
  }

  it should "throw an exception for incorrect decryption" in {
    val invalidEncryptedText = "invalidencryptedtext"
    assertThrows[Exception] {
      cipher.decrypt(invalidEncryptedText)
    }
  }
}
