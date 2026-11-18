package com.closet.core.data.dao

import androidx.room.useReaderConnection
import androidx.room.useWriterConnection
import androidx.sqlite.SQLiteStatement
import com.closet.core.data.ClothingDatabase
import com.closet.core.data.TestDatabase
import com.closet.core.data.model.ClothingItemEntity
import com.closet.core.data.model.OutfitEntity
import com.closet.core.data.model.OutfitItemEntity
import com.closet.core.data.model.OutfitLogEntity
import com.closet.core.data.util.toByteArrayLE
import com.closet.core.data.util.toFloatArrayLE
import com.closet.core.data.worker.EmbeddingWork
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock

/** Co-wear, saved-outfit, occasion and hex queries of [RecommendationDao]. */
class RecommendationDaoCoWearTest {

    private lateinit var testDb: TestDatabase
    private val db: ClothingDatabase get() = testDb.db
    private lateinit var dao: RecommendationDao

    @Before
    fun setUp() {
        testDb = TestDatabase()
        dao = db.recommendationDao()
    }

    @After
    fun tearDown() = testDb.close()

    private fun insertItem(name: String): Long = runBlocking {
        db.clothingDao().insertClothingItem(
            ClothingItemEntity(name = name, createdAt = Clock.System.now(), updatedAt = Clock.System.now())
        )
    }

    private fun insertOutfit(vararg itemIds: Long): Long = runBlocking {
        val id = db.outfitDao().insertOutfit(OutfitEntity())
        db.outfitDao().insertOutfitItems(itemIds.map { OutfitItemEntity(outfitId = id, clothingItemId = it) })
        id
    }

    /** Logs a wear of an outfit made of [itemIds]; the snapshot rows come from insertLogAndSnapshot. */
    private fun logWear(date: String, vararg itemIds: Long): Long = runBlocking {
        val outfitId = insertOutfit(*itemIds)
        db.logDao().insertLogAndSnapshot(OutfitLogEntity(outfitId = outfitId, date = date))
    }

    private fun exec(sql: String, bind: (SQLiteStatement) -> Unit = {}): Long = runBlocking {
        db.useWriterConnection { c ->
            c.usePrepared(sql) { s -> bind(s); s.step() }
            c.usePrepared("SELECT last_insert_rowid()") { s -> s.step(); s.getLong(0) }
        }
    }

    // --- pairs ---

    @Test
    fun coWearPairsAreUnorderedAndUniquePerLog() = runBlocking {
        val a = insertItem("a")
        val b = insertItem("b")
        val c = insertItem("c")
        logWear("2026-01-01", c, b, a) // insertion order must not matter
        logWear("2026-01-02", a, b)
        val rows = dao.getCoWearPairs(listOf(a, b, c))
        assertEquals(mapOf((a to b) to 2, (a to c) to 1, (b to c) to 1), rows.associate { (it.aId to it.bId) to it.count })
        assertTrue(rows.all { it.aId < it.bId })
    }

    @Test
    fun coWearReflectsSnapshotEvenIfSavedOutfitChangesLater() = runBlocking {
        val a = insertItem("a")
        val b = insertItem("b")
        val c = insertItem("c")
        val outfitId = insertOutfit(a, b)
        db.logDao().insertLogAndSnapshot(OutfitLogEntity(outfitId = outfitId, date = "2026-01-01"))

        // Changing the live saved outfit membership to A and C must not alter historical co-wear
        db.outfitDao().deleteItemsForOutfit(outfitId)
        db.outfitDao().insertOutfitItems(
            listOf(OutfitItemEntity(outfitId = outfitId, clothingItemId = a), OutfitItemEntity(outfitId = outfitId, clothingItemId = c))
        )

        val pairs = dao.getCoWearPairs(listOf(a, b, c)).associate { (it.aId to it.bId) to it.count }
        assertEquals(mapOf((a to b) to 1), pairs)
    }

    @Test
    fun coWearPairsOnlyCountCandidateItems() = runBlocking {
        val a = insertItem("a")
        val b = insertItem("b")
        val c = insertItem("c")
        logWear("2026-01-01", a, b, c)
        assertEquals(listOf(a to b), dao.getCoWearPairs(listOf(a, b)).map { it.aId to it.bId })
    }

    @Test
    fun itemLogCountsAndTotal() = runBlocking {
        val a = insertItem("a")
        val b = insertItem("b")
        logWear("2026-01-01", a, b)
        logWear("2026-01-02", a)
        assertEquals(mapOf(a to 2, b to 1), dao.getItemLogCounts(listOf(a, b)).associate { it.clothingItemId to it.count })
        assertEquals(mapOf(a to 2), dao.getItemLogCounts(listOf(a)).associate { it.clothingItemId to it.count })
        assertEquals(2, dao.getTotalLogCount())
    }

    // --- saved outfits (separate from logs) ---

    @Test
    fun savedOutfitsAreCountedSeparatelyFromLogs() = runBlocking {
        val a = insertItem("a")
        val b = insertItem("b")
        val c = insertItem("c")
        insertOutfit(a, b)
        insertOutfit(c, b, a)
        assertEquals(0, dao.getTotalLogCount())
        assertTrue(dao.getCoWearPairs(listOf(a, b, c)).isEmpty())
        assertEquals(2, dao.getSavedOutfitCount())
        val pairs = dao.getSavedOutfitPairs(listOf(a, b, c)).associate { (it.aId to it.bId) to it.count }
        assertEquals(mapOf((a to b) to 2, (a to c) to 1, (b to c) to 1), pairs)
        assertEquals(
            mapOf(a to 2, b to 2, c to 1),
            dao.getSavedItemCounts(listOf(a, b, c)).associate { it.clothingItemId to it.count }
        )
        assertEquals(listOf(a to b), dao.getSavedOutfitPairs(listOf(a, b)).map { it.aId to it.bId })
    }

    // --- occasions and hex ---

    @Test
    fun itemOccasionNamesReturnTaggedOccasions() = runBlocking {
        val a = insertItem("a")
        val b = insertItem("b")
        val formal = exec("INSERT INTO occasions (name, icon) VALUES ('ZZ Formal', 'x')")
        val casual = exec("INSERT INTO occasions (name, icon) VALUES ('ZZ Casual', 'x')")
        for ((item, occ) in listOf(a to formal, a to casual, b to casual)) {
            exec("INSERT INTO clothing_item_occasions (clothing_item_id, occasion_id) VALUES (?, ?)") {
                it.bindLong(1, item); it.bindLong(2, occ)
            }
        }
        val rows = dao.getItemOccasionNames(listOf(a)).map { it.clothingItemId to it.occasionName }.toSet()
        assertEquals(setOf(a to "ZZ Formal", a to "ZZ Casual"), rows)
    }

    @Test
    fun itemColorHexesSkipNullHex() = runBlocking {
        val a = insertItem("a")
        val withHex = exec("INSERT INTO colors (name, hex, color_family) VALUES ('ZZ Navy', '#112233', 'Cool')")
        val noHex = exec("INSERT INTO colors (name, hex) VALUES ('ZZ Mystery', NULL)")
        for (color in listOf(withHex, noHex)) {
            exec("INSERT INTO clothing_item_colors (clothing_item_id, color_id) VALUES (?, ?)") {
                it.bindLong(1, a); it.bindLong(2, color)
            }
        }
        assertEquals(listOf(ItemColorHex(a, "#112233", "Cool")), dao.getItemColorHexes(listOf(a)))
    }

    @Test
    fun itemColorHexesCarryTheFamilyInColorIdOrder() = runBlocking {
        val a = insertItem("a")
        val b = insertItem("b")
        val first = exec("INSERT INTO colors (name, hex, color_family) VALUES ('ZZ One', '#000080', 'Neutral')")
        val second = exec("INSERT INTO colors (name, hex, color_family) VALUES ('ZZ Two', '#FF0000', 'Warm')")
        // Tag in reverse color order, interleaving items, so only the ORDER BY gives the result.
        for ((item, color) in listOf(b to second, a to second, b to first, a to first)) {
            exec("INSERT INTO clothing_item_colors (clothing_item_id, color_id) VALUES (?, ?)") {
                it.bindLong(1, item); it.bindLong(2, color)
            }
        }
        assertEquals(
            listOf(
                ItemColorHex(a, "#000080", "Neutral"), ItemColorHex(a, "#FF0000", "Warm"),
                ItemColorHex(b, "#000080", "Neutral"), ItemColorHex(b, "#FF0000", "Warm"),
            ),
            dao.getItemColorHexes(listOf(b, a)),
        )
    }

    // --- embeddings and subcategories (Delivery 3) ---

    @Test
    fun getItemEmbeddingsReturnsMatchingModelVersionAndDecodesCorrectly() = runBlocking {
        val a = insertItem("a")
        val b = insertItem("b")
        val c = insertItem("c")
        val vecA = floatArrayOf(0.1f, 0.2f, 0.3f).toByteArrayLE()
        val vecB = floatArrayOf(0.4f, 0.5f, 0.6f).toByteArrayLE()
        val vecC = floatArrayOf(0.7f, 0.8f, 0.9f).toByteArrayLE()

        exec("INSERT INTO item_embeddings (item_id, embedding_blob, model_version, embedded_at) VALUES (?, ?, ?, 1000)") {
            it.bindLong(1, a); it.bindBlob(2, vecA); it.bindText(3, EmbeddingWork.MODEL_VERSION)
        }
        exec("INSERT INTO item_embeddings (item_id, embedding_blob, model_version, embedded_at) VALUES (?, ?, ?, 1000)") {
            it.bindLong(1, b); it.bindBlob(2, vecB); it.bindText(3, "old-model-version")
        }
        exec("INSERT INTO item_embeddings (item_id, embedding_blob, model_version, embedded_at) VALUES (?, ?, ?, 1000)") {
            it.bindLong(1, c); it.bindBlob(2, vecC); it.bindText(3, EmbeddingWork.MODEL_VERSION)
        }

        val rows = dao.getItemEmbeddings(listOf(a, b, c), EmbeddingWork.MODEL_VERSION)
        assertEquals(2, rows.size)
        val map = rows.associate { it.itemId to it.embeddingBlob.toFloatArrayLE() }
        assertTrue(a in map)
        assertTrue(c in map)
        assertTrue(b !in map) // skipped because model_version != current
        assertEquals(0.1f, map[a]!![0], 1e-5f)
        assertEquals(0.2f, map[a]!![1], 1e-5f)
        assertEquals(0.3f, map[a]!![2], 1e-5f)
    }

    @Test
    fun subcategoryCoWearPairsAndCountsAggregateAcrossWardrobe() = runBlocking {
        // Look up seeded subcategories
        val (subA, catA) = db.useReaderConnection { c ->
            c.usePrepared("SELECT id, category_id FROM subcategories ORDER BY id LIMIT 1") { s ->
                s.step(); s.getLong(0) to s.getLong(1)
            }
        }
        val (subB, catB) = db.useReaderConnection { c ->
            c.usePrepared("SELECT id, category_id FROM subcategories ORDER BY id LIMIT 1 OFFSET 1") { s ->
                s.step(); s.getLong(0) to s.getLong(1)
            }
        }

        val a1 = db.clothingDao().insertClothingItem(
            ClothingItemEntity(name = "a1", categoryId = catA, subcategoryId = subA, createdAt = Clock.System.now(), updatedAt = Clock.System.now())
        )
        val a2 = db.clothingDao().insertClothingItem(
            ClothingItemEntity(name = "a2", categoryId = catA, subcategoryId = subA, createdAt = Clock.System.now(), updatedAt = Clock.System.now())
        )
        val b1 = db.clothingDao().insertClothingItem(
            ClothingItemEntity(name = "b1", categoryId = catB, subcategoryId = subB, createdAt = Clock.System.now(), updatedAt = Clock.System.now())
        )

        // Log 1: a1 + b1
        logWear("2026-01-01", a1, b1)
        // Log 2: a2 + b1
        logWear("2026-01-02", a2, b1)

        val smallerSub = minOf(subA, subB)
        val largerSub = maxOf(subA, subB)

        val pairs = dao.getSubcategoryCoWearPairs().associate { (it.aId to it.bId) to it.count }
        assertEquals(2, pairs[smallerSub to largerSub])

        val counts = dao.getSubcategoryLogCounts().associate { it.clothingItemId to it.count }
        assertEquals(2, counts[subA])
        assertEquals(2, counts[subB])
    }

    @Test
    fun subcategorySavedOutfitPairsAndCountsAggregateAcrossWardrobe() = runBlocking {
        val (subA, catA) = db.useReaderConnection { c ->
            c.usePrepared("SELECT id, category_id FROM subcategories ORDER BY id LIMIT 1") { s ->
                s.step(); s.getLong(0) to s.getLong(1)
            }
        }
        val (subB, catB) = db.useReaderConnection { c ->
            c.usePrepared("SELECT id, category_id FROM subcategories ORDER BY id LIMIT 1 OFFSET 1") { s ->
                s.step(); s.getLong(0) to s.getLong(1)
            }
        }

        val a1 = db.clothingDao().insertClothingItem(
            ClothingItemEntity(name = "a1", categoryId = catA, subcategoryId = subA, createdAt = Clock.System.now(), updatedAt = Clock.System.now())
        )
        val b1 = db.clothingDao().insertClothingItem(
            ClothingItemEntity(name = "b1", categoryId = catB, subcategoryId = subB, createdAt = Clock.System.now(), updatedAt = Clock.System.now())
        )

        insertOutfit(a1, b1)

        val smallerSub = minOf(subA, subB)
        val largerSub = maxOf(subA, subB)

        val pairs = dao.getSubcategorySavedOutfitPairs().associate { (it.aId to it.bId) to it.count }
        assertEquals(1, pairs[smallerSub to largerSub])

        val counts = dao.getSubcategorySavedItemCounts().associate { it.clothingItemId to it.count }
        assertEquals(1, counts[subA])
        assertEquals(1, counts[subB])
    }
}
