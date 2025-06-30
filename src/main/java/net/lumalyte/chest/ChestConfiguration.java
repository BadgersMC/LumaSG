package net.lumalyte.chest;

import net.lumalyte.LumaSG;
import net.lumalyte.util.DebugLogger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles loading and validation of chest configuration.
 */
public class ChestConfiguration {
    private final @NotNull LumaSG plugin;
    private final @NotNull DebugLogger.ContextualLogger logger;
    private final @NotNull File configFile;
    private final @NotNull Map<String, ConfigurationSection> itemConfigs;
    
    public ChestConfiguration(@NotNull LumaSG plugin, @NotNull File configFile) {
        this.plugin = plugin;
        this.logger = plugin.getDebugLogger().forContext("ChestConfiguration");
        this.configFile = configFile;
        this.itemConfigs = new ConcurrentHashMap<>();
    }
    
    /**
     * Loads the chest configuration from file.
     * 
     * @return List of loaded chest items
     */
    public @NotNull List<ChestItem> loadItems() {
        List<ChestItem> items = new ArrayList<>();
        
        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
            ConfigurationSection itemsSection = config.getConfigurationSection("items");
            
            if (itemsSection == null) {
                logger.warn("No items section found in chest.yml");
                return items;
            }
            
            // Load and validate each item configuration
            for (String itemKey : itemsSection.getKeys(false)) {
                ConfigurationSection itemSection = itemsSection.getConfigurationSection(itemKey);
                if (itemSection != null) {
                    itemConfigs.put(itemKey, itemSection);
                    processItemConfig(itemKey, itemSection, items);
                }
            }
            
        } catch (Exception e) {
            logger.severe("Failed to load chest configuration", e);
        }
        
        return items;
    }
    
    /**
     * Processes a single item configuration.
     */
    private void processItemConfig(@NotNull String itemKey, @NotNull ConfigurationSection itemSection, 
                                 @NotNull List<ChestItem> items) {
        logger.debug("Processing item: " + itemKey);
        
        ConfigurationSection tierWeights = itemSection.getConfigurationSection("tier-weights");
        if (tierWeights == null) {
            logger.warn("Item " + itemKey + " has no tier-weights configuration - skipping");
            return;
        }
        
        // Process each tier for this item
        for (String tier : tierWeights.getKeys(false)) {
            double weight = tierWeights.getDouble(tier, 0.0);
            if (weight <= 0) {
                continue;
            }
            
            ChestItem item = createItemForTier(itemKey, itemSection, tier, weight);
            if (item != null) {
                items.add(item);
            }
        }
    }
    
    /**
     * Creates a chest item for a specific tier.
     */
    private @Nullable ChestItem createItemForTier(@NotNull String itemKey, @NotNull ConfigurationSection itemSection,
                                                @NotNull String tier, double weight) {
        logger.debug("Creating item: " + itemKey + " for tier: " + tier + " with weight: " + weight);
        
        YamlConfiguration enrichedSection = createEnrichedSection(itemSection, tier, weight);
        
        // Skip Nexo items if Nexo is not available
        if (enrichedSection.contains("nexo-item") && !plugin.getHookManager().isHookAvailable("Nexo")) {
            logger.debug("Skipping Nexo item: " + itemKey + " because Nexo plugin is not available");
            return null;
        }
        
        ChestItem item = ChestItem.fromConfig(plugin, enrichedSection, itemKey);
        if (item != null) {
            logger.debug("Successfully created item: " + itemKey + " for tier: " + tier);
        } else {
            logger.warn("Failed to create item: " + itemKey + " for tier: " + tier);
        }
        
        return item;
    }
    
    /**
     * Creates an enriched configuration section for an item tier.
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
     * Gets the configuration section for an item.
     */
    public @Nullable ConfigurationSection getItemConfig(@NotNull String itemKey) {
        return itemConfigs.get(itemKey);
    }
} 