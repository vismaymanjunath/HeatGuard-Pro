package com.example.appdev.data.ai

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Holds one secret (the user's API key). Never logs or exposes it beyond [get]. */
interface SecretStore {
    /** True while a secret is stored. */
    val hasSecret: StateFlow<Boolean>
    fun get(): String?
    fun put(secret: String)
    fun clear()
}

/**
 * Stores the secret encrypted with an AES-256-GCM key that lives in the Android Keystore and cannot
 * be exported. The encrypted blob sits in a private preferences file that is excluded from backups,
 * because it could not be decrypted on another device anyway.
 */
class KeystoreSecretStore(
    context: Context,
    private val alias: String = "heatguard_ai_key",
) : SecretStore {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
    private val _hasSecret = MutableStateFlow(prefs.contains(ENTRY))
    override val hasSecret: StateFlow<Boolean> = _hasSecret.asStateFlow()

    override fun get(): String? {
        val stored = prefs.getString(ENTRY, null) ?: return null
        return try {
            val raw = Base64.decode(stored, Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORM)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, raw, 0, IV_BYTES))
            String(cipher.doFinal(raw, IV_BYTES, raw.size - IV_BYTES), Charsets.UTF_8)
        } catch (e: Exception) {
            // The key was lost or the data is corrupt (e.g. restored on another device). Start clean.
            clear()
            null
        }
    }

    override fun put(secret: String) {
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val blob = cipher.iv + cipher.doFinal(secret.toByteArray(Charsets.UTF_8))
        prefs.edit { putString(ENTRY, Base64.encodeToString(blob, Base64.NO_WRAP)) }
        _hasSecret.value = true
    }

    override fun clear() {
        prefs.edit { remove(ENTRY) }
        _hasSecret.value = false
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    companion object {
        const val PREFS_FILE = "heatguard_secure"
        private const val ENTRY = "ai_api_key"
        private const val KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORM = "AES/GCM/NoPadding"
        private const val IV_BYTES = 12
        private const val TAG_BITS = 128
    }
}

/** Basic sanity check for a Groq key so a typo is caught before any request is made. */
object ApiKeyFormat {
    private val allowed = Regex("^gsk_[A-Za-z0-9_-]{16,200}$")

    fun normalize(raw: String): String = raw.trim()

    fun isPlausible(raw: String): Boolean = allowed.matches(normalize(raw))
}
