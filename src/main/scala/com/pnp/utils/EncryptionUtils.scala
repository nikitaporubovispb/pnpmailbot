package com.pnp.utils

import com.pnp.domain.EncryptionConfig

import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.*

class EncryptionUtils(private val encryptionConfig: EncryptionConfig) {
  private val algorithm = "AES/CBC/PKCS5Padding"
  //  private val key = "0123456789abcdef" // 16 bytes key  
  //  private val iv = "abcdef9876543210" // 16 bytes IV

  def encrypt(data: String): String = {
    val cipher = Cipher.getInstance(algorithm)
    val secretKey = SecretKeySpec(encryptionConfig.key.getBytes, "AES")
    val ivSpec = IvParameterSpec(encryptionConfig.iv.getBytes)
    cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivSpec)
    val encryptedData = cipher.doFinal(data.getBytes)
    Base64.getEncoder.encodeToString(encryptedData)
  }

  def decrypt(data: String): String = {
    val cipher = Cipher.getInstance(algorithm)
    val secretKey = SecretKeySpec(encryptionConfig.key.getBytes, "AES")
    val ivSpec = IvParameterSpec(encryptionConfig.iv.getBytes)
    cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec)
    val decodedData = Base64.getDecoder.decode(data)
    String(cipher.doFinal(decodedData))
  }
}
