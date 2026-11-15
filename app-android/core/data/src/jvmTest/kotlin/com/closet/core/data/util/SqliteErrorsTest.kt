package com.closet.core.data.util

import androidx.sqlite.SQLiteException
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [isConstraintViolation] must cover what `android.database.sqlite.SQLiteConstraintException`
 * covered: every SQLITE_CONSTRAINT error, including a trigger's `RAISE(ABORT, …)`.
 */
class SqliteErrorsTest {

    @Test
    fun bundledDriverUniqueViolation() {
        assertTrue(SQLiteException("Error code: 19, message: UNIQUE constraint failed: brands.normalized_name").isConstraintViolation())
    }

    @Test
    fun bundledDriverTriggerAbortWithPrimaryCode() {
        assertTrue(SQLiteException("Error code: 19, message: Subcategory does not belong to the selected Category").isConstraintViolation())
    }

    @Test
    fun extendedConstraintCodes() {
        // 2067 SQLITE_CONSTRAINT_UNIQUE, 787 _FOREIGNKEY, 1811 _TRIGGER
        for (code in listOf(2067, 787, 1811)) {
            assertTrue(SQLiteException("Error code: $code, message: x").isConstraintViolation(), "code $code")
        }
    }

    @Test
    fun frameworkStyleMessage() {
        assertTrue(SQLiteException("Subcategory does not belong (code 1811 SQLITE_CONSTRAINT_TRIGGER)").isConstraintViolation())
        assertTrue(SQLiteException("NOT NULL constraint failed: brands.name").isConstraintViolation())
    }

    @Test
    fun otherErrorsAreNotConstraintViolations() {
        assertFalse(SQLiteException("Error code: 1, message: no such table: foo").isConstraintViolation())
        assertFalse(SQLiteException("Error code: 5, message: database is locked").isConstraintViolation())
    }
}
