package net.lumalyte.util;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.jetbrains.annotations.NotNull;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import net.lumalyte.LumaSG;

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
    
    // Central coordination cache for inter-cache dependencies
    private static final Cache<String, Object> COORDINATION_CACHE = Caffeine.newBuilder()
            .maximumSize(100)
            .expireAfterWrite(Duration.ofMinutes(5))
            .recordStats()
            .build();
    
    // Cache for metadata about other caches
    private static final Cache<String, CacheMetadata> CACHE_METADATA = Caffeine.newBuilder()
            .maximumSize(50)
            .expireAfterAccess(Duration.ofHours(1))
            .recordStats()
            .build();
    
    private static ScheduledExecutorService maintenanceExecutor;
    private static boolean initialized = false;
    
    /**
     * Initializes the cache manager
     */
    public static void initialize(@NotNull LumaSG pluginInstance) {
        if (initialized) {
            return;
        }
        
        plugin = pluginInstance;
        logger = plugin.getDebugLogger().forContext("CacheManager");
        
        // Initialize maintenance executor with custom thread factory
        maintenanceExecutor = Executors.newScheduledThreadPool(2, r -> {
            Thread thread = new Thread(r, "LumaSG-CacheManager-" + System.currentTimeMillis());
            thread.setDaemon(true);
            thread.setPriority(Thread.NORM_PRIORITY - 1); // Slightly lower priority
            return thread;
        });
        
        // Initialize all cache subsystems
        initializeCacheSubsystems();
        
        // Register cache metadata
        registerCacheMetadata();
        
        // Schedule automatic maintenance
        scheduleMaintenanceTasks();
        
        initialized = true;
        logger.info("Advanced multi-tier cache management system initialized");
    }
    
    /**
     * Initializes all cache subsystems
     */
    private static void initializeCacheSubsystems() {
        try {
            // Initialize core caching systems
            PlayerDataCache.initialize(plugin);
            SkinCache.initialize(plugin);
            GuiComponentCache.initialize(plugin);
            ScoreboardCache.initialize(plugin);
            InvitationManager.initialize(plugin);
            
            logger.info("All cache subsystems initialized successfully");
        } catch (Exception e) {
            logger.error("Failed to initialize cache subsystems", e);
            throw new IllegalStateException("Cache initialization failed", e);
        }
    }
    
    /**
     * Registers metadata for all caches
     */
    private static void registerCacheMetadata() {
        // Register metadata for each cache subsystem
        CACHE_METADATA.put("player_data", new CacheMetadata("PlayerDataCache", CacheTier.HOT, 500, Duration.ofMinutes(15)));
        CACHE_METADATA.put("skin_cache", new CacheMetadata("SkinCache", CacheTier.WARM, 1000, Duration.ofHours(6)));
        CACHE_METADATA.put("gui_components", new CacheMetadata("GuiComponentCache", CacheTier.WARM, 500, Duration.ofMinutes(30)));
        CACHE_METADATA.put("scoreboard", new CacheMetadata("ScoreboardCache", CacheTier.HOT, 1000, Duration.ofSeconds(30)));
        CACHE_METADATA.put("invitations", new CacheMetadata("InvitationManager", CacheTier.HOT, 1000, Duration.ofSeconds(30)));
        
        logger.debug("Cache metadata registered for " + CACHE_METADATA.estimatedSize() + " subsystems");
    }
    
    /**
     * Schedules automatic maintenance tasks
     */
    private static void scheduleMaintenanceTasks() {
        // Light maintenance every minute
        maintenanceExecutor.scheduleAtFixedRate(CacheManager::performLightMaintenance, 1, 1, TimeUnit.MINUTES);
        
        // Heavy maintenance every 10 minutes
        maintenanceExecutor.scheduleAtFixedRate(CacheManager::performHeavyMaintenance, 5, 10, TimeUnit.MINUTES);
        
        // Statistics update every 5 minutes
        maintenanceExecutor.scheduleAtFixedRate(CacheManager::updateCacheStatistics, 2, 5, TimeUnit.MINUTES);
        
        logger.debug("Scheduled automatic maintenance tasks");
    }
    
    /**
     * Performs light maintenance operations
     */
    private static void performLightMaintenance() {
        try {
            // Perform cleanup on high-frequency caches
            PlayerDataCache.performMaintenance();
            ScoreboardCache.performMaintenance();
            
            // Clean up tier-specific caches
            HOT_CACHE.cleanUp();
            WARM_CACHE.cleanUp();
            
            // Update coordination cache
            COORDINATION_CACHE.cleanUp();
            
        } catch (Exception e) {
            logger.warn("Light maintenance failed", e);
        }
    }
    
    /**
     * Performs heavy maintenance operations
     */
    private static void performHeavyMaintenance() {
        try {
            // Perform maintenance on all cache subsystems
            SkinCache.clearCache(); // Periodic skin cache refresh
            GuiComponentCache.performMaintenance();
            
            // Clean up cold tier and persistent cache
            COLD_CACHE.cleanUp();
            PERSISTENT_CACHE.entrySet().removeIf(entry -> {
                if (!entry.getKey().startsWith("temp:")) {
                    return false; // Keep non-temp entries
                }
                
                // Safely check if value is a Long timestamp
                Object value = entry.getValue();
                if (!(value instanceof Long)) {
                    // Remove invalid temp entries that aren't timestamps
                    logger.warn("Found invalid temp cache entry with non-Long value: " + entry.getKey() + 
                               " (type: " + (value != null ? value.getClass().getSimpleName() : "null") + ")");
                    return true;
                }
                
                // Remove expired temp entries
                long timestamp = (Long) value;
                return System.currentTimeMillis() - timestamp > Duration.ofHours(24).toMillis();
            });
            
            // Update cache metadata
            updateCacheMetadata();
            
            // Log maintenance completion
            logger.debug("Heavy maintenance completed - Cold cache cleaned, persistent cache optimized");
            
        } catch (Exception e) {
            logger.warn("Heavy maintenance failed", e);
        }
    }
    
    /**
     * Updates cache statistics
     */
    private static void updateCacheStatistics() {
        try {
            // Update global cache metrics
            totalRequests.incrementAndGet();
            
            // Calculate hit/miss ratios from coordination cache
            double coordinationHitRate = COORDINATION_CACHE.stats().hitRate();
            if (coordinationHitRate > 0.8) {
                cacheHits.incrementAndGet();
            } else {
                cacheMisses.incrementAndGet();
            }
            
            logger.debug("Cache statistics updated - Total requests: " + totalRequests.get() + 
                        ", Hits: " + cacheHits.get() + ", Misses: " + cacheMisses.get());
        } catch (Exception e) {
            logger.warn("Failed to update cache statistics", e);
        }
    }
    
    /**
     * Updates metadata for all registered caches
     */
    private static void updateCacheMetadata() {
        for (CacheMetadata metadata : CACHE_METADATA.asMap().values()) {
            metadata.lastMaintenanceTime = System.currentTimeMillis();
        }
    }
    
    /**
     * Gets detailed statistics from all cache subsystems
     * 
     * @return Formatted string containing all cache statistics
     */
    public static String getDetailedStats() {
        return getAllCacheStats();
    }
    
    /**
     * Gets comprehensive statistics from all cache subsystems
     * 
     * @return Formatted string containing all cache statistics
     */
    public static String getAllCacheStats() {
        StringBuilder stats = new StringBuilder();
        stats.append("=== LumaSG Multi-Tier Cache System Statistics ===\n\n");
        
        // Add individual cache statistics
        stats.append(PlayerDataCache.getCacheStats()).append("\n\n");
        stats.append(SkinCache.getCacheStats()).append("\n\n");
        stats.append(GuiComponentCache.getCacheStats()).append("\n\n");
        stats.append(ScoreboardCache.getCacheStats()).append("\n\n");
        stats.append(InvitationManager.getStats()).append("\n\n");
        
        // Add coordination cache stats
        stats.append(String.format(
            "Coordination Cache - Size: %d, Hit Rate: %.2f%%\n",
            COORDINATION_CACHE.estimatedSize(),
            COORDINATION_CACHE.stats().hitRate() * 100
        ));
        
        // Add cache tier summary
        stats.append("\n=== Cache Tier Summary ===\n");
        for (CacheMetadata metadata : CACHE_METADATA.asMap().values()) {
            stats.append(String.format(
                "%s (%s): Hit Rate: %.2f%%, Max Size: %d, TTL: %s\n",
                metadata.cacheName,
                metadata.tier,
                metadata.getHitRate() * 100,
                metadata.maxSize,
                metadata.ttl
            ));
        }
        
        return stats.toString();
    }
    
    /**
     * Invalidates all caches (emergency cleanup)
     */
    public static void invalidateAllCaches() {
        logger.warn("Performing emergency cache invalidation");
        
        PlayerDataCache.clearCache();
        SkinCache.clearCache();
        GuiComponentCache.clearAllCaches();
        ScoreboardCache.clearAllCaches();
        InvitationManager.clearAll();
        
        COORDINATION_CACHE.invalidateAll();
        CACHE_METADATA.invalidateAll();
        
        // Re-register metadata
        registerCacheMetadata();
        
        logger.info("All caches invalidated and metadata re-registered");
    }
    
    /**
     * Performs cache warming for frequently accessed data
     * 
     * @param players The players to warm caches for
     */
    public static void warmCaches(Iterable<org.bukkit.entity.Player> players) {
        logger.info("Starting cache warming process");
        
        for (org.bukkit.entity.Player player : players) {
            try {
                // Warm player data caches
                PlayerDataCache.preloadPlayerData(player);
                
                // Warm skin cache
                SkinCache.preloadSkin(player);
                
            } catch (Exception e) {
                logger.warn("Failed to warm caches for player: " + player.getName(), e);
            }
        }
        
        logger.info("Cache warming process completed");
    }
    
    /**
     * Gets cache health status
     * 
     * @return Health status string
     */
    public static String getCacheHealth() {
        StringBuilder health = new StringBuilder();
        health.append("=== Cache Health Status ===\n");
        
        // Check each cache subsystem
        boolean allHealthy = true;
        
        try {
            // Check if caches are responsive
            PlayerDataCache.performMaintenance();
            health.append("PlayerDataCache: HEALTHY\n");
        } catch (Exception e) {
            health.append("PlayerDataCache: UNHEALTHY - ").append(e.getMessage()).append("\n");
            allHealthy = false;
        }
        
        try {
            SkinCache.getCacheStats();
            health.append("SkinCache: HEALTHY\n");
        } catch (Exception e) {
            health.append("SkinCache: UNHEALTHY - ").append(e.getMessage()).append("\n");
            allHealthy = false;
        }
        
        try {
            GuiComponentCache.performMaintenance();
            health.append("GuiComponentCache: HEALTHY\n");
        } catch (Exception e) {
            health.append("GuiComponentCache: UNHEALTHY - ").append(e.getMessage()).append("\n");
            allHealthy = false;
        }
        
        try {
            ScoreboardCache.performMaintenance();
            health.append("ScoreboardCache: HEALTHY\n");
        } catch (Exception e) {
            health.append("ScoreboardCache: UNHEALTHY - ").append(e.getMessage()).append("\n");
            allHealthy = false;
        }
        
        health.append("\nOverall Status: ").append(allHealthy ? "HEALTHY" : "DEGRADED").append("\n");
        
        return health.toString();
    }
    
    /**
     * Performs maintenance on all cache systems
     */
    public static void performMaintenance() {
        performLightMaintenance();
        performHeavyMaintenance();
    }
    
    /**
     * Puts a value into the specified cache tier
     */
    public static void put(String key, Object value, CacheTier tier) {
        switch (tier) {
            case HOT -> HOT_CACHE.put(key, value);
            case WARM -> WARM_CACHE.put(key, value);
            case COLD -> COLD_CACHE.put(key, value);
            case PERSISTENT -> PERSISTENT_CACHE.put(key, value);
        }
    }
    
    /**
     * Gets a value from the cache tiers
     */
    @SuppressWarnings("unchecked")
    public static <T> T get(String key, Class<T> clazz) {
        Object value = HOT_CACHE.getIfPresent(key);
        if (value != null) {
            return clazz.cast(value);
        }
        
        value = WARM_CACHE.getIfPresent(key);
        if (value != null) {
            // Promote to hot cache
            HOT_CACHE.put(key, value);
            return clazz.cast(value);
        }
        
        value = COLD_CACHE.getIfPresent(key);
        if (value != null) {
            // Promote to warm cache
            WARM_CACHE.put(key, value);
            return clazz.cast(value);
        }
        
        value = PERSISTENT_CACHE.get(key);
        if (value != null) {
            return clazz.cast(value);
        }
        
        return null;
    }
    
    /**
     * Invalidates a key from all cache tiers
     */
    public static void invalidate(String key) {
        HOT_CACHE.invalidate(key);
        WARM_CACHE.invalidate(key);
        COLD_CACHE.invalidate(key);
        PERSISTENT_CACHE.remove(key);
    }
    
    /**
     * Shuts down the cache management system
     */
    public static void shutdown() {
        if (!initialized) {
            return;
        }
        
        logger.info("Shutting down cache management system");
        
        // Perform final cleanup
        performHeavyMaintenance();
        
        // Shutdown maintenance executor
        if (maintenanceExecutor != null) {
            maintenanceExecutor.shutdown();
            try {
                if (!maintenanceExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    maintenanceExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                maintenanceExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        // Clear all caches
        invalidateAllCaches();
        
        initialized = false;
        logger.info("Cache management system shutdown completed");
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
    
    /**
     * Metadata container for cache information
     */
    private static class CacheMetadata {
        private final String cacheName;
        private final CacheTier tier;
        private final long maxSize;
        private final Duration ttl;
        private long lastMaintenanceTime;
        private long totalHits;
        private long totalMisses;
        
        public CacheMetadata(String cacheName, CacheTier tier, long maxSize, Duration ttl) {
            this.cacheName = cacheName;
            this.tier = tier;
            this.maxSize = maxSize;
            this.ttl = ttl;
            this.lastMaintenanceTime = System.currentTimeMillis();
        }
        
        public void updateStats(long hits, long misses) {
            this.totalHits = hits;
            this.totalMisses = misses;
        }
        
        public double getHitRate() {
            long total = totalHits + totalMisses;
            return total > 0 ? (double) totalHits / total : 0.0;
        }
    }
} 