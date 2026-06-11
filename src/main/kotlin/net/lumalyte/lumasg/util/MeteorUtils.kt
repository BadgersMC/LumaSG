package net.lumalyte.lumasg.util

import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import net.badgersmc.nexus.paper.BukkitDispatcher
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.metadata.FixedMetadataValue
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.util.Vector
import java.util.UUID
import kotlin.math.*

object MeteorUtils {
    private val GOLDEN_RATIO = Math.PI * (1 + sqrt(5.0))

    @Suppress("LongMethod") // particle/sound choreography for one sphere effect
    fun spawnMeteorSphere(
        center: Location,
        sphereRadius: Int = 3,
        particleDensity: Int = 6,
        particleSpread: Double = 1.5
    ) {
        val world = center.world

        // Core particles — dense inner sphere using spherical coordinates
        for (i in 0 until particleDensity * 3) {
            val r = Math.random() * (sphereRadius * 0.7)
            val theta = Math.random() * 2 * Math.PI
            val phi = acos(2 * Math.random() - 1)
            val x = r * sin(phi) * cos(theta)
            val y = r * sin(phi) * sin(theta)
            val z = r * cos(phi)
            val loc = center.clone().add(x, y, z)
            val spread = particleSpread * 0.1

            if (r < sphereRadius * 0.3) {
                // Inner core — very hot
                world.spawnParticle(Particle.LAVA, loc, 2, spread, spread, spread, 0.0)
                world.spawnParticle(Particle.FLAME, loc, 3, spread, spread, spread, 0.05)
                world.spawnParticle(Particle.SOUL_FIRE_FLAME, loc, 1, spread, spread, spread, 0.02)
            } else {
                // Outer core
                world.spawnParticle(Particle.FLAME, loc, 2, spread, spread, spread, 0.02)
                world.spawnParticle(Particle.FIREWORK, loc, 2, spread, spread, spread, 0.01)
                world.spawnParticle(Particle.SOUL_FIRE_FLAME, loc, 1, spread, spread, spread, 0.01)
            }
        }

        // Outer shell — golden ratio distribution for even point spacing
        val shellPoints = particleDensity * 6
        for (i in 0 until shellPoints) {
            val phi = acos(1 - 2.0 * (i + 0.5) / shellPoints)
            val theta = GOLDEN_RATIO * i
            val x = sphereRadius * sin(phi) * cos(theta)
            val y = sphereRadius * sin(phi) * sin(theta)
            val z = sphereRadius * cos(phi)
            val shellLoc = center.clone().add(x, y, z)

            world.spawnParticle(Particle.CLOUD, shellLoc, 2, 0.1, 0.1, 0.1, 0.0)
            world.spawnParticle(Particle.FIREWORK, shellLoc, 2, 0.0, 0.0, 0.0, 0.05)
            world.spawnParticle(Particle.SOUL_FIRE_FLAME, shellLoc, 1, 0.1, 0.1, 0.1, 0.02)
            if (Math.random() < 0.4) {
                world.spawnParticle(Particle.FLAME, shellLoc, 2, 0.1, 0.1, 0.1, 0.02)
            }
        }

        // Trailing particles — exponential falloff from hot to smoke
        val trailLength = sphereRadius * 4.0
        for (i in 0 until particleDensity * 3) {
            val trailProgress = Math.random()
            val trailRadius = sphereRadius * (1 + Math.random())
            val trailLoc = center.clone().add(
                (Math.random() - 0.5) * trailRadius,
                -trailProgress * trailLength,
                (Math.random() - 0.5) * trailRadius
            )
            when {
                trailProgress < 0.3 -> {
                    world.spawnParticle(Particle.FLAME, trailLoc, 2, 0.2, 0.2, 0.2, 0.02)
                    world.spawnParticle(Particle.FIREWORK, trailLoc, 2, 0.1, 0.1, 0.1, 0.01)
                    world.spawnParticle(Particle.SOUL_FIRE_FLAME, trailLoc, 1, 0.1, 0.1, 0.1, 0.01)
                }
                trailProgress < 0.7 -> {
                    world.spawnParticle(Particle.CLOUD, trailLoc, 2, 0.3, 0.3, 0.3, 0.01)
                    if (Math.random() < 0.4) {
                        world.spawnParticle(Particle.FLAME, trailLoc, 2, 0.1, 0.1, 0.1, 0.01)
                    }
                }
                else -> {
                    world.spawnParticle(Particle.CLOUD, trailLoc, 2, 0.4, 0.4, 0.4, 0.0)
                }
            }
        }
    }

    fun spawnPhysicsExplosion(center: Location, explosionRadius: Int = 4) {
        val world = center.world
        world.spawnParticle(Particle.EXPLOSION, center, 3, 1.0, 1.0, 1.0, 0.0)
        world.spawnParticle(Particle.EXPLOSION, center, 8, 2.0, 2.0, 2.0, 0.1)
        world.spawnParticle(Particle.FLAME, center, 30, 2.0, 2.0, 2.0, 0.2)
        world.spawnParticle(Particle.LAVA, center, 20, 2.0, 2.0, 2.0, 0.1)
        world.spawnParticle(Particle.LARGE_SMOKE, center, 25, 3.0, 3.0, 3.0, 0.1)

        // Debris — falling magma dust in a ring
        for (i in 0 until 50) {
            val a = Math.random() * Math.PI * 2
            val r = Math.random() * explosionRadius
            val h = Math.random() * 2
            val debrisLoc = center.clone().add(cos(a) * r, h, sin(a) * r)
            world.spawnParticle(
                Particle.FALLING_DUST, debrisLoc, 5, 0.2, 0.2, 0.2, 0.1,
                Material.MAGMA_BLOCK.createBlockData()
            )
        }
    }

    fun spawnShockwaveRing(center: Location, explosionRadius: Int = 4) {
        val world = center.world
        var a = 0.0
        while (a < Math.PI * 2) {
            var r = 0.5
            while (r <= explosionRadius) {
                val loc = center.clone().add(cos(a) * r, 0.1, sin(a) * r)
                world.spawnParticle(Particle.FLAME, loc, 1, 0.1, 0.1, 0.1, 0.0)
                if (r % 1.0 == 0.0) {
                    world.spawnParticle(Particle.LAVA, loc, 1, 0.1, 0.1, 0.1, 0.0)
                }
                r += 0.5
            }
            a += Math.PI / 16
        }
    }

    fun spawnGroundCircle(center: Location, radius: Int = 8, color: Color = Color.RED, stepDegrees: Int = 10) {
        val world = center.world
        val dustOptions = Particle.DustOptions(color, 1.0f)
        for (deg in 0 until 360 step stepDegrees) {
            val rad = Math.toRadians(deg.toDouble())
            val x = center.x + radius * cos(rad)
            val z = center.z + radius * sin(rad)
            val loc = Location(world, x, center.y + 0.5, z)
            world.spawnParticle(Particle.DUST, loc, 1, dustOptions)
        }
    }

    fun spawnVisualDebris(center: Location, radius: Int, count: Int = radius * 5, plugin: JavaPlugin) {
        val world = center.world
        val rng = java.util.Random()

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

        for (i in 0 until count) {
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

    fun spawnExplosion(
        center: Location,
        damage: Double = 6.0,
        explosionRadius: Int = 4,
        immuneUUIDs: Set<UUID> = emptySet(),
        debrisCountMultiplier: Int = 5,
        plugin: JavaPlugin
    ) {
        val world = center.world

        spawnPhysicsExplosion(center, explosionRadius)
        spawnShockwaveRing(center, explosionRadius)
        spawnVisualDebris(center, explosionRadius, explosionRadius * debrisCountMultiplier, plugin)

        // Rising fire column
        for (i in 0 until 20) {
            val columnLoc = center.clone().add(0.0, i * 0.5, 0.0)
            world.spawnParticle(Particle.FLAME, columnLoc, 3, 0.2, 0.1, 0.2, 0.05)
            world.spawnParticle(Particle.LARGE_SMOKE, columnLoc, 2, 0.2, 0.1, 0.2, 0.02)
        }

        // Multi-layer sound
        world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 2f, 0.7f)
        world.playSound(center, Sound.ENTITY_DRAGON_FIREBALL_EXPLODE, 1.5f, 0.8f)
        world.playSound(center, Sound.BLOCK_ANCIENT_DEBRIS_BREAK, 1f, 0.5f)
        world.playSound(center, Sound.BLOCK_FIRE_AMBIENT, 1f, 0.5f)
        world.playSound(center, Sound.BLOCK_LAVA_POP, 1f, 0.8f)

        // Damage + knockback
        world.getNearbyEntities(center, explosionRadius.toDouble(), explosionRadius.toDouble(), explosionRadius.toDouble())
            .filterIsInstance<Player>()
            .filter { it.uniqueId !in immuneUUIDs }
            .forEach { target ->
                val dist = target.location.distance(center)
                if (dist <= explosionRadius) {
                    val mult = 1.0 - (dist / explosionRadius)
                    val dmg = damage * mult
                    if (dmg > 0) target.damage(dmg)
                    val kb = target.location.toVector().subtract(center.toVector())
                    if (kb.lengthSquared() < 0.01) {
                        target.velocity = Vector((Math.random() - 0.5) * 0.5, 0.3, (Math.random() - 0.5) * 0.5)
                    } else {
                        val normalized = kb.normalize().multiply(mult * 0.5)
                        normalized.y = maxOf(normalized.y, 0.2)
                        target.velocity = normalized
                    }
                }
            }
    }

    suspend fun launchMeteor(
        target: Location,
        spawnHeight: Int = 150,
        horizontalOffset: Int = 50,
        approachAngle: Double = Math.random() * 2 * Math.PI,
        bukkitDispatcher: BukkitDispatcher,
        onImpact: suspend () -> Unit
    ) {
        val meteorStart = target.clone().add(
            cos(approachAngle) * horizontalOffset, spawnHeight.toDouble(), sin(approachAngle) * horizontalOffset
        )
        val totalDistance = meteorStart.distance(target)
        var currentLocation = meteorStart.clone()
        var ticksAlive = 0

        while (ticksAlive < 2000) {
            ticksAlive++
            val distanceToTarget = currentLocation.distance(target)
            val progress = (1.0 - (distanceToTarget / totalDistance)).coerceIn(0.0, 1.0)
            val currentSpeed = 6.0 * (0.5 + 0.5 * progress)
            val arcHeight = sin(progress * Math.PI) * 40.0
            val direction = target.toVector().subtract(currentLocation.toVector()).normalize()
            direction.multiply(currentSpeed)
            val yOffset = 0.05 * arcHeight
            if (progress < 0.5) direction.setY(direction.y + yOffset) else direction.setY(direction.y - yOffset)
            currentLocation.add(direction)

            withContext(bukkitDispatcher) {
                spawnMeteorSphere(currentLocation)
                if (Math.random() < 0.3) {
                    target.world.playSound(currentLocation, Sound.ENTITY_BLAZE_SHOOT, 0.5f, 0.5f)
                }
            }

            if (distanceToTarget < 3 || currentLocation.y <= target.y) break
            delay(50)
        }

        withContext(bukkitDispatcher) {
            onImpact()
        }
    }
}
