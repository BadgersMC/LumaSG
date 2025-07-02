package net.lumalyte.util;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;

import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachment;

import com.github.benmanes.caffeine.cache.AsyncLoadingCache;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;

import net.lumalyte.LumaSG;
import net.lumalyte.statistics.PlayerStats;

/**
 * Advanced high-performance player data caching system using Caffeine
 * Features LoadingCache, AsyncLoadingCache, write-through caching, and refresh-ahead patterns
 */
public class PlayerDataCache {
    
    // Advanced AsyncLoadingCache for player statistics with automatic refresh-ahead
    private static AsyncLoadingCache<UUID, PlayerStats> STATS_CACHE;
    
    // LoadingCache for permissions with efficient bulk loading
    private static LoadingCache<String, Boolean> PERMISSION_CACHE;
    
    // Cache for display names with size-based eviction
    private static final Cache<UUID, String> DISPLAY_NAME_CACHE = Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(Duration.ofMinutes(30))
            .recordStats()
            .build();
    
    // Cache for permission attachments to reduce object creation
    private static final Cache<UUID, PermissionAttachment> PERMISSION_ATTACHMENT_CACHE = Caffeine.newBuilder()
            .maximumSize(500)
            .expireAfterAccess(Duration.ofMinutes(15))
            .recordStats()
            .weakValues() // Allow GC when player disconnects
            .build();
    
    // Cache for computed player rankings with refresh-ahead
    private static AsyncLoadingCache<String, Integer> PLAYER_RANKING_CACHE;
    
    private static LumaSG plugin;
    private static DebugLogger.ContextualLogger logger;
    private static final Executor customExecutor = ForkJoinPool.commonPool();
    
    /**
     * Initializes the advanced player data cache with plugin instance
     * 
     * @param pluginInstance The plugin instance
     */
    public static void initialize(LumaSG pluginInstance) {
        plugin = pluginInstance;
        logger = plugin.getDebugLogger().forContext("PlayerDataCache");
        
        // Initialize AsyncLoadingCache for player statistics with automatic refresh-ahead
        STATS_CACHE = Caffeine.newBuilder()
                .maximumSize(500)
                .expireAfterWrite(Duration.ofMinutes(30))
                .expireAfterAccess(Duration.ofMinutes(15))
                .refreshAfterWrite(Duration.ofMinutes(10)) // Refresh-ahead pattern
                .recordStats()
                .executor(customExecutor)
                .buildAsync((UUID uuid, Executor executor) -> 
                    CompletableFuture.supplyAsync(() -> loadPlayerStatsFromDatabase(uuid), executor)
                );
        
        // Initialize LoadingCache for permissions with optimized bulk loading
        PERMISSION_CACHE = Caffeine.newBuilder()
                .maximumSize(2000)
                .expireAfterWrite(Duration.ofMinutes(10))
                .expireAfterAccess(Duration.ofMinutes(5))
                .recordStats()
                .build(PlayerDataCache::loadPermissionFromBukkit);
        
        // Initialize AsyncLoadingCache for player rankings
        PLAYER_RANKING_CACHE = Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(Duration.ofMinutes(20))
                .refreshAfterWrite(Duration.ofMinutes(5))
                .recordStats()
                .executor(customExecutor)
                .buildAsync((String rankingKey, Executor executor) ->
                    CompletableFuture.supplyAsync(() -> calculatePlayerRanking(rankingKey), executor)
                );
        
        logger.info("Advanced PlayerDataCache initialized with refresh-ahead patterns");
    }
    
    /**
     * Gets cached player statistics with automatic loading and refresh-ahead
     * 
     * @param uuid The player's UUID
     * @return CompletableFuture containing the PlayerStats
     */
    public static CompletableFuture<PlayerStats> getCachedStats(UUID uuid) {
        return STATS_CACHE.get(uuid).exceptionally(throwable -> {
            logger.warn("Failed to load stats for UUID: " + uuid, throwable);
            return new PlayerStats(uuid, "Unknown Player");
        });
    }
    
    /**
     * Gets cached player statistics with explicit player name for new players
     * 
     * @param uuid The player's UUID
     * @param playerName The player's name
     * @return CompletableFuture containing the PlayerStats
     */
    public static CompletableFuture<PlayerStats> getCachedStats(UUID uuid, String playerName) {
        return STATS_CACHE.get(uuid).exceptionally(throwable -> {
            logger.warn("Failed to load stats for UUID: " + uuid, throwable);
            return new PlayerStats(uuid, playerName);
        });
    }
    
    /**
     * Gets cached permission check result with automatic loading
     * 
     * @param player The player
     * @param permission The permission to check
     * @return The permission result
     */
    public static boolean getCachedPermission(Player player, String permission) {
        String key = player.getUniqueId() + ":" + permission;
        return PERMISSION_CACHE.get(key);
    }
    
    /**
     * Bulk loads and caches multiple permissions for a player
     * 
     * @param player The player
     * @param permissions Array of permissions to cache
     */
    public static void bulkCachePermissions(Player player, String... permissions) {
        for (String permission : permissions) {
            String key = player.getUniqueId() + ":" + permission;
            PERMISSION_CACHE.put(key, player.hasPermission(permission));
        }
    }
    
    /**
     * Gets cached display name with fallback loading
     * 
     * @param uuid The player's UUID
     * @return CompletableFuture containing the display name
     */
    public static CompletableFuture<String> getCachedDisplayName(UUID uuid) {
        String cached = DISPLAY_NAME_CACHE.getIfPresent(uuid);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }
        
        return CompletableFuture.supplyAsync(() -> {
            Player player = plugin.getServer().getPlayer(uuid);
            String displayName = player != null ? 
                player.displayName().toString() : 
                plugin.getServer().getOfflinePlayer(uuid).getName();
            
            if (displayName != null) {
                DISPLAY_NAME_CACHE.put(uuid, displayName);
            }
            return displayName != null ? displayName : "Unknown Player";
        }, customExecutor);
    }
    
    /**
     * Gets or creates a permission attachment for a player
     * 
     * @param player The player
     * @return The permission attachment
     */
    public static PermissionAttachment getCachedPermissionAttachment(Player player) {
        return PERMISSION_ATTACHMENT_CACHE.get(player.getUniqueId(), uuid -> 
            player.addAttachment(plugin)
        );
    }
    
    /**
     * Gets cached player ranking with automatic calculation
     * 
     * @param uuid The player's UUID
     * @param statType The statistic type for ranking
     * @return CompletableFuture containing the player's rank
     */
    public static CompletableFuture<Integer> getCachedPlayerRanking(UUID uuid, String statType) {
        String rankingKey = uuid + ":" + statType;
        return PLAYER_RANKING_CACHE.get(rankingKey);
    }
    
    /**
     * Invalidates all cached data for a specific player
     * 
     * @param uuid The player's UUID
     */
    public static void invalidatePlayer(UUID uuid) {
        STATS_CACHE.synchronous().invalidate(uuid);
        DISPLAY_NAME_CACHE.invalidate(uuid);
        PERMISSION_ATTACHMENT_CACHE.invalidate(uuid);
        
        // Invalidate all permission entries for this player
        PERMISSION_CACHE.asMap().keySet().removeIf(key -> key.startsWith(uuid.toString()));
        
        // Invalidate ranking entries for this player
        PLAYER_RANKING_CACHE.synchronous().asMap().keySet().removeIf(key -> key.startsWith(uuid.toString()));
    }
    
    /**
     * Forces refresh of player statistics
     * 
     * @param uuid The player's UUID
     * @return CompletableFuture that completes when refresh is done
     */
    public static CompletableFuture<PlayerStats> refreshPlayerStats(UUID uuid) {
        STATS_CACHE.synchronous().invalidate(uuid);
        return STATS_CACHE.get(uuid);
    }
    
    /**
     * Write-through cache update for player statistics
     * 
     * @param stats The updated player statistics
     */
    public static void updatePlayerStats(PlayerStats stats) {
        UUID uuid = stats.getPlayerId();
        
        // Update cache immediately (write-through)
        STATS_CACHE.put(uuid, CompletableFuture.completedFuture(stats));
        
        // Asynchronously persist to database
        CompletableFuture.runAsync(() -> {
            try {
                plugin.getStatisticsManager().savePlayerStats(uuid).get();
                logger.debug("Write-through cache update completed for player: " + stats.getPlayerName());
            } catch (Exception e) {
                logger.error("Failed to persist stats update for player: " + stats.getPlayerName(), e);
                // On failure, invalidate cache to ensure consistency
                STATS_CACHE.synchronous().invalidate(uuid);
            }
        }, customExecutor);
        
        // Invalidate related ranking caches
        PLAYER_RANKING_CACHE.synchronous().asMap().keySet().removeIf(key -> key.startsWith(uuid.toString()));
    }
    
    /**
     * Gets comprehensive cache statistics for monitoring
     * 
     * @return String containing detailed cache statistics
     */
    public static String getCacheStats() {
        return String.format(
            "Advanced Player Data Cache Stats:\n" +
            "Stats Cache - Size: %d, Hit Rate: %.2f%%, Load Count: %d\n" +
            "Permission Cache - Size: %d, Hit Rate: %.2f%%, Load Count: %d\n" +
            "Display Name Cache - Size: %d, Hit Rate: %.2f%%\n" +
            "Permission Attachment Cache - Size: %d, Hit Rate: %.2f%%\n" +
            "Player Ranking Cache - Size: %d, Hit Rate: %.2f%%, Load Count: %d\n" +
            "Total Memory Usage: ~%d KB",
            STATS_CACHE.synchronous().estimatedSize(), STATS_CACHE.synchronous().stats().hitRate() * 100, STATS_CACHE.synchronous().stats().loadCount(),
            PERMISSION_CACHE.estimatedSize(), PERMISSION_CACHE.stats().hitRate() * 100, PERMISSION_CACHE.stats().loadCount(),
            DISPLAY_NAME_CACHE.estimatedSize(), DISPLAY_NAME_CACHE.stats().hitRate() * 100,
            PERMISSION_ATTACHMENT_CACHE.estimatedSize(), PERMISSION_ATTACHMENT_CACHE.stats().hitRate() * 100,
            PLAYER_RANKING_CACHE.synchronous().estimatedSize(), PLAYER_RANKING_CACHE.synchronous().stats().hitRate() * 100, PLAYER_RANKING_CACHE.synchronous().stats().loadCount(),
            estimateMemoryUsage()
        );
    }
    
    /**
     * Clears all cached player data
     */
    public static void clearCache() {
        STATS_CACHE.synchronous().invalidateAll();
        PERMISSION_CACHE.invalidateAll();
        DISPLAY_NAME_CACHE.invalidateAll();
        PERMISSION_ATTACHMENT_CACHE.invalidateAll();
        PLAYER_RANKING_CACHE.synchronous().invalidateAll();
        logger.info("All player data caches cleared");
    }
    
    /**
     * Preloads essential data for a player when they join
     * 
     * @param player The player to preload data for
     */
    public static void preloadPlayerData(Player player) {
        UUID uuid = player.getUniqueId();
        
        // Asynchronously preload stats
        STATS_CACHE.get(uuid);
        
        // Preload display name
        DISPLAY_NAME_CACHE.put(uuid, player.displayName().toString());
        
        // Bulk preload common permissions
        String[] commonPermissions = {
            "lumasg.admin", "lumasg.moderator", "lumasg.vip",
            "lumasg.join", "lumasg.spectate", "lumasg.setup.game",
            "lumasg.arena.create", "lumasg.arena.edit", "lumasg.arena.delete"
        };
        
        bulkCachePermissions(player, commonPermissions);
        
        // Preload ranking for wins (most commonly viewed)
        getCachedPlayerRanking(uuid, "wins");
        
        logger.debug("Preloaded essential data for player: " + player.getName());
    }
    
    /**
     * Performs cache maintenance and cleanup
     */
    public static void performMaintenance() {
        // Caffeine handles most maintenance automatically, but we can trigger cleanup
        STATS_CACHE.synchronous().cleanUp();
        PERMISSION_CACHE.cleanUp();
        DISPLAY_NAME_CACHE.cleanUp();
        PERMISSION_ATTACHMENT_CACHE.cleanUp();
        PLAYER_RANKING_CACHE.synchronous().cleanUp();
        
        logger.debug("Cache maintenance completed");
    }
    
    // Private helper methods
    
    private static PlayerStats loadPlayerStatsFromDatabase(UUID uuid) {
        try {
            return plugin.getStatisticsManager().getPlayerStats(uuid, "Unknown Player").get();
        } catch (Exception e) {
            logger.error("Failed to load player stats from database for UUID: " + uuid, e);
            return new PlayerStats(uuid, "Unknown Player");
        }
    }
    
    private static Boolean loadPermissionFromBukkit(String permissionKey) {
        try {
            String[] parts = permissionKey.split(":", 2);
            UUID uuid = UUID.fromString(parts[0]);
            String permission = parts[1];
            
            Player player = plugin.getServer().getPlayer(uuid);
            return player != null && player.hasPermission(permission);
        } catch (Exception e) {
            logger.warn("Failed to load permission: " + permissionKey, e);
            return false;
        }
    }
    
    private static Integer calculatePlayerRanking(String rankingKey) {
        try {
            String[] parts = rankingKey.split(":", 2);
            UUID uuid = UUID.fromString(parts[0]);
            String statType = parts[1];
            
            // This would typically involve a database query to get ranking
            // For now, return a placeholder implementation
            return plugin.getStatisticsManager().getTotalPlayerCount().get() + 1;
        } catch (Exception e) {
            logger.error("Failed to calculate player ranking: " + rankingKey, e);
            return Integer.MAX_VALUE;
        }
    }
    
    private static long estimateMemoryUsage() {
        // Rough estimation of memory usage in KB
        long statsSize = STATS_CACHE.synchronous().estimatedSize() * 1024; // ~1KB per PlayerStats (convert to bytes)
        long permissionSize = PERMISSION_CACHE.estimatedSize() * 50; // ~50 bytes per permission
        long displayNameSize = DISPLAY_NAME_CACHE.estimatedSize() * 100; // ~100 bytes per name
        long attachmentSize = PERMISSION_ATTACHMENT_CACHE.estimatedSize() * 200; // ~200 bytes per attachment
        long rankingSize = PLAYER_RANKING_CACHE.synchronous().estimatedSize() * 50; // ~50 bytes per ranking
        
        return (statsSize + permissionSize + displayNameSize + attachmentSize + rankingSize) / 1024;
    }
} 