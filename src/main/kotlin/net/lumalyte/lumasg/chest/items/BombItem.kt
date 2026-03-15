package net.lumalyte.lumasg.chest.items

import net.badgersmc.nexus.annotations.Service
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.entity.TNTPrimed
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.metadata.FixedMetadataValue
import org.bukkit.plugin.Plugin

@Service
class BombItem(private val plugin: Plugin) : CustomItem {
    override val material = Material.TNT
    override val displayName = "§4Bomb"
    override val key = NamespacedKey(plugin, "bomb")

    override fun createStack(): ItemStack {
        val stack = ItemStack(material)
        val meta: ItemMeta = stack.itemMeta ?: return stack
        meta.setDisplayName(displayName)
        meta.lore = listOf("§7Explosive with knockback!", "§7Right-click to throw")
        stack.itemMeta = meta
        return tag(stack)
    }

    override fun onUse(player: Player, item: ItemStack) {
        val tnt = player.world.spawn(player.eyeLocation, TNTPrimed::class.java)
        tnt.velocity = player.location.direction.multiply(1.2)
        tnt.fuseTicks = 50 // 2.5 seconds
        tnt.setMetadata("lumasg_bomb", FixedMetadataValue(plugin, player.uniqueId.toString()))
        item.amount--
    }
}
