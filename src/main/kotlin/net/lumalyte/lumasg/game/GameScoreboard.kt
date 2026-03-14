package net.lumalyte.lumasg.game

import kotlinx.coroutines.*
import net.badgersmc.nexus.paper.BukkitDispatcher
import net.lumalyte.lumasg.domain.Arena
import net.lumalyte.lumasg.domain.GamePhase
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.scoreboard.Criteria
import org.bukkit.scoreboard.DisplaySlot
import org.bukkit.scoreboard.Scoreboard
import java.util.UUID

/**
 * Per-game sidebar scoreboard.
 *
 * Lifecycle: [start] → updates every 2 s → [stop] + [resetPlayer] on cleanup.
 *
 * Call [addPlayer]/[removePlayer] as players join or are eliminated.
 * The coroutine loop writes to the Bukkit scoreboard API on the main thread.
 */
class GameScoreboard(
    private val arena: Arena,
    private val game: Game,
    private val scope: CoroutineScope,
    private val bukkitDispatcher: BukkitDispatcher
) {
    private val scoreboard: Scoreboard = Bukkit.getScoreboardManager().newScoreboard
    private val objective = scoreboard.registerNewObjective(
        "sg_${arena.name.take(8)}",
        Criteria.DUMMY,
        Component.text("Survival Games", NamedTextColor.GOLD, TextDecoration.BOLD)
    ).also { it.displaySlot = DisplaySlot.SIDEBAR }

    private val participants = mutableSetOf<UUID>()
    private var updateJob: Job? = null

    // ── Lifecycle ────────────────────────────────────────────────────────────

    fun start() {
        updateJob = scope.launch {
            while (isActive) {
                withContext(bukkitDispatcher) { render() }
                delay(2_000)
            }
        }
    }

    fun stop() {
        updateJob?.cancel()
    }

    // ── Player membership ────────────────────────────────────────────────────

    fun addPlayer(player: Player) {
        participants.add(player.uniqueId)
        player.scoreboard = scoreboard
    }

    fun removePlayer(player: Player) {
        participants.remove(player.uniqueId)
        resetPlayer(player)
    }

    fun resetPlayer(player: Player) {
        if (player.isOnline) {
            player.scoreboard = Bukkit.getScoreboardManager().mainScoreboard
        }
    }

    fun resetAll() {
        stop()
        for (id in participants) {
            Bukkit.getPlayer(id)?.let { resetPlayer(it) }
        }
        participants.clear()
    }

    // ── Rendering ─────────────────────────────────────────────────────────

    private fun render() {
        // Clear existing entries
        scoreboard.entries.toList().forEach { scoreboard.resetScores(it) }

        val lines = buildLines()
        lines.forEachIndexed { index, line ->
            objective.getScore(line).score = lines.size - index
        }

        // Push to all participants
        for (id in participants.toList()) {
            Bukkit.getPlayer(id)?.scoreboard = scoreboard
        }
    }

    private fun buildLines(): List<String> {
        val phase = game.phase
        val aliveCount = game.alivePlayers.size
        val totalCount = game.players.size

        val timeStr = when (phase) {
            is GamePhase.Countdown -> "§eStarting..."
            is GamePhase.Grace     -> "§aGrace: §f${formatTime(phase.secondsRemaining)}"
            is GamePhase.Active    -> "§fTime: §e${formatTime(phase.secondsRemaining)}"
            is GamePhase.Deathmatch -> "§cDeathmatch: §f${formatTime(phase.secondsRemaining)}"
            is GamePhase.Waiting   -> "§7Waiting..."
            is GamePhase.Ended     -> "§aGame Over"
        }

        return listOf(
            "§7§m--------------------",
            "§6Arena: §f${arena.displayName}",
            "§6Players: §f$aliveCount§7/§f$totalCount",
            timeStr,
            "§7§m--------------------"
        )
    }

    private fun formatTime(seconds: Int): String {
        val m = seconds / 60
        val s = seconds % 60
        return "%02d:%02d".format(m, s)
    }
}
