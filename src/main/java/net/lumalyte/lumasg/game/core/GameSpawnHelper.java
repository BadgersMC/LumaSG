package net.lumalyte.lumasg.game.core;

import net.lumalyte.lumasg.LumaSG;
import net.lumalyte.lumasg.game.player.GamePlayerManager;
import net.lumalyte.lumasg.util.core.DebugLogger;
import net.lumalyte.lumasg.util.security.InputSanitizer;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Helper class for managing player spawn enforcement during waiting and
 * countdown phases.
 * 
 * <p>
 * This class centralizes all spawn enforcement logic that was previously
 * in the Game class. It handles teleporting players back to their spawn points
 * if they move too far away during the waiting/countdown phases.
 * </p>
 * 
 * <p>
 * This is a package-private helper class - it's an implementation detail
 * of the Game class and should not be used directly by external code.
 * </p>
 */
class GameSpawnHelper {
    private final @NotNull LumaSG plugin;
    private final @NotNull DebugLogger.ContextualLogger logger;
    private final @NotNull GamePlayerManager playerManager;
    private final @NotNull GameStateHelper stateHelper;

    // Task management for spawn enforcement
    private final @NotNull Map<Integer, BukkitTask> activeTasks = new ConcurrentHashMap<>();
    private @Nullable BukkitTask enforcementTask;

    // Configuration
    private static final double MAX_SPAWN_DISTANCE = 1.5; // blocks
    private static final long ENFORCEMENT_INTERVAL = 40L; // ticks (2 seconds)

    /**
     * Creates a new spawn helper for the specified game.
     * 
     * @param plugin        The plugin instance
     * @param gameId        The game ID for logging context
     * @param playerManager The player manager
     * @param stateHelper   The state helper to check game state
     */
    GameSpawnHelper(@NotNull LumaSG plugin, @NotNull String gameId,
            @NotNull GamePlayerManager playerManager,
            @NotNull GameStateHelper stateHelper) {
        this.plugin = plugin;
        this.logger = plugin.getDebugLogger().forContext("GameSpawn-" + gameId);
        this.playerManager = playerManager;
        this.stateHelper = stateHelper;
    }

    /**
     * Starts spawn point enforcement for players during waiting and countdown
     * phases.
     * Players who move too far from their spawn points will be teleported back.
     */
    void startSpawnPointEnforcement() {
        // Don't start if already running
        if (enforcementTask != null && !enforcementTask.isCancelled()) {
            logger.debug("Spawn enforcement already running");
            return;
        }

        // Only run enforcement during WAITING and COUNTDOWN states
        enforcementTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            try {
                enforceSpawnPoints();
            } catch (Exception e) {
                logger.error("Error during spawn point enforcement", e);
            }
        }, ENFORCEMENT_INTERVAL, ENFORCEMENT_INTERVAL);

        // Track the task for cleanup
        activeTasks.put(enforcementTask.getTaskId(), enforcementTask);

        logger.debug("Started spawn point enforcement task");
    }

    /**
     * Performs the actual spawn point enforcement check.
     */
    private void enforceSpawnPoints() {
        // Stop enforcement if game has progressed past countdown
        GameState currentState = stateHelper.getCurrentState();
        if (currentState != GameState.WAITING && currentState != GameState.COUNTDOWN) {
            stopSpawnPointEnforcement();
            return;
        }

        // Check each player's position
        for (UUID playerId : playerManager.getPlayers()) {
            enforcePlayerSpawnPoint(playerId);
        }
    }

    /**
     * Enforces spawn point for a specific player.
     * 
     * @param playerId The player ID to check
     */
    private void enforcePlayerSpawnPoint(@NotNull UUID playerId) {
        Player player = playerManager.getCachedPlayer(playerId);
        Location spawnLoc = playerManager.getPlayerLocations().get(playerId);

        if (player == null || spawnLoc == null || !player.isOnline()) {
            return;
        }

        Location playerLoc = player.getLocation();
        double distance = playerLoc.distance(spawnLoc);

        // Check if player is more than the maximum allowed distance from their spawn
        // point
        if (distance > MAX_SPAWN_DISTANCE) {
            // Teleport them back to their spawn point
            player.teleport(spawnLoc);

            logger.debug("Teleported " + InputSanitizer.sanitizeForLogging(player.getName())
                    + " back to spawn point - was " + String.format("%.2f", distance) + " blocks away");
        }
    }

    /**
     * Stops spawn point enforcement.
     */
    void stopSpawnPointEnforcement() {
        if (enforcementTask != null && !enforcementTask.isCancelled()) {
            enforcementTask.cancel();
            activeTasks.remove(enforcementTask.getTaskId());
            logger.debug("Stopped spawn point enforcement task");
        }
        enforcementTask = null;
    }

    /**
     * Checks if spawn point enforcement is currently active.
     * 
     * @return true if enforcement is active, false otherwise
     */
    boolean isEnforcementActive() {
        return enforcementTask != null && !enforcementTask.isCancelled();
    }

    /**
     * Cleans up all spawn enforcement resources.
     */
    void cleanup() {
        // Stop enforcement
        stopSpawnPointEnforcement();

        // Cancel any remaining tasks
        for (BukkitTask task : activeTasks.values()) {
            if (!task.isCancelled()) {
                task.cancel();
            }
        }
        activeTasks.clear();

        logger.debug("Cleaned up spawn enforcement resources");
    }
}