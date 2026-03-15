package net.lumalyte.lumasg.listeners

import net.badgersmc.nexus.annotations.PostConstruct
import net.badgersmc.nexus.annotations.Service
import net.lumalyte.lumasg.domain.Arena
import net.lumalyte.lumasg.domain.SerializableLocation
import net.lumalyte.lumasg.permissions.RankPermissions
import net.lumalyte.lumasg.service.ArenaService
import org.bukkit.Material
import org.bukkit.NamespacedKey
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
import org.bukkit.plugin.Plugin
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Service
class AdminWandListener(
    private val plugin: Plugin,
    private val arenaService: ArenaService
) : Listener {
    private val wandKey = NamespacedKey(plugin, "admin_wand")
    private val selectedArenas = ConcurrentHashMap<UUID, String>()

    @PostConstruct
    fun register() {
        plugin.server.pluginManager.registerEvents(this, plugin)
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
                val loc = SerializableLocation.fromBukkit(block.location)
                val updated = arena.copy(spawnPoints = arena.spawnPoints + loc)
                arenaService.addToCache(updated)
                player.sendMessage("§aAdded spawn point #${updated.spawnPoints.size} at §f${block.x}, ${block.y}, ${block.z}")
            }
            Action.RIGHT_CLICK_BLOCK -> {
                val block = event.clickedBlock ?: return
                event.isCancelled = true
                val loc = SerializableLocation.fromBukkit(block.location)
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
        val item = player.inventory.getItem(event.newSlot) ?: return
        if (!isWand(item)) return
        if (!RankPermissions.hasAdminAccess(player)) return
        val arena = getSelectedArena(player)
        if (arena != null) {
            player.sendActionBar(net.kyori.adventure.text.Component.text(
                "§6Arena: §f${arena.displayName} §7| §aL-Click: Add Spawn §7| §eR-Click: Set Center"
            ))
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
        selectedArenas.remove(event.player.uniqueId)
    }

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
