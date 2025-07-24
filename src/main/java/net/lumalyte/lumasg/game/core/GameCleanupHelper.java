package net.lumalyte.lumasg.game.core;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.lumalyte.lumasg.LumaSG;
import net.lumalyte.lumasg.game.mechanics.GameEliminationManager;
import net.lumalyte.lumasg.game.player.GamePlayerManager;
import net.lumalyte.lumasg.game.ui.CelebrationManager;
import net.lumalyte.lumasg.game.ui.GameScoreboardManager;
import net.lumalyte.lumasg.game.mechanics.GameTimerManager;
import net.lumalyte.lumasg.game.world.GameWorldManager;
import net.lumalyte.lumasg.util.core.DebugLogger;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Helper class for managing game cleanup and shutdown processes.
 * 
 * <p>This class centralizes all cleanup and shutdown logic that was previously
 * scattered throughout the Game class. It handles player preparation, statistics
 * recording, celebration management, and final cleanup tasks.</p>
 * 
 * <p>This is a package-private helper class - it's an implementation detail
 * of the Game class and should not be used directly by external code.</p>
 */
class GameCleanupHelper {
    private final @NotNull LumaSG plugin;
    private final @NotNull DebugLogger.ContextualLogger logger;
    private final @NotNull String gameId;
    
    // Manager references for cleanup operations
    private final @NotNull GamePlayerManager playerManager;
    private final @NotNull GameTimerManager timerManager;
    private final @NotNull GameScoreboardManager scoreboardManager;
    private final @NotNull GameWorldManager worldManager;
    private final @NotNull GameEliminationManager eliminationManager;
    private final @NotNull CelebrationManager celebrationManager;
    private final @NotNull GameEventHelper eventHelper;
    
    /**
     * Creates a new cleanup helper for the specified game.
     * 
     * @param plugin The plugin instance
     * @param gameId The game ID for logging context
     * @param playerManager The player manager
     * @param timerManager The timer manager
     * @param scoreboardManager The scoreboard manager
     * @param worldManager The world manager
     * @param eliminationManager The elimination manager
     * @param celebrationManager The celebration manager
     * @param eventHelper The event helper
     */
    GameCleanupHelper(@NotNull LumaSG plugin, @NotNull String gameId,
                     @NotNull GamePlayerManager playerManager,
                     @NotNull GameTimerManager timerManager,
                     @NotNull GameScoreboardManager scoreboardManager,
                     @NotNull GameWorldManager worldManager,
                     @NotNull GameEliminationManager eliminationManager,
                     @NotNull CelebrationManager celebrationManager,
                     @NotNull GameEventHelper eventHelper) {
        this.plugin = plugin;
        this.gameId = gameId;
        this.logger = plugin.getDebugLogger().forContext("GameCleanup-" + gameId);
        this.playerManager = playerManager;
        this.timerManager = timerManager;
        this.scoreboardManager = scoreboardManager;
        this.worldManager = worldManager;
        this.eliminationManager = eliminationManager;
        this.celebrationManager = celebrationManager;
        this.eventHelper = eventHelper;
    }
    
    /**
     * Cleans up timer and scoreboard managers.
     */
    void cleanupManagers() {
        timerManager.cleanup();
        scoreboardManager.cleanup();
        logger.debug("Cleaned up timer and scoreboard managers");
    }
    
    /**
     * Prepares all players and spectators for end game by setting them to adventure mode
     * and clearing their inventories.
     */
    void preparePlayersForEndGame() {
        preparePlayersForEndGameCleanup(playerManager.getPlayers());
        preparePlayersForEndGameCleanup(playerManager.getSpectators());
        logger.debug("Prepared all players for end game");
    }
    
    /**
     * Helper method to prepare a collection of player UUIDs for end game cleanup.
     * 
     * @param playerIds The collection of player UUIDs to prepare
     */
    private void preparePlayersForEndGameCleanup(@NotNull Set<UUID> playerIds) {
        for (UUID playerId : playerIds) {
            Player player = playerManager.getCachedPlayer(playerId);
            if (player != null && player.isOnline()) {
                // Set to adventure mode to prevent interaction
                player.setGameMode(GameMode.ADVENTURE);
                
                // Clear inventory immediately to prevent items from being kept
                player.getInventory().clear();
                player.getInventory().setArmorContents(null);
                
                logger.debug("Prepared player for end game: " + player.getName());
            }
        }
    }
    
    /**
     * Records game statistics if statistics are enabled in the configuration.
     * 
     * @param gameStartTime The time when the game started, or null if not started
     */
    void recordStatisticsIfEnabled(@NotNull Instant gameStartTime) {
        if (plugin.getConfig().getBoolean("statistics.enabled", true)) {
            long gameTimeSeconds = Duration.between(gameStartTime, Instant.now()).getSeconds();
            eliminationManager.recordFinalStatistics(gameTimeSeconds);
            logger.debug("Recorded game statistics - duration: " + gameTimeSeconds + " seconds");
        } else {
            logger.debug("Statistics recording disabled in configuration");
        }
    }
    
    /**
     * Handles the celebration phase by determining if there's a winner and triggering
     * the appropriate celebration.
     */
    void handleCelebration() {
        // Skip celebration if plugin is shutting down
        if (!plugin.isEnabled()) {
            logger.debug("Skipping celebration - plugin is shutting down");
            return;
        }
        
        if (playerManager.getPlayerCount() == 1) {
            celebrateWinner();
        } else {
            // Handle no winner case - broadcast end message
            eventHelper.broadcastMessage(Component.text("Game Over! No winner!", NamedTextColor.RED));
            eventHelper.fireGameEndEvent();
            logger.debug("No winner celebration - game ended without a single winner");
        }
    }
    
    /**
     * Celebrates the game winner if one exists.
     */
    private void celebrateWinner() {
        UUID winnerId = playerManager.getPlayers().iterator().next();
        Player winner = playerManager.getCachedPlayer(winnerId);
        
        if (winner != null) {
            celebrationManager.celebrateWinner(winner);
            eventHelper.fireGameWinEvent(winner);
            logger.debug("Celebrated winner: " + winner.getName());
        } else {
            logger.warn("Winner player not found for celebration");
        }
    }
    
    /**
     * Schedules the final cleanup tasks to run after the celebration period.
     * 
     * @param game The game instance to remove from the game manager
     */
    void scheduleGameCleanup(@NotNull Game game) {
        // Skip scheduling if plugin is shutting down
        if (!plugin.isEnabled()) {
            logger.debug("Skipping cleanup scheduling - plugin is shutting down, performing immediate cleanup");
            performFinalCleanup(game);
            return;
        }
        
        // This gives time for fireworks and title to be seen
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            performFinalCleanup(game);
        }, 120L); // 6 second delay (120 ticks) - gives time for celebration
        
        logger.debug("Scheduled final cleanup in 6 seconds");
    }
    
    /**
     * Performs the final cleanup operations.
     * 
     * @param game The game instance to remove from the game manager
     */
    private void performFinalCleanup(@NotNull Game game) {
        try {
            // Restore world settings using world manager (including original border)
            worldManager.restoreWorld();
            
            // Return all players to lobby or original locations using player manager
            playerManager.cleanup();
            
            // Remove from game manager
            plugin.getGameManager().removeGame(game);
            
            logger.info("Cleaned up game: " + gameId);
            
        } catch (Exception e) {
            logger.error("Error during final cleanup for game: " + gameId, e);
        }
    }
    
    /**
     * Performs immediate cleanup without scheduling.
     * Used when the game needs to be cleaned up immediately.
     * 
     * @param game The game instance to remove from the game manager
     */
    void performImmediateCleanup(@NotNull Game game) {
        logger.debug("Performing immediate cleanup for game: " + gameId);
        performFinalCleanup(game);
    }
}