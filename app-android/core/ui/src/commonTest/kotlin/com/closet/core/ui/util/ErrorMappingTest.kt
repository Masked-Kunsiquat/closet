package com.closet.core.ui.util

import com.closet.core.data.util.AppError
import com.closet.core.ui.resources.Res
import com.closet.core.ui.resources.error_database_constraint
import com.closet.core.ui.resources.error_database_not_found
import com.closet.core.ui.resources.error_unexpected
import kotlin.test.Test
import kotlin.test.assertEquals

class ErrorMappingTest {

    @Test
    fun notFoundHasNoArgs() {
        assertEquals(
            UserMessage(Res.string.error_database_not_found),
            AppError.DatabaseError.NotFound().asUserMessage()
        )
    }

    @Test
    fun constraintViolationPassesMessageAsArg() {
        assertEquals(
            UserMessage(Res.string.error_database_constraint, listOf("UNIQUE brand.name")),
            AppError.DatabaseError.ConstraintViolation("UNIQUE brand.name").asUserMessage()
        )
    }

    @Test
    fun missingFieldPassesFieldNameAsArg() {
        assertEquals(
            listOf("name"),
            AppError.ValidationError.MissingField("name").asUserMessage().args
        )
    }

    @Test
    fun unexpectedHasNoArgs() {
        assertEquals(
            UserMessage(Res.string.error_unexpected),
            AppError.Unexpected(null).asUserMessage()
        )
    }
}
