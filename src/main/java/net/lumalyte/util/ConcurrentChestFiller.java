package net.lumalyte.util;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import net.lumalyte.LumaSG;
import net.lumalyte.chest.ChestItem;
import net.lumalyte.chest.ChestManager;

/**
 * High-performance concurrent chest filling system.
 * Optimizes chest filling operations for multiple games with adaptive parallel processing.
 * 
 * Thread pool sizing follows the formula:
 * Threads = Available Cores * Target CPU Utilization * (1 + Wait Time / Service Time)
 * 
 * For I/O bound chest operations (database lookups, item generation):
 * - Wait Time: ~20ms (item generation, tier lookup)
 * - Service Time: ~5ms (inventory manipulation)
 * - Blocking Coefficient: 20/5 = 4
 * - Target CPU Utilization: 0.75 (leave 25% headroom)
 * 
 * Example thread pool sizes for different server configurations:
 * - 2-core server: 2 * 0.75 * (1 + 4) = 7.5 → 8 threads
 * - 4-core server: 4 * 0.75 * (1 + 4) = 15 threads  
 * - 8-core server: 8 * 0.75 * (1 + 4) = 30 → 16 threads (capped by MAX_THREADS)
 * - 1-core server: 1 * 0.75 * (1 + 4) = 3.75 → 4 threads
 * 
 * This ensures optimal resource utilization without overwhelming the system.
 * Server administrators can override these calculations via config.yml if needed.
 */
public class ConcurrentChestFiller {
    
    // Cache configurations
    private static final Cache<String, List<ChestItem>> TIER_LOOT_CACHE = Caffeine.newBuilder()
            .maximumSize(20)  // Cache loot for different tiers
            .expireAfterWrite(Duration.ofMinutes(15))
            .recordStats()
            .build();
    
    private static final Cache<Location, String> CHEST_TIER_CACHE = Caffeine.newBuilder()
            .maximumSize(500)  // Cache chest tier assignments
            .expireAfterWrite(Duration.ofMinutes(30))
            .build();
    
    // Thread pool configuration (loaded from config)
    private static int MIN_THREADS = 2;
    private static int MAX_THREADS = 16;
    private static double TARGET_CPU_UTILIZATION = 0.75;
    private static double BLOCKING_COEFFICIENT = 4.0;
    private static int CONFIGURED_THREAD_POOL_SIZE = 0;  // 0 = auto-calculate
    
    private static ScheduledExecutorService CHEST_EXECUTOR;
    private static int threadPoolSize;
    
    private static DebugLogger.ContextualLogger logger;
    private static LumaSG pluginInstance;
    private static ChestManager chestManager;
    
    /**
     * Loads thread pool configuration from plugin config
     */
    private static void loadConfiguration() {
        if (pluginInstance != null) {
            CONFIGURED_THREAD_POOL_SIZE = pluginInstance.getConfig().getInt("performance.chest-filling.thread-pool-size", 0);
            MIN_THREADS = pluginInstance.getConfig().getInt("performance.chest-filling.min-threads", 2);
            MAX_THREADS = pluginInstance.getConfig().getInt("performance.chest-filling.max-threads", 16);
            TARGET_CPU_UTILIZATION = pluginInstance.getConfig().getDouble("performance.chest-filling.target-cpu-utilization", 0.75);
            BLOCKING_COEFFICIENT = pluginInstance.getConfig().getDouble("performance.chest-filling.blocking-coefficient", 4.0);
            
            // Validate configuration values
            TARGET_CPU_UTILIZATION = Math.max(0.1, Math.min(1.0, TARGET_CPU_UTILIZATION));
            BLOCKING_COEFFICIENT = Math.max(0.1, Math.min(20.0, BLOCKING_COEFFICIENT));
            MIN_THREADS = Math.max(1, MIN_THREADS);
            MAX_THREADS = Math.max(MIN_THREADS, Math.min(64, MAX_THREADS));
        }
    }
    
    /**
     * Calculates optimal thread pool size based on system capabilities and configuration
     * 
     * @return Optimal thread pool size
     */
    private static int calculateOptimalThreadPoolSize() {
        // Use configured size if specified
        if (CONFIGURED_THREAD_POOL_SIZE > 0) {
            int finalSize = Math.max(MIN_THREADS, Math.min(MAX_THREADS, CONFIGURED_THREAD_POOL_SIZE));
            if (logger != null) {
                logger.info("Using configured thread pool size: " + finalSize);
                if (finalSize != CONFIGURED_THREAD_POOL_SIZE) {
                    logger.warn("Configured size " + CONFIGURED_THREAD_POOL_SIZE + " was bounded to " + finalSize);
                }
            }
            return finalSize;
        }
        
        // Auto-calculate based on system capabilities
        int availableCores = Runtime.getRuntime().availableProcessors();
        
        // Formula: Cores * Target CPU Utilization * (1 + Blocking Coefficient)
        double optimalSize = availableCores * TARGET_CPU_UTILIZATION * (1 + BLOCKING_COEFFICIENT);
        
        // Apply bounds
        int calculatedSize = (int) Math.round(optimalSize);
        int finalSize = Math.max(MIN_THREADS, Math.min(MAX_THREADS, calculatedSize));
        
        if (logger != null) {
            logger.info("Thread pool sizing calculation:");
            logger.info("  Available CPU cores: " + availableCores);
            logger.info("  Target CPU utilization: " + (TARGET_CPU_UTILIZATION * 100) + "%");
            logger.info("  Blocking coefficient (I/O ratio): " + BLOCKING_COEFFICIENT);
            logger.info("  Calculated optimal size: " + calculatedSize);
            logger.info("  Final size (after bounds): " + finalSize);
        }
        
        return finalSize;
    }
    
    /**
     * Initializes the concurrent chest filler with adaptive thread pool sizing
     * 
     * @param plugin The plugin instance
     * @param chestMgr The chest manager instance
     */
    public static void initialize(@NotNull LumaSG plugin, @NotNull ChestManager chestMgr) {
        pluginInstance = plugin;
        chestManager = chestMgr;
        logger = plugin.getDebugLogger().forContext("ConcurrentChestFiller");
        
        // Load configuration and calculate optimal thread pool size
        loadConfiguration();
        threadPoolSize = calculateOptimalThreadPoolSize();
        
        // Create adaptive thread pool
        CHEST_EXECUTOR = Executors.newScheduledThreadPool(threadPoolSize, r -> {
            Thread t = new Thread(r, "ChestFiller-" + System.currentTimeMillis());
            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY - 1);  // Slightly lower priority
            return t;
        });
        
        logger.info("ConcurrentChestFiller initialized:");
        logger.info("  ✓ Adaptive thread pool with " + threadPoolSize + " worker threads");
        logger.info("  ✓ Resource-aware sizing based on " + Runtime.getRuntime().availableProcessors() + " CPU cores");
        logger.info("  ✓ I/O optimized for chest filling operations");
    }
    
    /**
     * Fills multiple chests concurrently using adaptive parallel processing
     * 
     * @param chestLocations List of chest locations to fill
     * @param gameId The game ID for logging purposes
     * @return CompletableFuture containing the number of successfully filled chests
     */
    public static CompletableFuture<Integer> fillChestsConcurrent(@NotNull List<Location> chestLocations, 
                                                                 @NotNull String gameId) {
        if (chestLocations.isEmpty()) {
            return CompletableFuture.completedFuture(0);
        }
        
        return CompletableFuture.supplyAsync(() -> {
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failCount = new AtomicInteger(0);
            
            // Process chests in parallel batches, adaptive to thread pool size
            int batchSize = Math.max(1, chestLocations.size() / threadPoolSize);
            List<List<Location>> batches = partitionList(chestLocations, batchSize);
            
            List<CompletableFuture<Void>> batchFutures = new ArrayList<>();
            
            for (List<Location> batch : batches) {
                CompletableFuture<Void> batchFuture = CompletableFuture.runAsync(() -> {
                    processBatch(batch, successCount, failCount, gameId);
                }, CHEST_EXECUTOR);
                
                batchFutures.add(batchFuture);
            }
            
            // Wait for all batches to complete
            CompletableFuture.allOf(batchFutures.toArray(new CompletableFuture[0])).join();
            
            int total = successCount.get();
            int failed = failCount.get();
            
            if (logger != null) {
                logger.info("Game " + gameId + " - Filled " + total + " chests successfully, " + failed + " failed");
            }
            
            return total;
        });
    }
    
    /**
     * Processes a batch of chests
     * 
     * @param batch The batch of chest locations
     * @param successCount Success counter
     * @param failCount Failure counter
     * @param gameId Game ID for logging
     */
    private static void processBatch(@NotNull List<Location> batch, 
                                   @NotNull AtomicInteger successCount,
                                   @NotNull AtomicInteger failCount,
                                   @NotNull String gameId) {
        for (Location location : batch) {
            try {
                if (fillSingleChest(location, gameId)) {
                    successCount.incrementAndGet();
                } else {
                    failCount.incrementAndGet();
                }
            } catch (Exception e) {
                failCount.incrementAndGet();
                if (logger != null) {
                    logger.error("Error filling chest at " + location + " for game " + gameId, e);
                }
            }
        }
    }
    
    /**
     * Fills a single chest with optimized tier selection and item generation
     * 
     * @param location The chest location
     * @param gameId The game ID for context
     * @return true if chest was filled successfully
     */
    private static boolean fillSingleChest(@NotNull Location location, @NotNull String gameId) {
        // Check if location is valid and contains a chest
        Block block = location.getBlock();
        if (block.getType() != Material.CHEST) {
            return false;
        }
        
        Chest chest = (Chest) block.getState();
        Inventory inventory = chest.getInventory();
        
        // Clear existing items
        inventory.clear();
        
        // Get or determine chest tier
        String tier = getOrAssignChestTier(location);
        
        // Get cached loot for tier
        List<ChestItem> tierLoot = getCachedTierLoot(tier);
        if (tierLoot.isEmpty()) {
            return false;
        }
        
        // Fill chest with items
        return fillChestInventory(inventory, tierLoot, tier);
    }
    
    /**
     * Gets or assigns a tier to a chest location
     * 
     * @param location The chest location
     * @return The chest tier
     */
    @NotNull
    private static String getOrAssignChestTier(@NotNull Location location) {
        String cachedTier = CHEST_TIER_CACHE.getIfPresent(location);
        if (cachedTier != null) {
            return cachedTier;
        }
        
        // Assign tier based on distance from center or other logic
        String tier = selectChestTier();
        CHEST_TIER_CACHE.put(location, tier);
        
        return tier;
    }
    
    /**
     * Selects a chest tier based on configured probabilities
     * 
     * @return The selected tier
     */
    @NotNull
    private static String selectChestTier() {
        // Use weighted random selection
        ThreadLocalRandom random = ThreadLocalRandom.current();
        double roll = random.nextDouble(100.0);
        
        // These probabilities should match your chest.yml configuration
        if (roll < 5.0) {
            return "legendary";
        } else if (roll < 15.0) {
            return "epic";
        } else if (roll < 35.0) {
            return "rare";
        } else if (roll < 60.0) {
            return "uncommon";
        } else {
            return "common";
        }
    }
    
    /**
     * Gets cached loot for a specific tier
     * 
     * @param tier The tier name
     * @return List of chest items for the tier
     */
    @NotNull
    private static List<ChestItem> getCachedTierLoot(@NotNull String tier) {
        List<ChestItem> cached = TIER_LOOT_CACHE.getIfPresent(tier);
        if (cached != null) {
            return cached;
        }
        
        // Load from chest manager
        List<ChestItem> tierLoot = chestManager.getTierItems(tier);
        if (!tierLoot.isEmpty()) {
            TIER_LOOT_CACHE.put(tier, tierLoot);
        }
        
        return tierLoot;
    }
    
    /**
     * Fills a chest inventory with items from the tier loot
     * 
     * @param inventory The chest inventory
     * @param tierLoot Available loot for the tier
     * @param tier The tier name for logging
     * @return true if filling was successful
     */
    private static boolean fillChestInventory(@NotNull Inventory inventory, 
                                            @NotNull List<ChestItem> tierLoot, 
                                            @NotNull String tier) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        
        // Determine number of items to place (3-7 items typically)
        int itemCount = random.nextInt(3, 8);
        
        // Get available slots
        List<Integer> availableSlots = new ArrayList<>();
        for (int i = 0; i < inventory.getSize(); i++) {
            availableSlots.add(i);
        }
        Collections.shuffle(availableSlots);
        
        int placedItems = 0;
        
        for (int i = 0; i < Math.min(itemCount, availableSlots.size()); i++) {
            ChestItem randomItem = getRandomItem(tierLoot);
            if (randomItem == null) {
                continue;
            }
            
            ItemStack itemStack = randomItem.getItemStack(pluginInstance);
            if (itemStack != null) {
                int slot = availableSlots.get(i);
                
                // Schedule item placement on main thread
                Bukkit.getScheduler().runTask(pluginInstance, () -> {
                    inventory.setItem(slot, itemStack);
                });
                
                placedItems++;
            }
        }
        
        return placedItems > 0;
    }
    
    /**
     * Gets a random item from the tier loot based on weight
     * 
     * @param tierLoot The available loot
     * @return A random chest item or null
     */
    @Nullable
    private static ChestItem getRandomItem(@NotNull List<ChestItem> tierLoot) {
        if (tierLoot.isEmpty()) {
            return null;
        }
        
        // Calculate total weight
        double totalWeight = tierLoot.stream()
                .mapToDouble(ChestItem::getChance)
                .sum();
        
        if (totalWeight <= 0) {
            return tierLoot.get(ThreadLocalRandom.current().nextInt(tierLoot.size()));
        }
        
        // Weighted random selection
        double roll = ThreadLocalRandom.current().nextDouble(totalWeight);
        double currentWeight = 0.0;
        
        for (ChestItem item : tierLoot) {
            currentWeight += item.getChance();
            if (roll <= currentWeight) {
                return item;
            }
        }
        
        // Fallback to last item
        return tierLoot.get(tierLoot.size() - 1);
    }
    
    /**
     * Partitions a list into smaller batches
     * 
     * @param list The list to partition
     * @param batchSize The size of each batch
     * @return List of batches
     */
    @NotNull
    private static <T> List<List<T>> partitionList(@NotNull List<T> list, int batchSize) {
        List<List<T>> batches = new ArrayList<>();
        
        for (int i = 0; i < list.size(); i += batchSize) {
            int end = Math.min(i + batchSize, list.size());
            batches.add(new ArrayList<>(list.subList(i, end)));
        }
        
        return batches;
    }
    
    /**
     * Pre-caches loot for all tiers to improve performance
     * 
     * @return CompletableFuture that completes when caching is done
     */
    public static CompletableFuture<Void> precacheTierLoot() {
        return CompletableFuture.runAsync(() -> {
            // Get available tiers dynamically from configuration
            Set<String> availableTiers = chestManager.getTiers();
            
            for (String tier : availableTiers) {
                List<ChestItem> tierLoot = chestManager.getTierItems(tier);
                if (!tierLoot.isEmpty()) {
                    TIER_LOOT_CACHE.put(tier, tierLoot);
                }
            }
            
            if (logger != null) {
                logger.info("Pre-cached loot for " + availableTiers.size() + " tiers: " + availableTiers);
            }
        });
    }
    
    /**
     * Gets cache statistics for monitoring
     * 
     * @return String containing cache statistics
     */
    public static String getCacheStats() {
        return String.format(
                "ChestFiller - Tier Loot Cache: %d (%.2f%% hit rate), Chest Tier Cache: %d (%.2f%% hit rate)",
                TIER_LOOT_CACHE.estimatedSize(),
                TIER_LOOT_CACHE.stats().hitRate() * 100,
                CHEST_TIER_CACHE.estimatedSize(),
                CHEST_TIER_CACHE.stats().hitRate() * 100
        );
    }
    
    /**
     * Gets cache and thread pool statistics for monitoring
     * 
     * @return String containing performance statistics
     */
    public static String getPerformanceStats() {
        StringBuilder stats = new StringBuilder();
        stats.append("ConcurrentChestFiller Performance Statistics:\n");
        stats.append("  Thread Pool:\n");
        stats.append("    - Configured Size: ").append(threadPoolSize).append(" threads\n");
        stats.append("    - Available CPU Cores: ").append(Runtime.getRuntime().availableProcessors()).append("\n");
        stats.append("    - Target CPU Utilization: ").append(String.format("%.1f%%", TARGET_CPU_UTILIZATION * 100)).append("\n");
        stats.append("    - Blocking Coefficient: ").append(BLOCKING_COEFFICIENT).append("\n");
        
        if (CHEST_EXECUTOR != null) {
            stats.append("    - Active Threads: ").append(((java.util.concurrent.ThreadPoolExecutor) CHEST_EXECUTOR).getActiveCount()).append("\n");
            stats.append("    - Pool Size: ").append(((java.util.concurrent.ThreadPoolExecutor) CHEST_EXECUTOR).getPoolSize()).append("\n");
            stats.append("    - Completed Tasks: ").append(((java.util.concurrent.ThreadPoolExecutor) CHEST_EXECUTOR).getCompletedTaskCount()).append("\n");
        }
        
        stats.append("  Cache Statistics:\n");
        stats.append("    - Tier Loot Cache: Size=").append(TIER_LOOT_CACHE.estimatedSize())
              .append(", Hit Rate=").append(String.format("%.2f%%", TIER_LOOT_CACHE.stats().hitRate() * 100)).append("\n");
        stats.append("    - Chest Tier Cache: Size=").append(CHEST_TIER_CACHE.estimatedSize()).append("\n");
        
        return stats.toString();
    }
    
    /**
     * Gets the current thread pool size being used
     * 
     * @return Current thread pool size
     */
    public static int getCurrentThreadPoolSize() {
        return threadPoolSize;
    }
    
    /**
     * Checks if the current configuration is using auto-calculated thread pool size
     * 
     * @return true if using auto-calculation, false if using configured size
     */
    public static boolean isUsingAutoCalculation() {
        return CONFIGURED_THREAD_POOL_SIZE <= 0;
    }
    
    /**
     * Shuts down the concurrent chest filler
     */
    public static void shutdown() {
        if (logger != null) {
            logger.info("Shutting down ConcurrentChestFiller...");
        }
        
        // Clear caches
        TIER_LOOT_CACHE.invalidateAll();
        CHEST_TIER_CACHE.invalidateAll();
        
        // Shutdown executor
        CHEST_EXECUTOR.shutdown();
        try {
            if (!CHEST_EXECUTOR.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS)) {
                CHEST_EXECUTOR.shutdownNow();
            }
        } catch (InterruptedException e) {
            CHEST_EXECUTOR.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        if (logger != null) {
            logger.info("ConcurrentChestFiller shutdown complete");
        }
    }
} 