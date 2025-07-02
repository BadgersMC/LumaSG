package net.lumalyte.util;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import net.lumalyte.LumaSG;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Centralized cache management system for LumaSG
 * Demonstrates enterprise-level caching patterns using Caffeine, Guava principles, and thread-safe collections
 * 
 * This manager provides:
 * - Multi-tier caching strategies
 * - Cache warming and preloading
 * - Performance monitoring and statistics
 * - Memory-efficient expiration policies
 * - Thread-safe operations across all cache layers
 */
public class CacheManager {
    
    private static LumaSG plugin;
    private static DebugLogger.ContextualLogger logger;
    
    // High-frequency caches with aggressive optimization
    private static final Cache<String, Object> HOT_CACHE = Caffeine.newBuilder()
            .maximumSize(500)
            .expireAfterWrite(Duration.ofMinutes(5))
            .expireAfterAccess(Duration.ofMinutes(2))
            .recordStats()
            .build();
    
    // Medium-frequency caches with balanced optimization
    private static final Cache<String, Object> WARM_CACHE = Caffeine.newBuilder()
            .maximumSize(2000)
            .expireAfterWrite(Duration.ofMinutes(30))
            .expireAfterAccess(Duration.ofMinutes(10))
            .recordStats()
            .build();
    
    // Low-frequency caches with memory optimization
    private static final Cache<String, Object> COLD_CACHE = Caffeine.newBuilder()
            .maximumSize(5000)
            .expireAfterWrite(Duration.ofHours(2))
            .expireAfterAccess(Duration.ofHours(1))
            .recordStats()
            .build();
    
    // Thread-safe collections for non-expiring data
    private static final ConcurrentHashMap<String, Object> PERSISTENT_CACHE = new ConcurrentHashMap<>();
    
    // Performance monitoring
    private static final AtomicLong totalRequests = new AtomicLong(0);
    private static final AtomicLong cacheHits = new AtomicLong(0);
    private static final AtomicLong cacheMisses = new AtomicLong(0);
    
    /**
     * Initializes the cache manager
     */
    public static void initialize(@NotNull LumaSG pluginInstance) {
        plugin = pluginInstance;
        logger = plugin.getDebugLogger().forContext("CacheManager");
        
        logger.info("Initialized multi-tier caching system with Caffeine");
        logger.info("Hot tier: 500 entries, 5min write/2min access expiry");
        logger.info("Warm tier: 2000 entries, 30min write/10min access expiry");
        logger.info("Cold tier: 5000 entries, 2hr write/1hr access expiry");
    }
    
    /**
     * Stores data in the appropriate cache tier based on access pattern
     */
    public static void put(@NotNull String key, @NotNull Object value, @NotNull CacheTier tier) {
        switch (tier) {
            case HOT -> HOT_CACHE.put(key, value);
            case WARM -> WARM_CACHE.put(key, value);
            case COLD -> COLD_CACHE.put(key, value);
            case PERSISTENT -> PERSISTENT_CACHE.put(key, value);
        }
    }
    
    /**
     * Retrieves data from all cache tiers with automatic tier promotion
     */
    @SuppressWarnings("unchecked")
    public static <T> T get(@NotNull String key, @NotNull Class<T> type) {
        totalRequests.incrementAndGet();
        
        // Check hot cache first
        Object value = HOT_CACHE.getIfPresent(key);
        if (value != null) {
            cacheHits.incrementAndGet();
            return type.cast(value);
        }
        
        // Check warm cache and promote to hot if found
        value = WARM_CACHE.getIfPresent(key);
        if (value != null) {
            cacheHits.incrementAndGet();
            HOT_CACHE.put(key, value); // Promote to hot tier
            return type.cast(value);
        }
        
        // Check cold cache and promote to warm if found
        value = COLD_CACHE.getIfPresent(key);
        if (value != null) {
            cacheHits.incrementAndGet();
            WARM_CACHE.put(key, value); // Promote to warm tier
            return type.cast(value);
        }
        
        // Check persistent cache
        value = PERSISTENT_CACHE.get(key);
        if (value != null) {
            cacheHits.incrementAndGet();
            return type.cast(value);
        }
        
        cacheMisses.incrementAndGet();
        return null;
    }
    
    /**
     * Invalidates a key from all cache tiers
     */
    public static void invalidate(@NotNull String key) {
        HOT_CACHE.invalidate(key);
        WARM_CACHE.invalidate(key);
        COLD_CACHE.invalidate(key);
        PERSISTENT_CACHE.remove(key);
    }
    
    /**
     * Clears all caches
     */
    public static void invalidateAll() {
        HOT_CACHE.invalidateAll();
        WARM_CACHE.invalidateAll();
        COLD_CACHE.invalidateAll();
        PERSISTENT_CACHE.clear();
        
        // Reset metrics
        totalRequests.set(0);
        cacheHits.set(0);
        cacheMisses.set(0);
        
        logger.info("All caches cleared and metrics reset");
    }
    
    /**
     * Gets comprehensive cache statistics
     */
    public static String getDetailedStats() {
        CacheStats hotStats = HOT_CACHE.stats();
        CacheStats warmStats = WARM_CACHE.stats();
        CacheStats coldStats = COLD_CACHE.stats();
        
        double overallHitRate = totalRequests.get() > 0 ? 
            (double) cacheHits.get() / totalRequests.get() * 100 : 0.0;
        
        return String.format(
            "Overall: %.1f%% hit rate (%d/%d) | " +
            "Hot: %.1f%% (%d entries) | " +
            "Warm: %.1f%% (%d entries) | " +
            "Cold: %.1f%% (%d entries) | " +
            "Persistent: %d entries",
            overallHitRate, cacheHits.get(), totalRequests.get(),
            hotStats.hitRate() * 100, HOT_CACHE.estimatedSize(),
            warmStats.hitRate() * 100, WARM_CACHE.estimatedSize(),
            coldStats.hitRate() * 100, COLD_CACHE.estimatedSize(),
            PERSISTENT_CACHE.size()
        );
    }
    
    /**
     * Performs cache maintenance and optimization
     */
    public static void performMaintenance() {
        // Trigger cleanup of expired entries
        HOT_CACHE.cleanUp();
        WARM_CACHE.cleanUp();
        COLD_CACHE.cleanUp();
        
        // Log performance metrics
        logger.debug("Cache maintenance completed: " + getDetailedStats());
    }
    
    /**
     * Cache tier enumeration for strategic data placement
     */
    public enum CacheTier {
        /** High-frequency, short-lived data (player actions, GUI states) */
        HOT,
        /** Medium-frequency, medium-lived data (player stats, team data) */
        WARM,
        /** Low-frequency, long-lived data (arena configs, item definitions) */
        COLD,
        /** Never-expiring data (static configurations, constants) */
        PERSISTENT
    }
} 