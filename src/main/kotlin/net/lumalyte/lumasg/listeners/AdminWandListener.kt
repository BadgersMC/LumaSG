package net.lumalyte.lumasg.listeners

import net.badgersmc.nexus.annotations.PostConstruct
import net.badgersmc.nexus.annotations.PreDestroy
import net.badgersmc.nexus.annotations.Service
import net.lumalyte.lumasg.domain.Arena
import net.lumalyte.lumasg.domain.SerializableLocation
import net.lumalyte.lumasg.permissions.RankPermissions
import net.lumalyte.lumasg.service.ArenaService
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Particle
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerItemHeldEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerSwapHandItemsEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Service
class AdminWandListener(
    private val plugin: JavaPlugin,
    private val arenaService: ArenaService
) : Listener {
    private val wandKey = NamespacedKey(plugin, "admin_wand")
    private val selectedArenas = ConcurrentHashMap<UUID, String>()
    /** Active particle beam tasks per player — cancelled when switching away or quitting. */
    private val beamTasks = ConcurrentHashMap<UUID, MutableList<BukkitTask>>()

    @PostConstruct
    fun register() {
        plugin.server.pluginManager.registerEvents(this, plugin)
    }

    @PreDestroy
    fun cleanup() {
        hideAllBeams()
    }

    /** Give the admin wand to a player. */
    fun giveWand(player: Player) {
        val wand = createWand()
        player.inventory.addItem(wand)
        player.sendMessage("§aYou received the arena setup wand.")
    }

    fun setSelectedArena(player: Player, arena: Arena) {
        selectedArenas[player.uniqueId] = arena.name
        player.sendMessage("§aSelected arena: §f${arena.displayName}")
        // If already holding wand, refresh beams for new arena
        if (isWand(player.inventory.itemInMainHand)) {
            showSpawnBeams(player, arena)
        }
    }

    fun getSelectedArena(player: Player): Arena? =
        selectedArenas[player.uniqueId]?.let { arenaService.getArena(it) }

    @EventHandler
    fun onInteract(event: PlayerInteractEvent) {
        val player = event.player
        if (!RankPermissions.hasAdminAccess(player)) return
        if (!isWand(player.inventory.itemInMainHand)) return

        val arena = getSelectedArena(player) ?: run {
            player.sendMessage("§cNo arena selected. Use /sg arena select <name>.")
            return
        }

        when (event.action) {
            Action.LEFT_CLICK_BLOCK -> {
                val block = event.clickedBlock ?: return
                event.isCancelled = true
                // Center on block and raise 1 block (matches Java: add(0.5, 1, 0.5))
                val loc = SerializableLocation.fromBukkit(block.location.add(0.5, 1.0, 0.5))
                val updated = arena.copy(spawnPoints = arena.spawnPoints + loc)
                arenaService.addToCache(updated)
                player.sendMessage("§aAdded spawn point #${updated.spawnPoints.size} at §f${block.x}, ${block.y}, ${block.z}")
                // Refresh beams to include new spawn point
                showSpawnBeams(player, updated)
            }
            Action.RIGHT_CLICK_BLOCK -> {
                val block = event.clickedBlock ?: return
                event.isCancelled = true
                val loc = SerializableLocation.fromBukkit(block.location.add(0.5, 1.0, 0.5))
                val updated = arena.copy(center = loc)
                arenaService.addToCache(updated)
                player.sendMessage("§aSet arena center to §f${block.x}, ${block.y}, ${block.z}")
            }
            else -> return
        }
    }

    @EventHandler
    fun onPlayerItemHeld(event: PlayerItemHeldEvent) {
        val player = event.player
        if (!RankPermissions.hasAdminAccess(player)) return

        // Check old slot — hide beams when switching away from wand
        val oldItem = player.inventory.getItem(event.previousSlot)
        if (isWand(oldItem)) {
            hideBeams(player.uniqueId)
        }

        // Check new slot — show beams when switching to wand
        val newItem = player.inventory.getItem(event.newSlot)
        if (isWand(newItem)) {
            val arena = getSelectedArena(player)
            if (arena != null) {
                showSpawnBeams(player, arena)
                player.sendActionBar(net.kyori.adventure.text.Component.text(
                    "§6Arena: §f${arena.displayName} §7| §aL-Click: Add Spawn §7| §eR-Click: Set Center"
                ))
            } else {
                player.sendMessage("§cNo arena selected. Use /sg arena select <name>.")
            }
        }
    }

    @EventHandler
    fun onPlayerDropItem(event: PlayerDropItemEvent) {
        if (isWand(event.itemDrop.itemStack) && RankPermissions.hasAdminAccess(event.player)) {
            event.isCancelled = true
            event.player.sendMessage("§cYou cannot drop the admin wand.")
        }
    }

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        if (!RankPermissions.hasAdminAccess(player)) return
        val item = event.currentItem ?: return
        if (isWand(item)) {
            event.isCancelled = true
        }
    }

    @EventHandler
    fun onPlayerSwapHandItems(event: PlayerSwapHandItemsEvent) {
        if (isWand(event.offHandItem) || isWand(event.mainHandItem)) {
            if (RankPermissions.hasAdminAccess(event.player)) {
                event.isCancelled = true
            }
        }
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        hideBeams(event.player.uniqueId)
        selectedArenas.remove(event.player.uniqueId)
    }

    // ── Particle beam system ─────────────────────────────────────────────

    private fun showSpawnBeams(player: Player, arena: Arena) {
        hideBeams(player.uniqueId)
        val tasks = mutableListOf<BukkitTask>()
        for (spawnLoc in arena.spawnPoints) {
            val bukkit = spawnLoc.toBukkit() ?: continue
            tasks += createBeamTask(bukkit)
        }
        if (tasks.isNotEmpty()) {
            beamTasks[player.uniqueId] = tasks
        }
    }

    private fun hideBeams(playerId: UUID) {
        beamTasks.remove(playerId)?.forEach { task ->
            if (!task.isCancelled) task.cancel()
        }
    }

    private fun hideAllBeams() {
        beamTasks.values.forEach { tasks ->
            tasks.forEach { if (!it.isCancelled) it.cancel() }
        }
        beamTasks.clear()
    }

    /**
     * Creates a repeating task that spawns END_ROD particles in a vertical beam
     * and FIREWORK particles at the base, visible only to admins.
     * Runs every 5 ticks (1/4 second) matching the Java version.
     */
    private fun createBeamTask(location: Location): BukkitTask {
        return Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
            if (location.world == null) return@Runnable
            for (player in Bukkit.getOnlinePlayers()) {
                if (!player.hasPermission("lumasg.admin")) continue
                // Vertical beam of END_ROD particles from y+0 to y+3
                var y = 0.0
                while (y <= 3.0) {
                    player.spawnParticle(
                        Particle.END_ROD,
                        location.clone().add(0.0, y, 0.0),
                        2,        // count
                        0.0, 0.0, 0.0, // offset
                        0.01      // speed
                    )
                    y += 0.25
                }
                // Firework particles at the base for visibility
                player.spawnParticle(
                    Particle.FIREWORK,
                    location,
                    1,
                    0.5, 0.0, 0.5, // offset
                    0.1             // speed
                )
            }
        }, 0L, 5L)
    }

    // ── Wand creation ────────────────────────────────────────────────────

    private fun createWand(): ItemStack {
        val item = ItemStack(Material.BLAZE_ROD)
        val meta = item.itemMeta ?: return item
        meta.displayName(net.kyori.adventure.text.Component.text("§6Arena Setup Wand"))
        meta.lore(listOf(
            net.kyori.adventure.text.Component.text("§7Left Click: Add spawn point"),
            net.kyori.adventure.text.Component.text("§7Right Click: Set arena center")
        ))
        meta.persistentDataContainer.set(wandKey, PersistentDataType.BYTE, 1.toByte())
        item.itemMeta = meta
        return item
    }

    private fun isWand(item: ItemStack?): Boolean {
        if (item == null || item.type != Material.BLAZE_ROD) return false
        return item.itemMeta?.persistentDataContainer?.has(wandKey, PersistentDataType.BYTE) == true
    }
}
