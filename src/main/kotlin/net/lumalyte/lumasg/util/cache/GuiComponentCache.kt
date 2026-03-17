package net.lumalyte.lumasg.util.cache

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.LoadingCache
import net.badgersmc.nexus.annotations.PostConstruct
import net.badgersmc.nexus.annotations.Service
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.slf4j.LoggerFactory
import xyz.xenondevs.invui.item.Item
import xyz.xenondevs.invui.item.builder.ItemBuilder
import xyz.xenondevs.invui.item.impl.SimpleItem
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

/**
 * High-performance caching system for GUI components and frequently used items.
 * Reduces object allocation and improves GUI loading performance.
 */
@Service
class GuiComponentCache {

    private val logger = LoggerFactory.getLogger(GuiComponentCache::class.java)

    /** Cache for frequently used GUI items (borders, buttons, navigation). */
    private val guiItemCache: LoadingCache<String, Item> = Caffeine.newBuilder()
        .maximumSize(500)
        .expireAfterAccess(Duration.ofMinutes(30))
        .recordStats()
        .build { key -> createGuiItem(key) }

    /** Cache for ItemStack instances to reduce object creation. */
    private val itemStackCache: Cache<String, ItemStack> = Caffeine.newBuilder()
        .maximumSize(1000)
        .expireAfterAccess(Duration.ofHours(1))
        .recordStats()
        .build()

    /** Cache for leaderboard entries with automatic refresh. */
    private val leaderboardCache: Cache<String, List<Item>> = Caffeine.newBuilder()
        .maximumSize(50)
        .expireAfterWrite(Duration.ofMinutes(5))
        .recordStats()
        .build()

    /** Cache for game browser entries. */
    private val gameBrowserCache: Cache<String, List<Item>> = Caffeine.newBuilder()
        .maximumSize(20)
        .expireAfterWrite(Duration.ofSeconds(30))
        .recordStats()
        .build()

    /** Pre-registered item suppliers for common items. */
    private val itemSuppliers = ConcurrentHashMap<String, () -> Item>()

    // ── Lifecycle ───────────────────────────────────────────────────────────

    @PostConstruct
    fun init() {
        registerCommonItemSuppliers()
        logger.info("GUI Component Cache initialized with {} common items", itemSuppliers.size)
    }

    // ── Public API ──────────────────────────────────────────────────────────

    /** Gets a cached GUI item by key, creating it if not present. */
    fun getCachedGuiItem(key: String): Item =
        guiItemCache.get(key)!!

    /** Gets a cached ItemStack by key, creating it via [supplier] if not present. */
    fun getCachedItemStack(key: String, supplier: () -> ItemStack): ItemStack =
        itemStackCache.get(key) { supplier() }!!

    /** Caches leaderboard items for a given stat type. */
    fun cacheLeaderboard(statType: String, leaderboardItems: List<Item>) {
        leaderboardCache.put("leaderboard:$statType", leaderboardItems)
    }

    /** Gets cached leaderboard items, or null if not cached. */
    fun getCachedLeaderboard(statType: String): List<Item>? =
        leaderboardCache.getIfPresent("leaderboard:$statType")

    /** Caches game browser items for a given game mode. */
    fun cacheGameBrowser(gameMode: String, browserItems: List<Item>) {
        gameBrowserCache.put("browser:$gameMode", browserItems)
    }

    /** Gets cached game browser items, or null if not cached. */
    fun getCachedGameBrowser(gameMode: String): List<Item>? =
        gameBrowserCache.getIfPresent("browser:$gameMode")

    /** Creates a border item (black, gray, or light_gray stained glass pane). */
    fun createBorderItem(borderType: String): Item =
        getCachedGuiItem("border:$borderType")

    /** Creates a button item with the given type, display name, and optional lore lines. */
    fun createButtonItem(buttonType: String, displayName: String, vararg loreLines: String): Item {
        val key = "button:$buttonType:${displayName.hashCode()}"

        // Check pre-registered supplier first for ultra-fast access
        itemSuppliers[key]?.let { return it() }

        return getCachedGuiItem(key)
    }

    /** Creates a navigation item (prev, next, page). */
    fun createNavigationItem(navType: String, text: String): Item {
        val key = "nav:$navType:${text.hashCode()}"
        return getCachedGuiItem(key)
    }

    /** Creates a player head item for leaderboards. */
    fun createPlayerHeadItem(playerName: String, rank: Int, statValue: String): Item {
        val key = "head:$playerName:$rank:${statValue.hashCode()}"
        return getCachedGuiItem(key)
    }

    // ── Invalidation ────────────────────────────────────────────────────────

    /** Invalidates a specific cache entry by type and key. */
    fun invalidateCache(cacheType: String, key: String) {
        when (cacheType.lowercase()) {
            "leaderboard" -> leaderboardCache.invalidate("leaderboard:$key")
            "browser" -> gameBrowserCache.invalidate("browser:$key")
            "item" -> guiItemCache.invalidate(key)
            "itemstack" -> itemStackCache.invalidate(key)
            else -> logger.warn("Unknown cache type for invalidation: {}", cacheType)
        }
    }

    /** Invalidates all leaderboard caches. */
    fun invalidateAllLeaderboards() {
        leaderboardCache.asMap().keys.removeIf { it.startsWith("leaderboard:") }
    }

    /** Invalidates all game browser caches. */
    fun invalidateAllGameBrowsers() {
        gameBrowserCache.asMap().keys.removeIf { it.startsWith("browser:") }
    }

    /** Clears all caches. */
    fun clearAllCaches() {
        guiItemCache.invalidateAll()
        itemStackCache.invalidateAll()
        leaderboardCache.invalidateAll()
        gameBrowserCache.invalidateAll()
        logger.info("All GUI caches cleared")
    }

    /** Performs cache maintenance (evicts expired entries). */
    fun performMaintenance() {
        guiItemCache.cleanUp()
        itemStackCache.cleanUp()
        leaderboardCache.cleanUp()
        gameBrowserCache.cleanUp()
        logger.debug("GUI cache maintenance completed")
    }

    /** Returns comprehensive cache statistics. */
    fun getCacheStats(): String {
        val guiStats = guiItemCache.stats()
        val itemStackStats = itemStackCache.stats()
        val lbStats = leaderboardCache.stats()
        val gbStats = gameBrowserCache.stats()

        return """
            GUI Component Cache Stats:
            GUI Items - Size: ${guiItemCache.estimatedSize()}, Hit Rate: ${"%.2f".format(guiStats.hitRate() * 100)}%, Load Count: ${guiStats.loadCount()}
            ItemStacks - Size: ${itemStackCache.estimatedSize()}, Hit Rate: ${"%.2f".format(itemStackStats.hitRate() * 100)}%
            Leaderboards - Size: ${leaderboardCache.estimatedSize()}, Hit Rate: ${"%.2f".format(lbStats.hitRate() * 100)}%
            Game Browsers - Size: ${gameBrowserCache.estimatedSize()}, Hit Rate: ${"%.2f".format(gbStats.hitRate() * 100)}%
            Registered Suppliers: ${itemSuppliers.size}
        """.trimIndent()
    }

    // ── Internal creation logic ─────────────────────────────────────────────

    private fun createGuiItem(itemKey: String): Item {
        return try {
            val parts = itemKey.split(":", limit = 3)
            when (parts[0]) {
                "border" -> createBorderItemInternal(parts.getOrElse(1) { "black" })
                "button" -> createButtonItemInternal(parts.getOrElse(1) { "default" })
                "nav" -> createNavigationItemInternal(parts.getOrElse(1) { "default" })
                "head" -> createPlayerHeadItemInternal(parts.getOrElse(1) { "Unknown" })
                else -> createDefaultItem()
            }
        } catch (e: Exception) {
            logger.warn("Failed to create GUI item for key: {}", itemKey, e)
            createDefaultItem()
        }
    }

    private fun createBorderItemInternal(borderType: String): Item {
        val material = when (borderType) {
            "gray" -> Material.GRAY_STAINED_GLASS_PANE
            "light_gray" -> Material.LIGHT_GRAY_STAINED_GLASS_PANE
            else -> Material.BLACK_STAINED_GLASS_PANE
        }
        return SimpleItem(ItemBuilder(material).setDisplayName(""))
    }

    private fun createButtonItemInternal(buttonType: String): Item {
        val material = when (buttonType) {
            "back" -> Material.ARROW
            "refresh" -> Material.CLOCK
            "settings" -> Material.REDSTONE_TORCH
            "info" -> Material.BOOK
            else -> Material.STONE_BUTTON
        }
        val displayName = when (buttonType) {
            "back" -> "\u00a7c\u00a7lBack"
            "refresh" -> "\u00a7a\u00a7lRefresh"
            "settings" -> "\u00a7e\u00a7lSettings"
            "info" -> "\u00a7b\u00a7lInfo"
            else -> "\u00a7f\u00a7lButton"
        }
        return SimpleItem(ItemBuilder(material).setDisplayName(displayName))
    }

    private fun createNavigationItemInternal(navType: String): Item = when (navType) {
        "prev" -> SimpleItem(ItemBuilder(Material.ARROW).setDisplayName("\u00a7a\u00a7lPrevious Page"))
        "next" -> SimpleItem(ItemBuilder(Material.ARROW).setDisplayName("\u00a7a\u00a7lNext Page"))
        "page" -> SimpleItem(ItemBuilder(Material.PAPER).setDisplayName("\u00a7f\u00a7lPage"))
        else -> createDefaultItem()
    }

    private fun createPlayerHeadItemInternal(playerName: String): Item =
        SimpleItem(ItemBuilder(Material.PLAYER_HEAD).setDisplayName("\u00a7e\u00a7l$playerName"))

    private fun createDefaultItem(): Item =
        SimpleItem(ItemBuilder(Material.STONE).setDisplayName("\u00a7cError Item"))

    private fun registerCommonItemSuppliers() {
        // Borders
        itemSuppliers["border:black"] = { createBorderItemInternal("black") }
        itemSuppliers["border:gray"] = { createBorderItemInternal("gray") }
        itemSuppliers["border:light_gray"] = { createBorderItemInternal("light_gray") }

        // Buttons
        itemSuppliers["button:back"] = { createButtonItemInternal("back") }
        itemSuppliers["button:refresh"] = { createButtonItemInternal("refresh") }
        itemSuppliers["button:settings"] = { createButtonItemInternal("settings") }
        itemSuppliers["button:info"] = { createButtonItemInternal("info") }

        // Navigation
        itemSuppliers["nav:prev"] = { createNavigationItemInternal("prev") }
        itemSuppliers["nav:next"] = { createNavigationItemInternal("next") }
        itemSuppliers["nav:page"] = { createNavigationItemInternal("page") }

        logger.debug("Registered {} common item suppliers", itemSuppliers.size)
    }
}
