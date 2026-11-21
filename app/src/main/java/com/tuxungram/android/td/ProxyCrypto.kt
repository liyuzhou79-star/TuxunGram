package com.tuxungram.android.td

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * At-rest encryption for proxy credentials (v0.9.1, audit K2).
 *
 * Up to v0.9.0 the SOCKS5 password and the MTProto secret were
 * persisted as plaintext inside a MODE_PRIVATE SharedPreferences
 * file. They are now encrypted with AES-256-GCM under a key
 * that lives in the Android Keystore and never leaves it —
 * framework APIs only, no new dependency. The stored form is
 * "v1:" + base64([iv length][iv][ciphertext]); anything without
 * the prefix is a legacy plaintext value from an older version
 * and is read as-is, then rewritten encrypted on the next
 * persist (see ProxyStore), so existing users migrate without
 * re-entering anything.
 *
 * Failure policy: if encryption ever fails (a broken keystore
 * is the only realistic cause), the plaintext is returned so
 * the proxy keeps working and the migration retries on the
 * next launch; if decryption fails (key invalidated), the
 * field reads as empty and the user re-enters it — neither
 * path may crash.
 */
object ProxyCrypto {
    private const val KEYSTORE = "AndroidKeyStore"
    private const val ALIAS = "tuxungram_proxy_secrets"
    private const val PREFIX = "v1:"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    fun isEncrypted(stored: String): Boolean = stored.startsWith(PREFIX)

    @Synchronized
    private fun key(): SecretKey {
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator =
            KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    fun encrypt(plain: String): String {
        if (plain.isEmpty()) return ""
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key())
            val iv = cipher.iv
            val ct = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
            val packed = ByteArray(1 + iv.size + ct.size)
            packed[0] = iv.size.toByte()
            iv.copyInto(packed, 1)
            ct.copyInto(packed, 1 + iv.size)
            PREFIX + Base64.encodeToString(packed, Base64.NO_WRAP)
        } catch (t: Throwable) {
            plain
        }
    }

    fun decrypt(stored: String): String {
        if (stored.isEmpty() || !isEncrypted(stored)) return stored
        return try {
            val packed = Base64.decode(
                stored.substring(PREFIX.length), Base64.NO_WRAP
            )
            val ivLength = packed[0].toInt()
            val iv = packed.copyOfRange(1, 1 + ivLength)
            val ct = packed.copyOfRange(1 + ivLength, packed.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv)
            )
            String(cipher.doFinal(ct), Charsets.UTF_8)
        } catch (t: Throwable) {
            ""
        }
    }
}
