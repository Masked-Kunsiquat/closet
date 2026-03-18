package com.closet.core.data.util

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

/**
 * A sealed interface representing the result of a data operation.
 * Forces the consumer to handle both Loading, Success and Error states.
 */
sealed interface DataResult<out T> {
    data object Loading : DataResult<Nothing>
    data class Success<T>(val data: T) : DataResult<T>
    data class Error(val throwable: Throwable) : DataResult<Nothing>
}

/**
 * Converts a [Flow] of [T] to a [Flow] of [DataResult] of [T].
 */
fun <T> Flow<T>.asDataResult(): Flow<DataResult<T>> {
    return this
        .map<T, DataResult<T>> { DataResult.Success(it) }
        .onStart { emit(DataResult.Loading) }
        .catch { emit(DataResult.Error(it)) }
}

/**
 * Convenience function to handle [DataResult] states.
 */
inline fun <T, R> DataResult<T>.fold(
    onLoading: () -> R,
    onSuccess: (T) -> R,
    onError: (Throwable) -> R
): R = when (this) {
    is DataResult.Loading -> onLoading()
    is DataResult.Success -> onSuccess(data)
    is DataResult.Error -> onError(throwable)
}
