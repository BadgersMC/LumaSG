package net.lumalyte.lumasg.gui

import org.bukkit.Material
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import xyz.xenondevs.invui.item.builder.ItemBuilder
import xyz.xenondevs.invui.item.impl.SimpleItem

/** Shared utility functions for GUI menus. */
object MenuUtils {

    fun createItem(material: Material, name: String, lore: List<String> = emptyList()): ItemBuilder {
        val builder = ItemBuilder(material).setDisplayName(name)
        if (lore.isNotEmpty()) builder.addLoreLines(*lore.toTypedArray())
        return builder
    }

    fun createBorderItem(): SimpleItem =
        SimpleItem(ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).setDisplayName(" "))

    fun createBackButton(): ItemBuilder =
        ItemBuilder(Material.ARROW).setDisplayName("§7Back")

    fun createNextPageButton(): ItemBuilder =
        ItemBuilder(Material.ARROW).setDisplayName("§7Next Page")

    fun createPrevPageButton(): ItemBuilder =
        ItemBuilder(Material.ARROW).setDisplayName("§7Previous Page")

    fun fillEmptySlots(inventory: Inventory, filler: ItemStack = ItemStack(Material.GRAY_STAINED_GLASS_PANE).apply {
        val meta = itemMeta
        meta?.displayName(net.kyori.adventure.text.Component.text(" "))
        itemMeta = meta
    }) {
        for (i in 0 until inventory.size) {
            if (inventory.getItem(i) == null) {
                inventory.setItem(i, filler)
            }
        }
    }
}
