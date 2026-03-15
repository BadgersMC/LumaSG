package net.lumalyte.lumasg.listeners

import net.badgersmc.nexus.annotations.PostConstruct
import net.badgersmc.nexus.annotations.Service
import net.lumalyte.lumasg.game.GameManager
import org.bukkit.Material
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.Sound
import org.bukkit.entity.TNTPrimed
import org.bukkit.event.EventPriority
import org.bukkit.event.block.Action
import org.bukkit.event.entity.EntityExplodeEvent
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

    @EventHandler(priority = EventPriority.HIGH)
    fun onExplosion(event: EntityExplodeEvent) {
        val tnt = event.entity as? TNTPrimed ?: return
        if (!tnt.hasMetadata("lumasg_fire_bomb")) return

        event.isCancelled = true // prevent block damage
        val center = event.location
        val radius = 4
        for (x in -radius..radius) {
            for (y in -2..2) {
                for (z in -radius..radius) {
                    val loc = center.clone().add(x.toDouble(), y.toDouble(), z.toDouble())
                    if (loc.block.type == Material.AIR) {
                        loc.block.type = Material.FIRE
                    }
                }
            }
        }
        center.world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1f, 1f)
    }
}
