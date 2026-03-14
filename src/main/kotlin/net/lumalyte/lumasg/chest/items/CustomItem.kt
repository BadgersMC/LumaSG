package net.lumalyte.lumasg.chest.items

import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

interface CustomItem {
    val material: Material
    val displayName: String
    val key: NamespacedKey
    fun createStack(): ItemStack
    fun onUse(player: Player, item: ItemStack)
}
