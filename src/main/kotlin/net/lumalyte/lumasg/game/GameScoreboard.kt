package net.lumalyte.lumasg.game

import kotlinx.coroutines.*
import net.badgersmc.nexus.paper.BukkitDispatcher
import net.lumalyte.lumasg.config.LumaSGConfig
import net.lumalyte.lumasg.domain.Arena
import net.lumalyte.lumasg.domain.GamePhase
import net.lumalyte.lumasg.util.cache.ScoreboardCache
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
    private val config: LumaSGConfig = LumaSGConfig(),
    private val scoreboardCache: ScoreboardCache? = null
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
        if (!config.scoreboard.enabled) return
        updateJob = scope.launch {
            while (isActive) {
                withContext(bukkitDispatcher) { render() }
                delay(config.scoreboard.updateInterval.toLong() * 50) // ticks → ms
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

        val phaseStr = when (phase) {
            is GamePhase.Countdown  -> "§eStarting in §f${phase.secondsLeft}s"
            is GamePhase.Grace      -> "§aGrace: §f${formatTime(phase.secondsRemaining)}"
            is GamePhase.Active     -> "§fTime: §e${formatTime(game.getTimeRemaining())}"
            is GamePhase.Deathmatch -> "§c§lDeathmatch: §f${formatTime(phase.secondsRemaining)}"
            is GamePhase.Waiting    -> "§7Waiting..."
            is GamePhase.Ended      -> "§aGame Over"
        }

        val lines = mutableListOf<String>()

        for (template in config.scoreboard.lines) {
            lines.add(
                template
                    .replace("<alive>", aliveCount.toString())
                    .replace("<total>", totalCount.toString())
                    .replace("<time>", phaseStr)
                    .replace("<arena>", arena.displayName)
            )
        }

        if (phase is GamePhase.Deathmatch) {
            for (dmLine in config.scoreboard.deathmatchLines) {
                lines.add(dmLine)
            }
        }

        return lines.map { legacyFromMiniMessage(it) }
    }

    /** Convert MiniMessage to legacy section-codes for scoreboard string entries. */
    private fun legacyFromMiniMessage(input: String): String {
        if (input.contains("§")) return input
        val component = mm.deserialize(input)
        return net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
            .legacySection().serialize(component)
    }

    private fun formatTime(seconds: Int): String =
        scoreboardCache?.getCachedTimeFormat(seconds)
            ?: "%02d:%02d".format(seconds / 60, seconds % 60)
}
