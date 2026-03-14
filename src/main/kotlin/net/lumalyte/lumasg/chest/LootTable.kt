package net.lumalyte.lumasg.chest

data class LootTable(
    val tier: ChestTier,
    val entries: List<LootEntry>
) {
    /** Select a random item weighted by entry weights. */
    fun roll(): LootEntry? {
        if (entries.isEmpty()) return null
        val total = entries.sumOf { it.weight }
        var roll = Math.random() * total
        for (entry in entries) {
            roll -= entry.weight
            if (roll <= 0) return entry
        }
        return entries.last()
    }
}
