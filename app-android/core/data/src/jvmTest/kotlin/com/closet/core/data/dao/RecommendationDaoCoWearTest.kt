package com.closet.core.data.dao

import androidx.room.useWriterConnection
import androidx.sqlite.SQLiteStatement
import com.closet.core.data.ClothingDatabase
import com.closet.core.data.TestDatabase
import com.closet.core.data.model.ClothingItemEntity
import com.closet.core.data.model.OutfitEntity
import com.closet.core.data.model.OutfitItemEntity
import com.closet.core.data.model.OutfitLogEntity
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
    fun coWearPairsCountEachLogOnceDespiteRepeatedSnapshotRows() = runBlocking {
        val a = insertItem("a")
        val b = insertItem("b")
        val logId = logWear("2026-01-01", a, b)
        // Counting rows instead of DISTINCT logs would see 2 for this pair if a second row existed.
        exec("INSERT OR IGNORE INTO outfit_log_items (outfit_log_id, clothing_item_id, outfit_name) VALUES (?, ?, 'x')") {
            it.bindLong(1, logId); it.bindLong(2, a)
        }
        assertEquals(1, dao.getCoWearPairs(listOf(a, b)).single().count)
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
        val withHex = exec("INSERT INTO colors (name, hex) VALUES ('ZZ Navy', '#112233')")
        val noHex = exec("INSERT INTO colors (name, hex) VALUES ('ZZ Mystery', NULL)")
        for (color in listOf(withHex, noHex)) {
            exec("INSERT INTO clothing_item_colors (clothing_item_id, color_id) VALUES (?, ?)") {
                it.bindLong(1, a); it.bindLong(2, color)
            }
        }
        assertEquals(listOf(a to "#112233"), dao.getItemColorHexes(listOf(a)).map { it.clothingItemId to it.hex })
    }
}
