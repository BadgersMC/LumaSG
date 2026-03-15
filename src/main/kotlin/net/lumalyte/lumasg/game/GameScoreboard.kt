package net.lumalyte.lumasg.game

import kotlinx.coroutines.*
import net.badgersmc.nexus.paper.BukkitDispatcher
import net.lumalyte.lumasg.config.LumaSGConfig
import net.lumalyte.lumasg.domain.Arena
import net.lumalyte.lumasg.domain.GamePhase
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.minimessage.MiniMessage
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
private val mm = MiniMessage.miniMessage()

class GameScoreboard(
    private val arena: Arena,
    private val game: Game,
    private val scope: CoroutineScope,
    private val bukkitDispatcher: BukkitDispatcher,
    private val config: LumaSGConfig = LumaSGConfig()
) {
    private val scoreboard: Scoreboard = Bukkit.getScoreboardManager().newScoreboard
    private val objective = scoreboard.registerNewObjective(
        "sg_${arena.name.take(8)}",
        Criteria.DUMMY,
        mm.deserialize(config.scoreboard.title)
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
            is GamePhase.Countdown  -> "§eStarting in §f${phase.secondsLeft}s"
            is GamePhase.Grace      -> "§aGrace: §f${formatTime(phase.secondsRemaining)}"
            is GamePhase.Active     -> "§fTime: §e${formatTime(phase.secondsRemaining)}"
            is GamePhase.Deathmatch -> "§c§lDeathmatch: §f${formatTime(phase.secondsRemaining)}"
            is GamePhase.Waiting    -> "§7Waiting..."
            is GamePhase.Ended      -> "§aGame Over"
        }

        val lines = mutableListOf(
            "§7§m--------------------",
            "§6Arena: §f${arena.displayName}",
            "§6Players: §f$aliveCount§7/§f$totalCount",
            timeStr
        )

        // Show kills for the viewing player (per-player line uses a unique suffix)
        // Since Bukkit scoreboards are shared, we show a generic kills line
        if (phase !is GamePhase.Waiting && phase !is GamePhase.Countdown) {
            lines.add("§6Kills: §f(see tab)")
        }

        // Show deathmatch-specific info
        if (phase is GamePhase.Deathmatch) {
            lines.add("§c§lBorder shrinking!")
        }

        lines.add("§7§m--------------------")
        return lines
    }

    private fun formatTime(seconds: Int): String {
        val m = seconds / 60
        val s = seconds % 60
        return "%02d:%02d".format(m, s)
    }
}
