package com.closet.core.data.di

import io.ktor.client.HttpClient

/**
 * The [HttpClient] for AI inference calls, as its own type.
 *
 * AI providers (OpenAI-compatible, Anthropic, Gemini) can take well over 30 s for a single
 * inference call on a slow network or with a large payload. This client uses extended
 * timeouts (120 s socket and request) so long-running requests aren't aborted mid-inference.
 *
 * A provider that needs it takes an [AiHttpClient] constructor parameter, so plain type
 * resolution (including `singleOf`) always hands it the right client. The default, bare
 * [HttpClient] keeps 30 s timeouts and is used for short-lived calls: weather forecasts,
 * model-list discovery.
 */
class AiHttpClient(val client: HttpClient)
