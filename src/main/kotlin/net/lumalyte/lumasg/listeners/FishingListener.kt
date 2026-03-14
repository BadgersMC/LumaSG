package net.lumalyte.lumasg.listeners

import net.badgersmc.nexus.annotations.PostConstruct
import net.badgersmc.nexus.annotations.Service
import net.lumalyte.lumasg.game.GameManager
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerFishEvent
import org.bukkit.plugin.Plugin

@Service
class FishingListener(
    private val plugin: Plugin,
    private val gameManager: GameManager
) : Listener {

    @PostConstruct
    fun register() {
        plugin.server.pluginManager.registerEvents(this, plugin)
    }

    @EventHandler
    fun onFish(event: PlayerFishEvent) {
        // Only process fishing events during active games
        gameManager.getGameForPlayer(event.player.uniqueId) ?: return
        // Custom fishing loot or restriction logic goes here
    }
}
