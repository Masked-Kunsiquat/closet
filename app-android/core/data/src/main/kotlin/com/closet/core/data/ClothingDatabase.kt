package com.closet.core.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.closet.core.data.dao.*
import com.closet.core.data.migrations.MIGRATION_1_2
import com.closet.core.data.migrations.MIGRATION_2_3
import com.closet.core.data.migrations.MIGRATION_3_4
import com.closet.core.data.migrations.MIGRATION_4_5
import com.closet.core.data.migrations.MIGRATION_5_6
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
    version = 6,
    exportSchema = true
)
@TypeConverters(Converters::class)
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

    companion object {
        private const val DATABASE_NAME = "closet.db"

        @Volatile
        private var INSTANCE: ClothingDatabase? = null

        /**
         * Returns a singleton instance of the database.
         * Initializes the database with seeding, unique indices, and triggers on first creation.
         */
        /**
         * Closes the current database connection and clears the singleton so the next call to
         * [getDatabase] builds a fresh instance. Called by `RestoreRepository` before overwriting
         * the database file; the process is restarted afterward so stale DAO references are never
         * used in production code.
         */
        fun closeAndReset() {
            synchronized(this) {
                INSTANCE?.close()
                INSTANCE = null
            }
        }

        fun getDatabase(context: Context): ClothingDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ClothingDatabase::class.java,
                    DATABASE_NAME
                )
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        db.execSQL("PRAGMA foreign_keys = ON")
                        DatabaseSeeder.seedAll(db)
                        createCategoryConsistencyTriggers(db)
                    }

                    override fun onOpen(db: SupportSQLiteDatabase) {
                        super.onOpen(db)
                        db.execSQL("PRAGMA foreign_keys = ON")
                        // Partial index — Room cannot represent this in entity annotations so it is
                        // created here (runs for every open) rather than in onCreate. This avoids
                        // Room's post-migration schema validator rejecting it as an unexpected index.
                        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS one_ootd_per_day ON outfit_logs(date) WHERE is_ootd = 1")
                    }
                })
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                .build()
                INSTANCE = instance
                instance
            }
        }

        /**
         * Creates SQLite triggers to enforce consistency between categories and subcategories.
         * 1. Ensures both category_id and subcategory_id are either both NULL or both NOT NULL.
         * 2. Verifies that a selected subcategory actually belongs to the selected parent category.
         */
        private fun createCategoryConsistencyTriggers(db: SupportSQLiteDatabase) {
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
    }
}
