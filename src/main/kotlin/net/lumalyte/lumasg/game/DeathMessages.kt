package net.lumalyte.lumasg.game

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

private val mm = MiniMessage.miniMessage()

/**
 * Builds a kill-feed message for a player death.
 *
 * @param victim  The player who died
 * @param killer  The player who dealt the killing blow, or null for environmental death
 */
fun deathMessage(victim: Player, killer: Player?): Component {
    if (killer == null) {
        return mm.deserialize("<red>${victim.name} died</red>")
    }
    val weapon = weaponName(killer.inventory.itemInMainHand)
    return mm.deserialize(
        "<red>${victim.name} <gray>was killed by <red>${killer.name} <gray>with <white>$weapon"
    )
}

/**
 * Builds a personal kill-notification sent only to the killer.
 */
fun killNotification(victim: Player, killerKills: Int): Component =
    mm.deserialize("<green>You killed <white>${victim.name}<green>! (<gold>$killerKills kills<green>)")

/**
 * Returns a human-readable weapon name for the item held by the killer.
 */
fun weaponName(item: ItemStack): String {
    if (item.type == Material.AIR) return "fists"
    val name = item.type.name.lowercase().replace('_', ' ')
    // Return display name if it has one (custom item), otherwise use material name
    return item.itemMeta?.displayName()?.let { it as? TextComponent }
        ?.content()?.takeIf { it.isNotBlank() } ?: name
}
