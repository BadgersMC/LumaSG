package net.lumalyte.lumasg.listeners

import net.badgersmc.nexus.annotations.PostConstruct
import net.badgersmc.nexus.annotations.Service
import net.lumalyte.lumasg.items.CustomItem
import net.lumalyte.lumasg.game.GameManager
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.entity.TNTPrimed
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.entity.FallingBlock
import org.bukkit.event.block.Action
import org.bukkit.event.entity.EntityChangeBlockEvent
import org.bukkit.event.entity.EntityExplodeEvent
import org.bukkit.event.entity.ProjectileHitEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.util.Vector
import org.bukkit.metadata.FixedMetadataValue
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.scheduler.BukkitRunnable
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.sqrt

@Service
class CustomItemListener(
    private val plugin: JavaPlugin,
    private val gameManager: GameManager,
    private val customItems: List<CustomItem>
) : Listener {

    // Per-player cooldowns in milliseconds
    private val cooldowns = ConcurrentHashMap<UUID, Long>()
    private val COOLDOWN_MS = 1_000L

    @PostConstruct
    fun register() {
        plugin.server.pluginManager.registerEvents(this, plugin)
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onInteract(event: PlayerInteractEvent) {
        val item = event.item ?: return
        val player = event.player

        // Only respond to right-click
        if (event.action != Action.RIGHT_CLICK_AIR &&
            event.action != Action.RIGHT_CLICK_BLOCK) return

        // Must be in a game
        gameManager.getGameForPlayer(player.uniqueId) ?: return

        val customItem = CustomItem.fromStack(item, customItems) ?: return

        // Cooldown check
        val now = System.currentTimeMillis()
        val lastUse = cooldowns[player.uniqueId] ?: 0L
        if (now - lastUse < COOLDOWN_MS) return
        cooldowns[player.uniqueId] = now

        event.isCancelled = true
        customItem.onUse(player, item)
    }

    // ── Fire Bomb: imperfect circle fire spread with particle effects ────────

    @EventHandler(priority = EventPriority.HIGH)
    fun onFireBombExplode(event: EntityExplodeEvent) {
        val tnt = event.entity as? TNTPrimed ?: return
        if (!tnt.hasMetadata("lumasg_fire_bomb")) return

        event.isCancelled = true
        val center = event.location
        val world = center.world
        val throwerId = tnt.getMetadata("lumasg_fire_bomb").firstOrNull()?.asString()

        // Random radius between 3 and 7 (like Java: fireRadiusMin/Max)
        val radius = (3..7).random()

        // Generate imperfect circle with Gaussian noise for natural spread
        val fireLocations = generateImperfectCircle(center, radius)
        for (loc in fireLocations) {
            val block = world.getBlockAt(loc)
            if (block.type.isAir) {
                block.type = Material.FIRE
                // Schedule fire removal after 3 seconds
                plugin.server.scheduler.runTaskLater(plugin, Runnable {
                    if (block.type == Material.FIRE) block.type = Material.AIR
                }, 60L)
            }
        }

        // Molotov-style particle effects — fire and smoke, no explosion
        world.spawnParticle(Particle.FLAME, center, radius * 10,
            radius.toDouble(), 1.0, radius.toDouble(), 0.1)
        world.spawnParticle(Particle.LARGE_SMOKE, center, radius * 5,
            radius.toDouble(), 2.0, radius.toDouble(), 0.1)

        // Sounds — fire woosh, no explosion
        world.playSound(center, Sound.ITEM_FIRECHARGE_USE, 1.5f, 0.7f)
        world.playSound(center, Sound.BLOCK_FIRE_AMBIENT, 2f, 1f)
    }

    /**
     * Generates an imperfect circle of locations using Gaussian noise
     * for natural-looking fire spread (ported from Java ExplosiveBehavior).
     */
    private fun generateImperfectCircle(center: Location, radius: Int): Set<Location> {
        val locations = mutableSetOf<Location>()
        val rng = java.util.Random()

        for (x in -radius..radius) {
            for (z in -radius..radius) {
                val distance = sqrt((x * x + z * z).toDouble())
                // Gaussian noise creates organic edges
                val threshold = radius + rng.nextGaussian() * 0.5
                if (distance <= threshold) {
                    val loc = center.clone().add(x.toDouble(), 0.0, z.toDouble())
                    // 30% chance of vertical variation for 3D feel
                    if (rng.nextDouble() < 0.3) {
                        loc.add(0.0, (rng.nextInt(3) - 1).toDouble(), 0.0)
                    }
                    locations.add(loc)
                }
            }
        }
        return locations
    }

    // ── Bomb: knockback explosion with visual debris, no fire ───────────────────

    @EventHandler(priority = EventPriority.HIGH)
    fun onBombExplode(event: EntityExplodeEvent) {
        val tnt = event.entity as? TNTPrimed ?: return
        if (!tnt.hasMetadata("lumasg_bomb")) return

        event.isCancelled = true
        val center = event.location
        val world = center.world
        val throwerId = tnt.getMetadata("lumasg_bomb").firstOrNull()?.asString()
        val radius = 5

        // Visual debris — the main feature of the bomb
        spawnVisualDebris(center, radius)

        // Explosion particles and sounds
        world.spawnParticle(Particle.EXPLOSION, center, 3, 1.0, 1.0, 1.0, 0.0)
        world.spawnParticle(Particle.LARGE_SMOKE, center, 25, 2.0, 2.0, 2.0, 0.1)
        world.spawnParticle(Particle.LAVA, center, 15, 1.5, 1.5, 1.5, 0.0)
        world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 2f, 0.7f)
        world.playSound(center, Sound.ENTITY_DRAGON_FIREBALL_EXPLODE, 1.5f, 0.8f)

        // Distance-based damage + knockback to nearby players (skip thrower)
        world.getNearbyEntities(center, radius.toDouble(), radius.toDouble(), radius.toDouble())
            .filterIsInstance<Player>()
            .filter { it.uniqueId.toString() != throwerId }
            .forEach { target ->
                val distance = target.location.distance(center)
                val multiplier = 1.0 - (distance / radius)
                if (multiplier <= 0) return@forEach

                // Damage scales with proximity (6.0 base at epicenter)
                val damage = 6.0 * multiplier
                target.damage(damage)

                // Knockback — outward from center with upward component
                val direction = target.location.toVector().subtract(center.toVector()).normalize()
                val knockback = direction.multiply(1.5 * multiplier).setY(0.5 + 0.3 * multiplier)
                target.velocity = target.velocity.add(knockback)
            }
    }

    // ── Visual Debris: falling blocks that never actually place ────────────────

    /**
     * Samples block types around the explosion center and launches them as
     * FallingBlock entities with random outward velocities. Tagged with metadata
     * so they get cancelled on land — purely cosmetic, no terrain changes.
     */
    private fun spawnVisualDebris(center: Location, radius: Int) {
        val world = center.world
        val rng = java.util.Random()
        val debrisCount = radius * 5 // scale with explosion size

        // Collect unique solid block types in a sphere around center for realistic debris
        val sampleRadius = radius.coerceAtMost(4)
        val blockTypes = mutableSetOf<Material>()
        for (x in -sampleRadius..sampleRadius) {
            for (y in -1..2) {
                for (z in -sampleRadius..sampleRadius) {
                    val block = world.getBlockAt(
                        center.blockX + x, center.blockY + y, center.blockZ + z
                    )
                    if (block.type.isSolid && !block.type.isAir) {
                        blockTypes.add(block.type)
                    }
                }
            }
        }
        if (blockTypes.isEmpty()) blockTypes.add(Material.STONE)
        val typeList = blockTypes.toList()

        for (i in 0 until debrisCount) {
            val material = typeList[rng.nextInt(typeList.size)]
            val spawnLoc = center.clone().add(
                (rng.nextDouble() - 0.5) * 2,
                rng.nextDouble() * 1.5 + 0.5,
                (rng.nextDouble() - 0.5) * 2
            )

            val fallingBlock = world.spawnFallingBlock(
                spawnLoc, material.createBlockData()
            )
            fallingBlock.dropItem = false
            fallingBlock.setHurtEntities(false)
            fallingBlock.setMetadata("lumasg_debris", FixedMetadataValue(plugin, true))

            // Random outward velocity with upward bias
            val angle = rng.nextDouble() * Math.PI * 2
            val speed = 0.3 + rng.nextDouble() * 0.8
            val upward = 0.4 + rng.nextDouble() * 0.7
            fallingBlock.velocity = Vector(
                Math.cos(angle) * speed,
                upward,
                Math.sin(angle) * speed
            )
        }
    }

    /**
     * Prevents visual debris falling blocks from actually placing when they land.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    fun onDebrisLand(event: EntityChangeBlockEvent) {
        val entity = event.entity as? FallingBlock ?: return
        if (entity.hasMetadata("lumasg_debris")) {
            event.isCancelled = true
            entity.remove()
        }
    }

    // ── Poison Bomb: animated poison cloud with green dust particles ─────────

    @EventHandler
    fun onPoisonBombHit(event: ProjectileHitEvent) {
        val projectile = event.entity
        if (!projectile.hasMetadata("lumasg_poison_bomb")) return

        val shooterUuid = (projectile.shooter as? Player)?.uniqueId
        val center = projectile.location
        val world = center.world
        val radius = 5

        // Apply Poison II to nearby players (skip shooter), with distance-based damage
        world.getNearbyEntities(center, radius.toDouble(), radius.toDouble(), radius.toDouble())
            .filterIsInstance<Player>()
            .filter { it.uniqueId != shooterUuid }
            .forEach { target ->
                target.addPotionEffect(
                    PotionEffect(PotionEffectType.POISON, 100, 1, false, true, true)
                )
                // Distance-based direct damage (2.0 base, with falloff)
                val distance = target.location.distance(center)
                val damage = 2.0 * (1.0 - distance / radius)
                if (damage > 0) target.damage(damage)
            }

        // Poison explosion effects
        world.spawnParticle(Particle.EXPLOSION, center, 1)
        world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1f, 1.2f)
        world.playSound(center, Sound.ENTITY_SPLASH_POTION_BREAK, 2f, 0.8f)

        // Animated poison cloud — 5 seconds of green dust in a spherical volume
        val dustColor = Particle.DustOptions(Color.fromRGB(0, 150, 0), 1.0f)
        object : BukkitRunnable() {
            var ticks = 0
            override fun run() {
                if (ticks >= 100) { cancel(); return }
                for (i in 0 until 20) {
                    val theta = Math.random() * Math.PI * 2
                    val phi = Math.random() * Math.PI
                    val r = Math.random() * radius
                    val px = r * Math.sin(phi) * Math.cos(theta)
                    val py = r * Math.cos(phi)
                    val pz = r * Math.sin(phi) * Math.sin(theta)
                    val particleLoc = center.clone().add(px, py, pz)
                    world.spawnParticle(Particle.DUST, particleLoc, 1, dustColor)
                }
                ticks++
            }
        }.runTaskTimer(plugin, 0L, 2L)
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        cooldowns.remove(event.player.uniqueId)
    }
}
