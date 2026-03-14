package net.lumalyte.lumasg.chest.items

import net.badgersmc.nexus.annotations.Service
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.plugin.Plugin

@Service
class FireBombItem(private val plugin: Plugin) : CustomItem {
    override val material = Material.FIRE_CHARGE
    override val displayName = "§cFire Bomb"
    override val key = NamespacedKey(plugin, "fire_bomb")

    override fun createStack(): ItemStack {
        val stack = ItemStack(material)
        val meta: ItemMeta = stack.itemMeta ?: return stack
        meta.setDisplayName(displayName)
        meta.lore = listOf("§7Sets area on fire!", "§7Right-click to throw")
        stack.itemMeta = meta
        return stack
    }

    override fun onUse(player: Player, item: ItemStack) {
        val loc = player.location.add(player.location.direction.multiply(2))
        player.world.createExplosion(loc, 2.0f, true, false)
        item.amount--
    }
}
