package com.sielo.music.room.crypto

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * End-to-End Encryption (E2EE) engine for private Listen Together rooms.
 * Uses AES-256-GCM with unique 96-bit (12-byte) initialization vectors per message
 * and 128-bit authentication tags.
 */
object RoomCrypto {

    private const val ALGORITHM = "AES"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH = 128
    private const val IV_LENGTH_BYTES = 12
    private const val KEY_SIZE_BITS = 256

    private val secureRandom = SecureRandom()

    /**
     * Derives a deterministic 256-bit AES key from a Room ID for Quick Join simplicity.
     */
    fun deriveKey(roomId: String): String {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        val hash = md.digest((roomId.trim().uppercase() + ":sielo_e2ee_salt_v3").toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(hash, Base64.URL_SAFE or Base64.NO_WRAP)
    }

    /**
     * Generates a cryptographically strong 256-bit AES key, Base64 URL-safe encoded.
     */
    fun generateKey(): String {
        val keyGen = KeyGenerator.getInstance(ALGORITHM)
        keyGen.init(KEY_SIZE_BITS, secureRandom)
        val secretKey: SecretKey = keyGen.generateKey()
        return Base64.encodeToString(secretKey.encoded, Base64.URL_SAFE or Base64.NO_WRAP)
    }

    /**
     * Encrypts plaintext JSON with AES-256-GCM.
     * Returns a composite string: "{ivBase64}:{ciphertextBase64}"
     */
    fun encrypt(plaintext: String, keyBase64: String): String {
        val keyBytes = Base64.decode(keyBase64, Base64.URL_SAFE or Base64.NO_WRAP)
        val keySpec = SecretKeySpec(keyBytes, ALGORITHM)

        val iv = ByteArray(IV_LENGTH_BYTES)
        secureRandom.nextBytes(iv)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        val parameterSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, parameterSpec)

        val cipherBytes = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

        val ivString = Base64.encodeToString(iv, Base64.NO_WRAP)
        val cipherString = Base64.encodeToString(cipherBytes, Base64.NO_WRAP)

        return "$ivString:$cipherString"
    }

    /**
     * Decrypts composite string "{ivBase64}:{ciphertextBase64}" using AES-256-GCM.
     * Returns null if decryption fails (e.g. wrong key, corrupted or tampered message).
     */
    fun decrypt(encryptedComposite: String, keyBase64: String): String? {
        return try {
            val parts = encryptedComposite.split(":")
            if (parts.size != 2) return null

            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val cipherBytes = Base64.decode(parts[1], Base64.NO_WRAP)
            val keyBytes = Base64.decode(keyBase64, Base64.URL_SAFE or Base64.NO_WRAP)

            val keySpec = SecretKeySpec(keyBytes, ALGORITHM)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val parameterSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, keySpec, parameterSpec)

            val decryptedBytes = cipher.doFinal(cipherBytes)
            String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }
}
