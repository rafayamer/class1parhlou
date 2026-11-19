package pk.parhlou.student.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypts the sign-in tokens with a key kept in the phone's hardware keystore (AES-GCM), so the saved login is useless if
 * the app's files are copied off the phone. Where no keystore exists (unit tests), text is stored as before.
 * Encrypted values start with "k1:"; older plain values are still read, and re-saved encrypted at the next sign-in or refresh.
 */
object TokenCipher {
    private const val ALIAS = "parhlou-session"
    private const val PREFIX = "k1:"

    private fun key(): SecretKey? = runCatching {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build())
            generateKey()
        }
    }.getOrNull()

    fun seal(plain: String): String {
        val k = key() ?: return plain
        return runCatching {
            val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, k) }
            val out = c.iv + c.doFinal(plain.toByteArray())
            PREFIX + Base64.encodeToString(out, Base64.NO_WRAP)
        }.getOrDefault(plain)
    }

    /** The plain text, or null if it can't be decrypted (key gone after a reinstall/restore): the student signs in again. */
    fun open(stored: String): String? {
        if (!stored.startsWith(PREFIX)) return stored
        val k = key() ?: return null
        return runCatching {
            val all = Base64.decode(stored.removePrefix(PREFIX), Base64.NO_WRAP)
            val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, k, GCMParameterSpec(128, all, 0, 12)) }
            String(c.doFinal(all, 12, all.size - 12))
        }.getOrNull()
    }
}
