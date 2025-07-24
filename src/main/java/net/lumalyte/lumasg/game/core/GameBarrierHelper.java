package net.lumalyte.lumasg.game.core;

import net.lumalyte.lumasg.LumaSG;
import net.lumalyte.lumasg.arena.Arena;
import net.lumalyte.lumasg.game.world.GameWorldManager;
import net.lumalyte.lumasg.util.core.DebugLogger;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Helper class for managing spawn barriers in a game.
 * 
 * <p>This class handles all the implementation details of creating and removing
 * barrier blocks around player spawn points. It's used by the Game class to
 * keep players locked at their spawn points during waiting and countdown phases.</p>
 * 
 * <p>This is a package-private helper class - it's an implementation detail
 * of the Game class and should not be used directly by external code.</p>
 */
class GameBarrierHelper {
    private final @NotNull LumaSG plugin;
    private final @NotNull Arena arena;
    private final @NotNull GameWorldManager worldManager;
    private final @NotNull DebugLogger.ContextualLogger logger;
    
    // Barrier block management for movement restriction during countdown
    private final @NotNull Map<Location, Material> originalBlocks = new ConcurrentHashMap<>();
    private final @NotNull Set<Location> barrierBlocks = ConcurrentHashMap.newKeySet();
    
    /**
     * Creates a new barrier helper for the specified game.
     * 
     * @param plugin The plugin instance
     * @param arena The arena for this game
     * @param worldManager The world manager for tracking barriers
     * @param gameId The game ID for logging context
     */
    GameBarrierHelper(@NotNull LumaSG plugin, @NotNull Arena arena, 
                     @NotNull GameWorldManager worldManager, @NotNull String gameId) {
        this.plugin = plugin;
        this.arena = arena;
        this.worldManager = worldManager;
        this.logger = plugin.getDebugLogger().forContext("GameBarriers-" + gameId);
    }
    
    /**
     * Creates barriers around a player's spawn point to prevent movement.
     * 
     * @param player The player to create barriers for
     */
    void createBarriersForPlayer(@NotNull Player player) {
        // This would typically get the player's spawn location from the game
        // For now, we'll assume the Game class passes the location
        // This method signature might need adjustment based on how Game tracks spawn points
    }
    
    /**
     * Creates a 3x3x3 barrier box around the specified location.
     * 
     * @param center The center location for the barrier box
     */
    void createBarrierBoxAroundLocation(@NotNull Location center) {
        World world = center.getWorld();
        if (world == null) {
            logger.warn("Cannot create barrier box - world is null");
            return;
        }

        int centerX = center.getBlockX();
        int centerY = center.getBlockY();
        int centerZ = center.getBlockZ();

        // Create barriers on all 4 sides, 3 blocks high
        for (int y = 0; y < 3; y++) {
            // North side (Z-)
            placeBarrierBlock(world, centerX, centerY + y, centerZ - 1);
            // South side (Z+)
            placeBarrierBlock(world, centerX, centerY + y, centerZ + 1);
            // East side (X+)
            placeBarrierBlock(world, centerX + 1, centerY + y, centerZ);
            // West side (X-)
            placeBarrierBlock(world, centerX - 1, centerY + y, centerZ);

            // Corner blocks for complete enclosure
            placeBarrierBlock(world, centerX - 1, centerY + y, centerZ - 1); // NW
            placeBarrierBlock(world, centerX + 1, centerY + y, centerZ - 1); // NE
            placeBarrierBlock(world, centerX - 1, centerY + y, centerZ + 1); // SW
            placeBarrierBlock(world, centerX + 1, centerY + y, centerZ + 1); // SE
        }
        
        logger.debug("Created barrier box around location: " + formatLocation(center));
    }

    /**
     * Places a barrier block at the specified location, storing the original block
     * for restoration.
     * 
     * @param world The world to place the block in
     * @param x     The X coordinate
     * @param y     The Y coordinate
     * @param z     The Z coordinate
     */
    private void placeBarrierBlock(@NotNull World world, int x, int y, int z) {
        Location loc = new Location(world, x, y, z);
        Material originalMaterial = loc.getBlock().getType();

        // Only place barriers on air blocks or replace non-solid blocks
        if (originalMaterial == Material.AIR || !originalMaterial.isSolid()) {
            // Store original block for restoration
            originalBlocks.put(loc, originalMaterial);
            barrierBlocks.add(loc);

            // Place barrier block (invisible to players)
            loc.getBlock().setType(Material.BARRIER);

            // Track in world manager for safety cleanup
            worldManager.trackBarrierBlock(loc);

            logger.debug("Placed barrier block at (" + x + ", " + y + ", " + z + "), replacing " + originalMaterial);
        }
    }

    /**
     * Removes all barrier blocks and restores original blocks.
     * 
     * <p>This method is called when the grace period starts, allowing players to move freely.</p>
     */
    void removeAllBarriers() {
        logger.debug("Removing all spawn barriers - " + barrierBlocks.size() + " barriers to remove");

        for (Location loc : barrierBlocks) {
            if (loc.getWorld() != null) {
                Material originalMaterial = originalBlocks.getOrDefault(loc, Material.AIR);
                loc.getBlock().setType(originalMaterial);
                worldManager.untrackBarrierBlock(loc);
                logger.debug("Restored block at " + loc + " to " + originalMaterial);
            }
        }

        // Clear tracking collections
        originalBlocks.clear();
        barrierBlocks.clear();

        logger.info("Removed all spawn barriers - players can now move freely");
    }

    /**
     * Removes barrier blocks around a specific spawn location and restores the
     * original blocks. This is used when a player leaves the game to clean up 
     * their specific spawn point.
     * 
     * @param center The center location (spawn point) to remove barriers around
     */
    void removeBarriersAroundLocation(@NotNull Location center) {
        World world = center.getWorld();
        if (world == null) {
            logger.warn("Cannot remove barriers - world is null");
            return;
        }

        logger.debug("Removing barriers around location: " + formatLocation(center));

        // Remove barriers in a 3x3x3 box around the spawn point
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                for (int y = 0; y <= 2; y++) { // 3 blocks high
                    Location barrierLoc = center.clone().add(x, y, z);

                    // Only remove if it's actually a barrier block we placed
                    if (barrierBlocks.contains(barrierLoc)) {
                        // Get the original block material
                        Material originalMaterial = originalBlocks.get(barrierLoc);
                        if (originalMaterial != null) {
                            // Restore the original block
                            world.getBlockAt(barrierLoc).setType(originalMaterial);
                            originalBlocks.remove(barrierLoc);
                        } else {
                            // Default to air if no original block was stored
                            world.getBlockAt(barrierLoc).setType(Material.AIR);
                        }

                        barrierBlocks.remove(barrierLoc);
                        worldManager.untrackBarrierBlock(barrierLoc);
                    }
                }
            }
        }

        logger.debug("Removed barriers around spawn point: " + formatLocation(center));
    }
    
    /**
     * Cleans up all barrier resources.
     * Called when the game ends to ensure all barriers are removed.
     */
    void cleanup() {
        removeAllBarriers();
    }
    
    /**
     * Formats a location for logging.
     * 
     * @param location The location to format
     * @return A formatted string representation of the location
     */
    private @NotNull String formatLocation(@NotNull Location location) {
        return String.format("(%.2f, %.2f, %.2f)", 
            location.getX(), location.getY(), location.getZ());
    }
}