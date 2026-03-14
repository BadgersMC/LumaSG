package net.lumalyte.lumasg.game

import kotlinx.coroutines.*
import net.lumalyte.lumasg.domain.Arena
import net.lumalyte.lumasg.domain.GameMode
import net.lumalyte.lumasg.domain.GamePhase
import net.lumalyte.lumasg.persistence.repositories.PlayerStatsRepository
import net.badgersmc.nexus.paper.BukkitDispatcher
import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

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
    private val statsRepo: PlayerStatsRepository
) {
    /** Child scope — supervised so game failure doesn't kill the plugin scope. */
    val scope = CoroutineScope(
        parentScope.coroutineContext +
        SupervisorJob(parentScope.coroutineContext.job) +
        CoroutineName("game-${arena.name}-$id")
    )

    private val _players = ConcurrentHashMap<UUID, GamePlayer>()
    val players: Map<UUID, GamePlayer> get() = _players

    var phase: GamePhase = GamePhase.Waiting
        private set

    val alivePlayers: List<GamePlayer>
        get() = _players.values.filter { it.isAlive }

    // ─── Player management ─────────────────────────────────────────────────

    fun addPlayer(player: Player) {
        _players[player.uniqueId] = player.toGamePlayer()
    }

    fun removePlayer(uuid: UUID) {
        _players.remove(uuid)
    }

    fun eliminate(uuid: UUID) {
        _players[uuid]?.isAlive = false
    }

    // ─── Lifecycle ─────────────────────────────────────────────────────────

    /** Launch the full game lifecycle as a coroutine on the game scope. */
    fun launch() {
        scope.launch { runLifecycle() }
    }

    private suspend fun runLifecycle() {
        try {
            runCountdown()
            runGracePhase()
            runActivePhase()
            runDeathmatch()
        } catch (e: CancellationException) {
            // Scope was cancelled externally (e.g. server shutdown) — clean up gracefully
            withContext(NonCancellable) { cleanup() }
        } catch (e: Exception) {
            withContext(NonCancellable) { cleanup() }
            throw e
        }
    }

    private suspend fun runCountdown() {
        phase = GamePhase.Countdown
        for (i in 10 downTo 1) {
            withContext(bukkitDispatcher) { broadcastCountdown(i) }
            delay(1_000)
        }
    }

    private suspend fun runGracePhase() {
        val config = 60 // seconds — injected via config in real impl
        phase = GamePhase.Grace(config)
        for (i in config downTo 1) {
            phase = GamePhase.Grace(i)
            if (i == 30 || i == 10 || i <= 5) {
                withContext(bukkitDispatcher) { broadcastGraceWarning(i) }
            }
            delay(1_000)
        }
    }

    private suspend fun runActivePhase() {
        val config = 600 // 10 minutes
        for (i in config downTo 1) {
            phase = GamePhase.Active(i)
            checkWinCondition()
            delay(1_000)
        }
    }

    private suspend fun runDeathmatch() {
        phase = GamePhase.Deathmatch(120)
        coroutineScope {
            launch { shrinkBorder() }
            launch { runDeathmatchTimer() }
        }
    }

    private suspend fun shrinkBorder() {
        val steps = 120
        repeat(steps) {
            withContext(bukkitDispatcher) { updateWorldBorder(it, steps) }
            delay(1_000)
        }
    }

    private suspend fun runDeathmatchTimer() {
        for (i in 120 downTo 1) {
            phase = GamePhase.Deathmatch(i)
            checkWinCondition()
            delay(1_000)
        }
        endGame(winner = null)
    }

    private suspend fun checkWinCondition() {
        val alive = alivePlayers
        if (alive.size <= 1) {
            endGame(winner = alive.firstOrNull()?.uuid)
        }
    }

    private suspend fun endGame(winner: UUID?) {
        phase = GamePhase.Ended(winner)
        withContext(bukkitDispatcher) {
            broadcastWinner(winner)
            teleportPlayersToLobby()
        }
        persistStats()
        scope.cancel("Game ended")
    }

    private suspend fun persistStats() {
        // Persist per-game stats for all players — runs on IO dispatcher via dbQuery
        _players.values.forEach { _ -> }
    }

    private suspend fun cleanup() {
        withContext(bukkitDispatcher) { teleportPlayersToLobby() }
    }

    // ─── Bukkit API calls (only called via withContext(bukkitDispatcher)) ──

    private fun broadcastCountdown(seconds: Int) { /* player.sendMessage() calls */ }
    private fun broadcastGraceWarning(seconds: Int) { /* player.sendMessage() calls */ }
    private fun updateWorldBorder(step: Int, totalSteps: Int) { /* world.worldBorder calls */ }
    private fun broadcastWinner(winner: UUID?) { /* broadcast message */ }
    private fun teleportPlayersToLobby() { /* player.teleport() calls */ }
}
