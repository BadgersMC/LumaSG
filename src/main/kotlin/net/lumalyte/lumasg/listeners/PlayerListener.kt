package net.lumalyte.lumasg.listeners

import kotlinx.coroutines.launch
import net.badgersmc.nexus.annotations.PostConstruct
import net.badgersmc.nexus.annotations.Service
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.lumalyte.lumasg.domain.GamePhase
import net.lumalyte.lumasg.game.GameManager
import net.lumalyte.lumasg.statistics.StatisticsService
import org.bukkit.GameMode
import org.bukkit.entity.Firework
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerRespawnEvent
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

    // ── Entity damage (all sources) ──────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onEntityDamage(event: EntityDamageEvent) {
        val player = event.entity as? Player ?: return
        val game = gameManager.getGameForPlayer(player.uniqueId) ?: return

        // Firework damage prevention
        if (event.cause == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION && event is EntityDamageByEntityEvent) {
            val damager = event.damager
            if (damager is Firework && damager.hasMetadata("celebration_firework")) {
                event.isCancelled = true
                return
            }
        }

        when (game.phase) {
            is GamePhase.Waiting, is GamePhase.Countdown -> {
                // Cancel ALL damage during waiting/countdown
                event.isCancelled = true
            }
            is GamePhase.Grace -> {
                // During grace: cancel only PvP, allow environmental damage
                if (event is EntityDamageByEntityEvent) {
                    event.isCancelled = true
                    // Notify attacker if they are a player
                    val attacker = event.damageSource.causingEntity as? Player
                    attacker?.sendMessage(
                        Component.text("PvP is currently disabled!", NamedTextColor.RED)
                    )
                }
            }
            else -> { /* Active / Deathmatch / Ended — allow all damage */ }
        }
    }

    // ── PvP damage tracking ──────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onDamage(event: EntityDamageByEntityEvent) {
        val victim = event.entity as? Player ?: return
        val attacker = event.damageSource.causingEntity as? Player ?: return
        val game = gameManager.getGameForPlayer(victim.uniqueId) ?: return

        game.players[victim.uniqueId]?.let { it.damageTaken += event.finalDamage }
        game.players[attacker.uniqueId]?.let { it.damageDealt += event.finalDamage }
    }

    // ── Player death ─────────────────────────────────────────────────────

    @EventHandler
    fun onPlayerDeath(event: PlayerDeathEvent) {
        event.deathMessage(null)
        val game = gameManager.getGameForPlayer(event.entity.uniqueId) ?: return
        val killer = event.damageSource.causingEntity as? Player

        // Prevent normal death handling and clear drops
        event.isCancelled = true
        event.drops.clear()

        game.scope.launch {
            game.eliminate(event.entity.uniqueId)
            killer?.let {
                game.players[it.uniqueId]?.also { gp -> gp.kills++ }
                statsService.recordKill(it.uniqueId)
            }
        }

        game.broadcastDeathMessage(event.entity, killer)
    }

    // ── Player respawn → spectator mode ──────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    fun onPlayerRespawn(event: PlayerRespawnEvent) {
        val game = gameManager.getGameForPlayer(event.player.uniqueId) ?: return

        if (game.phase is GamePhase.Waiting) return

        // Set respawn location to the arena's first spawn point
        val spawnLocation = game.arena.spawnPoints.firstOrNull()?.toBukkit() ?: return
        event.respawnLocation = spawnLocation

        // Set to spectator mode 1 tick later
        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            event.player.gameMode = GameMode.SPECTATOR
        }, 1L)
    }

    // ── Block placement restrictions ─────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onBlockPlace(event: BlockPlaceEvent) {
        val game = gameManager.getGameForPlayer(event.player.uniqueId) ?: return

        when (game.phase) {
            is GamePhase.Waiting, is GamePhase.Countdown -> {
                event.isCancelled = true
            }
            is GamePhase.Grace, is GamePhase.Active, is GamePhase.Deathmatch -> {
                game.worldManager.trackPlacedBlock(event.block.location)
            }
            else -> { /* Ended — no special handling */ }
        }
    }

    // ── Player quit ──────────────────────────────────────────────────────

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val game = gameManager.getGameForPlayer(event.player.uniqueId) ?: return
        game.scope.launch {
            game.eliminate(event.player.uniqueId)
        }
    }
}
