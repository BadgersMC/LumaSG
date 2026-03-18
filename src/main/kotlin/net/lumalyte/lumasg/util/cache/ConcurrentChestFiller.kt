package net.lumalyte.lumasg.util.cache

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.badgersmc.nexus.annotations.PreDestroy
import net.badgersmc.nexus.annotations.Service
import net.badgersmc.nexus.paper.BukkitDispatcher
import net.lumalyte.lumasg.chest.ChestManager
import net.lumalyte.lumasg.domain.LootMode
import org.bukkit.block.Chest
import org.slf4j.LoggerFactory

/**
 * Concurrent chest filling system using Kotlin coroutines.
 *
 * Leverages [LootTableCache] for pre-generated loot when available,
 * falling back to [ChestManager.fillChest] for direct generation.
 * All inventory access is dispatched to the main thread via [BukkitDispatcher].
 */
@Service
class ConcurrentChestFiller(
    private val chestManager: ChestManager,
    private val lootTableCache: LootTableCache,
    private val bukkitDispatcher: BukkitDispatcher
) {
    private val logger = LoggerFactory.getLogger(ConcurrentChestFiller::class.java)

    // ── Public API ──────────────────────────────────────────────────────────

    /**
     * Fills all [chests] concurrently with loot from [tier].
     *
     * Each chest is filled in its own coroutine; inventory mutations
     * are dispatched to the main thread via [BukkitDispatcher].
     *
     * @param chests The list of chests to fill.
     * @param tier The loot tier to use.
     * @param mode The loot mode to use.
     */
    suspend fun fillChestsForGame(chests: List<Chest>, tier: String, mode: LootMode = LootMode.MODERN) = coroutineScope {
        if (chests.isEmpty()) return@coroutineScope

        var successCount = 0
        var failCount = 0

        chests.map { chest ->
            launch {
                val success = fillChestFromCache(chest, tier, mode)
                synchronized(this@ConcurrentChestFiller) {
                    if (success) successCount++ else failCount++
                }
            }
        }.forEach { it.join() }

        logger.info(
            "Filled {} chests successfully, {} failed (tier={})",
            successCount, failCount, tier
        )
    }

    /**
     * Fills a single chest, preferring pre-generated loot from the cache.
     * Falls back to [ChestManager.fillChest] if the cache has no data.
     *
     * @param chest The chest to fill.
     * @param tier The loot tier.
     * @param mode The loot mode.
     * @return true if the chest was filled successfully.
     */
    suspend fun fillChestFromCache(chest: Chest, tier: String, mode: LootMode = LootMode.MODERN): Boolean {
        return try {
            val preGenerated = lootTableCache.getPreGeneratedChest(tier, mode)

            if (preGenerated != null) {
                withContext(bukkitDispatcher) {
                    applyPreGeneratedChest(chest, preGenerated)
                }
            } else {
                logger.debug("No cached loot for tier {}, falling back to direct fill", tier)
                withContext(bukkitDispatcher) {
                    chestManager.fillChest(chest.location, tier, mode)
                }
            }
        } catch (e: Exception) {
            logger.error("Error filling chest at {} with tier {}", chest.location, tier, e)
            false
        }
    }

    // ── Lifecycle ───────────────────────────────────────────────────────────

    @PreDestroy
    fun shutdown() {
        logger.info("ConcurrentChestFiller shutdown complete")
    }

    // ── Internal ────────────────────────────────────────────────────────────

    /**
     * Applies a [PreGeneratedChest][LootTableCache.PreGeneratedChest] to a
     * Bukkit chest inventory. Must be called on the main thread.
     */
    private fun applyPreGeneratedChest(
        chest: Chest,
        preGenerated: LootTableCache.PreGeneratedChest
    ): Boolean {
        val inventory = chest.inventory

        if (preGenerated.items.size != preGenerated.slots.size) {
            logger.error("Mismatch between items and slots in pre-generated chest")
            return false
        }

        inventory.clear()

        for (i in preGenerated.items.indices) {
            val slot = preGenerated.slots[i]
            if (slot in 0 until inventory.size) {
                inventory.setItem(slot, preGenerated.items[i].clone())
            }
        }

        return true
    }
}
