package net.lumalyte.customitems;

import net.lumalyte.LumaSG;
import net.lumalyte.chest.ChestItem;
import net.lumalyte.util.DebugLogger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manager class for custom items system.
 * 
 * <p>This class handles loading custom items from configuration, managing
 * their behaviors, and integrating them with the game's loot systems.
 * Custom items are loaded from the custom-items.yml configuration file.</p>
 * 
 * <p>The manager provides methods for creating custom items, checking if
 * items are custom items, and integrating with chest loot and other systems.</p>
 * 
 * @author LumaLyte
 * @version 1.0
 * @since 1.0
 */
public class CustomItemsManager {
    
    private final LumaSG plugin;
    private final DebugLogger.ContextualLogger logger;
    private final Map<String, CustomItem> customItems;
    private final NamespacedKey customItemKey;
    private final NamespacedKey customIdKey;
    
    private FileConfiguration config;
    private boolean enabled;
    private boolean cleanupOnGameEnd;
    private boolean trackUsage;
    private int maxItemsPerGame;
    private int cleanupInterval;
    private boolean asyncProcessing;
    
    /**
     * Creates a new custom items manager.
     * 
     * @param plugin The plugin instance
     */
    public CustomItemsManager(@NotNull LumaSG plugin) {
        this.plugin = plugin;
        this.logger = plugin.getDebugLogger().forContext("CustomItemsManager");
        this.customItems = new ConcurrentHashMap<>();
        this.customItemKey = new NamespacedKey(plugin, "custom_item");
        this.customIdKey = new NamespacedKey(plugin, "custom_id");
    }
    
    /**
     * Initializes the custom items manager.
     * 
     * @return True if initialization was successful
     */
    public boolean initialize() {
        try {
            logger.info("Initializing Custom Items Manager...");
            
            // Load configuration
            if (!loadConfiguration()) {
                logger.severe("Failed to load custom items configuration");
                return false;
            }
            
            // Load settings
            loadSettings();
            
            if (!enabled) {
                logger.info("Custom items system is disabled");
                return true;
            }
            
            // Load custom items
            loadCustomItems();
            
            logger.info("Custom Items Manager initialized successfully with " + customItems.size() + " items");
            return true;
            
        } catch (Exception e) {
            logger.severe("Failed to initialize Custom Items Manager", e);
            return false;
        }
    }
    
    /**
     * Loads the custom items configuration file.
     */
    private boolean loadConfiguration() {
        try {
            File configFile = new File(plugin.getDataFolder(), "custom-items.yml");
            
            // Create config file if it doesn't exist
            if (!configFile.exists()) {
                plugin.getDataFolder().mkdirs();
                
                try (InputStream inputStream = plugin.getResource("custom-items.yml")) {
                    if (inputStream != null) {
                        Files.copy(inputStream, configFile.toPath());
                        logger.info("Created default custom-items.yml configuration file");
                    } else {
                        logger.warn("Could not find default custom-items.yml in plugin resources");
                        return false;
                    }
                }
            }
            
            config = YamlConfiguration.loadConfiguration(configFile);
            return true;
            
        } catch (IOException e) {
            logger.severe("Failed to load custom-items.yml configuration", e);
            return false;
        }
    }
    
    /**
     * Loads settings from the configuration.
     */
    private void loadSettings() {
        ConfigurationSection settingsSection = config.getConfigurationSection("settings");
        if (settingsSection != null) {
            enabled = settingsSection.getBoolean("enabled", true);
            cleanupOnGameEnd = settingsSection.getBoolean("cleanup-on-game-end", true);
            trackUsage = settingsSection.getBoolean("track-usage", true);
        } else {
            enabled = true;
            cleanupOnGameEnd = true;
            trackUsage = true;
        }
        
        ConfigurationSection performanceSection = config.getConfigurationSection("performance");
        if (performanceSection != null) {
            maxItemsPerGame = performanceSection.getInt("max-items-per-game", 50);
            cleanupInterval = performanceSection.getInt("cleanup-interval", 200);
            asyncProcessing = performanceSection.getBoolean("async-processing", true);
        } else {
            maxItemsPerGame = 50;
            cleanupInterval = 200;
            asyncProcessing = true;
        }
        
        logger.debug("Loaded settings - Enabled: " + enabled + ", Cleanup: " + cleanupOnGameEnd + ", Track: " + trackUsage);
    }
    
    /**
     * Loads all custom items from the configuration.
     */
    private void loadCustomItems() {
        ConfigurationSection itemsSection = config.getConfigurationSection("items");
        if (itemsSection == null) {
            logger.warn("No items section found in custom-items.yml");
            return;
        }
        
        int loadedCount = 0;
        int failedCount = 0;
        
        for (String itemKey : itemsSection.getKeys(false)) {
            ConfigurationSection itemSection = itemsSection.getConfigurationSection(itemKey);
            if (itemSection != null) {
                CustomItem customItem = CustomItem.fromConfig(plugin, itemSection, itemKey);
                if (customItem != null) {
                    customItems.put(itemKey, customItem);
                    loadedCount++;
                    logger.debug("Loaded custom item: " + itemKey);
                } else {
                    failedCount++;
                    logger.warn("Failed to load custom item: " + itemKey);
                }
            }
        }
        
        logger.info("Loaded " + loadedCount + " custom items (" + failedCount + " failed)");
    }
    
    /**
     * Gets a custom item by its ID.
     * 
     * @param id The custom item ID
     * @return The custom item, or null if not found
     */
    public @Nullable CustomItem getCustomItem(@NotNull String id) {
        return customItems.get(id);
    }
    
    /**
     * Gets all registered custom items.
     * 
     * @return A collection of all custom items
     */
    public @NotNull Collection<CustomItem> getAllCustomItems() {
        return Collections.unmodifiableCollection(customItems.values());
    }
    
    /**
     * Gets custom items by category.
     * 
     * @param category The category to filter by
     * @return A collection of custom items in the specified category
     */
    public @NotNull Collection<CustomItem> getCustomItemsByCategory(@NotNull String category) {
        return customItems.values().stream()
                .filter(item -> item.getCategory().equals(category))
                .toList();
    }
    
    /**
     * Gets custom items by behavior type.
     * 
     * @param behavior The behavior type to filter by
     * @return A collection of custom items with the specified behavior
     */
    public @NotNull Collection<CustomItem> getCustomItemsByBehavior(@NotNull CustomItemBehavior behavior) {
        return customItems.values().stream()
                .filter(item -> item.hasBehavior(behavior))
                .toList();
    }
    
    /**
     * Creates an item stack for a custom item.
     * 
     * @param customItemId The custom item ID
     * @return The item stack, or null if the custom item doesn't exist
     */
    public @Nullable ItemStack createCustomItemStack(@NotNull String customItemId) {
        CustomItem customItem = getCustomItem(customItemId);
        if (customItem == null) {
            return null;
        }
        
        ItemStack itemStack = customItem.createItemStack();
        markAsCustomItem(itemStack, customItemId);
        return itemStack;
    }
    
    /**
     * Creates an item stack for a custom item with a specific amount.
     * 
     * @param customItemId The custom item ID
     * @param amount The amount for the item stack
     * @return The item stack, or null if the custom item doesn't exist
     */
    public @Nullable ItemStack createCustomItemStack(@NotNull String customItemId, int amount) {
        CustomItem customItem = getCustomItem(customItemId);
        if (customItem == null) {
            return null;
        }
        
        ItemStack itemStack = customItem.createItemStack(amount);
        markAsCustomItem(itemStack, customItemId);
        return itemStack;
    }
    
    /**
     * Marks an item stack as a custom item.
     * 
     * @param itemStack The item stack to mark
     * @param customItemId The custom item ID
     */
    private void markAsCustomItem(@NotNull ItemStack itemStack, @NotNull String customItemId) {
        if (itemStack.getItemMeta() != null) {
            var meta = itemStack.getItemMeta();
            meta.getPersistentDataContainer().set(customItemKey, PersistentDataType.BOOLEAN, true);
            meta.getPersistentDataContainer().set(customIdKey, PersistentDataType.STRING, customItemId);
            itemStack.setItemMeta(meta);
        }
    }
    
    /**
     * Checks if an item stack is a custom item.
     * 
     * @param itemStack The item stack to check
     * @return True if the item is a custom item
     */
    public boolean isCustomItem(@Nullable ItemStack itemStack) {
        if (itemStack == null || itemStack.getItemMeta() == null) {
            return false;
        }
        
        return itemStack.getItemMeta().getPersistentDataContainer()
                .has(customItemKey, PersistentDataType.BOOLEAN);
    }
    
    /**
     * Gets the custom item ID from an item stack.
     * 
     * @param itemStack The item stack to check
     * @return The custom item ID, or null if not a custom item
     */
    public @Nullable String getCustomItemId(@Nullable ItemStack itemStack) {
        if (!isCustomItem(itemStack)) {
            return null;
        }
        
        return itemStack.getItemMeta().getPersistentDataContainer()
                .get(customIdKey, PersistentDataType.STRING);
    }
    
    /**
     * Gets the custom item from an item stack.
     * 
     * @param itemStack The item stack to check
     * @return The custom item, or null if not a custom item or not found
     */
    public @Nullable CustomItem getCustomItemFromStack(@Nullable ItemStack itemStack) {
        String customItemId = getCustomItemId(itemStack);
        if (customItemId == null) {
            return null;
        }
        
        return getCustomItem(customItemId);
    }
    
    /**
     * Converts custom items to chest items for loot integration.
     * 
     * @param tier The loot tier to get items for
     * @return A list of chest items for the specified tier
     */
    public @NotNull List<ChestItem> getChestItemsForTier(@NotNull String tier) {
        List<ChestItem> chestItems = new ArrayList<>();
        
        for (CustomItem customItem : customItems.values()) {
            CustomItem.LootSettings lootSettings = customItem.getLootSettings();
            double weight = lootSettings.getChanceForTier(tier);
            
            if (weight > 0.0) {
                // Create a ChestItem from the CustomItem
                ItemStack itemStack = customItem.createItemStack();
                markAsCustomItem(itemStack, customItem.getId());
                
                ChestItem chestItem = new ChestItem(
                    itemStack,
                    lootSettings.getMinAmount(),
                    lootSettings.getMaxAmount(),
                    weight,
                    tier
                );
                
                chestItems.add(chestItem);
                logger.debug("Added custom item '" + customItem.getId() + "' to tier '" + tier + "' loot table with weight " + weight);
            }
        }
        
        return chestItems;
    }
    
    /**
     * Reloads the custom items configuration.
     * 
     * @return True if reload was successful
     */
    public boolean reload() {
        logger.info("Reloading custom items configuration...");
        
        // Clear existing items
        customItems.clear();
        
        // Reload configuration and items
        if (!loadConfiguration()) {
            logger.severe("Failed to reload custom items configuration");
            return false;
        }
        
        loadSettings();
        
        if (enabled) {
            loadCustomItems();
        }
        
        logger.info("Custom items configuration reloaded successfully");
        return true;
    }
    
    /**
     * Checks if the custom items system is enabled.
     * 
     * @return True if enabled
     */
    public boolean isEnabled() {
        return enabled;
    }
    
    /**
     * Checks if cleanup on game end is enabled.
     * 
     * @return True if cleanup is enabled
     */
    public boolean isCleanupOnGameEnd() {
        return cleanupOnGameEnd;
    }
    
    /**
     * Checks if usage tracking is enabled.
     * 
     * @return True if tracking is enabled
     */
    public boolean isTrackUsage() {
        return trackUsage;
    }
    
    /**
     * Gets the maximum number of custom items per game.
     * 
     * @return The maximum items per game
     */
    public int getMaxItemsPerGame() {
        return maxItemsPerGame;
    }
    
    /**
     * Gets the cleanup interval in ticks.
     * 
     * @return The cleanup interval
     */
    public int getCleanupInterval() {
        return cleanupInterval;
    }
    
    /**
     * Checks if async processing is enabled.
     * 
     * @return True if async processing is enabled
     */
    public boolean isAsyncProcessing() {
        return asyncProcessing;
    }
} 