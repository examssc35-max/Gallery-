package com.example.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class KeystoreManager {
    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val MASTER_KEY_ALIAS = "CloudGallery_R2_Key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH = 128
    }

    private val keyStore: KeyStore? = try {
        KeyStore.getInstance(ANDROID_KEYSTORE).apply {
            load(null)
        }
    } catch (_: Exception) {
        null
    }

    @Synchronized
    private fun getOrCreateSecretKey(alias: String = MASTER_KEY_ALIAS): SecretKey? {
        val ks = keyStore ?: return null
        if (!ks.containsAlias(alias)) {
            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                ANDROID_KEYSTORE
            )
            val keyGenParameterSpec = KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()

            keyGenerator.init(keyGenParameterSpec)
            return keyGenerator.generateKey()
        }
        val entry = ks.getEntry(alias, null) as? KeyStore.SecretKeyEntry
        return entry?.secretKey
    }

    fun encrypt(plainText: String, alias: String = MASTER_KEY_ALIAS): String {
        if (plainText.isEmpty()) return ""
        try {
            val secretKey = getOrCreateSecretKey(alias)
            if (secretKey == null) {
                // In non-Android / JVM test fallback where AndroidKeyStore is unavailable
                return "fallback:" + Base64.encodeToString(plainText.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            }
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv
            val encryption = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            // Combine IV and encrypted data: [IV length (1 byte)][IV][Encrypted data]
            val combined = ByteArray(1 + iv.size + encryption.size)
            combined[0] = iv.size.toByte()
            System.arraycopy(iv, 0, combined, 1, iv.size)
            System.arraycopy(encryption, 0, combined, 1 + iv.size, encryption.size)
            return Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (_: Exception) {
            return ""
        }
    }

    fun decrypt(encryptedBase64: String, alias: String = MASTER_KEY_ALIAS): String {
        if (encryptedBase64.isEmpty()) return ""
        if (encryptedBase64.startsWith("fallback:")) {
            val raw = encryptedBase64.removePrefix("fallback:")
            return try {
                String(Base64.decode(raw, Base64.NO_WRAP), Charsets.UTF_8)
            } catch (_: Exception) {
                ""
            }
        }
        try {
            val secretKey = getOrCreateSecretKey(alias) ?: return ""
            val combined = Base64.decode(encryptedBase64, Base64.NO_WRAP)
            if (combined.isEmpty()) return ""
            val ivLength = combined[0].toInt()
            val iv = ByteArray(ivLength)
            System.arraycopy(combined, 1, iv, 0, ivLength)
            val encryptedSize = combined.size - 1 - ivLength
            val encryptedBytes = ByteArray(encryptedSize)
            System.arraycopy(combined, 1 + ivLength, encryptedBytes, 0, encryptedSize)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
            val decryptedBytes = cipher.doFinal(encryptedBytes)
            return String(decryptedBytes, Charsets.UTF_8)
        } catch (_: Exception) {
            return ""
        }
    }

    fun clearKey(alias: String = MASTER_KEY_ALIAS) {
        try {
            keyStore?.let { ks ->
                if (ks.containsAlias(alias)) {
                    ks.deleteEntry(alias)
                }
            }
        } catch (_: Exception) {}
    }
}
