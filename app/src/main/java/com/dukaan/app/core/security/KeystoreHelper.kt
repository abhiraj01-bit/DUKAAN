package com.dukaan.app.core.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Android Keystore-backed encryption helper for sensitive data (API keys, tokens) at rest.
 * Uses AES/GCM/NoPadding with hardware-backed key protection when available.
 */
object KeystoreHelper {
    private const val TAG = "KeystoreHelper"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "dukaan_gemini_credential_key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_IV_LENGTH = 12
    private const val GCM_TAG_LENGTH = 128

    init {
        ensureKeyExists()
    }

    private fun ensureKeyExists() {
        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (!keyStore.containsAlias(KEY_ALIAS)) {
                val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
                val spec = KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
                keyGenerator.init(spec)
                keyGenerator.generateKey()
                Log.d(TAG, "Generated new hardware/keystore AES key for credentials")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Android KeyStore key: ${e.message}")
        }
    }

    private fun getSecretKey(): SecretKey? {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
        } catch (e: Exception) {
            Log.e(TAG, "Failed to retrieve secret key: ${e.message}")
            null
        }
    }

    /**
     * Encrypts plain text string with AES-GCM and returns a Base64 string.
     * Output format: Base64(IV [12 bytes] + CipherTextWithTag).
     */
    fun encrypt(plainText: String): String {
        if (plainText.isBlank()) return ""
        return try {
            val key = getSecretKey() ?: return plainText
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val iv = cipher.iv
            val encryptedBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            val combined = ByteArray(iv.size + encryptedBytes.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(encryptedBytes, 0, combined, iv.size, encryptedBytes.size)
            Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e(TAG, "Encryption failed: ${e.message}")
            plainText // Graceful fallback
        }
    }

    /**
     * Decrypts a Base64 encoded string back to plain text.
     * Handles legacy unencrypted strings transparently.
     */
    fun decrypt(cipherTextBase64: String): String {
        if (cipherTextBase64.isBlank()) return ""
        // Legacy check: if it looks like a raw Google API key (starts with AIzaSy)
        if (cipherTextBase64.startsWith("AIzaSy")) {
            return cipherTextBase64
        }
        return try {
            val key = getSecretKey() ?: return cipherTextBase64
            val combined = Base64.decode(cipherTextBase64, Base64.NO_WRAP)
            if (combined.size < GCM_IV_LENGTH + 1) return cipherTextBase64
            val iv = ByteArray(GCM_IV_LENGTH)
            val cipherBytes = ByteArray(combined.size - GCM_IV_LENGTH)
            System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH)
            System.arraycopy(combined, GCM_IV_LENGTH, cipherBytes, 0, cipherBytes.size)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, key, spec)
            val decryptedBytes = cipher.doFinal(cipherBytes)
            String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            // If decryption fails (e.g. plain text or different key), return raw string
            Log.w(TAG, "Decryption fallback, returning raw string: ${e.message}")
            cipherTextBase64
        }
    }
}

