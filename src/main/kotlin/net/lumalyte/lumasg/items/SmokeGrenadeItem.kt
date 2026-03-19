package net.lumalyte.lumasg.items

import net.badgersmc.nexus.annotations.PostConstruct
import net.badgersmc.nexus.annotations.PreDestroy
import net.badgersmc.nexus.annotations.Service
import net.lumalyte.lumasg.config.LumaSGConfig
import net.lumalyte.lumasg.game.GameManager
import net.lumalyte.lumasg.util.MeteorUtils
import org.bukkit.*
import org.bukkit.entity.Player
import org.bukkit.entity.Snowball
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.ProjectileHitEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.metadata.FixedMetadataValue
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitRunnable
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.*

@Service
class SmokeGrenadeItem(
    private val plugin: JavaPlugin,
    private val config: LumaSGConfig,
    private val gameManager: GameManager
) : CustomItem, Listener {

    override val material = Material.SNOWBALL
    override val displayName = "§7§lSmoke Grenade"
    override val key = NamespacedKey(plugin, "smoke_grenade")

    private val cfg get() = config.smokeGrenade

    private val activeClouds = ConcurrentHashMap<Int, SmokeCloud>()
    private val nextCloudId = AtomicInteger(0)

    // Map<observerUUID, Set<hiddenPlayerUUID>>
    private val hiddenPlayers = ConcurrentHashMap<UUID, MutableSet<UUID>>()

    @PostConstruct
    fun register() {
        plugin.server.pluginManager.registerEvents(this, plugin)
    }

    @PreDestroy
    fun shutdown() {
        for ((_, cloud) in activeClouds) {
            cloud.task?.cancel()
        }
        restoreAllVisibility()
        activeClouds.clear()
    }

    override fun createStack(): ItemStack {
        val stack = ItemStack(material)
        val meta: ItemMeta = stack.itemMeta ?: return stack
        meta.setDisplayName(displayName)
        meta.lore = listOf("§7Throw to create a smoke screen", "§7Blocks vision and nametags")
        stack.itemMeta = meta
        return tag(stack)
    }

    override fun onUse(player: Player, item: ItemStack) {
        val snowball = player.launchProjectile(Snowball::class.java)
        snowball.setMetadata("lumasg_smoke_grenade", FixedMetadataValue(plugin, true))
        item.amount--
    }

    // ── Spawn cloud on impact ─────────────────────────────────────────────

    @EventHandler
    fun onProjectileHit(event: ProjectileHitEvent) {
        val projectile = event.entity
        if (!projectile.hasMetadata("lumasg_smoke_grenade")) return

        val center = projectile.location
        val world = center.world
        val cloudId = nextCloudId.getAndIncrement()

        // Impact sounds
        world.playSound(center, Sound.ENTITY_SPLASH_POTION_BREAK, 1.5f, 1f)
        world.playSound(center, Sound.BLOCK_FIRE_EXTINGUISH, 2f, 0.8f)

        val cloud = SmokeCloud(center = center, id = cloudId)
        activeClouds[cloudId] = cloud

        val task = object : BukkitRunnable() {
            var ticksElapsed = 0
            var visibilityCounter = 0

            override fun run() {
                if (ticksElapsed >= cfg.durationTicks) {
                    cancel()
                    activeClouds.remove(cloudId)
                    restoreVisibilityForCloud(cloud)
                    return
                }

                if (ticksElapsed % cfg.particleTickRate == 0) {
                    renderSmokeParticles(center, ticksElapsed)
                }

                visibilityCounter++
                if (visibilityCounter >= cfg.visibilityCheckRate) {
                    visibilityCounter = 0
                    updateVisibility(cloud)
                }

                if (ticksElapsed % cfg.ambientSoundInterval == 0 && ticksElapsed > 0) {
                    world.playSound(center, Sound.BLOCK_CAMPFIRE_CRACKLE, 0.5f, 1f)
                }

                ticksElapsed++
            }
        }
        cloud.task = task
        task.runTaskTimer(plugin, 0L, 1L)
    }

    // ── Golden-ratio particle rendering with color mixing ─────────────────

    private fun renderSmokeParticles(center: Location, ticksElapsed: Int) {
        val world = center.world
        val radius = cfg.radius
        val height = radius * 0.6

        val fadeStart = cfg.durationTicks - cfg.fadeDurationTicks
        val densityMultiplier = if (ticksElapsed >= fadeStart) {
            1.0 - (ticksElapsed - fadeStart).toDouble() / cfg.fadeDurationTicks
        } else 1.0

        val pointCount = (cfg.particleDensity * densityMultiplier).toInt().coerceAtLeast(1)

        val whiteOpt = Particle.DustOptions(Color.fromRGB(220, 220, 220), 2.0f)
        val greyOpt = Particle.DustOptions(Color.fromRGB(140, 140, 140), 2.0f)
        val darkOpt = Particle.DustOptions(Color.fromRGB(60, 60, 60), 2.0f)

        val goldenRatio = Math.PI * (1 + sqrt(5.0))

        for (i in 0 until pointCount) {
            val phi = acos(1 - 2.0 * (i + 0.5) / pointCount)
            val theta = goldenRatio * i
            val r = Math.random() * radius
            val x = r * sin(phi) * cos(theta)
            val rawY = r * sin(phi) * sin(theta)
            val y = abs(rawY) * (height / radius)
            val z = r * cos(phi)

            val loc = center.clone().add(x, y, z)

            val roll = Math.random()
            val dustOpt = when {
                roll < 0.40 -> whiteOpt
                roll < 0.75 -> greyOpt
                else -> darkOpt
            }
            world.spawnParticle(Particle.DUST, loc, 1, dustOpt)

            if (Math.random() < 0.2) {
                world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, loc, 1, 0.5, 0.3, 0.5, 0.01)
            }
        }
    }

    // ── Visibility toggling ───────────────────────────────────────────────

    private fun updateVisibility(cloud: SmokeCloud) {
        val center = cloud.center
        val radius = cfg.radius.toDouble()
        val radiusSq = radius * radius
        val checkRange = radius * 3

        val nearbyPlayers = center.world.getNearbyEntities(center, checkRange, checkRange, checkRange)
            .filterIsInstance<Player>()

        for (observer in nearbyPlayers) {
            for (target in nearbyPlayers) {
                if (observer.uniqueId == target.uniqueId) continue

                val shouldHide = shouldBlockVisibility(observer, target, center, radiusSq, radius)

                val observerHidden = hiddenPlayers.getOrPut(observer.uniqueId) { ConcurrentHashMap.newKeySet() }
                if (shouldHide) {
                    if (observerHidden.add(target.uniqueId)) {
                        observer.hidePlayer(plugin, target)
                    }
                } else {
                    val blockedByAny = activeClouds.values.any { otherCloud ->
                        shouldBlockVisibility(observer, target, otherCloud.center,
                            cfg.radius.toDouble().pow(2), cfg.radius.toDouble())
                    }
                    if (!blockedByAny && observerHidden.remove(target.uniqueId)) {
                        observer.showPlayer(plugin, target)
                    }
                }
            }
        }
    }

    private fun shouldBlockVisibility(
        observer: Player, target: Player,
        cloudCenter: Location, radiusSq: Double, radius: Double
    ): Boolean {
        val observerLoc = observer.eyeLocation
        val targetLoc = target.location

        if (observerLoc.distanceSquared(cloudCenter) <= radiusSq) return true
        if (targetLoc.distanceSquared(cloudCenter) <= radiusSq) return true
        return rayIntersectsSphere(observerLoc, targetLoc, cloudCenter, radius)
    }

    private fun rayIntersectsSphere(
        start: Location, end: Location, sphereCenter: Location, radius: Double
    ): Boolean {
        if (start.world != sphereCenter.world) return false
        val dx = end.x - start.x
        val dy = end.y - start.y
        val dz = end.z - start.z
        val fx = start.x - sphereCenter.x
        val fy = start.y - sphereCenter.y
        val fz = start.z - sphereCenter.z

        val a = dx * dx + dy * dy + dz * dz
        val b = 2 * (fx * dx + fy * dy + fz * dz)
        val c = fx * fx + fy * fy + fz * fz - radius * radius

        val discriminant = b * b - 4 * a * c
        if (discriminant < 0) return false

        val sqrtDisc = sqrt(discriminant)
        val t1 = (-b - sqrtDisc) / (2 * a)
        val t2 = (-b + sqrtDisc) / (2 * a)

        return (t1 in 0.0..1.0) || (t2 in 0.0..1.0) || (t1 < 0 && t2 > 1)
    }

    // ── Cleanup ───────────────────────────────────────────────────────────

    private fun restoreVisibilityForCloud(cloud: SmokeCloud) {
        for ((observerUuid, hiddenSet) in hiddenPlayers) {
            val observer = Bukkit.getPlayer(observerUuid) ?: continue
            val toRestore = mutableListOf<UUID>()
            for (targetUuid in hiddenSet) {
                val target = Bukkit.getPlayer(targetUuid) ?: continue
                val stillBlocked = activeClouds.values.any { otherCloud ->
                    shouldBlockVisibility(observer, target, otherCloud.center,
                        cfg.radius.toDouble().pow(2), cfg.radius.toDouble())
                }
                if (!stillBlocked) {
                    observer.showPlayer(plugin, target)
                    toRestore.add(targetUuid)
                }
            }
            hiddenSet.removeAll(toRestore.toSet())
        }
    }

    private fun restoreAllVisibility() {
        for ((observerUuid, hiddenSet) in hiddenPlayers) {
            val observer = Bukkit.getPlayer(observerUuid) ?: continue
            for (targetUuid in hiddenSet) {
                val target = Bukkit.getPlayer(targetUuid) ?: continue
                observer.showPlayer(plugin, target)
            }
        }
        hiddenPlayers.clear()
    }

    fun cleanupVisibility(uuid: UUID) {
        val hidden = hiddenPlayers.remove(uuid)
        val player = Bukkit.getPlayer(uuid)
        if (player != null && hidden != null) {
            for (targetUuid in hidden) {
                val target = Bukkit.getPlayer(targetUuid) ?: continue
                player.showPlayer(plugin, target)
            }
        }
        for ((observerUuid, set) in hiddenPlayers) {
            if (set.remove(uuid)) {
                val observer = Bukkit.getPlayer(observerUuid) ?: continue
                observer.showPlayer(plugin, player ?: continue)
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onQuit(event: PlayerQuitEvent) {
        cleanupVisibility(event.player.uniqueId)
    }

    data class SmokeCloud(
        val center: Location,
        val id: Int,
        var task: BukkitRunnable? = null
    )
}
