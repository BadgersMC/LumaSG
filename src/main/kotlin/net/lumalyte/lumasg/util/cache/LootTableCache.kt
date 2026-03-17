package net.lumalyte.lumasg.util.cache

import com.github.benmanes.caffeine.cache.Caffeine
import net.badgersmc.nexus.annotations.PostConstruct
import net.badgersmc.nexus.annotations.PreDestroy
import net.badgersmc.nexus.annotations.Service
import net.lumalyte.lumasg.chest.ChestItem
import net.lumalyte.lumasg.chest.ChestManager
import org.bukkit.inventory.ItemStack
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Pre-generated loot table caching system for optimal chest filling performance.
 * Eliminates runtime loot generation by pre-computing chest contents.
 */
@Service
class LootTableCache(
    private val chestManager: ChestManager
) {
    private val logger = LoggerFactory.getLogger(LootTableCache::class.java)

    /**
     * Represents a pre-generated chest configuration with items and their target slots.
     */
    data class PreGeneratedChest(
        val items: List<ItemStack>,
        val slots: List<Int>
    ) {
        /** Number of items in this pre-generated chest. */
        val itemCount: Int get() = items.size
    }

    /** Caffeine cache storing pre-generated chests per tier. */
    private val lootTableCache = Caffeine.newBuilder()
        .maximumSize(100)
        .expireAfterWrite(Duration.ofMinutes(30))
        .recordStats()
        .build<String, List<PreGeneratedChest>>()

    /** Round-robin counters for each tier. */
    private val generationCounters = ConcurrentHashMap<String, AtomicInteger>()

    /** Scheduled executor for periodic loot regeneration. */
    private lateinit var generationExecutor: ScheduledExecutorService

    // ── Configuration constants ─────────────────────────────────────────────

    companion object {
        private const val PREGENERATED_CHESTS_PER_TIER = 50
        private const val MIN_ITEMS_PER_CHEST = 3
        private const val MAX_ITEMS_PER_CHEST = 7
        private const val CHEST_SIZE = 27
    }

    // ── Lifecycle ───────────────────────────────────────────────────────────

    @PostConstruct
    fun init() {
        generationExecutor = Executors.newScheduledThreadPool(2) { runnable ->
            Thread(runnable, "LootGen-${System.currentTimeMillis()}").apply {
                isDaemon = true
            }
        }

        generationExecutor.scheduleAtFixedRate(
            {
                try {
                    regenerateLootTables()
                } catch (e: Exception) {
                    logger.error("Error during loot table regeneration", e)
                }
            },
            15, 15, TimeUnit.MINUTES
        )

        logger.info(
            "LootTableCache initialized - will pre-generate {} chests per tier",
            PREGENERATED_CHESTS_PER_TIER
        )
    }

    @PreDestroy
    fun destroy() {
        shutdown()
    }

    // ── Public API ──────────────────────────────────────────────────────────

    /**
     * Pre-generates loot tables for all tiers known to the [ChestManager].
     */
    fun preGenerateLootTables() {
        val availableTiers = chestManager.getTiers()

        for (tier in availableTiers) {
            try {
                generateLootTableForTier(tier)
            } catch (e: Exception) {
                logger.error("Error generating loot table for tier: {}", tier, e)
            }
        }

        logger.info("Pre-generated loot tables for {} tiers: {}", availableTiers.size, availableTiers)
    }

    /**
     * Gets a pre-generated chest for a specific tier using round-robin selection.
     * Generates on-demand if the cache is empty for the requested tier.
     *
     * @param tier The tier to retrieve a chest for.
     * @return A pre-generated chest, or null if generation failed.
     */
    fun getPreGeneratedChest(tier: String): PreGeneratedChest? {
        var chests = lootTableCache.getIfPresent(tier)
        if (chests.isNullOrEmpty()) {
            generateLootTableForTier(tier)
            chests = lootTableCache.getIfPresent(tier)
            if (chests.isNullOrEmpty()) return null
        }

        val counter = generationCounters.computeIfAbsent(tier) { AtomicInteger(0) }
        val index = counter.getAndIncrement() % chests.size
        return chests[index]
    }

    /**
     * Invalidates all cached loot tables and regenerates them.
     */
    fun forceRegeneration() {
        lootTableCache.invalidateAll()
        generationCounters.clear()
        preGenerateLootTables()
        logger.info("Forced regeneration of all loot tables completed")
    }

    /**
     * Shuts down the executor and clears all caches.
     */
    fun shutdown() {
        logger.info("Shutting down LootTableCache...")

        lootTableCache.invalidateAll()
        generationCounters.clear()

        if (::generationExecutor.isInitialized) {
            generationExecutor.shutdown()
            try {
                if (!generationExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    generationExecutor.shutdownNow()
                }
            } catch (_: InterruptedException) {
                generationExecutor.shutdownNow()
                Thread.currentThread().interrupt()
            }
        }

        logger.info("LootTableCache shutdown complete")
    }

    // ── Statistics ──────────────────────────────────────────────────────────

    /**
     * Returns a summary of cache statistics for monitoring.
     */
    fun getCacheStats(): String {
        val totalChests = lootTableCache.asMap().values.sumOf { it.size.toLong() }
        return "LootTableCache - Cached Tiers: ${lootTableCache.estimatedSize()}, " +
            "Total Pre-generated Chests: $totalChests, " +
            "Hit Rate: ${"%.2f".format(lootTableCache.stats().hitRate() * 100)}%"
    }

    /**
     * Returns detailed per-tier statistics.
     */
    fun getDetailedStats(): String = buildString {
        appendLine("Loot Table Statistics:")
        lootTableCache.asMap().forEach { (tier, chests) ->
            val usageCount = generationCounters[tier]?.get() ?: 0
            appendLine("  $tier: ${chests.size} chests, $usageCount used")
        }
    }

    // ── Internal generation ─────────────────────────────────────────────────

    /**
     * Periodic regeneration — only regenerates tiers whose cache has
     * fallen below half capacity.
     */
    private fun regenerateLootTables() {
        val availableTiers = chestManager.getTiers()

        for (tier in availableTiers) {
            val existing = lootTableCache.getIfPresent(tier)
            if (existing == null || existing.size < PREGENERATED_CHESTS_PER_TIER / 2) {
                generateLootTableForTier(tier)
            }
        }
    }

    private fun generateLootTableForTier(tier: String) {
        val tierItems = chestManager.getItemsForTier(tier)
        if (tierItems.isEmpty()) {
            logger.warn("No items found for tier: {}", tier)
            return
        }

        val preGenerated = (0 until PREGENERATED_CHESTS_PER_TIER).mapNotNull {
            generateSingleChest(tierItems)
        }

        if (preGenerated.isNotEmpty()) {
            lootTableCache.put(tier, preGenerated)
            generationCounters.computeIfAbsent(tier) { AtomicInteger(0) }.set(0)
            logger.debug("Generated {} chests for tier: {}", preGenerated.size, tier)
        }
    }

    private fun generateSingleChest(tierItems: List<ChestItem>): PreGeneratedChest? {
        val random = ThreadLocalRandom.current()
        val itemCount = random.nextInt(MIN_ITEMS_PER_CHEST, MAX_ITEMS_PER_CHEST + 1)
        val shuffledSlots = (0 until CHEST_SIZE).shuffled()

        val items = mutableListOf<ItemStack>()
        val slots = mutableListOf<Int>()

        for (i in 0 until minOf(itemCount, shuffledSlots.size)) {
            val selected = weightedRandomItem(tierItems) ?: continue
            val stack = selected.resolveItemStack() ?: continue

            stack.amount = if (selected.maxAmount > selected.minAmount) {
                selected.minAmount + random.nextInt(selected.maxAmount - selected.minAmount + 1)
            } else {
                selected.minAmount
            }

            items += stack.clone()
            slots += shuffledSlots[i]
        }

        return if (items.isEmpty()) null else PreGeneratedChest(items, slots)
    }

    private fun weightedRandomItem(tierItems: List<ChestItem>): ChestItem? {
        if (tierItems.isEmpty()) return null

        val totalWeight = tierItems.sumOf { it.chance }
        if (totalWeight <= 0) {
            return tierItems[ThreadLocalRandom.current().nextInt(tierItems.size)]
        }

        var roll = ThreadLocalRandom.current().nextDouble() * totalWeight
        for (item in tierItems) {
            roll -= item.chance
            if (roll <= 0) return item
        }
        return tierItems.last()
    }
}
