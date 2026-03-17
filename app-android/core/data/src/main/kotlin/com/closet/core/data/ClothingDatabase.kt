package com.closet.core.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
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
abstract class ClothingDatabase : RoomDatabase() {
    abstract fun clothingDao(): ClothingDao
    abstract fun lookupDao(): LookupDao
    abstract fun outfitDao(): OutfitDao
    abstract fun logDao(): LogDao

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
                        
                        // Parity: Only one outfit per day can be OOTD
                        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS one_ootd_per_day ON outfit_logs(date) WHERE is_ootd = 1")
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
    }
}
