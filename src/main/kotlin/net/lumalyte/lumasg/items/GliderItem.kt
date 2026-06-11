package net.lumalyte.lumasg.items

import net.badgersmc.nexus.annotations.PostConstruct
import net.badgersmc.nexus.annotations.PreDestroy
import net.badgersmc.nexus.annotations.Service
import net.lumalyte.lumasg.config.LumaSGConfig
import net.lumalyte.lumasg.game.GameManager
import org.bukkit.*
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityToggleGlideEvent
import org.bukkit.event.player.PlayerItemHeldEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitRunnable
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor

@Service
class GliderItem(
    private val plugin: JavaPlugin,
    private val config: LumaSGConfig,
    private val gameManager: GameManager
) : CustomItem, Listener {

    override val material = Material.FEATHER
    override val displayName = "\u00a7b\u00a7lGlider"
    override val key = NamespacedKey(plugin, "glider")

    private val cfg get() = config.glider

    data class GlideState(
        val startTick: Long,
        var airtimeTicks: Int = 0,
        var glidingActive: Boolean = false
    )

    val glideStates = ConcurrentHashMap<UUID, GlideState>()
    private var glideTask: BukkitRunnable? = null

    @PostConstruct
    fun register() {
        plugin.server.pluginManager.registerEvents(this, plugin)
    }

    @PreDestroy
    fun shutdown() {
        glideTask?.cancel()
        glideTask = null
        for (uuid in glideStates.keys) {
            Bukkit.getPlayer(uuid)?.isGliding = false
        }
        glideStates.clear()
    }

    override fun createStack(): ItemStack {
        val stack = ItemStack(material)
        val meta: ItemMeta = stack.itemMeta ?: return stack
        meta.setDisplayName(displayName)
        meta.lore = listOf("\u00a77Hold while airborne to glide", "\u00a77Land before timer expires to keep it")
        stack.itemMeta = meta
        return tag(stack)
    }

    override fun onUse(player: Player, item: ItemStack) {
        // Glider is passive — no right-click action needed
    }

    fun isGliderItem(stack: ItemStack): Boolean {
        val meta = stack.itemMeta ?: return false
        return meta.persistentDataContainer.has(key, PersistentDataType.BOOLEAN)
    }

    // ── Repeating task: check airborne + holding feather ──────────────────

    @Suppress("LoopWithTooManyJumpStatements") // particle loop in the glide tick task
    private fun ensureTaskRunning() {
        if (glideTask != null) return
        glideTask = object : BukkitRunnable() {
            override fun run() {
                if (glideStates.isEmpty()) {
                    cancel()
                    glideTask = null
                    return
                }
                val toRemove = mutableListOf<UUID>()
                for ((uuid, state) in glideStates) {
                    val player = Bukkit.getPlayer(uuid)
                    if (player == null || !player.isOnline) {
                        toRemove.add(uuid)
                        continue
                    }
                    if (!isGliderItem(player.inventory.itemInMainHand)) {
                        endGlide(player, state, consume = false)
                        toRemove.add(uuid)
                        continue
                    }
                    val airborne = !player.isOnGround && !player.isInWater && !player.isInsideVehicle
                    if (!airborne) {
                        if (state.glidingActive) {
                            endGlide(player, state, consume = false)
                            toRemove.add(uuid)
                            player.world.spawnParticle(Particle.CLOUD, player.location, 8, 0.3, 0.1, 0.3, 0.02)
                        }
                        continue
                    }

                    state.airtimeTicks += 2

                    if (!state.glidingActive && state.airtimeTicks >= cfg.minAirtimeTicks) {
                        state.glidingActive = true
                        player.isGliding = true
                    }

                    if (state.glidingActive) {
                        if (!player.isGliding) player.isGliding = true

                        val elapsed = state.airtimeTicks
                        val remaining = cfg.maxDurationTicks - elapsed
                        if (remaining <= 0) {
                            endGlide(player, state, consume = true)
                            toRemove.add(uuid)
                            continue
                        }

                        val secondsLeft = remaining / 20.0
                        player.sendActionBar(
                            Component.text("\u2726 Glide: %.1fs \u2726".format(secondsLeft), NamedTextColor.AQUA)
                        )

                        if (elapsed % 20 == 0) {
                            player.world.playSound(player.location, Sound.ENTITY_PHANTOM_FLAP, 0.3f, 1.2f)
                        }

                        player.world.spawnParticle(Particle.CLOUD, player.location, 2, 0.1, 0.1, 0.1, 0.01)
                    }
                }
                toRemove.forEach { glideStates.remove(it) }
            }
        }
        glideTask!!.runTaskTimer(plugin, 0L, 2L)
    }

    private fun endGlide(player: Player, state: GlideState, consume: Boolean) {
        if (state.glidingActive) {
            recentlyGliding[player.uniqueId] = player.world.fullTime
        }
        state.glidingActive = false
        player.isGliding = false
        player.sendActionBar(Component.empty())
        if (consume) {
            val held = player.inventory.itemInMainHand
            if (isGliderItem(held)) held.amount--
        }
    }

    fun cancelGlide(uuid: UUID) {
        val state = glideStates.remove(uuid) ?: return
        val player = Bukkit.getPlayer(uuid) ?: return
        endGlide(player, state, consume = false)
    }

    // ── Track players who hold the glider while airborne ──────────────────

    @EventHandler(priority = EventPriority.MONITOR)
    fun onItemHeld(event: PlayerItemHeldEvent) {
        val player = event.player
        val oldItem = player.inventory.getItem(event.previousSlot)
        val newItem = player.inventory.getItem(event.newSlot)

        if (oldItem != null && isGliderItem(oldItem)) {
            cancelGlide(player.uniqueId)
        }
        if (newItem != null && isGliderItem(newItem)) {
            if (!player.isOnGround && !player.isInWater) {
                gameManager.getGameForPlayer(player.uniqueId) ?: return
                glideStates.putIfAbsent(player.uniqueId, GlideState(startTick = player.world.fullTime))
                ensureTaskRunning()
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onMove(event: PlayerMoveEvent) {
        val player = event.player
        if (glideStates.containsKey(player.uniqueId)) return
        if (!isGliderItem(player.inventory.itemInMainHand)) return
        gameManager.getGameForPlayer(player.uniqueId) ?: return
        if (player.isOnGround || player.isInWater) return

        glideStates.putIfAbsent(player.uniqueId, GlideState(startTick = player.world.fullTime))
        ensureTaskRunning()
    }

    // ── Prevent server from reverting glide state ─────────────────────────

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onToggleGlide(event: EntityToggleGlideEvent) {
        val player = event.entity as? Player ?: return
        val state = glideStates[player.uniqueId] ?: return
        if (state.glidingActive && !event.isGliding) {
            event.isCancelled = true
        }
    }

    // ── Cancel fall damage on glide landing ───────────────────────────────

    private val recentlyGliding = ConcurrentHashMap<UUID, Long>()
    private val fallDamageGraceTicks = 5L

    @EventHandler(priority = EventPriority.HIGH)
    fun onDamage(event: EntityDamageEvent) {
        if (cfg.landingFallDamage) return
        val player = event.entity as? Player ?: return
        if (event.cause != EntityDamageEvent.DamageCause.FALL) return
        if (glideStates.containsKey(player.uniqueId)) {
            event.isCancelled = true
            return
        }
        val endedAt = recentlyGliding[player.uniqueId] ?: return
        if (player.world.fullTime - endedAt <= fallDamageGraceTicks) {
            event.isCancelled = true
            recentlyGliding.remove(player.uniqueId)
        }
    }

    // ── Block firework boost while gliding ─────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    fun onElytraBoost(event: com.destroystokyo.paper.event.player.PlayerElytraBoostEvent) {
        if (!cfg.blockFireworkBoost) return
        val player = event.player
        if (glideStates.containsKey(player.uniqueId)) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onQuit(event: PlayerQuitEvent) {
        glideStates.remove(event.player.uniqueId)
        recentlyGliding.remove(event.player.uniqueId)
    }
}
