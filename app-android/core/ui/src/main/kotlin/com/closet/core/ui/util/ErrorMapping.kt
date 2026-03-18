package com.closet.core.ui.util

import android.content.Context
import androidx.annotation.StringRes
import com.closet.core.data.util.AppError
import com.closet.core.ui.R

/**
 * Extension function to map [AppError] to a user-friendly localized message.
 */
fun AppError.toUserMessage(context: Context): String {
    return when (this) {
        is AppError.DatabaseError.NotFound -> 
            context.getString(R.string.error_database_not_found)
        
        is AppError.DatabaseError.ConstraintViolation -> 
            context.getString(R.string.error_database_constraint, this.message ?: "")
        
        is AppError.DatabaseError.QueryError -> 
            context.getString(R.string.error_database_query)
        
        is AppError.ValidationError.InvalidInput -> 
            context.getString(R.string.error_validation_invalid, this.message ?: "")
        
        is AppError.ValidationError.MissingField -> 
            context.getString(R.string.error_validation_missing, this.fieldName)
        
        is AppError.Unexpected -> 
            context.getString(R.string.error_unexpected)
    }
}

/**
 * Alternative that returns the resource ID and arguments for more flexible UI handling.
 */
data class UserMessage(
    @StringRes val resId: Int,
    val args: Array<out Any> = emptyArray()
)

fun AppError.asUserMessage(): UserMessage {
    return when (this) {
        is AppError.DatabaseError.NotFound -> 
            UserMessage(R.string.error_database_not_found)
        
        is AppError.DatabaseError.ConstraintViolation -> 
            UserMessage(R.string.error_database_constraint, arrayOf(this.message ?: ""))
        
        is AppError.DatabaseError.QueryError -> 
            UserMessage(R.string.error_database_query)
        
        is AppError.ValidationError.InvalidInput -> 
            UserMessage(R.string.error_validation_invalid, arrayOf(this.message ?: ""))
        
        is AppError.ValidationError.MissingField -> 
            UserMessage(R.string.error_validation_missing, arrayOf(this.fieldName))
        
        is AppError.Unexpected -> 
            UserMessage(R.string.error_unexpected)
    }
}
