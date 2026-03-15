package net.lumalyte.lumasg.chest.items

import net.badgersmc.nexus.annotations.Service
import net.lumalyte.lumasg.game.GameManager
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.plugin.Plugin

@Service
class PlayerTrackerItem(
    private val plugin: Plugin,
    private val gameManager: GameManager
) : CustomItem {
    override val material = Material.COMPASS
    override val displayName = "§cPlayer Tracker"
    override val key = NamespacedKey(plugin, "player_tracker")

    override fun createStack(): ItemStack {
        val stack = ItemStack(material)
        val meta: ItemMeta = stack.itemMeta ?: return stack
        meta.setDisplayName(displayName)
        meta.lore = listOf("§7Points to the nearest player", "§7Right-click to use")
        stack.itemMeta = meta
        return tag(stack)
    }

    override fun onUse(player: Player, item: ItemStack) {
        val game = gameManager.getGameForPlayer(player.uniqueId) ?: return
        val nearest = game.alivePlayers
            .filter { it.uuid != player.uniqueId }
            .mapNotNull { gp -> Bukkit.getPlayer(gp.uuid)?.location }
            .minByOrNull { it.distanceSquared(player.location) }
            ?: run {
                player.sendMessage("§cNo players found.")
                return
            }
        player.compassTarget = nearest
        player.sendMessage("§cTracking nearest player: §f${nearest.blockX}, ${nearest.blockY}, ${nearest.blockZ}")
    }
}
