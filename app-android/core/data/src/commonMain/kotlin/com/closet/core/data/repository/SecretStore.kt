package com.closet.core.data.repository

import kotlinx.coroutines.flow.Flow

/**
 * Storage for AI provider API keys. Kept apart from the plain `ai_prefs` DataStore, which holds
 * only non-sensitive configuration.
 *
 * Android binds [EncryptedKeyStore] (Keystore-backed, persistent). JVM and iOS bind
 * [InMemorySecretStore] until they get a real secure store.
 */
interface SecretStore {
    /** The stored OpenAI-compatible API key, or `""` when unset. Emits immediately on collection. */
    fun openAiKeyFlow(): Flow<String>

    /** The stored Anthropic API key, or `""` when unset. Emits immediately on collection. */
    fun anthropicKeyFlow(): Flow<String>

    /** The stored Gemini API key, or `""` when unset. Emits immediately on collection. */
    fun geminiKeyFlow(): Flow<String>

    /**
     * Stores [key] for the OpenAI-compatible provider. Returns `true` when the store accepted it;
     * `false` on write failure, in which case the flow keeps its previous value.
     */
    suspend fun setOpenAiKey(key: String): Boolean

    /** Stores [key] for the Anthropic provider. Same contract as [setOpenAiKey]. */
    suspend fun setAnthropicKey(key: String): Boolean

    /** Stores [key] for the Gemini provider. Same contract as [setOpenAiKey]. */
    suspend fun setGeminiKey(key: String): Boolean
}
