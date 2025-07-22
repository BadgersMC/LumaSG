package net.lumalyte.lumasg.customitems.behaviors;

import net.lumalyte.lumasg.LumaSG;
import net.lumalyte.lumasg.customitems.CustomItem;
import net.lumalyte.lumasg.game.core.Game;
import net.lumalyte.lumasg.util.core.DebugLogger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import net.lumalyte.lumasg.listeners.CustomItemListener;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Behavior handler for the Player Tracker custom item.
 * 
 * <p>The Player Tracker displays a directional compass in the action bar that shows
 * the positions of other players and the top killer relative to the holder's facing direction.
 * The compass appears as: [---------- • ----- • --- 🗡️ ]</p>
 * 
 * <p>Features:</p>
 * <ul>
 *   <li>Action bar compass display showing player directions</li>
 *   <li>Distance-based color coding for players</li>
 *   <li>Special sword emoji for top killer</li>
 *   <li>Updates based on player's facing direction</li>
 *   <li>Configurable max tracking distance</li>
 *   <li>Configurable update intervals</li>
 * </ul>
 * 
 * @author LumaLyte
 * @version 1.0
 * @since 1.0
 */
public class PlayerTrackerBehavior {
    
    private final LumaSG plugin;
    private final DebugLogger.ContextualLogger logger;
    private final Map<UUID, TrackerData> activeTrackers;
    private BukkitRunnable updateTask;
    
    // Compass display constants
    private static final int COMPASS_WIDTH = 21; // Total width of compass display
    private static final String COMPASS_BAR = "─";
    private static final String PLAYER_DOT = "•";
    private static final String TOP_KILLER_EMOJI = "🗡️";
    private static final String AIRDROP_EMOJI = "📦"; // For future airdrop tracking
    
    // Distance-based colors (configurable via custom item config)
    private static final TextColor CLOSE_COLOR = NamedTextColor.RED;      // 0-50 blocks
    private static final TextColor MEDIUM_COLOR = NamedTextColor.YELLOW;   // 50-150 blocks
    private static final TextColor FAR_COLOR = NamedTextColor.GREEN;       // 150+ blocks
    private static final TextColor TOP_KILLER_COLOR = NamedTextColor.DARK_RED;
    private static final TextColor COMPASS_COLOR = NamedTextColor.GRAY;
    
    /**
     * Creates a new player tracker behavior handler.
     * 
     * @param plugin The plugin instance
     */
    public PlayerTrackerBehavior(@NotNull LumaSG plugin) {
        this.plugin = plugin;
        this.logger = plugin.getDebugLogger().forContext("PlayerTrackerBehavior");
        this.activeTrackers = new ConcurrentHashMap<>();
    }
    
    /**
     * Initializes the player tracker behavior system.
     */
    public void initialize() {
        logger.info("Initializing Player Tracker behavior system...");
        startUpdateTask();
        logger.info("Player Tracker behavior system initialized");
    }
    
    /**
     * Shuts down the player tracker behavior system.
     */
    public void shutdown() {
        if (updateTask != null) {
            updateTask.cancel();
            updateTask = null;
        }
        activeTrackers.clear();
        logger.info("Player Tracker behavior system shut down");
    }
    
    /**
     * Starts the periodic update task for all active trackers.
     */
    private void startUpdateTask() {
        if (updateTask != null) {
            updateTask.cancel();
        }
        
        updateTask = new BukkitRunnable() {
            @Override
            public void run() {
                updateAllTrackers();
            }
        };
        
        // Run every 10 ticks (0.5 seconds) for smooth updates
        updateTask.runTaskTimer(plugin, 10L, 10L);
    }
    
    /**
     * Registers a player tracker for a player.
     * 
     * @param player The player holding the tracker
     * @param customItem The custom item configuration
     */
    public void registerTracker(@NotNull Player player, @NotNull CustomItem customItem) {
        TrackerData data = new TrackerData(
            customItem.getBehaviorInt("update-interval", 10),
            customItem.getBehaviorBoolean("track-players", true),
            customItem.getBehaviorBoolean("track-top-killer", true),
            customItem.getBehaviorBoolean("track-airdrops", true),
            customItem.getBehaviorInt("max-range", 500),
            customItem.getBehaviorInt("close-distance", 50),
            customItem.getBehaviorInt("medium-distance", 150)
        );
        
        activeTrackers.put(player.getUniqueId(), data);
        logger.debug("Registered player tracker for " + player.getName());
        
        // Immediately update the tracker
        updateTracker(player, data);
    }
    
    /**
     * Unregisters a player tracker for a player.
     * 
     * @param player The player to unregister
     */
    public void unregisterTracker(@NotNull Player player) {
        activeTrackers.remove(player.getUniqueId());
        // Clear the action bar
        player.sendActionBar(Component.empty());
        logger.debug("Unregistered player tracker for " + player.getName());
    }
    
    /**
     * Updates all active trackers.
     */
    private void updateAllTrackers() {
        if (activeTrackers.isEmpty()) {
            return;
        }
        
        // Collect offline players to remove after iteration
        List<UUID> offlinePlayers = new ArrayList<>();
        
        for (Map.Entry<UUID, TrackerData> entry : activeTrackers.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null && player.isOnline()) {
                try {
                    updateTracker(player, entry.getValue());
                } catch (Exception e) {
                    logger.warn("Error updating tracker for player " + player.getName(), e);
                    offlinePlayers.add(entry.getKey()); // Remove problematic trackers
                }
            } else {
                // Mark offline players for removal
                offlinePlayers.add(entry.getKey());
            }
        }
        
        // Remove offline or problematic players
        for (UUID playerId : offlinePlayers) {
            activeTrackers.remove(playerId);
        }
    }
    
    /**
     * Updates a specific player's tracker.
     * 
     * @param player The player holding the tracker
     * @param data The tracker configuration data
     */
    private void updateTracker(@NotNull Player player, @NotNull TrackerData data) {
        try {
            // Check if player is in a game
            Game game = plugin.getGameManager().getGameByPlayer(player);
            if (game == null) {
                return;
            }
            
            // Check if player still has the tracker item
            if (!hasTrackerInInventory(player)) {
                unregisterTracker(player);
                return;
            }
            
            // Validate player location
            Location playerLocation = player.getLocation();
            if (playerLocation == null || playerLocation.getWorld() == null) {
                logger.debug("Invalid player location for tracker update: " + player.getName());
                return;
            }
            
            // Generate and display the compass
            Component compass = generateCompass(player, game, data);
            if (compass != null) {
                player.sendActionBar(compass);
            }
        } catch (Exception e) {
            logger.warn("Error updating tracker for player " + player.getName() + ": " + e.getMessage());
            // Don't spam the logs with full stack traces, but log the error
            logger.debug("Full tracker update error for " + player.getName(), e);
        }
    }
    
    /**
     * Checks if the player has a tracker in their inventory.
     * 
     * @param player The player to check
     * @return True if the player has a tracker
     */
    private boolean hasTrackerInInventory(@NotNull Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == Material.COMPASS) {
                String customId = plugin.getCustomItemsManager().getCustomItemId(item);
                if ("player_tracker".equals(customId)) {
                    return true;
                }
            }
        }
        return false;
    }
    
    /**
     * Generates the compass display component.
     * 
     * @param player The player holding the tracker
     * @param game The game the player is in
     * @param data The tracker configuration data
     * @return The compass component to display
     */
    private @NotNull Component generateCompass(@NotNull Player player, @NotNull Game game, @NotNull TrackerData data) {
        Location playerLocation = player.getLocation();
        if (playerLocation == null || playerLocation.getWorld() == null) {
            logger.debug("Invalid player location in generateCompass for " + player.getName());
            return Component.text("Tracker Error", NamedTextColor.RED);
        }
        
        float playerYaw = playerLocation.getYaw();
        
        // Get all trackable targets
        List<TrackableTarget> targets = getTrackableTargets(player, game, data);
        if (targets == null) {
            targets = new ArrayList<>(); // Fallback to empty list
        }
        
        // Create compass array
        Component[] compassElements = new Component[COMPASS_WIDTH];
        
        // Fill with compass bars initially
        for (int i = 0; i < COMPASS_WIDTH; i++) {
            compassElements[i] = Component.text(COMPASS_BAR, COMPASS_COLOR);
        }
        
        // Place targets on compass
        for (TrackableTarget target : targets) {
            if (target == null) continue; // Skip null targets
            
            int position = calculateCompassPosition(playerYaw, target.angle);
            if (position >= 0 && position < COMPASS_WIDTH) {
                compassElements[position] = Component.text(target.symbol, target.color);
            }
        }
        
        // Build the final compass component
        TextComponent.Builder compassBuilder = Component.text()
            .append(Component.text("[", COMPASS_COLOR));
        
        for (Component element : compassElements) {
            if (element != null) {
                compassBuilder.append(element);
            } else {
                compassBuilder.append(Component.text(COMPASS_BAR, COMPASS_COLOR));
            }
        }
        
        compassBuilder.append(Component.text("]", COMPASS_COLOR));
        
        return compassBuilder.build();
    }
    
    /**
     * Gets a list of trackable targets for a player.
     */
    private @NotNull List<TrackableTarget> getTrackableTargets(@NotNull Player player, @NotNull Game game, @NotNull TrackerData data) {
        List<TrackableTarget> targets = new ArrayList<>();
        Location playerLocation = player.getLocation();
        
        // Find top killer first for reference
        Player topKiller = data.trackTopKiller ? findTopKiller(game) : null;
        
        // Add player targets
        if (data.trackPlayers) {
            targets.addAll(getPlayerTargets(player, game, playerLocation, topKiller, data));
        }
        
        // Add airdrop targets
        if (data.trackAirdrops) {
            targets.addAll(getAirdropTargets(playerLocation, data));
        }
        
        // Sort targets by priority
        sortTargetsByPriority(targets);
        
        return targets;
    }
    
    /**
     * Gets a list of player targets within range.
     */
    private @NotNull List<TrackableTarget> getPlayerTargets(
            @NotNull Player player,
            @NotNull Game game,
            @NotNull Location playerLocation,
            @Nullable Player topKiller,
            @NotNull TrackerData data) {
        
        List<TrackableTarget> playerTargets = new ArrayList<>();
        
        for (UUID playerId : game.getPlayers()) {
            if (playerId.equals(player.getUniqueId())) {
                continue; // Don't track self
            }
            
            TrackableTarget target = createPlayerTarget(playerId, playerLocation, topKiller, data);
            if (target != null) {
                playerTargets.add(target);
            }
        }
        
        return playerTargets;
    }
    
    /**
     * Creates a TrackableTarget for a player if they are valid and in range.
     */
    private @Nullable TrackableTarget createPlayerTarget(
            @NotNull UUID playerId,
            @NotNull Location playerLocation,
            @Nullable Player topKiller,
            @NotNull TrackerData data) {
        
        Player gamePlayer = Bukkit.getPlayer(playerId);
        if (gamePlayer == null || !gamePlayer.isOnline()) {
            return null; // Skip offline players
        }
        
        Location targetLocation = gamePlayer.getLocation();
        double distance = playerLocation.distance(targetLocation);
        
        if (distance > data.maxRange) {
            return null; // Skip out of range players
        }
        
        double angle = calculateAngle(playerLocation, targetLocation);
        boolean isTopKiller = gamePlayer.equals(topKiller);
        String symbol = isTopKiller ? TOP_KILLER_EMOJI : PLAYER_DOT;
        TextColor color = isTopKiller ? TOP_KILLER_COLOR : getDistanceColor(distance, data);
        
        return new TrackableTarget(angle, symbol, color, distance, isTopKiller);
    }
    
    /**
     * Gets a list of airdrop targets within range.
     */
    private @NotNull List<TrackableTarget> getAirdropTargets(
            @NotNull Location playerLocation,
            @NotNull TrackerData data) {
        
        List<TrackableTarget> airdropTargets = new ArrayList<>();
        CustomItemListener customItemListener = plugin.getCustomItemListener();
        
        if (customItemListener != null) {
            Map<UUID, Location> activeAirdrops = customItemListener.getActiveAirdropLocations();
            for (Location airdropLocation : activeAirdrops.values()) {
                TrackableTarget target = createAirdropTarget(playerLocation, airdropLocation, data);
                if (target != null) {
                    airdropTargets.add(target);
                }
            }
        }
        
        return airdropTargets;
    }
    
    /**
     * Creates a TrackableTarget for an airdrop if it's in range.
     */
    private @Nullable TrackableTarget createAirdropTarget(
            @NotNull Location playerLocation,
            @NotNull Location airdropLocation,
            @NotNull TrackerData data) {
        
        double distance = playerLocation.distance(airdropLocation);
        if (distance > data.maxRange) {
            return null;
        }
        
        double angle = calculateAngle(playerLocation, airdropLocation);
        return new TrackableTarget(angle, AIRDROP_EMOJI, NamedTextColor.GOLD, distance, false);
    }
    
    /**
     * Sorts targets by priority (top killer first, then by distance).
     */
    private void sortTargetsByPriority(@NotNull List<TrackableTarget> targets) {
        targets.sort((a, b) -> {
            if (a.isTopKiller && !b.isTopKiller) return -1;
            if (!a.isTopKiller && b.isTopKiller) return 1;
            return Double.compare(a.distance, b.distance);
        });
    }
    
    /**
     * Calculates the angle between two locations.
     * 
     * @param from The starting location
     * @param to The target location
     * @return The angle in degrees
     */
    private double calculateAngle(@NotNull Location from, @NotNull Location to) {
        // Validate inputs
        if (from == null || to == null || from.getWorld() == null || to.getWorld() == null) {
            return 0.0; // Default to north
        }
        
        if (!from.getWorld().equals(to.getWorld())) {
            return 0.0; // Different worlds, can't calculate angle
        }
        
        // Get vector between points
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        
        // Handle edge case where positions are identical
        if (Math.abs(dx) < 0.001 && Math.abs(dz) < 0.001) {
            return 0.0; // Same position, default to north
        }
        
        // Calculate angle in degrees (0° is North, increases clockwise)
        double angle = Math.toDegrees(Math.atan2(-dx, dz));
        
        // Validate the result
        if (Double.isNaN(angle) || Double.isInfinite(angle)) {
            logger.debug("Invalid angle calculated between " + from + " and " + to + ": " + angle);
            return 0.0; // Default to north
        }
        
        // Normalize to 0-360
        angle = (angle % 360 + 360) % 360;
        
        return angle;
    }
    
    /**
     * Calculates the position on the compass for a given angle.
     * 
     * @param playerYaw The player's facing direction
     * @param targetAngle The target's angle
     * @return The position on the compass (0 to COMPASS_WIDTH-1)
     */
    private int calculateCompassPosition(float playerYaw, double targetAngle) {
        // Normalize player yaw to 0-360 (Minecraft yaw is -180 to +180)
        double normalizedYaw = (playerYaw % 360 + 360) % 360;
        
        // Calculate relative angle (how far target is from player's view direction)
        double relativeAngle = (targetAngle - normalizedYaw + 360) % 360;
        
        // Convert to -180 to +180 range for compass display
        if (relativeAngle > 180) {
            relativeAngle -= 360;
        }
        
        // Only show targets within the compass field of view (±90 degrees)
        if (Math.abs(relativeAngle) > 90) {
            return -1; // Not visible on compass
        }
        
        // Map to compass position (0 to COMPASS_WIDTH-1)
        // relativeAngle ranges from -90 to +90, map to 0 to COMPASS_WIDTH-1
        double normalizedPosition = (relativeAngle + 90) / 180.0; // 0 to 1
        int position = (int) Math.round(normalizedPosition * (COMPASS_WIDTH - 1));
        
        // Ensure we stay within bounds
        return Math.max(0, Math.min(COMPASS_WIDTH - 1, position));
    }
    
    /**
     * Gets the color based on distance.
     * 
     * @param distance The distance to the target
     * @param data The tracker configuration data
     * @return The appropriate color
     */
    private @NotNull TextColor getDistanceColor(double distance, @NotNull TrackerData data) {
        if (distance <= data.closeDistance) {
            return CLOSE_COLOR;
        } else if (distance <= data.mediumDistance) {
            return MEDIUM_COLOR;
        } else {
            return FAR_COLOR;
        }
    }
    
    /**
     * Finds the player with the most kills in the game.
     * 
     * @param game The game to search
     * @return The top killer, or null if none found
     */
    private @Nullable Player findTopKiller(@NotNull Game game) {
        Player topKiller = null;
        int mostKills = 0;
        
        for (UUID playerId : game.getPlayers()) {
            int kills = game.getPlayerKills(playerId);
            if (kills > mostKills) {
                mostKills = kills;
                Player player = Bukkit.getPlayer(playerId);
                if (player != null && player.isOnline()) {
                    topKiller = player;
                }
            }
        }
        
        // Only return if they have at least 1 kill
        return mostKills > 0 ? topKiller : null;
    }
    
    /**
     * Checks if a player has an active tracker.
     * 
     * @param player The player to check
     * @return True if the player has an active tracker
     */
    public boolean hasActiveTracker(@NotNull Player player) {
        return activeTrackers.containsKey(player.getUniqueId());
    }
    
    /**
     * Gets the number of active trackers.
     * 
     * @return The number of active trackers
     */
    public int getActiveTrackerCount() {
        return activeTrackers.size();
    }
    
    /**
     * Data class for tracker configuration.
     */
    private static class TrackerData {
        final int updateInterval;
        final boolean trackPlayers;
        final boolean trackTopKiller;
        final boolean trackAirdrops;
        final int maxRange;
        final int closeDistance;
        final int mediumDistance;
        
        TrackerData(int updateInterval, boolean trackPlayers, boolean trackTopKiller, boolean trackAirdrops, 
                   int maxRange, int closeDistance, int mediumDistance) {
            this.updateInterval = updateInterval;
            this.trackPlayers = trackPlayers;
            this.trackTopKiller = trackTopKiller;
            this.trackAirdrops = trackAirdrops;
            this.maxRange = maxRange;
            this.closeDistance = closeDistance;
            this.mediumDistance = mediumDistance;
        }
    }
    
    /**
     * Data class for trackable targets.
     */
    private static class TrackableTarget {
        final double angle;
        final String symbol;
        final TextColor color;
        final double distance;
        final boolean isTopKiller;
        
        TrackableTarget(double angle, String symbol, TextColor color, double distance, boolean isTopKiller) {
            this.angle = angle;
            this.symbol = symbol;
            this.color = color;
            this.distance = distance;
            this.isTopKiller = isTopKiller;
        }
    }
} 
