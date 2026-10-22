package com.example.offlinechat

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object Crypto {
    // Password se group ka secret service ID banta hai: alag password = alag group, dikhega hi nahi
    fun serviceId(password: String): String {
        val h = MessageDigest.getInstance("SHA-256").digest(("svc|$password").toByteArray())
        return "oc" + h.joinToString("") { "%02x".format(it) }.take(24)
    }

    // Password se AES key (messages encrypt karne ke liye)
    fun key(password: String): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), "OfflineChatSalt-v1".toByteArray(), 20000, 256)
        val k = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).encoded
        return SecretKeySpec(k, "AES")
    }

    fun encrypt(key: SecretKeySpec, plain: ByteArray): ByteArray {
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        return iv + c.doFinal(plain)
    }

    // Galat password ho ya data badla ho to null
    fun decrypt(key: SecretKeySpec, data: ByteArray): ByteArray? = try {
        if (data.size < 13) null else {
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, data.copyOfRange(0, 12)))
            c.doFinal(data, 12, data.size - 12)
        }
    } catch (e: Exception) { null }
}
