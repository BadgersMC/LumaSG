package net.lumalyte.util;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import net.lumalyte.LumaSG;
import net.lumalyte.chest.ChestItem;
import net.lumalyte.chest.ChestManager;

/**
 * Pre-generated loot table caching system for optimal chest filling performance.
 * Eliminates runtime loot generation by pre-computing chest contents.
 */
public class LootTableCache {
    
    /**
     * Represents a pre-generated chest configuration
     */
    public static class PreGeneratedChest {
        private final List<ItemStack> items;
        private final List<Integer> slots;
        
        public PreGeneratedChest(@NotNull List<ItemStack> items, @NotNull List<Integer> slots) {
            this.items = new ArrayList<>(items);
            this.slots = new ArrayList<>(slots);
        }
        
        @NotNull
        public List<ItemStack> getItems() {
            return items;
        }
        
        @NotNull
        public List<Integer> getSlots() {
            return slots;
        }
        
        public int getItemCount() {
            return items.size();
        }
    }
    
    private static final Cache<String, List<PreGeneratedChest>> LOOT_TABLE_CACHE = Caffeine.newBuilder()
            .maximumSize(100)  // Cache for different tier configurations
            .expireAfterWrite(Duration.ofMinutes(30))
            .recordStats()
            .build();
    
    private static final ConcurrentHashMap<String, AtomicInteger> GENERATION_COUNTERS = new ConcurrentHashMap<>();
    
    private static final ScheduledExecutorService GENERATION_EXECUTOR = 
            Executors.newScheduledThreadPool(2, r -> {
                Thread t = new Thread(r, "LootGen-" + System.currentTimeMillis());
                t.setDaemon(true);
                return t;
            });
    
    private static DebugLogger.ContextualLogger logger;
    private static LumaSG pluginInstance;
    private static ChestManager chestManager;
    
    // Configuration constants
    private static final int PREGENERATED_CHESTS_PER_TIER = 50; // Number of pre-generated chests per tier
    private static final int MIN_ITEMS_PER_CHEST = 3;
    private static final int MAX_ITEMS_PER_CHEST = 7;
    private static final int CHEST_SIZE = 27; // Standard chest inventory size
    
    /**
     * Initializes the loot table cache
     * 
     * @param plugin The plugin instance
     * @param chestMgr The chest manager instance
     */
    public static void initialize(@NotNull LumaSG plugin, @NotNull ChestManager chestMgr) {
        pluginInstance = plugin;
        chestManager = chestMgr;
        logger = plugin.getDebugLogger().forContext("LootTableCache");
        
        // Schedule periodic regeneration of loot tables
        GENERATION_EXECUTOR.scheduleAtFixedRate(() -> {
            try {
                regenerateLootTables();
            } catch (Exception e) {
                if (logger != null) {
                    logger.error("Error during loot table regeneration", e);
                }
            }
        }, 15, 15, TimeUnit.MINUTES);
        
        logger.info("LootTableCache initialized - will pre-generate " + PREGENERATED_CHESTS_PER_TIER + " chests per tier");
    }
    
    /**
     * Pre-generates loot tables for all tiers
     * 
     * @return CompletableFuture that completes when generation is done
     */
    public static CompletableFuture<Void> preGenerateLootTables() {
        return CompletableFuture.runAsync(() -> {
            // Get available tiers dynamically from configuration
            Set<String> availableTiers = chestManager.getTiers();
            
            for (String tier : availableTiers) {
                try {
                    generateLootTableForTier(tier);
                } catch (Exception e) {
                    if (logger != null) {
                        logger.error("Error generating loot table for tier: " + tier, e);
                    }
                }
            }
            
            if (logger != null) {
                logger.info("Pre-generated loot tables for " + availableTiers.size() + " tiers: " + availableTiers);
            }
        }, GENERATION_EXECUTOR);
    }
    
    /**
     * Generates a loot table for a specific tier
     * 
     * @param tier The tier to generate loot for
     */
    private static void generateLootTableForTier(@NotNull String tier) {
        List<ChestItem> tierItems = chestManager.getTierItems(tier);
        if (tierItems.isEmpty()) {
            if (logger != null) {
                logger.warn("No items found for tier: " + tier);
            }
            return;
        }
        
        List<PreGeneratedChest> preGeneratedChests = new ArrayList<>();
        
        for (int i = 0; i < PREGENERATED_CHESTS_PER_TIER; i++) {
            PreGeneratedChest chest = generateSingleChest(tierItems, tier);
            if (chest != null) {
                preGeneratedChests.add(chest);
            }
        }
        
        if (!preGeneratedChests.isEmpty()) {
            LOOT_TABLE_CACHE.put(tier, preGeneratedChests);
            GENERATION_COUNTERS.computeIfAbsent(tier, k -> new AtomicInteger(0)).set(0);
            
            if (logger != null) {
                logger.debug("Generated " + preGeneratedChests.size() + " chests for tier: " + tier);
            }
        }
    }
    
    /**
     * Generates a single pre-configured chest
     * 
     * @param tierItems Available items for the tier
     * @param tier The tier name for logging
     * @return Pre-generated chest or null if generation failed
     */
    @Nullable
    private static PreGeneratedChest generateSingleChest(@NotNull List<ChestItem> tierItems, @NotNull String tier) {
        int itemCount = generateRandomItemCount();
        List<Integer> availableSlots = generateShuffledSlots();
        
        ChestContents contents = populateChestContents(tierItems, itemCount, availableSlots);
        
        return contents.isEmpty() ? null : new PreGeneratedChest(contents.items, contents.slots);
    }
    
    /**
     * Generates a random number of items for a chest
     */
    private static int generateRandomItemCount() {
        return ThreadLocalRandom.current().nextInt(MIN_ITEMS_PER_CHEST, MAX_ITEMS_PER_CHEST + 1);
    }
    
    /**
     * Generates a shuffled list of available chest slots
     */
    private static List<Integer> generateShuffledSlots() {
        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < CHEST_SIZE; i++) {
            slots.add(i);
        }
        Collections.shuffle(slots);
        return slots;
    }
    
    /**
     * Populates chest contents with items and their slots
     */
    private static ChestContents populateChestContents(List<ChestItem> tierItems, int itemCount, List<Integer> availableSlots) {
        List<ItemStack> items = new ArrayList<>();
        List<Integer> selectedSlots = new ArrayList<>();
        
        for (int i = 0; i < Math.min(itemCount, availableSlots.size()); i++) {
            ItemStack itemStack = generateRandomItemStack(tierItems);
            if (itemStack != null) {
                items.add(itemStack);
                selectedSlots.add(availableSlots.get(i));
            }
        }
        
        return new ChestContents(items, selectedSlots);
    }
    
    /**
     * Generates a random ItemStack from the tier items
     */
    @Nullable
    private static ItemStack generateRandomItemStack(List<ChestItem> tierItems) {
        ChestItem randomItem = getRandomWeightedItem(tierItems);
        if (randomItem == null) {
            return null;
        }
        
        ItemStack itemStack = randomItem.getItemStack(pluginInstance);
        if (itemStack != null) {
            int amount = ThreadLocalRandom.current().nextInt(randomItem.getMinAmount(), randomItem.getMaxAmount() + 1);
            itemStack.setAmount(amount);
            return itemStack.clone();
        }
        
        return null;
    }
    
    /**
     * Helper class to hold chest contents during generation
     */
    private static class ChestContents {
        private final List<ItemStack> items;
        private final List<Integer> slots;
        
        ChestContents(List<ItemStack> items, List<Integer> slots) {
            this.items = items;
            this.slots = slots;
        }
        
        boolean isEmpty() {
            return items.isEmpty();
        }
    }
    
    /**
     * Gets a random weighted item from the tier items
     * 
     * @param tierItems Available items
     * @return Random item based on weight
     */
    @Nullable
    private static ChestItem getRandomWeightedItem(@NotNull List<ChestItem> tierItems) {
        if (tierItems.isEmpty()) {
            return null;
        }
        
        // Calculate total weight
        double totalWeight = tierItems.stream()
                .mapToDouble(ChestItem::getChance)
                .sum();
        
        if (totalWeight <= 0) {
            return tierItems.get(ThreadLocalRandom.current().nextInt(tierItems.size()));
        }
        
        // Weighted random selection
        double roll = ThreadLocalRandom.current().nextDouble(totalWeight);
        double currentWeight = 0.0;
        
        for (ChestItem item : tierItems) {
            currentWeight += item.getChance();
            if (roll <= currentWeight) {
                return item;
            }
        }
        
        // Fallback
        return tierItems.get(tierItems.size() - 1);
    }
    
    /**
     * Gets a pre-generated chest for a specific tier
     * 
     * @param tier The tier to get a chest for
     * @return Pre-generated chest or null if none available
     */
    @Nullable
    public static PreGeneratedChest getPreGeneratedChest(@NotNull String tier) {
        List<PreGeneratedChest> chests = LOOT_TABLE_CACHE.getIfPresent(tier);
        if (chests == null || chests.isEmpty()) {
            // Try to generate on-demand
            generateLootTableForTier(tier);
            chests = LOOT_TABLE_CACHE.getIfPresent(tier);
            
            if (chests == null || chests.isEmpty()) {
                return null;
            }
        }
        
        // Get next chest in round-robin fashion
        AtomicInteger counter = GENERATION_COUNTERS.computeIfAbsent(tier, k -> new AtomicInteger(0));
        int index = counter.getAndIncrement() % chests.size();
        
        return chests.get(index);
    }
    
    /**
     * Applies a pre-generated chest to an inventory
     * 
     * @param inventory The inventory to fill
     * @param chest The pre-generated chest
     * @return CompletableFuture that completes when the chest is filled
     */
    public static CompletableFuture<Boolean> applyPreGeneratedChest(@NotNull org.bukkit.inventory.Inventory inventory, 
                                                                   @NotNull PreGeneratedChest chest) {
        return CompletableFuture.supplyAsync(() -> {
            if (!validateChestData(chest)) {
                return false;
            }
            
            return applyChestToInventoryOnMainThread(inventory, chest);
        });
    }
    
    /**
     * Validates that chest data is consistent
     */
    private static boolean validateChestData(PreGeneratedChest chest) {
        List<ItemStack> items = chest.getItems();
        List<Integer> slots = chest.getSlots();
        
        if (items.size() != slots.size()) {
            if (logger != null) {
                logger.error("Mismatch between items and slots in pre-generated chest");
            }
            return false;
        }
        
        return true;
    }
    
    /**
     * Applies chest contents to inventory on the main thread
     */
    private static boolean applyChestToInventoryOnMainThread(org.bukkit.inventory.Inventory inventory, PreGeneratedChest chest) {
        CompletableFuture<Boolean> mainThreadApplication = new CompletableFuture<>();
        
        org.bukkit.Bukkit.getScheduler().runTask(pluginInstance, () -> {
            try {
                inventory.clear();
                fillInventoryWithChestContents(inventory, chest);
                mainThreadApplication.complete(true);
            } catch (Exception e) {
                if (logger != null) {
                    logger.error("Error applying pre-generated chest to inventory", e);
                }
                mainThreadApplication.complete(false);
            }
        });
        
        return waitForMainThreadCompletion(mainThreadApplication);
    }
    
    /**
     * Fills inventory with chest contents
     */
    private static void fillInventoryWithChestContents(org.bukkit.inventory.Inventory inventory, PreGeneratedChest chest) {
        List<ItemStack> items = chest.getItems();
        List<Integer> slots = chest.getSlots();
        
        for (int i = 0; i < items.size(); i++) {
            ItemStack item = items.get(i);
            int slot = slots.get(i);
            
            if (isValidSlot(slot, inventory)) {
                inventory.setItem(slot, item.clone());
            }
        }
    }
    
    /**
     * Checks if a slot is valid for the given inventory
     */
    private static boolean isValidSlot(int slot, org.bukkit.inventory.Inventory inventory) {
        return slot >= 0 && slot < inventory.getSize();
    }
    
    /**
     * Waits for main thread operation to complete with timeout
     */
    private static boolean waitForMainThreadCompletion(CompletableFuture<Boolean> future) {
        try {
            return future.get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            if (logger != null) {
                logger.error("Timeout applying pre-generated chest", e);
            }
            return false;
        }
    }
    
    /**
     * Regenerates all loot tables (called periodically)
     */
    private static void regenerateLootTables() {
        // Get available tiers dynamically from configuration
        Set<String> availableTiers = chestManager.getTiers();
        
        for (String tier : availableTiers) {
            // Only regenerate if cache is getting low
            List<PreGeneratedChest> existing = LOOT_TABLE_CACHE.getIfPresent(tier);
            if (existing == null || existing.size() < PREGENERATED_CHESTS_PER_TIER / 2) {
                generateLootTableForTier(tier);
            }
        }
    }
    
    /**
     * Gets cache statistics for monitoring
     * 
     * @return String containing cache statistics
     */
    public static String getCacheStats() {
        long totalChests = LOOT_TABLE_CACHE.asMap().values().stream()
                .mapToLong(List::size)
                .sum();
        
        return String.format(
                "LootTableCache - Cached Tiers: %d, Total Pre-generated Chests: %d, Hit Rate: %.2f%%",
                LOOT_TABLE_CACHE.estimatedSize(),
                totalChests,
                LOOT_TABLE_CACHE.stats().hitRate() * 100
        );
    }
    
    /**
     * Gets detailed statistics for all tiers
     * 
     * @return String containing detailed tier statistics
     */
    public static String getDetailedStats() {
        StringBuilder stats = new StringBuilder("Loot Table Statistics:\n");
        
        LOOT_TABLE_CACHE.asMap().forEach((tier, chests) -> {
            AtomicInteger counter = GENERATION_COUNTERS.get(tier);
            int usageCount = counter != null ? counter.get() : 0;
            
            stats.append(String.format("  %s: %d chests, %d used\n", 
                    tier, chests.size(), usageCount));
        });
        
        return stats.toString();
    }
    
    /**
     * Forces regeneration of loot tables for all tiers
     * 
     * @return CompletableFuture that completes when regeneration is done
     */
    public static CompletableFuture<Void> forceRegeneration() {
        return CompletableFuture.runAsync(() -> {
            LOOT_TABLE_CACHE.invalidateAll();
            GENERATION_COUNTERS.clear();
            preGenerateLootTables().join();
            
            if (logger != null) {
                logger.info("Forced regeneration of all loot tables completed");
            }
        }, GENERATION_EXECUTOR);
    }
    
    /**
     * Shuts down the loot table cache
     */
    public static void shutdown() {
        if (logger != null) {
            logger.info("Shutting down LootTableCache...");
        }
        
        // Clear caches
        LOOT_TABLE_CACHE.invalidateAll();
        GENERATION_COUNTERS.clear();
        
        // Shutdown executor
        GENERATION_EXECUTOR.shutdown();
        try {
            if (!GENERATION_EXECUTOR.awaitTermination(5, TimeUnit.SECONDS)) {
                GENERATION_EXECUTOR.shutdownNow();
            }
        } catch (InterruptedException e) {
            GENERATION_EXECUTOR.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        if (logger != null) {
            logger.info("LootTableCache shutdown complete");
        }
    }
} 