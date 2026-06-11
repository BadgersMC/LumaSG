package net.lumalyte.lumasg.game

import kotlinx.coroutines.*
import net.badgersmc.nexus.paper.BukkitDispatcher
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.title.Title
import net.lumalyte.lumasg.config.LumaSGConfig
import org.bukkit.Bukkit
import org.bukkit.Color
import org.bukkit.FireworkEffect
import org.bukkit.Sound
import org.bukkit.entity.Firework
import org.bukkit.entity.Player
import org.bukkit.metadata.FixedMetadataValue
import org.bukkit.plugin.java.JavaPlugin
import org.slf4j.LoggerFactory
import java.awt.image.BufferedImage
import java.net.URI
import java.time.Duration
import java.util.UUID
import javax.imageio.ImageIO
import kotlin.random.Random

private val logger = LoggerFactory.getLogger("LumaSG-Celebration")

private val mm = MiniMessage.miniMessage()

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

private const val PIXEL_CHAR = "\u2B1B" // ⬛

/**
 * Fetches the winner's 8x8 face from Starlight Skins API and renders pixel art + winner
 * info into chat for all participants.
 *
 * The HTTP fetch runs on [Dispatchers.IO]; chat messages are sent on
 * [bukkitDispatcher]. If the fetch fails for any reason the pixel art is
 * silently skipped.
 *
 * @param winnerUuid    UUID of the winning player
 * @param winnerName    Display name of the winner
 * @param kills         Winner's kill count
 * @param deathMessage  Optional last-death message to display
 * @param participants  UUIDs of everyone who should see the render
 * @param bukkitDispatcher Main-thread dispatcher
 */
@Suppress("LongMethod", "CyclomaticComplexMethod", "UnusedParameter") // pixel-art renderer; kept whole for readability
private suspend fun renderPixelArtHead(
    winnerUuid: UUID,
    winnerName: String,
    kills: Int,
    deathMessage: String?,
    participants: Collection<UUID>,
    config: LumaSGConfig,
    bukkitDispatcher: BukkitDispatcher
) {
    if (!config.rewards.winnerAnnouncement.pixelArt.enabled) return

    val apiUrl = config.rewards.winnerAnnouncement.pixelArt.apiUrl
        .replace("<uuid>", winnerUuid.toString())
        .replace("<name>", winnerName)
    val pixelChar = config.rewards.winnerAnnouncement.pixelArt.character.ifEmpty { PIXEL_CHAR }
    val size = config.rewards.winnerAnnouncement.pixelArt.size

    logger.info("Fetching Starlight skin for $winnerName ($winnerUuid) from: $apiUrl")

    val image: BufferedImage? = withContext(Dispatchers.IO) {
        try {
            val url = URI(apiUrl).toURL()
            val conn = url.openConnection().apply {
                connectTimeout = 5_000
                readTimeout = 5_000
                setRequestProperty("User-Agent", "LumaSG-Plugin")
            }
            val raw = ImageIO.read(conn.getInputStream()) ?: return@withContext null
            // Starlight returns a full-size render — scale down to pixel art grid size
            if (raw.width == size && raw.height == size) raw
            else {
                val scaled = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
                val g = scaled.createGraphics()
                g.drawImage(raw, 0, 0, size, size, null)
                g.dispose()
                scaled
            }
        } catch (e: Exception) {
            logger.warn("Failed to fetch Starlight skin for $winnerName: ${e::class.simpleName}: ${e.message}")
            null
        }
    }

    if (image == null) {
        logger.warn("Pixel art render skipped — image was null for $winnerName")
        return
    }

    logger.info("Starlight skin loaded: ${image.width}x${image.height} — rendering pixel art")

    // Build rows of colored pixel squares using configured size and character
    val imgWidth = minOf(size, image.width)
    val imgHeight = minOf(size, image.height)
    val pixelRows = Array(imgHeight) { y ->
        var row = Component.empty()
        for (x in 0 until imgWidth) {
            val rgb = image.getRGB(x, y)
            val alpha = (rgb shr 24) and 0xFF
            val r = (rgb shr 16) and 0xFF
            val g = (rgb shr 8) and 0xFF
            val b = rgb and 0xFF
            val color = if (alpha < 128) NamedTextColor.BLACK else TextColor.color(r, g, b)
            row = row.append(Component.text(pixelChar).color(color))
        }
        row
    }

    // Build the info lines that appear alongside the pixel art
    val separator = Component.text("  ")
    val borderTop = Component.text("\u2554\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2557")
        .color(NamedTextColor.GOLD) // ╔══════════════════╗
    val winnerLine = Component.text("\u2551 \uD83D\uDC51 WINNER: ")
        .color(NamedTextColor.GOLD)
        .append(
            Component.text(winnerName)
                .color(NamedTextColor.GOLD)
                .decorate(TextDecoration.BOLD)
        )
        .append(
            Component.text(" \uD83D\uDC51 \u2551")
                .color(NamedTextColor.GOLD)
                .decoration(TextDecoration.BOLD, false)
        ) // ║ 👑 WINNER: <name> 👑 ║
    val borderBottom = Component.text("\u255A\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u255D")
        .color(NamedTextColor.GOLD) // ╚══════════════════╝
    val deathLine = if (deathMessage != null) {
        Component.text(deathMessage).color(NamedTextColor.GRAY).decorate(TextDecoration.ITALIC)
    } else {
        null
    }

    // Compose full lines: pixel art on the left, info on the right for rows 1-4 (indices 1-4)
    val lines = mutableListOf<Component>()
    for (y in 0 until imgHeight) {
        var line = pixelRows[y]
        when (y) {
            1 -> line = line.append(separator).append(borderTop)
            2 -> line = line.append(separator).append(winnerLine)
            3 -> line = line.append(separator).append(borderBottom)
            4 -> if (deathLine != null) {
                line = line.append(separator).append(deathLine)
            }
        }
        lines.add(line)
    }

    withContext(bukkitDispatcher) {
        for (id in participants) {
            val p = Bukkit.getPlayer(id) ?: continue
            p.sendMessage(Component.empty()) // blank line before art
            for (line in lines) {
                p.sendMessage(line)
            }
            p.sendMessage(Component.empty()) // blank line after art
        }
    }
}

/**
 * Runs the end-game celebration sequence.
 *
 * Must be called from a coroutine. Bukkit API calls are dispatched to the
 * main thread via [bukkitDispatcher]. Runs for ~5 seconds total.
 *
 * @param winnerUuid   UUID of the winner, or null if time ran out
 * @param participants UUIDs of all players + spectators in the game
 * @param kills        Winner's kill count (used in messages/commands)
 * @param config       Plugin config for rewards and announcements
 * @param plugin       Plugin instance for firework spawning
 */
@Suppress("LongMethod", "CyclomaticComplexMethod") // sequential celebration choreography
suspend fun runCelebration(
    winnerUuid: UUID?,
    participants: Collection<UUID>,
    kills: Int,
    config: LumaSGConfig,
    plugin: JavaPlugin,
    bukkitDispatcher: BukkitDispatcher,
    deathMessage: String? = null
) {
    val winner: Player? = winnerUuid?.let { Bukkit.getPlayer(it) }

    withContext(bukkitDispatcher) {
        // MiniMessage gradient titles (matching Java CelebrationManager)
        val title = if (winner != null) {
            val titleText = config.rewards.winnerAnnouncement.title
            val subtitleText = config.rewards.winnerAnnouncement.subtitle
                .replace("<player>", winner.name)
            Title.title(
                mm.deserialize(titleText),
                mm.deserialize(subtitleText),
                Title.Times.times(Duration.ofMillis(1_000), Duration.ofMillis(3_000), Duration.ofMillis(1_000))
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

        // Broadcast winner message with kill count
        if (winner != null) {
            val template = config.rewards.winnerAnnouncement.message
                .replace("<player>", winner.name)
                .replace("<kills>", kills.toString())
            val winMsg = mm.deserialize(template)
            for (id in participants) {
                Bukkit.getPlayer(id)?.sendMessage(winMsg)
            }
        }

        // Execute winner reward command
        if (winner != null && config.rewards.enabled && config.rewards.winCommand.isNotEmpty()) {
            val cmd = config.rewards.winCommand
                .replace("<player>", winner.name)
                .replace("<kills>", kills.toString())
            plugin.server.dispatchCommand(plugin.server.consoleSender, cmd)
        }
    }

    // Render pixel art head for the winner
    if (winner != null && winnerUuid != null) {
        renderPixelArtHead(
            winnerUuid = winnerUuid,
            winnerName = winner.name,
            kills = kills,
            deathMessage = deathMessage,
            participants = participants,
            config = config,
            bukkitDispatcher = bukkitDispatcher
        )
    }

    // Fireworks every 250 ms
    if (winner != null && config.rewards.winnerAnnouncement.fireworks) {
        val fireworkCount = config.rewards.winnerAnnouncement.fireworkCount
        repeat(fireworkCount) {
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
                firework.setMetadata("celebration_firework", FixedMetadataValue(plugin, true))
                firework.setPersistent(false)
            }
        }
    }
}
