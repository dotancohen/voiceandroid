package com.dotancohen.voiceandroid.util

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Wraps a secret the core keeps on disk (Stage 14): the device key, under an
 * AES key that lives in the Android Keystore and never leaves the hardware.
 * The core hands the clear bytes to [wrap] before writing `config.json` and
 * the wrapped bytes to [unwrap] after reading it, and holds the clear key in
 * memory only. The key comes from [keyOf], so the JVM tests use one of their
 * own (TECHNICAL-DECISIONS 6.7).
 */
class SecretWrap(private val keyOf: () -> SecretKey) : uniffi.voicecore.KeystoreWrapper {
    /** The nonce, then the ciphertext with its tag. A fresh nonce every time. */
    override fun wrap(clear: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, keyOf())
        val nonce = cipher.iv
        require(nonce.size == NONCE_BYTES) { "a GCM nonce is $NONCE_BYTES bytes, not ${nonce.size}" }
        return nonce + cipher.doFinal(clear)
    }

    /** The clear bytes, or an exception when the bytes were not wrapped by this key or were changed. */
    override fun unwrap(wrapped: ByteArray): ByteArray {
        require(wrapped.size > NONCE_BYTES) { "too short to be a wrapped secret" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, keyOf(), GCMParameterSpec(TAG_BITS, wrapped, 0, NONCE_BYTES))
        return cipher.doFinal(wrapped, NONCE_BYTES, wrapped.size - NONCE_BYTES)
    }

    companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val NONCE_BYTES = 12
        const val TAG_BITS = 128
        const val KEY_ALIAS = "voice-secrets"

        /** The wrapper over the Keystore key, made on first use and kept by the phone. */
        fun keystore(): SecretWrap = SecretWrap {
            val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            (store.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey ?: run {
                val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
                generator.init(
                    KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(256)
                        .build()
                )
                generator.generateKey()
            }
        }
    }
}
