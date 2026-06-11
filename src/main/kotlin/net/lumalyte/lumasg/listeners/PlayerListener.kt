package net.lumalyte.lumasg.listeners

import kotlinx.coroutines.launch
import net.badgersmc.nexus.annotations.PostConstruct
import net.badgersmc.nexus.annotations.Service
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.minimessage.MiniMessage
import net.lumalyte.lumasg.config.LumaSGConfig
import net.lumalyte.lumasg.domain.GamePhase
import net.lumalyte.lumasg.game.GameManager
import net.lumalyte.lumasg.statistics.StatisticsService
import net.lumalyte.lumasg.util.cache.PlayerDataCache
import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.entity.Firework
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityExplodeEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerRespawnEvent
import org.bukkit.event.player.PlayerTeleportEvent
import org.bukkit.plugin.java.JavaPlugin

@Service
class PlayerListener(
    private val plugin: JavaPlugin,
    private val gameManager: GameManager,
    private val statsService: StatisticsService,
    private val config: LumaSGConfig,
    private val playerDataCache: PlayerDataCache
) : Listener {

    private val mm = MiniMessage.miniMessage()

    @PostConstruct
    fun register() {
        plugin.server.pluginManager.registerEvents(this, plugin)
    }

    // ── Entity damage (all sources) ──────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    @Suppress("CyclomaticComplexMethod") // damage rules: phase/pvp/friendly-fire guard clauses
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
                event.isCancelled = true
            }
            is GamePhase.Grace -> {
                if (event is EntityDamageByEntityEvent) {
                    event.isCancelled = true
                    val attacker = event.damageSource.causingEntity as? Player
                    attacker?.sendMessage(
                        Component.text("PvP is currently disabled!", NamedTextColor.RED)
                    )
                }
            }
            else -> {
                // Friendly fire check
                if (event is EntityDamageByEntityEvent && !config.game.teams.friendlyFire && game.mode.teamSize > 1) {
                    val attacker = event.damageSource.causingEntity as? Player
                    if (attacker != null && game.teamManager.areTeammates(attacker, player)) {
                        event.isCancelled = true
                        return
                    }
                }
            }
        }
    }

    // ── PvP damage tracking ──────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onDamage(event: EntityDamageByEntityEvent) {
        val victim = event.entity as? Player ?: return
        val attacker = event.damageSource.causingEntity as? Player ?: return
        val game = gameManager.getGameForPlayer(victim.uniqueId) ?: return

        if (config.statistics.trackDamage) {
            game.players[victim.uniqueId]?.let { it.damageTaken += event.finalDamage }
            game.players[attacker.uniqueId]?.let { it.damageDealt += event.finalDamage }
        }
    }

    // ── Player death ─────────────────────────────────────────────────────

    @EventHandler
    fun onPlayerDeath(event: PlayerDeathEvent) {
        event.deathMessage(null)
        val game = gameManager.getGameForPlayer(event.entity.uniqueId) ?: return
        val killer = event.damageSource.causingEntity as? Player

        event.isCancelled = true
        event.drops.clear()

        game.scope.launch {
            game.eliminate(event.entity.uniqueId)
            killer?.let {
                game.players[it.uniqueId]?.also { gp -> gp.kills++ }
                statsService.recordKill(it.uniqueId)
                if (config.rewards.enabled && config.rewards.killCommand.isNotEmpty()) {
                    val cmd = config.rewards.killCommand
                        .replace("<player>", it.name)
                        .replace("<kills>", (game.players[it.uniqueId]?.kills ?: 0).toString())
                    plugin.server.dispatchCommand(plugin.server.consoleSender, cmd)
                }
            }
        }

        game.broadcastDeathMessage(event.entity, killer)
    }

    // ── Player respawn → spectator mode ──────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    fun onPlayerRespawn(event: PlayerRespawnEvent) {
        val game = gameManager.getGameForPlayer(event.player.uniqueId) ?: return

        if (game.phase is GamePhase.Waiting) return

        val spawnLocation = game.arena.spawnPoints.firstOrNull()?.toBukkit() ?: return
        event.respawnLocation = spawnLocation

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
            else -> {}
        }
    }

    // ── Block break restrictions ─────────────────────────────────────────

    companion object {
        private val BREAKABLE_BLOCKS = setOf(
            Material.OAK_LEAVES, Material.BIRCH_LEAVES,
            Material.SPRUCE_LEAVES, Material.JUNGLE_LEAVES,
            Material.ACACIA_LEAVES, Material.DARK_OAK_LEAVES,
            Material.MANGROVE_LEAVES, Material.CHERRY_LEAVES,
            Material.AZALEA_LEAVES, Material.FLOWERING_AZALEA_LEAVES,
            Material.SHORT_GRASS, Material.TALL_GRASS,
            Material.FERN, Material.LARGE_FERN,
            Material.DEAD_BUSH
        )
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onBlockBreak(event: BlockBreakEvent) {
        val game = gameManager.getGameForPlayer(event.player.uniqueId) ?: return
        val phase = game.phase
        if (phase !is GamePhase.Grace && phase !is GamePhase.Active && phase !is GamePhase.Deathmatch) {
            event.isCancelled = true
            return
        }
        if (event.block.type !in BREAKABLE_BLOCKS) {
            event.isCancelled = true
        }
    }

    // ── Teleport restriction during active game ──────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    fun onPlayerTeleport(event: PlayerTeleportEvent) {
        val player = event.player
        val game = gameManager.getGameForPlayer(player.uniqueId) ?: return
        val phase = game.phase

        if (phase is GamePhase.Waiting || phase is GamePhase.Ended) return

        // Allow internal game teleports (spawn enforcement, deathmatch teleport)
        if (event.cause == PlayerTeleportEvent.TeleportCause.PLUGIN) return

        val center = game.arena.center.toBukkit() ?: return
        val to = event.to
        val distSq = to.distanceSquared(center)
        if (distSq > game.arena.radius * game.arena.radius) {
            event.isCancelled = true
            player.sendMessage(mm.deserialize("<red>You cannot teleport outside the arena during a game!"))
        }
    }

    // ── Entity explosion block damage prevention ─────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    fun onEntityExplode(event: EntityExplodeEvent) {
        val entity = event.entity

        // Prevent celebration firework block damage
        if (entity.hasMetadata("celebration_firework")) {
            event.blockList().clear()
            return
        }

        // Prevent all firework block damage in game areas
        if (entity is Firework) {
            val game = gameManager.getGameAtLocation(entity.location)
            if (game != null) {
                event.blockList().clear()
            }
        }
    }

    // ── Player reconnection ──────────────────────────────────────────────

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        // Preload player data into cache for fast access
        playerDataCache.preloadPlayerData(event.player)

        val game = gameManager.getDisconnectedGame(event.player.uniqueId) ?: return
        val phase = game.phase
        val canReconnect = phase is GamePhase.Waiting || phase is GamePhase.Countdown
            || (config.game.allowReconnect && (phase is GamePhase.Grace || phase is GamePhase.Active || phase is GamePhase.Deathmatch))
        if (canReconnect && game.reconnectPlayer(event.player)) {
            event.player.sendMessage(mm.deserialize("<green>Reconnected to your game!"))
        }
    }

    // ── Player quit — track disconnect instead of immediate elimination ──

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        // Invalidate cached player data on disconnect
        playerDataCache.invalidatePlayer(event.player.uniqueId)

        val game = gameManager.getGameForPlayer(event.player.uniqueId) ?: return
        game.handleDisconnect(event.player.uniqueId)
    }
}
