package net.lumalyte.lumasg.items

import net.badgersmc.nexus.annotations.Service
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.plugin.java.JavaPlugin

@Service
class KnockbackStickItem(private val plugin: JavaPlugin) : CustomItem {
    override val material = Material.STICK
    override val displayName = "§eKnockback Stick"
    override val key = NamespacedKey(plugin, "knockback_stick")

    override fun createStack(): ItemStack {
        val stack = ItemStack(material)
        val meta: ItemMeta = stack.itemMeta ?: return stack
        meta.setDisplayName(displayName)
        meta.lore = listOf("§7Sends players flying!", "§7Right-click to swing")
        meta.addEnchant(Enchantment.KNOCKBACK, 5, true)
        stack.itemMeta = meta
        return tag(stack)
    }

    override fun onUse(player: Player, item: ItemStack) {
        // Knockback handled by melee enchantment — nothing extra needed for right-click
    }
}
