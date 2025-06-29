package net.lumalyte.statistics;

import net.lumalyte.LumaSG;
import net.lumalyte.util.DebugLogger;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages player statistics for the LumaSG plugin.
 * 
 * <p>This class acts as the main interface between the game system and the
 * statistics database. It handles loading, caching, and saving player statistics,
 * as well as providing methods for updating statistics during gameplay.</p>
 * 
 * @author LumaLyte
 * @version 1.0
 * @since 1.0
 */
public class StatisticsManager {
    
    private final @NotNull LumaSG plugin;
    private final @NotNull StatisticsDatabase database;
    
    /** The debug logger instance for this statistics manager */
    private final @NotNull DebugLogger.ContextualLogger logger;
    
    /** Cache of loaded player statistics for quick access during games */
    private final @NotNull Map<UUID, PlayerStats> statisticsCache;
    
    /** Set of player UUIDs whose statistics have been modified and need saving */
    private final @NotNull Map<UUID, Long> pendingSaves;
    
    /**
     * Creates a new StatisticsManager instance.
     * 
     * @param plugin The plugin instance
     */
    public StatisticsManager(@NotNull LumaSG plugin) {
        this.plugin = plugin;
        this.database = new StatisticsDatabase(plugin);
        this.logger = plugin.getDebugLogger().forContext("StatisticsManager");
        this.statisticsCache = new ConcurrentHashMap<>();
        this.pendingSaves = new ConcurrentHashMap<>();
    }
    
    /**
     * Initializes the statistics manager and database.
     * 
     * @return A CompletableFuture that completes when initialization is done
     */
    public @NotNull CompletableFuture<Void> initialize() {
        return database.initialize().thenRun(() -> {
            logger.info("Statistics manager initialized successfully");
            
            // Start the periodic save task
            startPeriodicSaveTask();
        });
    }
    
    /**
     * Shuts down the statistics manager and saves all pending data.
     * 
     * @return A CompletableFuture that completes when shutdown is done
     */
    public @NotNull CompletableFuture<Void> shutdown() {
        return saveAllPendingStats().thenCompose(v -> {
            database.shutdown();
            return CompletableFuture.completedFuture(null);
        });
    }
    
    /**
     * Gets or loads player statistics.
     * 
     * @param playerId The player's unique identifier
     * @param playerName The player's name (used for new players)
     * @return A CompletableFuture containing the player statistics
     */
    public @NotNull CompletableFuture<PlayerStats> getPlayerStats(@NotNull UUID playerId, @NotNull String playerName) {
        // Check cache first
        PlayerStats cached = statisticsCache.get(playerId);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }
        
        // Load from database
        return database.loadPlayerStats(playerId).thenApply(stats -> {
            if (stats == null) {
                // Create new player statistics
                stats = new PlayerStats(playerId, playerName);
                logger.debug("Created new statistics for player: " + playerName);
            } else {
                // Update player name in case it changed
                stats.setPlayerName(playerName);
            }
            
            // Cache the statistics
            statisticsCache.put(playerId, stats);
            return stats;
        });
    }
    
    /**
     * Gets player statistics from cache only (synchronous).
     * 
     * @param playerId The player's unique identifier
     * @return The cached player statistics, or null if not cached
     */
    public @Nullable PlayerStats getCachedPlayerStats(@NotNull UUID playerId) {
        return statisticsCache.get(playerId);
    }
    
    /**
     * Records a game result for a player.
     * 
     * @param playerId The player's unique identifier
     * @param placement The player's final placement (1 = winner, 2 = second, etc.)
     * @param kills The number of kills the player achieved
     * @param damageDealt The total damage dealt by the player
     * @param damageTaken The total damage taken by the player
     * @param chestsOpened The number of chests opened by the player
     * @param gameTimeSeconds The duration the player was in the game (in seconds)
     */
    public void recordGameResult(@NotNull UUID playerId, int placement, int kills, 
                                double damageDealt, double damageTaken, int chestsOpened, 
                                long gameTimeSeconds) {
        PlayerStats stats = statisticsCache.get(playerId);
        if (stats == null) {
            logger.warn("Attempted to record game result for uncached player: " + playerId);
            return;
        }
        
        // Update game statistics
        stats.incrementGamesPlayed();
        stats.addTimePlayed(gameTimeSeconds);
        stats.setLastPlayed(LocalDateTime.now());
        
        // Update placement
        stats.updatePlacement(placement);
        
        // Update win/loss record
        if (placement == 1) {
            stats.incrementWins();
        } else {
            stats.incrementLosses();
        }
        
        // Update kill statistics
        for (int i = 0; i < kills; i++) {
            stats.incrementKills();
        }
        
        // Update damage statistics
        stats.addDamageDealt(damageDealt);
        stats.addDamageTaken(damageTaken);
        
        // Update chests opened
        for (int i = 0; i < chestsOpened; i++) {
            stats.incrementChestsOpened();
        }
        
        // Mark for saving
        markForSaving(playerId);
        
        logger.debug("Recorded game result for " + stats.getPlayerName() + 
            ": placement=" + placement + ", kills=" + kills);
    }
    
    /**
     * Records a player death.
     * 
     * @param playerId The player's unique identifier
     */
    public void recordDeath(@NotNull UUID playerId) {
        PlayerStats stats = statisticsCache.get(playerId);
        if (stats != null) {
            stats.incrementDeaths();
            markForSaving(playerId);
        }
    }
    
    /**
     * Records a kill for a player.
     * 
     * @param playerId The player's unique identifier
     */
    public void recordKill(@NotNull UUID playerId) {
        PlayerStats stats = statisticsCache.get(playerId);
        if (stats != null) {
            stats.incrementKills();
            markForSaving(playerId);
        }
    }
    
    /**
     * Records damage dealt by a player.
     * 
     * @param playerId The player's unique identifier
     * @param damage The amount of damage dealt
     */
    public void recordDamageDealt(@NotNull UUID playerId, double damage) {
        PlayerStats stats = statisticsCache.get(playerId);
        if (stats != null) {
            stats.addDamageDealt(damage);
            markForSaving(playerId);
        }
    }
    
    /**
     * Records damage taken by a player.
     * 
     * @param playerId The player's unique identifier
     * @param damage The amount of damage taken
     */
    public void recordDamageTaken(@NotNull UUID playerId, double damage) {
        PlayerStats stats = statisticsCache.get(playerId);
        if (stats != null) {
            stats.addDamageTaken(damage);
            markForSaving(playerId);
        }
    }
    
    /**
     * Records a chest opened by a player.
     * 
     * @param playerId The player's unique identifier
     */
    public void recordChestOpened(@NotNull UUID playerId) {
        PlayerStats stats = statisticsCache.get(playerId);
        if (stats != null) {
            stats.incrementChestsOpened();
            markForSaving(playerId);
        }
    }
    
    /**
     * Gets a leaderboard for a specific statistic type.
     * 
     * @param statType The type of statistic to get the leaderboard for
     * @param limit The maximum number of entries to return
     * @return A CompletableFuture containing the leaderboard
     */
    public @NotNull CompletableFuture<List<PlayerStats>> getLeaderboard(@NotNull StatType statType, int limit) {
        return database.getLeaderboard(statType, limit);
    }
    
    /**
     * Gets the total number of players with statistics.
     * 
     * @return A CompletableFuture containing the total player count
     */
    public @NotNull CompletableFuture<Integer> getTotalPlayerCount() {
        return database.getTotalPlayerCount();
    }
    
    /**
     * Saves a specific player's statistics immediately.
     * 
     * @param playerId The player's unique identifier
     * @return A CompletableFuture that completes when the save is done
     */
    public @NotNull CompletableFuture<Void> savePlayerStats(@NotNull UUID playerId) {
        PlayerStats stats = statisticsCache.get(playerId);
        if (stats == null) {
            return CompletableFuture.completedFuture(null);
        }
        
        return database.savePlayerStats(stats).thenRun(() -> {
            pendingSaves.remove(playerId);
        });
    }
    
    /**
     * Saves all pending player statistics.
     * 
     * @return A CompletableFuture that completes when all saves are done
     */
    public @NotNull CompletableFuture<Void> saveAllPendingStats() {
        if (pendingSaves.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        
        CompletableFuture<?>[] futures = pendingSaves.keySet().stream()
            .map(this::savePlayerStats)
            .toArray(CompletableFuture[]::new);
        
        return CompletableFuture.allOf(futures);
    }
    
    /**
     * Removes a player from the cache.
     * 
     * @param playerId The player's unique identifier
     */
    public void uncachePlayer(@NotNull UUID playerId) {
        // Save before removing from cache if there are pending changes
        if (pendingSaves.containsKey(playerId)) {
            savePlayerStats(playerId).thenRun(() -> {
                statisticsCache.remove(playerId);
            });
        } else {
            statisticsCache.remove(playerId);
        }
    }
    
    /**
     * Preloads statistics for a player (useful when they join the server).
     *
     * @param player The player to preload statistics for
     */
    public void preloadPlayerStats(@NotNull Player player) {
        getPlayerStats(player.getUniqueId(), player.getName()).thenAccept(stats -> {
            // Statistics are now cached
        }).exceptionally(throwable -> {
            logger.warn("Failed to preload statistics for " + player.getName(), throwable);
            return null;
        });
    }
    
    /**
     * Marks a player's statistics for saving.
     * 
     * @param playerId The player's unique identifier
     */
    private void markForSaving(@NotNull UUID playerId) {
        pendingSaves.put(playerId, System.currentTimeMillis());
    }
    
    /**
     * Starts the periodic task to save pending statistics.
     */
    private void startPeriodicSaveTask() {
        int saveInterval = plugin.getConfig().getInt("statistics.save-interval-seconds", 300); // Default 5 minutes
        
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            if (!pendingSaves.isEmpty()) {
                saveAllPendingStats().exceptionally(throwable -> {
                    logger.warn("Failed to save pending statistics", throwable);
                    return null;
                });
            }
        }, 20L * saveInterval, 20L * saveInterval);
    }
    
    /**
     * Gets the statistics database instance.
     * 
     * @return The statistics database
     */
    public @NotNull StatisticsDatabase getDatabase() {
        return database;
    }
} 