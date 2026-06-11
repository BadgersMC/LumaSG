package net.lumalyte.lumasg.items

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.badgersmc.nexus.annotations.Service
import net.badgersmc.nexus.paper.BukkitDispatcher
import net.kyori.adventure.text.minimessage.MiniMessage
import net.lumalyte.lumasg.chest.ChestManager
import net.lumalyte.lumasg.game.GameManager
import net.lumalyte.lumasg.util.MeteorUtils
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.block.Chest
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.plugin.java.JavaPlugin

@Service
class AirdropFlareItem(
    private val plugin: JavaPlugin,
    private val gameManager: GameManager,
    private val chestManager: ChestManager,
    private val bukkitDispatcher: BukkitDispatcher
) : CustomItem {
    override val material = Material.TORCH
    override val displayName = "§bAirdrop Flare"
    override val key = NamespacedKey(plugin, "airdrop_flare")

    private val mm = MiniMessage.miniMessage()

    override fun createStack(): ItemStack {
        val stack = ItemStack(material)
        val meta: ItemMeta = stack.itemMeta ?: return stack
        meta.setDisplayName(displayName)
        meta.lore = listOf("§7Calls in an airdrop!", "§7Right-click to activate")
        stack.itemMeta = meta
        return tag(stack)
    }

    @Suppress("LongMethod") // single coroutine driving the full airdrop sequence
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
                            MeteorUtils.spawnGroundCircle(dropLocation)
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

            // ── Meteor flight + impact ────────────────────────────────────
            MeteorUtils.launchMeteor(
                target = dropLocation,
                bukkitDispatcher = bukkitDispatcher
            ) {
                // onImpact — runs on main thread
                MeteorUtils.spawnExplosion(center = dropLocation, plugin = plugin)

                // Place airdrop chest safely
                val chestLoc = findSolidGround(dropLocation)
                if (chestLoc == null) {
                    plugin.logger.fine("AirdropFlare: no solid ground (void column), skipping chest")
                } else {
                    val chest = placeChestSafely(chestLoc)
                    if (chest != null) {
                        chestManager.fillAll(listOf(chest), "rare")
                    } else {
                        plugin.logger.fine("AirdropFlare: no safe chest location near ${dropLocation.blockX}, ${dropLocation.blockZ}")
                    }
                }

                // Announce arrival
                val arrivalMsg = mm.deserialize(
                    "<gold><bold>Airdrop has landed!</bold></gold> <yellow>Coordinates: ${dropLocation.blockX}, ${dropLocation.blockZ}</yellow>"
                )
                for (uuid in game.players.keys) {
                    Bukkit.getPlayer(uuid)?.sendMessage(arrivalMsg)
                }
            }

            indicatorJob.cancel()

            // ── Chest protection glow (END_ROD particles for 9 seconds) ─
            val chestLoc = findSolidGround(dropLocation)
            repeat(9) {
                delay(1_000)
                withContext(bukkitDispatcher) {
                    if (chestLoc != null && chestLoc.block.type == Material.CHEST) {
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

    // ── Safe chest placement ────────────────────────────────────────────────

    private fun placeChestSafely(loc: Location): Chest? {
        // Try original spot
        if (loc.block.type.isAir && !hasPlayerAt(loc)) {
            loc.block.type = Material.CHEST
            return loc.block.state as? Chest
        }
        // Search upward for an air block
        for (dy in 1..5) {
            val candidate = loc.clone().add(0.0, dy.toDouble(), 0.0)
            if (candidate.block.type.isAir && !hasPlayerAt(candidate)) {
                candidate.block.type = Material.CHEST
                return candidate.block.state as? Chest
            }
        }
        return null
    }

    private fun hasPlayerAt(loc: Location): Boolean =
        loc.world?.getNearbyEntities(loc, 1.0, 1.0, 1.0)?.any { it is Player } ?: false

    // ── Find solid ground for chest placement ───────────────────────────────

    /** Returns the block above the first solid ground below [impact], or null over a void column. */
    private fun findSolidGround(impact: Location): Location? {
        val loc = impact.clone()
        val world = loc.world ?: return null
        while (loc.y > world.minHeight && !loc.block.type.isSolid) {
            loc.subtract(0.0, 1.0, 0.0)
        }
        if (!loc.block.type.isSolid) return null // hit the void without finding solid ground
        loc.add(0.0, 1.0, 0.0) // place on top of solid block
        return loc
    }
}
