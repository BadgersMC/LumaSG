package net.lumalyte.lumasg.game;

import net.lumalyte.lumasg.LumaSG;
import net.lumalyte.lumasg.util.core.DebugLogger;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages barrier blocks for player movement restriction during game phases.
 * 
 * <p>This class handles the creation and removal of barrier blocks around spawn
 * points and other locations to prevent player movement during specific game
 * phases like countdown.</p>
 * 
 * @author LumaLyte
 * @version 1.0
 * @since 1.0
 */
public class GameBarrierManager {

	/** The debug logger instance for this manager */
    private final @NotNull DebugLogger.ContextualLogger logger;
    
    /** Map of locations to their original block materials */
    private final @NotNull Map<Location, Material> originalBlocks = new ConcurrentHashMap<>();
    
    /** Set of locations where barrier blocks have been placed */
    private final @NotNull Set<Location> barrierBlocks = ConcurrentHashMap.newKeySet();
    
    /**
     * Constructs a new GameBarrierManager instance.
     * 
     * @param plugin The plugin instance
     * @param gameId The ID of the game this manager is associated with
     */
    public GameBarrierManager(@NotNull LumaSG plugin, @NotNull String gameId) {
		this.logger = plugin.getDebugLogger().forContext("GameBarrierManager-" + gameId);
    }
    
    /**
     * Creates a box of barrier blocks around a location.
     * 
     * <p>The box is created with a radius of 1 block and a height of 3 blocks
     * to prevent player movement while allowing visibility.</p>
     * 
     * @param center The center location to create barriers around
     */
    public void createBarrierBoxAroundLocation(@NotNull Location center) {
        World world = center.getWorld();
        int x = center.getBlockX();
        int y = center.getBlockY();
        int z = center.getBlockZ();
        
        // Create a 3x3x3 box of barriers around the location
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = 0; dy <= 2; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    // Skip the center column to allow players to stand
                    if (dx == 0 && dz == 0) continue;
                    placeBarrierBlock(world, x + dx, y + dy, z + dz);
                }
            }
        }
    }
    
    /**
     * Places a barrier block at the specified coordinates.
     * 
     * @param world The world to place the block in
     * @param x The x coordinate
     * @param y The y coordinate
     * @param z The z coordinate
     */
    private void placeBarrierBlock(@NotNull World world, int x, int y, int z) {
        Location location = new Location(world, x, y, z);
        Block block = world.getBlockAt(location);
        
        // Store the original block type if we haven't already
        if (!barrierBlocks.contains(location)) {
            originalBlocks.put(location, block.getType());
            barrierBlocks.add(location);
        }
        
        // Set the block to a barrier
        block.setType(Material.BARRIER);
    }
    
    /**
     * Removes all barrier blocks and restores original blocks.
     */
    public void removeAllBarriers() {
        logger.debug("Removing " + barrierBlocks.size() + " barrier blocks");
        
        for (Location location : barrierBlocks) {
            Material originalType = originalBlocks.getOrDefault(location, Material.AIR);
            location.getBlock().setType(originalType);
        }
        
        barrierBlocks.clear();
        originalBlocks.clear();
    }
    
    /**
     * Removes barrier blocks around a specific location.
     * 
     * @param center The center location to remove barriers around
     */
    public void removeBarriersAroundLocation(@NotNull Location center) {
        World world = center.getWorld();
        int x = center.getBlockX();
        int y = center.getBlockY();
        int z = center.getBlockZ();
        
        // Remove barriers in a 3x3x3 area around the location
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = 0; dy <= 2; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    Location location = new Location(world, x + dx, y + dy, z + dz);
                    if (barrierBlocks.contains(location)) {
                        Material originalType = originalBlocks.getOrDefault(location, Material.AIR);
                        location.getBlock().setType(originalType);
                        barrierBlocks.remove(location);
                        originalBlocks.remove(location);
                    }
                }
            }
        }
    }
    
    /**
     * Checks if a location has a barrier block.
     * 
     * @param location The location to check
     * @return true if the location has a barrier block, false otherwise
     */
    public boolean hasBarrier(@NotNull Location location) {
        return barrierBlocks.contains(location);
    }
    
    /**
     * Gets the number of active barrier blocks.
     * 
     * @return The number of barrier blocks
     */
    public int getBarrierCount() {
        return barrierBlocks.size();
    }
    
    /**
     * Cleans up all resources used by this manager.
     */
    public void cleanup() {
        removeAllBarriers();
    }
} 
