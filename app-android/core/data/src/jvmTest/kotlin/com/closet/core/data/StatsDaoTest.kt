package com.closet.core.data

import androidx.room.useWriterConnection
import androidx.sqlite.SQLiteStatement
import androidx.room.execSQL
import com.closet.core.data.dao.StatsDao
import com.closet.core.data.model.ClothingItemEntity
import com.closet.core.data.model.OutfitEntity
import com.closet.core.data.model.OutfitItemEntity
import com.closet.core.data.model.OutfitLogEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.time.Clock

/**
 * [StatsDao] tests on the JVM, against a database built with the production builder
 * ([TestDatabase]: bundled driver, `configureClosetDatabase` callbacks).
 *
 * Each test starts with a fresh database file. Test data is inserted via the production DAOs
 * (clothing items, outfits, logs) and raw SQL (lookup tables such as categories).
 *
 * The seeded lookup rows stay (the stats queries are driven by `clothing_items`, and the test
 * categories get their own ids), but [setUp] drops the four category-consistency triggers: these
 * tests exercise the stats queries with items that have a category and no subcategory, which the
 * triggers reject. [FreshInstallSchemaTest] covers the triggers.
 */
class StatsDaoTest {

    private lateinit var testDb: TestDatabase
    private val db: ClothingDatabase get() = testDb.db
    private lateinit var dao: StatsDao

    @Before
    fun setUp() {
        testDb = TestDatabase()
        dao = db.statsDao()
        runBlocking {
            db.useWriterConnection { connection ->
                for (trigger in listOf(
                    "check_clothing_item_category_consistency_insert",
                    "verify_subcategory_parent_category_insert",
                    "check_clothing_item_category_consistency_update",
                    "verify_subcategory_parent_category_update",
                )) {
                    connection.execSQL("DROP TRIGGER `$trigger`")
                }
            }
        }
    }

    @After
    fun tearDown() {
        testDb.close()
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private fun insertItem(
        name: String,
        status: String = "Active",
        washStatus: String = "Clean",
        categoryId: Long? = null,
        subcategoryId: Long? = null,
        purchasePrice: Double? = null
    ): Long = runBlocking {
        db.clothingDao().insertClothingItem(
            ClothingItemEntity(
                name = name,
                status = com.closet.core.data.model.ClothingStatus.fromString(status),
                washStatus = com.closet.core.data.model.WashStatus.fromString(washStatus),
                categoryId = categoryId,
                subcategoryId = subcategoryId,
                purchasePrice = purchasePrice,
                createdAt = Clock.System.now(),
                updatedAt = Clock.System.now()
            )
        )
    }

    /** Inserts a subcategory via raw SQL. */
    private fun insertSubcategory(categoryId: Long, name: String): Long =
        insertReturningId("INSERT INTO subcategories (category_id, name, sort_order) VALUES (?, ?, 0)") {
            it.bindLong(1, categoryId)
            it.bindText(2, name)
        }

    /** Inserts a category via raw SQL (no production DAO exposes category inserts). */
    private fun insertCategory(name: String): Long =
        insertReturningId("INSERT INTO categories (name, sort_order) VALUES (?, 0)") {
            it.bindText(1, name)
        }

    private fun insertReturningId(sql: String, bind: (SQLiteStatement) -> Unit): Long =
        runBlocking {
            db.useWriterConnection { connection ->
                connection.usePrepared(sql) { statement ->
                    bind(statement)
                    statement.step()
                }
                connection.usePrepared("SELECT last_insert_rowid()") { statement ->
                    statement.step()
                    statement.getLong(0)
                }
            }
        }

    private fun insertOutfit(): Long = runBlocking {
        db.outfitDao().insertOutfit(OutfitEntity())
    }

    private fun linkItemToOutfit(outfitId: Long, itemId: Long) = runBlocking {
        db.outfitDao().insertOutfitItems(listOf(OutfitItemEntity(outfitId = outfitId, clothingItemId = itemId)))
    }

    /**
     * Logs a wear the way the app does ([LogDao.insertLogAndSnapshot]): the log row plus a snapshot
     * of the outfit's current items in `outfit_log_items`, which the stats queries count. Link
     * items to the outfit before logging it.
     */
    private fun logOutfit(outfitId: Long, date: String) = runBlocking {
        db.logDao().insertLogAndSnapshot(OutfitLogEntity(outfitId = outfitId, date = date))
    }

    // ─── getStatsOverview ─────────────────────────────────────────────────────

    @Test
    fun getStatsOverview_emptyDatabase_returnsAllZeros() = runBlocking {
        val overview = dao.getStatsOverview(null).first()
        assertEquals(0, overview.totalItems)
        assertEquals(0, overview.wornItems)
        assertEquals(0, overview.neverWornItems)
    }

    @Test
    fun getStatsOverview_withActiveItems_returnsTotalCount() = runBlocking {
        insertItem("Shirt")
        insertItem("Jeans")
        val overview = dao.getStatsOverview(null).first()
        assertEquals(2, overview.totalItems)
    }

    @Test
    fun getStatsOverview_excludesNonActiveItems() = runBlocking {
        insertItem("Active shirt", status = "Active")
        insertItem("Sold jeans", status = "Sold")
        insertItem("Donated coat", status = "Donated")
        val overview = dao.getStatsOverview(null).first()
        assertEquals(1, overview.totalItems)
    }

    @Test
    fun getStatsOverview_correctlyCountsWornVsNeverWorn() = runBlocking {
        val shirtId = insertItem("Shirt")
        insertItem("Unworn Jeans")

        val outfitId = insertOutfit()
        linkItemToOutfit(outfitId, shirtId)
        logOutfit(outfitId, "2026-01-01")

        val overview = dao.getStatsOverview(null).first()
        assertEquals(2, overview.totalItems)
        assertEquals(1, overview.wornItems)
        assertEquals(1, overview.neverWornItems)
    }

    @Test
    fun getStatsOverview_withFromDate_onlyCountsWearsAfterDate() = runBlocking {
        val shirtId = insertItem("Shirt")
        val outfitId = insertOutfit()
        linkItemToOutfit(outfitId, shirtId)
        logOutfit(outfitId, "2025-06-01") // before the filter date

        // Shirt exists but wasn't worn after 2026-01-01
        val overview = dao.getStatsOverview("2026-01-01").first()
        assertEquals(1, overview.totalItems)
        assertEquals(0, overview.wornItems)
        assertEquals(1, overview.neverWornItems)
    }

    @Test
    fun getStatsOverview_sumsTotalValueOfActiveItems() = runBlocking {
        insertItem("Shirt", purchasePrice = 29.99)
        insertItem("Jeans", purchasePrice = 59.99)
        insertItem("Sold coat", status = "Sold", purchasePrice = 100.0) // excluded
        val overview = dao.getStatsOverview(null).first()
        assertEquals(89.98, overview.totalValue!!, 0.01)
    }

    // ─── getWashStatusBreakdown ───────────────────────────────────────────────

    @Test
    fun getWashStatusBreakdown_emptyDatabase_returnsEmptyList() = runBlocking {
        val rows = dao.getWashStatusBreakdown().first()
        assertTrue(rows.isEmpty())
    }

    @Test
    fun getWashStatusBreakdown_returnsCorrectCleanAndDirtyCounts() = runBlocking {
        insertItem("Shirt 1", washStatus = "Clean")
        insertItem("Shirt 2", washStatus = "Clean")
        insertItem("Jeans", washStatus = "Dirty")

        val rows = dao.getWashStatusBreakdown().first()
        val cleanRow = rows.firstOrNull { it.label == "Clean" }
        val dirtyRow = rows.firstOrNull { it.label == "Dirty" }

        assertEquals(2, cleanRow?.count)
        assertEquals(1, dirtyRow?.count)
    }

    @Test
    fun getWashStatusBreakdown_excludesNonActiveItems() = runBlocking {
        insertItem("Active dirty shirt", status = "Active", washStatus = "Dirty")
        insertItem("Sold dirty shirt", status = "Sold", washStatus = "Dirty")

        val rows = dao.getWashStatusBreakdown().first()
        val dirtyRow = rows.firstOrNull { it.label == "Dirty" }
        assertEquals(1, dirtyRow?.count)
    }

    // ─── getCategorySubcategoryBreakdown ───────────────────────────────────────────────

    @Test
    fun getCategorySubcategoryBreakdown_emptyDatabase_returnsEmptyList() = runBlocking {
        val rows = dao.getCategorySubcategoryBreakdown().first()
        assertTrue(rows.isEmpty())
    }

    @Test
    fun getCategorySubcategoryBreakdown_groupsItemsByCategory() = runBlocking {
        val topsId = insertCategory("Tops")
        val bottomsId = insertCategory("Bottoms")

        insertItem("T-Shirt", categoryId = topsId)
        insertItem("Tank Top", categoryId = topsId)
        insertItem("Jeans", categoryId = bottomsId)

        val rows = dao.getCategorySubcategoryBreakdown().first()
        val topsRow = rows.firstOrNull { it.categoryLabel == "Tops" }
        val bottomsRow = rows.firstOrNull { it.categoryLabel == "Bottoms" }

        assertEquals(2, topsRow?.count)
        assertEquals(1, bottomsRow?.count)
    }

    @Test
    fun getCategorySubcategoryBreakdown_itemsWithoutCategory_labelledUncategorized() = runBlocking {
        insertItem("Mystery item") // no categoryId
        val rows = dao.getCategorySubcategoryBreakdown().first()
        val row = rows.firstOrNull { it.categoryLabel == "Uncategorized" }
        assertEquals(1, row?.count)
    }

    @Test
    fun getCategorySubcategoryBreakdown_sortedDescendingByCount() = runBlocking {
        // Rows are ordered category ascending, then count descending within each category.
        val topsId = insertCategory("Tops")
        val bottomsId = insertCategory("Bottoms")
        val tShirtsId = insertSubcategory(topsId, "T-Shirts")
        val tanksId = insertSubcategory(topsId, "Tanks")
        insertItem("Tank", categoryId = topsId, subcategoryId = tanksId)
        insertItem("T-Shirt 1", categoryId = topsId, subcategoryId = tShirtsId)
        insertItem("T-Shirt 2", categoryId = topsId, subcategoryId = tShirtsId)
        insertItem("T-Shirt 3", categoryId = topsId, subcategoryId = tShirtsId)
        insertItem("Jeans", categoryId = bottomsId)

        val rows = dao.getCategorySubcategoryBreakdown().first()
        assertEquals(listOf("Bottoms", "Tops", "Tops"), rows.map { it.categoryLabel })
        val topsCounts = rows.filter { it.categoryLabel == "Tops" }.map { it.count }
        assertEquals(topsCounts.sortedDescending(), topsCounts)
        assertEquals(listOf(3, 1), topsCounts)
    }

    // ─── getMostWornItems ─────────────────────────────────────────────────────

    @Test
    fun getMostWornItems_emptyDatabase_returnsEmptyList() = runBlocking {
        val items = dao.getMostWornItems(null, 15).first()
        assertTrue(items.isEmpty())
    }

    @Test
    fun getMostWornItems_returnsItemsSortedByWearCountDescending() = runBlocking {
        val shirt = insertItem("Shirt")
        val jeans = insertItem("Jeans")

        val outfit1 = insertOutfit()
        linkItemToOutfit(outfit1, shirt)
        logOutfit(outfit1, "2026-01-01")
        logOutfit(outfit1, "2026-01-02")
        logOutfit(outfit1, "2026-01-03") // shirt worn 3×

        val outfit2 = insertOutfit()
        linkItemToOutfit(outfit2, jeans)
        logOutfit(outfit2, "2026-01-04") // jeans worn 1×

        val items = dao.getMostWornItems(null, 15).first()
        assertEquals(2, items.size)
        assertEquals(shirt, items[0].id)
        assertEquals(3, items[0].wearCount)
        assertEquals(jeans, items[1].id)
        assertEquals(1, items[1].wearCount)
    }

    @Test
    fun getMostWornItems_respectsLimit() = runBlocking {
        repeat(5) { i ->
            val itemId = insertItem("Item $i")
            val outfitId = insertOutfit()
            linkItemToOutfit(outfitId, itemId)
            logOutfit(outfitId, "2026-01-0${i + 1}")
        }
        val items = dao.getMostWornItems(null, 3).first()
        assertEquals(3, items.size)
    }

    @Test
    fun getMostWornItems_withFromDate_excludesWearsBeforeDate() = runBlocking {
        val shirtId = insertItem("Shirt")
        val outfitId = insertOutfit()
        linkItemToOutfit(outfitId, shirtId)
        logOutfit(outfitId, "2025-06-01") // before filter

        val items = dao.getMostWornItems("2026-01-01", 15).first()
        assertTrue(items.isEmpty())
    }

    // ─── getTotalOutfitsLogged ────────────────────────────────────────────────

    @Test
    fun getTotalOutfitsLogged_emptyDatabase_returnsZero() = runBlocking {
        assertEquals(0, dao.getTotalOutfitsLogged(null).first())
    }

    @Test
    fun getTotalOutfitsLogged_countsDistinctLogs() = runBlocking {
        val outfitId = insertOutfit()
        val otherOutfitId = insertOutfit()
        logOutfit(outfitId, "2026-01-01")
        logOutfit(outfitId, "2026-01-02")
        // Two logs on the same date (different outfits; one outfit can't be logged twice a day).
        logOutfit(otherOutfitId, "2026-01-02")
        logOutfit(outfitId, "2026-01-03")
        // COUNT(DISTINCT id) counts every log entry, not every date, so 4 rows → 4
        assertEquals(4, dao.getTotalOutfitsLogged(null).first())
    }

    @Test
    fun getTotalOutfitsLogged_withFromDate_onlyCountsLogsOnOrAfterDate() = runBlocking {
        val outfitId = insertOutfit()
        logOutfit(outfitId, "2025-12-01") // excluded
        logOutfit(outfitId, "2026-01-01") // included
        logOutfit(outfitId, "2026-02-01") // included
        assertEquals(2, dao.getTotalOutfitsLogged("2026-01-01").first())
    }
}
