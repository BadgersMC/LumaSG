package net.lumalyte.lumasg.game

import kotlinx.coroutines.*
import net.badgersmc.nexus.paper.BukkitDispatcher
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.title.Title
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

    private val eliminationOrder = mutableListOf<UUID>()

    var phase: GamePhase = GamePhase.Waiting
        private set

    private var startTime: Instant = Instant.now()

    private var spawnEnforcementJob: Job? = null

    val alivePlayers: List<GamePlayer>
        get() = _players.values.filter { it.isAlive }

    internal val worldManager = WorldManager(arena, config)
    private val scoreboard = GameScoreboard(arena, this, scope, bukkitDispatcher, config)

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
        eliminationOrder.add(0, uuid)
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

            startSpawnEnforcement()
            runCountdown()

            // Game start announcement
            spawnEnforcementJob?.cancel()
            spawnEnforcementJob = null
            withContext(bukkitDispatcher) {
                worldManager.removeBarriers()
                broadcastTitle(
                    mm.deserialize("<green><bold>Game Started!"),
                    mm.deserialize("<gray>Grace period has begun"),
                    Sound.ENTITY_PLAYER_LEVELUP
                )
            }

            runGracePhase()

            // PvP announcement
            withContext(bukkitDispatcher) {
                broadcastTitle(
                    mm.deserialize("<red><bold>Grace Period Ended!"),
                    mm.deserialize("<gray>PvP is now enabled!"),
                    Sound.ENTITY_ENDER_DRAGON_GROWL
                )
            }

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

    private fun startSpawnEnforcement() {
        spawnEnforcementJob = scope.launch {
            while (isActive) {
                delay(2_000)
                val currentPhase = phase
                if (currentPhase !is GamePhase.Waiting && currentPhase !is GamePhase.Countdown) {
                    break
                }
                withContext(bukkitDispatcher) {
                    for ((index, entry) in _players.entries.withIndex()) {
                        if (!entry.value.isAlive) continue
                        val player = Bukkit.getPlayer(entry.key) ?: continue
                        val spawn = arena.spawnPoints.getOrNull(index)?.toBukkit()
                            ?: arena.center.toBukkit()
                            ?: continue
                        if (player.location.distanceSquared(spawn) > 1.5 * 1.5) {
                            player.teleport(spawn)
                        }
                    }
                }
            }
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
            if (i in setOf(300, 180, 120, 60, 30, 10)) {
                withContext(bukkitDispatcher) { broadcastDeathmatchReminder(i) }
            }
            checkWinCondition()
            delay(1_000)
        }
        // Time's up — force deathmatch
    }

    private suspend fun runDeathmatch() {
        withContext(bukkitDispatcher) {
            worldManager.setupDeathmatch()

            // Teleport alive players to spawn points
            val alive = alivePlayers
            for ((index, gp) in alive.withIndex()) {
                val player = Bukkit.getPlayer(gp.uuid) ?: continue
                val spawn = arena.spawnPoints.getOrNull(index)?.toBukkit()
                    ?: arena.center.toBukkit()
                    ?: continue
                player.teleport(spawn)
            }

            broadcastTitle(
                mm.deserialize("<dark_red><bold>DEATHMATCH"),
                mm.deserialize("<red>Fight to the death!"),
                Sound.ENTITY_WITHER_SPAWN
            )
        }
        coroutineScope {
            launch { runDeathmatchTimer() }
        }
    }

    private suspend fun runDeathmatchTimer() {
        val dmSeconds = config.worldBorder.shrinkDurationSeconds.toInt()
        val quarter = dmSeconds / 4
        val half = dmSeconds / 2
        val threeQuarter = (dmSeconds * 3) / 4
        for (i in dmSeconds downTo 1) {
            phase = GamePhase.Deathmatch(i)
            val elapsed = dmSeconds - i
            if (elapsed == quarter || elapsed == half || elapsed == threeQuarter) {
                val percentThrough = (elapsed * 100) / dmSeconds
                withContext(bukkitDispatcher) {
                    broadcastBorderWarning("The border has shrunk to $percentThrough% — keep fighting!")
                }
            }
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

        // Reset world border before celebration
        withContext(bukkitDispatcher) {
            worldManager.resetBorderForCelebration()
        }

        val winnerKills = winner?.let { _players[it]?.kills } ?: 0
        runCelebration(winner, allParticipants(), winnerKills, config, plugin, bukkitDispatcher)

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
        spawnEnforcementJob?.cancel()
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

    private fun broadcastTitle(title: Component, subtitle: Component, sound: Sound) {
        val titleObj = Title.title(
            title, subtitle,
            Title.Times.times(Duration.ofMillis(500), Duration.ofMillis(2_000), Duration.ofMillis(1_000))
        )
        for (uuid in allParticipants()) {
            Bukkit.getPlayer(uuid)?.let { p ->
                p.showTitle(titleObj)
                p.playSound(p.location, sound, 1f, 1f)
            }
        }
    }

    private fun broadcastDeathmatchReminder(secondsRemaining: Int) {
        val minutes = secondsRemaining / 60
        val seconds = secondsRemaining % 60
        val timeStr = if (minutes > 0) "${minutes}m ${seconds}s" else "${seconds}s"
        val color = when {
            secondsRemaining >= 180 -> "yellow"
            secondsRemaining >= 30 -> "gold"
            else -> "red"
        }
        broadcast(mm.deserialize("<$color><bold>Deathmatch</bold> starts in <white>$timeStr<$color>!"))
    }

    private fun broadcastBorderWarning(message: String) {
        broadcast(mm.deserialize("<red><bold>Warning:</bold> <gray>$message"))
    }

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
