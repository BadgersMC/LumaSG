package net.lumalyte.lumasg.chest

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.badgersmc.nexus.annotations.PostConstruct
import net.badgersmc.nexus.annotations.Service
import net.badgersmc.nexus.paper.BukkitDispatcher
import org.bukkit.Location
import org.bukkit.block.Chest
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import net.lumalyte.lumasg.config.LumaSGConfig
import net.lumalyte.lumasg.domain.LootMode
import net.lumalyte.lumasg.hooks.NexoHook
import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ThreadLocalRandom

/**
 * Manages chest loot generation and distribution in Survival Games.
 *
 * Loads item definitions from `chest.yml`, where each item declares
 * per-tier weights. At runtime, items are selected via weighted random
 * and placed in random inventory slots.
 */
@Service
class ChestManager(
    private val plugin: JavaPlugin,
    private val bukkitDispatcher: BukkitDispatcher,
    private val nexoHook: NexoHook,
    private val config: LumaSGConfig
) {
    private val logger = LoggerFactory.getLogger(ChestManager::class.java)

    /** Thread-safe list of all loaded chest items (one entry per item-tier combo). */
    private val chestItems = CopyOnWriteArrayList<ChestItem>()

    /** Per-tier min/max item counts loaded from config. */
    private val tierSettings = mutableMapOf<String, TierSettings>()

    // ── Lifecycle ───────────────────────────────────────────────────────────

    @PostConstruct
    fun init() {
        ChestItem.nexoResolver = nexoHook::getNexoItem

        val chestFile = File(plugin.dataFolder, "chest.yml")
        if (!chestFile.exists()) {
            plugin.saveResource("chest.yml", false)
        }
        loadChestItems(chestFile)
    }

    // ── Loading ─────────────────────────────────────────────────────────────

    private fun loadChestItems(chestFile: File) {
        chestItems.clear()
        tierSettings.clear()

        try {
            val config = YamlConfiguration.loadConfiguration(chestFile)

            // Load items
            val itemsSection = config.getConfigurationSection("items")
            if (itemsSection == null) {
                logger.warn("No 'items' section found in chest.yml")
                return
            }

            for (itemKey in itemsSection.getKeys(false)) {
                val itemSection = itemsSection.getConfigurationSection(itemKey) ?: continue
                loadSingleItem(itemKey, itemSection)
            }

            // Load tier settings
            config.getConfigurationSection("tier-settings")?.let { ts ->
                for (tier in ts.getKeys(false)) {
                    val sec = ts.getConfigurationSection(tier) ?: continue
                    tierSettings[tier] = TierSettings(
                        minItems = sec.getInt("min-items", 3),
                        maxItems = sec.getInt("max-items", 6)
                    )
                }
            }

            logLoadingSummary()
        } catch (e: Exception) {
            logger.error("Failed to load chest configuration", e)
        }
    }

    private fun loadSingleItem(itemKey: String, itemSection: ConfigurationSection) {
        val tierWeights = itemSection.getConfigurationSection("tier-weights")
        if (tierWeights == null) {
            logger.warn("Item {} has no tier-weights configuration - skipping", itemKey)
            return
        }

        for (tier in tierWeights.getKeys(false)) {
            val weight = tierWeights.getDouble(tier, 0.0)
            if (weight <= 0) continue

            val enriched = createEnrichedSection(itemSection, tier, weight)
            val item = ChestItem.fromConfig(plugin, enriched, itemKey)
            if (item != null) {
                chestItems.add(item)
                logger.debug("Loaded item: {} for tier: {} with weight: {}", itemKey, tier, weight)
            } else {
                logger.warn("Failed to create item: {} for tier: {}", itemKey, tier)
            }
        }
    }

    /**
     * Builds an enriched YamlConfiguration that merges the item properties
     * with tier/chance overrides (stripping the tier-weights block).
     */
    private fun createEnrichedSection(
        itemSection: ConfigurationSection,
        tier: String,
        weight: Double
    ): YamlConfiguration {
        val enriched = YamlConfiguration()
        enriched.set("tier", tier)
        enriched.set("chance", weight)

        for (key in itemSection.getKeys(true)) {
            if (!key.startsWith("tier-weights")) {
                enriched.set(key, itemSection.get(key))
            }
        }
        return enriched
    }

    private fun logLoadingSummary() {
        val perTier = chestItems.groupingBy { it.tier }.eachCount()
        logger.info("Loaded {} chest item entries", chestItems.size)
        perTier.forEach { (tier, count) ->
            logger.info("  Tier {}: {} items", tier, count)
        }
    }

    // ── Filling ─────────────────────────────────────────────────────────────

    /**
     * Fills a chest at [location] with weighted-random items from [tier].
     * Must be called on the main thread (or inside a BukkitDispatcher context).
     */
    fun fillChest(location: Location, tier: String, mode: LootMode = LootMode.MODERN): Boolean {
        val block = location.block
        val state = block.state
        if (state !is Chest) {
            logger.warn("Block at {} is not a chest", location)
            return false
        }

        val inventory = state.inventory
        inventory.clear()

        val loot = getItemsForTier(tier, mode)
        if (loot.isEmpty()) {
            logger.warn("No items available for tier: {}", tier)
            return false
        }

        val settings = tierSettings[tier]
        val minItems = settings?.minItems ?: config.chest.minItems
        val maxItems = settings?.maxItems ?: config.chest.maxItems
        val itemCount = ThreadLocalRandom.current().nextInt(minItems, maxItems + 1)
        var filledSlots = 0
        var attempts = 0
        val maxAttempts = 50

        while (filledSlots < itemCount && attempts < maxAttempts) {
            attempts++

            val selected = weightedRandomItem(loot, mode) ?: continue
            val itemStack = selected.resolveItemStack() ?: continue

            // Randomise amount
            val amount = if (selected.maxAmount > selected.minAmount) {
                selected.minAmount + ThreadLocalRandom.current()
                    .nextInt(selected.maxAmount - selected.minAmount + 1)
            } else {
                selected.minAmount
            }
            itemStack.amount = amount

            // Place in a random empty slot
            val slot = getRandomEmptySlot(inventory)
            if (slot == -1) break

            inventory.setItem(slot, itemStack)
            filledSlots++
        }

        logger.debug("Filled chest at {} with {} items ({} attempts, tier={})", location, filledSlots, attempts, tier)
        return filledSlots > 0
    }

    /**
     * Fill multiple chests concurrently. Inventory access runs on the
     * main thread via [bukkitDispatcher].
     */
    suspend fun fillAll(chests: List<Chest>, tier: String, mode: LootMode = LootMode.MODERN) = coroutineScope {
        chests.map { chest ->
            launch {
                withContext(bukkitDispatcher) {
                    fillChest(chest.location, tier, mode)
                }
            }
        }
    }

    // ── Public queries ──────────────────────────────────────────────────────

    /**
     * Returns a single weighted-random ItemStack from [tier],
     * with a randomised amount, or null if no items exist for that tier.
     */
    fun getRandomItem(tier: String, mode: LootMode = LootMode.MODERN): ItemStack? {
        val items = getItemsForTier(tier, mode)
        if (items.isEmpty()) return null

        val selected = weightedRandomItem(items, mode) ?: return null
        val stack = selected.resolveItemStack() ?: return null

        stack.amount = if (selected.maxAmount > selected.minAmount) {
            selected.minAmount + ThreadLocalRandom.current()
                .nextInt(selected.maxAmount - selected.minAmount + 1)
        } else {
            selected.minAmount
        }
        return stack
    }

    /**
     * Returns [count] weighted-random ItemStacks from [tier].
     */
    fun getRandomItems(tier: String, count: Int, mode: LootMode = LootMode.MODERN): List<ItemStack> {
        val items = getItemsForTier(tier, mode)
        if (items.isEmpty()) {
            logger.warn("No items found for tier: {}", tier)
            return emptyList()
        }

        return (1..count).mapNotNull {
            val selected = weightedRandomItem(items, mode) ?: return@mapNotNull null
            val stack = selected.resolveItemStack() ?: return@mapNotNull null
            stack.amount = if (selected.maxAmount > selected.minAmount) {
                selected.minAmount + ThreadLocalRandom.current()
                    .nextInt(selected.maxAmount - selected.minAmount + 1)
            } else {
                selected.minAmount
            }
            stack
        }
    }

    /** Returns all tier names present in the loaded config. */
    fun getTiers(): Set<String> =
        chestItems.mapTo(mutableSetOf()) { it.tier }

    /** Returns all loaded ChestItems for a given tier, excluding items with zero weight for the given mode. */
    fun getItemsForTier(tier: String, mode: LootMode = LootMode.MODERN): List<ChestItem> =
        chestItems.filter { it.tier.equals(tier, ignoreCase = true) && (it.modeWeights[mode] ?: 1.0) > 0.0 }

    // ── Internals ───────────────────────────────────────────────────────────

    private fun weightedRandomItem(loot: List<ChestItem>, mode: LootMode = LootMode.MODERN): ChestItem? {
        if (loot.isEmpty()) return null

        val totalWeight = loot.sumOf { it.chance * (it.modeWeights[mode] ?: 1.0) }
        if (totalWeight <= 0) {
            return loot[ThreadLocalRandom.current().nextInt(loot.size)]
        }

        var roll = ThreadLocalRandom.current().nextDouble() * totalWeight
        for (item in loot) {
            roll -= item.chance * (item.modeWeights[mode] ?: 1.0)
            if (roll <= 0) return item
        }
        return loot.last()
    }

    private fun getRandomEmptySlot(inventory: Inventory): Int {
        val emptySlots = (0 until inventory.size).filter { inventory.getItem(it) == null }
        if (emptySlots.isEmpty()) return -1
        return emptySlots[ThreadLocalRandom.current().nextInt(emptySlots.size)]
    }

    private data class TierSettings(val minItems: Int, val maxItems: Int)
}
