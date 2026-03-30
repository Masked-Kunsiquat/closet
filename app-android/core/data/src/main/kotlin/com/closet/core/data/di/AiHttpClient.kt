package com.closet.core.data.di

import javax.inject.Qualifier

/**
 * Hilt qualifier for the AI inference [io.ktor.client.HttpClient].
 *
 * AI providers (OpenAI-compatible, Anthropic) can take well over 30 s for a single
 * inference call on a slow network or with a large payload. This client uses extended
 * timeouts so long-running requests aren't aborted mid-inference.
 *
 * Inject with `@AiHttpClient val client: HttpClient`.
 *
 * The default (unqualified) [io.ktor.client.HttpClient] keeps 30 s timeouts and is
 * used for short-lived calls: weather forecasts, model-list discovery.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AiHttpClient
