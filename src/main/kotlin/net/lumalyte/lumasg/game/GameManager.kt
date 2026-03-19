package net.lumalyte.lumasg.game

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import net.badgersmc.nexus.annotations.PreDestroy
import net.badgersmc.nexus.annotations.Service
import net.badgersmc.nexus.paper.BukkitDispatcher
import net.lumalyte.lumasg.config.LumaSGConfig
import net.lumalyte.lumasg.discord.DiscordService
import net.lumalyte.lumasg.domain.Arena
import net.lumalyte.lumasg.domain.GameMode
import net.lumalyte.lumasg.domain.GamePhase
import net.lumalyte.lumasg.domain.LootMode
import net.lumalyte.lumasg.statistics.StatisticsService
import net.lumalyte.lumasg.util.cache.ScoreboardCache
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Service
class GameManager(
    private val nexusScope: CoroutineScope,
    private val bukkitDispatcher: BukkitDispatcher,
    private val statisticsService: StatisticsService,
    private val playerStateManager: PlayerStateManager,
    private val config: LumaSGConfig,
    private val discordService: DiscordService?,
    private val plugin: JavaPlugin,
    private val scoreboardCache: ScoreboardCache
) {
    private val logger = LoggerFactory.getLogger(GameManager::class.java)
    private val activeGames = ConcurrentHashMap<UUID, Game>()

    /**
     * Create and launch a new game instance on the given arena.
     * Returns immediately — the game runs entirely in its own coroutine scope.
     */
    fun createGame(arena: Arena, mode: GameMode, lootMode: LootMode? = null): Game {
        val resolvedLootMode = lootMode
            ?: LootMode.fromString(config.modes.defaultMode)
            ?: LootMode.MODERN
        val game = Game(
            arena = arena,
            mode = mode,
            lootMode = resolvedLootMode,
            parentScope = nexusScope,
            bukkitDispatcher = bukkitDispatcher,
            statisticsService = statisticsService,
            playerStateManager = playerStateManager,
            config = config,
            discordService = discordService,
            plugin = plugin,
            scoreboardCache = scoreboardCache
        )
        activeGames[game.id] = game

        // Auto-remove from registry when scope completes (normal end, stop, or crash)
        game.scope.coroutineContext[kotlinx.coroutines.Job]!!.invokeOnCompletion {
            activeGames.remove(game.id)
            logger.info("Game ${game.id} removed from active registry (arena '${arena.name}')")
        }

        game.launch()
        logger.info("Game ${game.id} launched on arena '${arena.name}' (${mode.displayName}, ${resolvedLootMode.name})")
        return game
    }

    fun getGame(id: UUID): Game? = activeGames[id]

    fun getGameForPlayer(playerUuid: UUID): Game? =
        activeGames.values.firstOrNull { it.players.containsKey(playerUuid) }

    fun getAllActiveGames(): Collection<Game> = activeGames.values

    fun getDisconnectedGame(uuid: UUID): Game? =
        activeGames.values.firstOrNull { uuid in it.disconnectedPlayers }

    fun getGameByArena(arenaName: String): Game? =
        activeGames.values.firstOrNull { it.arena.name.equals(arenaName, ignoreCase = true) }

    fun getGameByArena(arena: Arena): Game? = getGameByArena(arena.name)

    fun getWaitingGames(): List<Game> =
        activeGames.values.filter { it.phase is GamePhase.Waiting }

    fun getGameAtLocation(location: org.bukkit.Location): Game? =
        activeGames.values.firstOrNull { game ->
            val center = game.arena.center.toBukkit() ?: return@firstOrNull false
            center.world == location.world && location.distance(center) <= game.arena.radius
        }

    /** Find an available (waiting) game on the given arena. */
    fun findAvailableGame(arena: Arena): Game? =
        activeGames.values.firstOrNull {
            it.arena.name.equals(arena.name, ignoreCase = true) &&
            it.phase is GamePhase.Waiting &&
            it.getPlayerCount() < it.arena.maxPlayers
        }

    /** Find all games running on the given arena. */
    fun findGamesByArena(arena: Arena): List<Game> =
        activeGames.values.filter { it.arena.name.equals(arena.name, ignoreCase = true) }

    /** Whether a player is currently in any game. */
    fun isPlayerInGame(player: Player): Boolean = getGameForPlayer(player.uniqueId) != null

    fun isPlayerInGame(uuid: UUID): Boolean = getGameForPlayer(uuid) != null

    /** Number of active games. */
    fun getActiveGameCount(): Int = activeGames.size

    /** Total number of games (same as active, since finished games are removed). */
    fun getTotalGameCount(): Int = activeGames.size

    /** Number of active games on a specific arena. */
    fun getActiveGameCountInArena(arena: Arena): Int =
        findGamesByArena(arena).size

    /** Whether there are any active games on a specific arena. */
    fun hasActiveGames(arena: Arena): Boolean =
        activeGames.values.any { it.arena.name.equals(arena.name, ignoreCase = true) }

    /** Get or create a waiting game on the given arena. */
    fun getOrCreateGame(arena: Arena, mode: GameMode = GameMode.Solo, lootMode: LootMode? = null): Game =
        findAvailableGame(arena) ?: createGame(arena, mode, lootMode)

    /** Remove orphaned games (stuck in non-active states with no players). */
    fun cleanupOrphanedGames(): Int {
        val orphaned = activeGames.values.filter { game ->
            game.getPlayerCount() == 0 && game.phase !is GamePhase.Waiting
        }
        orphaned.forEach { game ->
            activeGames.remove(game.id)
            game.scope.cancel(CancellationException("Orphaned game cleanup"))
            logger.warn("Cleaned up orphaned game ${game.id}")
        }
        return orphaned.size
    }

    @PreDestroy
    fun shutdown() {
        logger.info("Shutting down GameManager — cancelling ${activeGames.size} active games")
        activeGames.values.forEach { game ->
            game.scope.cancel(CancellationException("Server shutdown"))
        }
        activeGames.clear()
    }
}
