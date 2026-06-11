package net.lumalyte.lumasg.util

import io.papermc.paper.registry.RegistryAccess
import io.papermc.paper.registry.RegistryKey
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.attribute.Attribute
import org.bukkit.attribute.AttributeModifier
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.enchantments.Enchantment
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ArmorMeta
import org.bukkit.inventory.meta.EnchantmentStorageMeta
import org.bukkit.inventory.meta.PotionMeta
import org.bukkit.inventory.meta.trim.ArmorTrim
import org.bukkit.inventory.meta.trim.TrimMaterial
import org.bukkit.inventory.meta.trim.TrimPattern
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.potion.PotionType
import org.slf4j.LoggerFactory

/**
 * Utility object for creating and modifying items from YAML configuration.
 *
 * Handles: material, name (MiniMessage format), lore, enchantments,
 * stored-enchants (enchanted books), attributes, potion effects,
 * custom-effects, persistent-data, armor trims, unbreakable flag,
 * item-flags, custom-model-data.
 */
object ItemUtils {

    private val logger = LoggerFactory.getLogger(ItemUtils::class.java)
    private val miniMessage = MiniMessage.miniMessage()

    /**
     * Creates an ItemStack from a YAML configuration section.
     *
     * @param plugin The plugin instance (needed for NamespacedKeys)
     * @param section The configuration section containing item data
     * @param itemKey The key identifying this item (for logging)
     * @return The created ItemStack, or null if creation failed
     */
    fun createItemFromConfig(plugin: JavaPlugin, section: ConfigurationSection, itemKey: String): ItemStack? {
        return try {
            val itemStack = createBasicItemStack(section, itemKey) ?: return null
            val meta = itemStack.itemMeta
            if (meta == null) {
                logger.warn("Failed to get item meta for item: {}", itemKey)
                return itemStack
            }

            applyBasicProperties(section, meta, itemKey)
            applyEnchantments(section, meta)
            applyAttributes(section, meta, itemKey, plugin)
            applyPotionEffects(section, meta, itemKey)
            applyPersistentData(section, meta, plugin)

            if (meta is ArmorMeta && isArmorMaterial(itemStack.type)) {
                applyArmorTrim(section, meta)
            }

            itemStack.itemMeta = meta
            itemStack
        } catch (e: Exception) {
            logger.error("Error creating item: {}", itemKey, e)
            null
        }
    }

    /**
     * Creates a simple ItemStack with optional name and lore (MiniMessage format).
     */
    fun createItem(
        material: Material,
        name: String? = null,
        lore: List<String>? = null
    ): ItemStack {
        val item = ItemStack(material)
        val meta = item.itemMeta ?: return item

        if (!name.isNullOrEmpty()) {
            val displayName = if (name.contains("<!italic>")) name else "<!italic>$name</!italic>"
            meta.displayName(miniMessage.deserialize(displayName))
        }

        if (!lore.isNullOrEmpty()) {
            meta.lore(lore.map { line ->
                val loreLine = if (line.contains("<!italic>")) line else "<!italic>$line</!italic>"
                miniMessage.deserialize(loreLine)
            })
        }

        item.itemMeta = meta
        return item
    }

    // ── Private helpers ─────────────────────────────────────────────────────

    private fun createBasicItemStack(section: ConfigurationSection, itemKey: String): ItemStack? {
        val materialName = section.getString("material")
        if (materialName == null) {
            logger.warn("No material specified for item: {}", itemKey)
            return null
        }

        val material = try {
            Material.valueOf(materialName.uppercase())
        } catch (e: IllegalArgumentException) {
            logger.warn("Invalid material: {} for item: {}", materialName, itemKey)
            return null
        }

        return ItemStack(material)
    }

    private fun applyBasicProperties(section: ConfigurationSection, meta: org.bukkit.inventory.meta.ItemMeta, itemKey: String) {
        // Display name
        section.getString("name")?.let { name ->
            val wrapped = if (name.contains("<!italic>")) name else "<!italic>$name</!italic>"
            meta.displayName(miniMessage.deserialize(wrapped))
            logger.debug("Set display name for item: {}", itemKey)
        }

        // Lore
        val loreStrings = section.getStringList("lore")
        if (loreStrings.isNotEmpty()) {
            meta.lore(loreStrings.map { line ->
                val wrapped = if (line.contains("<!italic>")) line else "<!italic>$line</!italic>"
                miniMessage.deserialize(wrapped)
            })
            logger.debug("Set lore for item: {}", itemKey)
        }

        // Custom model data
        if (section.contains("custom-model-data")) {
            val cmd = section.getInt("custom-model-data")
            meta.setCustomModelData(cmd)
            logger.debug("Set custom model data: {}", cmd)
        }

        // Unbreakable
        if (section.contains("unbreakable")) {
            val unbreakable = section.getBoolean("unbreakable")
            meta.isUnbreakable = unbreakable
            logger.debug("Set unbreakable: {}", unbreakable)
        }

        // Item flags
        applyItemFlags(section, meta)
    }

    private fun applyItemFlags(section: ConfigurationSection, meta: org.bukkit.inventory.meta.ItemMeta) {
        if (!section.contains("item-flags")) return

        for (flagString in section.getStringList("item-flags")) {
            try {
                val flag = ItemFlag.valueOf(flagString.uppercase())
                meta.addItemFlags(flag)
                logger.debug("Added item flag: {}", flag)
            } catch (e: IllegalArgumentException) {
                logger.warn("Invalid item flag: {}", flagString)
            }
        }
    }

    private fun applyEnchantments(section: ConfigurationSection, meta: org.bukkit.inventory.meta.ItemMeta) {
        // Stored enchantments (enchanted books)
        if (meta is EnchantmentStorageMeta) {
            section.getConfigurationSection("stored-enchants")?.let { storedSection ->
                for (enchantKey in storedSection.getKeys(false)) {
                    val enchant = getEnchantmentFromRegistry(enchantKey)
                    if (enchant != null) {
                        val level = storedSection.getInt(enchantKey)
                        meta.addStoredEnchant(enchant, level, true)
                        logger.debug("Added stored enchantment: {} level {}", enchantKey, level)
                    } else {
                        logger.warn("Unknown stored enchantment: {}", enchantKey)
                    }
                }
            }
        }

        // Regular enchantments
        section.getConfigurationSection("enchantments")?.let { enchantSection ->
            for (enchantKey in enchantSection.getKeys(false)) {
                val enchant = getEnchantmentFromRegistry(enchantKey)
                if (enchant != null) {
                    val level = enchantSection.getInt(enchantKey)
                    meta.addEnchant(enchant, level, true)
                    logger.debug("Added enchantment: {} level {}", enchantKey, level)
                } else {
                    logger.warn("Unknown enchantment: {}", enchantKey)
                }
            }
        }
    }

    private fun getEnchantmentFromRegistry(enchantKey: String): Enchantment? {
        return try {
            RegistryAccess.registryAccess()
                .getRegistry(RegistryKey.ENCHANTMENT)
                .get(NamespacedKey.minecraft(enchantKey.lowercase()))
        } catch (e: IllegalArgumentException) {
            logger.warn("Invalid enchantment: {}", enchantKey)
            null
        }
    }

    @Suppress("UnusedParameter") // itemKey kept for symmetry/logging across apply* helpers
    private fun applyAttributes(
        section: ConfigurationSection,
        meta: org.bukkit.inventory.meta.ItemMeta,
        itemKey: String,
        plugin: JavaPlugin
    ) {
        val attributesSection = section.getConfigurationSection("attributes") ?: return

        for (attributeKey in attributesSection.getKeys(false)) {
            try {
                val attribute = RegistryAccess.registryAccess()
                    .getRegistry(RegistryKey.ATTRIBUTE)
                    .get(NamespacedKey.minecraft(attributeKey.lowercase()))

                if (attribute != null) {
                    val value = attributesSection.getDouble(attributeKey)
                    val modifierKey = NamespacedKey(plugin, "${attributeKey.lowercase()}_modifier")
                    val modifier = AttributeModifier(
                        modifierKey,
                        value,
                        AttributeModifier.Operation.ADD_NUMBER
                    )
                    meta.addAttributeModifier(attribute, modifier)
                    logger.debug("Added attribute modifier: {} = {}", attributeKey, value)
                } else {
                    logger.warn("Invalid attribute: {}", attributeKey)
                }
            } catch (e: IllegalArgumentException) {
                logger.warn("Invalid attribute: {}", attributeKey)
            }
        }
    }

    @Suppress("UnusedParameter") // itemKey kept for symmetry/logging across apply* helpers
    private fun applyPotionEffects(
        section: ConfigurationSection,
        meta: org.bukkit.inventory.meta.ItemMeta,
        itemKey: String
    ) {
        if (meta !is PotionMeta) return

        // Base potion type
        section.getString("potion-type")?.let { potionTypeStr ->
            try {
                val potionType = PotionType.valueOf(potionTypeStr.uppercase())
                meta.basePotionType = potionType
                logger.debug("Set potion type: {}", potionType)
            } catch (e: IllegalArgumentException) {
                logger.warn("Invalid potion type: {}", potionTypeStr)
            }
        }

        // Custom effects
        val effectsSection = section.getConfigurationSection("custom-effects") ?: return
        for (effectKey in effectsSection.getKeys(false)) {
            val effectSection = effectsSection.getConfigurationSection(effectKey) ?: continue
            try {
                val effectType: PotionEffectType? = RegistryAccess.registryAccess()
                    .getRegistry(RegistryKey.MOB_EFFECT)
                    .get(NamespacedKey.minecraft(effectKey.lowercase()))

                if (effectType != null) {
                    val effect = PotionEffect(
                        effectType,
                        effectSection.getInt("duration", 200),
                        effectSection.getInt("amplifier", 0),
                        effectSection.getBoolean("ambient", false),
                        effectSection.getBoolean("particles", true),
                        effectSection.getBoolean("icon", true)
                    )
                    meta.addCustomEffect(effect, true)
                    logger.debug("Added custom potion effect: {}", effectKey)
                } else {
                    logger.warn("Invalid potion effect type: {}", effectKey)
                }
            } catch (e: IllegalArgumentException) {
                logger.warn("Invalid potion effect type: {}", effectKey)
            }
        }
    }

    private fun applyPersistentData(
        section: ConfigurationSection,
        meta: org.bukkit.inventory.meta.ItemMeta,
        plugin: JavaPlugin
    ) {
        val dataSection = section.getConfigurationSection("persistent-data") ?: return

        for (dataKey in dataSection.getKeys(false)) {
            val value = dataSection.getString(dataKey) ?: continue
            val key = NamespacedKey(plugin, dataKey)
            meta.persistentDataContainer.set(key, PersistentDataType.STRING, value)
            logger.debug("Set persistent data: {} = {}", dataKey, value)
        }
    }

    private fun isArmorMaterial(material: Material): Boolean {
        val name = material.name
        return name.contains("LEATHER_") ||
                name.contains("CHAINMAIL_") ||
                name.contains("IRON_") ||
                name.contains("GOLDEN_") ||
                name.contains("DIAMOND_") ||
                name.contains("NETHERITE_")
    }

    private fun applyArmorTrim(section: ConfigurationSection, meta: ArmorMeta) {
        try {
            val trimSection = section.getConfigurationSection("trim")
            if (trimSection != null) {
                // Specified trim
                val patternKey = trimSection.getString("pattern", "") ?: ""
                val materialKey = trimSection.getString("material", "") ?: ""

                val pattern: TrimPattern? = RegistryAccess.registryAccess()
                    .getRegistry(RegistryKey.TRIM_PATTERN)
                    .get(NamespacedKey.minecraft(patternKey.lowercase()))
                val material: TrimMaterial? = RegistryAccess.registryAccess()
                    .getRegistry(RegistryKey.TRIM_MATERIAL)
                    .get(NamespacedKey.minecraft(materialKey.lowercase()))

                if (pattern != null && material != null) {
                    meta.trim = ArmorTrim(material, pattern)
                    logger.debug("Applied armor trim: {} with material {}", pattern, material)
                }
            } else {
                // Random trim if no specific trim is configured
                val patternRegistry = RegistryAccess.registryAccess().getRegistry(RegistryKey.TRIM_PATTERN)
                val materialRegistry = RegistryAccess.registryAccess().getRegistry(RegistryKey.TRIM_MATERIAL)

                val patterns = mutableListOf<TrimPattern>()
                val materials = mutableListOf<TrimMaterial>()
                patternRegistry.forEach { patterns.add(it) }
                materialRegistry.forEach { materials.add(it) }

                if (patterns.isNotEmpty() && materials.isNotEmpty()) {
                    val pattern = patterns.random()
                    val material = materials.random()
                    meta.trim = ArmorTrim(material, pattern)
                    logger.debug("Applied random armor trim: {} with material {}", pattern, material)
                }
            }
        } catch (e: Exception) {
            logger.warn("Failed to apply armor trim", e)
        }
    }
}
