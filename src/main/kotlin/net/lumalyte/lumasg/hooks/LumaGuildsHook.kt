package net.lumalyte.lumasg.hooks

import net.badgersmc.nexus.annotations.PostConstruct
import net.badgersmc.nexus.annotations.Service
import net.lumalyte.lumasg.domain.GamePhase
import net.lumalyte.lumasg.game.GameManager
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.plugin.Plugin

/**
 * Overrides LumaGuilds PvP protections during active Survival Games.
 *
 * LumaGuilds' CombatService cancels EntityDamageByEntityEvent for same-guild,
 * alliance, and peaceful-mode players. This handler runs at HIGHEST priority
 * to un-cancel damage when both players are in an active game during
 * ACTIVE or DEATHMATCH phases.
 */
@Service
class LumaGuildsHook(
    private val plugin: Plugin,
    private val gameManager: GameManager
) : PluginHook, Listener {

    override val pluginName = "LumaGuilds"

    @PostConstruct
    fun register() {
        if (!isAvailable()) {
            plugin.logger.info("LumaGuilds not found — guild PvP override disabled.")
            return
        }
        plugin.server.pluginManager.registerEvents(this, plugin)
        plugin.logger.info("LumaGuilds integration enabled — PvP overrides active during games.")
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onEntityDamageByEntity(event: EntityDamageByEntityEvent) {
        if (!event.isCancelled) return // only intervene if something cancelled it

        val victim = event.entity as? Player ?: return
        val attacker = event.damageSource.causingEntity as? Player ?: return

        val victimGame = gameManager.getGameForPlayer(victim.uniqueId) ?: return
        val attackerGame = gameManager.getGameForPlayer(attacker.uniqueId) ?: return

        // Both must be in the same game
        if (victimGame.id != attackerGame.id) return

        // Only un-cancel during PvP-enabled phases
        val phase = victimGame.phase
        if (phase !is GamePhase.Active && phase !is GamePhase.Deathmatch) return

        // Don't override if they're on the same SG team (friendly fire stays blocked)
        val attackerTeam = victimGame.teamManager.getTeamForPlayer(attacker.uniqueId)
        val victimTeam = victimGame.teamManager.getTeamForPlayer(victim.uniqueId)
        if (attackerTeam != null && victimTeam != null && attackerTeam.id == victimTeam.id) return

        event.isCancelled = false
    }
}
