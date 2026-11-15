package com.closet.platform.gms.chat

import com.closet.features.chat.ChatDateParser
import com.closet.features.chat.RegexChatDateParser
import com.google.mlkit.nl.entityextraction.DateTimeEntity
import com.google.mlkit.nl.entityextraction.Entity
import com.google.mlkit.nl.entityextraction.EntityExtraction
import com.google.mlkit.nl.entityextraction.EntityExtractionParams
import com.google.mlkit.nl.entityextraction.EntityExtractorOptions
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.TimeZone

/**
 * GMS (full-flavor) [ChatDateParser]: uses ML Kit Entity Extraction to handle the full range of
 * natural date expressions ("yesterday", "last Monday", "3 days ago") that the regex parser
 * cannot handle. Falls back to [RegexChatDateParser] if the model is unavailable or returns no result.
 *
 * The English model (~5.6 MB) is downloaded via Play Services on first use. The download is
 * triggered lazily in [init] so it's typically ready by the time the user first queries a date.
 */
class MlKitChatDateParser : ChatDateParser {

    private val regexFallback = RegexChatDateParser()

    private val extractor = EntityExtraction.getClient(
        EntityExtractorOptions.Builder(EntityExtractorOptions.ENGLISH).build()
    )

    init {
        // Trigger model download in the background so it's ready on first query.
        extractor.downloadModelIfNeeded()
            .addOnFailureListener { e ->
                Timber.w(e, "ChatDateParser: entity extraction model download failed")
            }
    }

    /**
     * Parses a date from [text], returning an ISO date string (`YYYY-MM-DD`) or `null`.
     *
     * Tries ML Kit Entity Extraction first with a reference time of "now" so relative
     * expressions resolve correctly. Falls back to [RegexChatDateParser] if ML Kit returns no
     * date entity or the model is not yet available.
     */
    override suspend fun parseDate(text: String): String? = try {
        val params = EntityExtractionParams.Builder(text)
            .setEntityTypesFilter(setOf(Entity.TYPE_DATE_TIME))
            .setPreferredLocale(Locale.US)
            .setReferenceTime(System.currentTimeMillis())
            .setReferenceTimeZone(TimeZone.getDefault())
            .build()

        val annotations = extractor.annotate(params).await()

        annotations
            .flatMap { it.entities }
            .filterIsInstance<DateTimeEntity>()
            .filter { it.dateTimeGranularity >= DateTimeEntity.GRANULARITY_DAY }
            .firstOrNull()
            ?.let { entity ->
                Instant.ofEpochMilli(entity.timestampMillis)
                    .atZone(ZoneOffset.UTC)
                    .toLocalDate()
                    .format(DateTimeFormatter.ISO_LOCAL_DATE)
            }
            ?: regexFallback.parseDate(text) // model returned nothing — regex fallback
    } catch (e: Exception) {
        if (e is kotlinx.coroutines.CancellationException) throw e
        Timber.w(e, "ChatDateParser: ML Kit annotation failed, falling back to regex")
        regexFallback.parseDate(text)
    }
}
