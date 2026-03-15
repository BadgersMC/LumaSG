package net.lumalyte.lumasg.listeners

import net.badgersmc.nexus.annotations.PostConstruct
import net.badgersmc.nexus.annotations.Service
import net.lumalyte.lumasg.chest.items.CustomItem
import net.lumalyte.lumasg.game.GameManager
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.Plugin
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Service
class CustomItemListener(
    private val plugin: Plugin,
    private val gameManager: GameManager,
    private val customItems: List<CustomItem>
) : Listener {

    // Per-player cooldowns in milliseconds
    private val cooldowns = ConcurrentHashMap<UUID, Long>()
    private val COOLDOWN_MS = 1_000L

    @PostConstruct
    fun register() {
        plugin.server.pluginManager.registerEvents(this, plugin)
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onInteract(event: PlayerInteractEvent) {
        val item = event.item ?: return
        val player = event.player

        // Only respond to right-click
        if (event.action != Action.RIGHT_CLICK_AIR &&
            event.action != Action.RIGHT_CLICK_BLOCK) return

        // Must be in a game
        gameManager.getGameForPlayer(player.uniqueId) ?: return

        val customItem = CustomItem.fromStack(item, customItems) ?: return

        // Cooldown check
        val now = System.currentTimeMillis()
        val lastUse = cooldowns[player.uniqueId] ?: 0L
        if (now - lastUse < COOLDOWN_MS) return
        cooldowns[player.uniqueId] = now

        event.isCancelled = true
        customItem.onUse(player, item)
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        cooldowns.remove(event.player.uniqueId)
    }
}
