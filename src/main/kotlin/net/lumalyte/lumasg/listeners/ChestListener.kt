package net.lumalyte.lumasg.listeners

import kotlinx.coroutines.launch
import net.badgersmc.nexus.annotations.PostConstruct
import net.badgersmc.nexus.annotations.Service
import net.lumalyte.lumasg.chest.ChestManager
import net.lumalyte.lumasg.domain.GamePhase
import net.lumalyte.lumasg.game.Game
import net.lumalyte.lumasg.game.GameManager
import net.lumalyte.lumasg.util.cache.ConcurrentChestFiller
import org.bukkit.Material
import org.bukkit.block.Chest
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.inventory.InventoryOpenEvent
import net.kyori.adventure.text.minimessage.MiniMessage
import net.lumalyte.lumasg.config.LumaSGConfig
import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin
import java.util.concurrent.ConcurrentHashMap

@Service
class ChestListener(
    private val plugin: JavaPlugin,
    private val gameManager: GameManager,
    private val chestManager: ChestManager,
    private val chestFiller: ConcurrentChestFiller,
    private val config: LumaSGConfig
) : Listener {

    private val mm = MiniMessage.miniMessage()

    /** Tracks which chests have already been filled (by block location hash). */
    private val filledChests = ConcurrentHashMap.newKeySet<Long>()

    @PostConstruct
    fun register() {
        plugin.server.pluginManager.registerEvents(this, plugin)
    }

    fun clearFilledChests() { filledChests.clear() }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    fun onChestOpen(event: InventoryOpenEvent) {
        val player = event.player as? Player ?: return
        val chest = event.inventory.holder as? Chest ?: return
        val game = gameManager.getGameForPlayer(player.uniqueId) ?: return
        val phase = game.phase
        if (phase is GamePhase.Waiting || phase is GamePhase.Ended) return

        val locKey = chest.location.toBlockKey()
        if (!filledChests.add(locKey)) return // already filled

        // Fill on first open — determine tier by distance from arena center
        val tier = determineTier(chest, game)
        game.scope.launch {
            chestFiller.fillChestFromCache(chest, tier, game.lootMode)
        }

        // Record stat
        if (config.statistics.trackChests) {
            game.players[player.uniqueId]?.let { it.chestsOpened++ }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onChestBreak(event: BlockBreakEvent) {
        if (event.block.type != Material.CHEST && event.block.type != Material.TRAPPED_CHEST) return
        val game = gameManager.getGameForPlayer(event.player.uniqueId) ?: return
        if (game.phase !is GamePhase.Waiting) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onChestPlace(event: BlockPlaceEvent) {
        if (event.block.type != Material.CHEST && event.block.type != Material.TRAPPED_CHEST) return
        val game = gameManager.getGameForPlayer(event.player.uniqueId) ?: return
        if (game.phase !is GamePhase.Waiting) {
            event.isCancelled = true
        }
    }

    private fun determineTier(chest: Chest, game: Game): String {
        if (!config.chest.distanceBasedLoot) return "common"
        val center = game.arena.center.toBukkit() ?: return "common"
        val dist = chest.location.distance(center)
        return when {
            dist < 20 -> "rare"
            dist < 60 -> "uncommon"
            else -> "common"
        }
    }

    /**
     * Schedules a chest refill after the configured delay.
     * Called from Game.kt when transitioning to the ACTIVE phase.
     */
    fun scheduleRefill(game: Game) {
        if (!config.chest.refillEnabled) return
        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            val phase = game.phase
            if (phase is GamePhase.Active || phase is GamePhase.Deathmatch) {
                filledChests.clear()
                game.broadcastRefillMessage()
            }
        }, config.chest.refillTimeSeconds * 20L)
    }

    private fun org.bukkit.Location.toBlockKey(): Long =
        (blockX.toLong() shl 32) or (blockZ.toLong() and 0xFFFFFFFFL) xor (blockY.toLong() shl 48)
}
