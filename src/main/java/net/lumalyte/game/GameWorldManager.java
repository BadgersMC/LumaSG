package net.lumalyte.game;

import net.lumalyte.LumaSG;
import net.lumalyte.arena.Arena;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
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
    
    /** Track placed blocks during the game */
    private final @NotNull Set<Location> placedBlocks = new HashSet<>();
    
    /** Original difficulty of the arena world before game start */
    private org.bukkit.Difficulty originalDifficulty;
    
    /** Original time in the arena world before game start */
    private long originalTime;
    
    public GameWorldManager(@NotNull LumaSG plugin, @NotNull Arena arena) {
        this.plugin = plugin;
        this.arena = arena;
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
                plugin.getLogger().info("Set world border to 500 blocks centered at " + 
                    String.format("(%.1f, %.1f)", center.getX(), center.getZ()));
            }
            
            if (plugin.getConfig().getBoolean("debug.enabled", false)) {
                plugin.getLogger().info("Set arena world " + arenaWorld.getName() + " to peaceful and daytime (was " + 
                    originalDifficulty + " and time " + originalTime + ")");
            }
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
            plugin.getLogger().info("Set deathmatch world border to 75 blocks centered at " + 
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
            plugin.getLogger().info("Reset world border to 500 blocks for arena: " + arena.getName());
            
            // Restore original difficulty and time if available
            if (originalDifficulty != null) {
                arenaWorld.setDifficulty(originalDifficulty);
                arenaWorld.setTime(originalTime);
                if (plugin.getConfig().getBoolean("debug.enabled", false)) {
                    plugin.getLogger().info("Restored arena world " + arenaWorld.getName() + " to " + 
                        originalDifficulty + " and time " + originalTime);
                }
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
     * Cleans up all world-related resources.
     */
    public void cleanup() {
        removeAllPlacedBlocks();
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