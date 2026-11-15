package com.closet.core.data.util

import androidx.sqlite.SQLiteException

/** SQLite's primary result code for constraint failures (`SQLITE_CONSTRAINT`). */
private const val SQLITE_CONSTRAINT = 19

/** Matches `Error code: 2067` (bundled driver) and `(code 2067 …)` (Android framework). */
private val ERROR_CODE = Regex("""\bcode:? (\d+)""")

/**
 * True when this is a constraint failure: UNIQUE, NOT NULL, FOREIGN KEY, CHECK, or a trigger's
 * `RAISE(ABORT, …)`. This is the set `android.database.sqlite.SQLiteConstraintException` covered
 * before the bundled driver, which throws a plain [SQLiteException] whose message carries the
 * result code. The primary code is the low byte of an extended code (e.g. 2067 → 19).
 */
fun SQLiteException.isConstraintViolation(): Boolean {
    val text = message ?: return false
    val code = ERROR_CODE.find(text)?.groupValues?.get(1)?.toIntOrNull()
    if (code != null && code and 0xFF == SQLITE_CONSTRAINT) return true
    return "constraint failed" in text
}
