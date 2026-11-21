package com.closet.core.data

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.TypeConverters
import androidx.room.execSQL
import androidx.room.useReaderConnection
import androidx.room.useWriterConnection
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import com.closet.core.data.dao.*
import com.closet.core.data.migrations.MIGRATION_1_2
import com.closet.core.data.migrations.MIGRATION_2_3
import com.closet.core.data.model.*

/**
 * Main Room database for the Closet application.
 * Manages tables for clothing items, outfits, logs, and various lookup categories.
 * Parity: This is the native equivalent of the SQLite schema in the Expo project.
 */
@Database(
    entities = [
        ClothingItemEntity::class,
        BrandEntity::class,
        CategoryEntity::class,
        SubcategoryEntity::class,
        SeasonEntity::class,
        OccasionEntity::class,
        ColorEntity::class,
        ColorShadeEntity::class,
        MaterialEntity::class,
        PatternEntity::class,
        SizeSystemEntity::class,
        SizeValueEntity::class,
        OutfitEntity::class,
        OutfitItemEntity::class,
        OutfitLogEntity::class,
        OutfitLogItemEntity::class,
        ClothingItemColorEntity::class,
        ClothingItemMaterialEntity::class,
        ClothingItemSeasonEntity::class,
        ClothingItemOccasionEntity::class,
        ClothingItemPatternEntity::class,
        ItemEmbeddingEntity::class
    ],
    // Version 1 is the squashed baseline (the old 1→6 chain was reset; see migrations/AGENTS.md).
    // Version 2 (MIGRATION_1_2) adds measured item colours, basic terms, the neutral flag and
    // reference shades. Version 3 (MIGRATION_2_3) adds clothing_items.colors_curated.
    // Every schema change needs a real migration.
    version = 3,
    exportSchema = true
)
@TypeConverters(Converters::class)
@ConstructedBy(ClothingDatabaseConstructor::class)
abstract class ClothingDatabase : RoomDatabase() {
    /** @return [ClothingDao] for clothing item operations. */
    abstract fun clothingDao(): ClothingDao
    /** @return [BrandDao] for brand management. */
    abstract fun brandDao(): BrandDao
    /** @return [LookupDao] for lookup table queries. */
    abstract fun lookupDao(): LookupDao
    /** @return [OutfitDao] for outfit management. */
    abstract fun outfitDao(): OutfitDao
    /** @return [LogDao] for outfit logging and journal history. */
    abstract fun logDao(): LogDao
    /** @return [StatsDao] for wardrobe analytics. */
    abstract fun statsDao(): StatsDao
    /** @return [RecommendationDao] for the outfit recommendation pipeline. */
    abstract fun recommendationDao(): RecommendationDao
    /** @return [EmbeddingDao] for RAG vector storage (Phase 2A). */
    abstract fun embeddingDao(): EmbeddingDao

    /**
     * Executes `PRAGMA wal_checkpoint(FULL)` to flush the WAL file into the main database
     * file before a backup copy. Exposed here so callers in the `app` module don't need to
     * reach for Room's connection APIs.
     */
    suspend fun checkpointWal() {
        useWriterConnection { it.execSQL("PRAGMA wal_checkpoint(FULL)") }
    }

    /**
     * Returns the current SQLite schema version (`PRAGMA user_version`). Exposed here so callers
     * in the `app` module don't need to reach for Room's connection APIs.
     */
    suspend fun databaseVersion(): Int =
        useReaderConnection { connection ->
            connection.usePrepared("PRAGMA user_version") { statement ->
                statement.step()
                statement.getLong(0).toInt()
            }
        }

    /** Anchor for the platform entry points, e.g. `ClothingDatabase.getDatabase(context)` on Android. */
    companion object
}

/** Room's generated constructor for [ClothingDatabase]; KSP writes the `actual` for each target. */
@Suppress("KotlinNoActualForExpect")
expect object ClothingDatabaseConstructor : RoomDatabaseConstructor<ClothingDatabase> {
    override fun initialize(): ClothingDatabase
}

/**
 * Applies the settings every platform's builder shares: the bundled SQLite driver, [Dispatchers.IO]
 * for queries, the migrations, and the callbacks that seed lookup data, install the category triggers and create
 * the `one_ootd_per_day` partial index.
 */
fun RoomDatabase.Builder<ClothingDatabase>.configureClosetDatabase(): RoomDatabase.Builder<ClothingDatabase> =
    setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
        .addCallback(object : RoomDatabase.Callback() {
            override fun onCreate(connection: SQLiteConnection) {
                connection.execSQL("PRAGMA foreign_keys = ON")
                DatabaseSeeder.seedAll(connection)
                createCategoryConsistencyTriggers(connection)
            }

            override fun onOpen(connection: SQLiteConnection) {
                connection.execSQL("PRAGMA foreign_keys = ON")
                // Partial index — Room cannot represent this in entity annotations, so it is
                // created here (runs on every open) rather than in onCreate. Room's
                // post-migration validator would reject it as an unexpected index.
                connection.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS one_ootd_per_day ON outfit_logs(date) WHERE is_ootd = 1")
            }
        })

/**
 * Creates SQLite triggers to enforce consistency between categories and subcategories.
 * 1. Ensures both category_id and subcategory_id are either both NULL or both NOT NULL.
 * 2. Verifies that a selected subcategory actually belongs to the selected parent category.
 */
private fun createCategoryConsistencyTriggers(db: SQLiteConnection) {
    // Enforce category_id and subcategory_id consistency (Both NULL or both NOT NULL)
    val consistencyCheck = """
                SELECT CASE
                    WHEN (NEW.subcategory_id IS NOT NULL AND NEW.category_id IS NULL)
                    OR (NEW.subcategory_id IS NULL AND NEW.category_id IS NOT NULL)
                    THEN RAISE(ABORT, 'Category and Subcategory must both be NULL or both be NOT NULL')
                END;
            """

    // Enforce that subcategory belongs to the selected category
    val relationshipCheck = """
                SELECT CASE
                    WHEN (SELECT category_id FROM subcategories WHERE id = NEW.subcategory_id) != NEW.category_id
                    THEN RAISE(ABORT, 'Subcategory does not belong to the selected Category')
                END;
            """

    // INSERT Triggers
    db.execSQL("""
                CREATE TRIGGER IF NOT EXISTS check_clothing_item_category_consistency_insert
                BEFORE INSERT ON clothing_items
                FOR EACH ROW
                BEGIN
                    $consistencyCheck
                END;
            """)

    db.execSQL("""
                CREATE TRIGGER IF NOT EXISTS verify_subcategory_parent_category_insert
                BEFORE INSERT ON clothing_items
                FOR EACH ROW
                WHEN NEW.subcategory_id IS NOT NULL
                BEGIN
                    $relationshipCheck
                END;
            """)

    // UPDATE Triggers
    db.execSQL("""
                CREATE TRIGGER IF NOT EXISTS check_clothing_item_category_consistency_update
                BEFORE UPDATE OF category_id, subcategory_id ON clothing_items
                FOR EACH ROW
                BEGIN
                    $consistencyCheck
                END;
            """)

    db.execSQL("""
                CREATE TRIGGER IF NOT EXISTS verify_subcategory_parent_category_update
                BEFORE UPDATE OF category_id, subcategory_id ON clothing_items
                FOR EACH ROW
                WHEN NEW.subcategory_id IS NOT NULL
                BEGIN
                    $relationshipCheck
                END;
            """)
}
