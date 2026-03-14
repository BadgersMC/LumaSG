package net.lumalyte.lumasg.game

import kotlinx.coroutines.CoroutineScope
import net.badgersmc.nexus.annotations.Service
import net.badgersmc.nexus.paper.BukkitDispatcher
import net.lumalyte.lumasg.config.LumaSGConfig
import net.lumalyte.lumasg.discord.DiscordService
import net.lumalyte.lumasg.domain.Arena
import net.lumalyte.lumasg.domain.GameMode
import net.lumalyte.lumasg.statistics.StatisticsService
import org.bukkit.plugin.Plugin
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
    private val plugin: Plugin
) {
    private val logger = LoggerFactory.getLogger(GameManager::class.java)
    private val activeGames = ConcurrentHashMap<UUID, Game>()

    /**
     * Create and launch a new game instance on the given arena.
     * Returns immediately — the game runs entirely in its own coroutine scope.
     */
    fun createGame(arena: Arena, mode: GameMode): Game {
        val game = Game(
            arena = arena,
            mode = mode,
            parentScope = nexusScope,
            bukkitDispatcher = bukkitDispatcher,
            statisticsService = statisticsService,
            playerStateManager = playerStateManager,
            config = config,
            discordService = discordService,
            plugin = plugin
        )
        activeGames[game.id] = game
        game.launch()
        logger.info("Game ${game.id} launched on arena '${arena.name}' (${mode.displayName})")
        return game
    }

    fun getGame(id: UUID): Game? = activeGames[id]

    fun getGameForPlayer(playerUuid: UUID): Game? =
        activeGames.values.firstOrNull { it.players.containsKey(playerUuid) }

    fun getAllActiveGames(): Collection<Game> = activeGames.values

    /** Called when a game scope completes or is cancelled. */
    fun onGameEnd(gameId: UUID) {
        activeGames.remove(gameId)
        logger.info("Game $gameId removed from active registry")
    }
}
