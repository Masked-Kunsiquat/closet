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
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.aiDataStore: DataStore<Preferences> by preferencesDataStore(name = "ai_prefs")

/**
 * Repository for persisting AI provider preferences via [DataStore].
 *
 * Backed by a dedicated DataStore file (`ai_prefs`), isolated from the weather and app
 * preference stores. Mirrors the pattern established by [WeatherPreferencesRepository].
 *
 * Stores:
 * - Selected [AiProvider] (enum name as string; defaults to [AiProvider.Nano])
 * - Per-provider API keys (plain strings in DataStore — encryption via EncryptedSharedPreferences
 *   or Android Keystore is a Phase 2 Settings UI concern; the repository API is key-agnostic)
 * - Nano-specific: [aiReady] flag and [tokenLimit] (populated by the background init worker)
 *
 * All AI-dependent features must gate on [getAiReady] returning `true` before invoking an
 * [com.closet.features.recommendations.ai.OutfitAiProvider].
 *
 * Provided as a [Singleton] — inject anywhere via Hilt.
 */
@Singleton
class AiPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    // Keys
    private val aiEnabledKey = booleanPreferencesKey("ai_enabled")
    private val selectedProviderKey = stringPreferencesKey("ai_selected_provider")
    private val openAiApiKeyKey = stringPreferencesKey("ai_openai_api_key")
    private val openAiBaseUrlKey = stringPreferencesKey("ai_openai_base_url")
    private val openAiModelKey = stringPreferencesKey("ai_openai_model")
    private val anthropicApiKeyKey = stringPreferencesKey("ai_anthropic_api_key")
    private val nanoAiReadyKey = booleanPreferencesKey("ai_nano_ready")
    private val nanoTokenLimitKey = intPreferencesKey("ai_nano_token_limit")

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

    // ── API keys ─────────────────────────────────────────────────────────────

    /**
     * API key for OpenAI-compatible providers (OpenAI, Gemini cloud, Ollama, Groq, etc.).
     * Returns an empty string when no key has been set.
     *
     * Note: stored as plain text in DataStore for now. The Settings UI (Phase 2) will
     * layer EncryptedSharedPreferences or Android Keystore on top of this if required.
     */
    fun getOpenAiApiKey(): Flow<String> = context.aiDataStore.data.map { prefs ->
        prefs[openAiApiKeyKey] ?: ""
    }

    suspend fun setOpenAiApiKey(key: String) {
        context.aiDataStore.edit { prefs ->
            prefs[openAiApiKeyKey] = key
        }
    }

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
     *
     * Set to any model name accepted by the configured endpoint — e.g. `gemini-2.0-flash`,
     * `llama3`, `mixtral-8x7b-32768` (Groq), etc.
     */
    fun getOpenAiModel(): Flow<String> = context.aiDataStore.data.map { prefs ->
        prefs[openAiModelKey] ?: ""
    }

    suspend fun setOpenAiModel(model: String) {
        context.aiDataStore.edit { prefs ->
            prefs[openAiModelKey] = model
        }
    }

    /**
     * API key for the Anthropic provider (Claude).
     * Returns an empty string when no key has been set.
     */
    fun getAnthropicApiKey(): Flow<String> = context.aiDataStore.data.map { prefs ->
        prefs[anthropicApiKeyKey] ?: ""
    }

    suspend fun setAnthropicApiKey(key: String) {
        context.aiDataStore.edit { prefs ->
            prefs[anthropicApiKeyKey] = key
        }
    }

    // ── Nano — ready flag ────────────────────────────────────────────────────

    /**
     * Whether Gemini Nano is downloaded and ready for inference on this device.
     *
     * Set to `true` by the background init worker after:
     * 1. [checkStatus()] confirms the model is supported.
     * 2. [download()] completes successfully (if the model was not already present).
     * 3. [getTokenLimit()] has been fetched and stored.
     *
     * All callers must check this flag before invoking [NanoProvider].
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
     * The maximum token count supported by the on-device Nano model, as reported by
     * `getTokenLimit()` at init time. Stored here so the coherence scorer can gate
     * candidate payload size without re-querying the model on every request.
     *
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

    // ── Composite helpers ────────────────────────────────────────────────────

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
