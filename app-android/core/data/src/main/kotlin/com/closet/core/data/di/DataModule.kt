package com.closet.core.data.di

import android.content.Context
import com.closet.core.data.ClothingDatabase
import com.closet.core.data.dao.ClothingDao
import com.closet.core.data.dao.LogDao
import com.closet.core.data.dao.LookupDao
import com.closet.core.data.dao.OutfitDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): ClothingDatabase {
        return ClothingDatabase.getDatabase(context)
    }

    @Provides
    fun provideClothingDao(db: ClothingDatabase): ClothingDao = db.clothingDao()

    @Provides
    fun provideLookupDao(db: ClothingDatabase): LookupDao = db.lookupDao()

    @Provides
    fun provideOutfitDao(db: ClothingDatabase): OutfitDao = db.outfitDao()

    @Provides
    fun provideLogDao(db: ClothingDatabase): LogDao = db.logDao()
}
