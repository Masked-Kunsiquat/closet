package com.closet.core.data.di

import org.koin.core.qualifier.named

/**
 * Koin qualifier for the AI inference [io.ktor.client.HttpClient].
 *
 * AI providers (OpenAI-compatible, Anthropic) can take well over 30 s for a single
 * inference call on a slow network or with a large payload. This client uses extended
 * timeouts so long-running requests aren't aborted mid-inference.
 *
 * Resolve with `get(AiHttpClientQualifier)` in a Koin definition.
 *
 * The default (unqualified) [io.ktor.client.HttpClient] keeps 30 s timeouts and is
 * used for short-lived calls: weather forecasts, model-list discovery.
 */
val AiHttpClientQualifier = named("AiHttpClient")
