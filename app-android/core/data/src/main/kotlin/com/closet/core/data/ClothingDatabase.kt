package com.closet.core.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.closet.core.data.dao.*
import com.closet.core.data.model.*

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
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class ClothingDatabase : RoomDatabase() {
    abstract fun clothingDao(): ClothingDao
    abstract fun lookupDao(): LookupDao
    abstract fun outfitDao(): OutfitDao
    abstract fun logDao(): LogDao
    abstract fun statsDao(): StatsDao

    companion object {
        private const val DATABASE_NAME = "closet.db"

        @Volatile
        private var INSTANCE: ClothingDatabase? = null

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
