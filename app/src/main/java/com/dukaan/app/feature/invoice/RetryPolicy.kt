package com.dukaan.app.feature.invoice

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * Robust retry policy with exponential backoff, jitter, and Retry-After support.
 */
class RetryPolicy(
    val maxRetries: Int = 2,
    val initialDelayMs: Long = 1000L,
    val maxDelayMs: Long = 6000L,
    val backoffMultiplier: Double = 2.0
) {
    companion object {
        private const val TAG = "RetryPolicy"
    }

    /**
     * Executes the given block, automatically retrying on recoverable errors.
     * @param onRetry Callback notifying UI that retry attempt is happening.
     */
    suspend fun <T> execute(
        onRetry: suspend (attemptNumber: Int, delayMs: Long, error: GeminiError) -> Unit = { _, _, _ -> },
        block: suspend (attemptNumber: Int) -> Result<T>
    ): Result<T> {
        var currentDelay = initialDelayMs

        for (attempt in 1..maxRetries) {
            try {
                val result = block(attempt)
                if (result.isSuccess) {
                    return result
                }

                val exception = result.exceptionOrNull()
                val geminiError = (exception as? GeminiException)?.error

                // Check if error is retryable
                if (geminiError != null && !geminiError.canRetry) {
                    Log.d(TAG, "Error is non-retryable (${geminiError::class.simpleName}) — halting retries")
                    return result
                }

                if (attempt == maxRetries) {
                    Log.w(TAG, "Max retries ($maxRetries) reached for this model")
                    return result
                }

                // Calculate delay with Retry-After or exponential backoff + jitter
                val computedDelay: Long = when {
                    geminiError is GeminiError.QuotaExceeded && geminiError.retryAfterSeconds != null -> {
                        minOf(geminiError.retryAfterSeconds * 1000L, 10_000L)
                    }
                    geminiError is GeminiError.RateLimited && geminiError.retryAfterSeconds != null -> {
                        minOf(geminiError.retryAfterSeconds * 1000L, 10_000L)
                    }
                    else -> {
                        val jitter = Random.nextLong(-250, 250)
                        (currentDelay + jitter).coerceIn(500L, maxDelayMs)
                    }
                }

                Log.d(TAG, "Attempt $attempt failed. Backing off for ${computedDelay}ms before attempt ${attempt + 1}")
                if (geminiError != null) {
                    onRetry(attempt + 1, computedDelay, geminiError)
                }

                delay(computedDelay)
                currentDelay = (currentDelay * backoffMultiplier).toLong().coerceAtMost(maxDelayMs)

            } catch (ce: CancellationException) {
                // NEVER suppress coroutine cancellation
                Log.d(TAG, "Scan coroutine was cancelled by user or lifecycle")
                throw ce
            } catch (t: Throwable) {
                if (attempt == maxRetries) {
                    return Result.failure(t)
                }
                Log.w(TAG, "Unexpected exception on attempt $attempt: ${t.message}")
                delay(currentDelay)
                currentDelay = (currentDelay * backoffMultiplier).toLong().coerceAtMost(maxDelayMs)
            }
        }

        return Result.failure(IllegalStateException("Retries exhausted"))
    }
}

class GeminiException(val error: GeminiError) : Exception(error.userMessage)

