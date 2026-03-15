package net.lumalyte.lumasg.listeners

import kotlinx.coroutines.launch
import net.badgersmc.nexus.annotations.PostConstruct
import net.badgersmc.nexus.annotations.Service
import net.lumalyte.lumasg.game.GameManager
import net.lumalyte.lumasg.statistics.StatisticsService
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
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
        event.deathMessage(null) // suppress default death message
        val game = gameManager.getGameForPlayer(event.entity.uniqueId) ?: return
        val killer = event.damageSource.causingEntity as? Player

        // Eliminate and track kill
        game.scope.launch {
            game.eliminate(event.entity.uniqueId)
            killer?.let {
                game.players[it.uniqueId]?.also { gp -> gp.kills++ }
                statsService.recordKill(it.uniqueId)
            }
        }

        // Death message is broadcast on the main thread
        game.broadcastDeathMessage(event.entity, killer)
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onDamage(event: EntityDamageByEntityEvent) {
        val victim = event.entity as? Player ?: return
        val attacker = event.damageSource.causingEntity as? Player ?: return
        val game = gameManager.getGameForPlayer(victim.uniqueId) ?: return

        game.players[victim.uniqueId]?.let { it.damageTaken += event.finalDamage }
        game.players[attacker.uniqueId]?.let { it.damageDealt += event.finalDamage }
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val game = gameManager.getGameForPlayer(event.player.uniqueId) ?: return
        game.scope.launch {
            game.eliminate(event.player.uniqueId)
        }
    }
}
