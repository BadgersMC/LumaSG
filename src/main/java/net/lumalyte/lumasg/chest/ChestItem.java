package net.lumalyte.lumasg.chest;

import net.lumalyte.lumasg.LumaSG;
import net.lumalyte.lumasg.hooks.NexoHook;
import net.lumalyte.lumasg.util.core.DebugLogger;
import net.lumalyte.lumasg.util.core.ItemUtils;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;

/**
 * Represents an item that can be found in survival game chests.
 * 
 * <p>ChestItems define the loot pool for survival game chests. Each item
 * has configurable properties including spawn amounts, chance/weight, and
 * tier classification for different chest types.</p>
 * 
 * <p>This class supports both regular Bukkit items and external Nexo items,
 * providing a unified interface for chest loot management.</p>
 * 
 * @author LumaLyte
 * @version 1.0
 * @since 1.0
 */
public class ChestItem {
    
    /** The actual item stack for this chest item (null for Nexo items) */
    private final ItemStack itemStack;
    
    /** The minimum amount that can spawn in a chest */
    private final int minAmount;
    
    /** The maximum amount that can spawn in a chest */
    private final int maxAmount;
    
    /** The spawn chance/weight for this item */
    private final double chance;
    
    /** Whether this item is a Nexo item */
    private final boolean isNexoItem;
    
    /** The Nexo item ID (only used for Nexo items) */
    private final String nexoItemId;
    
    /** The tier this item belongs to */
    private final String tier;
    
    /**
     * Creates a new chest item with a material and default tier.
     * 
     * @param material The material for the item
     * @param minAmount The minimum amount that can spawn
     * @param maxAmount The maximum amount that can spawn
     * @param chance The spawn chance/weight for this item
     */
    public ChestItem(Material material, int minAmount, int maxAmount, double chance) {
        this(material, minAmount, maxAmount, chance, "default");
    }
    
    /**
     * Creates a new chest item with a material and specific tier.
     * 
     * @param material The material for the item
     * @param minAmount The minimum amount that can spawn
     * @param maxAmount The maximum amount that can spawn
     * @param chance The spawn chance/weight for this item
     * @param tier The tier/category this item belongs to
     */
    public ChestItem(Material material, int minAmount, int maxAmount, double chance, String tier) {
        this.itemStack = new ItemStack(material);
        this.minAmount = minAmount;
        this.maxAmount = maxAmount;
        this.chance = chance;
        this.isNexoItem = false;
        this.nexoItemId = null;
        this.tier = tier;
    }
    
    /**
     * Creates a new chest item with an item stack and default tier.
     * 
     * @param itemStack The item stack for this chest item
     * @param minAmount The minimum amount that can spawn
     * @param maxAmount The maximum amount that can spawn
     * @param chance The spawn chance/weight for this item
     */
    public ChestItem(ItemStack itemStack, int minAmount, int maxAmount, double chance) {
        this(itemStack, minAmount, maxAmount, chance, "default");
    }
    
    /**
     * Creates a new chest item with an item stack and specific tier.
     * 
     * @param itemStack The item stack for this chest item
     * @param minAmount The minimum amount that can spawn
     * @param maxAmount The maximum amount that can spawn
     * @param chance The spawn chance/weight for this item
     * @param tier The tier/category this item belongs to
     */
    public ChestItem(ItemStack itemStack, int minAmount, int maxAmount, double chance, String tier) {
        this.itemStack = itemStack.clone();
        this.minAmount = minAmount;
        this.maxAmount = maxAmount;
        this.chance = chance;
        this.isNexoItem = false;
        this.nexoItemId = null;
        this.tier = tier;
    }
    
    /**
     * Creates a new chest item as a Nexo item with default tier.
     * 
     * <p>Nexo items are external items that are loaded dynamically from
     * the Nexo plugin when needed.</p>
     * 
     * @param nexoItemId The Nexo item identifier
     * @param minAmount The minimum amount that can spawn
     * @param maxAmount The maximum amount that can spawn
     * @param chance The spawn chance/weight for this item
     */
    public ChestItem(String nexoItemId, int minAmount, int maxAmount, double chance) {
        this(nexoItemId, minAmount, maxAmount, chance, "default");
    }
    
    /**
     * Creates a new chest item as a Nexo item with a specific tier.
     * 
     * <p>Nexo items are external items that are loaded dynamically from
     * the Nexo plugin when needed.</p>
     * 
     * @param nexoItemId The Nexo item identifier
     * @param minAmount The minimum amount that can spawn
     * @param maxAmount The maximum amount that can spawn
     * @param chance The spawn chance/weight for this item
     * @param tier The tier/category this item belongs to
     */
    public ChestItem(String nexoItemId, int minAmount, int maxAmount, double chance, String tier) {
        this.itemStack = null; // Will be loaded from Nexo when needed
        this.minAmount = minAmount;
        this.maxAmount = maxAmount;
        this.chance = chance;
        this.isNexoItem = true;
        this.nexoItemId = nexoItemId;
        this.tier = tier;
    }
    
    /**
     * Creates a ChestItem from a configuration section.
     * 
     * @param plugin The plugin instance
     * @param section The configuration section
     * @param itemKey The key of the item in the configuration
     * @return The created ChestItem, or null if creation failed
     */
    public static ChestItem fromConfig(LumaSG plugin, ConfigurationSection section, String itemKey) {
        DebugLogger.ContextualLogger logger = plugin.getDebugLogger().forContext("ChestItem");
        
        if (section == null) {
            logger.warn("Null configuration section for item: " + itemKey);
            return null;
        }
        
        // Check if this is a Nexo item
        if (section.contains("nexo-item")) {
            String nexoItemId = section.getString("nexo-item");
            if (nexoItemId != null && !nexoItemId.isEmpty()) {
                int minAmount = section.getInt("min-amount", 1);
                int maxAmount = section.getInt("max-amount", 1);
                double chance = section.getDouble("chance", 10.0);
                String tier = section.getString("tier", "common");
                
                logger.debug("Created Nexo item: " + nexoItemId + " with chance " + chance + " and tier " + tier);
                return new ChestItem(nexoItemId, minAmount, maxAmount, chance, tier);
            }
        }
        
        // Regular item
        ItemStack itemStack = ItemUtils.createItemFromConfig(plugin, section, itemKey);
        if (itemStack == null) {
            return null;
        }
        
        int minAmount = section.getInt("min-amount", 1);
        int maxAmount = section.getInt("max-amount", 1);
        double chance = section.getDouble("chance", 10.0);
        String tier = section.getString("tier", "common");
        
        logger.debug("Successfully created item: " + itemKey + " with material " + itemStack.getType());
        return new ChestItem(itemStack, minAmount, maxAmount, chance, tier);
    }
    
    /**
     * Gets the item stack for this chest item.
     * 
     * @param plugin The plugin instance
     * @return The item stack, or null if it could not be created
     */
    public ItemStack getItemStack(LumaSG plugin) {
        if (isNexoItem) {
            // Check if Nexo hook is available
            NexoHook nexoHook = plugin.getHookManager().getNexoHook();
            if (nexoHook != null && nexoHook.isAvailable()) {
                return nexoHook.getNexoItem(nexoItemId).orElse(createFallbackItem(plugin));
            } else {
                DebugLogger.ContextualLogger logger = plugin.getDebugLogger().forContext("ChestItem");
                logger.warn("Nexo items are not available. Using fallback item for: " + nexoItemId);
                return createFallbackItem(plugin);
            }
        }
        
        return itemStack != null ? itemStack.clone() : null;
    }
    
    /**
     * Creates a fallback item when Nexo is not available.
     * 
     * @param plugin The plugin instance
     * @return A fallback item
     */
    private ItemStack createFallbackItem(LumaSG plugin) {
        // Create a simple diamond as fallback
        ItemStack fallback = new ItemStack(Material.DIAMOND);
        try {
            // Try to make it a bit more interesting with some metadata
            org.bukkit.inventory.meta.ItemMeta meta = fallback.getItemMeta();
            if (meta != null) {
                meta.displayName(net.kyori.adventure.text.Component.text("Missing Nexo Item: " + nexoItemId)
                    .color(net.kyori.adventure.text.format.NamedTextColor.AQUA));
                meta.lore(java.util.Arrays.asList(
                    net.kyori.adventure.text.Component.text("This is a fallback item because")
                        .color(net.kyori.adventure.text.format.NamedTextColor.GRAY),
                    net.kyori.adventure.text.Component.text("Nexo plugin is not available.")
                        .color(net.kyori.adventure.text.format.NamedTextColor.GRAY),
                    net.kyori.adventure.text.Component.text("Original item ID: ")
                        .color(net.kyori.adventure.text.format.NamedTextColor.GRAY)
                        .append(net.kyori.adventure.text.Component.text(nexoItemId)
                            .color(net.kyori.adventure.text.format.NamedTextColor.YELLOW))
                ));
                fallback.setItemMeta(meta);
            }
        } catch (Exception e) {
            DebugLogger.ContextualLogger logger = plugin.getDebugLogger().forContext("ChestItem");
            logger.warn("Failed to create fallback item metadata: " + e.getMessage());
        }
        return fallback;
    }
    
    /**
     * Gets the material of the item.
     * 
     * @return The material
     */
    public Material getMaterial() {
        return itemStack != null ? itemStack.getType() : Material.AIR;
    }
    
    /**
     * Gets the minimum amount.
     * 
     * @return The minimum amount
     */
    public int getMinAmount() {
        return minAmount;
    }
    
    /**
     * Gets the maximum amount.
     * 
     * @return The maximum amount
     */
    public int getMaxAmount() {
        return maxAmount;
    }
    
    /**
     * Gets the chance of the item appearing.
     * 
     * @return The chance
     */
    public double getChance() {
        return chance;
    }
    
    /**
     * Checks if this is a Nexo item.
     * 
     * @return True if this is a Nexo item, false otherwise
     */
    public boolean isNexoItem() {
        return isNexoItem;
    }
    
    /**
     * Gets the Nexo item ID.
     * 
     * @return The Nexo item ID, or null if this is not a Nexo item
     */
    public String getNexoItemId() {
        return nexoItemId;
    }
    
    /**
     * Gets the tier of this chest item.
     * 
     * @return The tier
     */
    public String getTier() {
        return tier;
    }
} 
