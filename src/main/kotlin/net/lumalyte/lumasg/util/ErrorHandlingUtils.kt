package net.lumalyte.lumasg.util

import kotlinx.coroutines.delay
import org.slf4j.LoggerFactory
import java.io.IOException
import java.sql.SQLException
import java.util.concurrent.ConcurrentHashMap

/** Thrown when a retried operation or open circuit breaker gives up. */
class OperationFailedException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Utility object for advanced error handling, retry mechanisms, and error classification.
 *
 * Provides:
 * - Retry mechanisms with exponential backoff (blocking and suspend)
 * - Error classification (recoverable vs fatal)
 * - Comprehensive error logging
 * - Circuit breaker pattern for repeated failures
 * - Serialization failure handling with fallbacks
 */
object ErrorHandlingUtils {

    private val logger = LoggerFactory.getLogger(ErrorHandlingUtils::class.java)

    /** Default maximum number of retry attempts. */
    const val DEFAULT_MAX_RETRIES: Int = 3

    /** Default initial delay for exponential backoff (in milliseconds). */
    const val DEFAULT_INITIAL_DELAY_MS: Long = 1000L

    /** Default backoff multiplier for exponential backoff. */
    const val DEFAULT_BACKOFF_MULTIPLIER: Double = 2.0

    // ── Retry (blocking) ────────────────────────────────────────────────

    /**
     * Executes an operation with retry logic and exponential backoff.
     *
     * @throws RuntimeException if all retry attempts are exhausted
     */
    fun <T> executeWithRetry(
        operation: () -> T,
        maxRetries: Int = DEFAULT_MAX_RETRIES,
        initialDelayMs: Long = DEFAULT_INITIAL_DELAY_MS,
        backoffMultiplier: Double = DEFAULT_BACKOFF_MULTIPLIER,
        operationName: String
    ): T {
        var lastException: Exception? = null
        var currentDelay = initialDelayMs

        for (attempt in 0..maxRetries) {
            try {
                if (attempt > 0) {
                    logger.info("Retrying {} (attempt {}/{})", operationName, attempt + 1, maxRetries + 1)
                    Thread.sleep(currentDelay)
                    currentDelay = (currentDelay * backoffMultiplier).toLong()
                }
                return operation()
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                throw OperationFailedException("Operation interrupted during retry: $operationName", e)
            } catch (e: Exception) {
                lastException = e

                if (attempt == maxRetries) {
                    logger.error("All retry attempts failed for {}", operationName, e)
                    break
                }

                if (!isRecoverableError(e)) {
                    logger.error("Non-recoverable error in {}, aborting retries", operationName, e)
                    break
                }

                logger.warn("Recoverable error in {} (attempt {}), will retry", operationName, attempt + 1, e)
            }
        }

        throw OperationFailedException(
            "Operation failed after ${maxRetries + 1} attempts: $operationName",
            lastException
        )
    }

    // ── Retry (suspend / coroutine-friendly) ────────────────────────────

    /**
     * Coroutine-friendly version of [executeWithRetry] using `delay()` instead of `Thread.sleep()`.
     *
     * @throws RuntimeException if all retry attempts are exhausted
     */
    suspend fun <T> executeWithRetrySuspend(
        operation: suspend () -> T,
        maxRetries: Int = DEFAULT_MAX_RETRIES,
        initialDelayMs: Long = DEFAULT_INITIAL_DELAY_MS,
        backoffMultiplier: Double = DEFAULT_BACKOFF_MULTIPLIER,
        operationName: String
    ): T {
        var lastException: Exception? = null
        var currentDelay = initialDelayMs

        for (attempt in 0..maxRetries) {
            try {
                if (attempt > 0) {
                    logger.info("Retrying {} (attempt {}/{})", operationName, attempt + 1, maxRetries + 1)
                    delay(currentDelay)
                    currentDelay = (currentDelay * backoffMultiplier).toLong()
                }
                return operation()
            } catch (e: Exception) {
                lastException = e

                if (attempt == maxRetries) {
                    logger.error("All retry attempts failed for {}", operationName, e)
                    break
                }

                if (!isRecoverableError(e)) {
                    logger.error("Non-recoverable error in {}, aborting retries", operationName, e)
                    break
                }

                logger.warn("Recoverable error in {} (attempt {}), will retry", operationName, attempt + 1, e)
            }
        }

        throw OperationFailedException(
            "Operation failed after ${maxRetries + 1} attempts: $operationName",
            lastException
        )
    }

    // ── Safe execution wrappers ─────────────────────────────────────────

    /**
     * Executes an operation safely, returning [defaultValue] on failure.
     */
    fun <T> safeExecute(
        operation: () -> T,
        defaultValue: T?,
        operationName: String
    ): T? = try {
        operation()
    } catch (e: Exception) {
        logger.warn("Error in operation: {} (Using default value)", operationName, e)
        defaultValue
    }

    /**
     * Executes a void operation safely, returning `true` on success and `false` on failure.
     */
    fun safeExecute(
        operation: () -> Unit,
        operationName: String
    ): Boolean = try {
        operation()
        true
    } catch (e: Exception) {
        logger.warn("Error in operation: {} (Operation failed silently)", operationName, e)
        false
    }

    // ── Serialization failure handling ──────────────────────────────────

    /**
     * Handles serialization failures gracefully by providing fallback mechanisms.
     *
     * Tries [primaryOperation] first, then [fallbackOperation], and finally [emptyFallback].
     */
    fun <T> handleSerializationFailure(
        primaryOperation: () -> T?,
        fallbackOperation: () -> T?,
        emptyFallback: () -> T?,
        operationName: String
    ): T? {
        // Try primary operation
        try {
            val result = primaryOperation()
            if (result != null) return result
        } catch (e: Exception) {
            logger.warn("Error in operation: {} (Primary serialization failed, trying fallback)", operationName, e)
        }

        // Try fallback operation
        try {
            val result = fallbackOperation()
            if (result != null) {
                logger.info("Fallback serialization succeeded for {}", operationName)
                return result
            }
        } catch (e: Exception) {
            logger.warn("Error in operation: {} (Fallback serialization failed, using empty fallback)", operationName, e)
        }

        // Use empty fallback as last resort
        return try {
            val result = emptyFallback()
            logger.info("Using empty fallback for {}", operationName)
            result
        } catch (e: Exception) {
            logger.error("Error in operation: {} (Even empty fallback failed)", operationName, e)
            null
        }
    }

    // ── Error classification ────────────────────────────────────────────

    /**
     * Determines whether an error is recoverable and should be retried.
     *
     * - [IOException] is always recoverable.
     * - [SQLException] is recoverable when connection- or timeout-related.
     * - [OutOfMemoryError], [StackOverflowError], [LinkageError], [AssertionError] are fatal.
     */
    fun isRecoverableError(throwable: Throwable): Boolean = when {
        throwable is IOException -> true

        throwable is SQLException -> {
            val sqlState = throwable.sqlState
            val msg = throwable.message?.lowercase() ?: return false
            // Connection errors (08xxx) are recoverable
            (sqlState != null && sqlState.startsWith("08")) ||
                msg.contains("timeout") ||
                msg.contains("lock")
        }

        throwable is RuntimeException -> {
            val msg = throwable.message?.lowercase() ?: return false
            msg.contains("timeout") ||
                msg.contains("too many connections") ||
                msg.contains("connection reset") ||
                msg.contains("temporarily unavailable")
        }

        // Fatal JVM errors
        throwable is OutOfMemoryError ||
            throwable is StackOverflowError ||
            throwable is LinkageError ||
            throwable is AssertionError -> false

        // Default: treat as recoverable unless proven fatal
        else -> true
    }

    // ── Error logging helpers ───────────────────────────────────────────

    /**
     * Logs an error with comprehensive context information.
     */
    fun logError(
        operation: String,
        context: String? = null,
        throwable: Throwable
    ) {
        val message = buildErrorMessage(operation, context)
        logger.error(message, throwable)
    }

    /**
     * Logs a warning with comprehensive context information.
     */
    fun logWarning(
        operation: String,
        context: String? = null,
        throwable: Throwable
    ) {
        val message = buildErrorMessage(operation, context)
        logger.warn(message, throwable)
    }

    private fun buildErrorMessage(operation: String, context: String?): String = buildString {
        append("Error in operation: ")
        append(operation)
        if (!context.isNullOrEmpty()) {
            append(" (").append(context).append(")")
        }
    }

    // ── Circuit Breaker ─────────────────────────────────────────────────

    /**
     * Circuit breaker that opens after [failureThreshold] consecutive failures and
     * automatically resets after [resetTimeoutMs] milliseconds.
     */
    class CircuitBreaker(
        private val failureThreshold: Int,
        private val resetTimeoutMs: Long
    ) {
        private val cbLogger = LoggerFactory.getLogger(CircuitBreaker::class.java)

        var failureCount: Int = 0
            private set

        var isOpen: Boolean = false
            private set

        private var lastFailureTime: Long = 0L

        /**
         * Executes an operation with circuit breaker protection.
         *
         * @throws RuntimeException if the circuit is open or the operation fails
         */
        fun <T> execute(operation: () -> T, operationName: String): T {
            // Check if circuit breaker should reset
            if (isOpen && (System.currentTimeMillis() - lastFailureTime) > resetTimeoutMs) {
                cbLogger.info("Circuit breaker reset for {}", operationName)
                isOpen = false
                failureCount = 0
            }

            // If circuit is open, fail fast
            if (isOpen) {
                throw OperationFailedException(
                    "Circuit breaker is open for $operationName " +
                        "(failures: $failureCount, threshold: $failureThreshold)"
                )
            }

            return try {
                val result = operation()
                // Success — reset failure count
                if (failureCount > 0) {
                    cbLogger.info("Circuit breaker success for {}, resetting failure count", operationName)
                    failureCount = 0
                }
                result
            } catch (e: Exception) {
                failureCount++
                lastFailureTime = System.currentTimeMillis()

                if (failureCount >= failureThreshold) {
                    isOpen = true
                    cbLogger.error(
                        "Circuit breaker opened for {} after {} failures",
                        operationName, failureCount, e
                    )
                }

                throw OperationFailedException("Operation failed in circuit breaker: $operationName", e)
            }
        }
    }
}
