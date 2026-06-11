package net.lumalyte.lumasg.util.cache

import com.github.benmanes.caffeine.cache.Caffeine
import net.badgersmc.nexus.annotations.PostConstruct
import net.badgersmc.nexus.annotations.PreDestroy
import net.badgersmc.nexus.annotations.Service
import net.lumalyte.lumasg.chest.ChestItem
import net.lumalyte.lumasg.chest.ChestManager
import net.lumalyte.lumasg.domain.LootMode
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

    /** Cache key: (tier, lootMode) */
    private data class CacheKey(val tier: String, val mode: LootMode)

    /** Caffeine cache storing pre-generated chests per (tier, mode) bucket. */
    private val chestBucketCache = Caffeine.newBuilder()
        .maximumSize(100)
        .expireAfterWrite(Duration.ofMinutes(30))
        .recordStats()
        .build<CacheKey, List<PreGeneratedChest>>()

    /** Round-robin counters for each (tier, mode) bucket. */
    private val generationCounters = ConcurrentHashMap<CacheKey, AtomicInteger>()

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
     * Pre-generates loot tables for all tiers × modes known to the [ChestManager].
     */
    fun preGenerateLootTables() {
        val availableTiers = chestManager.getTiers()
        val bucketCount = availableTiers.size * LootMode.entries.size

        for (tier in availableTiers) {
            for (mode in LootMode.entries) {
                try {
                    generateLootTableForTier(tier, mode)
                } catch (e: Exception) {
                    logger.error("Error generating loot table for tier: {}, mode: {}", tier, mode, e)
                }
            }
        }

        logger.info("Pre-generated loot tables for {} buckets ({} tiers × {} modes)",
            bucketCount, availableTiers.size, LootMode.entries.size)
    }

    /**
     * Gets a pre-generated chest for a specific tier and mode using round-robin selection.
     * Generates on-demand if the cache is empty for the requested (tier, mode) bucket.
     *
     * @param tier The tier to retrieve a chest for.
     * @param mode The loot mode to retrieve a chest for.
     * @return A pre-generated chest, or null if generation failed.
     */
    fun getPreGeneratedChest(tier: String, mode: LootMode = LootMode.MODERN): PreGeneratedChest? {
        val key = CacheKey(tier, mode)
        var chests = chestBucketCache.getIfPresent(key)
        if (chests.isNullOrEmpty()) {
            generateLootTableForTier(tier, mode)
            chests = chestBucketCache.getIfPresent(key)
            if (chests.isNullOrEmpty()) return null
        }

        val counter = generationCounters.computeIfAbsent(key) { AtomicInteger(0) }
        val index = counter.getAndIncrement() % chests.size
        return chests[index]
    }

    /**
     * Invalidates all cached loot tables and regenerates them.
     */
    fun forceRegeneration() {
        chestBucketCache.invalidateAll()
        generationCounters.clear()
        preGenerateLootTables()
        logger.info("Forced regeneration of all loot tables completed")
    }

    /**
     * Shuts down the executor and clears all caches.
     */
    fun shutdown() {
        logger.info("Shutting down LootTableCache...")

        chestBucketCache.invalidateAll()
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
        val totalChests = chestBucketCache.asMap().values.sumOf { it.size.toLong() }
        return "LootTableCache - Cached Buckets: ${chestBucketCache.estimatedSize()}, " +
            "Total Pre-generated Chests: $totalChests, " +
            "Hit Rate: ${"%.2f".format(chestBucketCache.stats().hitRate() * 100)}%"
    }

    /**
     * Returns detailed per-bucket statistics.
     */
    fun getDetailedStats(): String = buildString {
        appendLine("Loot Table Statistics:")
        chestBucketCache.asMap().forEach { (key, chests) ->
            val usageCount = generationCounters[key]?.get() ?: 0
            appendLine("  ${key.tier}/${key.mode}: ${chests.size} chests, $usageCount used")
        }
    }

    // ── Internal generation ─────────────────────────────────────────────────

    /**
     * Periodic regeneration — only regenerates buckets whose cache has
     * fallen below half capacity.
     */
    private fun regenerateLootTables() {
        val availableTiers = chestManager.getTiers()

        for (tier in availableTiers) {
            for (mode in LootMode.entries) {
                val key = CacheKey(tier, mode)
                val existing = chestBucketCache.getIfPresent(key)
                if (existing == null || existing.size < PREGENERATED_CHESTS_PER_TIER / 2) {
                    generateLootTableForTier(tier, mode)
                }
            }
        }
    }

    private fun generateLootTableForTier(tier: String, mode: LootMode = LootMode.MODERN) {
        val tierItems = chestManager.getItemsForTier(tier, mode)
        if (tierItems.isEmpty()) {
            logger.warn("No items found for tier: {}, mode: {}", tier, mode)
            return
        }

        val preGenerated = (0 until PREGENERATED_CHESTS_PER_TIER).mapNotNull {
            generateSingleChest(tierItems, mode)
        }

        if (preGenerated.isNotEmpty()) {
            val key = CacheKey(tier, mode)
            chestBucketCache.put(key, preGenerated)
            generationCounters.computeIfAbsent(key) { AtomicInteger(0) }.set(0)
            logger.debug("Generated {} chests for tier: {}, mode: {}", preGenerated.size, tier, mode)
        }
    }

    private fun generateSingleChest(tierItems: List<ChestItem>, mode: LootMode): PreGeneratedChest? {
        val random = ThreadLocalRandom.current()
        val itemCount = random.nextInt(MIN_ITEMS_PER_CHEST, MAX_ITEMS_PER_CHEST + 1)
        val shuffledSlots = (0 until CHEST_SIZE).shuffled()

        val items = mutableListOf<ItemStack>()
        val slots = mutableListOf<Int>()

        for (i in 0 until minOf(itemCount, shuffledSlots.size)) {
            val selected = weightedRandomItem(tierItems, mode) ?: continue
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

    private fun weightedRandomItem(tierItems: List<ChestItem>, mode: LootMode): ChestItem? {
        if (tierItems.isEmpty()) return null

        val totalWeight = tierItems.sumOf { it.chance * (it.modeWeights[mode] ?: 1.0) }
        if (totalWeight <= 0) {
            return tierItems[ThreadLocalRandom.current().nextInt(tierItems.size)]
        }

        var roll = ThreadLocalRandom.current().nextDouble() * totalWeight
        for (item in tierItems) {
            roll -= item.chance * (item.modeWeights[mode] ?: 1.0)
            if (roll <= 0) return item
        }
        return tierItems.last()
    }
}
