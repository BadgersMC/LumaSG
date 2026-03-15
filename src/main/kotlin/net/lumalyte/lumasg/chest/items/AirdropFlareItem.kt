package net.lumalyte.lumasg.chest.items

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.badgersmc.nexus.annotations.Service
import net.badgersmc.nexus.paper.BukkitDispatcher
import net.kyori.adventure.text.minimessage.MiniMessage
import net.lumalyte.lumasg.chest.ChestManager
import net.lumalyte.lumasg.chest.ChestTier
import net.lumalyte.lumasg.game.GameManager
import org.bukkit.Bukkit
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.block.Chest
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.plugin.Plugin
import org.bukkit.util.Vector
import kotlin.math.*

@Service
class AirdropFlareItem(
    private val plugin: Plugin,
    private val gameManager: GameManager,
    private val chestManager: ChestManager,
    private val bukkitDispatcher: BukkitDispatcher
) : CustomItem {
    override val material = Material.TORCH
    override val displayName = "§bAirdrop Flare"
    override val key = NamespacedKey(plugin, "airdrop_flare")

    private val mm = MiniMessage.miniMessage()

    // Meteor physics constants (ported from Java AirdropBehavior)
    private companion object {
        const val METEOR_HEIGHT = 150
        const val METEOR_HORIZONTAL_SPEED = 6.0
        const val METEOR_ARC_HEIGHT = 40.0
        const val METEOR_GRAVITY = 0.05
        const val METEOR_SPHERE_RADIUS = 3
        const val PARTICLE_DENSITY = 6
        const val PARTICLE_SPREAD = 1.5
        const val PARTICLE_CIRCLE_RADIUS = 8
        const val EXPLOSION_RADIUS = 4
        const val EXPLOSION_DAMAGE = 6.0
        val GOLDEN_RATIO = Math.PI * (1 + sqrt(5.0)) // π(1+√5)
    }

    override fun createStack(): ItemStack {
        val stack = ItemStack(material)
        val meta: ItemMeta = stack.itemMeta ?: return stack
        meta.setDisplayName(displayName)
        meta.lore = listOf("§7Calls in an airdrop!", "§7Right-click to activate")
        stack.itemMeta = meta
        return tag(stack)
    }

    override fun onUse(player: Player, item: ItemStack) {
        val dropLocation = player.location.clone()
        val game = gameManager.getGameForPlayer(player.uniqueId) ?: return
        val world = dropLocation.world

        game.scope.launch {
            // ── Announcement + flare activation effects ──────────────────
            withContext(bukkitDispatcher) {
                val announcement = mm.deserialize(
                    "<gold><bold>AIRDROP</bold> <yellow>incoming at <white>${dropLocation.blockX}, ${dropLocation.blockZ}<yellow>! Activated by <white>${player.name}"
                )
                for (uuid in game.players.keys) {
                    Bukkit.getPlayer(uuid)?.let { p ->
                        p.sendMessage(announcement)
                        p.playSound(p.location, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 2f, 0.8f)
                    }
                }
                // Flare activation particles
                world.spawnParticle(Particle.FIREWORK, dropLocation, 20, 2.0, 2.0, 2.0, 0.1)
                world.spawnParticle(Particle.FLAME, dropLocation, 30, 1.0, 1.0, 1.0, 0.05)
                world.playSound(dropLocation, Sound.BLOCK_BEACON_ACTIVATE, 1.5f, 1.2f)
            }

            // ── Drop zone indicator — blinking red dust particle circle ──
            val indicatorJob = launch {
                var elapsed = 0
                while (elapsed < 100) { // 5 seconds (100 half-ticks)
                    if ((elapsed / 20) % 2 == 0) {
                        withContext(bukkitDispatcher) {
                            spawnDropZoneCircle(dropLocation)
                        }
                    }
                    delay(500) // every 0.5s
                    elapsed += 10
                }
            }

            // ── Meteor announcement ─────────────────────────────────────
            delay(2_000L)
            withContext(bukkitDispatcher) {
                val meteorMsg = mm.deserialize(
                    "<gold><bold>Meteor incoming!</bold></gold> <yellow>Impact in approximately 5 seconds...</yellow>"
                )
                for (uuid in game.players.keys) {
                    Bukkit.getPlayer(uuid)?.sendMessage(meteorMsg)
                }
            }

            // ── Calculate meteor trajectory ─────────────────────────────
            val angle = Math.random() * 2 * Math.PI
            val spawnDistance = 50.0
            val meteorStart = dropLocation.clone().add(
                cos(angle) * spawnDistance,
                METEOR_HEIGHT.toDouble(),
                sin(angle) * spawnDistance
            )
            val totalDistance = meteorStart.distance(dropLocation)
            var currentLocation = meteorStart.clone()
            var currentSpeed = METEOR_HORIZONTAL_SPEED * 0.5
            var ticksAlive = 0

            // ── Meteor flight loop (tick-by-tick via coroutine) ─────────
            while (ticksAlive < 2000) {
                ticksAlive++
                val distanceToTarget = currentLocation.distance(dropLocation)

                // Progress and speed ramp
                val progress = (1.0 - (distanceToTarget / totalDistance)).coerceIn(0.0, 1.0)
                currentSpeed = METEOR_HORIZONTAL_SPEED * (0.5 + 0.5 * progress)

                // Arc height
                val arcHeight = sin(progress * Math.PI) * METEOR_ARC_HEIGHT

                // Direction with arc
                val direction = dropLocation.toVector().subtract(currentLocation.toVector()).normalize()
                direction.multiply(currentSpeed)
                val yOffset = METEOR_GRAVITY * arcHeight
                if (progress < 0.5) {
                    direction.setY(direction.y + yOffset)
                } else {
                    direction.setY(direction.y - yOffset)
                }

                currentLocation.add(direction)

                withContext(bukkitDispatcher) {
                    spawnMeteorSphere(currentLocation)
                    // Ambient blaze sound ~30% of ticks
                    if (Math.random() < 0.3) {
                        world.playSound(currentLocation, Sound.ENTITY_BLAZE_SHOOT, 0.5f, 0.5f)
                    }
                }

                // Impact check
                if (distanceToTarget < METEOR_SPHERE_RADIUS || currentLocation.y <= dropLocation.y) {
                    break
                }

                delay(50) // 1 tick
            }

            indicatorJob.cancel()

            // ── Meteor impact ───────────────────────────────────────────
            withContext(bukkitDispatcher) {
                // Physics explosion particles
                spawnPhysicsExplosion(dropLocation)

                // Shockwave ring
                spawnShockwaveRing(dropLocation)

                // Rising fire column
                for (i in 0 until 20) {
                    val columnLoc = dropLocation.clone().add(0.0, i * 0.5, 0.0)
                    world.spawnParticle(Particle.FLAME, columnLoc, 3, 0.2, 0.1, 0.2, 0.05)
                    world.spawnParticle(Particle.LARGE_SMOKE, columnLoc, 2, 0.2, 0.1, 0.2, 0.02)
                }

                // Sounds
                world.playSound(dropLocation, Sound.ENTITY_GENERIC_EXPLODE, 2f, 0.7f)
                world.playSound(dropLocation, Sound.ENTITY_DRAGON_FIREBALL_EXPLODE, 1.5f, 0.8f)
                world.playSound(dropLocation, Sound.BLOCK_ANCIENT_DEBRIS_BREAK, 1f, 0.5f)
                world.playSound(dropLocation, Sound.BLOCK_FIRE_AMBIENT, 1f, 0.5f)
                world.playSound(dropLocation, Sound.BLOCK_LAVA_POP, 1f, 0.8f)

                // Damage + knockback nearby players
                world.getNearbyEntities(
                    dropLocation, EXPLOSION_RADIUS.toDouble(),
                    EXPLOSION_RADIUS.toDouble(), EXPLOSION_RADIUS.toDouble()
                ).filterIsInstance<Player>().forEach { target ->
                    val dist = target.location.distance(dropLocation)
                    if (dist <= EXPLOSION_RADIUS) {
                        val mult = 1.0 - (dist / EXPLOSION_RADIUS)
                        val dmg = EXPLOSION_DAMAGE * mult
                        if (dmg > 0) target.damage(dmg)
                        // Safe knockback calculation
                        val kb = target.location.toVector().subtract(dropLocation.toVector())
                        if (kb.lengthSquared() < 0.01) {
                            target.velocity = Vector(
                                (Math.random() - 0.5) * 0.5, 0.3, (Math.random() - 0.5) * 0.5
                            )
                        } else {
                            val normalized = kb.normalize().multiply(mult * 0.5)
                            normalized.y = maxOf(normalized.y, 0.2)
                            target.velocity = normalized
                        }
                    }
                }

                // Place airdrop chest
                val chestLoc = findSolidGround(dropLocation)
                chestLoc.block.type = Material.CHEST
                val chest = chestLoc.block.state as? Chest
                if (chest != null) {
                    chestManager.fillAll(listOf(chest), ChestTier.CENTER)
                }

                // Announce arrival
                val arrivalMsg = mm.deserialize(
                    "<gold><bold>Airdrop has landed!</bold></gold> <yellow>Coordinates: ${dropLocation.blockX}, ${dropLocation.blockZ}</yellow>"
                )
                for (uuid in game.players.keys) {
                    Bukkit.getPlayer(uuid)?.sendMessage(arrivalMsg)
                }
            }

            // ── Chest protection glow (END_ROD particles for 9 seconds) ─
            val chestLoc = findSolidGround(dropLocation)
            repeat(9) {
                delay(1_000)
                withContext(bukkitDispatcher) {
                    if (chestLoc.block.type == Material.CHEST) {
                        world.spawnParticle(
                            Particle.END_ROD,
                            chestLoc.clone().add(0.5, 1.0, 0.5),
                            3, 0.3, 0.3, 0.3, 0.01
                        )
                    }
                }
            }
        }

        item.amount--
    }

    // ── Meteor sphere with golden ratio point distribution ───────────────────

    private fun spawnMeteorSphere(center: Location) {
        val world = center.world

        // Core particles — dense inner sphere using spherical coordinates
        for (i in 0 until PARTICLE_DENSITY * 3) {
            val r = Math.random() * (METEOR_SPHERE_RADIUS * 0.7)
            val theta = Math.random() * 2 * Math.PI
            val phi = acos(2 * Math.random() - 1)
            val x = r * sin(phi) * cos(theta)
            val y = r * sin(phi) * sin(theta)
            val z = r * cos(phi)
            val loc = center.clone().add(x, y, z)
            val spread = PARTICLE_SPREAD * 0.1

            if (r < METEOR_SPHERE_RADIUS * 0.3) {
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
        val shellPoints = PARTICLE_DENSITY * 6
        for (i in 0 until shellPoints) {
            val phi = acos(1 - 2.0 * (i + 0.5) / shellPoints)
            val theta = GOLDEN_RATIO * i
            val x = METEOR_SPHERE_RADIUS * sin(phi) * cos(theta)
            val y = METEOR_SPHERE_RADIUS * sin(phi) * sin(theta)
            val z = METEOR_SPHERE_RADIUS * cos(phi)
            val shellLoc = center.clone().add(x, y, z)

            world.spawnParticle(Particle.CLOUD, shellLoc, 2, 0.1, 0.1, 0.1, 0.0)
            world.spawnParticle(Particle.FIREWORK, shellLoc, 2, 0.0, 0.0, 0.0, 0.05)
            world.spawnParticle(Particle.SOUL_FIRE_FLAME, shellLoc, 1, 0.1, 0.1, 0.1, 0.02)
            if (Math.random() < 0.4) {
                world.spawnParticle(Particle.FLAME, shellLoc, 2, 0.1, 0.1, 0.1, 0.02)
            }
        }

        // Trailing particles — exponential falloff from hot to smoke
        val trailLength = METEOR_SPHERE_RADIUS * 4.0
        for (i in 0 until PARTICLE_DENSITY * 3) {
            val trailProgress = Math.random()
            val trailRadius = METEOR_SPHERE_RADIUS * (1 + Math.random())
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

    // ── Physics explosion with debris ───────────────────────────────────────

    private fun spawnPhysicsExplosion(center: Location) {
        val world = center.world
        world.spawnParticle(Particle.EXPLOSION, center, 3, 1.0, 1.0, 1.0, 0.0)
        world.spawnParticle(Particle.EXPLOSION, center, 8, 2.0, 2.0, 2.0, 0.1)
        world.spawnParticle(Particle.FLAME, center, 30, 2.0, 2.0, 2.0, 0.2)
        world.spawnParticle(Particle.LAVA, center, 20, 2.0, 2.0, 2.0, 0.1)
        world.spawnParticle(Particle.LARGE_SMOKE, center, 25, 3.0, 3.0, 3.0, 0.1)

        // Debris — falling magma dust in a ring
        for (i in 0 until 50) {
            val a = Math.random() * Math.PI * 2
            val r = Math.random() * EXPLOSION_RADIUS
            val h = Math.random() * 2
            val debrisLoc = center.clone().add(cos(a) * r, h, sin(a) * r)
            world.spawnParticle(
                Particle.FALLING_DUST, debrisLoc, 5, 0.2, 0.2, 0.2, 0.1,
                Material.MAGMA_BLOCK.createBlockData()
            )
        }
    }

    // ── Shockwave ring ──────────────────────────────────────────────────────

    private fun spawnShockwaveRing(center: Location) {
        val world = center.world
        var a = 0.0
        while (a < Math.PI * 2) {
            var r = 0.5
            while (r <= EXPLOSION_RADIUS) {
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

    // ── Drop zone blinking circle ───────────────────────────────────────────

    private fun spawnDropZoneCircle(center: Location) {
        val world = center.world
        val dustOptions = Particle.DustOptions(Color.RED, 1.0f)
        for (deg in 0 until 360 step 10) {
            val rad = Math.toRadians(deg.toDouble())
            val x = center.x + PARTICLE_CIRCLE_RADIUS * cos(rad)
            val z = center.z + PARTICLE_CIRCLE_RADIUS * sin(rad)
            val loc = Location(world, x, center.y + 0.5, z)
            world.spawnParticle(Particle.DUST, loc, 1, dustOptions)
        }
    }

    // ── Find solid ground for chest placement ───────────────────────────────

    private fun findSolidGround(impact: Location): Location {
        val loc = impact.clone()
        while (loc.y > 0 && !loc.block.type.isSolid) {
            loc.subtract(0.0, 1.0, 0.0)
        }
        loc.add(0.0, 1.0, 0.0) // place on top of solid block
        return loc
    }
}
