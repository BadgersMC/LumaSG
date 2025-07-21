package net.lumalyte.lumasg.game;

import net.lumalyte.lumasg.LumaSG;
import net.lumalyte.lumasg.statistics.StatisticsManager;
import net.lumalyte.lumasg.util.core.DebugLogger;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages game statistics collection and recording.
 * 
 * <p>This class handles tracking and recording various game statistics including
 * player performance metrics, game duration, and final rankings. It ensures
 * thread-safe access to statistics data.</p>
 * 
 * @author LumaLyte
 * @version 1.0
 * @since 1.0
 */
public class GameStatisticsManager {
    /** The plugin instance for configuration and server access */
    private final @NotNull LumaSG plugin;
    
    /** The debug logger instance for this manager */
    private final @NotNull DebugLogger.ContextualLogger logger;
    
    /** The statistics manager for database operations */
    private final @NotNull StatisticsManager statisticsManager;
    
    /** Game start time for duration calculation */
    private final @NotNull Instant gameStartTime;
    
    /** Map of player UUIDs to their damage dealt during the game */
    private final @NotNull Map<UUID, Double> playerDamageDealt = new ConcurrentHashMap<>();
    
    /** Map of player UUIDs to their damage taken during the game */
    private final @NotNull Map<UUID, Double> playerDamageTaken = new ConcurrentHashMap<>();
    
    /** Map of player UUIDs to their chests opened during the game */
    private final @NotNull Map<UUID, Integer> playerChestsOpened = new ConcurrentHashMap<>();
    
    /** Map of player UUIDs to their kill counts during the game */
    private final @NotNull Map<UUID, Integer> playerKills = new ConcurrentHashMap<>();
    
    /** Ordered list of eliminated players (first eliminated = last place) */
    private final @NotNull List<UUID> eliminationOrder = new ArrayList<>();
    
    /**
     * Constructs a new GameStatisticsManager instance.
     * 
     * @param plugin The plugin instance
     * @param gameId The ID of the game this manager is associated with
     */
    public GameStatisticsManager(@NotNull LumaSG plugin, @NotNull String gameId) {
        this.plugin = plugin;
        this.logger = plugin.getDebugLogger().forContext("GameStatisticsManager-" + gameId);
        this.statisticsManager = plugin.getStatisticsManager();
        this.gameStartTime = Instant.now();
    }
    
    /**
     * Records damage dealt by a player.
     * 
     * @param playerId The UUID of the player
     * @param damage The amount of damage dealt
     */
    public void recordDamageDealt(@NotNull UUID playerId, double damage) {
        playerDamageDealt.merge(playerId, damage, Double::sum);
    }
    
    /**
     * Records damage taken by a player.
     * 
     * @param playerId The UUID of the player
     * @param damage The amount of damage taken
     */
    public void recordDamageTaken(@NotNull UUID playerId, double damage) {
        playerDamageTaken.merge(playerId, damage, Double::sum);
    }
    
    /**
     * Records a chest being opened by a player.
     * 
     * @param playerId The UUID of the player
     */
    public void recordChestOpened(@NotNull UUID playerId) {
        playerChestsOpened.merge(playerId, 1, Integer::sum);
    }
    
    /**
     * Records a kill by a player.
     * 
     * @param playerId The UUID of the player
     */
    public void recordKill(@NotNull UUID playerId) {
        playerKills.merge(playerId, 1, Integer::sum);
    }
    
    /**
     * Records a player elimination.
     * 
     * @param playerId The UUID of the eliminated player
     */
    public void recordElimination(@NotNull UUID playerId) {
        if (!eliminationOrder.contains(playerId)) {
            eliminationOrder.add(playerId);
        }
    }
    
    /**
     * Gets the number of kills for a player.
     * 
     * @param playerId The UUID of the player
     * @return The number of kills
     */
    public int getPlayerKills(@NotNull UUID playerId) {
        return playerKills.getOrDefault(playerId, 0);
    }
    
    /**
     * Gets the amount of damage dealt by a player.
     * 
     * @param playerId The UUID of the player
     * @return The amount of damage dealt
     */
    public double getPlayerDamageDealt(@NotNull UUID playerId) {
        return playerDamageDealt.getOrDefault(playerId, 0.0);
    }
    
    /**
     * Gets the amount of damage taken by a player.
     * 
     * @param playerId The UUID of the player
     * @return The amount of damage taken
     */
    public double getPlayerDamageTaken(@NotNull UUID playerId) {
        return playerDamageTaken.getOrDefault(playerId, 0.0);
    }
    
    /**
     * Gets the number of chests opened by a player.
     * 
     * @param playerId The UUID of the player
     * @return The number of chests opened
     */
    public int getPlayerChestsOpened(@NotNull UUID playerId) {
        return playerChestsOpened.getOrDefault(playerId, 0);
    }
    
    /**
     * Records all game statistics to the database.
     * 
     * @param participants The set of all game participants
     */
    public void recordGameStatistics(@NotNull Set<UUID> participants) {
        if (!plugin.getConfig().getBoolean("statistics.enabled", true)) {
            logger.debug("Statistics are disabled - skipping recording");
            return;
        }
        
        long gameTimeSeconds = calculateGameDuration();
        List<UUID> finalRankings = determineFinalRankings(participants);
        
        logger.info("Recording statistics for " + participants.size() + " players");
        recordPlayerStatistics(finalRankings, gameTimeSeconds);
    }
    
    /**
     * Calculates the duration of the game in seconds.
     * 
     * @return The game duration in seconds
     */
    private long calculateGameDuration() {
        return Duration.between(gameStartTime, Instant.now()).getSeconds();
    }
    
    /**
     * Determines the final rankings of all players.
     * 
     * @param participants The set of all game participants
     * @return The ordered list of player UUIDs from winner to last place
     */
    private @NotNull List<UUID> determineFinalRankings(@NotNull Set<UUID> participants) {
        List<UUID> rankings = new ArrayList<>(participants);
        
        // Remove eliminated players from the list
        rankings.removeAll(eliminationOrder);
        
        // Add eliminated players in reverse order (last eliminated = higher rank)
        for (int i = eliminationOrder.size() - 1; i >= 0; i--) {
            rankings.add(eliminationOrder.get(i));
        }
        
        return rankings;
    }
    
    /**
     * Records statistics for all players.
     * 
     * @param finalRankings The ordered list of player UUIDs from winner to last place
     * @param gameTimeSeconds The duration of the game in seconds
     */
    private void recordPlayerStatistics(@NotNull List<UUID> finalRankings, long gameTimeSeconds) {
        for (int i = 0; i < finalRankings.size(); i++) {
            UUID playerId = finalRankings.get(i);
            int placement = i + 1;
            PlayerGameStats stats = collectPlayerStats(playerId);
            recordIndividualPlayerStats(playerId, placement, stats, gameTimeSeconds);
        }
    }
    
    /**
     * Collects all statistics for a single player.
     * 
     * @param playerId The UUID of the player
     * @return The collected statistics
     */
    private @NotNull PlayerGameStats collectPlayerStats(@NotNull UUID playerId) {
        return new PlayerGameStats(
            getPlayerKills(playerId),
            getPlayerDamageDealt(playerId),
            getPlayerDamageTaken(playerId),
            getPlayerChestsOpened(playerId)
        );
    }
    
    /**
     * Records statistics for a single player.
     * 
     * @param playerId The UUID of the player
     * @param placement The player's final placement
     * @param stats The player's game statistics
     * @param gameTimeSeconds The duration of the game in seconds
     */
    private void recordIndividualPlayerStats(@NotNull UUID playerId, int placement, 
                                           @NotNull PlayerGameStats stats, long gameTimeSeconds) {
        try {
            statisticsManager.recordGameResult(
                playerId,
                placement,
                stats.kills(),
                stats.damageDealt(),
                stats.damageTaken(),
                stats.chestsOpened(),
                gameTimeSeconds
            );
            
            logger.debug("Recorded statistics for player " + playerId);
        } catch (Exception e) {
            logger.warn("Failed to record statistics for player " + playerId, e);
        }
    }
    
    /**
     * Record containing a player's game statistics.
     */
    private record PlayerGameStats(int kills, double damageDealt, double damageTaken, int chestsOpened) {}
    
    /**
     * Cleans up all resources used by this manager.
     */
    public void cleanup() {
        playerDamageDealt.clear();
        playerDamageTaken.clear();
        playerChestsOpened.clear();
        playerKills.clear();
        eliminationOrder.clear();
    }
} 
