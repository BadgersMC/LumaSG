package net.lumalyte.lumasg.listeners

import net.badgersmc.nexus.annotations.PostConstruct
import net.badgersmc.nexus.annotations.Service
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
import org.bukkit.event.world.ChunkLoadEvent
import net.lumalyte.lumasg.config.LumaSGConfig
import org.bukkit.Bukkit
import org.bukkit.block.BlockState
import org.bukkit.plugin.java.JavaPlugin
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

@Service
class ChestListener(
    private val plugin: JavaPlugin,
    private val gameManager: GameManager,
    private val chestFiller: ConcurrentChestFiller,
    private val config: LumaSGConfig
) : Listener {

    private val logger = LoggerFactory.getLogger(ChestListener::class.java)

    /** Tracks which chests have already been filled (by block location hash). */
    private val filledChests = ConcurrentHashMap.newKeySet<Long>()

    @PostConstruct
    fun register() {
        plugin.server.pluginManager.registerEvents(this, plugin)
    }

    fun clearFilledChests() { filledChests.clear() }

    // ── Chunk-load filling ──────────────────────────────────────────────────

    /**
     * When a chunk loads within a player's sim distance during an active game,
     * fill all chests in the chunk immediately. By the time the player reaches
     * the chest, it's already populated — zero interaction delay.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    fun onChunkLoad(event: ChunkLoadEvent) {
        val chunk = event.chunk
        val worldName = chunk.world.name

        // Find the game running in this world (if any)
        val game = gameManager.getAllActiveGames().firstOrNull {
            it.arena.worldName == worldName
        } ?: return

        // Only fill during active gameplay phases
        val phase = game.phase
        if (phase is GamePhase.Waiting || phase is GamePhase.Countdown || phase is GamePhase.Ended) return

        // Scan chunk tile entities for chests and fill any that haven't been filled yet
        val tileEntities = chunk.tileEntities
        var filled = 0
        for (state: BlockState in tileEntities) {
            if (state !is Chest) continue
            val locKey = state.location.toBlockKey()
            if (!filledChests.add(locKey)) continue // already filled

            val tier = determineTier(state, game)
            chestFiller.fillChestSync(state, tier, game.lootMode)
            filled++
        }

        if (filled > 0) {
            logger.debug("Chunk [{}, {}] loaded — filled {} chests (arena '{}')",
                chunk.x, chunk.z, filled, game.arena.name)
        }
    }

    // ── Chest open (fallback + stat tracking) ───────────────────────────────

    /**
     * If a chest wasn't filled by chunk-load (edge case: already loaded chunk),
     * fill it synchronously on first open as a fallback.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    fun onChestOpen(event: InventoryOpenEvent) {
        val player = event.player as? Player ?: return
        val chest = event.inventory.holder as? Chest ?: return
        val game = gameManager.getGameForPlayer(player.uniqueId) ?: return
        val phase = game.phase
        if (phase is GamePhase.Waiting || phase is GamePhase.Ended) return

        val locKey = chest.location.toBlockKey()
        if (filledChests.add(locKey)) {
            // Wasn't filled by chunk-load — fill now as fallback
            val tier = determineTier(chest, game)
            chestFiller.fillChestSync(chest, tier, game.lootMode)
        }

        // Record stat
        if (config.statistics.trackChests) {
            game.players[player.uniqueId]?.let { it.chestsOpened++ }
        }
    }

    // ── Chest protection ────────────────────────────────────────────────────

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

    // ── Tier + refill ───────────────────────────────────────────────────────

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
