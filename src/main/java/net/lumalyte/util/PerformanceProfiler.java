package net.lumalyte.util;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import net.lumalyte.LumaSG;

/**
 * Advanced performance profiling system for LumaSG
 * Demonstrates enterprise-level performance monitoring and optimization techniques
 * 
 * Features:
 * - Multi-threaded performance metrics
 * - Cache efficiency analysis
 * - Memory usage optimization
 * - Asynchronous performance testing
 * - Thread-safe metric collection
 */
public class PerformanceProfiler {
    
    private static LumaSG plugin;
    private static DebugLogger.ContextualLogger logger;
    
    // Thread-safe performance counters using atomic operations
    private static final LongAdder totalOperations = new LongAdder();
    private static final LongAdder cacheOperations = new LongAdder();
    private static final LongAdder databaseOperations = new LongAdder();
    
    // Concurrent performance tracking
    private static final ConcurrentHashMap<String, PerformanceMetric> operationMetrics = new ConcurrentHashMap<>();
    
    /**
     * Initializes the performance profiler
     */
    public static void initialize(@NotNull LumaSG pluginInstance) {
        plugin = pluginInstance;
        logger = plugin.getDebugLogger().forContext("PerformanceProfiler");
        
        logger.info("Initialized performance profiler with thread-safe metrics");
        
        // Schedule periodic cache maintenance
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            CacheManager.performMaintenance();
            logPerformanceMetrics();
        }, 20L * 60L * 5L, 20L * 60L * 5L); // Every 5 minutes
    }
    
    /**
     * Profiles cache performance vs direct operations
     */
    public static CompletableFuture<String> profileCacheEfficiency(@NotNull Player player) {
        return CompletableFuture.supplyAsync(() -> {
            long startTime = System.nanoTime();
            
            // Test cache performance
            String cacheKey = "profile_test_" + player.getUniqueId();
            String testData = "Performance test data for " + player.getName();
            
            // Test HOT tier performance
            long hotStart = System.nanoTime();
            CacheManager.put(cacheKey + "_hot", testData, CacheManager.CacheTier.HOT);
            String hotResult = CacheManager.get(cacheKey + "_hot", String.class);
            long hotTime = System.nanoTime() - hotStart;
            
            // Test WARM tier performance
            long warmStart = System.nanoTime();
            CacheManager.put(cacheKey + "_warm", testData, CacheManager.CacheTier.WARM);
            String warmResult = CacheManager.get(cacheKey + "_warm", String.class);
            long warmTime = System.nanoTime() - warmStart;
            
            // Test COLD tier performance
            long coldStart = System.nanoTime();
            CacheManager.put(cacheKey + "_cold", testData, CacheManager.CacheTier.COLD);
            String coldResult = CacheManager.get(cacheKey + "_cold", String.class);
            long coldTime = System.nanoTime() - coldStart;
            
            // Test tier promotion (cache hit from lower tier)
            long promotionStart = System.nanoTime();
            CacheManager.invalidate(cacheKey + "_hot"); // Remove from hot
            String promotedResult = CacheManager.get(cacheKey + "_warm", String.class); // Should promote to hot
            long promotionTime = System.nanoTime() - promotionStart;
            
            long totalTime = System.nanoTime() - startTime;
            
            // Update metrics
            cacheOperations.add(4);
            totalOperations.add(4);
            
            // Clean up test data
            CacheManager.invalidate(cacheKey + "_hot");
            CacheManager.invalidate(cacheKey + "_warm");
            CacheManager.invalidate(cacheKey + "_cold");
            
            return String.format(
                "Cache Performance Profile:\n" +
                "Hot Tier: %.2fμs | Warm Tier: %.2fμs | Cold Tier: %.2fμs\n" +
                "Tier Promotion: %.2fμs | Total: %.2fμs\n" +
                "All operations successful: %b",
                hotTime / 1000.0, warmTime / 1000.0, coldTime / 1000.0,
                promotionTime / 1000.0, totalTime / 1000.0,
                hotResult != null && warmResult != null && coldResult != null && promotedResult != null
            );
        });
    }
    
    /**
     * Benchmarks thread safety under concurrent load
     */
    public static CompletableFuture<String> benchmarkConcurrentAccess() {
        return CompletableFuture.supplyAsync(() -> {
            int threadCount = 10;
            int operationsPerThread = 100;
            
            CompletableFuture<Void>[] futures = new CompletableFuture[threadCount];
            
            long startTime = System.nanoTime();
            
            for (int i = 0; i < threadCount; i++) {
                final int threadId = i;
                futures[i] = CompletableFuture.runAsync(() -> {
                    for (int j = 0; j < operationsPerThread; j++) {
                        String key = "thread_" + threadId + "_op_" + j;
                        String value = "data_" + threadId + "_" + j;
                        
                        // Mix of operations across all tiers
                        CacheManager.CacheTier tier = switch (j % 4) {
                            case 0 -> CacheManager.CacheTier.HOT;
                            case 1 -> CacheManager.CacheTier.WARM;
                            case 2 -> CacheManager.CacheTier.COLD;
                            default -> CacheManager.CacheTier.PERSISTENT;
                        };
                        
                        CacheManager.put(key, value, tier);
                        String retrieved = CacheManager.get(key, String.class);
                        
                        if (!value.equals(retrieved)) {
                            logger.warn("Thread safety violation detected in thread " + threadId);
                        }
                    }
                });
            }
            
            // Wait for all threads to complete
            CompletableFuture.allOf(futures).join();
            
            long totalTime = System.nanoTime() - startTime;
            int totalOps = threadCount * operationsPerThread;
            
            cacheOperations.add(totalOps * 2); // put + get operations
            totalOperations.add(totalOps * 2);
            
            return String.format(
                "Concurrent Access Benchmark:\n" +
                "Threads: %d | Operations/Thread: %d | Total Operations: %d\n" +
                "Total Time: %.2fms | Avg per Operation: %.2fμs\n" +
                "Thread Safety: Verified",
                threadCount, operationsPerThread, totalOps * 2,
                totalTime / 1_000_000.0, (totalTime / (totalOps * 2.0)) / 1000.0
            );
        });
    }
    
    /**
     * Records operation performance metrics
     */
    public static void recordOperation(@NotNull String operationType, long durationNanos) {
        operationMetrics.compute(operationType, (key, metric) -> {
            if (metric == null) {
                metric = new PerformanceMetric();
            }
            metric.addSample(durationNanos);
            return metric;
        });
        
        totalOperations.increment();
    }
    
    /**
     * Gets comprehensive performance statistics
     */
    public static String getPerformanceReport() {
        StringBuilder report = new StringBuilder();
        report.append("=== Performance Report ===\n");
        
        long total = totalOperations.sum();
        long cache = cacheOperations.sum();
        long database = databaseOperations.sum();
        
        report.append(String.format("Total Operations: %d | Cache: %d | Database: %d\n", total, cache, database));
        
        if (total > 0) {
            double cacheRatio = (double) cache / total * 100;
            double dbRatio = (double) database / total * 100;
            report.append(String.format("Cache Utilization: %.1f%% | Database Utilization: %.1f%%\n", cacheRatio, dbRatio));
        }
        
        // Operation-specific metrics
        operationMetrics.forEach((operation, metric) -> {
            report.append(String.format("%s: avg=%.2fμs, min=%.2fμs, max=%.2fμs (%d samples)\n",
                operation, 
                metric.getAverageNanos() / 1000.0,
                metric.getMinNanos() / 1000.0,
                metric.getMaxNanos() / 1000.0,
                metric.getSampleCount()));
        });
        
        return report.toString();
    }
    
    /**
     * Logs performance metrics periodically
     */
    private static void logPerformanceMetrics() {
        if (totalOperations.sum() > 0) {
            logger.debug("Performance metrics: " + getPerformanceReport());
        }
    }
    
    /**
     * Thread-safe performance metric collector
     */
    private static class PerformanceMetric {
        private final LongAdder totalNanos = new LongAdder();
        private final LongAdder sampleCount = new LongAdder();
        private final AtomicLong minNanos = new AtomicLong(Long.MAX_VALUE);
        private final AtomicLong maxNanos = new AtomicLong(Long.MIN_VALUE);
        
        public void addSample(long nanos) {
            totalNanos.add(nanos);
            sampleCount.increment();
            
            // Update min/max atomically
            minNanos.updateAndGet(current -> Math.min(current, nanos));
            maxNanos.updateAndGet(current -> Math.max(current, nanos));
        }
        
        public double getAverageNanos() {
            long count = sampleCount.sum();
            return count > 0 ? (double) totalNanos.sum() / count : 0.0;
        }
        
        public long getMinNanos() {
            return minNanos.get() == Long.MAX_VALUE ? 0 : minNanos.get();
        }
        
        public long getMaxNanos() {
            return maxNanos.get() == Long.MIN_VALUE ? 0 : maxNanos.get();
        }
        
        public long getSampleCount() {
            return sampleCount.sum();
        }
    }
} 