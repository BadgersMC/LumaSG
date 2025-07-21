package net.lumalyte.lumasg.customitems;

import net.lumalyte.lumasg.LumaSG;
import net.lumalyte.lumasg.util.core.DebugLogger;
import net.lumalyte.lumasg.util.core.ItemUtils;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashMap;

/**
 * Represents a custom item with special behaviors in Survival Games.
 * 
 * <p>CustomItems are special items that have unique functionality beyond
 * regular Minecraft items. They can have custom behaviors, tracking abilities,
 * special effects, and integration with the game's loot systems.</p>
 * 
 * <p>Each custom item is defined in the custom-items.yml configuration file
 * and can be integrated into chest loot, fishing rewards, or other game systems.</p>
 * 
 * @author LumaLyte
 * @version 1.0
 * @since 1.0
 */
public class CustomItem {
    
    /** The unique identifier for this custom item */
    private final String id;
    
    /** The item stack template for this custom item */
    private final ItemStack itemStack;
    
    /** The behavior type of this custom item */
    private final CustomItemBehavior behaviorType;
    
    /** The behavior configuration for this custom item */
    private final ConfigurationSection behaviorConfig;
    
    /** The loot integration settings */
    private final LootSettings lootSettings;
    
    /** The category this item belongs to */
    private final String category;
    
    /**
     * Creates a new custom item.
     * 
     * @param id The unique identifier for this custom item
     * @param itemStack The item stack template
     * @param behaviorType The behavior type
     * @param behaviorConfig The behavior configuration
     * @param lootSettings The loot integration settings
     * @param category The category this item belongs to
     */
    public CustomItem(@NotNull String id, @NotNull ItemStack itemStack, @NotNull CustomItemBehavior behaviorType,
                     @Nullable ConfigurationSection behaviorConfig, @NotNull LootSettings lootSettings, @NotNull String category) {
        this.id = id;
        this.itemStack = itemStack.clone();
        this.behaviorType = behaviorType;
        this.behaviorConfig = behaviorConfig;
        this.lootSettings = lootSettings;
        this.category = category;
    }
    
    /**
     * Creates a CustomItem from a configuration section.
     * 
     * @param plugin The plugin instance
     * @param section The configuration section
     * @param itemKey The key of the item in the configuration
     * @return The created CustomItem, or null if creation failed
     */
    public static @Nullable CustomItem fromConfig(@NotNull LumaSG plugin, @NotNull ConfigurationSection section, @NotNull String itemKey) {
        DebugLogger.ContextualLogger logger = plugin.getDebugLogger().forContext("CustomItem");

		try {
            // Create the base item stack using ItemUtils
            ItemStack itemStack = ItemUtils.createItemFromConfig(plugin, section, itemKey);
            if (itemStack == null) {
                logger.warn("Failed to create item stack for custom item: " + itemKey);
                return null;
            }
            
            // Get behavior configuration
            ConfigurationSection behaviorSection = section.getConfigurationSection("behavior");
            CustomItemBehavior behaviorType = CustomItemBehavior.NONE;
            
            if (behaviorSection != null) {
                String behaviorTypeStr = behaviorSection.getString("type", "none");
                try {
                    behaviorType = CustomItemBehavior.valueOf(behaviorTypeStr.toUpperCase());
                } catch (IllegalArgumentException e) {
                    logger.warn("Invalid behavior type '" + behaviorTypeStr + "' for custom item: " + itemKey);
				}
            }
            
            // Get loot settings
            LootSettings lootSettings = LootSettings.fromConfig(section.getConfigurationSection("loot"));
            
            // Get category from persistent data or default to "custom"
            String category = "custom";
            ConfigurationSection persistentDataSection = section.getConfigurationSection("persistent-data");
            if (persistentDataSection != null) {
                category = persistentDataSection.getString("category", "custom");
            }
            
            logger.debug("Successfully created custom item: " + itemKey + " with behavior " + behaviorType);
            return new CustomItem(itemKey, itemStack, behaviorType, behaviorSection, lootSettings, category);
            
        } catch (Exception e) {
            logger.severe("Error creating custom item: " + itemKey, e);
            return null;
        }
    }
    
    /**
     * Creates a new item stack instance for this custom item.
     * 
     * @return A new item stack instance
     */
    public @NotNull ItemStack createItemStack() {
        return itemStack.clone();
    }
    
    /**
     * Creates a new item stack instance with a specific amount.
     * 
     * @param amount The amount for the item stack
     * @return A new item stack instance with the specified amount
     */
    public @NotNull ItemStack createItemStack(int amount) {
        ItemStack item = itemStack.clone();
        item.setAmount(Math.max(1, Math.min(amount, item.getMaxStackSize())));
        return item;
    }
    
    /**
     * Gets the unique identifier for this custom item.
     * 
     * @return The item ID
     */
    public @NotNull String getId() {
        return id;
    }
    
    /**
     * Gets the behavior type of this custom item.
     * 
     * @return The behavior type
     */
    public @NotNull CustomItemBehavior getBehaviorType() {
        return behaviorType;
    }
    
    /**
     * Gets the behavior configuration for this custom item.
     * 
     * @return The behavior configuration, or null if none
     */
    public @Nullable ConfigurationSection getBehaviorConfig() {
        return behaviorConfig;
    }
    
    /**
     * Gets the loot integration settings.
     * 
     * @return The loot settings
     */
    public @NotNull LootSettings getLootSettings() {
        return lootSettings;
    }
    
    /**
     * Gets the category this item belongs to.
     * 
     * @return The item category
     */
    public @NotNull String getCategory() {
        return category;
    }
    
    /**
     * Checks if this custom item has a specific behavior.
     * 
     * @param behavior The behavior to check for
     * @return True if this item has the specified behavior
     */
    public boolean hasBehavior(@NotNull CustomItemBehavior behavior) {
        return behaviorType == behavior;
    }
    
    /**
     * Gets a behavior configuration value as an integer.
     * 
     * @param path The configuration path
     * @param defaultValue The default value if not found
     * @return The configuration value
     */
    public int getBehaviorInt(@NotNull String path, int defaultValue) {
        if (behaviorConfig == null) {
            return defaultValue;
        }
        return behaviorConfig.getInt(path, defaultValue);
    }
    
    /**
     * Gets a behavior configuration value as a double.
     * 
     * @param path The configuration path
     * @param defaultValue The default value if not found
     * @return The configuration value
     */
    public double getBehaviorDouble(@NotNull String path, double defaultValue) {
        if (behaviorConfig == null) {
            return defaultValue;
        }
        return behaviorConfig.getDouble(path, defaultValue);
    }
    
    /**
     * Gets a behavior configuration value as a boolean.
     * 
     * @param path The configuration path
     * @param defaultValue The default value if not found
     * @return The configuration value
     */
    public boolean getBehaviorBoolean(@NotNull String path, boolean defaultValue) {
        if (behaviorConfig == null) {
            return defaultValue;
        }
        return behaviorConfig.getBoolean(path, defaultValue);
    }
    
    /**
     * Gets a behavior configuration value as a string.
     * 
     * @param path The configuration path
     * @param defaultValue The default value if not found
     * @return The configuration value
     */
    public @NotNull String getBehaviorString(@NotNull String path, @NotNull String defaultValue) {
        if (behaviorConfig == null) {
            return defaultValue;
        }
        return behaviorConfig.getString(path, defaultValue);
    }
    
    /**
     * Represents loot integration settings for a custom item.
     */
    public static class LootSettings {
        private final Map<String, Double> tierWeights;
        private final int minAmount;
        private final int maxAmount;
        
        /**
         * Creates new loot settings.
         * 
         * @param tierWeights Map of tier names to their weights
         * @param minAmount The minimum amount that can spawn
         * @param maxAmount The maximum amount that can spawn
         */
        public LootSettings(@NotNull Map<String, Double> tierWeights, int minAmount, int maxAmount) {
            this.tierWeights = new HashMap<>(tierWeights);
            this.minAmount = minAmount;
            this.maxAmount = maxAmount;
        }
        
        /**
         * Creates loot settings from a configuration section.
         * 
         * @param section The configuration section
         * @return The loot settings, or default settings if section is null
         */
        public static @NotNull LootSettings fromConfig(@Nullable ConfigurationSection section) {
            if (section == null) {
                return new LootSettings(Map.of("common", 1.0), 1, 1);
            }
            
            Map<String, Double> tierWeights = new HashMap<>();
            
            // Check for new tier-weights format
            ConfigurationSection tierWeightsSection = section.getConfigurationSection("tier-weights");
            if (tierWeightsSection != null) {
                for (String tier : tierWeightsSection.getKeys(false)) {
                    double weight = tierWeightsSection.getDouble(tier, 0.0);
                    if (weight > 0.0) {
                        tierWeights.put(tier, weight);
                    }
                }
            }
            
            // Fallback to old format if no tier weights found
            if (tierWeights.isEmpty()) {
                List<String> tiers = section.getStringList("tiers");
                double chance = section.getDouble("chance", 1.0);
                
                if (tiers.isEmpty()) {
                    tiers = List.of("common");
                }
                
                for (String tier : tiers) {
                    tierWeights.put(tier, chance);
                }
            }
            
            int minAmount = section.getInt("min-amount", 1);
            int maxAmount = section.getInt("max-amount", 1);
            
            return new LootSettings(tierWeights, minAmount, maxAmount);
        }
        
        /**
         * Gets the loot tiers this item can appear in.
         * 
         * @return The loot tiers
         */
        public @NotNull Set<String> getTiers() {
            return tierWeights.keySet();
        }
        
        /**
         * Gets the spawn chance/weight for a specific tier.
         * 
         * @param tier The tier to get the weight for
         * @return The spawn chance/weight, or 0.0 if not available in this tier
         */
        public double getChanceForTier(@NotNull String tier) {
            return tierWeights.getOrDefault(tier, 0.0);
        }
        
        /**
         * Gets the minimum amount that can spawn.
         * 
         * @return The minimum amount
         */
        public int getMinAmount() {
            return minAmount;
        }
        
        /**
         * Gets the maximum amount that can spawn.
         * 
         * @return The maximum amount
         */
        public int getMaxAmount() {
            return maxAmount;
        }
        
        /**
         * Checks if this item can appear in a specific tier.
         * 
         * @param tier The tier to check
         * @return True if this item can appear in the specified tier
         */
        public boolean canAppearInTier(@NotNull String tier) {
            return tierWeights.containsKey(tier) && tierWeights.get(tier) > 0.0;
        }
    }
} 
