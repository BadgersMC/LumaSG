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

    /**
     * Tracks which chests have already been filled, keyed by "arenaName:blockKey" so two
     * arenas sharing a world keep independent fill state (H13).
     */
    private val filledChests = ConcurrentHashMap.newKeySet<String>()

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

        // All games active (in a fillable phase) in this world. Multiple arenas can share
        // a world, so we resolve the owning game per chest by arena bounds (H13).
        val games = gameManager.getAllActiveGames().filter { g ->
            g.arena.worldName == worldName &&
                g.phase !is GamePhase.Waiting &&
                g.phase !is GamePhase.Countdown &&
                g.phase !is GamePhase.Ended
        }
        if (games.isEmpty()) return

        // Scan chunk tile entities for chests and fill any that haven't been filled yet
        val tileEntities = chunk.tileEntities
        var filled = 0
        for (state: BlockState in tileEntities) {
            if (state !is Chest) continue
            // Only fill a chest with the game whose arena bounds actually contain it.
            val game = games.firstOrNull { g ->
                val center = g.arena.center.toBukkit() ?: return@firstOrNull false
                state.location.world == center.world &&
                    state.location.distanceSquared(center) <= g.arena.radius * g.arena.radius
            } ?: continue

            val locKey = "${game.arena.name}:${state.location.toBlockKey()}"
            if (!filledChests.add(locKey)) continue // already filled

            val tier = determineTier(state, game)
            chestFiller.fillChestSync(state, tier, game.lootMode)
            filled++
        }

        if (filled > 0) {
            logger.debug("Chunk [{}, {}] loaded — filled {} chests in world '{}'",
                chunk.x, chunk.z, filled, worldName)
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

        val locKey = "${game.arena.name}:${chest.location.toBlockKey()}"
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
                // Only reset this arena's fill tracking — a shared-world arena's refill
                // must not wipe another arena's state (keeps H13 isolation intact).
                val prefix = "${game.arena.name}:"
                filledChests.removeIf { it.startsWith(prefix) }
                game.broadcastRefillMessage()
            }
        }, config.chest.refillTimeSeconds * 20L)
    }

    private fun org.bukkit.Location.toBlockKey(): Long =
        (blockX.toLong() shl 32) or (blockZ.toLong() and 0xFFFFFFFFL) xor (blockY.toLong() shl 48)
}
