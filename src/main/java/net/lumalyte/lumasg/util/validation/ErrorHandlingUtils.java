package net.lumalyte.lumasg.util.validation;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import net.lumalyte.lumasg.exception.LumaSGException;

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
    
    private static final ErrorClassifier ERROR_CLASSIFIER = new ErrorClassifier();
    
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
        
        RetryContext<T> context = new RetryContext<>(
            operation, maxRetries, initialDelayMs, backoffMultiplier, logger, operationName);
            
        return context.execute();
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
        
        AsyncRetryContext<T> context = new AsyncRetryContext<>(
            operation, maxRetries, initialDelayMs, backoffMultiplier, logger, operationName);
            
        return context.execute();
    }
    
    /**
     * Determines if an error is recoverable and should be retried.
     * 
     * @param throwable The error to classify
     * @return true if the error is recoverable, false otherwise
     */
    public static boolean isRecoverableError(@NotNull Throwable throwable) {
        return ERROR_CLASSIFIER.isRecoverable(throwable);
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
        
        String message = buildErrorMessage(operation, context);
        logger.log(level, message, throwable);
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
    
    private static String buildErrorMessage(String operation, String context) {
        StringBuilder message = new StringBuilder("Error in operation: ").append(operation);
        if (context != null && !context.isEmpty()) {
            message.append(" (").append(context).append(")");
        }
        return message.toString();
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
    
    /**
     * Context class for retry operations.
     */
    private static class RetryContext<T> {
        private final Supplier<T> operation;
        private final int maxRetries;
        private final double backoffMultiplier;
        private final Logger logger;
        private final String operationName;
        private Exception lastException;
        private long currentDelay;
        
        RetryContext(
                Supplier<T> operation,
                int maxRetries,
                long initialDelayMs,
                double backoffMultiplier,
                Logger logger,
                String operationName) {
            this.operation = operation;
            this.maxRetries = maxRetries;
            this.backoffMultiplier = backoffMultiplier;
            this.logger = logger;
            this.operationName = operationName;
            this.currentDelay = initialDelayMs;
        }
        
        T execute() throws LumaSGException {
            for (int attempt = 0; attempt <= maxRetries; attempt++) {
                try {
                    if (attempt > 0) {
                        handleRetryDelay(attempt);
                    }
                    return operation.get();
                } catch (Exception e) {
                    if (!handleError(e, attempt)) break;
                }
            }
            throw LumaSGException.configError(
                "Operation failed after " + (maxRetries + 1) + " attempts: " + operationName,
                lastException);
        }
        
        private void handleRetryDelay(int attempt) throws LumaSGException {
            logger.info("Retrying " + operationName + " (attempt " + attempt + "/" + (maxRetries + 1) + ")");
            try {
                Thread.sleep(currentDelay);
                currentDelay = (long) (currentDelay * backoffMultiplier);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw LumaSGException.configError("Operation interrupted during retry: " + operationName, e);
            }
        }
        
        private boolean handleError(Exception e, int attempt) {
            lastException = e;
            
            if (attempt == maxRetries) {
                logger.log(Level.SEVERE, "All retry attempts failed for " + operationName, e);
                return false;
            }
            
            if (!isRecoverableError(e)) {
                logger.log(Level.SEVERE, "Non-recoverable error in " + operationName + ", aborting retries", e);
                return false;
            }
            
            logger.log(Level.WARNING, "Recoverable error in " + operationName + " (attempt " + (attempt + 1) + "), will retry", e);
            return true;
        }
    }
    
    /**
     * Context class for async retry operations.
     */
    private static class AsyncRetryContext<T> {
        private final Supplier<CompletableFuture<T>> operation;
        private final int maxRetries;
        private final long initialDelayMs;
        private final double backoffMultiplier;
        private final Logger logger;
        private final String operationName;
        
        AsyncRetryContext(
                Supplier<CompletableFuture<T>> operation,
                int maxRetries,
                long initialDelayMs,
                double backoffMultiplier,
                Logger logger,
                String operationName) {
            this.operation = operation;
            this.maxRetries = maxRetries;
            this.initialDelayMs = initialDelayMs;
            this.backoffMultiplier = backoffMultiplier;
            this.logger = logger;
            this.operationName = operationName;
        }
        
        CompletableFuture<T> execute() {
            return executeWithRetry(0, initialDelayMs);
        }
        
        private CompletableFuture<T> executeWithRetry(int currentAttempt, long currentDelay) {
            if (currentAttempt > 0) {
                logger.info("Retrying " + operationName + " (attempt " + (currentAttempt + 1) + "/" + (maxRetries + 1) + ")");
            }
            
            return operation.get()
                .exceptionally(throwable -> handleError(throwable, currentAttempt))
                .thenCompose(result -> {
                    if (result == null && currentAttempt < maxRetries) {
                        return scheduleRetry(currentAttempt, currentDelay);
                    }
                    return CompletableFuture.completedFuture(result);
                });
        }
        
        private T handleError(Throwable throwable, int currentAttempt) {
            if (currentAttempt >= maxRetries) {
                logger.log(Level.SEVERE, "All async retry attempts failed for " + operationName, throwable);
                throw new IllegalStateException("Operation failed after " + (maxRetries + 1) + " attempts: " + operationName, throwable);
            }
            
            if (!isRecoverableError(throwable)) {
                logger.log(Level.SEVERE, "Non-recoverable error in async " + operationName + ", aborting retries", throwable);
                throw new IllegalStateException("Non-recoverable error: " + operationName, throwable);
            }
            
            logger.log(Level.WARNING, "Recoverable error in async " + operationName + " (attempt " + (currentAttempt + 1) + "), will retry", throwable);
            return null;
        }
        
        private CompletableFuture<T> scheduleRetry(int currentAttempt, long currentDelay) {
            return CompletableFuture
                .supplyAsync(() -> null, CompletableFuture.delayedExecutor(currentDelay, TimeUnit.MILLISECONDS))
                .thenCompose(v -> executeWithRetry(
                    currentAttempt + 1,
                    (long) (currentDelay * backoffMultiplier)
                ));
        }
    }
}

/**
 * Handles error classification logic.
 */
class ErrorClassifier {
    private final Map<Class<? extends Throwable>, Predicate<Throwable>> errorClassifiers;
    
    ErrorClassifier() {
        errorClassifiers = new ConcurrentHashMap<>();
        initializeClassifiers();
    }
    
    private void initializeClassifiers() {
        // IO errors are always recoverable
        errorClassifiers.put(IOException.class, throwable -> true);
        
        // SQL errors are recoverable if they're connection or timeout related
        errorClassifiers.put(SQLException.class, this::isRecoverableSQLError);
        
        // Runtime errors are recoverable if they're timeout or resource related
        errorClassifiers.put(RuntimeException.class, this::isRecoverableRuntimeError);
        
        // LumaSG errors are recoverable based on their type
        errorClassifiers.put(LumaSGException.class, this::isRecoverableLumaSGError);
    }
    
    boolean isRecoverable(Throwable throwable) {
        // Check each registered error type
        for (Map.Entry<Class<? extends Throwable>, Predicate<Throwable>> entry : errorClassifiers.entrySet()) {
            if (entry.getKey().isInstance(throwable)) {
                return entry.getValue().test(throwable);
            }
        }
        
        // Check for unrecoverable error types and return opposite
        return !isUnrecoverableError(throwable);
    }
    
    private boolean isRecoverableSQLError(Throwable throwable) {
        SQLException sqlException = (SQLException) throwable;
        String sqlState = sqlException.getSQLState();
        String message = sqlException.getMessage();
        
        if (message == null) return false;
        String lowerMessage = message.toLowerCase();
        
        // Connection errors (08xxx) are recoverable
        if (sqlState != null && sqlState.startsWith("08")) return true;
        
        // Timeout or lock errors are recoverable
        return lowerMessage.contains("timeout") || lowerMessage.contains("lock");
    }
    
    private boolean isRecoverableRuntimeError(Throwable throwable) {
        if (throwable.getMessage() == null) return false;
        String message = throwable.getMessage().toLowerCase();
        
        // Common recoverable runtime errors
        return message.contains("timeout") ||
               message.contains("too many connections") ||
               message.contains("connection reset") ||
               message.contains("temporarily unavailable");
    }
    
    private boolean isRecoverableLumaSGError(Throwable throwable) {
        // Most database errors are recoverable
        if (throwable instanceof LumaSGException.DatabaseException) return true;
        
        // Game and arena errors might be recoverable depending on context
        if (throwable instanceof LumaSGException.GameException ||
            throwable instanceof LumaSGException.ArenaException ||
            throwable instanceof LumaSGException.ChestException) {
            return true;
        }
        
        // Configuration, validation, and player errors are not recoverable
        return !(throwable instanceof LumaSGException.ConfigurationException ||
                throwable instanceof LumaSGException.ValidationException ||
                throwable instanceof LumaSGException.PlayerException);
    }
    
    private boolean isUnrecoverableError(Throwable throwable) {
        return throwable instanceof OutOfMemoryError ||
               throwable instanceof StackOverflowError ||
               throwable instanceof LinkageError ||
               throwable instanceof AssertionError;
    }
} 
