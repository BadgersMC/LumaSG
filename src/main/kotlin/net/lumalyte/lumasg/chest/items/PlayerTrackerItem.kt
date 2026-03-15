package net.lumalyte.lumasg.chest.items

import net.badgersmc.nexus.annotations.PostConstruct
import net.badgersmc.nexus.annotations.PreDestroy
import net.badgersmc.nexus.annotations.Service
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import net.lumalyte.lumasg.game.Game
import net.lumalyte.lumasg.game.GameManager
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerItemHeldEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitRunnable
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.roundToInt

@Service
class PlayerTrackerItem(
    private val plugin: Plugin,
    private val gameManager: GameManager
) : CustomItem, Listener {
    override val material = Material.COMPASS
    override val displayName = "§cPlayer Tracker"
    override val key = NamespacedKey(plugin, "player_tracker")

    // Compass display constants (ported from Java PlayerTrackerBehavior)
    private companion object {
        const val COMPASS_WIDTH = 21
        const val COMPASS_BAR = "─"
        const val PLAYER_DOT = "•"
        const val TOP_KILLER_EMOJI = "🗡"

        val CLOSE_COLOR: TextColor = NamedTextColor.RED        // 0–50 blocks
        val MEDIUM_COLOR: TextColor = NamedTextColor.YELLOW     // 50–150 blocks
        val FAR_COLOR: TextColor = NamedTextColor.GREEN         // 150+ blocks
        val TOP_KILLER_COLOR: TextColor = NamedTextColor.DARK_RED
        val COMPASS_COLOR: TextColor = NamedTextColor.GRAY

        const val CLOSE_DISTANCE = 50
        const val MEDIUM_DISTANCE = 150
        const val MAX_RANGE = 500
    }

    /** Active trackers: player UUID → true (presence set) */
    private val activeTrackers = ConcurrentHashMap.newKeySet<UUID>()
    private var updateTask: BukkitRunnable? = null

    @PostConstruct
    fun register() {
        plugin.server.pluginManager.registerEvents(this, plugin)
        startUpdateTask()
    }

    @PreDestroy
    fun shutdown() {
        updateTask?.cancel()
        updateTask = null
        activeTrackers.clear()
    }

    override fun createStack(): ItemStack {
        val stack = ItemStack(material)
        val meta: ItemMeta = stack.itemMeta ?: return stack
        meta.setDisplayName(displayName)
        meta.lore = listOf("§7Tracks nearby players on your HUD", "§7Hold to activate compass")
        stack.itemMeta = meta
        return tag(stack)
    }

    override fun onUse(player: Player, item: ItemStack) {
        // Right-click toggles the tracker on (it auto-activates on hold too)
        if (!activeTrackers.contains(player.uniqueId)) {
            activeTrackers.add(player.uniqueId)
            player.sendMessage(Component.text("Tracker activated.", NamedTextColor.GREEN))
        }
    }

    // ── Hold / switch detection ──────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR)
    fun onItemHeld(event: PlayerItemHeldEvent) {
        val player = event.player
        val newItem = player.inventory.getItem(event.newSlot)
        val oldItem = player.inventory.getItem(event.previousSlot)

        // Switched away from tracker → unregister
        if (oldItem != null && isTracker(oldItem)) {
            unregisterTracker(player)
        }
        // Switched to tracker → register
        if (newItem != null && isTracker(newItem)) {
            activeTrackers.add(player.uniqueId)
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onDrop(event: PlayerDropItemEvent) {
        if (isTracker(event.itemDrop.itemStack)) {
            unregisterTracker(event.player)
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onQuit(event: PlayerQuitEvent) {
        activeTrackers.remove(event.player.uniqueId)
    }

    private fun isTracker(stack: ItemStack): Boolean {
        val meta = stack.itemMeta ?: return false
        return meta.persistentDataContainer.has(key, PersistentDataType.BOOLEAN)
    }

    private fun unregisterTracker(player: Player) {
        if (activeTrackers.remove(player.uniqueId)) {
            player.sendActionBar(Component.empty())
        }
    }

    // ── Periodic update task ─────────────────────────────────────────────────

    private fun startUpdateTask() {
        updateTask = object : BukkitRunnable() {
            override fun run() {
                val toRemove = mutableListOf<UUID>()
                for (uuid in activeTrackers) {
                    val player = Bukkit.getPlayer(uuid)
                    if (player == null || !player.isOnline) {
                        toRemove.add(uuid)
                        continue
                    }
                    // Verify player still holds the tracker
                    val held = player.inventory.itemInMainHand
                    if (!isTracker(held)) {
                        toRemove.add(uuid)
                        player.sendActionBar(Component.empty())
                        continue
                    }
                    val game = gameManager.getGameForPlayer(uuid) ?: continue
                    val compass = generateCompass(player, game)
                    player.sendActionBar(compass)
                }
                activeTrackers.removeAll(toRemove.toSet())
            }
        }
        updateTask!!.runTaskTimer(plugin, 10L, 10L) // every 0.5s
    }

    // ── Compass generation ───────────────────────────────────────────────────

    private fun generateCompass(player: Player, game: Game): Component {
        val playerLoc = player.location
        val playerYaw = playerLoc.yaw.toDouble()

        // Find top killer
        val topKiller = findTopKiller(game, player.uniqueId)

        // Collect trackable targets
        val targets = mutableListOf<TrackableTarget>()
        for ((uuid, gp) in game.players) {
            if (uuid == player.uniqueId || !gp.isAlive) continue
            val target = Bukkit.getPlayer(uuid) ?: continue
            val targetLoc = target.location
            if (targetLoc.world != playerLoc.world) continue

            val distance = playerLoc.distance(targetLoc)
            if (distance > MAX_RANGE) continue

            val angle = calculateAngle(playerLoc, targetLoc)
            val isTopKiller = uuid == topKiller
            targets.add(TrackableTarget(
                angle = angle,
                symbol = if (isTopKiller) TOP_KILLER_EMOJI else PLAYER_DOT,
                color = if (isTopKiller) TOP_KILLER_COLOR else distanceColor(distance),
                distance = distance,
                isTopKiller = isTopKiller
            ))
        }

        // Sort: top killer first, then closest
        targets.sortWith(compareByDescending<TrackableTarget> { it.isTopKiller }.thenBy { it.distance })

        // Build compass array
        val elements = Array<Component>(COMPASS_WIDTH) {
            Component.text(COMPASS_BAR, COMPASS_COLOR)
        }

        for (target in targets) {
            val pos = compassPosition(playerYaw, target.angle)
            if (pos in 0 until COMPASS_WIDTH) {
                elements[pos] = Component.text(target.symbol, target.color)
            }
        }

        val builder: TextComponent.Builder = Component.text()
            .append(Component.text("[", COMPASS_COLOR))
        for (element in elements) {
            builder.append(element)
        }
        builder.append(Component.text("]", COMPASS_COLOR))
        return builder.build()
    }

    /** Angle from [from] to [to] in degrees (0° = North, clockwise). */
    private fun calculateAngle(from: Location, to: Location): Double {
        val dx = to.x - from.x
        val dz = to.z - from.z
        if (abs(dx) < 0.001 && abs(dz) < 0.001) return 0.0
        val angle = Math.toDegrees(atan2(-dx, dz))
        return ((angle % 360) + 360) % 360
    }

    /** Maps a target angle to a compass position (0..COMPASS_WIDTH-1), or -1 if out of FOV. */
    private fun compassPosition(playerYaw: Double, targetAngle: Double): Int {
        val normalizedYaw = ((playerYaw % 360) + 360) % 360
        var relative = (targetAngle - normalizedYaw + 360) % 360
        if (relative > 180) relative -= 360
        // ±90° field of view
        if (abs(relative) > 90) return -1
        val normalized = (relative + 90) / 180.0 // 0..1
        return (normalized * (COMPASS_WIDTH - 1)).roundToInt().coerceIn(0, COMPASS_WIDTH - 1)
    }

    private fun distanceColor(distance: Double): TextColor = when {
        distance <= CLOSE_DISTANCE -> CLOSE_COLOR
        distance <= MEDIUM_DISTANCE -> MEDIUM_COLOR
        else -> FAR_COLOR
    }

    /** Returns the UUID of the player with most kills (≥1), excluding [self]. */
    private fun findTopKiller(game: Game, self: UUID): UUID? {
        var topUuid: UUID? = null
        var topKills = 0
        for ((uuid, gp) in game.players) {
            if (uuid == self) continue
            if (gp.kills > topKills) {
                val online = Bukkit.getPlayer(uuid)
                if (online != null && online.isOnline) {
                    topUuid = uuid
                    topKills = gp.kills
                }
            }
        }
        return if (topKills > 0) topUuid else null
    }

    private data class TrackableTarget(
        val angle: Double,
        val symbol: String,
        val color: TextColor,
        val distance: Double,
        val isTopKiller: Boolean
    )
}
