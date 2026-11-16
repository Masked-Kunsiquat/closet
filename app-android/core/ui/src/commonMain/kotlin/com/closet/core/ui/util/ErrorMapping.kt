package com.closet.core.ui.util

import com.closet.core.data.util.AppError
import com.closet.core.ui.resources.*
import org.jetbrains.compose.resources.StringResource

/** A localized message: a Compose string [res] plus the positional [args] for its placeholders. */
data class UserMessage(
    val res: StringResource,
    val args: List<Any> = emptyList()
)

fun AppError.asUserMessage(): UserMessage {
    return when (this) {
        is AppError.DatabaseError.NotFound ->
            UserMessage(Res.string.error_database_not_found)

        is AppError.DatabaseError.ConstraintViolation ->
            UserMessage(Res.string.error_database_constraint, listOf(message))

        is AppError.DatabaseError.QueryError ->
            UserMessage(Res.string.error_database_query)

        is AppError.ValidationError.InvalidInput ->
            UserMessage(Res.string.error_validation_invalid, listOf(message))

        is AppError.ValidationError.MissingField ->
            UserMessage(Res.string.error_validation_missing, listOf(fieldName))

        is AppError.Unexpected ->
            UserMessage(Res.string.error_unexpected)
    }
}
