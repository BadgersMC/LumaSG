package net.lumalyte.lumasg.chest.items

import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType

interface CustomItem {
    val material: Material
    val displayName: String
    val key: NamespacedKey
    fun createStack(): ItemStack
    fun onUse(player: Player, item: ItemStack)

    /**
     * Tags [stack] with this item's key so CustomItemListener can route events.
     * Call this after [createStack] when placing in chests or giving to players.
     */
    fun tag(stack: ItemStack): ItemStack {
        val meta = stack.itemMeta ?: return stack
        meta.persistentDataContainer.set(key, PersistentDataType.BOOLEAN, true)
        stack.itemMeta = meta
        return stack
    }

    companion object {
        /** Returns the CustomItem whose key matches the PDC tag on [stack], or null. */
        fun fromStack(stack: ItemStack, registry: Collection<CustomItem>): CustomItem? {
            val meta = stack.itemMeta ?: return null
            return registry.firstOrNull { item ->
                meta.persistentDataContainer.has(item.key, PersistentDataType.BOOLEAN)
            }
        }
    }
}
