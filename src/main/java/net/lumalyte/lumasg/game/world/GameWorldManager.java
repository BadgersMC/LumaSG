package net.lumalyte.lumasg.game.world;

import net.lumalyte.lumasg.LumaSG;
import net.lumalyte.lumasg.arena.Arena;
import net.lumalyte.lumasg.util.core.DebugLogger;
import net.lumalyte.lumasg.util.validation.ValidationUtils;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages world-related aspects of a game instance.
 * Handles world settings, borders, block tracking, and cleanup.
 */
public class GameWorldManager {
    private final @NotNull LumaSG plugin;
    private final @NotNull Arena arena;
    
    /** The debug logger instance for this world manager */
    private final @NotNull DebugLogger.ContextualLogger logger;
    
    /** Track placed blocks during the game */
    private final @NotNull Set<Location> placedBlocks = new HashSet<>();
    
    /** Track barrier blocks for cleanup */
    private final @NotNull Set<Location> barrierBlocks = ConcurrentHashMap.newKeySet();
    
    /** Barrier manager for handling spawn point barriers */
    private final @NotNull GameBarrierManager barrierManager;
    
    /** Chest manager for handling chest filling */
    private final @NotNull GameChestFiller chestManager;
    
    
    /** Original difficulty of the arena world before game start */
    private org.bukkit.Difficulty originalDifficulty;
    
    /** Original time in the arena world before game start */
    private long originalTime;
    
    /** Original world border settings */
    private double originalBorderSize;
    private Location originalBorderCenter;
    
    /** Deathmatch border shrinking task */
    private @Nullable BukkitTask borderShrinkTask;
    
    /** Flag to track if original settings have been stored */
    private boolean originalSettingsStored = false;
    
    public GameWorldManager(@NotNull LumaSG plugin, @NotNull Arena arena) {
        ValidationUtils.requireNonNull(plugin, "Plugin", "GameWorldManager Construction");
        ValidationUtils.requireNonNull(arena, "Arena", "GameWorldManager Construction");
        
        this.plugin = plugin;
        this.arena = arena;
        this.logger = plugin.getDebugLogger().forContext("GameWorldManager-" + arena.getName());
        
        // Initialize sub-managers
        String gameId = arena.getName() + "-" + System.currentTimeMillis();
        this.barrierManager = new GameBarrierManager(plugin, gameId);
        this.chestManager = new GameChestFiller(plugin, arena, gameId);
        
        // Store original world settings immediately when the manager is created
        storeOriginalWorldSettings();
    }
    
    /**
     * Stores the original world settings that will be restored after the game.
     * This is called once when the manager is created to prevent overwriting with modified values.
     */
    private void storeOriginalWorldSettings() {
        World arenaWorld = arena.getWorld();
        if (arenaWorld != null && !originalSettingsStored) {
            // Store original settings
            originalDifficulty = arenaWorld.getDifficulty();
            originalTime = arenaWorld.getTime();
            
            // Store original world border settings
            WorldBorder worldBorder = arenaWorld.getWorldBorder();
            originalBorderSize = worldBorder.getSize();
            originalBorderCenter = worldBorder.getCenter();
            
            originalSettingsStored = true;
            
            logger.debug("Stored original world settings - Difficulty: " + originalDifficulty + 
                ", Time: " + originalTime + ", Border Size: " + originalBorderSize);
        }
    }
    
    /**
     * Sets up the world for game start.
     */
    public void setupWorld() {
        World arenaWorld = arena.getWorld();
        if (arenaWorld != null) {
            // Set game settings
            arenaWorld.setDifficulty(org.bukkit.Difficulty.PEACEFUL);
            arenaWorld.setTime(1000); // Set to morning/day (1000 ticks)
            
            // Set initial world border
            Location center = arena.getCenter() != null ? arena.getCenter() : 
                             (arena.getLobbySpawn() != null ? arena.getLobbySpawn() : 
                             (!arena.getSpawnPoints().isEmpty() ? arena.getSpawnPoints().getFirst() : null));
            
            if (center != null) {
                WorldBorder worldBorder = arenaWorld.getWorldBorder();
                double initialBorderSize = plugin.getConfig().getDouble("world-border.initial-size", 500.0);
                worldBorder.setCenter(center);
                worldBorder.setSize(initialBorderSize);
                logger.info("Set world border to " + initialBorderSize + " blocks centered at " + 
                    String.format("(%.1f, %.1f)", center.getX(), center.getZ()));
            }
            
            // Fill arena chests asynchronously
            chestManager.fillArenaChestsAsync().thenRun(() -> 
                logger.info("Arena chests filled successfully"));
            
            // Create barriers around spawn points
            if (!arena.getSpawnPoints().isEmpty()) {
                for (Location spawnPoint : arena.getSpawnPoints()) {
                    barrierManager.createBarrierBoxAroundLocation(spawnPoint);
                    logger.debug("Created barrier around spawn point at " + 
                        String.format("(%.1f, %.1f, %.1f)", spawnPoint.getX(), spawnPoint.getY(), spawnPoint.getZ()));
                }
                logger.info("Created barriers around " + arena.getSpawnPoints().size() + " spawn points");
            }
            
            logger.debug("Set arena world " + arenaWorld.getName() + " to peaceful and daytime");
        }
    }
    
    /**
     * Removes barriers around spawn points when the game starts.
     */
    public void removeSpawnBarriers() {
        if (!arena.getSpawnPoints().isEmpty()) {
            for (Location spawnPoint : arena.getSpawnPoints()) {
                barrierManager.removeBarriersAroundLocation(spawnPoint);
            }
            logger.info("Removed barriers around " + arena.getSpawnPoints().size() + " spawn points");
        }
    }
    
    /**
     * Sets up the world border for deathmatch with gradual shrinking.
     */
    public void setupDeathmatchBorder() {
        Location center = arena.getLobbySpawn();
        if (center == null) {
            center = arena.getCenter();
        }
        if (center == null && !arena.getSpawnPoints().isEmpty()) {
            center = arena.getSpawnPoints().getFirst();
        }
        
        if (center != null && center.getWorld() != null) {
            WorldBorder worldBorder = center.getWorld().getWorldBorder();
            
            // Get configuration values
            double deathmatchStartSize = plugin.getConfig().getDouble("world-border.deathmatch.start-size", 75.0);
            double deathmatchEndSize = plugin.getConfig().getDouble("world-border.deathmatch.end-size", 10.0);
            long shrinkDurationSeconds = plugin.getConfig().getLong("world-border.deathmatch.shrink-duration-seconds", 120);
            boolean enableShrinking = plugin.getConfig().getBoolean("world-border.deathmatch.enable-shrinking", true);
            
            // Set center and initial size
            worldBorder.setCenter(center);
            worldBorder.setSize(deathmatchStartSize);
            
            logger.info("Set deathmatch world border to " + deathmatchStartSize + " blocks centered at " + 
                String.format("(%.1f, %.1f)", center.getX(), center.getZ()));
            
            // Start gradual shrinking if enabled
            if (enableShrinking && deathmatchEndSize < deathmatchStartSize) {
                startBorderShrinking(worldBorder, deathmatchEndSize, shrinkDurationSeconds);
            }
        }
    }
    
    /**
     * Sets the world border to a safe size during the celebration phase.
     * This ensures players don't get hurt by the border during celebration.
     */
    public void setCelebrationBorder() {
        World arenaWorld = arena.getWorld();
        if (arenaWorld != null) {
            WorldBorder worldBorder = arenaWorld.getWorldBorder();
            
            // Get the initial border size from config (default 500.0)
            double celebrationSize = plugin.getConfig().getDouble("world-border.initial-size", 500.0);
            
            // Set center to arena center or lobby spawn
            Location center = arena.getCenter();
            if (center == null) {
                center = arena.getLobbySpawn();
            }
            if (center == null && !arena.getSpawnPoints().isEmpty()) {
                center = arena.getSpawnPoints().getFirst();
            }
            
            if (center != null) {
                worldBorder.setCenter(center);
            }
            
            // Set to safe size immediately
            worldBorder.setSize(celebrationSize);
            logger.info("Set celebration world border to " + celebrationSize + " blocks");
        }
    }
    
    /**
     * Starts the gradual border shrinking process during deathmatch.
     */
    private void startBorderShrinking(@NotNull WorldBorder worldBorder, double targetSize, long durationSeconds) {
        // Cancel any existing shrink task
        if (borderShrinkTask != null && !borderShrinkTask.isCancelled()) {
            borderShrinkTask.cancel();
        }
        
        // Use Bukkit's built-in smooth border shrinking
        worldBorder.setSize(targetSize, durationSeconds);
        
        logger.info("Started world border shrinking to " + targetSize + " blocks over " + durationSeconds + " seconds");
        
        // Optional: Schedule warnings for players
        schedulePlayerWarnings(durationSeconds);
    }
    
    /**
     * Schedules warnings to players about the shrinking border.
     */
    private void schedulePlayerWarnings(long durationSeconds) {
        // Warning at 75% time remaining
        long warning1Time = (long)(durationSeconds * 0.25 * 20); // Convert to ticks
        // Warning at 50% time remaining  
        long warning2Time = (long)(durationSeconds * 0.5 * 20);
        // Warning at 25% time remaining
        long warning3Time = (long)(durationSeconds * 0.75 * 20);
        
        if (warning1Time > 0) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                broadcastBorderWarning("The world border will continue shrinking! Stay inside the safe zone!");
            }, warning1Time);
        }
        
        if (warning2Time > 0) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                broadcastBorderWarning("World border is halfway to its final size! Move toward the center!");
            }, warning2Time);
        }
        
        if (warning3Time > 0) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                broadcastBorderWarning("World border is almost at minimum size! Fight in the center!");
            }, warning3Time);
        }
    }
    
    /**
     * Broadcasts a border warning message to all players in the arena.
     */
    private void broadcastBorderWarning(@NotNull String message) {
        World arenaWorld = arena.getWorld();
        if (arenaWorld != null) {
            for (Player player : arenaWorld.getPlayers()) {
                player.sendMessage(net.kyori.adventure.text.Component.text(message, 
                    net.kyori.adventure.text.format.NamedTextColor.RED));
                player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 0.5f);
            }
        }
    }
    
    /**
     * Restores the world to its original state.
     */
    public void restoreWorld() {
        World arenaWorld = arena.getWorld();
        if (arenaWorld != null) {
            // Cancel any border shrinking task
            if (borderShrinkTask != null && !borderShrinkTask.isCancelled()) {
                borderShrinkTask.cancel();
                borderShrinkTask = null;
            }
            
            // Restore original world border settings
            WorldBorder worldBorder = arenaWorld.getWorldBorder();
            if (originalBorderCenter != null) {
                worldBorder.setCenter(originalBorderCenter);
            }
            // Ensure we're setting the size immediately, not gradually
            worldBorder.setSize(originalBorderSize);
            logger.info("Restored world border to original size: " + originalBorderSize + " blocks for arena: " + arena.getName());
            
            // Restore original difficulty and time if available
            if (originalDifficulty != null) {
                arenaWorld.setDifficulty(originalDifficulty);
                arenaWorld.setTime(originalTime);
                logger.debug("Restored arena world " + arenaWorld.getName() + " to " + 
                    originalDifficulty + " and time " + originalTime);
            }
        }
    }
    
    /**
     * Checks if a block type is allowed to be placed during the game.
     */
    public boolean isBlockAllowed(@NotNull Material material) {
        return arena.isBlockAllowed(material);
    }
    
    /**
     * Tracks a placed block during the game.
     */
    public void trackPlacedBlock(@NotNull Location location) {
        placedBlocks.add(location.clone());
    }
    
    /**
     * Removes all blocks placed during the game.
     */
    public void removeAllPlacedBlocks() {
        for (Location location : placedBlocks) {
            location.getBlock().setType(Material.AIR);
        }
        placedBlocks.clear();
    }
    
    /**
     * Clears all item drops in the arena.
     */
    public void clearAllDrops() {
        World arenaWorld = arena.getWorld();
        if (arenaWorld == null) {
            logger.warn("Arena world is null, cannot clear drops");
            return;
        }
        
        Location center = arena.getCenter();
        if (center == null) {
            center = arena.getLobbySpawn();
        }
        if (center == null && !arena.getSpawnPoints().isEmpty()) {
            center = arena.getSpawnPoints().getFirst();
        }
        
        if (center == null) {
            logger.warn("No center location found for arena, clearing all drops in world");
            // Clear all drops in the world if no center is found
            for (Entity entity : arenaWorld.getEntities()) {
                if (entity instanceof Item) {
                    entity.remove();
                }
            }
            return;
        }
        
        // Clear drops within a reasonable radius of the arena center
        double radius = 300.0; // 300 block radius should cover most arenas
        int dropsCleared = 0;
        
        for (Entity entity : arenaWorld.getEntities()) {
            if (entity instanceof Item item) {
				if (item.getLocation().distance(center) <= radius) {
                    item.remove();
                    dropsCleared++;
                }
            }
        }
        
        logger.info("Cleared " + dropsCleared + " item drops from arena: " + arena.getName());
    }
    
    /**
     * Tracks a barrier block for cleanup.
     * @param location The location of the barrier block
     */
    public void trackBarrierBlock(@NotNull Location location) {
        barrierBlocks.add(location.clone());
    }

    /**
     * Removes a barrier block from tracking.
     * @param location The location of the barrier block
     */
    public void untrackBarrierBlock(@NotNull Location location) {
        barrierBlocks.remove(location);
    }

    /**
     * Removes all barrier blocks in the arena.
     */
    public void removeAllBarrierBlocks() {
        World arenaWorld = arena.getWorld();
        if (arenaWorld == null) {
            logger.warn("Arena world is null, cannot remove barrier blocks");
            return;
        }

        // Remove barriers managed by the barrier manager
        barrierManager.removeAllBarriers();
        logger.debug("Removed all barriers managed by barrier manager");

        // Remove all tracked barrier blocks
        removeTrackedBarrierBlocks();

        // Safety check: Scan for any untracked barrier blocks in the arena
        performSafetyBarrierScan(arenaWorld);
    }
    
    /**
     * Removes all tracked barrier blocks.
     */
    private void removeTrackedBarrierBlocks() {
        int removedCount = 0;
        for (Location location : barrierBlocks) {
            if (isValidBarrierBlock(location)) {
                location.getBlock().setType(Material.AIR);
                removedCount++;
                logger.debug("Removed barrier block at " + location);
            }
        }
        barrierBlocks.clear();

        if (removedCount > 0) {
            logger.info("Removed " + removedCount + " tracked barrier blocks");
        }
    }
    
    /**
     * Checks if a location contains a valid barrier block that should be removed.
     */
    private boolean isValidBarrierBlock(@NotNull Location location) {
        return location.getWorld() != null && 
               location.getBlock().getType() == Material.BARRIER;
    }
    
    /**
     * Performs a safety scan to remove any untracked barrier blocks in the arena.
     */
    private void performSafetyBarrierScan(@NotNull World arenaWorld) {
        Location center = getArenaCenterLocation();
        if (center == null) {
            logger.debug("No center location found, skipping safety barrier scan");
            return;
        }
        
        int barrierBlocksFound = scanAreaForBarriers(center);
        
        if (barrierBlocksFound > 0) {
            logger.warn("Found and removed " + barrierBlocksFound + " untracked barrier blocks during cleanup");
        }
    }
    
    /**
     * Gets the center location for the arena, trying multiple fallback options.
     */
    private @Nullable Location getArenaCenterLocation() {
        Location center = arena.getCenter();
        if (center != null) return center;
        
        center = arena.getLobbySpawn();
        if (center != null) return center;
        
        if (!arena.getSpawnPoints().isEmpty()) {
            return arena.getSpawnPoints().getFirst();
        }

        return null;
    }
    
    /**
     * Scans a cubic area around the center location for barrier blocks and removes them.
     * 
     * @param center The center location to scan around
     * @return The number of barrier blocks found and removed
     */
    private int scanAreaForBarriers(@NotNull Location center) {
        final int HORIZONTAL_RADIUS = 100;
        final int VERTICAL_RADIUS = 50;
            int barrierBlocksFound = 0;
            
        for (int x = -HORIZONTAL_RADIUS; x <= HORIZONTAL_RADIUS; x++) {
            for (int y = -VERTICAL_RADIUS; y <= VERTICAL_RADIUS; y++) {
                for (int z = -HORIZONTAL_RADIUS; z <= HORIZONTAL_RADIUS; z++) {
                        Location loc = center.clone().add(x, y, z);
                        if (loc.getBlock().getType() == Material.BARRIER) {
                            loc.getBlock().setType(Material.AIR);
                            barrierBlocksFound++;
                        }
                    }
                }
            }
            
        return barrierBlocksFound;
    }
    
    /**
     * Cleans up all world-related resources.
     */
    public void cleanup() {
        // Clean up the barrier manager
        barrierManager.cleanup();
        
        // Clean up the chest manager
        chestManager.cleanup();
        
        // Clean up other resources
        removeAllBarrierBlocks();
        removeAllPlacedBlocks();
        clearAllDrops();
        restoreWorld();
        
        logger.info("GameWorldManager cleanup complete");
    }
    
    // Getters
    public @NotNull Set<Location> getPlacedBlocks() {
        return new HashSet<>(placedBlocks);
    }
    
    public org.bukkit.Difficulty getOriginalDifficulty() {
        return originalDifficulty;
    }
    
    public long getOriginalTime() {
        return originalTime;
    }
    
    /**
     * Gets the barrier manager for this world.
     * 
     * @return The barrier manager
     */
    public @NotNull GameBarrierManager getBarrierManager() {
        return barrierManager;
    }
    
    /**
     * Gets the chest manager for this world.
     * 
     * @return The chest manager
     */
    public @NotNull GameChestFiller getChestManager() {
        return chestManager;
    }
} 
