package net.lumalyte.lumasg.listeners

import net.badgersmc.nexus.annotations.PostConstruct
import net.badgersmc.nexus.annotations.Service
import net.lumalyte.lumasg.service.ArenaService
import org.bukkit.Material
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.plugin.Plugin

@Service
class AdminWandListener(
    private val plugin: Plugin,
    private val arenaService: ArenaService
) : Listener {

    @PostConstruct
    fun register() {
        plugin.server.pluginManager.registerEvents(this, plugin)
    }

    @EventHandler
    fun onInteract(event: PlayerInteractEvent) {
        val player = event.player
        if (!player.hasPermission("lumasg.admin")) return
        val item = player.inventory.itemInMainHand
        if (item.type != Material.BLAZE_ROD) return // setup wand material

        when (event.action) {
            Action.LEFT_CLICK_BLOCK -> {
                val block = event.clickedBlock ?: return
                player.sendMessage("§aSet position 1: §f${block.location}")
                // TODO: store position 1 in arena setup state
            }
            Action.RIGHT_CLICK_BLOCK -> {
                val block = event.clickedBlock ?: return
                player.sendMessage("§aSet position 2: §f${block.location}")
                // TODO: store position 2 in arena setup state
            }
            else -> return
        }
    }
}
