package net.lumalyte.lumasg.chest.items

import net.badgersmc.nexus.annotations.Service
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.plugin.Plugin

@Service
class KnockbackStickItem(private val plugin: Plugin) : CustomItem {
    override val material = Material.STICK
    override val displayName = "§eKnockback Stick"
    override val key = NamespacedKey(plugin, "knockback_stick")

    override fun createStack(): ItemStack {
        val stack = ItemStack(material)
        val meta: ItemMeta = stack.itemMeta ?: return stack
        meta.setDisplayName(displayName)
        meta.lore = listOf("§7Extreme knockback!", "§7One-time use")
        stack.itemMeta = meta
        stack.addUnsafeEnchantment(Enchantment.KNOCKBACK, 10)
        return stack
    }

    override fun onUse(player: Player, item: ItemStack) { /* handled by damage event */ }
}
