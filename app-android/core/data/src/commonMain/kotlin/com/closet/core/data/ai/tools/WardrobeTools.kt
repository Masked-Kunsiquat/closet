package com.closet.core.data.ai.tools

import com.closet.core.data.dao.ClothingDao
import com.closet.core.data.dao.LogDao
import com.closet.core.data.dao.LookupDao
import com.closet.core.data.dao.StatsDao
import com.closet.core.data.model.ClothingItemDetail
import com.closet.core.data.model.ClothingStatus
import com.closet.core.data.repository.LogRepository
import com.closet.core.data.util.EmbeddingEncoder
import com.closet.core.data.util.EmbeddingIndex
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.todayIn
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlin.time.Clock

// The 8 read-only wardrobe tools. Kept in one file (about 450 lines); split per tool if it grows.

private const val DEFAULT_SEARCH_LIMIT = 10
private const val MAX_ITEMS = 20

// ─── Argument parsing ────────────────────────────────────────────────────────

private fun JsonObject.primitive(name: String): JsonPrimitive? =
    (this[name] as? JsonPrimitive)?.takeIf { it !is JsonNull }

private fun JsonObject.string(name: String): String? =
    primitive(name)?.content?.trim()?.takeIf { it.isNotEmpty() }

private fun JsonObject.int(name: String): Int? =
    primitive(name)?.content?.trim()?.let { it.toIntOrNull() ?: it.toDoubleOrNull()?.toInt() }

private fun JsonObject.long(name: String): Long? =
    primitive(name)?.content?.trim()?.let { it.toLongOrNull() ?: it.toDoubleOrNull()?.toLong() }

private fun JsonObject.bool(name: String): Boolean? =
    primitive(name)?.content?.trim()?.lowercase()?.let {
        when (it) {
            "true" -> true
            "false" -> false
            else -> null
        }
    }

/** A string array; a lone string is accepted as a one-element array. */
private fun JsonObject.strings(name: String): List<String> = when (val v = this[name]) {
    is JsonArray -> v.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p !is JsonNull }?.content?.trim() }
    is JsonPrimitive -> if (v is JsonNull) emptyList() else listOf(v.content.trim())
    else -> emptyList()
}.filter { it.isNotEmpty() }

// ─── Result building ─────────────────────────────────────────────────────────

private fun errorResult(message: String, validValues: List<String>? = null) = ToolResult(
    buildJsonObject {
        put("error", message)
        if (validValues != null) putJsonArray("valid_values") { validValues.forEach { add(JsonPrimitive(it)) } }
    },
    emptySet(),
)

private fun ClothingItemDetail.toJson(): JsonObject = buildJsonObject {
    put("id", item.id)
    put("name", item.name)
    put("category", category?.name ?: "Uncategorized")
    subcategory?.let { put("subcategory", it.name) }
    putJsonArray("colors") { colors.forEach { add(JsonPrimitive(it.name)) } }
    put("wash_status", item.washStatus.label)
    put("wear_count", wearCount)
}

private fun itemsResult(items: List<ClothingItemDetail>, extra: Map<String, JsonElement> = emptyMap()) = ToolResult(
    buildJsonObject {
        put("items", JsonArray(items.map { it.toJson() }))
        extra.forEach { (k, v) -> put(k, v) }
    },
    items.map { it.item.id }.toSet(),
)

/** Details for [ids], in [ids] order. Ids that no longer exist are skipped. */
private suspend fun ClothingDao.detailsInOrder(ids: List<Long>): List<ClothingItemDetail> {
    if (ids.isEmpty()) return emptyList()
    val byId = getItemDetailsByIds(ids).associateBy { it.item.id }
    return ids.mapNotNull { byId[it] }
}

// ─── Lookup resolution ───────────────────────────────────────────────────────

private sealed interface Resolved<out T> {
    data class Ok<T>(val value: T) : Resolved<T>
    data class Bad(val result: ToolResult) : Resolved<Nothing>
}

/** Resolves model-supplied values against the lookup tables, case-insensitively. */
private class LookupResolver(private val lookupDao: LookupDao) {

    /** A category or subcategory name resolves to its category's name. */
    suspend fun category(value: String): Resolved<String> {
        val categories = lookupDao.getCategories().first()
        categories.firstOrNull { it.name.equals(value, ignoreCase = true) }
            ?.let { return Resolved.Ok(it.name) }
        val subs = categories.flatMap { cat ->
            lookupDao.getSubcategories(cat.id).first().map { it.name to cat.name }
        }
        subs.firstOrNull { it.first.equals(value, ignoreCase = true) }
            ?.let { return Resolved.Ok(it.second) }
        return Resolved.Bad(
            errorResult("Unknown category '$value'.", categories.map { it.name } + subs.map { it.first }),
        )
    }

    /** Lower-cased color names matching any of [values]: exact, or containing it as a whole word. */
    suspend fun colors(values: List<String>): Resolved<Set<String>> {
        val names = lookupDao.getAllColors().map { it.name }
        val matched = mutableSetOf<String>()
        for (value in values) {
            val v = value.lowercase()
            val hits = names.filter { name ->
                val lower = name.lowercase()
                lower == v || lower.split(' ', '-').contains(v)
            }
            if (hits.isEmpty()) return Resolved.Bad(errorResult("Unknown color '$value'.", names))
            hits.forEach { matched += it.lowercase() }
        }
        return Resolved.Ok(matched)
    }

    suspend fun season(value: String): Resolved<String> {
        val names = lookupDao.getSeasons().first().map { it.name }
        return names.firstOrNull { it.equals(value, ignoreCase = true) }?.let { Resolved.Ok(it) }
            ?: Resolved.Bad(errorResult("Unknown season '$value'.", names))
    }

    suspend fun occasion(value: String): Resolved<String> {
        val names = lookupDao.getOccasions().first().map { it.name }
        return names.firstOrNull { it.equals(value, ignoreCase = true) }?.let { Resolved.Ok(it) }
            ?: Resolved.Bad(errorResult("Unknown occasion '$value'.", names))
    }
}

// ─── Tools ───────────────────────────────────────────────────────────────────

class SearchItemsTool(
    private val clothingDao: ClothingDao,
    lookupDao: LookupDao,
    private val embeddingIndex: EmbeddingIndex,
    private val encoder: EmbeddingEncoder,
) : WardrobeTool {
    private val resolver = LookupResolver(lookupDao)

    override val spec = ToolSpec(
        name = "search_items",
        description = "Find wardrobe items by text, color, category, season or occasion. Returns up to 20 items with id, name, category, colors, wash status and wear count.",
        params = listOf(
            ToolParam("query", ToolParamType.STRING, "Free text, e.g. 'warm wool sweater'. Ranks the results."),
            ToolParam("colors", ToolParamType.STRING_ARRAY, "Color names; an item matching any of them is kept. 'blue' covers Royal Blue and Sky Blue."),
            ToolParam("category", ToolParamType.STRING, "Category or subcategory name, e.g. 'Outerwear' or 'Jacket'."),
            ToolParam("season", ToolParamType.STRING, "Season name, e.g. 'Winter'."),
            ToolParam("occasion", ToolParamType.STRING, "Occasion name, e.g. 'Casual'."),
            ToolParam("include_archived", ToolParamType.BOOLEAN, "Include sold, donated and lost items. Default false."),
            ToolParam("limit", ToolParamType.INTEGER, "Maximum items to return. Default 10, maximum 20."),
        ),
    )

    override suspend fun run(args: JsonObject): ToolResult {
        val limit = (args.int("limit") ?: DEFAULT_SEARCH_LIMIT).coerceIn(1, MAX_ITEMS)
        val includeArchived = args.bool("include_archived") ?: false

        var colorNames: Set<String>? = null
        args.strings("colors").takeIf { it.isNotEmpty() }?.let {
            when (val r = resolver.colors(it)) {
                is Resolved.Ok -> colorNames = r.value
                is Resolved.Bad -> return r.result
            }
        }
        var category: String? = null
        args.string("category")?.let {
            when (val r = resolver.category(it)) {
                is Resolved.Ok -> category = r.value
                is Resolved.Bad -> return r.result
            }
        }
        var season: String? = null
        args.string("season")?.let {
            when (val r = resolver.season(it)) {
                is Resolved.Ok -> season = r.value
                is Resolved.Bad -> return r.result
            }
        }
        var occasion: String? = null
        args.string("occasion")?.let {
            when (val r = resolver.occasion(it)) {
                is Resolved.Ok -> occasion = r.value
                is Resolved.Bad -> return r.result
            }
        }

        val filtered = clothingDao.getAllClothingItemDetails().first().filter { d ->
            (includeArchived || d.item.status == ClothingStatus.Active) &&
                (colorNames == null || d.colors.any { it.name.lowercase() in colorNames }) &&
                (category == null || d.category?.name == category) &&
                (season == null || d.seasons.any { it.name == season }) &&
                (occasion == null || d.occasions.any { it.name == occasion })
        }

        val ranked = args.string("query")?.let { rank(it, filtered) } ?: filtered
        return itemsResult(
            ranked.take(limit),
            mapOf("total_matches" to JsonPrimitive(ranked.size)),
        )
    }

    /** Semantic ranking when the encoder is up, else name/description substring matching. */
    private suspend fun rank(query: String, items: List<ClothingItemDetail>): List<ClothingItemDetail> {
        if (items.isEmpty()) return items
        val semantic = semanticOrder(query)
        if (semantic.isNotEmpty()) {
            val byId = items.associateBy { it.item.id }
            val ranked = semantic.mapNotNull { byId[it] }
            val rankedIds = ranked.map { it.item.id }.toSet()
            // Items with no usable embedding still count if their text matches.
            return ranked + substringRank(query, items.filter { it.item.id !in rankedIds })
        }
        return substringRank(query, items)
    }

    private suspend fun semanticOrder(query: String): List<Long> = try {
        withContext(Dispatchers.IO) {
            if (!encoder.isAvailable || !embeddingIndex.isLoaded) return@withContext emptyList()
            val vec = encoder.encode(query).getOrNull() ?: return@withContext emptyList()
            embeddingIndex.search(vec, topK = Int.MAX_VALUE)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        emptyList()
    }

    private fun substringRank(query: String, items: List<ClothingItemDetail>): List<ClothingItemDetail> {
        val tokens = query.lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return items
        return items
            .map { d ->
                val text = listOfNotNull(d.item.name, d.item.semanticDescription, d.item.imageCaption)
                    .joinToString(" ").lowercase()
                d to tokens.count { it in text }
            }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .map { it.first }
    }
}

class GetItemsTool(private val clothingDao: ClothingDao) : WardrobeTool {
    override val spec = ToolSpec(
        name = "get_items",
        description = "Look up specific wardrobe items by their ids. Returns id, name, category, colors, wash status and wear count for each.",
        params = listOf(
            ToolParam("item_ids", ToolParamType.STRING_ARRAY, "Item ids as numeric strings, e.g. [\"12\", \"40\"].", required = true),
        ),
    )

    override suspend fun run(args: JsonObject): ToolResult {
        val raw = args.strings("item_ids")
        if (raw.isEmpty()) return errorResult("item_ids is required.")
        val ids = raw.map { it.toLongOrNull() ?: return errorResult("Item id '$it' is not numeric.") }
            .distinct().take(MAX_ITEMS)
        return itemsResult(clothingDao.detailsInOrder(ids))
    }
}

class WearHistoryTool(
    private val clothingDao: ClothingDao,
    private val logRepository: LogRepository,
) : WardrobeTool {
    override val spec = ToolSpec(
        name = "wear_history",
        description = "Show how often and when one item was worn: wear count, last worn date and up to 5 recent dates.",
        params = listOf(ToolParam("item_id", ToolParamType.INTEGER, "The item id.", required = true)),
    )

    override suspend fun run(args: JsonObject): ToolResult {
        val id = args.long("item_id") ?: return errorResult("item_id is required.")
        val detail = clothingDao.getItemDetailsByIds(listOf(id)).firstOrNull()
            ?: return errorResult("No item with id $id.")
        val logs = logRepository.getLogsForItem(id).first()
        return ToolResult(
            buildJsonObject {
                put("item", detail.toJson())
                put("wear_count", detail.wearCount)
                put("last_worn", logs.firstOrNull()?.date?.let { JsonPrimitive(it) } ?: JsonNull)
                putJsonArray("recent_dates") { logs.take(5).forEach { add(JsonPrimitive(it.date)) } }
            },
            setOf(id),
        )
    }
}

class ItemsNotWornSinceTool(
    private val clothingDao: ClothingDao,
    lookupDao: LookupDao,
) : WardrobeTool {
    private val resolver = LookupResolver(lookupDao)

    override val spec = ToolSpec(
        name = "items_not_worn_since",
        description = "List items not worn in the last N days, including items never worn. Optionally limited to a category.",
        params = listOf(
            ToolParam("days", ToolParamType.INTEGER, "Number of days, e.g. 30.", required = true),
            ToolParam("category", ToolParamType.STRING, "Category or subcategory name to limit to."),
        ),
    )

    override suspend fun run(args: JsonObject): ToolResult {
        val days = args.int("days")?.takeIf { it >= 0 } ?: return errorResult("days must be a non-negative integer.")
        var category: String? = null
        args.string("category")?.let {
            when (val r = resolver.category(it)) {
                is Resolved.Ok -> category = r.value
                is Resolved.Bad -> return r.result
            }
        }
        val cutoff = Clock.System.todayIn(TimeZone.currentSystemDefault()).minus(days, DateTimeUnit.DAY).toString()
        val ids = clothingDao.getItemsNotWornSince(cutoff).map { it.id }
        val matching = clothingDao.detailsInOrder(ids).filter {
            it.item.status == ClothingStatus.Active && (category == null || it.category?.name == category)
        }
        return itemsResult(
            matching.take(MAX_ITEMS),
            mapOf("total_matches" to JsonPrimitive(matching.size), "cutoff_date" to JsonPrimitive(cutoff)),
        )
    }
}

class MostWornTool(
    private val statsDao: StatsDao,
    private val clothingDao: ClothingDao,
    lookupDao: LookupDao,
) : WardrobeTool {
    private val resolver = LookupResolver(lookupDao)

    override val spec = ToolSpec(
        name = "most_worn",
        description = "List the most worn items of all time, highest wear count first. Optionally limited to a category.",
        params = listOf(
            ToolParam("limit", ToolParamType.INTEGER, "Maximum items. Default 5, maximum 20."),
            ToolParam("category", ToolParamType.STRING, "Category or subcategory name to limit to."),
        ),
    )

    override suspend fun run(args: JsonObject): ToolResult {
        val limit = (args.int("limit") ?: 5).coerceIn(1, MAX_ITEMS)
        var category: String? = null
        args.string("category")?.let {
            when (val r = resolver.category(it)) {
                is Resolved.Ok -> category = r.value
                is Resolved.Bad -> return r.result
            }
        }
        // The DAO has no category filter, so over-fetch and filter in memory.
        val fetch = if (category == null) limit else Int.MAX_VALUE
        val ids = statsDao.getMostWornItems(null, fetch).first().map { it.id }
        val items = clothingDao.detailsInOrder(ids)
            .filter { category == null || it.category?.name == category }
            .take(limit)
        return itemsResult(items)
    }
}

class OutfitsWornOnTool(
    private val logDao: LogDao,
    private val clothingDao: ClothingDao,
) : WardrobeTool {
    override val spec = ToolSpec(
        name = "outfits_worn_on",
        description = "Show what was worn on one date: each logged outfit with its items. The date must be YYYY-MM-DD.",
        params = listOf(ToolParam("date", ToolParamType.STRING, "Date as YYYY-MM-DD.", required = true)),
    )

    override suspend fun run(args: JsonObject): ToolResult {
        val raw = args.string("date") ?: return errorResult("date is required (YYYY-MM-DD).")
        val date = try {
            LocalDate.parse(raw).toString()
        } catch (e: IllegalArgumentException) {
            return errorResult("'$raw' is not a valid date; use YYYY-MM-DD.")
        }
        val logs = logDao.getLogsForDateOnce(date)
        val allIds = mutableSetOf<Long>()
        val outfits = logs.map { log ->
            val items = clothingDao.detailsInOrder(logDao.getItemIdsForLog(log.id))
            allIds += items.map { it.item.id }
            buildJsonObject {
                put("outfit_name", log.outfitName?.let { JsonPrimitive(it) } ?: JsonNull)
                put("is_ootd", log.isOotd == 1)
                put("items", JsonArray(items.map { it.toJson() }))
            }
        }
        return ToolResult(
            buildJsonObject {
                put("date", date)
                put("outfits", JsonArray(outfits))
            },
            allIds,
        )
    }
}

class LaundryListTool(private val clothingDao: ClothingDao) : WardrobeTool {
    override val spec = ToolSpec(
        name = "laundry_list",
        description = "List the items currently marked dirty and waiting to be washed.",
        params = emptyList(),
    )

    override suspend fun run(args: JsonObject): ToolResult {
        val items = clothingDao.detailsInOrder(clothingDao.getItemsNeedingWash().map { it.id })
        return itemsResult(
            items.take(MAX_ITEMS),
            mapOf("total_matches" to JsonPrimitive(items.size)),
        )
    }
}

class WardrobeSummaryTool(
    private val clothingDao: ClothingDao,
    private val statsDao: StatsDao,
) : WardrobeTool {
    override val spec = ToolSpec(
        name = "wardrobe_summary",
        description = "Summarize the wardrobe: total item count plus item counts per category and per color (active items).",
        params = emptyList(),
    )

    override suspend fun run(args: JsonObject): ToolResult {
        val total = clothingDao.getItemCount()
        val byCategory = statsDao.getCategorySubcategoryBreakdown().first()
            .groupBy { it.categoryLabel }
            .mapValues { (_, rows) -> rows.sumOf { it.count } }
        val byColor = statsDao.getBreakdownByColor().first()
        return ToolResult(
            buildJsonObject {
                put("total_items", total)
                put("by_category", buildJsonObject { byCategory.forEach { (k, v) -> put(k, v) } })
                put("by_color", buildJsonObject { byColor.forEach { put(it.label, it.count) } })
            },
            emptySet(),
        )
    }
}
