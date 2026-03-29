package com.closet.core.data.repository

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fetches available model IDs from AI provider APIs so the Settings screen can populate
 * the model selector dropdown without requiring users to look up model names manually.
 *
 * Both OpenAI-compatible and Anthropic endpoints return the same envelope shape:
 * `{ "data": [ { "id": "model-name", ... }, ... ] }`. The Anthropic endpoint requires
 * `x-api-key` + `anthropic-version` headers rather than `Authorization: Bearer`.
 *
 * All failures are caught and returned as [Result.failure] — callers fall back to
 * free-text entry when models cannot be fetched.
 */
@Singleton
class ModelDiscoveryRepository @Inject constructor(
    private val client: HttpClient,
    private val json: Json,
) {
    companion object {
        private const val TAG = "ModelDiscovery"
        private const val DEFAULT_OPENAI_BASE = "https://api.openai.com"
        private const val ANTHROPIC_BASE = "https://api.anthropic.com"
        private const val ANTHROPIC_VERSION = "2023-06-01"
    }

    /**
     * Fetches the list of available model IDs from an OpenAI-compatible `GET /v1/models`
     * endpoint. [baseUrl] is normalized (trailing slash stripped; defaults to OpenAI when blank).
     */
    suspend fun fetchOpenAiModels(apiKey: String, baseUrl: String): Result<List<String>> {
        val base = if (baseUrl.isBlank()) DEFAULT_OPENAI_BASE else baseUrl.trimEnd('/')
        // Avoid /v1/v1/models when the caller's baseUrl already ends with /v1
        val modelsUrl = if (base.endsWith("/v1")) "$base/models" else "$base/v1/models"
        return runCatching {
            val responseText: String = client.get(modelsUrl) {
                header("Authorization", "Bearer $apiKey")
            }.body()
            parseModelIds(responseText)
        }.onFailure { e ->
            Timber.tag(TAG).w(e, "fetchOpenAiModels failed (url=%s)", modelsUrl)
        }
    }

    /**
     * Fetches the list of available Claude model IDs from `GET https://api.anthropic.com/v1/models`.
     */
    suspend fun fetchAnthropicModels(apiKey: String): Result<List<String>> {
        return runCatching {
            val responseText: String = client.get("$ANTHROPIC_BASE/v1/models") {
                header("x-api-key", apiKey)
                header("anthropic-version", ANTHROPIC_VERSION)
            }.body()
            parseModelIds(responseText)
        }.onFailure { e ->
            Timber.tag(TAG).w(e, "fetchAnthropicModels failed")
        }
    }

    /** Extracts the `id` field from each entry in the `data` array of the models response. */
    private fun parseModelIds(responseText: String): List<String> {
        val root = json.parseToJsonElement(responseText.trim()).jsonObject
        val data = root["data"]?.jsonArray ?: return emptyList()
        return data.mapNotNull { element ->
            element.jsonObject["id"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
        }.sorted()
    }
}
