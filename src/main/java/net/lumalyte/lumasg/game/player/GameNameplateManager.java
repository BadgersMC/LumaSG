package net.lumalyte.lumasg.game.player;

import net.lumalyte.lumasg.LumaSG;
import net.lumalyte.lumasg.arena.Arena;
import net.lumalyte.lumasg.util.core.DebugLogger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages nameplate visibility for players in a game.
 * Uses Paper's modern APIs to hide nameplates through walls and implements
 * line-of-sight checking to prevent wallhacking through player names.
 */
public class GameNameplateManager {
    private final @NotNull LumaSG plugin;
    private final @NotNull Arena arena;
    private final @NotNull UUID gameId;
    private final @NotNull GamePlayerManager playerManager;
    
    /** The debug logger instance for this nameplate manager */
    private final @NotNull DebugLogger.ContextualLogger logger;
    
    /** Track which players can see which other players' nameplates */
    private final @NotNull Map<UUID, Set<UUID>> visibilityMap = new ConcurrentHashMap<>();
    
    /** Task for updating nameplate visibility */
    private @Nullable BukkitTask visibilityUpdateTask;
    
    /** Whether nameplate hiding is enabled */
    private boolean nameplateHidingEnabled;
    
    /** Update interval for visibility checks (in ticks) */
    private int updateInterval;
    
    /** Maximum distance for nameplate visibility */
    private double maxVisibilityDistance;
    
    public GameNameplateManager(@NotNull LumaSG plugin, @NotNull Arena arena, @NotNull UUID gameId,
                               @NotNull GamePlayerManager playerManager) {
        this.plugin = plugin;
        this.arena = arena;
        this.gameId = gameId;
        this.playerManager = playerManager;
        this.logger = plugin.getDebugLogger().forContext("GameNameplateManager-" + gameId.toString().substring(0, 8));
        
        // Load configuration
        loadConfiguration();
    }
    
    /**
     * Loads configuration settings for nameplate management.
     */
    private void loadConfiguration() {
        nameplateHidingEnabled = plugin.getConfig().getBoolean("nameplate-hiding.enabled", true);
        updateInterval = plugin.getConfig().getInt("nameplate-hiding.update-interval-ticks", 10);
        maxVisibilityDistance = plugin.getConfig().getDouble("nameplate-hiding.max-visibility-distance", 50.0);
        
        logger.debug("Loaded nameplate configuration - Enabled: " + nameplateHidingEnabled + 
                    ", Update interval: " + updateInterval + " ticks, Max distance: " + maxVisibilityDistance);
    }
    
    /**
     * Starts the nameplate management system.
     */
    public void start() {
        if (!nameplateHidingEnabled) {
            logger.debug("Nameplate hiding is disabled in configuration");
            return;
        }
        
        logger.info("Starting nameplate management for game: " + gameId);
        
        // Initialize visibility map for all players
        initializeVisibilityMap();
        
        // Start the visibility update task
        startVisibilityUpdateTask();
    }
    
    /**
     * Initializes the visibility map with all players being able to see each other.
     */
    private void initializeVisibilityMap() {
        Set<UUID> allPlayers = new HashSet<>(playerManager.getPlayers());
        
        for (UUID playerId : allPlayers) {
            visibilityMap.put(playerId, new HashSet<>(allPlayers));
        }
        
        logger.debug("Initialized visibility map for " + allPlayers.size() + " players");
    }
    
    /**
     * Starts the task that periodically updates nameplate visibility.
     */
    private void startVisibilityUpdateTask() {
        if (visibilityUpdateTask != null && !visibilityUpdateTask.isCancelled()) {
            visibilityUpdateTask.cancel();
        }
        
        visibilityUpdateTask = Bukkit.getScheduler().runTaskTimer(plugin, 
            this::updateNameplateVisibility, 
            0L, // Start immediately
            updateInterval);
        
        logger.debug("Started nameplate visibility update task with interval: " + updateInterval + " ticks");
    }
    
    /**
     * Updates nameplate visibility for all players based on line-of-sight.
     */
    private void updateNameplateVisibility() {
        Set<UUID> activePlayers = playerManager.getPlayers();
        
        for (UUID viewerId : activePlayers) {
            Player viewer = playerManager.getCachedPlayer(viewerId);
            if (viewer == null || !viewer.isOnline()) {
                continue;
            }
            
            Set<UUID> visiblePlayers = visibilityMap.computeIfAbsent(viewerId, k -> new HashSet<>());
            
            for (UUID targetId : activePlayers) {
                if (viewerId.equals(targetId)) {
                    continue; // Don't check visibility to self
                }
                
                Player target = playerManager.getCachedPlayer(targetId);
                if (target == null || !target.isOnline()) {
                    continue;
                }
                
                boolean shouldBeVisible = calculateVisibility(viewer, target);
                boolean currentlyVisible = visiblePlayers.contains(targetId);
                
                if (shouldBeVisible != currentlyVisible) {
                    updatePlayerVisibility(viewer, target, shouldBeVisible);
                    
                    if (shouldBeVisible) {
                        visiblePlayers.add(targetId);
                    } else {
                        visiblePlayers.remove(targetId);
                    }
                }
            }
        }
    }
    
    /**
     * Calculates whether a target player should be visible to a viewer.
     */
    private boolean calculateVisibility(@NotNull Player viewer, @NotNull Player target) {
        Location viewerEye = viewer.getEyeLocation();
        Location targetEye = target.getEyeLocation();
        
        // Check distance
        double distance = viewerEye.distance(targetEye);
        if (distance > maxVisibilityDistance) {
            return false;
        }
        
        // Check line of sight
        return hasLineOfSight(viewerEye, targetEye);
    }
    
    /**
     * Checks if there's a clear line of sight between two locations.
     */
    private boolean hasLineOfSight(@NotNull Location from, @NotNull Location to) {
        if (from.getWorld() == null || to.getWorld() == null || !from.getWorld().equals(to.getWorld())) {
            return false;
        }
        
        // Use ray tracing to check for blocks in the way
        RayTraceResult result = from.getWorld().rayTraceBlocks(from, 
            to.toVector().subtract(from.toVector()).normalize(), 
            from.distance(to),
            org.bukkit.FluidCollisionMode.NEVER,
            true);
        
        // If no blocks were hit, there's a clear line of sight
        return result == null || result.getHitBlock() == null;
    }
    
    /**
     * Updates the visibility of a target player for a specific viewer.
     */
    private void updatePlayerVisibility(@NotNull Player viewer, @NotNull Player target, boolean visible) {
        try {
            if (visible) {
                viewer.showEntity(plugin, target);
            } else {
                viewer.hideEntity(plugin, target);
            }
        } catch (Exception e) {
            logger.warn("Failed to update player visibility for " + viewer.getName() + 
                       " viewing " + target.getName() + ": " + e.getMessage());
        }
    }
    
    /**
     * Adds a player to the nameplate management system.
     */
    public void addPlayer(@NotNull Player player) {
        if (!nameplateHidingEnabled) {
            return;
        }
        
        UUID playerId = player.getUniqueId();
        Set<UUID> allPlayers = new HashSet<>(playerManager.getPlayers());
        
        // Initialize visibility for the new player
        visibilityMap.put(playerId, new HashSet<>(allPlayers));
        
        // Add the new player to other players' visibility maps
        for (UUID otherId : allPlayers) {
            if (!otherId.equals(playerId)) {
                visibilityMap.computeIfAbsent(otherId, k -> new HashSet<>()).add(playerId);
            }
        }
        
        logger.debug("Added player " + player.getName() + " to nameplate management");
    }
    
    /**
     * Removes a player from the nameplate management system.
     */
    public void removePlayer(@NotNull Player player) {
        if (!nameplateHidingEnabled) {
            return;
        }
        
        UUID playerId = player.getUniqueId();
        
        // Remove player from all visibility maps
        visibilityMap.remove(playerId);
        for (Set<UUID> visiblePlayers : visibilityMap.values()) {
            visiblePlayers.remove(playerId);
        }
        
        // Ensure the player can see all other players again (restore normal visibility)
        restorePlayerVisibility(player);
        
        logger.debug("Removed player " + player.getName() + " from nameplate management");
    }
    
    /**
     * Restores normal visibility for a player (shows all other players).
     */
    private void restorePlayerVisibility(@NotNull Player player) {
        for (UUID otherId : playerManager.getPlayers()) {
            Player other = playerManager.getCachedPlayer(otherId);
            if (other != null && other.isOnline() && !other.equals(player)) {
                try {
                    player.showEntity(plugin, other);
                    other.showEntity(plugin, player);
                } catch (Exception e) {
                    logger.warn("Failed to restore visibility between " + player.getName() + 
                               " and " + other.getName() + ": " + e.getMessage());
                }
            }
        }
    }
    
    /**
     * Temporarily disables nameplate hiding (e.g., during grace period).
     */
    public void disableNameplateHiding() {
        if (!nameplateHidingEnabled) {
            return;
        }
        
        logger.debug("Temporarily disabling nameplate hiding");
        
        // Make all players visible to each other
        for (UUID viewerId : playerManager.getPlayers()) {
            Player viewer = playerManager.getCachedPlayer(viewerId);
            if (viewer != null && viewer.isOnline()) {
                restorePlayerVisibility(viewer);
            }
        }
    }
    
    /**
     * Re-enables nameplate hiding after it was temporarily disabled.
     */
    public void enableNameplateHiding() {
        if (!nameplateHidingEnabled) {
            return;
        }
        
        logger.debug("Re-enabling nameplate hiding");
        
        // Force an immediate visibility update
        updateNameplateVisibility();
    }
    
    /**
     * Cleans up the nameplate management system.
     */
    public void cleanup() {
        logger.debug("Cleaning up nameplate management");
        
        // Cancel the visibility update task
        if (visibilityUpdateTask != null && !visibilityUpdateTask.isCancelled()) {
            visibilityUpdateTask.cancel();
            visibilityUpdateTask = null;
        }
        
        // Restore visibility for all players
        for (UUID playerId : new HashSet<>(playerManager.getPlayers())) {
            Player player = playerManager.getCachedPlayer(playerId);
            if (player != null && player.isOnline()) {
                restorePlayerVisibility(player);
            }
        }
        
        // Clear the visibility map
        visibilityMap.clear();
        
        logger.info("Nameplate management cleanup completed");
    }
    
    /**
     * Gets the current visibility status for debugging.
     */
    public @NotNull Map<String, Object> getDebugInfo() {
        Map<String, Object> info = new HashMap<>();
        info.put("enabled", nameplateHidingEnabled);
        info.put("updateInterval", updateInterval);
        info.put("maxVisibilityDistance", maxVisibilityDistance);
        info.put("trackedPlayers", visibilityMap.size());
        info.put("taskRunning", visibilityUpdateTask != null && !visibilityUpdateTask.isCancelled());
        return info;
    }
} 
