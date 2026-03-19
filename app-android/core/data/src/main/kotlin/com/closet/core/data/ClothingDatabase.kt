package com.closet.core.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.closet.core.data.dao.*
import com.closet.core.data.model.*

/**
 * Main Room database for the Closet application.
 * Manages tables for clothing items, outfits, logs, and various lookup categories.
 * Parity: This is the native equivalent of the SQLite schema in the Expo project.
 */
@Database(
    entities = [
        ClothingItemEntity::class,
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
        ClothingItemColorEntity::class,
        ClothingItemMaterialEntity::class,
        ClothingItemSeasonEntity::class,
        ClothingItemOccasionEntity::class,
        ClothingItemPatternEntity::class
    ],
    version = 2,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class ClothingDatabase : RoomDatabase() {
    /** @return [ClothingDao] for clothing item operations. */
    abstract fun clothingDao(): ClothingDao
    /** @return [LookupDao] for lookup table queries. */
    abstract fun lookupDao(): LookupDao
    /** @return [OutfitDao] for outfit management. */
    abstract fun outfitDao(): OutfitDao
    /** @return [LogDao] for outfit logging and journal history. */
    abstract fun logDao(): LogDao
    /** @return [StatsDao] for wardrobe analytics. */
    abstract fun statsDao(): StatsDao

    companion object {
        private const val DATABASE_NAME = "closet.db"

        @Volatile
        private var INSTANCE: ClothingDatabase? = null

        /**
         * Migration from version 1 to 2: Add layout columns to outfit_items and populate colors.
         * Note: Columns are checked before adding to avoid errors if they already exist in some v1 versions.
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val cursor = db.query("PRAGMA table_info(outfit_items)")
                val existingColumns = mutableSetOf<String>()
                while (cursor.moveToNext()) {
                    val nameIndex = cursor.getColumnIndex("name")
                    if (nameIndex != -1) {
                        existingColumns.add(cursor.getString(nameIndex))
                    }
                }
                cursor.close()

                if (!existingColumns.contains("pos_x")) {
                    db.execSQL("ALTER TABLE outfit_items ADD COLUMN pos_x REAL")
                }
                if (!existingColumns.contains("pos_y")) {
                    db.execSQL("ALTER TABLE outfit_items ADD COLUMN pos_y REAL")
                }
                if (!existingColumns.contains("scale")) {
                    db.execSQL("ALTER TABLE outfit_items ADD COLUMN scale REAL")
                }
                if (!existingColumns.contains("z_index")) {
                    db.execSQL("ALTER TABLE outfit_items ADD COLUMN z_index INTEGER")
                }

                DatabaseSeeder.seedColors(db)
            }
        }

        /**
         * Returns a singleton instance of the database.
         * Initializes the database with seeding, unique indices, and triggers on first creation.
         */
        fun getDatabase(context: Context): ClothingDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ClothingDatabase::class.java,
                    DATABASE_NAME
                )
                .addMigrations(MIGRATION_1_2)
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        // Seed data on first creation
                        DatabaseSeeder.seedAll(db)
                        
                        // Enforce one-OOTD-per-day via partial unique index
                        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS one_ootd_per_day ON outfit_logs(date) WHERE is_ootd = 1")
                        
                        createCategoryConsistencyTriggers(db)
                    }

                    override fun onOpen(db: SupportSQLiteDatabase) {
                        super.onOpen(db)
                        // Parity: PRAGMA foreign_keys = ON at DB open time, always
                        db.execSQL("PRAGMA foreign_keys = ON")
                    }
                })
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
