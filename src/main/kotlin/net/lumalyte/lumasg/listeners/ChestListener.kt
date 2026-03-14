package net.lumalyte.lumasg.listeners

import net.badgersmc.nexus.annotations.PostConstruct
import net.badgersmc.nexus.annotations.Service
import net.lumalyte.lumasg.game.GameManager
import org.bukkit.Material
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.plugin.Plugin

@Service
class ChestListener(
    private val plugin: Plugin,
    private val gameManager: GameManager
) : Listener {

    @PostConstruct
    fun register() {
        plugin.server.pluginManager.registerEvents(this, plugin)
    }

    @EventHandler
    fun onChestOpen(event: PlayerInteractEvent) {
        if (event.action != Action.RIGHT_CLICK_BLOCK) return
        val block = event.clickedBlock ?: return
        if (block.type != Material.CHEST && block.type != Material.TRAPPED_CHEST) return
        val game = gameManager.getGameForPlayer(event.player.uniqueId) ?: return
        // Game is active — chest open tracking or loot refill logic can go here
    }
}
