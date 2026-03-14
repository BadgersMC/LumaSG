package net.lumalyte.lumasg.chest

import org.bukkit.Material
import org.bukkit.enchantments.Enchantment

data class LootEntry(
    val material: Material,
    val weight: Double,
    val minAmount: Int = 1,
    val maxAmount: Int = 1,
    val enchantments: Map<Enchantment, Int> = emptyMap()
)
