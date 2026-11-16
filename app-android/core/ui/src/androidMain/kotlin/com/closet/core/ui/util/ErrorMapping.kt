package com.closet.core.ui.util

import androidx.annotation.StringRes
import com.closet.core.data.util.AppError
import com.closet.core.ui.R

data class UserMessage(
    @param:StringRes val resId: Int,
    val args: Array<out Any> = emptyArray()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as UserMessage
        if (resId != other.resId) return false
        if (!args.contentEquals(other.args)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = resId
        result = 31 * result + args.contentHashCode()
        return result
    }
}

fun AppError.asUserMessage(): UserMessage {
    return when (this) {
        is AppError.DatabaseError.NotFound -> 
            UserMessage(R.string.error_database_not_found)
        
        is AppError.DatabaseError.ConstraintViolation -> 
            UserMessage(R.string.error_database_constraint)
        
        is AppError.DatabaseError.QueryError -> 
            UserMessage(R.string.error_database_query)
        
        is AppError.ValidationError.InvalidInput -> 
            UserMessage(R.string.error_validation_invalid)
        
        is AppError.ValidationError.MissingField -> 
            UserMessage(R.string.error_validation_missing)
        
        is AppError.Unexpected -> 
            UserMessage(R.string.error_unexpected)
    }
}
