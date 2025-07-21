package net.lumalyte.lumasg.util.performance;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

import org.jetbrains.annotations.NotNull;

import net.lumalyte.lumasg.LumaSG;
import net.lumalyte.lumasg.util.core.DebugLogger;

/**
 * Performance profiling utility for monitoring execution times and resource usage.
 * Provides thread-safe performance metrics collection and reporting.
 */
public class PerformanceProfiler {
    private static LumaSG plugin;
    private static DebugLogger.ContextualLogger logger;
    private static final ConcurrentMap<String, ProfileData> profiles = new ConcurrentHashMap<>();
    private static boolean enabled = false;
    private static boolean initialized = false;

    /**
     * Initializes the performance profiler.
     */
    public static void initialize(@NotNull LumaSG pluginInstance) {
        if (initialized) {
            return;
        }
        
        plugin = pluginInstance;
        logger = plugin.getDebugLogger().forContext("PerformanceProfiler");
        enabled = plugin.getConfig().getBoolean("debug.performance-profiling", false);
        initialized = true;
        
        logger.info("Performance profiler initialized (enabled: " + enabled + ")");
    }

    /**
     * Starts profiling a named operation.
     */
    public static @NotNull ProfileSession startProfiling(@NotNull String operationName) {
        if (!enabled || !initialized) {
            return new NoOpProfileSession();
        }
        
        return new ActiveProfileSession(operationName, System.nanoTime());
    }

    /**
     * Records the completion of a profiling session.
     */
    private static void recordCompletion(@NotNull String operationName, long startTime) {
        if (!enabled || !initialized) return;
        
        long duration = System.nanoTime() - startTime;
        ProfileData data = profiles.computeIfAbsent(operationName, k -> new ProfileData());
        data.recordExecution(duration);
        
        if (logger.isDebugEnabled()) {
            logger.debug("Operation '" + operationName + "' completed in " + 
                String.format("%.3f", duration / 1_000_000.0) + "ms");
        }
    }

    /**
     * Gets performance statistics for an operation.
     */
    public static @NotNull String getStats(@NotNull String operationName) {
        ProfileData data = profiles.get(operationName);
        if (data == null) {
            return "No data for operation: " + operationName;
        }
        return data.getStatistics(operationName);
    }

    /**
     * Gets all performance statistics.
     */
    public static @NotNull String getAllStats() {
        if (profiles.isEmpty()) {
            return "No performance data collected";
        }
        
        StringBuilder sb = new StringBuilder("Performance Statistics:\n");
        profiles.forEach((name, data) -> {
            sb.append(data.getStatistics(name)).append("\n");
        });
        return sb.toString();
    }

    /**
     * Clears all performance data.
     */
    public static void clearStats() {
        profiles.clear();
        if (logger != null) {
            logger.debug("Performance statistics cleared");
        }
    }

    /**
     * Shuts down the performance profiler.
     */
    public static void shutdown() {
        if (!initialized) {
            return;
        }
        
        clearStats();
        initialized = false;
        enabled = false;
        
        if (logger != null) {
            logger.info("Performance profiler shut down");
        }
    }

    /**
     * Interface for profiling sessions.
     */
    public interface ProfileSession extends AutoCloseable {
        @Override
        void close();
    }

    /**
     * Active profiling session implementation.
     */
    private static class ActiveProfileSession implements ProfileSession {
        private final String operationName;
        private final long startTime;
        private boolean closed = false;

        public ActiveProfileSession(@NotNull String operationName, long startTime) {
            this.operationName = operationName;
            this.startTime = startTime;
        }

        @Override
        public void close() {
            if (!closed) {
                recordCompletion(operationName, startTime);
                closed = true;
            }
        }
    }

    /**
     * No-operation profiling session for when profiling is disabled.
     */
    private static class NoOpProfileSession implements ProfileSession {
        @Override
        public void close() {
            // No operation
        }
    }

    /**
     * Stores profiling data for an operation.
     */
    private static class ProfileData {
        private final AtomicLong totalExecutions = new AtomicLong(0);
        private final AtomicLong totalTime = new AtomicLong(0);
        private volatile long minTime = Long.MAX_VALUE;
        private volatile long maxTime = Long.MIN_VALUE;

        public void recordExecution(long duration) {
            totalExecutions.incrementAndGet();
            totalTime.addAndGet(duration);
            
            // Update min/max (not perfectly thread-safe but good enough for profiling)
            if (duration < minTime) minTime = duration;
            if (duration > maxTime) maxTime = duration;
        }

        public @NotNull String getStatistics(@NotNull String operationName) {
            long executions = totalExecutions.get();
            if (executions == 0) {
                return operationName + ": No executions recorded";
            }
            
            long total = totalTime.get();
            double avgMs = (total / (double) executions) / 1_000_000.0;
            double minMs = minTime / 1_000_000.0;
            double maxMs = maxTime / 1_000_000.0;
            double totalMs = total / 1_000_000.0;
            
            return String.format("%s: %d executions, avg: %.3fms, min: %.3fms, max: %.3fms, total: %.3fms",
                operationName, executions, avgMs, minMs, maxMs, totalMs);
        }
    }
} 
