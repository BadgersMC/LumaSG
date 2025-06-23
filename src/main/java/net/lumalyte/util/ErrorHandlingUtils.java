package net.lumalyte.util;

import net.lumalyte.exception.LumaSGException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Utility class for advanced error handling, retry mechanisms, and error classification.
 * 
 * <p>This class provides comprehensive error handling capabilities including:
 * <ul>
 *   <li>Retry mechanisms with exponential backoff</li>
 *   <li>Error classification (recoverable vs fatal)</li>
 *   <li>Comprehensive error logging</li>
 *   <li>Circuit breaker pattern for repeated failures</li>
 * </ul>
 * 
 * @author LumaSG Team
 * @since 1.0.0
 */
public final class ErrorHandlingUtils {
    
    /**
     * Default maximum number of retry attempts.
     */
    public static final int DEFAULT_MAX_RETRIES = 3;
    
    /**
     * Default initial delay for exponential backoff (in milliseconds).
     */
    public static final long DEFAULT_INITIAL_DELAY_MS = 1000L;
    
    /**
     * Default backoff multiplier for exponential backoff.
     */
    public static final double DEFAULT_BACKOFF_MULTIPLIER = 2.0;
    
    private ErrorHandlingUtils() {
        // Utility class - prevent instantiation
    }
    
    /**
     * Executes an operation with retry logic and exponential backoff.
     * 
     * @param operation The operation to execute
     * @param maxRetries Maximum number of retry attempts
     * @param initialDelayMs Initial delay in milliseconds
     * @param backoffMultiplier Multiplier for exponential backoff
     * @param logger Logger for error reporting
     * @param operationName Name of the operation for logging
     * @param <T> Return type of the operation
     * @return The result of the operation
     * @throws LumaSGException if all retry attempts fail
     */
    public static <T> T executeWithRetry(
            @NotNull Supplier<T> operation,
            int maxRetries,
            long initialDelayMs,
            double backoffMultiplier,
            @NotNull Logger logger,
            @NotNull String operationName) throws LumaSGException {
        
        Exception lastException = null;
        long currentDelay = initialDelayMs;
        
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                if (attempt > 0) {
                    logger.info("Retrying " + operationName + " (attempt " + (attempt + 1) + "/" + (maxRetries + 1) + ")");
                    
                    // Wait before retry
                    try {
                        Thread.sleep(currentDelay);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw LumaSGException.configError("Operation interrupted during retry: " + operationName, e);
                    }
                    
                    // Exponential backoff
                    currentDelay = (long) (currentDelay * backoffMultiplier);
                }
                
                return operation.get();
                
            } catch (Exception e) {
                lastException = e;
                
                if (attempt == maxRetries) {
                    logger.log(Level.SEVERE, "All retry attempts failed for " + operationName, e);
                    break;
                }
                
                if (!isRecoverableError(e)) {
                    logger.log(Level.SEVERE, "Non-recoverable error in " + operationName + ", aborting retries", e);
                    break;
                }
                
                logger.log(Level.WARNING, "Recoverable error in " + operationName + " (attempt " + (attempt + 1) + "), will retry", e);
            }
        }
        
        // All retries failed
        throw LumaSGException.configError("Operation failed after " + (maxRetries + 1) + " attempts: " + operationName, lastException);
    }
    
    /**
     * Executes an operation with default retry settings.
     * 
     * @param operation The operation to execute
     * @param logger Logger for error reporting
     * @param operationName Name of the operation for logging
     * @param <T> Return type of the operation
     * @return The result of the operation
     * @throws LumaSGException if all retry attempts fail
     */
    public static <T> T executeWithRetry(
            @NotNull Supplier<T> operation,
            @NotNull Logger logger,
            @NotNull String operationName) throws LumaSGException {
        return executeWithRetry(operation, DEFAULT_MAX_RETRIES, DEFAULT_INITIAL_DELAY_MS, DEFAULT_BACKOFF_MULTIPLIER, logger, operationName);
    }
    
    /**
     * Executes an async operation with retry logic.
     * 
     * @param operation The async operation to execute
     * @param maxRetries Maximum number of retry attempts
     * @param initialDelayMs Initial delay in milliseconds
     * @param backoffMultiplier Multiplier for exponential backoff
     * @param logger Logger for error reporting
     * @param operationName Name of the operation for logging
     * @param <T> Return type of the operation
     * @return CompletableFuture with the result of the operation
     */
    public static <T> CompletableFuture<T> executeAsyncWithRetry(
            @NotNull Supplier<CompletableFuture<T>> operation,
            int maxRetries,
            long initialDelayMs,
            double backoffMultiplier,
            @NotNull Logger logger,
            @NotNull String operationName) {
        
        return executeAsyncWithRetryInternal(operation, 0, maxRetries, initialDelayMs, backoffMultiplier, logger, operationName);
    }
    
    private static <T> CompletableFuture<T> executeAsyncWithRetryInternal(
            @NotNull Supplier<CompletableFuture<T>> operation,
            int currentAttempt,
            int maxRetries,
            long currentDelay,
            double backoffMultiplier,
            @NotNull Logger logger,
            @NotNull String operationName) {
        
        if (currentAttempt > 0) {
            logger.info("Retrying " + operationName + " (attempt " + (currentAttempt + 1) + "/" + (maxRetries + 1) + ")");
        }
        
        return operation.get()
            .exceptionally(throwable -> {
                if (currentAttempt >= maxRetries) {
                    logger.log(Level.SEVERE, "All async retry attempts failed for " + operationName, throwable);
                    throw new RuntimeException("Operation failed after " + (maxRetries + 1) + " attempts: " + operationName, throwable);
                }
                
                if (!isRecoverableError(throwable)) {
                    logger.log(Level.SEVERE, "Non-recoverable error in async " + operationName + ", aborting retries", throwable);
                    throw new RuntimeException("Non-recoverable error: " + operationName, throwable);
                }
                
                logger.log(Level.WARNING, "Recoverable error in async " + operationName + " (attempt " + (currentAttempt + 1) + "), will retry", throwable);
                return null; // Will trigger retry
            })
            .thenCompose(result -> {
                if (result == null) {
                    // Retry needed
                    return CompletableFuture
                        .supplyAsync(() -> null, CompletableFuture.delayedExecutor(currentDelay, TimeUnit.MILLISECONDS))
                        .thenCompose(v -> executeAsyncWithRetryInternal(
                            operation, 
                            currentAttempt + 1, 
                            maxRetries, 
                            (long) (currentDelay * backoffMultiplier), 
                            backoffMultiplier, 
                            logger, 
                            operationName
                        ));
                } else {
                    return CompletableFuture.completedFuture(result);
                }
            });
    }
    
    /**
     * Determines if an error is recoverable and should be retried.
     * 
     * @param throwable The error to classify
     * @return true if the error is recoverable, false otherwise
     */
    public static boolean isRecoverableError(@NotNull Throwable throwable) {
        // Network-related errors are usually recoverable
        if (throwable instanceof IOException) {
            return true;
        }
        
        // Some SQL errors are recoverable (connection issues, timeouts)
        if (throwable instanceof SQLException) {
            SQLException sqlException = (SQLException) throwable;
            String sqlState = sqlException.getSQLState();
            
            // Connection errors (08xxx) are recoverable
            if (sqlState != null && sqlState.startsWith("08")) {
                return true;
            }
            
            // Timeout errors are recoverable
            if (sqlException.getMessage() != null && 
                sqlException.getMessage().toLowerCase().contains("timeout")) {
                return true;
            }
            
            // Lock timeout errors are recoverable
            if (sqlException.getMessage() != null && 
                sqlException.getMessage().toLowerCase().contains("lock")) {
                return true;
            }
        }
        
        // Runtime exceptions with specific messages that indicate temporary issues
        if (throwable instanceof RuntimeException) {
            String message = throwable.getMessage();
            if (message != null) {
                String lowerMessage = message.toLowerCase();
                if (lowerMessage.contains("timeout") || 
                    lowerMessage.contains("connection") ||
                    lowerMessage.contains("temporary") ||
                    lowerMessage.contains("busy")) {
                    return true;
                }
            }
        }
        
        // LumaSG exceptions with specific types
        if (throwable instanceof LumaSGException.DatabaseException) {
            return true; // Most database errors are recoverable
        }
        
        // Configuration errors are usually not recoverable
        if (throwable instanceof LumaSGException.ConfigurationException) {
            return false;
        }
        
        // Validation errors are not recoverable
        if (throwable instanceof LumaSGException.ValidationException) {
            return false;
        }
        
        // IllegalArgumentException is usually not recoverable
        if (throwable instanceof IllegalArgumentException) {
            return false;
        }
        
        // NullPointerException is usually not recoverable
        if (throwable instanceof NullPointerException) {
            return false;
        }
        
        // InterruptedException should not be retried
        if (throwable instanceof InterruptedException) {
            return false;
        }
        
        // Default to recoverable for unknown exceptions
        return true;
    }
    
    /**
     * Logs an error with comprehensive context information.
     * 
     * @param logger The logger to use
     * @param level The log level
     * @param operation The operation that failed
     * @param context Additional context information
     * @param throwable The error that occurred
     */
    public static void logError(@NotNull Logger logger, 
                               @NotNull Level level, 
                               @NotNull String operation, 
                               @Nullable String context, 
                               @NotNull Throwable throwable) {
        
        StringBuilder message = new StringBuilder();
        message.append("Error in ").append(operation);
        
        if (context != null && !context.trim().isEmpty()) {
            message.append(" (").append(context).append(")");
        }
        
        message.append(": ").append(throwable.getMessage());
        
        // Add error classification
        if (isRecoverableError(throwable)) {
            message.append(" [RECOVERABLE]");
        } else {
            message.append(" [FATAL]");
        }
        
        logger.log(level, message.toString(), throwable);
    }
    
    /**
     * Logs an error with comprehensive context information using SEVERE level.
     * 
     * @param logger The logger to use
     * @param operation The operation that failed
     * @param context Additional context information
     * @param throwable The error that occurred
     */
    public static void logError(@NotNull Logger logger, 
                               @NotNull String operation, 
                               @Nullable String context, 
                               @NotNull Throwable throwable) {
        logError(logger, Level.SEVERE, operation, context, throwable);
    }
    
    /**
     * Logs an error with comprehensive context information using SEVERE level.
     * 
     * @param logger The logger to use
     * @param operation The operation that failed
     * @param throwable The error that occurred
     */
    public static void logError(@NotNull Logger logger, 
                               @NotNull String operation, 
                               @NotNull Throwable throwable) {
        logError(logger, Level.SEVERE, operation, null, throwable);
    }
    
    /**
     * Creates a safe wrapper for operations that might throw exceptions.
     * 
     * @param operation The operation to wrap
     * @param defaultValue The default value to return if the operation fails
     * @param logger Logger for error reporting
     * @param operationName Name of the operation for logging
     * @param <T> Return type of the operation
     * @return The result of the operation or the default value if it fails
     */
    public static <T> T safeExecute(@NotNull Supplier<T> operation, 
                                   @Nullable T defaultValue, 
                                   @NotNull Logger logger, 
                                   @NotNull String operationName) {
        try {
            return operation.get();
        } catch (Exception e) {
            logError(logger, Level.WARNING, operationName, "Using default value", e);
            return defaultValue;
        }
    }
    
    /**
     * Creates a safe wrapper for void operations that might throw exceptions.
     * 
     * @param operation The operation to wrap
     * @param logger Logger for error reporting
     * @param operationName Name of the operation for logging
     * @return true if the operation succeeded, false otherwise
     */
    public static boolean safeExecute(@NotNull Runnable operation, 
                                     @NotNull Logger logger, 
                                     @NotNull String operationName) {
        try {
            operation.run();
            return true;
        } catch (Exception e) {
            logError(logger, Level.WARNING, operationName, "Operation failed silently", e);
            return false;
        }
    }
    
    /**
     * Circuit breaker state for tracking repeated failures.
     */
    public static class CircuitBreaker {
        private final int failureThreshold;
        private final long resetTimeoutMs;
        private int failureCount = 0;
        private long lastFailureTime = 0;
        private boolean isOpen = false;
        
        public CircuitBreaker(int failureThreshold, long resetTimeoutMs) {
            this.failureThreshold = failureThreshold;
            this.resetTimeoutMs = resetTimeoutMs;
        }
        
        /**
         * Executes an operation with circuit breaker protection.
         * 
         * @param operation The operation to execute
         * @param logger Logger for error reporting
         * @param operationName Name of the operation for logging
         * @param <T> Return type of the operation
         * @return The result of the operation
         * @throws LumaSGException if the circuit breaker is open or the operation fails
         */
        public <T> T execute(@NotNull Supplier<T> operation, 
                           @NotNull Logger logger, 
                           @NotNull String operationName) throws LumaSGException {
            
            // Check if circuit breaker should reset
            if (isOpen && (System.currentTimeMillis() - lastFailureTime) > resetTimeoutMs) {
                logger.info("Circuit breaker reset for " + operationName);
                isOpen = false;
                failureCount = 0;
            }
            
            // If circuit is open, fail fast
            if (isOpen) {
                throw LumaSGException.configError("Circuit breaker is open for " + operationName + 
                    " (failures: " + failureCount + ", threshold: " + failureThreshold + ")");
            }
            
            try {
                T result = operation.get();
                // Success - reset failure count
                if (failureCount > 0) {
                    logger.info("Circuit breaker success for " + operationName + ", resetting failure count");
                    failureCount = 0;
                }
                return result;
            } catch (Exception e) {
                failureCount++;
                lastFailureTime = System.currentTimeMillis();
                
                if (failureCount >= failureThreshold) {
                    isOpen = true;
                    logger.log(Level.SEVERE, "Circuit breaker opened for " + operationName + 
                        " after " + failureCount + " failures", e);
                }
                
                throw LumaSGException.configError("Operation failed in circuit breaker: " + operationName, e);
            }
        }
        
        /**
         * Gets the current state of the circuit breaker.
         * 
         * @return true if the circuit breaker is open, false otherwise
         */
        public boolean isOpen() {
            return isOpen;
        }
        
        /**
         * Gets the current failure count.
         * 
         * @return The current failure count
         */
        public int getFailureCount() {
            return failureCount;
        }
    }
} 