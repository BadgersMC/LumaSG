package net.lumalyte.lumasg.game

import kotlinx.coroutines.*
import net.badgersmc.nexus.paper.BukkitDispatcher
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.lumalyte.lumasg.config.LumaSGConfig
import net.lumalyte.lumasg.discord.DiscordService
import net.lumalyte.lumasg.discord.GameEmbed
import net.lumalyte.lumasg.domain.Arena
import net.lumalyte.lumasg.domain.GameMode
import net.lumalyte.lumasg.domain.GamePhase
import net.lumalyte.lumasg.statistics.StatisticsService
import org.bukkit.Bukkit
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

private val mm = MiniMessage.miniMessage()

/**
 * A single game instance.
 *
 * Each game owns a child CoroutineScope supervised under the plugin scope.
 * When a game ends (normally or via exception), its scope is cancelled, cleaning up
 * all timers, border animations, and background tasks automatically.
 *
 * Multiple games can run in parallel — they do not share state.
 */
class Game(
    val id: UUID = UUID.randomUUID(),
    val arena: Arena,
    val mode: GameMode,
    parentScope: CoroutineScope,
    private val bukkitDispatcher: BukkitDispatcher,
    private val statisticsService: StatisticsService,
    private val playerStateManager: PlayerStateManager,
    private val config: LumaSGConfig,
    private val discordService: DiscordService?,
    private val plugin: Plugin
) {
    /** Child scope — supervised so game failure doesn't kill the plugin scope. */
    val scope = CoroutineScope(
        parentScope.coroutineContext +
        SupervisorJob(parentScope.coroutineContext.job) +
        CoroutineName("game-${arena.name}-$id")
    )

    private val _players = ConcurrentHashMap<UUID, GamePlayer>()
    val players: Map<UUID, GamePlayer> get() = _players

    private val spectators = ConcurrentHashMap.newKeySet<UUID>()

    var phase: GamePhase = GamePhase.Waiting
        private set

    private var startTime: Instant = Instant.now()

    val alivePlayers: List<GamePlayer>
        get() = _players.values.filter { it.isAlive }

    internal val worldManager = WorldManager(arena, config)
    private val scoreboard = GameScoreboard(arena, this, scope, bukkitDispatcher)

    // ── Player management ─────────────────────────────────────────────────

    fun addPlayer(player: Player) {
        val spawn = arena.spawnPoints.getOrNull(_players.size)?.toBukkit()
            ?: arena.center.toBukkit()
            ?: player.location
        playerStateManager.saveAndPrepare(player, spawn)
        _players[player.uniqueId] = player.toGamePlayer()
        scoreboard.addPlayer(player)
    }

    fun removePlayer(uuid: UUID) {
        _players.remove(uuid)
        spectators.remove(uuid)
        Bukkit.getPlayer(uuid)?.let { p ->
            scoreboard.removePlayer(p)
            playerStateManager.restore(p)
        }
    }

    fun eliminate(uuid: UUID) {
        _players[uuid]?.isAlive = false
        spectators.add(uuid)
        Bukkit.getPlayer(uuid)?.let { p ->
            playerStateManager.makeSpectator(p)
        }
    }

    private fun allParticipants(): Collection<UUID> = _players.keys + spectators

    // ── Lifecycle ─────────────────────────────────────────────────────────

    /** Launch the full game lifecycle as a coroutine on the game scope. */
    fun launch() {
        scope.launch { runLifecycle() }
    }

    private suspend fun runLifecycle() {
        try {
            withContext(bukkitDispatcher) {
                worldManager.setup()
                scoreboard.start()
                discordService?.announce(
                    GameEmbed.gameStarted(arena.name, players.size, mode.displayName)
                )
            }

            runCountdown()

            withContext(bukkitDispatcher) { worldManager.removeBarriers() }
            runGracePhase()
            runActivePhase()
            runDeathmatch()

        } catch (e: GameEndSignal) {
            withContext(NonCancellable) { endGame(e.winner) }
        } catch (e: CancellationException) {
            withContext(NonCancellable) { cleanup() }
        } catch (e: Exception) {
            withContext(NonCancellable) { cleanup() }
            throw e
        }
    }

    private suspend fun runCountdown() {
        for (i in config.countdownSeconds downTo 1) {
            phase = GamePhase.Countdown(i)
            if (i <= 5 || i == 10 || i == 30) {
                withContext(bukkitDispatcher) { broadcastCountdown(i) }
            }
            delay(1_000)
        }
    }

    private suspend fun runGracePhase() {
        startTime = Instant.now()
        for (i in config.gracePeriodSeconds downTo 1) {
            phase = GamePhase.Grace(i)
            if (i == 30 || i == 10 || i <= 5) {
                withContext(bukkitDispatcher) { broadcastGraceWarning(i) }
            }
            checkWinCondition()
            delay(1_000)
        }
    }

    private suspend fun runActivePhase() {
        val totalSeconds = config.maxGameMinutes * 60
        for (i in totalSeconds downTo 1) {
            phase = GamePhase.Active(i)
            checkWinCondition()
            delay(1_000)
        }
        // Time's up — force deathmatch
    }

    private suspend fun runDeathmatch() {
        withContext(bukkitDispatcher) { worldManager.setupDeathmatch() }
        coroutineScope {
            launch { runDeathmatchTimer() }
        }
    }

    private suspend fun runDeathmatchTimer() {
        val dmSeconds = config.worldBorder.shrinkDurationSeconds.toInt()
        for (i in dmSeconds downTo 1) {
            phase = GamePhase.Deathmatch(i)
            checkWinCondition()
            delay(1_000)
        }
        throw GameEndSignal(winner = null)
    }

    private suspend fun checkWinCondition() {
        val alive = alivePlayers
        if (alive.size <= 1) {
            throw GameEndSignal(winner = alive.firstOrNull()?.uuid)
        }
    }

    private suspend fun endGame(winner: UUID?) {
        phase = GamePhase.Ended(winner)

        runCelebration(winner, allParticipants(), plugin, bukkitDispatcher)

        withContext(bukkitDispatcher) {
            restoreAllPlayers()
            worldManager.cleanup()
            scoreboard.resetAll()
            discordService?.announce(
                GameEmbed.gameEnded(
                    winner = winner?.let { Bukkit.getOfflinePlayer(it).name },
                    arena = arena.name,
                    duration = formatDuration(startTime)
                )
            )
        }
        persistStats(winner)
        scope.cancel("Game ended")
    }

    private suspend fun cleanup() {
        withContext(bukkitDispatcher) {
            restoreAllPlayers()
            worldManager.cleanup()
            scoreboard.resetAll()
        }
    }

    private suspend fun persistStats(winner: UUID?) {
        statisticsService.recordGameEnd(this, winner)
    }

    private fun formatDuration(since: Instant): String {
        val d = Duration.between(since, Instant.now())
        val m = d.toMinutes()
        val s = d.seconds % 60
        return "${m}m ${s}s"
    }

    // ── Bukkit API calls (always called via withContext(bukkitDispatcher)) ──

    private fun broadcastCountdown(seconds: Int) {
        val msg = if (seconds <= 5) {
            mm.deserialize("<gold>Game starting in <yellow><bold>$seconds</bold><gold>!")
        } else {
            mm.deserialize("<yellow>Game starting in <white>$seconds<yellow> seconds.")
        }
        broadcast(msg)
        allParticipants().forEach { uuid ->
            Bukkit.getPlayer(uuid)?.let { p ->
                p.playSound(
                    p.location,
                    if (seconds <= 5) Sound.BLOCK_NOTE_BLOCK_PLING else Sound.UI_BUTTON_CLICK,
                    1f, if (seconds <= 5) 1.5f else 1f
                )
            }
        }
    }

    private fun broadcastGraceWarning(seconds: Int) {
        broadcast(mm.deserialize("<green>Grace period ends in <white>$seconds<green> seconds!"))
    }

    fun broadcastDeathMessage(victim: Player, killer: Player?) {
        val msg = deathMessage(victim, killer)
        broadcast(msg)
        killer?.let { k ->
            val gp = _players[k.uniqueId]
            k.sendMessage(killNotification(victim, gp?.kills ?: 0))
        }
    }

    private fun broadcast(msg: Component) {
        for (uuid in allParticipants()) {
            Bukkit.getPlayer(uuid)?.sendMessage(msg)
        }
    }

    private fun restoreAllPlayers() {
        for (uuid in _players.keys + spectators) {
            Bukkit.getPlayer(uuid)?.let { playerStateManager.restore(it) }
        }
    }
}

/** Thrown from within the lifecycle coroutines to signal normal game end. */
private class GameEndSignal(val winner: UUID?) : Exception()
