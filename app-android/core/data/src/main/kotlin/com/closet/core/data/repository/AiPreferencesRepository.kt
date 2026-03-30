package com.closet.core.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.closet.core.data.model.AiProvider
import com.closet.core.data.model.StyleVibe
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import timber.log.Timber

private val Context.aiDataStore: DataStore<Preferences> by preferencesDataStore(name = "ai_prefs")

/**
 * Repository for persisting AI provider preferences.
 *
 * **Storage split:**
 * - **API keys** (OpenAI, Anthropic) — stored encrypted via [EncryptedKeyStore], backed by
 *   AES-256-GCM/Android Keystore. Never written to DataStore.
 * - **Everything else** (provider selection, model names, Nano ready flag, token limit,
 *   style vibe, master AI toggle) — stored in plain DataStore (`ai_prefs`). These values
 *   are non-sensitive configuration, not credentials.
 *
 * Provided as a [Singleton] — inject anywhere via Hilt.
 */
class AiPreferencesRepository(
    @ApplicationContext private val context: Context,
    private val encryptedKeyStore: EncryptedKeyStore,
) {
    // Non-sensitive DataStore keys (no API keys here)
    private val aiEnabledKey = booleanPreferencesKey("ai_enabled")
    private val selectedProviderKey = stringPreferencesKey("ai_selected_provider")
    private val openAiBaseUrlKey = stringPreferencesKey("ai_openai_base_url")
    private val openAiModelKey = stringPreferencesKey("ai_openai_model")
    private val anthropicModelKey = stringPreferencesKey("ai_anthropic_model")
    private val nanoAiReadyKey = booleanPreferencesKey("ai_nano_ready")
    private val nanoTokenLimitKey = intPreferencesKey("ai_nano_token_limit")
    private val styleVibeKey = stringPreferencesKey("ai_style_vibe")

    // ── Master AI toggle ─────────────────────────────────────────────────────

    /**
     * Whether AI coherence scoring is enabled at all. Independent of [getAiReady] —
     * a user can have AI enabled with an OpenAI key without Nano ever being downloaded.
     * Defaults to false (off by default).
     */
    fun getAiEnabled(): Flow<Boolean> = context.aiDataStore.data.map { prefs ->
        prefs[aiEnabledKey] ?: false
    }

    suspend fun setAiEnabled(enabled: Boolean) {
        context.aiDataStore.edit { prefs ->
            prefs[aiEnabledKey] = enabled
        }
    }

    // ── Provider selection ───────────────────────────────────────────────────

    /**
     * The currently selected [AiProvider]. Defaults to [AiProvider.Nano] (on-device).
     */
    fun getSelectedProvider(): Flow<AiProvider> = context.aiDataStore.data.map { prefs ->
        AiProvider.fromString(prefs[selectedProviderKey] ?: AiProvider.Nano.name)
    }

    suspend fun setSelectedProvider(provider: AiProvider) {
        context.aiDataStore.edit { prefs ->
            prefs[selectedProviderKey] = provider.name
        }
    }

    // ── API keys (encrypted) ─────────────────────────────────────────────────

    /**
     * API key for OpenAI-compatible providers (OpenAI, Gemini cloud, Ollama, Groq, etc.).
     * Stored encrypted via [EncryptedKeyStore]. Returns an empty string when not set.
     */
    fun getOpenAiApiKey(): Flow<String> = encryptedKeyStore.openAiKeyFlow()

    suspend fun setOpenAiApiKey(key: String) = encryptedKeyStore.setOpenAiKey(key)

    /**
     * API key for the Anthropic provider (Claude).
     * Stored encrypted via [EncryptedKeyStore]. Returns an empty string when not set.
     */
    fun getAnthropicApiKey(): Flow<String> = encryptedKeyStore.anthropicKeyFlow()

    suspend fun setAnthropicApiKey(key: String) = encryptedKeyStore.setAnthropicKey(key)

    // ── OpenAI-compatible non-sensitive config ────────────────────────────────

    /**
     * Base URL for OpenAI-compatible providers. Defaults to `https://api.openai.com` when not set.
     *
     * Override to point at a self-hosted Ollama instance, Groq, Gemini cloud, or any other
     * OpenAI-compatible endpoint. The provider normalizes trailing slashes before use.
     */
    fun getOpenAiBaseUrl(): Flow<String> = context.aiDataStore.data.map { prefs ->
        prefs[openAiBaseUrlKey] ?: ""
    }

    suspend fun setOpenAiBaseUrl(url: String) {
        context.aiDataStore.edit { prefs ->
            prefs[openAiBaseUrlKey] = url
        }
    }

    /**
     * Model identifier for the OpenAI-compatible provider. Defaults to `gpt-4o-mini` when not set.
     */
    fun getOpenAiModel(): Flow<String> = context.aiDataStore.data.map { prefs ->
        prefs[openAiModelKey] ?: ""
    }

    suspend fun setOpenAiModel(model: String) {
        context.aiDataStore.edit { prefs ->
            prefs[openAiModelKey] = model
        }
    }

    // ── Anthropic non-sensitive config ────────────────────────────────────────

    /**
     * Model identifier for the Anthropic provider. Defaults to Haiku when not set.
     */
    fun getAnthropicModel(): Flow<String> = context.aiDataStore.data.map { prefs ->
        prefs[anthropicModelKey] ?: ""
    }

    suspend fun setAnthropicModel(model: String) {
        context.aiDataStore.edit { prefs ->
            prefs[anthropicModelKey] = model
        }
    }

    // ── Nano — ready flag ────────────────────────────────────────────────────

    /**
     * Whether Gemini Nano is downloaded and ready for inference on this device.
     */
    fun getAiReady(): Flow<Boolean> = context.aiDataStore.data.map { prefs ->
        prefs[nanoAiReadyKey] ?: false
    }

    suspend fun setAiReady(ready: Boolean) {
        context.aiDataStore.edit { prefs ->
            prefs[nanoAiReadyKey] = ready
        }
    }

    // ── Nano — token limit ───────────────────────────────────────────────────

    /**
     * The maximum token count supported by the on-device Nano model.
     * Returns 0 when not yet populated (i.e. [getAiReady] is false).
     * Never hardcode a token limit — always read from DataStore.
     */
    fun getTokenLimit(): Flow<Int> = context.aiDataStore.data.map { prefs ->
        prefs[nanoTokenLimitKey] ?: 0
    }

    suspend fun setTokenLimit(limit: Int) {
        context.aiDataStore.edit { prefs ->
            prefs[nanoTokenLimitKey] = limit
        }
    }

    // ── Style vibe ───────────────────────────────────────────────────────────

    /**
     * The user's preferred style aesthetic. Defaults to [StyleVibe.SmartCasual] when not set.
     */
    fun getStyleVibe(): Flow<StyleVibe> = context.aiDataStore.data.map { prefs ->
        StyleVibe.fromString(prefs[styleVibeKey] ?: StyleVibe.SmartCasual.name)
    }

    suspend fun setStyleVibe(vibe: StyleVibe) {
        context.aiDataStore.edit { prefs -> prefs[styleVibeKey] = vibe.name }
    }

    // ── Composite helpers ────────────────────────────────────────────────────

    /**
     * One-time migration: moves API keys previously stored as plain strings in DataStore
     * (before encryption was introduced) into [EncryptedKeyStore], then removes them from
     * DataStore. Safe to call on every app start — if the legacy DataStore keys are absent
     * (migration already ran, or keys were never set), this is a no-op.
     */
    suspend fun migrateKeysFromPlainDataStore() {
        val legacyOpenAiKey = stringPreferencesKey("ai_openai_api_key")
        val legacyAnthropicKey = stringPreferencesKey("ai_anthropic_api_key")

        val snapshot = context.aiDataStore.data.first()
        val oldOpenAiKey = snapshot[legacyOpenAiKey] ?: ""
        val oldAnthropicKey = snapshot[legacyAnthropicKey] ?: ""

        if (oldOpenAiKey.isBlank() && oldAnthropicKey.isBlank()) return

        // Only backfill when the encrypted slot is currently empty — avoids overwriting a
        // key the user may have set in the new encrypted store after a partial migration.
        val currentOpenAiKey = encryptedKeyStore.openAiKeyFlow().first()
        val currentAnthropicKey = encryptedKeyStore.anthropicKeyFlow().first()

        val openAiMigrated = oldOpenAiKey.isNotBlank() && currentOpenAiKey.isBlank() &&
            encryptedKeyStore.setOpenAiKey(oldOpenAiKey)
        val anthropicMigrated = oldAnthropicKey.isNotBlank() && currentAnthropicKey.isBlank() &&
            encryptedKeyStore.setAnthropicKey(oldAnthropicKey)

        // Only remove the plaintext entry when the encrypted write succeeded.
        context.aiDataStore.edit { prefs ->
            if (openAiMigrated) prefs.remove(legacyOpenAiKey)
            if (anthropicMigrated) prefs.remove(legacyAnthropicKey)
        }

        val migratedCount = listOf(openAiMigrated, anthropicMigrated).count { it }
        if (migratedCount > 0) {
            Timber.tag("AiPrefsRepo").i(
                "Migrated %d key(s) from plain DataStore to EncryptedKeyStore",
                migratedCount,
            )
        }
    }

    /**
     * Clears the Nano ready flag and token limit.
     * Called when the AI toggle is turned off or model init fails.
     */
    suspend fun clearNanoState() {
        context.aiDataStore.edit { prefs ->
            prefs[nanoAiReadyKey] = false
            prefs.remove(nanoTokenLimitKey)
        }
    }
}
