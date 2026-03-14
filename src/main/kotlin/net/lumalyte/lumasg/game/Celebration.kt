package net.lumalyte.lumasg.game

import kotlinx.coroutines.*
import net.badgersmc.nexus.paper.BukkitDispatcher
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.title.Title
import org.bukkit.Bukkit
import org.bukkit.Color
import org.bukkit.FireworkEffect
import org.bukkit.Sound
import org.bukkit.entity.Firework
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import java.time.Duration
import java.util.UUID
import kotlin.random.Random

private val FIREWORK_COLORS = listOf(
    Color.RED, Color.BLUE, Color.GREEN,
    Color.YELLOW, Color.PURPLE, Color.ORANGE,
    Color.AQUA, Color.FUCHSIA, Color.LIME
)

private val FIREWORK_TYPES = listOf(
    FireworkEffect.Type.BALL, FireworkEffect.Type.BALL_LARGE,
    FireworkEffect.Type.STAR, FireworkEffect.Type.BURST,
    FireworkEffect.Type.CREEPER
)

/**
 * Runs the end-game celebration sequence.
 *
 * Must be called from a coroutine. Bukkit API calls are dispatched to the
 * main thread via [bukkitDispatcher]. Runs for ~5 seconds total.
 *
 * @param winnerUuid  UUID of the winner, or null if time ran out
 * @param participants UUIDs of all players + spectators in the game
 * @param plugin      Plugin instance for firework spawning
 */
suspend fun runCelebration(
    winnerUuid: UUID?,
    participants: Collection<UUID>,
    plugin: Plugin,
    bukkitDispatcher: BukkitDispatcher
) {
    val winner: Player? = winnerUuid?.let { Bukkit.getPlayer(it) }

    withContext(bukkitDispatcher) {
        val title = if (winner != null) {
            Title.title(
                Component.text("WINNER!", NamedTextColor.GOLD, TextDecoration.BOLD),
                Component.text(winner.name, NamedTextColor.YELLOW, TextDecoration.BOLD),
                Title.Times.times(Duration.ofMillis(500), Duration.ofMillis(3_000), Duration.ofMillis(1_000))
            )
        } else {
            Title.title(
                Component.text("GAME OVER", NamedTextColor.RED, TextDecoration.BOLD),
                Component.text("No winner this round", NamedTextColor.GRAY),
                Title.Times.times(Duration.ofMillis(500), Duration.ofMillis(3_000), Duration.ofMillis(1_000))
            )
        }

        for (id in participants) {
            val p = Bukkit.getPlayer(id) ?: continue
            p.showTitle(title)
            p.playSound(p.location, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f)
            p.playSound(p.location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 0.5f)
        }
    }

    // Fireworks every 250 ms for 5 seconds
    if (winner != null) {
        repeat(20) {
            delay(250)
            withContext(bukkitDispatcher) {
                if (!winner.isOnline) return@withContext
                val loc = winner.location.clone().add(
                    Random.nextDouble(-3.0, 3.0),
                    Random.nextDouble(2.0, 6.0),
                    Random.nextDouble(-3.0, 3.0)
                )
                val firework: Firework = loc.world.spawn(loc, Firework::class.java)
                firework.fireworkMeta = firework.fireworkMeta.also { meta ->
                    val effect = FireworkEffect.builder()
                        .withColor(FIREWORK_COLORS.random())
                        .withFade(FIREWORK_COLORS.random())
                        .with(FIREWORK_TYPES.random())
                        .trail(Random.nextBoolean())
                        .flicker(Random.nextBoolean())
                        .build()
                    meta.addEffect(effect)
                    meta.power = Random.nextInt(1, 3)
                }
                firework.setPersistent(false)
            }
        }
    }
}
