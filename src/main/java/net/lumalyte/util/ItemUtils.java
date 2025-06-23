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
     * Creates an ItemStack from a configuration section.
     * 
     * @param plugin The plugin instance
     * @param section The configuration section containing item properties
     * @param itemKey The key of the item in the configuration
     * @return The created ItemStack, or null if creation failed
     */
    public static @Nullable ItemStack createItemFromConfig(@NotNull LumaSG plugin, @NotNull ConfigurationSection section, @NotNull String itemKey) {
        DebugLogger.ContextualLogger logger = plugin.getDebugLogger().forContext("ItemUtils");
        try {
            // Get material
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
            
            // Create item stack
            ItemStack itemStack = new ItemStack(material);
            ItemMeta meta = itemStack.getItemMeta();
            if (meta == null) {
                logger.warn("Failed to get item meta for material: " + material);
                return itemStack;
            }
            
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
            if (section.contains("item-flags")) {
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
            
            // Set enchantments
            if (section.contains("enchantments")) {
                ConfigurationSection enchantSection = section.getConfigurationSection("enchantments");
                if (enchantSection != null) {
                    for (String enchantKey : enchantSection.getKeys(false)) {
                        try {
                            // Use registry API instead of deprecated getByKey
                            Enchantment enchant = RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT).get(NamespacedKey.minecraft(enchantKey.toLowerCase()));
                            if (enchant != null) {
                                int level = enchantSection.getInt(enchantKey);
                                meta.addEnchant(enchant, level, true);
                                logger.debug("Added enchantment: " + enchantKey + " level " + level);
                            }
                        } catch (IllegalArgumentException e) {
                            logger.warn("Invalid enchantment: " + enchantKey);
                        }
                    }
                }
            }
            
            // Add attribute modifiers
            if (section.contains("attributes")) {
                ConfigurationSection attributesSection = section.getConfigurationSection("attributes");
                if (attributesSection != null) {
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
                            } else {
                                logger.warn("Invalid attribute: " + attributeKey);
                            }
                        } catch (IllegalArgumentException e) {
                            logger.warn("Invalid attribute: " + attributeKey);
                        }
                    }
                }
            }
            
            // Handle potion effects if this is a potion
            if (meta instanceof PotionMeta potionMeta) {
                if (section.contains("potion-type")) {
                    String potionTypeStr = section.getString("potion-type");
                    if (potionTypeStr != null) {
                        try {
                            PotionType potionType = PotionType.valueOf(potionTypeStr.toUpperCase());
                            potionMeta.setBasePotionType(potionType);
                        } catch (IllegalArgumentException e) {
                            logger.warn("Invalid potion type: " + potionTypeStr);
                        }
                    }
                }
                
                if (section.contains("custom-effects")) {
                    ConfigurationSection effectsSection = section.getConfigurationSection("custom-effects");
                    if (effectsSection != null) {
                        for (String effectKey : effectsSection.getKeys(false)) {
                            ConfigurationSection effectSection = effectsSection.getConfigurationSection(effectKey);
                            if (effectSection != null) {
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
                                    } else {
                                        logger.warn("Invalid potion effect type: " + effectKey);
                                    }
                                } catch (IllegalArgumentException e) {
                                    logger.warn("Invalid potion effect type: " + effectKey);
                                }
                            }
                        }
                    }
                }
            }
            
            // Set persistent data
            if (section.contains("persistent-data")) {
                ConfigurationSection dataSection = section.getConfigurationSection("persistent-data");
                if (dataSection != null) {
                    for (String dataKey : dataSection.getKeys(false)) {
                        String value = dataSection.getString(dataKey);
                        if (value != null) {
                            NamespacedKey key = new NamespacedKey(plugin, dataKey);
                            meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, value);
                        }
                    }
                }
            }
            
            itemStack.setItemMeta(meta);
            return itemStack;
        } catch (Exception e) {
            logger.severe("Error creating item: " + itemKey, e);
            return null;
        }
    }
} 