package com.example.meshchat.crypto

import android.util.Base64
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Utility object for AES encryption of chat messages.
 * Uses AES-CTR mode for same-size output (no padding overhead).
 * Key is derived from the sorted pair of device IDs.
 */
object CryptoHelper {

    /**
     * Derives a shared AES key from two device IDs.
     * Both devices will compute the same key independently.
     */
    fun deriveKey(myId: Short, theirId: Short): SecretKey {
        val sortedIds = listOf(myId, theirId).sorted()
        val combined = "${sortedIds[0]}:${sortedIds[1]}"
        val hash = MessageDigest.getInstance("SHA-256").digest(combined.toByteArray())
        // Use first 16 bytes for AES-128
        return SecretKeySpec(hash.copyOfRange(0, 16), "AES")
    }

    /**
     * Encrypts plaintext using AES-CTR mode.
     * @param plaintext The message to encrypt
     * @param key The shared secret key
     * @param nonce A unique value per message (messageId works well)
     * @return Base64-encoded ciphertext
     */
    fun encrypt(plaintext: String, key: SecretKey, nonce: Short): String {
        val cipher = Cipher.getInstance("AES/CTR/NoPadding")
        val iv = createIv(nonce)
        cipher.init(Cipher.ENCRYPT_MODE, key, IvParameterSpec(iv))
        val encrypted = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(encrypted, Base64.NO_WRAP)
    }

    /**
     * Decrypts ciphertext using AES-CTR mode.
     * @param ciphertext Base64-encoded ciphertext
     * @param key The shared secret key
     * @param nonce The same nonce used for encryption
     * @return The original plaintext
     */
    fun decrypt(ciphertext: String, key: SecretKey, nonce: Short): String {
        val cipher = Cipher.getInstance("AES/CTR/NoPadding")
        val iv = createIv(nonce)
        cipher.init(Cipher.DECRYPT_MODE, key, IvParameterSpec(iv))
        val decoded = Base64.decode(ciphertext, Base64.NO_WRAP)
        val decrypted = cipher.doFinal(decoded)
        return String(decrypted, Charsets.UTF_8)
    }

    /**
     * Creates a 16-byte IV from a short nonce.
     * The nonce (messageId) ensures each message uses a unique IV.
     */
    private fun createIv(nonce: Short): ByteArray {
        val iv = ByteArray(16)
        iv[0] = (nonce.toInt() shr 8).toByte()
        iv[1] = nonce.toByte()
        return iv
    }
}
