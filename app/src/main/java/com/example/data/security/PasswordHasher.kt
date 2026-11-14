package com.example.data.security

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object PasswordHasher {
  private const val ITERATIONS = 12_000
  private const val KEY_LENGTH_BITS = 256
  private val random = SecureRandom()

  data class HashedPassword(val hash: String, val salt: String)

  fun hashPassword(password: String): HashedPassword {
    val saltBytes = ByteArray(16)
    random.nextBytes(saltBytes)
    val saltHex = saltBytes.toHex()
    val hashHex = deriveKey(password, saltBytes)
    return HashedPassword(hash = hashHex, salt = saltHex)
  }

  fun verifyPassword(password: String, expectedHash: String, saltHex: String): Boolean {
    if (password.isBlank() || expectedHash.isBlank() || saltHex.isBlank()) return false
    val saltBytes = hexToBytes(saltHex) ?: return false
    val candidate = deriveKey(password, saltBytes)
    return MessageDigest.isEqual(candidate.toByteArray(), expectedHash.toByteArray())
  }

  private fun deriveKey(password: String, salt: ByteArray): String {
    val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH_BITS)
    val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
    val bytes = factory.generateSecret(spec).encoded
    return bytes.toHex()
  }

  private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

  private fun hexToBytes(hex: String): ByteArray? {
    if (hex.length % 2 != 0) return null
    return try {
      ByteArray(hex.length / 2) { i ->
        hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
      }
    } catch (_: NumberFormatException) {
      null
    }
  }
}
