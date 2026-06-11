package net.lumalyte.lumasg.game

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.lumalyte.lumasg.config.LumaSGConfig
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

private val mm = MiniMessage.miniMessage()

/**
 * Builds a kill-feed message for a player death.
 * Uses configurable message templates from [LumaSGConfig.MessagesSection].
 *
 * @param victim  The player who died
 * @param killer  The player who dealt the killing blow, or null for environmental death
 * @param config  Optional config for message templates; falls back to defaults if null
 */
fun deathMessage(victim: Player, killer: Player?, config: LumaSGConfig? = null): Component {
    if (killer == null) {
        val template = config?.messages?.deathNatural ?: "<red><victim> died"
        return mm.deserialize(template, Placeholder.unparsed("victim", victim.name))
    }
    val weapon = weaponName(killer.inventory.itemInMainHand)
    val template = config?.messages?.deathByPlayer
        ?: "<red><victim> <gray>was killed by <red><killer> <gray>with <white><weapon>"
    return mm.deserialize(
        template,
        Placeholder.unparsed("victim", victim.name),
        Placeholder.unparsed("killer", killer.name),
        Placeholder.unparsed("weapon", weapon)
    )
}

/**
 * Builds a personal kill-notification sent only to the killer.
 * Uses configurable message template from [LumaSGConfig.MessagesSection].
 */
fun killNotification(victim: Player, killerKills: Int, config: LumaSGConfig? = null): Component {
    val template = config?.messages?.killNotification
        ?: "<green>You killed <white><victim><green>! (<gold><kills> kills<green>)"
    return mm.deserialize(
        template,
        Placeholder.unparsed("victim", victim.name),
        Placeholder.unparsed("kills", killerKills.toString())
    )
}

/**
 * Returns a human-readable weapon name for the item held by the killer.
 * Handles swords, axes, bows, crossbows, tridents, tools, and custom items.
 */
@Suppress("CyclomaticComplexMethod") // flat when-mapping of material -> weapon name
fun weaponName(item: ItemStack): String {
    if (item.type == Material.AIR) return "fists"

    val materialName = item.type.name.lowercase()

    // Swords
    if ("sword" in materialName) {
        return when {
            "wooden" in materialName -> "wooden sword"
            "stone" in materialName -> "stone sword"
            "copper" in materialName -> "copper sword"
            "iron" in materialName -> "iron sword"
            "golden" in materialName -> "golden sword"
            "diamond" in materialName -> "diamond sword"
            "netherite" in materialName -> "netherite sword"
            else -> "sword"
        }
    }

    // Axes
    if ("axe" in materialName) {
        return when {
            "wooden" in materialName -> "wooden axe"
            "stone" in materialName -> "stone axe"
            "copper" in materialName -> "copper axe"
            "iron" in materialName -> "iron axe"
            "golden" in materialName -> "golden axe"
            "diamond" in materialName -> "diamond axe"
            "netherite" in materialName -> "netherite axe"
            else -> "axe"
        }
    }

    // Spears
    if ("spear" in materialName) {
        return when {
            "wooden" in materialName -> "wooden spear"
            "stone" in materialName -> "stone spear"
            "iron" in materialName -> "iron spear"
            "diamond" in materialName -> "diamond spear"
            else -> "spear"
        }
    }

    // Ranged / special
    return when (item.type) {
        Material.BOW -> "bow"
        Material.CROSSBOW -> "crossbow"
        Material.TRIDENT -> "trident"
        Material.MACE -> "mace"
        Material.STICK -> "stick"
        else -> when {
            "pickaxe" in materialName -> "pickaxe"
            "shovel" in materialName || "spade" in materialName -> "shovel"
            "hoe" in materialName -> "hoe"
            "rod" in materialName -> "rod"
            else -> {
                // Custom item display name, or formatted material name
                item.itemMeta?.displayName()?.let { it as? TextComponent }
                    ?.content()?.takeIf { it.isNotBlank() }
                    ?: materialName.replace('_', ' ')
            }
        }
    }
}
