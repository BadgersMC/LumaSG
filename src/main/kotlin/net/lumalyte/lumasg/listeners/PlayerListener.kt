package net.lumalyte.lumasg.listeners

import kotlinx.coroutines.launch
import net.badgersmc.nexus.annotations.PostConstruct
import net.badgersmc.nexus.annotations.Service
import net.lumalyte.lumasg.game.GameManager
import net.lumalyte.lumasg.statistics.StatisticsService
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.Plugin

@Service
class PlayerListener(
    private val plugin: Plugin,
    private val gameManager: GameManager,
    private val statsService: StatisticsService
) : Listener {

    @PostConstruct
    fun register() {
        plugin.server.pluginManager.registerEvents(this, plugin)
    }

    @EventHandler
    fun onPlayerDeath(event: PlayerDeathEvent) {
        val game = gameManager.getGameForPlayer(event.entity.uniqueId) ?: return
        game.scope.launch {
            game.eliminate(event.entity.uniqueId)
            val killer = event.damageSource.causingEntity as? Player ?: return@launch
            statsService.recordKill(killer.uniqueId)
            game.players[killer.uniqueId]?.also { it.kills++ }
        }
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val game = gameManager.getGameForPlayer(event.player.uniqueId) ?: return
        game.scope.launch {
            game.eliminate(event.player.uniqueId)
        }
    }
}
