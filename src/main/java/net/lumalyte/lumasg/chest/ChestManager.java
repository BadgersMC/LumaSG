package net.lumalyte.lumasg.chest;

import net.lumalyte.lumasg.LumaSG;
import net.lumalyte.lumasg.exception.LumaSGException;
import net.lumalyte.lumasg.util.core.DebugLogger;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

/**
 * Manages chest loot generation and distribution in Survival Games.
 * 
 * <p>This class is responsible for populating chests with randomized loot
 * based on configured loot tables. It handles the generation of different
 * tiers of loot (common, uncommon, rare, etc.) and ensures balanced
 * distribution of items across the arena.</p>
 * 
 * <p>The ChestManager loads loot configurations from files and provides
 * methods to fill chests with appropriate loot based on their tier and
 * the current game state.</p>
 * 
 * <p>Thread-safe implementation using ThreadLocalRandom and proper synchronization.</p>
 * 
 * @author LumaLyte
 * @version 1.0
 * @since 1.0
 */
public class ChestManager {
    private final @NotNull LumaSG plugin;
    /** Debug logger instance for chest management */
    private final @NotNull DebugLogger.ContextualLogger logger;
    /** Thread-safe list for chest items */
    private final @NotNull List<ChestItem> chestItems;
    private final @NotNull File chestFile;
    private final @NotNull ChestConfiguration configuration;
    
    /** ReadWriteLock for chest items operations */
    private final ReadWriteLock itemsLock = new ReentrantReadWriteLock();
    
    /**
     * Constructs a new ChestManager instance.
     * 
     * @param plugin The plugin instance
     */
    public ChestManager(@NotNull LumaSG plugin) {
        this.plugin = plugin;
        this.logger = plugin.getDebugLogger().forContext("ChestManager");
        this.chestItems = new CopyOnWriteArrayList<>();
        this.chestFile = new File(plugin.getDataFolder(), "chest.yml");
        this.configuration = new ChestConfiguration(plugin, chestFile);
        
        // Save default chest configuration if it doesn't exist
        if (!chestFile.exists()) {
            plugin.saveResource("chest.yml", false);
        }
    }
    
    /**
     * Starts the chest manager.
     */
    public void start() {
        // Load chest items
        loadChestItems().thenRun(() -> 
            logger.info("Loaded " + chestItems.size() + " chest items."));
    }
    
    /**
     * Stops the chest manager.
     */
    public void stop() {
        itemsLock.writeLock().lock();
        try {
            chestItems.clear();
        } finally {
            itemsLock.writeLock().unlock();
        }
    }
    
    /**
     * Loads a single item from the configuration and adds it to the chest items list.
     * 
     * @param itemKey The key of the item in the configuration
     * @param itemSection The configuration section containing the item data
     */
    private void loadSingleItem(@NotNull String itemKey, @NotNull ConfigurationSection itemSection) {
        logger.debug("Processing item: " + itemKey);
        
        // Check if this item has tier-weights
        ConfigurationSection tierWeights = itemSection.getConfigurationSection("tier-weights");
        if (tierWeights == null) {
            logger.warn("Item " + itemKey + " has no tier-weights configuration - skipping");
            return;
        }
        
        // Create a ChestItem for each tier with appropriate weight
        for (String tier : tierWeights.getKeys(false)) {
            loadItemTier(itemKey, itemSection, tierWeights, tier);
        }
    }
    
    /**
     * Loads a specific tier variant of an item.
     * 
     * @param itemKey The key of the item
     * @param itemSection The item's configuration section
     * @param tierWeights The tier weights configuration section
     * @param tier The specific tier being loaded
     */
    private void loadItemTier(@NotNull String itemKey, @NotNull ConfigurationSection itemSection,
                            @NotNull ConfigurationSection tierWeights, @NotNull String tier) {
        double weight = tierWeights.getDouble(tier, 0.0);
        if (weight <= 0) {
            return;
        }
        
        logger.debug("Loading item: " + itemKey + " for tier: " + tier + " with weight: " + weight);
        
        YamlConfiguration enrichedSection = createEnrichedSection(itemSection, tier, weight);
        
        // Check if this is a Nexo item and handle it gracefully
        if (enrichedSection.contains("nexo-item") && !plugin.getHookManager().isHookAvailable("Nexo")) {
            logger.debug("Skipping Nexo item: " + itemKey + " because Nexo plugin is not available");
            return;
        }
        
        ChestItem item = ChestItem.fromConfig(plugin, enrichedSection, itemKey);
        if (item != null) {
            chestItems.add(item);
            logger.debug("Successfully loaded item: " + itemKey + " for tier: " + tier + " with weight: " + weight);
        } else {
            logger.warn("Failed to create item: " + itemKey + " for tier: " + tier);
        }
    }
    
    /**
     * Creates an enriched configuration section for an item tier.
     * 
     * @param itemSection Original item configuration
     * @param tier The tier being processed
     * @param weight The weight/chance for this tier
     * @return Enriched configuration section
     */
    private @NotNull YamlConfiguration createEnrichedSection(@NotNull ConfigurationSection itemSection,
                                                           @NotNull String tier, double weight) {
        YamlConfiguration enrichedSection = new YamlConfiguration();
        enrichedSection.set("tier", tier);
        enrichedSection.set("chance", weight);
        
        // Copy all item properties except tier-weights
        for (String propertyKey : itemSection.getKeys(true)) {
            if (!propertyKey.startsWith("tier-weights")) {
                enrichedSection.set(propertyKey, itemSection.get(propertyKey));
            }
        }
        
        return enrichedSection;
    }
    
    /**
     * Logs a summary of loaded items per tier.
     */
    private void logLoadingSummary() {
        Map<String, Integer> itemsPerTier = new HashMap<>();
        for (ChestItem item : chestItems) {
            itemsPerTier.merge(item.getTier(), 1, Integer::sum);
        }
        
        logger.info("Chest items loaded - Summary:");
        for (Map.Entry<String, Integer> entry : itemsPerTier.entrySet()) {
            logger.info("Tier " + entry.getKey() + ": " + entry.getValue() + " items");
        }
    }

    /**
     * Loads chest items from the configuration file.
     *
     * @return A future that completes when the items are loaded
     */
    public @NotNull CompletableFuture<Void> loadChestItems() {
        return CompletableFuture.runAsync(() -> {
            try {
                // Use write lock for clearing and adding items
                itemsLock.writeLock().lock();
                try {
                    // Clear existing items
                    chestItems.clear();
                    logger.debug("Loading chest items from configuration...");
                    
                    // Load items using the configuration class
                    List<ChestItem> loadedItems = configuration.loadItems();
                    chestItems.addAll(loadedItems);
                } finally {
                    itemsLock.writeLock().unlock();
                }
                
                // Log the number of items loaded per tier (using read lock)
                itemsLock.readLock().lock();
                try {
                    logLoadingSummary();
                } finally {
                    itemsLock.readLock().unlock();
                }
                
            } catch (Exception e) {
                logger.severe("Failed to load chest items", e);
            }
        });
    }
    
    /**
     * Fills a chest at the specified location with randomized loot.
     * 
     * <p>This method determines the appropriate loot tier for the chest
     * and populates it with random items from that tier's loot table.
     * The chest is completely cleared before being filled with new loot.</p>
     * 
     * @param location The location of the chest to fill
     * @param tier The loot tier to use (e.g., "common", "uncommon", "rare")
     * 
     * @return true if the chest was successfully filled, false otherwise
     *
	 */
    public boolean fillChest(@NotNull Location location, @NotNull String tier) throws LumaSGException.ChestException {
        if (tier.trim().isEmpty()) {
            throw LumaSGException.chestError("Tier cannot be null or empty", location.toString());
        }
        
        Block block = location.getBlock();
        if (!isChest(block)) {
            throw LumaSGException.chestError("Block at location is not a chest", location.toString());
        }
        
        BlockState state = block.getState();
        if (!(state instanceof Chest chest)) {
            throw LumaSGException.chestError("Failed to get chest state", location.toString());
        }
        
        Inventory inventory = chest.getInventory();
        inventory.clear();
        
        logger.debug("Filling chest at " + location.toString() + " with tier: " + tier);
        
        // Get loot for this tier
        List<ChestItem> loot = getLootForTier(tier);
        logger.debug("Got " + loot.size() + " possible items for tier: " + tier);
        
        // Fill chest with random items
        int itemCount = ThreadLocalRandom.current().nextInt(4, 7); // 4-6 items per chest
        int filledSlots = 0;
        int attempts = 0;
        int maxAttempts = 50; // Prevent infinite loops
        
        logger.debug("Attempting to fill chest with " + itemCount + " items");
        
        while (filledSlots < itemCount && attempts < maxAttempts) {
            attempts++;
            
            // Get a random item from the loot pool
            ChestItem selectedItem = getRandomItem(loot);
            if (selectedItem == null) {
                logger.debug("Failed to select an item from loot pool (attempt " + attempts + ")");
                continue;
            }
            
            // Get the actual item stack
            ItemStack itemStack = selectedItem.getItemStack(plugin);
            if (itemStack == null) {
                logger.debug("Selected item returned null ItemStack (attempt " + attempts + ")");
                continue;
            }
            
            // Randomize amount within configured range
            int amount = selectedItem.getMinAmount();
            if (selectedItem.getMaxAmount() > selectedItem.getMinAmount()) {
                amount += ThreadLocalRandom.current().nextInt(selectedItem.getMaxAmount() - selectedItem.getMinAmount() + 1);
            }
            itemStack.setAmount(amount);
            
            // Find a random empty slot
            int slot = getRandomEmptySlot(inventory);
            if (slot == -1) {
                logger.debug("No empty slots found in chest (attempt " + attempts + ")");
                break;
            }
            
            // Add the item to the chest
            inventory.setItem(slot, itemStack);
            filledSlots++;
            
            logger.debug("Added item to chest - Material: " + itemStack.getType() + 
                ", Amount: " + itemStack.getAmount() + 
                ", Slot: " + slot + 
                " (attempt " + attempts + ")");
        }
        
        logger.debug("Chest filling complete - Added " + filledSlots + " items in " + attempts + " attempts");
        return filledSlots > 0;
    }

    /**
     * Fills a chest with loot from a random tier.
     *
     * <p>This method randomly selects a loot tier and fills the chest with
     * items from that tier. The selection is weighted based on tier rarity
     * if configured.</p>
     *
     * @param location The location of the chest to fill
     */
    public void fillChest(@NotNull Location location) {
        if (chestItems.isEmpty()) {
            logger.warn("No chest items available for chest filling");
            return;
        }
        
        // Select a random tier from available items
        Set<String> availableTiers = getTiers();
        if (availableTiers.isEmpty()) {
            logger.warn("No tiers available for chest filling");
            return;
        }
        
        String[] tierArray = availableTiers.toArray(new String[0]);
        String tier = tierArray[ThreadLocalRandom.current().nextInt(tierArray.length)];
        try {
            fillChest(location, tier);
        } catch (LumaSGException.ChestException e) {
            logger.severe("Failed to fill chest at " + location + " with tier " + tier, e);
        }
    }

    /**
     * Gets the loot table for a specific tier.
     * 
     * <p>This method returns all chest items that belong to the specified tier.</p>
     * 
     * @param tier The loot tier to get items for
     * @return A list of ChestItem objects for the tier, or empty list if not found
     */
    private @NotNull List<ChestItem> getLootForTier(@NotNull String tier) {
        itemsLock.readLock().lock();
        try {
            List<ChestItem> loot = new ArrayList<>(chestItems.stream()
                .filter(item -> tier.equalsIgnoreCase(item.getTier()))
                .collect(Collectors.toList()));
            
            logger.debug("Found " + loot.size() + " regular items for tier: " + tier);
            
            // Add custom items for this tier
            if (plugin.getCustomItemsManager() != null) {
                List<ChestItem> customItems = plugin.getCustomItemsManager().getChestItemsForTier(tier);
                logger.debug("Found " + customItems.size() + " custom items for tier: " + tier);
                
                for (ChestItem item : customItems) {
                    logger.debug("Adding custom item to loot pool - Material: " + 
                        (item.getItemStack(plugin) != null ? item.getItemStack(plugin).getType() : "null") + 
                        ", Chance: " + item.getChance() + 
                        ", Amount: " + item.getMinAmount() + "-" + item.getMaxAmount());
                }
                
                loot.addAll(customItems);
                logger.debug("Total items in loot pool for tier " + tier + ": " + loot.size());
            } else {
                logger.warn("CustomItemsManager is null - custom items will not be added to loot pool");
            }
            
            return loot;
        } finally {
            itemsLock.readLock().unlock();
        }
    }

    /**
     * Selects a random item from a list of loot items.
     * 
     * <p>This method uses the chance/rarity system to select items. Items
     * with higher chances have a greater probability of being selected.</p>
     * 
     * @param loot The list of loot items to choose from
     * @return A randomly selected ChestItem, or null if the list is empty
     */
    private @Nullable ChestItem getRandomItem(@NotNull List<ChestItem> loot) {
        if (loot.isEmpty()) {
            return null;
        }
        
        ThreadLocalRandom random = ThreadLocalRandom.current();
        
        // Calculate total weight
        double totalWeight = loot.stream().mapToDouble(ChestItem::getChance).sum();
        if (totalWeight <= 0) {
            // If no weights are set, use uniform distribution
            return loot.get(random.nextInt(loot.size()));
        }
        
        // Weighted random selection
        double randomValue = random.nextDouble() * totalWeight;
        double currentWeight = 0;
        
        for (ChestItem item : loot) {
            currentWeight += item.getChance();
            if (randomValue <= currentWeight) {
                return item;
            }
        }
        
        // Fallback to last item if rounding errors occur
        return loot.getLast();
    }

    /**
     * Finds a random empty slot in the chest inventory.
     * 
     * @param inventory The chest inventory to search
     * @return The index of a random empty slot, or -1 if no empty slots
     */
    private int getRandomEmptySlot(@NotNull Inventory inventory) {
        List<Integer> emptySlots = new ArrayList<>();
        
        for (int i = 0; i < inventory.getSize(); i++) {
            if (inventory.getItem(i) == null) {
                emptySlots.add(i);
            }
        }
        
        if (emptySlots.isEmpty()) {
            return -1; // No empty slots
        }
        
        ThreadLocalRandom random = ThreadLocalRandom.current();
        return emptySlots.get(random.nextInt(emptySlots.size()));
    }

    /**
     * Checks if a block is a chest.
     *
     * @param block The block to check
     * @return True if the block is a chest, false otherwise
     */
    public boolean isChest(@NotNull Block block) {
        return block.getType() == Material.CHEST || block.getType() == Material.TRAPPED_CHEST;
    }
    
    /**
     * Gets a list of all chest items.
     * 
     * <p>This method returns an unmodifiable view of the internal chest items list
     * to prevent external modification of the collection.</p>
     * 
     * @return An unmodifiable list of all chest items
     */
    public @NotNull List<ChestItem> getChestItems() {
        return Collections.unmodifiableList(chestItems);
    }
    
    /**
     * Gets a set of all available loot tiers.
     * 
     * <p>This method scans all loaded chest items and extracts the unique
     * tier names, providing a complete set of available tiers.</p>
     * 
     * @return A set of all tier names
     */
    public @NotNull Set<String> getTiers() {
        Set<String> tiers = new HashSet<>();
        for (ChestItem item : chestItems) {
            String tier = item.getTier();
            if (tier != null && !tier.isEmpty()) {
                tiers.add(tier);
            }
        }
        return Collections.unmodifiableSet(tiers);
    }
    
    /**
     * Gets all chest items belonging to a specific tier.
     * 
     * <p>This method filters the loaded chest items by the specified tier
     * and returns only those that match. If no items match the tier,
     * an empty list is returned.</p>
     * 
     * @param tier The tier to filter by
     * @return A list of chest items belonging to the specified tier
     */
    public @NotNull List<ChestItem> getTierItems(String tier) {
        if (tier == null || tier.isEmpty()) {
            return Collections.emptyList();
        }
        
        List<ChestItem> tierItems = new ArrayList<>();
        for (ChestItem item : chestItems) {
            if (tier.equalsIgnoreCase(item.getTier())) {
                tierItems.add(item);
            }
        }
        
        return tierItems;
    }
    
    /**
     * Gets a random item from a tier.
     * 
     * @param tier The tier
     * @return The item stack, or null if no item was selected
     */
    public @Nullable ItemStack getRandomItem(String tier) {
        List<ChestItem> items = getTierItems(tier);
        if (items.isEmpty()) {
            return null;
        }
        
        // Calculate total chance
        double totalChance = 0;
        for (ChestItem item : items) {
            totalChance += item.getChance();
        }
        
        // Select a random item based on chance
        double randomValue = ThreadLocalRandom.current().nextDouble() * totalChance;
        double currentChance = 0;
        
        for (ChestItem item : items) {
            currentChance += item.getChance();
            if (randomValue <= currentChance) {
                ItemStack itemStack = item.getItemStack(plugin);
                if (itemStack != null) {
                    // Randomize amount
                    int amount = item.getMinAmount();
                    if (item.getMaxAmount() > item.getMinAmount()) {
                        amount += ThreadLocalRandom.current().nextInt(item.getMaxAmount() - item.getMinAmount() + 1);
                    }
                    itemStack.setAmount(amount);
                    return itemStack;
                }
            }
        }
        
        return null;
    }
    
    /**
     * Gets a list of random items from a specific tier.
     *
     * @param tier The tier to get items from
     * @param count The number of items to get
     * @return A list of random items
     */
    public @NotNull List<ItemStack> getRandomItems(@NotNull String tier, int count) {
        List<ItemStack> items = new ArrayList<>();
        List<ChestItem> tierItems = getTierItems(tier);
        
        if (tierItems.isEmpty()) {
            logger.warn("No items found for tier: " + tier);
            return items;
        }
        
        for (int i = 0; i < count; i++) {
            ChestItem chestItem = getRandomItem(tierItems);
            if (chestItem != null) {
                ItemStack item = chestItem.getItemStack(plugin);
                if (item != null) {
                    // Set random amount between min and max
                    int minAmount = chestItem.getMinAmount();
                    int maxAmount = chestItem.getMaxAmount();
                    int amount = minAmount;
                    if (maxAmount > minAmount) {
                        amount = ThreadLocalRandom.current().nextInt(maxAmount - minAmount + 1) + minAmount;
                    }
                    item.setAmount(amount);
                    items.add(item);
                }
            }
        }
        
        return items;
    }
} 
