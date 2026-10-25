package com.example.utils

import android.util.Base64
import org.json.JSONObject
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object BackupEncryptionUtil {

    private const val ALGORITHM = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH_BITS = 128
    private const val IV_LENGTH_BYTES = 12
    private const val SALT_LENGTH_BYTES = 16
    // Upgraded to NIST/OWASP standard (120,000 iterations for PBKDF2-HMAC-SHA256)
    private const val PBKDF2_ITERATIONS = 120_000
    private const val LEGACY_PBKDF2_ITERATIONS = 10_000
    private const val KEY_LENGTH_BITS = 256

    private val secureRandom = SecureRandom()

    private fun deriveKey(passphrase: String, salt: ByteArray, iterations: Int = PBKDF2_ITERATIONS): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, iterations, KEY_LENGTH_BITS)
        val keyBytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }

    /**
     * Encrypts plaintext JSON string using AES-256-GCM with 120,000 PBKDF2 iterations.
     */
    fun encrypt(plainText: String, passphrase: String): String {
        val salt = ByteArray(SALT_LENGTH_BYTES).also { secureRandom.nextBytes(it) }
        val iv = ByteArray(IV_LENGTH_BYTES).also { secureRandom.nextBytes(it) }
        val key = deriveKey(passphrase, salt, PBKDF2_ITERATIONS)

        val cipher = Cipher.getInstance(ALGORITHM)
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.ENCRYPT_MODE, key, gcmSpec)

        val cipherBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

        val envelope = JSONObject().apply {
            put("application", "Yosan Finance")
            put("encrypted", true)
            put("version", 2)
            put("algorithm", "AES-256-GCM")
            put("iterations", PBKDF2_ITERATIONS)
            put("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            put("iv", Base64.encodeToString(iv, Base64.NO_WRAP))
            put("ciphertext", Base64.encodeToString(cipherBytes, Base64.NO_WRAP))
        }
        return envelope.toString()
    }

    /**
     * Decrypts ciphertext envelope JSON string using AES-256-GCM.
     * Backwards-compatible with legacy 10,000-iteration backups.
     */
    fun decrypt(inputJson: String, passphrase: String?): String {
        val clean = inputJson.trim().removePrefix("﻿")
        if (!clean.startsWith("{")) return clean

        return try {
            val root = JSONObject(clean)
            if (!root.optBoolean("encrypted", false)) {
                return clean
            }

            if (passphrase.isNullOrBlank()) {
                throw IllegalArgumentException("Passphrase required to decrypt encrypted backup")
            }

            val salt = Base64.decode(root.getString("salt"), Base64.NO_WRAP)
            val iv = Base64.decode(root.getString("iv"), Base64.NO_WRAP)
            val cipherBytes = Base64.decode(root.getString("ciphertext"), Base64.NO_WRAP)
            val configuredIterations = root.optInt("iterations", if (root.optInt("version", 1) >= 2) PBKDF2_ITERATIONS else LEGACY_PBKDF2_ITERATIONS)

            val plainBytes = try {
                val key = deriveKey(passphrase, salt, configuredIterations)
                val cipher = Cipher.getInstance(ALGORITHM)
                val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
                cipher.init(Cipher.DECRYPT_MODE, key, gcmSpec)
                cipher.doFinal(cipherBytes)
            } catch (e: Exception) {
                // If standard decryption failed and iterations differed, try legacy 10,000 iterations
                if (configuredIterations != LEGACY_PBKDF2_ITERATIONS) {
                    val fallbackKey = deriveKey(passphrase, salt, LEGACY_PBKDF2_ITERATIONS)
                    val cipher = Cipher.getInstance(ALGORITHM)
                    val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
                    cipher.init(Cipher.DECRYPT_MODE, fallbackKey, gcmSpec)
                    cipher.doFinal(cipherBytes)
                } else {
                    throw e
                }
            }

            String(plainBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            if (clean.contains("\"encrypted\":true") || clean.contains("\"encrypted\": true")) {
                throw e
            }
            clean
        }
    }

    fun isEncrypted(jsonString: String): Boolean {
        val clean = jsonString.trim().removePrefix("\uFEFF")
        if (!clean.startsWith("{")) return false
        return try {
            JSONObject(clean).optBoolean("encrypted", false)
        } catch (_: Exception) {
            false
        }
    }
}
