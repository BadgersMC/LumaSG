package net.lumalyte.lumasg.chest.items

import net.badgersmc.nexus.annotations.Service
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Particle
import org.bukkit.Sound
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
        return tag(stack)
    }

    override fun onUse(player: Player, item: ItemStack) {
        val loc = player.eyeLocation.add(player.location.direction.multiply(2))
        // Apply poison to players within 5 blocks
        player.world.getNearbyEntities(loc, 5.0, 5.0, 5.0)
            .filterIsInstance<Player>()
            .filter { it.uniqueId != player.uniqueId }
            .forEach { target ->
                target.addPotionEffect(PotionEffect(PotionEffectType.POISON, 100, 1))
            }
        player.world.spawnParticle(Particle.SPLASH, loc, 30, 2.0, 2.0, 2.0)
        player.world.playSound(loc, Sound.ENTITY_SPLASH_POTION_BREAK, 1f, 0.8f)
        item.amount--
    }
}
