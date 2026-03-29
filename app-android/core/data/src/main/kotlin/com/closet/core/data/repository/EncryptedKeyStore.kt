package com.closet.core.data.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "EncryptedKeyStore"
private const val PREFS_FILE = "ai_keys_encrypted"
private const val KEY_OPENAI = "openai_api_key"
private const val KEY_ANTHROPIC = "anthropic_api_key"

/**
 * Secure storage for AI provider API keys, backed by [EncryptedSharedPreferences].
 *
 * Keys are encrypted at rest using AES-256-GCM (values) and AES-256-SIV (key names),
 * both backed by the Android Keystore — satisfying the on-device credential protection
 * requirement without a server round-trip or user-managed passphrase.
 *
 * Non-sensitive AI preferences (selected provider, model names, Nano ready flag,
 * token limit, style vibe) remain in the plain DataStore managed by
 * [AiPreferencesRepository].
 *
 * ### Keystore corruption recovery
 * [EncryptedSharedPreferences] can fail to open if the Keystore entry is invalidated
 * (e.g. after a device factory reset or a security policy change that removes the
 * Android Keystore). In that case the encrypted file is deleted and re-created so the
 * app remains functional — the user will need to re-enter their keys.
 *
 * ### Reactive reads
 * Each key is mirrored in a [MutableStateFlow] so [AiPreferencesRepository] can
 * expose `Flow<String>` observables without polling the file on every emission.
 * Writes update both the encrypted file and the in-memory flow atomically.
 */
@Singleton
class EncryptedKeyStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val prefs: SharedPreferences = openOrRecreate()

    private val _openAiKey = MutableStateFlow(prefs.getString(KEY_OPENAI, "") ?: "")
    private val _anthropicKey = MutableStateFlow(prefs.getString(KEY_ANTHROPIC, "") ?: "")

    /** A [Flow] of the stored OpenAI-compatible API key. Emits immediately on collection. */
    fun openAiKeyFlow(): Flow<String> = _openAiKey.asStateFlow()

    /** A [Flow] of the stored Anthropic API key. Emits immediately on collection. */
    fun anthropicKeyFlow(): Flow<String> = _anthropicKey.asStateFlow()

    /** Persists [key] for the OpenAI-compatible provider and updates the in-memory flow. */
    suspend fun setOpenAiKey(key: String) = withContext(Dispatchers.IO) {
        prefs.edit().putString(KEY_OPENAI, key).apply()
        _openAiKey.value = key
    }

    /** Persists [key] for the Anthropic provider and updates the in-memory flow. */
    suspend fun setAnthropicKey(key: String) = withContext(Dispatchers.IO) {
        prefs.edit().putString(KEY_ANTHROPIC, key).apply()
        _anthropicKey.value = key
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun openOrRecreate(): SharedPreferences {
        return try {
            createEncryptedPrefs()
        } catch (e: Exception) {
            Timber.tag(TAG).e(
                e,
                "EncryptedSharedPreferences failed to open — Keystore may be corrupted. " +
                    "Deleting and recreating (stored keys will be lost).",
            )
            context.deleteSharedPreferences(PREFS_FILE)
            try {
                createEncryptedPrefs()
            } catch (retryException: Exception) {
                // Second failure is unrecoverable — rethrow so Hilt surfaces it at
                // injection time rather than silently swallowing credentials.
                throw IllegalStateException(
                    "Cannot initialise EncryptedSharedPreferences after recovery attempt",
                    retryException,
                )
            }
        }
    }

    private fun createEncryptedPrefs(): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }
}
