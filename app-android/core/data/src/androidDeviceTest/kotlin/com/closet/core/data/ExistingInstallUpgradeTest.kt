package com.closet.core.data

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.useReaderConnection
import androidx.sqlite.SQLiteConnection
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.closet.core.data.model.ClothingItemEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Clock

/**
 * An install made by the previous build (Room over the framework SQLite, no driver set) must open
 * in place with the bundled driver: same `closet.db`, its rows kept, still the current version, and the
 * seeds not re-run (seeding is `onCreate` only).
 */
@RunWith(AndroidJUnit4::class)
class ExistingInstallUpgradeTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setUp() {
        ClothingDatabase.closeAndReset()
        context.deleteDatabase(DATABASE_NAME)
    }

    @After
    fun tearDown() {
        ClothingDatabase.closeAndReset()
        context.deleteDatabase(DATABASE_NAME)
    }

    @Test
    fun frameworkSqliteDatabase_opensWithBundledDriver_keepingItemsAndSeeds() = runBlocking {
        // The previous build: a driverless builder (framework SQLite) whose onCreate seeds lookups.
        val previous = Room.databaseBuilder(
            context,
            ClothingDatabase::class.java,
            context.getDatabasePath(DATABASE_NAME).absolutePath,
        )
            .addCallback(object : RoomDatabase.Callback() {
                override fun onCreate(connection: SQLiteConnection) {
                    DatabaseSeeder.seedAll(connection)
                }
            })
            .build()
        val now = Clock.System.now()
        val itemId = previous.clothingDao().insertClothingItem(
            ClothingItemEntity(name = "Upgrade Shirt", createdAt = now, updatedAt = now)
        )
        val categoriesBefore = count(previous, "categories")
        val subcategoriesBefore = count(previous, "subcategories")
        assertEquals("previous build should have seeded", 10, categoriesBefore)
        previous.close()

        val upgraded = ClothingDatabase.getDatabase(context)

        val item = upgraded.clothingDao().getClothingItemEntityById(itemId)
        assertNotNull("item from the previous build should survive", item)
        assertEquals("Upgrade Shirt", item!!.name)
        assertEquals(ClothingDatabase.VERSION, upgraded.databaseVersion())
        assertEquals(10, count(upgraded, "categories"))
        // Subcategories have no stable ids, so a second seeding would double them.
        assertEquals(subcategoriesBefore, count(upgraded, "subcategories"))
    }

    private suspend fun count(db: ClothingDatabase, table: String): Int =
        db.useReaderConnection { connection ->
            connection.usePrepared("SELECT COUNT(*) FROM `$table`") {
                it.step()
                it.getLong(0).toInt()
            }
        }

    private companion object {
        const val DATABASE_NAME = "closet.db"
    }
}
