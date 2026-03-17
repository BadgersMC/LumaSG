package net.lumalyte.lumasg.items

import net.badgersmc.nexus.annotations.Service
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.entity.WindCharge
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.metadata.FixedMetadataValue
import org.bukkit.plugin.java.JavaPlugin

@Service
class PoisonBombItem(private val plugin: JavaPlugin) : CustomItem {
    override val material = Material.WIND_CHARGE
    override val displayName = "§2Poison Bomb"
    override val key = NamespacedKey(plugin, "poison_bomb")

    override fun createStack(): ItemStack {
        val stack = ItemStack(material)
        val meta: ItemMeta = stack.itemMeta ?: return stack
        meta.setDisplayName(displayName)
        meta.lore = listOf("§7Poisons nearby players!", "§7Right-click to throw")
        stack.itemMeta = meta
        return tag(stack)
    }

    override fun onUse(player: Player, item: ItemStack) {
        // Launch a wind charge projectile tagged for poison effect on impact
        val charge = player.launchProjectile(WindCharge::class.java)
        charge.velocity = player.location.direction.multiply(1.5)
        charge.setMetadata("lumasg_poison_bomb", FixedMetadataValue(plugin, player.uniqueId.toString()))
        item.amount--
    }
}
