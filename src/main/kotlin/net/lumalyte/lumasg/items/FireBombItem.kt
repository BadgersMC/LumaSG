package net.lumalyte.lumasg.items

import net.badgersmc.nexus.annotations.Service
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.entity.TNTPrimed
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.metadata.FixedMetadataValue
import org.bukkit.plugin.java.JavaPlugin

@Service
class FireBombItem(private val plugin: JavaPlugin) : CustomItem {
    override val material = Material.FIRE_CHARGE
    override val displayName = "§cFire Bomb"
    override val key = NamespacedKey(plugin, "fire_bomb")

    override fun createStack(): ItemStack {
        val stack = ItemStack(material)
        val meta: ItemMeta = stack.itemMeta ?: return stack
        meta.setDisplayName(displayName)
        meta.lore = listOf("§7Sets area on fire!", "§7Right-click to throw")
        stack.itemMeta = meta
        return tag(stack)
    }

    override fun onUse(player: Player, item: ItemStack) {
        val tnt = player.world.spawn(player.eyeLocation, TNTPrimed::class.java)
        tnt.velocity = player.location.direction.multiply(1.2)
        tnt.fuseTicks = 60 // 3 seconds
        tnt.setMetadata("lumasg_fire_bomb", FixedMetadataValue(plugin, player.uniqueId.toString()))
        item.amount--
    }
}
