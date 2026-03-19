package net.lumalyte.lumasg.chest

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.lumalyte.lumasg.domain.LootMode
import net.lumalyte.lumasg.util.ItemUtils
import org.bukkit.Material
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import org.slf4j.LoggerFactory

/**
 * Represents an item that can be found in survival game chests.
 *
 * Each ChestItem has configurable properties including spawn amounts,
 * chance/weight, and tier classification. Supports both regular Bukkit
 * items and Nexo items.
 */
class ChestItem(
    /** The actual item stack (null for Nexo items until resolved) */
    val itemStack: ItemStack?,
    /** Minimum amount that can spawn in a chest */
    val minAmount: Int,
    /** Maximum amount that can spawn in a chest */
    val maxAmount: Int,
    /** Spawn chance/weight for this item */
    val chance: Double,
    /** The tier this item belongs to */
    val tier: String,
    /** Whether this item is a Nexo item */
    val isNexoItem: Boolean = false,
    /** The Nexo item ID (only used for Nexo items) */
    val nexoItemId: String? = null,
    /** Per-mode weight multipliers. Defaults to 1.0 for all modes (appears normally). */
    val modeWeights: Map<LootMode, Double> = LootMode.entries.associateWith { 1.0 }
) {

    /**
     * Gets the item stack, resolving Nexo items if needed.
     * For Nexo items without the hook available, returns a fallback diamond.
     */
    fun resolveItemStack(): ItemStack? {
        if (isNexoItem && nexoItemId != null) {
            val resolved = nexoResolver?.invoke(nexoItemId)
            if (resolved != null) return resolved.clone()
            logger.warn("Could not resolve Nexo item '{}' — using fallback", nexoItemId)
            return createFallbackItem()
        }
        return itemStack?.clone()
    }

    /** The material of this item (AIR for Nexo items without a resolved stack). */
    val material: Material
        get() = itemStack?.type ?: Material.AIR

    private fun createFallbackItem(): ItemStack {
        val fallback = ItemStack(Material.DIAMOND)
        val meta = fallback.itemMeta ?: return fallback
        meta.displayName(
            Component.text("Missing Nexo Item: ${nexoItemId ?: "unknown"}")
                .color(NamedTextColor.AQUA)
        )
        meta.lore(
            listOf(
                Component.text("This is a fallback item because").color(NamedTextColor.GRAY),
                Component.text("Nexo plugin is not available.").color(NamedTextColor.GRAY),
                Component.text("Original item ID: ").color(NamedTextColor.GRAY)
                    .append(Component.text(nexoItemId ?: "unknown").color(NamedTextColor.YELLOW))
            )
        )
        fallback.itemMeta = meta
        return fallback
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ChestItem::class.java)

        /**
         * Resolver function for Nexo items. Set by [ChestManager] at startup
         * when the Nexo hook is available. Maps nexo-item-id → ItemStack.
         */
        @Volatile
        var nexoResolver: ((String) -> ItemStack?)? = null

        /**
         * Creates a ChestItem from a YAML configuration section.
         *
         * @param plugin The plugin instance
         * @param section The configuration section
         * @param itemKey The key of the item in the configuration
         * @return The created ChestItem, or null if creation failed
         */
        fun fromConfig(plugin: JavaPlugin, section: ConfigurationSection?, itemKey: String): ChestItem? {
            if (section == null) {
                logger.warn("Null configuration section for item: {}", itemKey)
                return null
            }

            val minAmount = section.getInt("min-amount", 1)
            val maxAmount = section.getInt("max-amount", 1)
            val chance = section.getDouble("chance", 10.0)
            val tier = section.getString("tier", "common") ?: "common"

            // Parse mode-weights (optional — defaults to 1.0 for all modes)
            val modeWeightsSection = section.getConfigurationSection("mode-weights")
            val modeWeights: Map<LootMode, Double> = if (modeWeightsSection != null) {
                LootMode.entries.associateWith { mode ->
                    modeWeightsSection.getDouble(mode.name.lowercase(), 1.0)
                }
            } else {
                LootMode.entries.associateWith { 1.0 }
            }

            // Check if this is a Nexo item
            val nexoItemId = section.getString("nexo-item")
            if (!nexoItemId.isNullOrEmpty()) {
                logger.debug("Created Nexo item: {} with chance {} and tier {}", nexoItemId, chance, tier)
                return ChestItem(
                    itemStack = null,
                    minAmount = minAmount,
                    maxAmount = maxAmount,
                    chance = chance,
                    tier = tier,
                    isNexoItem = true,
                    nexoItemId = nexoItemId,
                    modeWeights = modeWeights
                )
            }

            // Regular item — use ItemUtils to build the stack
            val itemStack = ItemUtils.createItemFromConfig(plugin, section, itemKey) ?: return null

            logger.debug("Successfully created item: {} with material {}", itemKey, itemStack.type)
            return ChestItem(
                itemStack = itemStack,
                minAmount = minAmount,
                maxAmount = maxAmount,
                chance = chance,
                tier = tier,
                modeWeights = modeWeights
            )
        }
    }
}
