package com.closet.core.data.ai.tools

import androidx.room.useReaderConnection
import com.closet.core.data.ClothingDatabase
import com.closet.core.data.TestDatabase
import com.closet.core.data.model.ClothingItemEntity
import com.closet.core.data.model.ItemEmbeddingEntity
import com.closet.core.data.model.OutfitLogEntity
import com.closet.core.data.model.WashStatus
import com.closet.core.data.repository.LogRepository
import com.closet.core.data.util.EmbeddingEncoder
import com.closet.core.data.util.EmbeddingIndex
import com.closet.core.data.util.toByteArrayLE
import com.closet.core.data.worker.EmbeddingWork
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

class WardrobeToolsTest {

    private class FakeEncoder(
        override val isAvailable: Boolean = false,
        private val vector: FloatArray = FloatArray(EmbeddingEncoder.HIDDEN_SIZE),
    ) : EmbeddingEncoder {
        override val modelVersion = EmbeddingWork.MODEL_VERSION
        override suspend fun encode(text: String) = Result.success(vector)
    }

    private lateinit var testDb: TestDatabase
    private val db: ClothingDatabase get() = testDb.db
    private lateinit var registry: ToolRegistry
    private lateinit var index: EmbeddingIndex
    private var encoder: EmbeddingEncoder = FakeEncoder()

    private var sneakersId = 0L
    private var overcoatId = 0L
    private var shirtId = 0L

    @BeforeTest
    fun setUp() {
        testDb = TestDatabase()
        runBlocking {
            sneakersId = insertItem("Yellow sneakers", "Footwear", "Sneakers", listOf("Yellow"))
            overcoatId = insertItem("Grey overcoat", "Outerwear", "Coat", listOf("Grey"))
            shirtId = insertItem("Blue T-shirt", "Tops", "T-Shirt", listOf("Royal Blue", "Sky Blue"))
        }
        index = EmbeddingIndex(db.embeddingDao())
        registry = buildRegistry()
    }

    @AfterTest
    fun tearDown() {
        testDb.close()
    }

    private fun buildRegistry() = ToolRegistry(
        listOf(
            SearchItemsTool(db.clothingDao(), db.lookupDao(), index, encoder),
            GetItemsTool(db.clothingDao()),
            WearHistoryTool(db.clothingDao(), LogRepository(db.logDao())),
            ItemsNotWornSinceTool(db.clothingDao(), db.lookupDao()),
            MostWornTool(db.statsDao(), db.clothingDao(), db.lookupDao()),
            OutfitsWornOnTool(db.logDao(), db.clothingDao()),
            LaundryListTool(db.clothingDao()),
            WardrobeSummaryTool(db.clothingDao(), db.statsDao()),
        ),
    )

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private suspend fun idOf(table: String, name: String): Long = db.useReaderConnection { c ->
        c.usePrepared("SELECT id FROM $table WHERE name = ?") { s ->
            s.bindText(1, name)
            check(s.step()) { "no $table row named $name" }
            s.getLong(0)
        }
    }

    private suspend fun insertItem(
        name: String,
        category: String,
        subcategory: String,
        colors: List<String>,
        wash: WashStatus = WashStatus.Clean,
        description: String? = null,
    ): Long {
        val id = db.clothingDao().insertClothingItem(
            ClothingItemEntity(
                name = name,
                categoryId = idOf("categories", category),
                subcategoryId = idOf("subcategories", subcategory),
                washStatus = wash,
                semanticDescription = description,
            ),
        )
        db.clothingDao().updateItemColors(id, colors.map { idOf("colors", it) })
        return id
    }

    private fun run(tool: String, args: JsonObject = JsonObject(emptyMap())): ToolResult =
        runBlocking { registry.find(tool)!!.run(args) }

    private fun search(args: JsonObject) = run("search_items", args)

    private fun strings(vararg v: String) = buildJsonArray { v.forEach { add(JsonPrimitive(it)) } }

    private fun ToolResult.obj() = json.jsonObject
    private fun ToolResult.itemIdsInJson(): List<Long> =
        obj()["items"]!!.jsonArray.map { it.jsonObject["id"]!!.jsonPrimitive.content.toLong() }

    private fun wear(itemId: Long, date: String) = runBlocking {
        db.logDao().insertAdHocLogAndSnapshot(OutfitLogEntity(date = date), listOf(itemId))
    }

    // ─── search_items ─────────────────────────────────────────────────────────

    @Test
    fun searchByColorYellowReturnsOnlySneakers() {
        val r = search(buildJsonObject { put("colors", strings("yellow")) })
        assertEquals(listOf(sneakersId), r.itemIdsInJson())
        assertEquals(setOf(sneakersId), r.itemIds)
    }

    @Test
    fun searchItemShapeHasDocumentedFields() {
        val r = search(buildJsonObject { put("colors", strings("grey")) })
        val item = r.obj()["items"]!!.jsonArray.single().jsonObject
        assertEquals(setOf("id", "name", "category", "subcategory", "colors", "wash_status", "wear_count", "match"), item.keys)
        assertEquals("Outerwear", item["category"]!!.jsonPrimitive.content)
        assertEquals("Coat", item["subcategory"]!!.jsonPrimitive.content)
    }

    @Test
    fun searchByKnownColorWithNoItemsIsEmptyNotError() {
        val r = search(buildJsonObject { put("colors", strings("purple")) })
        assertTrue(r.itemIdsInJson().isEmpty())
        assertEquals(0, r.obj()["total_matches"]!!.jsonPrimitive.int)
        assertNull(r.obj()["error"])
    }

    @Test
    fun searchByUnknownColorReturnsErrorWithValidValues() {
        val r = search(buildJsonObject { put("colors", strings("chartreuse")) })
        assertNotNull(r.obj()["error"])
        val valid = r.obj()["valid_values"]!!.jsonArray.map { it.jsonPrimitive.content }
        assertTrue("Yellow" in valid)
        assertTrue(r.itemIds.isEmpty())
    }

    @Test
    fun searchBySubcategoryNameFiltersByItsCategory() {
        val r = search(buildJsonObject { put("category", "jacket") })
        assertEquals(listOf(overcoatId), r.itemIdsInJson())
    }

    @Test
    fun searchByUnknownCategoryReturnsError() {
        val r = search(buildJsonObject { put("category", "spacesuits") })
        assertNotNull(r.obj()["error"])
        assertNotNull(r.obj()["valid_values"])
    }

    @Test
    fun searchBlueMatchesRoyalAndSkyBlueButNavyOnlyByName() {
        val blue = search(buildJsonObject { put("colors", strings("blue")) })
        assertEquals(listOf(shirtId), blue.itemIdsInJson())
        val navy = search(buildJsonObject { put("colors", strings("navy")) })
        assertTrue(navy.itemIdsInJson().isEmpty())
        assertNull(navy.obj()["error"])
    }

    private fun ToolResult.matches(): Map<Long, String> = obj()["items"]!!.jsonArray.associate {
        it.jsonObject["id"]!!.jsonPrimitive.content.toLong() to it.jsonObject["match"]!!.jsonPrimitive.content
    }

    @Test
    fun colorSearchMatchesDescriptionAfterTaggedItems() {
        val tanId = runBlocking { insertItem("Trainers", "Footwear", "Sneakers", listOf("Tan"), description = "Yellow sneakers with white soles") }
        val r = search(buildJsonObject { put("colors", strings("yellow")) })
        assertEquals(listOf(sneakersId, tanId), r.itemIdsInJson())
        assertEquals("tagged", r.matches()[sneakersId])
        assertEquals("description", r.matches()[tanId])
        assertEquals(2, r.obj()["total_matches"]!!.jsonPrimitive.int)
    }

    @Test
    fun colorSearchTaggedWinsWhenDescriptionAlsoMatches() {
        runBlocking { insertItem("Mustard top", "Tops", "T-Shirt", listOf("Yellow"), description = "A yellow top") }
        val r = search(buildJsonObject { put("colors", strings("yellow")) })
        assertTrue(r.matches().values.all { it == "tagged" })
    }

    @Test
    fun colorSearchIgnoresYellowishInDescription() {
        runBlocking { insertItem("Odd", "Tops", "T-Shirt", listOf("Tan"), description = "A yellowish shirt") }
        val r = search(buildJsonObject { put("colors", strings("yellow")) })
        assertEquals(listOf(sneakersId), r.itemIdsInJson())
    }

    @Test
    fun colorSearchExcludesItemsWithNoTagOrWordMatch() {
        runBlocking { insertItem("Plain", "Tops", "T-Shirt", listOf("Tan"), description = "A plain cotton shirt") }
        val r = search(buildJsonObject { put("colors", strings("yellow")) })
        assertEquals(listOf(sneakersId), r.itemIdsInJson())
    }

    @Test
    fun colorSearchMatchesImageCaptionAndMultiWordColor() {
        val id = runBlocking {
            val i = insertItem("Cap", "Tops", "T-Shirt", listOf("Tan"))
            val e = db.clothingDao().getItemDetailsByIds(listOf(i)).first().item
            db.clothingDao().updateClothingItem(e.copy(imageCaption = "a Royal Blue hat"))
            i
        }
        val r = search(buildJsonObject { put("colors", strings("royal blue")) })
        assertEquals(setOf(shirtId, id), r.itemIdsInJson().toSet())
        assertEquals("description", r.matches()[id])
    }

    @Test
    fun wearHistoryDeduplicatesRecentDates() {
        wear(sneakersId, "2026-02-10")
        wear(sneakersId, "2026-02-10")
        wear(sneakersId, "2026-01-05")
        val r = run("wear_history", buildJsonObject { put("item_id", sneakersId) })
        assertEquals(
            listOf("2026-02-10", "2026-01-05"),
            r.obj()["recent_dates"]!!.jsonArray.map { it.jsonPrimitive.content },
        )
    }

    @Test
    fun searchColorsMatchAnyRequested() {
        val r = search(buildJsonObject { put("colors", strings("yellow", "grey")) })
        assertEquals(setOf(sneakersId, overcoatId), r.itemIdsInJson().toSet())
    }

    @Test
    fun searchQueryFallsBackToSubstringWhenEncoderUnavailable() {
        val r = search(buildJsonObject { put("query", "overcoat") })
        assertEquals(listOf(overcoatId), r.itemIdsInJson())
    }

    @Test
    fun searchQueryRanksByEmbeddingSimilarityWhenEncoderAvailable() {
        runBlocking {
            fun unit(i: Int) = FloatArray(EmbeddingEncoder.HIDDEN_SIZE).also { it[i] = 1f }
            fun embed(id: Long, v: FloatArray) = ItemEmbeddingEntity(
                itemId = id,
                embeddingBlob = v.toByteArrayLE(),
                modelVersion = EmbeddingWork.MODEL_VERSION,
                inputSnapshot = "x",
                embeddedAt = Clock.System.now(),
            )
            db.embeddingDao().upsert(embed(sneakersId, unit(0)))
            db.embeddingDao().upsert(embed(overcoatId, unit(1)))
            db.embeddingDao().upsert(embed(shirtId, unit(2)))
            index.load()
        }
        encoder = FakeEncoder(isAvailable = true, vector = FloatArray(EmbeddingEncoder.HIDDEN_SIZE).also { it[1] = 1f })
        registry = buildRegistry()
        val r = search(buildJsonObject { put("query", "something warm") })
        assertEquals(overcoatId, r.itemIdsInJson().first())
        assertEquals(3, r.obj()["total_matches"]!!.jsonPrimitive.int)
    }

    @Test
    fun searchRethrowsCancellationReturnedInsideTheEncoderResult() {
        runBlocking {
            db.embeddingDao().upsert(
                ItemEmbeddingEntity(
                    itemId = sneakersId,
                    embeddingBlob = FloatArray(EmbeddingEncoder.HIDDEN_SIZE).also { it[0] = 1f }.toByteArrayLE(),
                    modelVersion = EmbeddingWork.MODEL_VERSION,
                    inputSnapshot = "x",
                    embeddedAt = Clock.System.now(),
                ),
            )
            index.load()
        }
        encoder = object : EmbeddingEncoder {
            override val isAvailable = true
            override val modelVersion = EmbeddingWork.MODEL_VERSION
            override suspend fun encode(text: String): Result<FloatArray> =
                Result.failure(CancellationException("cancelled"))
        }
        registry = buildRegistry()
        assertFailsWith<CancellationException> { search(buildJsonObject { put("query", "anything") }) }
    }

    @Test
    fun fractionalIdIsRejectedNotTruncated() {
        val fractional = run("wear_history", buildJsonObject { put("item_id", sneakersId + 0.5) })
        assertNotNull(fractional.obj()["error"])
        val integral = run("wear_history", buildJsonObject { put("item_id", sneakersId.toDouble()) })
        assertNull(integral.obj()["error"])
        assertEquals(setOf(sneakersId), integral.itemIds)
    }

    @Test
    fun fractionalLimitIsNotTruncated() {
        runBlocking { repeat(5) { insertItem("Extra yellow $it", "Tops", "T-Shirt", listOf("Yellow")) } }
        // 1.7 must not become 1; an invalid limit falls back to the default (10).
        val r = search(buildJsonObject { put("colors", strings("yellow")); put("limit", 1.7) })
        assertEquals(6, r.itemIdsInJson().size)
    }

    // ─── other tools ──────────────────────────────────────────────────────────

    @Test
    fun getItemsReturnsRequestedItems() {
        val r = run("get_items", buildJsonObject { put("item_ids", strings(shirtId.toString(), "99999")) })
        assertEquals(listOf(shirtId), r.itemIdsInJson())
        assertEquals(setOf(shirtId), r.itemIds)
    }

    @Test
    fun wearHistoryReportsCountAndLastWorn() {
        wear(sneakersId, "2026-01-05")
        wear(sneakersId, "2026-02-10")
        val r = run("wear_history", buildJsonObject { put("item_id", sneakersId) })
        assertEquals(2, r.obj()["wear_count"]!!.jsonPrimitive.int)
        assertEquals("2026-02-10", r.obj()["last_worn"]!!.jsonPrimitive.content)
        assertEquals(2, r.obj()["recent_dates"]!!.jsonArray.size)
        assertEquals(setOf(sneakersId), r.itemIds)
    }

    @Test
    fun wearHistoryUnknownItemIsError() {
        val r = run("wear_history", buildJsonObject { put("item_id", 424242) })
        assertNotNull(r.obj()["error"])
    }

    @Test
    fun outfitsWornOnBadDateReturnsError() {
        val r = run("outfits_worn_on", buildJsonObject { put("date", "2026-13-40") })
        assertNotNull(r.obj()["error"])
    }

    @Test
    fun outfitsWornOnListsItemsOfThatDay() {
        wear(overcoatId, "2026-03-03")
        val r = run("outfits_worn_on", buildJsonObject { put("date", "2026-03-03") })
        assertEquals(1, r.obj()["outfits"]!!.jsonArray.size)
        assertEquals(setOf(overcoatId), r.itemIds)
    }

    @Test
    fun outfitsWornOnCapsItemsPerOutfitAndKeepsIdsAligned() {
        val ids = runBlocking { (1..25).map { insertItem("Layer $it", "Tops", "T-Shirt", listOf("Grey")) } }
        runBlocking { db.logDao().insertAdHocLogAndSnapshot(OutfitLogEntity(date = "2026-04-04"), ids) }
        val r = run("outfits_worn_on", buildJsonObject { put("date", "2026-04-04") })
        val items = r.obj()["outfits"]!!.jsonArray.single().jsonObject["items"]!!.jsonArray
        assertEquals(20, items.size)
        val jsonIds = items.map { it.jsonObject["id"]!!.jsonPrimitive.content.toLong() }.toSet()
        assertEquals(jsonIds, r.itemIds)
    }

    @Test
    fun laundryListReturnsOnlyDirtyItems() {
        runBlocking { db.clothingDao().updateWashStatus(overcoatId, "Dirty", "2026-01-01T00:00:00Z") }
        val r = run("laundry_list")
        assertEquals(listOf(overcoatId), r.itemIdsInJson())
    }

    @Test
    fun itemsNotWornSinceExcludesRecentlyWorn() {
        // Dates are days away from the 30-day cutoff, so the UTC vs system-zone day never matters.
        fun daysAgo(n: Int) = (Clock.System.now() - n.days).toString().substringBefore('T')
        wear(sneakersId, daysAgo(5))
        wear(overcoatId, daysAgo(100))
        val r = run("items_not_worn_since", buildJsonObject { put("days", 30) })
        assertEquals(setOf(overcoatId, shirtId), r.itemIdsInJson().toSet())
    }

    // ─── search_items filters ─────────────────────────────────────────────────

    private fun archive(id: Long) = runBlocking {
        db.clothingDao().updateItemStatus(id, "Sold", "2026-01-01T00:00:00Z")
    }

    @Test
    fun searchExcludesArchivedItemsUnlessRequested() {
        archive(overcoatId)
        val default = search(buildJsonObject { put("colors", strings("grey")) })
        assertTrue(default.itemIdsInJson().isEmpty())
        val explicitFalse = search(buildJsonObject { put("colors", strings("grey")); put("include_archived", false) })
        assertTrue(explicitFalse.itemIdsInJson().isEmpty())
        val included = search(buildJsonObject { put("colors", strings("grey")); put("include_archived", true) })
        assertEquals(listOf(overcoatId), included.itemIdsInJson())
    }

    @Test
    fun searchLimitDefaultsToTenAndCapsAtTwenty() {
        runBlocking { repeat(25) { insertItem("Extra yellow $it", "Tops", "T-Shirt", listOf("Yellow")) } }
        val yellow = strings("yellow")
        val default = search(buildJsonObject { put("colors", yellow) })
        assertEquals(10, default.itemIdsInJson().size)
        assertEquals(26, default.obj()["total_matches"]!!.jsonPrimitive.int)
        val capped = search(buildJsonObject { put("colors", yellow); put("limit", 50) })
        assertEquals(20, capped.itemIdsInJson().size)
        assertEquals(20, capped.itemIds.size)
        val small = search(buildJsonObject { put("colors", yellow); put("limit", 3) })
        assertEquals(3, small.itemIdsInJson().size)
    }

    @Test
    fun searchFiltersBySeason() {
        runBlocking { db.clothingDao().updateItemSeasons(shirtId, listOf(idOf("seasons", "Winter"))) }
        val r = search(buildJsonObject { put("season", "winter") })
        assertEquals(listOf(shirtId), r.itemIdsInJson())
        val none = search(buildJsonObject { put("season", "Summer") })
        assertTrue(none.itemIdsInJson().isEmpty())
        assertNotNull(search(buildJsonObject { put("season", "monsoon") }).obj()["error"])
    }

    @Test
    fun searchFiltersByOccasion() {
        runBlocking { db.clothingDao().updateItemOccasions(sneakersId, listOf(idOf("occasions", "Casual"))) }
        val r = search(buildJsonObject { put("occasion", "casual") })
        assertEquals(listOf(sneakersId), r.itemIdsInJson())
        val none = search(buildJsonObject { put("occasion", "Formal") })
        assertTrue(none.itemIdsInJson().isEmpty())
        assertNotNull(search(buildJsonObject { put("occasion", "gala") }).obj()["error"])
    }

    @Test
    fun searchCategoryMatchIsCaseInsensitive() {
        val r = search(buildJsonObject { put("category", "outerwear") })
        assertEquals(listOf(overcoatId), r.itemIdsInJson())
        val upper = search(buildJsonObject { put("category", "OUTERWEAR") })
        assertEquals(listOf(overcoatId), upper.itemIdsInJson())
    }

    @Test
    fun mostWornFiltersByCategoryInMemory() {
        wear(sneakersId, "2026-01-01")
        wear(sneakersId, "2026-01-02")
        wear(shirtId, "2026-01-03")
        val all = run("most_worn")
        assertEquals(sneakersId, all.itemIdsInJson().first())
        val tops = run("most_worn", buildJsonObject { put("category", "Tops") })
        assertEquals(listOf(shirtId), tops.itemIdsInJson())
    }

    @Test
    fun wardrobeSummaryCountsItems() {
        runBlocking { insertItem("Old sold hat", "Tops", "T-Shirt", listOf("Red")) }
            .also { archive(it) }
        val r = run("wardrobe_summary")
        assertEquals(4, r.obj()["total_items"]!!.jsonPrimitive.int)
        assertEquals(3, r.obj()["active_items"]!!.jsonPrimitive.int)
        assertEquals(1, r.obj()["by_category"]!!.jsonObject["Footwear"]!!.jsonPrimitive.int)
        assertEquals(1, r.obj()["by_color"]!!.jsonObject["Yellow"]!!.jsonPrimitive.int)
    }
}
