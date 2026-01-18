package com.pcexplorer.core.common

/**
 * Extension functions for Kotlin Result type.
 */

inline fun <T, R> Result<T>.flatMap(transform: (T) -> Result<R>): Result<R> {
    return fold(
        onSuccess = { transform(it) },
        onFailure = { Result.failure(it) }
    )
}

inline fun <T> Result<T>.onError(action: (Throwable) -> Unit): Result<T> {
    exceptionOrNull()?.let(action)
    return this
}

fun <T> Result<T>.getOrDefault(default: T): T = getOrNull() ?: default

suspend fun <T> runCatchingAsync(block: suspend () -> T): Result<T> {
    return try {
        Result.success(block())
    } catch (e: Exception) {
        Result.failure(e)
    }
}
