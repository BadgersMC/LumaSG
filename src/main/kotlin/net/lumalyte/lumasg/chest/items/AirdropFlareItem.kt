package net.lumalyte.lumasg.chest.items

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.badgersmc.nexus.annotations.Service
import net.badgersmc.nexus.paper.BukkitDispatcher
import net.kyori.adventure.text.minimessage.MiniMessage
import net.lumalyte.lumasg.chest.ChestManager
import net.lumalyte.lumasg.chest.ChestTier
import net.lumalyte.lumasg.game.GameManager
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Sound
import org.bukkit.block.Chest
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.plugin.Plugin

@Service
class AirdropFlareItem(
    private val plugin: Plugin,
    private val gameManager: GameManager,
    private val chestManager: ChestManager,
    private val bukkitDispatcher: BukkitDispatcher
) : CustomItem {
    override val material = Material.TORCH
    override val displayName = "§bAirdrop Flare"
    override val key = NamespacedKey(plugin, "airdrop_flare")

    private val mm = MiniMessage.miniMessage()

    override fun createStack(): ItemStack {
        val stack = ItemStack(material)
        val meta: ItemMeta = stack.itemMeta ?: return stack
        meta.setDisplayName(displayName)
        meta.lore = listOf("§7Calls in an airdrop!", "§7Right-click to activate")
        stack.itemMeta = meta
        return tag(stack)
    }

    override fun onUse(player: Player, item: ItemStack) {
        val dropLocation = player.location.clone()
        val game = gameManager.getGameForPlayer(player.uniqueId) ?: return

        game.scope.launch {
            withContext(bukkitDispatcher) {
                // Broadcast airdrop announcement
                val announcement = mm.deserialize(
                    "<gold><bold>AIRDROP</bold> <yellow>incoming at <white>${dropLocation.blockX}, ${dropLocation.blockZ}<yellow>! Activated by <white>${player.name}"
                )
                for (uuid in game.players.keys) {
                    Bukkit.getPlayer(uuid)?.let { p ->
                        p.sendMessage(announcement)
                        p.playSound(p.location, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1f, 0.5f)
                    }
                }
            }

            // Wait 3 seconds for dramatic effect
            delay(3_000L)

            withContext(bukkitDispatcher) {
                val chestLoc = dropLocation.clone().add(0.0, 1.0, 0.0)
                chestLoc.block.type = Material.CHEST
                val chest = chestLoc.block.state as? Chest ?: return@withContext
                // Fill with CENTER-tier loot (Task 39: use chestManager, not LootTable.center())
                chestManager.fillAll(listOf(chest), ChestTier.CENTER)
                // Visual effects
                dropLocation.world.strikeLightningEffect(dropLocation)
                dropLocation.world.playSound(dropLocation, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 1f, 1f)
            }
        }

        item.amount--
    }
}
