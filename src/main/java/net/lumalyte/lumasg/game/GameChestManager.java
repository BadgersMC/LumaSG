package net.lumalyte.game;

import net.lumalyte.LumaSG;
import net.lumalyte.arena.Arena;
import net.lumalyte.chest.ChestManager;
import net.lumalyte.util.DebugLogger;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.inventory.Inventory;
import org.jetbrains.annotations.NotNull;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Map;

/**
 * Manages chest-related operations in a Survival Games game.
 * 
 * <p>This class handles the filling of chests with items, ensuring chests are
 * properly scanned and loaded, and managing chest tiers. It works asynchronously
 * to minimize impact on server performance.</p>
 * 
 * @author LumaLyte
 * @version 1.0
 * @since 1.0
 */
public class GameChestManager {
    /** The plugin instance for configuration and server access */
    private final @NotNull LumaSG plugin;
    
    /** The debug logger instance for this manager */
    private final @NotNull DebugLogger.ContextualLogger logger;
    
    /** The arena where this game is being played */
    private final @NotNull Arena arena;
    
    /** The chest manager for item selection */
    private final @NotNull ChestManager chestManager;
    
    /** Map to track which chests have been filled */
    private final @NotNull Map<Location, Boolean> filledChests = new ConcurrentHashMap<>();
    
    /** Secure random number generator for chest tier selection */
    private static final SecureRandom secureRandom = new SecureRandom();
    
    /** Default batch size for processing chests */
    private static final int DEFAULT_BATCH_SIZE = 10;
    
    /**
     * Constructs a new GameChestManager instance.
     * 
     * @param plugin The plugin instance
     * @param arena The arena where the game is being played
     * @param gameId The ID of the game this manager is associated with
     */
    public GameChestManager(@NotNull LumaSG plugin, @NotNull Arena arena, @NotNull String gameId) {
        this.plugin = plugin;
        this.arena = arena;
        this.logger = plugin.getDebugLogger().forContext("GameChestManager-" + gameId);
        this.chestManager = plugin.getChestManager();
    }
    
    /**
     * Fills all arena chests with items asynchronously.
     * 
     * @return A CompletableFuture that completes when all chests are filled
     */
    public CompletableFuture<Void> fillArenaChestsAsync() {
        return CompletableFuture.runAsync(() -> {
            try {
                if (!ensureChestsScanned()) {
                    logger.warn("Failed to scan for chests - chest filling aborted");
                    return;
                }
                
                if (!ensureChestItemsLoaded()) {
                    logger.warn("Failed to load chest items - chest filling aborted");
                    return;
                }
                
                List<Location> chestLocations = new ArrayList<>(arena.getChestLocations());
                int totalChests = chestLocations.size();
                AtomicInteger filledChests = new AtomicInteger(0);
                AtomicInteger failedChests = new AtomicInteger(0);
                
                logger.info("Starting to fill " + totalChests + " chests");
                
                // Process chests in batches to avoid overwhelming the server
                for (int i = 0; i < chestLocations.size(); i += DEFAULT_BATCH_SIZE) {
                    int end = Math.min(i + DEFAULT_BATCH_SIZE, chestLocations.size());
                    List<Location> batch = chestLocations.subList(i, end);
                    processBatch(batch, filledChests, failedChests);
                }
                
                logger.info("Chest filling complete: " + filledChests.get() + " filled, " + 
                    failedChests.get() + " failed");
            } catch (Exception e) {
                logger.severe("Error during chest filling", e);
            }
        });
    }
    
    /**
     * Ensures all chests in the arena have been scanned.
     * 
     * @return true if scan was successful, false otherwise
     */
    private boolean ensureChestsScanned() {
        try {
            int chestCount = arena.scanForChests();
            logger.debug("Found " + chestCount + " chests in arena");
            return true;
        } catch (Exception e) {
            logger.warn("Error scanning for chests", e);
            return false;
        }
    }
    
    /**
     * Ensures chest items are loaded from configuration.
     * 
     * @return true if items were loaded successfully, false otherwise
     */
    private boolean ensureChestItemsLoaded() {
        try {
            chestManager.loadChestItems();
            return true;
        } catch (Exception e) {
            logger.warn("Error loading chest items", e);
            return false;
        }
    }
    
    /**
     * Processes a batch of chest locations.
     * 
     * @param batch The batch of locations to process
     * @param filledChests Counter for successfully filled chests
     * @param failedChests Counter for failed chest fills
     */
    private void processBatch(@NotNull List<Location> batch, 
                            @NotNull AtomicInteger filledChests, 
                            @NotNull AtomicInteger failedChests) {
        for (Location location : batch) {
            try {
                Block block = location.getBlock();
                if (block.getType() != Material.CHEST) {
                    logger.debug("Block at " + location + " is not a chest");
                    failedChests.incrementAndGet();
                    continue;
                }
                
                Chest chest = (Chest) block.getState();
                Inventory inventory = chest.getInventory();
                inventory.clear();
                
                String tier = selectChestTier();
                chestManager.fillChest(location, tier);
                
                this.filledChests.put(location, true);
                filledChests.incrementAndGet();
                
                logger.debug("Filled chest at " + location + " with tier " + tier);
            } catch (Exception e) {
                logger.warn("Error filling chest at " + location, e);
                failedChests.incrementAndGet();
            }
        }
    }
    
    /**
     * Selects a random chest tier based on configuration weights.
     * 
     * @return The selected tier name
     */
    private @NotNull String selectChestTier() {
        Map<String, Integer> tierWeights = Objects.requireNonNull(plugin.getConfig().getConfigurationSection("chest-tiers"))
            .getValues(false)
            .entrySet()
            .stream()
            .collect(java.util.stream.Collectors.toMap(
                Map.Entry::getKey,
                e -> ((Number) e.getValue()).intValue()
            ));
        
        int totalWeight = tierWeights.values().stream().mapToInt(Integer::intValue).sum();
        int randomValue = secureRandom.nextInt(totalWeight);
        
        int currentWeight = 0;
        for (Map.Entry<String, Integer> entry : tierWeights.entrySet()) {
            currentWeight += entry.getValue();
            if (randomValue < currentWeight) {
                return entry.getKey();
            }
        }
        
        // Fallback to default tier
        return "default";
    }
    
    /**
     * Checks if a chest at the given location has been filled.
     * 
     * @param location The location to check
     * @return true if the chest has been filled, false otherwise
     */
    public boolean isChestFilled(@NotNull Location location) {
        return filledChests.getOrDefault(location, false);
    }
    
    /**
     * Gets the number of filled chests.
     * 
     * @return The number of filled chests
     */
    public int getFilledChestCount() {
        return (int) filledChests.values().stream().filter(Boolean::booleanValue).count();
    }
    
    /**
     * Cleans up all resources used by this manager.
     */
    public void cleanup() {
        filledChests.clear();
    }
} 