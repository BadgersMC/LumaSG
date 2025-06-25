package net.lumalyte.util;

import net.lumalyte.LumaSG;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;
import net.kyori.adventure.text.Component;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.enchantments.Enchantment;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * Utility class for creating and modifying items.
 * 
 * <p>This class provides methods for creating items from configuration sections
 * and applying various properties to items such as enchantments, attributes,
 * and custom effects.</p>
 * 
 * @author LumaLyte
 * @version 1.0
 * @since 1.0
 */
public class ItemUtils {
    
    /**
     * Creates an ItemStack from configuration data.
     */
    public static @Nullable ItemStack createItemFromConfig(@NotNull LumaSG plugin, @NotNull ConfigurationSection section, @NotNull String itemKey) {
        DebugLogger.ContextualLogger logger = plugin.getDebugLogger().forContext("ItemUtils");
        try {
            // Get material and create basic item
            ItemStack itemStack = createBasicItemStack(section, itemKey, logger);
            if (itemStack == null) {
                return null;
            }
            
            ItemMeta meta = itemStack.getItemMeta();
            if (meta == null) {
                logger.warn("Failed to get item meta for item: " + itemKey);
                return itemStack;
            }
            
            // Apply all configurations to the item meta
            applyBasicProperties(section, meta, itemKey, logger);
            applyEnchantments(section, meta, itemKey, logger, plugin);
            applyAttributes(section, meta, itemKey, logger, plugin);
            applyPotionEffects(section, meta, itemKey, logger);
            applyPersistentData(section, meta, itemKey, logger, plugin);
            
            itemStack.setItemMeta(meta);
            return itemStack;
        } catch (Exception e) {
            logger.severe("Error creating item: " + itemKey, e);
            return null;
        }
    }
    
    /**
     * Creates a basic ItemStack with material validation.
     */
    private static @Nullable ItemStack createBasicItemStack(@NotNull ConfigurationSection section, @NotNull String itemKey, @NotNull DebugLogger.ContextualLogger logger) {
        String materialName = section.getString("material");
        if (materialName == null) {
            logger.warn("No material specified for item: " + itemKey);
            return null;
        }
        
        Material material;
        try {
            material = Material.valueOf(materialName.toUpperCase());
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid material: " + materialName + " for item: " + itemKey);
            return null;
        }
        
        return new ItemStack(material);
    }
    
    /**
     * Applies basic properties like name, lore, custom model data, unbreakable, and item flags.
     */
    private static void applyBasicProperties(@NotNull ConfigurationSection section, @NotNull ItemMeta meta, @NotNull String itemKey, @NotNull DebugLogger.ContextualLogger logger) {
        // Set name if specified
        if (section.contains("name")) {
            String name = section.getString("name");
            if (name != null) {
                Component displayName = MiniMessageUtils.parseMessage(name);
                meta.displayName(displayName);
                logger.debug("Set display name for item: " + itemKey);
            }
        }
        
        // Set lore if specified
        if (section.contains("lore")) {
            List<String> loreStrings = section.getStringList("lore");
            if (!loreStrings.isEmpty()) {
                List<Component> lore = new ArrayList<>();
                for (String line : loreStrings) {
                    lore.add(MiniMessageUtils.parseMessage(line));
                }
                meta.lore(lore);
                logger.debug("Set lore for item: " + itemKey);
            }
        }
        
        // Set custom model data
        if (section.contains("custom-model-data")) {
            int cmd = section.getInt("custom-model-data");
            meta.setCustomModelData(cmd);
            logger.debug("Set custom model data: " + cmd);
        }
        
        // Set unbreakable
        if (section.contains("unbreakable")) {
            boolean unbreakable = section.getBoolean("unbreakable");
            meta.setUnbreakable(unbreakable);
            logger.debug("Set unbreakable: " + unbreakable);
        }
        
        // Add item flags
        applyItemFlags(section, meta, itemKey, logger);
    }
    
    /**
     * Applies item flags to the item meta.
     */
    private static void applyItemFlags(@NotNull ConfigurationSection section, @NotNull ItemMeta meta, @NotNull String itemKey, @NotNull DebugLogger.ContextualLogger logger) {
        if (!section.contains("item-flags")) {
            return;
        }
        
        List<String> flagStrings = section.getStringList("item-flags");
        for (String flagString : flagStrings) {
            try {
                ItemFlag flag = ItemFlag.valueOf(flagString.toUpperCase());
                meta.addItemFlags(flag);
                logger.debug("Added item flag: " + flag);
            } catch (IllegalArgumentException e) {
                logger.warn("Invalid item flag: " + flagString);
            }
        }
    }
    
    /**
     * Applies enchantments to the item meta.
     */
    private static void applyEnchantments(@NotNull ConfigurationSection section, @NotNull ItemMeta meta, @NotNull String itemKey, @NotNull DebugLogger.ContextualLogger logger, @NotNull LumaSG plugin) {
        if (!section.contains("enchantments")) {
            return;
        }
        
        ConfigurationSection enchantSection = section.getConfigurationSection("enchantments");
        if (enchantSection == null) {
            return;
        }
        
        for (String enchantKey : enchantSection.getKeys(false)) {
            try {
                // Use registry API instead of deprecated getByKey
                Enchantment enchant = RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT).get(NamespacedKey.minecraft(enchantKey.toLowerCase()));
                if (enchant != null) {
                    int level = enchantSection.getInt(enchantKey);
                    meta.addEnchant(enchant, level, true);
                    logger.debug("Added enchantment: " + enchantKey + " level " + level);
                } else {
                    logger.warn("Unknown enchantment: " + enchantKey);
                }
            } catch (IllegalArgumentException e) {
                logger.warn("Invalid enchantment: " + enchantKey);
            }
        }
    }
    
    /**
     * Applies attribute modifiers to the item meta.
     */
    private static void applyAttributes(@NotNull ConfigurationSection section, @NotNull ItemMeta meta, @NotNull String itemKey, @NotNull DebugLogger.ContextualLogger logger, @NotNull LumaSG plugin) {
        if (!section.contains("attributes")) {
            return;
        }
        
        ConfigurationSection attributesSection = section.getConfigurationSection("attributes");
        if (attributesSection == null) {
            return;
        }
        
        for (String attributeKey : attributesSection.getKeys(false)) {
            try {
                Attribute attribute = RegistryAccess.registryAccess().getRegistry(RegistryKey.ATTRIBUTE).get(NamespacedKey.minecraft(attributeKey.toLowerCase()));
                if (attribute != null) {
                    double value = attributesSection.getDouble(attributeKey);
                    
                    // Use new Paper constructor with NamespacedKey instead of deprecated UUID constructor
                    NamespacedKey modifierKey = new NamespacedKey(plugin, attributeKey.toLowerCase() + "_modifier");
                    AttributeModifier modifier = new AttributeModifier(
                        modifierKey,
                        value,
                        AttributeModifier.Operation.ADD_NUMBER
                    );
                    
                    meta.addAttributeModifier(attribute, modifier);
                    logger.debug("Added attribute modifier: " + attributeKey + " = " + value);
                } else {
                    logger.warn("Invalid attribute: " + attributeKey);
                }
            } catch (IllegalArgumentException e) {
                logger.warn("Invalid attribute: " + attributeKey);
            }
        }
    }
    
    /**
     * Applies potion effects if the item is a potion.
     */
    private static void applyPotionEffects(@NotNull ConfigurationSection section, @NotNull ItemMeta meta, @NotNull String itemKey, @NotNull DebugLogger.ContextualLogger logger) {
        if (!(meta instanceof PotionMeta potionMeta)) {
            return;
        }
        
        // Set base potion type
        if (section.contains("potion-type")) {
            String potionTypeStr = section.getString("potion-type");
            if (potionTypeStr != null) {
                try {
                    PotionType potionType = PotionType.valueOf(potionTypeStr.toUpperCase());
                    potionMeta.setBasePotionType(potionType);
                    logger.debug("Set potion type: " + potionType);
                } catch (IllegalArgumentException e) {
                    logger.warn("Invalid potion type: " + potionTypeStr);
                }
            }
        }
        
        // Add custom effects
        applyCustomPotionEffects(section, potionMeta, itemKey, logger);
    }
    
    /**
     * Applies custom potion effects to a potion meta.
     */
    private static void applyCustomPotionEffects(@NotNull ConfigurationSection section, @NotNull PotionMeta potionMeta, @NotNull String itemKey, @NotNull DebugLogger.ContextualLogger logger) {
        if (!section.contains("custom-effects")) {
            return;
        }
        
        ConfigurationSection effectsSection = section.getConfigurationSection("custom-effects");
        if (effectsSection == null) {
            return;
        }
        
        for (String effectKey : effectsSection.getKeys(false)) {
            ConfigurationSection effectSection = effectsSection.getConfigurationSection(effectKey);
            if (effectSection != null) {
                createAndAddPotionEffect(effectKey, effectSection, potionMeta, logger);
            }
        }
    }
    
    /**
     * Creates and adds a single potion effect.
     */
    private static void createAndAddPotionEffect(@NotNull String effectKey, @NotNull ConfigurationSection effectSection, @NotNull PotionMeta potionMeta, @NotNull DebugLogger.ContextualLogger logger) {
        try {
            PotionEffectType effectType = RegistryAccess.registryAccess().getRegistry(RegistryKey.MOB_EFFECT).get(NamespacedKey.minecraft(effectKey.toLowerCase()));
            if (effectType != null) {
                int duration = effectSection.getInt("duration", 200);
                int amplifier = effectSection.getInt("amplifier", 0);
                boolean ambient = effectSection.getBoolean("ambient", false);
                boolean particles = effectSection.getBoolean("particles", true);
                boolean icon = effectSection.getBoolean("icon", true);
                
                PotionEffect effect = new PotionEffect(
                    effectType,
                    duration,
                    amplifier,
                    ambient,
                    particles,
                    icon
                );
                
                potionMeta.addCustomEffect(effect, true);
                logger.debug("Added custom potion effect: " + effectKey);
            } else {
                logger.warn("Invalid potion effect type: " + effectKey);
            }
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid potion effect type: " + effectKey);
        }
    }
    
    /**
     * Applies persistent data to the item meta.
     */
    private static void applyPersistentData(@NotNull ConfigurationSection section, @NotNull ItemMeta meta, @NotNull String itemKey, @NotNull DebugLogger.ContextualLogger logger, @NotNull LumaSG plugin) {
        if (!section.contains("persistent-data")) {
            return;
        }
        
        ConfigurationSection dataSection = section.getConfigurationSection("persistent-data");
        if (dataSection == null) {
            return;
        }
        
        for (String dataKey : dataSection.getKeys(false)) {
            String value = dataSection.getString(dataKey);
            if (value != null) {
                NamespacedKey key = new NamespacedKey(plugin, dataKey);
                meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, value);
                logger.debug("Set persistent data: " + dataKey + " = " + value);
            }
        }
    }
} 