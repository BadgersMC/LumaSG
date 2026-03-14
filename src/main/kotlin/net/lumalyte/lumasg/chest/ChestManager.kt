package net.lumalyte.lumasg.chest

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.badgersmc.nexus.annotations.Service
import net.badgersmc.nexus.paper.BukkitDispatcher
import org.bukkit.Material
import org.bukkit.block.Chest
import org.bukkit.enchantments.Enchantment
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.Plugin

@Service
class ChestManager(
    private val plugin: Plugin,
    private val bukkitDispatcher: BukkitDispatcher
) {
    private val lootTables = mapOf(
        ChestTier.OUTER to LootTable(ChestTier.OUTER, listOf(
            LootEntry(Material.IRON_SWORD, 10.0),
            LootEntry(Material.BREAD, 15.0, 1, 3),
            LootEntry(Material.LEATHER_CHESTPLATE, 8.0)
        )),
        ChestTier.MIDDLE to LootTable(ChestTier.MIDDLE, listOf(
            LootEntry(Material.DIAMOND_SWORD, 5.0),
            LootEntry(Material.GOLDEN_APPLE, 3.0),
            LootEntry(Material.IRON_CHESTPLATE, 7.0)
        )),
        ChestTier.CENTER to LootTable(ChestTier.CENTER, listOf(
            LootEntry(Material.DIAMOND_SWORD, 10.0, 1, 1, mapOf(Enchantment.SHARPNESS to 2)),
            LootEntry(Material.DIAMOND_CHESTPLATE, 5.0),
            LootEntry(Material.ENCHANTED_GOLDEN_APPLE, 1.0)
        ))
    )

    /** Fill all chests in an arena concurrently — runs entirely on virtual threads. */
    suspend fun fillAll(chests: List<Chest>, tier: ChestTier) = coroutineScope {
        chests.map { chest ->
            launch { fillChest(chest, tier) }
        }
    }

    private suspend fun fillChest(chest: Chest, tier: ChestTier) {
        val table = lootTables[tier] ?: return
        val items = (1..5).mapNotNull { table.roll() }.map { entry ->
            val amount = (entry.minAmount..entry.maxAmount).random()
            ItemStack(entry.material, amount).also { stack ->
                entry.enchantments.forEach { (ench, level) ->
                    stack.addUnsafeEnchantment(ench, level)
                }
            }
        }
        withContext(bukkitDispatcher) {
            chest.blockInventory.clear()
            items.forEach { chest.blockInventory.addItem(it) }
        }
    }
}
