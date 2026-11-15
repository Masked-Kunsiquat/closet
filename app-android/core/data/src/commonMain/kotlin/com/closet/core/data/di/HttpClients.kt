package com.closet.core.data.di

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import co.touchlab.kermit.Logger as KermitLogger

/** Headers whose values are replaced with `<REDACTED>` in debug HTTP logs. */
private val SENSITIVE_HEADERS = setOf("authorization", "x-api-key", "x-goog-api-key")

/**
 * Ktor's Logging plugin writes header lines as `-> Name: value` (requests and responses alike);
 * `<- ` is accepted too. Group 1 is the prefix kept in the output (leading whitespace and any
 * arrow), group 2 the header name.
 */
private val HEADER_LINE = Regex("""^(\s*(?:->|<-)?\s*)([^:\s][^:]*):""")

/** A `key=` query parameter (the Google Weather API key) in a logged URL; group 1 is kept. */
private val KEY_QUERY_PARAM = Regex("""([?&]key=)[^&#\s]*""")

/**
 * Redacts secrets from one Ktor log message:
 * - every header line whose name is in [SENSITIVE_HEADERS] (case-insensitively) becomes
 *   `<prefix>Name: <REDACTED>`, keeping Ktor's `-> ` / `<- ` prefix;
 * - every `key=` query parameter value in a URL becomes `<REDACTED>`.
 *
 * Other lines are unchanged.
 */
internal fun redactSensitiveHeaders(message: String): String =
    message.lines().joinToString("\n") { line ->
        val header = HEADER_LINE.find(line)
        if (header != null && header.groupValues[2].trim().lowercase() in SENSITIVE_HEADERS) {
            "${header.groupValues[1]}${header.groupValues[2]}: <REDACTED>"
        } else {
            KEY_QUERY_PARAM.replace(line) { "${it.groupValues[1]}<REDACTED>" }
        }
    }

/**
 * Builds an [HttpClient] on [engine] with JSON content negotiation and debug-only
 * logging (when [isDebug]) that redacts [SENSITIVE_HEADERS]. Connect timeout is always 30 s.
 */
fun buildHttpClient(
    engine: HttpClientEngineFactory<*>,
    json: Json,
    socketTimeoutMs: Long,
    requestTimeoutMs: Long,
    logTag: String,
    isDebug: Boolean,
): HttpClient = HttpClient(engine) {
    install(HttpTimeout) {
        connectTimeoutMillis = 30_000
        socketTimeoutMillis = socketTimeoutMs
        requestTimeoutMillis = requestTimeoutMs
    }
    install(ContentNegotiation) {
        json(json)
    }
    install(Logging) {
        logger = object : Logger {
            override fun log(message: String) {
                KermitLogger.withTag(logTag).d { redactSensitiveHeaders(message) }
            }
        }
        level = if (isDebug) LogLevel.BODY else LogLevel.NONE
    }
}
