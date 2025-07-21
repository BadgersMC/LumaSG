package net.lumalyte.util;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import net.lumalyte.LumaSG;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * High-performance scoreboard caching system
 * Reduces scoreboard computation overhead and improves update performance
 */
public class ScoreboardCache {
    
    // Cache for scoreboard line content to avoid string concatenation overhead
    private static final Cache<String, String> SCOREBOARD_LINE_CACHE = Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(Duration.ofSeconds(30))
            .recordStats()
            .build();
    
    // Cache for computed placeholder values
    private static final Cache<String, String> PLACEHOLDER_CACHE = Caffeine.newBuilder()
            .maximumSize(500)
            .expireAfterWrite(Duration.ofSeconds(10))
            .recordStats()
            .build();
    
    // Cache for formatted time strings
    private static final Cache<Integer, String> TIME_FORMAT_CACHE = Caffeine.newBuilder()
            .maximumSize(3600) // Cache up to 1 hour of time formats
            .expireAfterWrite(Duration.ofMinutes(5))
            .recordStats()
            .build();
    
    // Cache for player-specific scoreboard data
    private static final Map<UUID, PlayerScoreboardData> PLAYER_SCOREBOARD_DATA = new ConcurrentHashMap<>();
    
    private static LumaSG plugin;
    private static DebugLogger.ContextualLogger logger;
    
    /**
     * Player-specific scoreboard data container
     */
    private static class PlayerScoreboardData {
        private final Map<String, String> cachedLines = new ConcurrentHashMap<>();
        private long lastUpdate = 0;
        private boolean needsRefresh = true;
        
        public void setCachedLine(String key, String value) {
            cachedLines.put(key, value);
        }
        
        public String getCachedLine(String key) {
            return cachedLines.get(key);
        }
        
        public void markForRefresh() {
            needsRefresh = true;
            lastUpdate = System.currentTimeMillis();
        }
        
        public boolean needsRefresh(long updateInterval) {
            return needsRefresh || (System.currentTimeMillis() - lastUpdate) > updateInterval;
        }
        
        public void refreshCompleted() {
            needsRefresh = false;
            lastUpdate = System.currentTimeMillis();
        }
    }
    
    /**
     * Initializes the scoreboard cache
     * 
     * @param pluginInstance The plugin instance
     */
    public static void initialize(LumaSG pluginInstance) {
        plugin = pluginInstance;
        logger = plugin.getDebugLogger().forContext("ScoreboardCache");
        
        logger.info("Scoreboard Cache initialized");
    }
    
    /**
     * Gets cached scoreboard line content
     * 
     * @param lineKey The unique key for the line
     * @param generator The function to generate the line if not cached
     * @return The cached or generated line content
     */
    public static String getCachedScoreboardLine(String lineKey, java.util.function.Supplier<String> generator) {
        return SCOREBOARD_LINE_CACHE.get(lineKey, key -> generator.get());
    }
    
    /**
     * Gets cached placeholder value
     * 
     * @param placeholderKey The placeholder key
     * @param generator The function to generate the value if not cached
     * @return The cached or generated placeholder value
     */
    public static String getCachedPlaceholder(String placeholderKey, java.util.function.Supplier<String> generator) {
        return PLACEHOLDER_CACHE.get(placeholderKey, key -> generator.get());
    }
    
    /**
     * Gets cached formatted time string
     * 
     * @param timeInSeconds The time in seconds
     * @return The formatted time string
     */
    public static String getCachedTimeFormat(int timeInSeconds) {
        return TIME_FORMAT_CACHE.get(timeInSeconds, time -> {
            int minutes = time / 60;
            int seconds = time % 60;
            return String.format("%02d:%02d", minutes, seconds);
        });
    }
    
    /**
     * Gets or creates player scoreboard data
     * 
     * @param playerId The player's UUID
     * @return The player's scoreboard data
     */
    public static PlayerScoreboardData getPlayerScoreboardData(UUID playerId) {
        return PLAYER_SCOREBOARD_DATA.computeIfAbsent(playerId, id -> new PlayerScoreboardData());
    }
    
    /**
     * Checks if a player's scoreboard needs updating
     * 
     * @param playerId The player's UUID
     * @param updateInterval The update interval in milliseconds
     * @return True if the scoreboard needs updating
     */
    public static boolean needsScoreboardUpdate(UUID playerId, long updateInterval) {
        PlayerScoreboardData data = getPlayerScoreboardData(playerId);
        return data.needsRefresh(updateInterval);
    }
    
    /**
     * Marks a player's scoreboard as needing refresh
     * 
     * @param playerId The player's UUID
     */
    public static void markScoreboardForRefresh(UUID playerId) {
        PlayerScoreboardData data = getPlayerScoreboardData(playerId);
        data.markForRefresh();
    }
    
    /**
     * Marks a player's scoreboard refresh as completed
     * 
     * @param playerId The player's UUID
     */
    public static void markScoreboardRefreshCompleted(UUID playerId) {
        PlayerScoreboardData data = getPlayerScoreboardData(playerId);
        data.refreshCompleted();
    }
    
    /**
     * Caches a scoreboard line for a specific player
     * 
     * @param playerId The player's UUID
     * @param lineKey The line key
     * @param lineContent The line content
     */
    public static void cachePlayerScoreboardLine(UUID playerId, String lineKey, String lineContent) {
        PlayerScoreboardData data = getPlayerScoreboardData(playerId);
        data.setCachedLine(lineKey, lineContent);
    }
    
    /**
     * Gets a cached scoreboard line for a specific player
     * 
     * @param playerId The player's UUID
     * @param lineKey The line key
     * @return The cached line content, or null if not cached
     */
    public static String getCachedPlayerScoreboardLine(UUID playerId, String lineKey) {
        PlayerScoreboardData data = PLAYER_SCOREBOARD_DATA.get(playerId);
        return data != null ? data.getCachedLine(lineKey) : null;
    }
    
    /**
     * Invalidates all cached data for a player
     * 
     * @param playerId The player's UUID
     */
    public static void invalidatePlayer(UUID playerId) {
        PLAYER_SCOREBOARD_DATA.remove(playerId);
        
        // Remove player-specific entries from global caches
        String playerPrefix = playerId.toString();
        SCOREBOARD_LINE_CACHE.asMap().keySet().removeIf(key -> key.startsWith(playerPrefix));
        PLACEHOLDER_CACHE.asMap().keySet().removeIf(key -> key.startsWith(playerPrefix));
    }
    
    /**
     * Invalidates cached placeholders
     * 
     * @param placeholderPrefix The prefix of placeholders to invalidate
     */
    public static void invalidatePlaceholders(String placeholderPrefix) {
        PLACEHOLDER_CACHE.asMap().keySet().removeIf(key -> key.startsWith(placeholderPrefix));
    }
    
    /**
     * Gets comprehensive cache statistics
     * 
     * @return String containing cache statistics
     */
    public static String getCacheStats() {
        return String.format(
            "Scoreboard Cache Stats:\n" +
            "Scoreboard Lines - Size: %d, Hit Rate: %.2f%%\n" +
            "Placeholders - Size: %d, Hit Rate: %.2f%%\n" +
            "Time Formats - Size: %d, Hit Rate: %.2f%%\n" +
            "Player Data Entries: %d",
            SCOREBOARD_LINE_CACHE.estimatedSize(), SCOREBOARD_LINE_CACHE.stats().hitRate() * 100,
            PLACEHOLDER_CACHE.estimatedSize(), PLACEHOLDER_CACHE.stats().hitRate() * 100,
            TIME_FORMAT_CACHE.estimatedSize(), TIME_FORMAT_CACHE.stats().hitRate() * 100,
            PLAYER_SCOREBOARD_DATA.size()
        );
    }
    
    /**
     * Performs cache maintenance
     */
    public static void performMaintenance() {
        SCOREBOARD_LINE_CACHE.cleanUp();
        PLACEHOLDER_CACHE.cleanUp();
        TIME_FORMAT_CACHE.cleanUp();
        
        // Clean up inactive player data
        long cutoffTime = System.currentTimeMillis() - Duration.ofMinutes(15).toMillis();
        PLAYER_SCOREBOARD_DATA.entrySet().removeIf(entry -> entry.getValue().lastUpdate < cutoffTime);
        
        logger.debug("Scoreboard cache maintenance completed");
    }
    
    /**
     * Clears all caches
     */
    public static void clearAllCaches() {
        SCOREBOARD_LINE_CACHE.invalidateAll();
        PLACEHOLDER_CACHE.invalidateAll();
        TIME_FORMAT_CACHE.invalidateAll();
        PLAYER_SCOREBOARD_DATA.clear();
        
        logger.info("All scoreboard caches cleared");
    }
    
    /**
     * Creates optimized scoreboard line with caching
     * 
     * @param gameId The game ID
     * @param lineType The type of line (players, time, arena, etc.)
     * @param value The value to display
     * @return The formatted scoreboard line
     */
    public static String createOptimizedScoreboardLine(String gameId, String lineType, String value) {
        String cacheKey = gameId + ":" + lineType + ":" + value;
        
        return getCachedScoreboardLine(cacheKey, () -> {
            return switch (lineType) {
                case "players" -> "§f§lPlayers: §e" + value;
                case "time" -> "§f§lTime: §c" + value;
                case "arena" -> "§f§lArena: §b" + value;
                case "alive" -> "§f§lAlive: §a" + value;
                default -> "§f" + value;
            };
        });
    }
    
    /**
     * Bulk updates scoreboard for multiple players efficiently
     * 
     * @param players The players to update
     * @param lineUpdates The line updates to apply
     */
    public static void bulkUpdateScoreboards(Iterable<Player> players, Map<String, String> lineUpdates) {
        for (Player player : players) {
            UUID playerId = player.getUniqueId();
            PlayerScoreboardData data = getPlayerScoreboardData(playerId);
            
            // Check if any lines actually changed
            boolean hasChanges = false;
            for (Map.Entry<String, String> update : lineUpdates.entrySet()) {
                String cachedLine = data.getCachedLine(update.getKey());
                if (!update.getValue().equals(cachedLine)) {
                    hasChanges = true;
                    break;
                }
            }
            
            // Only update if there are actual changes
            if (hasChanges) {
                for (Map.Entry<String, String> update : lineUpdates.entrySet()) {
                    data.setCachedLine(update.getKey(), update.getValue());
                }
                data.markForRefresh();
            }
        }
    }
} 