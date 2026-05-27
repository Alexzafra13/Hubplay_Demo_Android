package com.alex.hubplay.data

/**
 * Unified result type for the data layer. Replaces the inconsistent mix
 * of `runCatching`, raw throws, and per-repo sealed classes.
 *
 * New code should return `ApiResult<T>` from repositories; existing code
 * migrates incrementally. ViewModels `when`-match on the sealed branches
 * to drive UI state cleanly.
 */
sealed interface ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>
    data class Error(
        val message: String,
        val cause: Throwable? = null,
    ) : ApiResult<Nothing>
}

inline fun <T> apiRunCatching(block: () -> T): ApiResult<T> =
    try {
        ApiResult.Success(block())
    } catch (e: Throwable) {
        ApiResult.Error(
            message = e.message ?: "Error desconocido",
            cause = e,
        )
    }
