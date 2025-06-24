package net.lumalyte.game;

import net.lumalyte.LumaSG;
import net.lumalyte.arena.Arena;
import net.lumalyte.util.DebugLogger;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

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
    
    /** Original difficulty of the arena world before game start */
    private org.bukkit.Difficulty originalDifficulty;
    
    /** Original time in the arena world before game start */
    private long originalTime;
    
    public GameWorldManager(@NotNull LumaSG plugin, @NotNull Arena arena) {
        this.plugin = plugin;
        this.arena = arena;
        this.logger = plugin.getDebugLogger().forContext("GameWorldManager");
    }
    
    /**
     * Sets up the world for game start.
     */
    public void setupWorld() {
        World arenaWorld = arena.getWorld();
        if (arenaWorld != null) {
            // Store original settings
            originalDifficulty = arenaWorld.getDifficulty();
            originalTime = arenaWorld.getTime();
            
            // Set game settings
            arenaWorld.setDifficulty(org.bukkit.Difficulty.PEACEFUL);
            arenaWorld.setTime(1000); // Set to morning/day (1000 ticks)
            
            // Set initial world border
            Location center = arena.getCenter() != null ? arena.getCenter() : 
                             (arena.getLobbySpawn() != null ? arena.getLobbySpawn() : 
                             (!arena.getSpawnPoints().isEmpty() ? arena.getSpawnPoints().get(0) : null));
            
            if (center != null) {
                arenaWorld.getWorldBorder().setCenter(center);
                arenaWorld.getWorldBorder().setSize(500.0); // 500x500 border for the main game
                logger.info("Set world border to 500 blocks centered at " + 
                    String.format("(%.1f, %.1f)", center.getX(), center.getZ()));
            }
            
            logger.debug("Set arena world " + arenaWorld.getName() + " to peaceful and daytime (was " + 
                originalDifficulty + " and time " + originalTime + ")");
        }
    }
    
    /**
     * Sets up the world border for deathmatch.
     */
    public void setupDeathmatchBorder() {
        Location center = arena.getLobbySpawn();
        if (center != null && center.getWorld() != null) {
            center.getWorld().getWorldBorder().setCenter(center);
            center.getWorld().getWorldBorder().setSize(75.0); // 75x75 border for deathmatch
            logger.info("Set deathmatch world border to 75 blocks centered at " + 
                String.format("(%.1f, %.1f)", center.getX(), center.getZ()));
        }
    }
    
    /**
     * Restores the world to its original state.
     */
    public void restoreWorld() {
        World arenaWorld = arena.getWorld();
        if (arenaWorld != null) {
            // Reset world border to normal size
            arenaWorld.getWorldBorder().setSize(500.0);
            logger.info("Reset world border to 500 blocks for arena: " + arena.getName());
            
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
            center = arena.getSpawnPoints().get(0);
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
            if (entity instanceof Item) {
                Item item = (Item) entity;
                if (item.getLocation().distance(center) <= radius) {
                    item.remove();
                    dropsCleared++;
                }
            }
        }
        
        logger.info("Cleared " + dropsCleared + " item drops from arena: " + arena.getName());
    }
    
    /**
     * Cleans up all world-related resources.
     */
    public void cleanup() {
        removeAllPlacedBlocks();
        clearAllDrops();
        restoreWorld();
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
} 