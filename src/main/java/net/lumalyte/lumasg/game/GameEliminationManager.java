package net.lumalyte.lumasg.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import net.lumalyte.lumasg.LumaSG;
import net.lumalyte.lumasg.util.core.DebugLogger;

/**
 * Manages player elimination and statistics tracking in a game.
 */
public class GameEliminationManager {
    private final @NotNull LumaSG plugin;
    private final @NotNull DebugLogger.ContextualLogger logger;
    private final @NotNull GamePlayerManager playerManager;
    private final @NotNull GameStatisticsManager statisticsManager;
    
    /** List to track elimination order for placement calculation */
    private final @NotNull List<UUID> eliminationOrder;
    
    /** Maps to track player statistics */
    private final @NotNull Map<UUID, Double> playerDamageDealt;
    private final @NotNull Map<UUID, Double> playerDamageTaken;
    private final @NotNull Map<UUID, Integer> playerChestsOpened;
    
    public GameEliminationManager(@NotNull LumaSG plugin, @NotNull String gameId,
                                @NotNull GamePlayerManager playerManager) {
        this.plugin = plugin;
        this.logger = plugin.getDebugLogger().forContext("GameEliminationManager-" + gameId);
        this.playerManager = playerManager;
        this.statisticsManager = new GameStatisticsManager(plugin, gameId);
        
        this.eliminationOrder = new CopyOnWriteArrayList<>();
        this.playerDamageDealt = new ConcurrentHashMap<>();
        this.playerDamageTaken = new ConcurrentHashMap<>();
        this.playerChestsOpened = new ConcurrentHashMap<>();
    }
    
    /**
     * Eliminates a player from the game.
     * 
     * @param player The player to eliminate
     * @return true if the player was eliminated, false if they were not in the game
     */
    public boolean eliminatePlayer(@NotNull Player player) {
        if (!playerManager.getPlayers().contains(player.getUniqueId())) {
            return false;
        }
        
        // Record elimination order for proper placement tracking
        eliminationOrder.add(player.getUniqueId());
        
        // Record death statistics if enabled
        if (plugin.getConfig().getBoolean("statistics.enabled", true)) {
            plugin.getStatisticsManager().recordDeath(player.getUniqueId());
        }
        
        // Move player to spectator
        playerManager.eliminatePlayer(player);
        
        return true;
    }
    
    /**
     * Records damage dealt by a player during the game.
     */
    public void recordDamageDealt(@NotNull UUID playerId, double damage) {
        if (playerManager.getPlayers().contains(playerId)) {
            playerDamageDealt.merge(playerId, damage, Double::sum);
            
            if (plugin.getConfig().getBoolean("statistics.enabled", true) && 
                plugin.getConfig().getBoolean("statistics.track-damage", true)) {
                plugin.getStatisticsManager().recordDamageDealt(playerId, damage);
            }
        }
    }
    
    /**
     * Records damage taken by a player during the game.
     */
    public void recordDamageTaken(@NotNull UUID playerId, double damage) {
        if (playerManager.getPlayers().contains(playerId) || playerManager.getSpectators().contains(playerId)) {
            playerDamageTaken.merge(playerId, damage, Double::sum);
            
            if (plugin.getConfig().getBoolean("statistics.enabled", true) && 
                plugin.getConfig().getBoolean("statistics.track-damage", true)) {
                plugin.getStatisticsManager().recordDamageTaken(playerId, damage);
            }
        }
    }
    
    /**
     * Records a chest opened by a player during the game.
     */
    public void recordChestOpened(@NotNull UUID playerId) {
        if (playerManager.getPlayers().contains(playerId)) {
            playerChestsOpened.merge(playerId, 1, Integer::sum);
            
            if (plugin.getConfig().getBoolean("statistics.enabled", true) && 
                plugin.getConfig().getBoolean("statistics.track-chests", true)) {
                plugin.getStatisticsManager().recordChestOpened(playerId);
            }
        }
    }
    
    /**
     * Gets the final rankings for all players.
     */
    public @NotNull List<UUID> getFinalRankings() {
        // Winners first (remaining players)
        List<UUID> finalRankings = new ArrayList<>(playerManager.getPlayers());
        
        // Then eliminated players in reverse order (last eliminated = highest placement)
        List<UUID> reversedElimination = new ArrayList<>(eliminationOrder);
        Collections.reverse(reversedElimination);
        finalRankings.addAll(reversedElimination);
        
        // Finally disconnected players last
        finalRankings.addAll(playerManager.getDisconnectedPlayers());
        
        return finalRankings;
    }
    
    /**
     * Records final game statistics for all players.
     */
    public void recordFinalStatistics(long gameTimeSeconds) {
        List<UUID> finalRankings = getFinalRankings();
        
        for (int i = 0; i < finalRankings.size(); i++) {
            UUID playerId = finalRankings.get(i);
            int placement = i + 1;
            
            PlayerGameStats stats = collectPlayerStats(playerId);
            recordIndividualPlayerStats(playerId, placement, stats, gameTimeSeconds);
        }
    }
    
    /**
     * Collects all statistics for a specific player.
     */
    private @NotNull PlayerGameStats collectPlayerStats(@NotNull UUID playerId) {
        int kills = playerManager.getPlayerKills(playerId);
        double damageDealt = getPlayerDamageDealt(playerId);
        double damageTaken = getPlayerDamageTaken(playerId);
        int chestsOpened = getPlayerChestsOpened(playerId);
        
        return new PlayerGameStats(kills, damageDealt, damageTaken, chestsOpened);
    }
    
    /**
     * Records statistics for an individual player.
     */
    private void recordIndividualPlayerStats(@NotNull UUID playerId, int placement,
                                           @NotNull PlayerGameStats stats, long gameTimeSeconds) {
        if (plugin.getConfig().getBoolean("statistics.enabled", true)) {
            plugin.getStatisticsManager().recordGameResult(playerId, placement,
                stats.getKills(), stats.getDamageDealt(), stats.getDamageTaken(),
                stats.getChestsOpened(), gameTimeSeconds);
        }
    }
    
    /**
     * Handles a player's death and elimination from the game.
     */
    public void handlePlayerDeath(@NotNull Player victim, @Nullable Player killer) {
        // Record death statistics if enabled
        if (plugin.getConfig().getBoolean("statistics.enabled", true)) {
            plugin.getStatisticsManager().recordDeath(victim.getUniqueId());
        }
        
        // Record kill for killer if applicable
        if (killer != null && playerManager.getPlayers().contains(killer.getUniqueId())) {
            playerManager.incrementKills(killer.getUniqueId());
        }
        
        // Send death message
        Game game = plugin.getGameManager().getGameByPlayer(victim);
        if (game != null) {
            game.getDeathMessageManager().handlePlayerKill(victim, killer);
        }
        
        // Eliminate the player (this will automatically check if game should end)
        eliminatePlayer(victim);
    }
    
    // Getters
    
    public double getPlayerDamageDealt(@NotNull UUID playerId) {
        return playerDamageDealt.getOrDefault(playerId, 0.0);
    }
    
    public double getPlayerDamageTaken(@NotNull UUID playerId) {
        return playerDamageTaken.getOrDefault(playerId, 0.0);
    }
    
    public int getPlayerChestsOpened(@NotNull UUID playerId) {
        return playerChestsOpened.getOrDefault(playerId, 0);
    }
    
    public @NotNull List<UUID> getEliminationOrder() {
        return Collections.unmodifiableList(eliminationOrder);
    }
    
    public @NotNull GameStatisticsManager getStatisticsManager() {
        return statisticsManager;
    }

	public @NotNull DebugLogger.ContextualLogger getLogger() {
		return logger;
	}
}
