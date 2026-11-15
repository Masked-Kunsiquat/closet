package com.closet.core.data

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Pins where the production database lives: `closet.db` under [Context.getDatabasePath].
 * Backup and restore copy that exact file, so the Room builder must keep writing there.
 */
@RunWith(AndroidJUnit4::class)
class DatabasePathTest {

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
    fun getDatabase_writesToClosetDbUnderGetDatabasePath() {
        val db = ClothingDatabase.getDatabase(context)
        runBlocking { db.brandDao().insertBrand("Path Test", "path test") }

        assertTrue(
            "closet.db should exist at getDatabasePath",
            context.getDatabasePath(DATABASE_NAME).exists(),
        )
        assertTrue(
            "databaseList() should contain closet.db",
            context.databaseList().contains(DATABASE_NAME),
        )
    }

    private companion object {
        const val DATABASE_NAME = "closet.db"
    }
}
