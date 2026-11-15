package com.closet.core.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A [SecretStore] that keeps keys in memory only. Nothing is written to disk: every key is lost
 * when the process ends, and each instance starts empty. Used on JVM and iOS until those
 * platforms get a real secure store, and in tests.
 */
class InMemorySecretStore : SecretStore {
    private val openAiKey = MutableStateFlow("")
    private val anthropicKey = MutableStateFlow("")
    private val geminiKey = MutableStateFlow("")

    override fun openAiKeyFlow(): Flow<String> = openAiKey.asStateFlow()

    override fun anthropicKeyFlow(): Flow<String> = anthropicKey.asStateFlow()

    override fun geminiKeyFlow(): Flow<String> = geminiKey.asStateFlow()

    override suspend fun setOpenAiKey(key: String): Boolean {
        openAiKey.value = key
        return true
    }

    override suspend fun setAnthropicKey(key: String): Boolean {
        anthropicKey.value = key
        return true
    }

    override suspend fun setGeminiKey(key: String): Boolean {
        geminiKey.value = key
        return true
    }
}
