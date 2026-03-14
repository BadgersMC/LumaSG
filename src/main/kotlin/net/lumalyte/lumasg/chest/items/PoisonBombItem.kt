package net.lumalyte.lumasg.chest.items

import net.badgersmc.nexus.annotations.Service
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.plugin.Plugin
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType

@Service
class PoisonBombItem(private val plugin: Plugin) : CustomItem {
    override val material = Material.SPLASH_POTION
    override val displayName = "§2Poison Bomb"
    override val key = NamespacedKey(plugin, "poison_bomb")

    override fun createStack(): ItemStack {
        val stack = ItemStack(material)
        val meta: ItemMeta = stack.itemMeta ?: return stack
        meta.setDisplayName(displayName)
        meta.lore = listOf("§7Poisons nearby players!", "§7Right-click to throw")
        stack.itemMeta = meta
        return stack
    }

    override fun onUse(player: Player, item: ItemStack) {
        val nearby = player.world.getNearbyEntities(player.location, 4.0, 4.0, 4.0)
            .filterIsInstance<Player>()
            .filter { it != player }
        nearby.forEach { target ->
            target.addPotionEffect(PotionEffect(PotionEffectType.POISON, 100, 1))
        }
        item.amount--
    }
}
