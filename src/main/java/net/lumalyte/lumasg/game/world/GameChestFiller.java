package net.lumalyte.lumasg.game.world;

import net.lumalyte.lumasg.LumaSG;
import net.lumalyte.lumasg.arena.Arena;
import net.lumalyte.lumasg.chest.ChestManager;
import net.lumalyte.lumasg.util.core.DebugLogger;
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
public class GameChestFiller {
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
    
    /** Semaphore to limit concurrent chest filling operations across all games */
    private static final java.util.concurrent.Semaphore CONCURRENT_FILL_LIMITER = 
        new java.util.concurrent.Semaphore(Math.max(2, Runtime.getRuntime().availableProcessors()));
    
    /** Thread pool for concurrent chest filling operations */
    private static final java.util.concurrent.ExecutorService CHEST_FILLER_POOL;
    
    static {
        // Calculate optimal thread pool size for concurrent games
        // We need to balance between performance and resource usage
        int coreCount = Runtime.getRuntime().availableProcessors();
        
        // For chest filling across multiple games, we use a more conservative approach
        // This prevents thread pool exhaustion when running many concurrent games
        int poolSize = Math.max(4, Math.min(coreCount * 2, 16)); // Cap at 16 threads max
        
        CHEST_FILLER_POOL = java.util.concurrent.Executors.newFixedThreadPool(poolSize, 
            r -> {
                Thread t = new Thread(r, "LumaSG-ChestFiller-" + System.currentTimeMillis());
                t.setDaemon(true); // Allow JVM to exit if tasks are still running
                t.setPriority(Thread.NORM_PRIORITY - 1); // Slightly lower priority
                return t;
            });
    }
    
    /**
     * Constructs a new GameChestFiller instance.
     * 
     * @param plugin The plugin instance
     * @param arena The arena where the game is being played
     * @param gameId The ID of the game this manager is associated with
     */
    public GameChestFiller(@NotNull LumaSG plugin, @NotNull Arena arena, @NotNull String gameId) {
        this.plugin = plugin;
        this.arena = arena;
        this.logger = plugin.getDebugLogger().forContext("GameChestFiller-" + gameId);
        this.chestManager = plugin.getChestManager();
        
        int poolSize = ((java.util.concurrent.ThreadPoolExecutor)CHEST_FILLER_POOL).getMaximumPoolSize();
        int permits = CONCURRENT_FILL_LIMITER.availablePermits();
        logger.debug("Created GameChestFiller - Thread pool size: " + poolSize + 
            ", Available permits: " + permits);
    }
    
    /**
     * Fills all arena chests with items asynchronously.
     * 
     * @return A CompletableFuture that completes when all chests are filled
     */
    public CompletableFuture<Void> fillArenaChestsAsync() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Acquire permit to limit concurrent chest filling operations
                CONCURRENT_FILL_LIMITER.acquire();
                
                try {
                    if (!ensureChestsScanned()) {
                        logger.warn("Failed to scan for chests - chest filling aborted");
                        return CompletableFuture.<Void>completedFuture(null);
                    }
                    
                    if (!ensureChestItemsLoaded()) {
                        logger.warn("Failed to load chest items - chest filling aborted");
                        return CompletableFuture.<Void>completedFuture(null);
                    }
                    
                    List<Location> chestLocations = new ArrayList<>(arena.getChestLocations());
                    int totalChests = chestLocations.size();
                    AtomicInteger filledChests = new AtomicInteger(0);
                    AtomicInteger failedChests = new AtomicInteger(0);
                    
                    logger.info("Starting to fill " + totalChests + " chests (permit acquired)");
                    
                    // Create a list to hold all batch futures
                    List<CompletableFuture<Void>> batchFutures = new ArrayList<>();
                    
                    // Process chests in smaller batches to reduce CPU spikes
                    int adaptiveBatchSize = Math.max(5, Math.min(DEFAULT_BATCH_SIZE, totalChests / 4));
                    
                    // Process chests in batches to avoid overwhelming the server
                    for (int i = 0; i < chestLocations.size(); i += adaptiveBatchSize) {
                        int end = Math.min(i + adaptiveBatchSize, chestLocations.size());
                        List<Location> batch = chestLocations.subList(i, end);
                        
                        // Process each batch concurrently and collect the futures
                        CompletableFuture<Void> batchFuture = CompletableFuture.runAsync(() -> {
                            processBatch(batch, filledChests, failedChests);
                        }, CHEST_FILLER_POOL);
                        
                        batchFutures.add(batchFuture);
                        
                        // Add small delay between batch submissions to prevent CPU spikes
                        if (i + adaptiveBatchSize < chestLocations.size()) {
                            try {
                                Thread.sleep(10); // 10ms delay between batches
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                break;
                            }
                        }
                    }
                    
                    // Return a future that completes when all batches are done
                    return CompletableFuture.allOf(batchFutures.toArray(new CompletableFuture[0]))
                        .thenRun(() -> {
                            logger.info("Chest filling complete: " + filledChests.get() + " filled, " + 
                                failedChests.get() + " failed");
                        })
                        .whenComplete((result, throwable) -> {
                            // Always release the permit
                            CONCURRENT_FILL_LIMITER.release();
                        });
                } catch (Exception e) {
                    CONCURRENT_FILL_LIMITER.release();
                    throw e;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.warn("Chest filling interrupted while waiting for permit");
                return CompletableFuture.<Void>completedFuture(null);
            } catch (Exception e) {
                logger.severe("Error during chest filling", e);
                return CompletableFuture.<Void>completedFuture(null);
            }
        }, CHEST_FILLER_POOL).thenCompose(future -> future);
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
        // Process chests sequentially within each batch to reduce CPU load
        // The concurrency comes from multiple batches running in parallel
        for (Location location : batch) {
            try {
                Block block = location.getBlock();
                if (block.getType() != Material.CHEST) {
                    logger.debug("Block at " + location + " is not a chest");
                    failedChests.incrementAndGet();
                    continue;
                }
                
                // We need to run inventory operations on the main thread
                CompletableFuture<Void> mainThreadFuture = new CompletableFuture<>();
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    try {
                        Chest chest = (Chest) block.getState();
                        Inventory inventory = chest.getInventory();
                        inventory.clear();
                        
                        String tier = selectChestTier();
                        chestManager.fillChest(location, tier);
                        
                        this.filledChests.put(location, true);
                        filledChests.incrementAndGet();
                        
                        logger.debug("Filled chest at " + location + " with tier " + tier);
                        mainThreadFuture.complete(null);
                    } catch (Exception e) {
                        logger.warn("Error filling chest at " + location, e);
                        failedChests.incrementAndGet();
                        mainThreadFuture.completeExceptionally(e);
                    }
                });
                
                // Wait for the main thread operation to complete with timeout
                try {
                    mainThreadFuture.get(5, java.util.concurrent.TimeUnit.SECONDS);
                } catch (java.util.concurrent.TimeoutException e) {
                    logger.warn("Timeout filling chest at " + location);
                    failedChests.incrementAndGet();
                } catch (Exception e) {
                    logger.warn("Error waiting for chest fill at " + location, e);
                    failedChests.incrementAndGet();
                }
                
                // Small delay between chests to prevent overwhelming the main thread
                try {
                    Thread.sleep(5); // 5ms delay between chests
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            } catch (Exception e) {
                logger.warn("Error processing chest at " + location, e);
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
    
    /**
     * Shuts down the chest filler thread pool.
     * This should be called when the plugin is disabled.
     */
    public static void shutdownThreadPool() {
        CHEST_FILLER_POOL.shutdown();
        try {
            // Wait for tasks to complete with a timeout
            if (!CHEST_FILLER_POOL.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                CHEST_FILLER_POOL.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            CHEST_FILLER_POOL.shutdownNow();
        }
    }
}