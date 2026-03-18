package com.closet.core.data.util

/**
 * A sealed class hierarchy representing structured application errors.
 * Replaces raw strings/throwables with domain-specific error objects.
 */
sealed class AppError : Throwable() {
    
    sealed class DatabaseError : AppError() {
        data object NotFound : DatabaseError()
        data class ConstraintViolation(override val message: String) : DatabaseError()
        data class QueryError(override val cause: Throwable?) : DatabaseError()
    }

    sealed class ValidationError : AppError() {
        data class InvalidInput(override val message: String) : ValidationError()
        data class MissingField(val fieldName: String) : ValidationError()
    }

    data class Unexpected(override val cause: Throwable?) : AppError()
}
