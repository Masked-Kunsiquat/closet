package com.closet.features.chat

import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.languageid.LanguageIdentificationOptions
import kotlinx.coroutines.tasks.await
import timber.log.Timber

/** Full-flavor [LanguageDetector] backed by ML Kit Language Identification. */
class MlKitLanguageDetector : LanguageDetector {

    // Language identifier configured with a 0.7 confidence threshold.
    // Returns "und" (undetermined) when confidence < threshold — treated as non-English.
    private val languageIdentifier = LanguageIdentification.getClient(
        LanguageIdentificationOptions.Builder()
            .setConfidenceThreshold(LANGUAGE_CONFIDENCE_THRESHOLD)
            .build()
    )

    /**
     * Returns true if ML Kit identifies [text] as English with confidence ≥ [LANGUAGE_CONFIDENCE_THRESHOLD].
     * On any identification failure, logs the error and returns false (Unrouted) so the query
     * falls through to RAG rather than risking a misrouted stat answer on non-English input.
     */
    override suspend fun isEnglish(text: String): Boolean = try {
        languageIdentifier.identifyLanguage(text).await() == "en"
    } catch (e: Exception) {
        if (e is kotlinx.coroutines.CancellationException) throw e
        Timber.w(e, "ChatRouter: language identification failed, falling through to RAG")
        false
    }

    private companion object {
        const val LANGUAGE_CONFIDENCE_THRESHOLD = 0.7f
    }
}
