package com.closet.core.data.ai.tools

import com.closet.core.data.dao.ClothingDao
import com.closet.core.data.dao.LogDao
import com.closet.core.data.dao.LookupDao
import com.closet.core.data.dao.StatsDao
import com.closet.core.data.model.CategoryEntity
import com.closet.core.data.model.ClothingItemDetail
import com.closet.core.data.model.ClothingStatus
import com.closet.core.data.repository.LogRepository
import com.closet.core.data.time.AppClock
import com.closet.core.data.util.EmbeddingEncoder
import com.closet.core.data.util.EmbeddingIndex
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

// The 8 read-only wardrobe tools. Kept in one file (about 450 lines); split per tool if it grows.

private const val DEFAULT_SEARCH_LIMIT = 10
private const val MAX_ITEMS = 20

// ─── Argument parsing ────────────────────────────────────────────────────────

private fun JsonObject.primitive(name: String): JsonPrimitive? =
    (this[name] as? JsonPrimitive)?.takeIf { it !is JsonNull }

private fun JsonObject.string(name: String): String? =
    primitive(name)?.content?.trim()?.takeIf { it.isNotEmpty() }

private fun JsonObject.int(name: String): Int? =
    primitive(name)?.content?.trim()?.let { it.toIntOrNull() ?: integralOrNull(it, Int.MIN_VALUE.toDouble(), Int.MAX_VALUE.toDouble())?.toInt() }

private fun JsonObject.long(name: String): Long? =
    primitive(name)?.content?.trim()?.let { it.toLongOrNull() ?: integralOrNull(it, Long.MIN_VALUE.toDouble(), Long.MAX_VALUE.toDouble())?.toLong() }

/** Accepts "3.0"-style numbers only when finite, integral and in range; "1.7" is rejected, not truncated. */
private fun integralOrNull(text: String, min: Double, max: Double): Double? =
    text.toDoubleOrNull()?.takeIf { it.isFinite() && it == kotlin.math.floor(it) && it >= min && it <= max }

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

/** Colour id to share for each item id; one batched query for the items being returned. */
private typealias ColorShares = Map<Long, Map<Long, Double>>

private suspend fun ClothingDao.sharesFor(ids: List<Long>): ColorShares {
    if (ids.isEmpty()) return emptyMap()
    return getItemColorRowsForItems(ids)
        .filter { it.share != null }
        .groupBy({ it.clothingItemId }, { it.colorId to it.share!! })
        .mapValues { (_, v) -> v.toMap() }
}

private fun roundShare(share: Double): Double = kotlin.math.round(share * 100.0) / 100.0

private fun ClothingItemDetail.toJson(shares: Map<Long, Double> = emptyMap()): JsonObject = buildJsonObject {
    put("id", item.id)
    put("name", item.name)
    put("category", category?.name ?: "Uncategorized")
    subcategory?.let { put("subcategory", it.name) }
    // Shared colours first by descending share; the rest keep the relation order.
    val withShare = colors.filter { it.id in shares }.sortedByDescending { shares.getValue(it.id) }
    val ordered = withShare + colors.filter { it.id !in shares }
    putJsonArray("colors") {
        ordered.forEach { c ->
            add(
                buildJsonObject {
                    put("name", c.name)
                    shares[c.id]?.let { put("share", roundShare(it)) }
                },
            )
        }
    }
    put("wash_status", item.washStatus.label)
    put("wear_count", wearCount)
}

private const val MATCH_TAGGED = "tagged"
private const val MATCH_DESCRIPTION = "description"

/** True when [word] occurs in [text] (both lower case) bounded by non-alphanumerics, so "yellow" skips "yellowish". */
private fun containsWholeWord(text: String, word: String): Boolean {
    var from = 0
    while (true) {
        val i = text.indexOf(word, from)
        if (i < 0) return false
        val before = i == 0 || !text[i - 1].isLetterOrDigit()
        val end = i + word.length
        val after = end >= text.length || !text[end].isLetterOrDigit()
        if (before && after) return true
        from = i + 1
    }
}

/** [matchKinds], when given, adds a `match` label to each item. */
private suspend fun ClothingDao.itemsResult(
    items: List<ClothingItemDetail>,
    extra: Map<String, JsonElement> = emptyMap(),
    matchKinds: Map<Long, String>? = null,
): ToolResult {
    val shares = sharesFor(items.map { it.item.id })
    return ToolResult(
        buildJsonObject {
            put(
                "items",
                JsonArray(
                    items.map { d ->
                        val json = d.toJson(shares[d.item.id].orEmpty())
                        val kind = matchKinds?.get(d.item.id)
                        if (kind == null) json else JsonObject(json + ("match" to JsonPrimitive(kind)))
                    },
                ),
            )
            extra.forEach { (k, v) -> put(k, v) }
        },
        items.map { it.item.id }.toSet(),
    )
}

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

/** What a color filter matches: tagged color names (lower case) and the requested words for description matching. */
private class ColorFilter(val taggedNames: Set<String>, val words: List<String>)

/**
 * Resolves model-supplied values against the lookup tables, case-insensitively.
 * Create one per tool invocation: it caches the category tree for that call only.
 */
private class LookupResolver(private val lookupDao: LookupDao) {
    private var categoryTree: Pair<List<CategoryEntity>, List<Pair<String, String>>>? = null

    private suspend fun tree(): Pair<List<CategoryEntity>, List<Pair<String, String>>> =
        categoryTree ?: run {
            val categories = lookupDao.getCategories().first()
            val subs = categories.flatMap { cat ->
                lookupDao.getSubcategories(cat.id).first().map { it.name to cat.name }
            }
            (categories to subs).also { categoryTree = it }
        }

    /** A category or subcategory name resolves to its category's name. */
    suspend fun category(value: String): Resolved<String> {
        val (categories, subs) = tree()
        categories.firstOrNull { it.name.equals(value, ignoreCase = true) }
            ?.let { return Resolved.Ok(it.name) }
        subs.firstOrNull { it.first.equals(value, ignoreCase = true) }
            ?.let { return Resolved.Ok(it.second) }
        return Resolved.Bad(
            errorResult("Unknown category '$value'.", categories.map { it.name } + subs.map { it.first }),
        )
    }

    /** Validates [values] against the color table: exact, or as a whole word of a name. */
    suspend fun colors(values: List<String>): Resolved<ColorFilter> {
        val all = lookupDao.getAllColors()
        val names = all.map { it.name }
        val terms = all.map { it.basicTerm }.filter { it.isNotEmpty() }.distinct()
        val matched = mutableSetOf<String>()
        val words = mutableListOf<String>()
        for (value in values) {
            val v = value.lowercase()
            val term = if (v == "grey") "gray" else v
            val hits = all.filter { c ->
                val lower = c.name.lowercase()
                lower == v || lower.split(' ', '-').contains(v) || c.basicTerm.lowercase() == term
            }
            if (hits.isEmpty()) return Resolved.Bad(errorResult("Unknown color '$value'.", names + terms))
            hits.forEach { matched += it.name.lowercase() }
            words += v
        }
        return Resolved.Ok(ColorFilter(matched, words))
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
    private val lookupDao: LookupDao,
    private val embeddingIndex: EmbeddingIndex,
    private val encoder: EmbeddingEncoder,
) : WardrobeTool {

    override val spec = ToolSpec(
        name = "search_items",
        description = "Find items by text, color, category, season or occasion. Colors match tags or description text. Up to 20 items with id, name, category, colors, wash status, wear count.",
        params = listOf(
            ToolParam("query", ToolParamType.STRING, "Free text, e.g. 'warm wool sweater'. Ranks the results."),
            ToolParam("colors", ToolParamType.STRING_ARRAY, "Color names or basic terms; any match is kept. 'blue' covers Navy, Royal Blue, Sky Blue, Denim and Teal."),
            ToolParam("category", ToolParamType.STRING, "Category or subcategory name, e.g. 'Outerwear' or 'Jacket'."),
            ToolParam("season", ToolParamType.STRING, "Season name, e.g. 'Winter'."),
            ToolParam("occasion", ToolParamType.STRING, "Occasion name, e.g. 'Casual'."),
            ToolParam("include_archived", ToolParamType.BOOLEAN, "Include sold, donated and lost items. Default false."),
            ToolParam("limit", ToolParamType.INTEGER, "Maximum items to return. Default 10, maximum 20."),
        ),
    )

    override suspend fun run(args: JsonObject): ToolResult {
        val resolver = LookupResolver(lookupDao)
        val limit = (args.int("limit") ?: DEFAULT_SEARCH_LIMIT).coerceIn(1, MAX_ITEMS)
        val includeArchived = args.bool("include_archived") ?: false

        var colorFilter: ColorFilter? = null
        args.strings("colors").takeIf { it.isNotEmpty() }?.let {
            when (val r = resolver.colors(it)) {
                is Resolved.Ok -> colorFilter = r.value
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

        val colors = colorFilter
        val matchKind = mutableMapOf<Long, String>()
        val filtered = clothingDao.getAllClothingItemDetails().first().filter { d ->
            val colorOk = colors == null || when {
                d.colors.any { it.name.lowercase() in colors.taggedNames } -> true.also { matchKind[d.item.id] = MATCH_TAGGED }
                descriptionMentions(d, colors.words) -> true.also { matchKind[d.item.id] = MATCH_DESCRIPTION }
                else -> false
            }
            colorOk &&
                (includeArchived || d.item.status == ClothingStatus.Active) &&
                (category == null || d.category?.name == category) &&
                (season == null || d.seasons.any { it.name == season }) &&
                (occasion == null || d.occasions.any { it.name == occasion })
        }

        val ranked = args.string("query")?.let { rank(it, filtered) } ?: filtered
        // Tagged matches first, then description-only; each group keeps its ranking order.
        val ordered = if (colors == null) ranked else {
            val (tagged, described) = ranked.partition { matchKind[it.item.id] == MATCH_TAGGED }
            tagged + described
        }
        return clothingDao.itemsResult(
            ordered.take(limit),
            mapOf("total_matches" to JsonPrimitive(ordered.size)),
            if (colors == null) null else matchKind,
        )
    }

    private fun descriptionMentions(d: ClothingItemDetail, words: List<String>): Boolean {
        val text = listOfNotNull(d.item.semanticDescription, d.item.imageCaption).joinToString("\n").lowercase()
        return text.isNotEmpty() && words.any { containsWholeWord(text, it) }
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
            val encoded = encoder.encode(query)
            // A cancellation wrapped in the Result must still cancel, not fall back.
            encoded.exceptionOrNull()?.let { if (it is CancellationException) throw it }
            val vec = encoded.getOrNull() ?: return@withContext emptyList()
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
        return clothingDao.itemsResult(clothingDao.detailsInOrder(ids))
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
                put("item", detail.toJson(clothingDao.sharesFor(listOf(id))[id].orEmpty()))
                put("wear_count", detail.wearCount)
                put("last_worn", logs.firstOrNull()?.date?.let { JsonPrimitive(it) } ?: JsonNull)
                putJsonArray("recent_dates") { logs.map { it.date }.distinct().take(5).forEach { add(JsonPrimitive(it)) } }
            },
            setOf(id),
        )
    }
}

class ItemsNotWornSinceTool(
    private val clothingDao: ClothingDao,
    private val lookupDao: LookupDao,
    private val clock: AppClock,
) : WardrobeTool {

    override val spec = ToolSpec(
        name = "items_not_worn_since",
        description = "List items not worn in the last N days, including items never worn. Optionally limited to a category.",
        params = listOf(
            ToolParam("days", ToolParamType.INTEGER, "Number of days, e.g. 30.", required = true),
            ToolParam("category", ToolParamType.STRING, "Category or subcategory name to limit to."),
        ),
    )

    override suspend fun run(args: JsonObject): ToolResult {
        val resolver = LookupResolver(lookupDao)
        val days = args.int("days")?.takeIf { it >= 0 } ?: return errorResult("days must be a non-negative integer.")
        var category: String? = null
        args.string("category")?.let {
            when (val r = resolver.category(it)) {
                is Resolved.Ok -> category = r.value
                is Resolved.Bad -> return r.result
            }
        }
        val cutoff = clock.today().minus(days, DateTimeUnit.DAY).toString()
        val ids = clothingDao.getItemsNotWornSince(cutoff).map { it.id }
        val matching = clothingDao.detailsInOrder(ids).filter {
            it.item.status == ClothingStatus.Active && (category == null || it.category?.name == category)
        }
        return clothingDao.itemsResult(
            matching.take(MAX_ITEMS),
            mapOf("total_matches" to JsonPrimitive(matching.size), "cutoff_date" to JsonPrimitive(cutoff)),
        )
    }
}

class MostWornTool(
    private val statsDao: StatsDao,
    private val clothingDao: ClothingDao,
    private val lookupDao: LookupDao,
) : WardrobeTool {

    override val spec = ToolSpec(
        name = "most_worn",
        description = "List the most worn items of all time, highest wear count first. Optionally limited to a category.",
        params = listOf(
            ToolParam("limit", ToolParamType.INTEGER, "Maximum items. Default 5, maximum 20."),
            ToolParam("category", ToolParamType.STRING, "Category or subcategory name to limit to."),
        ),
    )

    override suspend fun run(args: JsonObject): ToolResult {
        val resolver = LookupResolver(lookupDao)
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
        return clothingDao.itemsResult(items)
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
        // Same 20-item cap as every other item list; ids come from the capped list.
        val perLog = logs.map { log -> log to clothingDao.detailsInOrder(logDao.getItemIdsForLog(log.id)).take(MAX_ITEMS) }
        val shares = clothingDao.sharesFor(perLog.flatMap { (_, items) -> items.map { it.item.id } }.distinct())
        val outfits = perLog.map { (log, items) ->
            allIds += items.map { it.item.id }
            buildJsonObject {
                put("outfit_name", log.outfitName?.let { JsonPrimitive(it) } ?: JsonNull)
                put("is_ootd", log.isOotd == 1)
                put("items", JsonArray(items.map { it.toJson(shares[it.item.id].orEmpty()) }))
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
        return clothingDao.itemsResult(
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
        description = "Summarize the wardrobe: total and active item counts, plus per-category and per-color counts (breakdowns count active items only).",
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
                put("active_items", byCategory.values.sum())
                put("by_category", buildJsonObject { byCategory.forEach { (k, v) -> put(k, v) } })
                put("by_color", buildJsonObject { byColor.forEach { put(it.label, it.count) } })
            },
            emptySet(),
        )
    }
}
