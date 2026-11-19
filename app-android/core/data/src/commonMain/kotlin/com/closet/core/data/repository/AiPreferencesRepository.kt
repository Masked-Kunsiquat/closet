package com.closet.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.closet.core.data.ai.NanoAvailability
import com.closet.core.data.model.AiProvider
import com.closet.core.data.model.StyleVibe
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import co.touchlab.kermit.Logger

/**
 * Repository for persisting AI provider preferences.
 *
 * **Storage split:**
 * - **API keys** (OpenAI, Anthropic, Gemini) — stored via [SecretStore] (on Android,
 *   `EncryptedKeyStore`: AES-256-GCM/Android Keystore). Never written to DataStore.
 * - **Everything else** (provider selection, model names, Nano ready flag, token limit,
 *   style vibe, master AI toggle) — stored in plain DataStore (`ai_prefs`). These values
 *   are non-sensitive configuration, not credentials.
 *
 * Provided as a Koin `single`. [aiDataStore] is the one `ai_prefs` DataStore instance, the
 * Koin single qualified `named(AI_PREFS_NAME)`.
 */
class AiPreferencesRepository(
    private val aiDataStore: DataStore<Preferences>,
    private val secretStore: SecretStore,
) {
    // Non-sensitive DataStore keys (no API keys here)
    private val aiEnabledKey = booleanPreferencesKey("ai_enabled")
    private val selectedProviderKey = stringPreferencesKey("ai_selected_provider")
    private val openAiBaseUrlKey = stringPreferencesKey("ai_openai_base_url")
    private val openAiModelKey = stringPreferencesKey("ai_openai_model")
    private val anthropicModelKey = stringPreferencesKey("ai_anthropic_model")
    private val geminiModelKey = stringPreferencesKey("ai_gemini_model")
    private val nanoAiReadyKey = booleanPreferencesKey("ai_nano_ready")
    private val nanoAvailabilityKey = stringPreferencesKey("ai_nano_availability")
    private val nanoTokenLimitKey = intPreferencesKey("ai_nano_token_limit")
    private val styleVibeKey = stringPreferencesKey("ai_style_vibe")
    private val tasteWeightsKey = stringPreferencesKey("taste_weights")
    private val tasteWeightsWatermarkKey = stringPreferencesKey("taste_weights_watermark")

    // ── Master AI toggle ─────────────────────────────────────────────────────

    /**
     * Whether AI coherence scoring is enabled at all. Independent of [getAiReady] —
     * a user can have AI enabled with an OpenAI key without Nano ever being downloaded.
     * Defaults to false (off by default).
     */
    fun getAiEnabled(): Flow<Boolean> = aiDataStore.data.map { prefs ->
        prefs[aiEnabledKey] ?: false
    }

    suspend fun setAiEnabled(enabled: Boolean) {
        aiDataStore.edit { prefs ->
            prefs[aiEnabledKey] = enabled
        }
    }

    // ── Provider selection ───────────────────────────────────────────────────

    /**
     * The currently selected [AiProvider]. Defaults to [AiProvider.Nano] (on-device).
     */
    fun getSelectedProvider(): Flow<AiProvider> = aiDataStore.data.map { prefs ->
        AiProvider.fromString(prefs[selectedProviderKey] ?: AiProvider.Nano.name)
    }

    suspend fun setSelectedProvider(provider: AiProvider) {
        aiDataStore.edit { prefs ->
            prefs[selectedProviderKey] = provider.name
        }
    }

    // ── API keys (encrypted) ─────────────────────────────────────────────────

    /**
     * API key for OpenAI-compatible providers (OpenAI, Gemini cloud, Ollama, Groq, etc.).
     * Stored encrypted via [SecretStore]. Returns an empty string when not set.
     */
    fun getOpenAiApiKey(): Flow<String> = secretStore.openAiKeyFlow()

    suspend fun setOpenAiApiKey(key: String) = secretStore.setOpenAiKey(key)

    /**
     * API key for the Anthropic provider (Claude).
     * Stored encrypted via [SecretStore]. Returns an empty string when not set.
     */
    fun getAnthropicApiKey(): Flow<String> = secretStore.anthropicKeyFlow()

    suspend fun setAnthropicApiKey(key: String) = secretStore.setAnthropicKey(key)

    /**
     * API key for the Gemini provider (Google Gemini native API).
     * Stored encrypted via [SecretStore]. Returns an empty string when not set.
     */
    fun getGeminiApiKey(): Flow<String> = secretStore.geminiKeyFlow()

    suspend fun setGeminiApiKey(key: String) = secretStore.setGeminiKey(key)

    // ── OpenAI-compatible non-sensitive config ────────────────────────────────

    /**
     * Base URL for OpenAI-compatible providers. Defaults to `https://api.openai.com` when not set.
     *
     * Override to point at a self-hosted Ollama instance, Groq, Gemini cloud, or any other
     * OpenAI-compatible endpoint. The provider normalizes trailing slashes before use.
     */
    fun getOpenAiBaseUrl(): Flow<String> = aiDataStore.data.map { prefs ->
        prefs[openAiBaseUrlKey] ?: ""
    }

    suspend fun setOpenAiBaseUrl(url: String) {
        aiDataStore.edit { prefs ->
            prefs[openAiBaseUrlKey] = url
        }
    }

    /**
     * Model identifier for the OpenAI-compatible provider. Defaults to `gpt-4o-mini` when not set.
     */
    fun getOpenAiModel(): Flow<String> = aiDataStore.data.map { prefs ->
        prefs[openAiModelKey] ?: ""
    }

    suspend fun setOpenAiModel(model: String) {
        aiDataStore.edit { prefs ->
            prefs[openAiModelKey] = model
        }
    }

    // ── Anthropic non-sensitive config ────────────────────────────────────────

    /**
     * Model identifier for the Anthropic provider. Defaults to Haiku when not set.
     */
    fun getAnthropicModel(): Flow<String> = aiDataStore.data.map { prefs ->
        prefs[anthropicModelKey] ?: ""
    }

    suspend fun setAnthropicModel(model: String) {
        aiDataStore.edit { prefs ->
            prefs[anthropicModelKey] = model
        }
    }

    // ── Gemini non-sensitive config ───────────────────────────────────────────

    /**
     * Model identifier for the Gemini provider. Defaults to `gemini-2.0-flash-lite` when not set.
     */
    fun getGeminiModel(): Flow<String> = aiDataStore.data.map { prefs ->
        prefs[geminiModelKey] ?: "gemini-2.0-flash-lite"
    }

    suspend fun setGeminiModel(model: String) {
        aiDataStore.edit { prefs ->
            prefs[geminiModelKey] = model
        }
    }

    // ── Nano — ready flag ────────────────────────────────────────────────────

    /**
     * Whether Gemini Nano is downloaded and ready for inference on this device.
     */
    fun getAiReady(): Flow<Boolean> = aiDataStore.data.map { prefs ->
        prefs[nanoAiReadyKey] ?: false
    }

    suspend fun setAiReady(ready: Boolean) {
        aiDataStore.edit { prefs ->
            prefs[nanoAiReadyKey] = ready
        }
    }

    // ── Nano — availability ──────────────────────────────────────────────────

    /**
     * The outcome of the last Nano init, written alongside [setAiReady] (which existing callers
     * still read). [NanoAvailability.UNKNOWN] until an init has run.
     */
    fun getNanoAvailability(): Flow<NanoAvailability> = aiDataStore.data.map { prefs ->
        prefs[nanoAvailabilityKey]?.let { stored -> NanoAvailability.entries.firstOrNull { it.name == stored } }
            ?: NanoAvailability.UNKNOWN
    }

    suspend fun setNanoAvailability(availability: NanoAvailability) {
        aiDataStore.edit { prefs ->
            prefs[nanoAvailabilityKey] = availability.name
        }
    }

    // ── Nano — token limit ───────────────────────────────────────────────────

    /**
     * The maximum token count supported by the on-device Nano model.
     * Returns 0 when not yet populated (i.e. [getAiReady] is false).
     * Never hardcode a token limit — always read from DataStore.
     */
    fun getTokenLimit(): Flow<Int> = aiDataStore.data.map { prefs ->
        prefs[nanoTokenLimitKey] ?: 0
    }

    suspend fun setTokenLimit(limit: Int) {
        aiDataStore.edit { prefs ->
            prefs[nanoTokenLimitKey] = limit
        }
    }

    // ── Style vibe ───────────────────────────────────────────────────────────

    /**
     * The user's preferred style aesthetic. Defaults to [StyleVibe.SmartCasual] when not set.
     */
    fun getStyleVibe(): Flow<StyleVibe> = aiDataStore.data.map { prefs ->
        StyleVibe.fromString(prefs[styleVibeKey] ?: StyleVibe.SmartCasual.name)
    }

    suspend fun setStyleVibe(vibe: StyleVibe) {
        aiDataStore.edit { prefs -> prefs[styleVibeKey] = vibe.name }
    }

    // ── Learned taste weights (Engine v2, #114) ──────────────────────────────

    /**
     * Serialized learned taste weights string (`"w0,w1,...,w7"`), or `null` when not yet trained.
     * Catches DataStore I/O errors and emits `null` so callers fall back to default weights.
     */
    fun getTasteWeightsRaw(): Flow<String?> = aiDataStore.data
        .map { prefs -> prefs[tasteWeightsKey] }
        .catch { e ->
            if (e is CancellationException) throw e
            Logger.withTag("AiPrefsRepo").w(e) { "Failed to read taste_weights; falling back to null" }
            emit(null)
        }

    /**
     * Watermark of the wear log history on which [getTasteWeightsRaw] was last trained
     * (format: `"v1:$totalLogCount:$maxLogId"`), or `null` when not yet trained.
     */
    fun getTasteWeightsWatermark(): Flow<String?> = aiDataStore.data
        .map { prefs -> prefs[tasteWeightsWatermarkKey] }
        .catch { e ->
            if (e is CancellationException) throw e
            Logger.withTag("AiPrefsRepo").w(e) { "Failed to read taste_weights_watermark; falling back to null" }
            emit(null)
        }

    /**
     * Atomically persists the serialized [rawWeights] and [watermark] to DataStore.
     */
    suspend fun setTasteWeights(rawWeights: String, watermark: String) {
        try {
            aiDataStore.edit { prefs ->
                prefs[tasteWeightsKey] = rawWeights
                prefs[tasteWeightsWatermarkKey] = watermark
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.withTag("AiPrefsRepo").w(e) { "Failed to persist taste_weights" }
        }
    }

    // ── Composite helpers ────────────────────────────────────────────────────

    /**
     * One-time migration: moves API keys previously stored as plain strings in DataStore
     * (before encryption was introduced) into [SecretStore], then removes them from
     * DataStore. Safe to call on every app start — if the legacy DataStore keys are absent
     * (migration already ran, or keys were never set), this is a no-op.
     */
    suspend fun migrateKeysFromPlainDataStore() {
        val legacyOpenAiKey = stringPreferencesKey("ai_openai_api_key")
        val legacyAnthropicKey = stringPreferencesKey("ai_anthropic_api_key")

        val snapshot = aiDataStore.data.first()
        val oldOpenAiKey = snapshot[legacyOpenAiKey] ?: ""
        val oldAnthropicKey = snapshot[legacyAnthropicKey] ?: ""

        if (oldOpenAiKey.isBlank() && oldAnthropicKey.isBlank()) return

        // Only backfill when the encrypted slot is currently empty — avoids overwriting a
        // key the user may have set in the new encrypted store after a partial migration.
        val currentOpenAiKey = secretStore.openAiKeyFlow().first()
        val currentAnthropicKey = secretStore.anthropicKeyFlow().first()

        val openAiMigrated = oldOpenAiKey.isNotBlank() && currentOpenAiKey.isBlank() &&
            secretStore.setOpenAiKey(oldOpenAiKey)
        val anthropicMigrated = oldAnthropicKey.isNotBlank() && currentAnthropicKey.isBlank() &&
            secretStore.setAnthropicKey(oldAnthropicKey)

        // Remove the plaintext entry when the encrypted slot is populated — either because
        // we just wrote it this run OR it was already populated by an earlier migration run.
        // This cleans up stale plaintext left behind when a previous run skipped writing
        // (encrypted was non-blank) but forgot to delete the legacy key.
        val shouldRemoveOpenAi = oldOpenAiKey.isNotBlank() && (currentOpenAiKey.isNotBlank() || openAiMigrated)
        val shouldRemoveAnthropic = oldAnthropicKey.isNotBlank() && (currentAnthropicKey.isNotBlank() || anthropicMigrated)
        aiDataStore.edit { prefs ->
            if (shouldRemoveOpenAi) prefs.remove(legacyOpenAiKey)
            if (shouldRemoveAnthropic) prefs.remove(legacyAnthropicKey)
        }

        val migratedCount = listOf(openAiMigrated, anthropicMigrated).count { it }
        if (migratedCount > 0) {
            Logger.withTag("AiPrefsRepo").i {
                "Migrated $migratedCount key(s) from plain DataStore to SecretStore"
            }
        }
    }

    /**
     * Clears the Nano ready flag, availability and token limit.
     * Called when the AI toggle is turned off or model init fails.
     */
    suspend fun clearNanoState() {
        aiDataStore.edit { prefs ->
            prefs[nanoAiReadyKey] = false
            prefs.remove(nanoAvailabilityKey)
            prefs.remove(nanoTokenLimitKey)
        }
    }
}
