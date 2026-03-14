package net.lumalyte.lumasg.chest.items

import net.badgersmc.nexus.annotations.Service
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.plugin.Plugin

@Service
class AirdropFlareItem(private val plugin: Plugin) : CustomItem {
    override val material = Material.TORCH
    override val displayName = "§bAirdrop Flare"
    override val key = NamespacedKey(plugin, "airdrop_flare")

    override fun createStack(): ItemStack {
        val stack = ItemStack(material)
        val meta: ItemMeta = stack.itemMeta ?: return stack
        meta.setDisplayName(displayName)
        meta.lore = listOf("§7Calls in an airdrop!", "§7Right-click to activate")
        stack.itemMeta = meta
        return stack
    }

    override fun onUse(player: Player, item: ItemStack) {
        player.world.strikeLightningEffect(player.location)
        player.sendMessage("§bAirdrop incoming at your location!")
        item.amount--
    }
}
