package net.lumalyte.util;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import net.lumalyte.LumaSG;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import xyz.xenondevs.invui.item.Item;
import xyz.xenondevs.invui.item.builder.ItemBuilder;
import xyz.xenondevs.invui.item.impl.SimpleItem;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * High-performance caching system for GUI components and frequently used items
 * Reduces object allocation and improves GUI loading performance
 */
public class GuiComponentCache {
    
    // Cache for frequently used GUI items (borders, buttons, etc.)
    private static final LoadingCache<String, Item> GUI_ITEM_CACHE = Caffeine.newBuilder()
            .maximumSize(500)
            .expireAfterAccess(Duration.ofMinutes(30))
            .recordStats()
            .build(GuiComponentCache::createGuiItem);
    
    // Cache for ItemStack instances to reduce object creation
    private static final Cache<String, ItemStack> ITEMSTACK_CACHE = Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterAccess(Duration.ofHours(1))
            .recordStats()
            .build();
    
    // Cache for leaderboard entries with automatic refresh
    private static final Cache<String, List<Item>> LEADERBOARD_CACHE = Caffeine.newBuilder()
            .maximumSize(50)
            .expireAfterWrite(Duration.ofMinutes(5))
            .recordStats()
            .build();
    
    // Cache for game browser entries
    private static final Cache<String, List<Item>> GAME_BROWSER_CACHE = Caffeine.newBuilder()
            .maximumSize(20)
            .expireAfterWrite(Duration.ofSeconds(30))
            .recordStats()
            .build();
    
    // Pre-built item suppliers for common items
    private static final ConcurrentHashMap<String, Supplier<Item>> ITEM_SUPPLIERS = new ConcurrentHashMap<>();
    
    // Note: Border materials will be implemented when needed
    // Currently using inline material selection for better performance
    
    private static LumaSG plugin;
    private static DebugLogger.ContextualLogger logger;
    
    /**
     * Initializes the GUI component cache
     * 
     * @param pluginInstance The plugin instance
     */
    public static void initialize(LumaSG pluginInstance) {
        plugin = pluginInstance;
        logger = plugin.getDebugLogger().forContext("GuiComponentCache");
        
        // Pre-register common item suppliers
        registerCommonItemSuppliers();
        
        logger.info("GUI Component Cache initialized with common items");
    }
    
    /**
     * Gets a cached GUI item by key, creating it if not present
     * 
     * @param itemKey The unique key for the item
     * @return The cached GUI item
     */
    public static Item getCachedGuiItem(String itemKey) {
        return GUI_ITEM_CACHE.get(itemKey);
    }
    
    /**
     * Gets a cached ItemStack by key, creating it if not present
     * 
     * @param itemKey The unique key for the ItemStack
     * @param supplier The supplier to create the ItemStack if not cached
     * @return The cached ItemStack
     */
    public static ItemStack getCachedItemStack(String itemKey, Supplier<ItemStack> supplier) {
        return ITEMSTACK_CACHE.get(itemKey, key -> supplier.get());
    }
    
    /**
     * Caches a leaderboard for quick access
     * 
     * @param statType The statistic type
     * @param leaderboardItems The leaderboard items to cache
     */
    public static void cacheLeaderboard(String statType, List<Item> leaderboardItems) {
        LEADERBOARD_CACHE.put("leaderboard:" + statType, leaderboardItems);
    }
    
    /**
     * Gets cached leaderboard items
     * 
     * @param statType The statistic type
     * @return The cached leaderboard items, or null if not cached
     */
    public static List<Item> getCachedLeaderboard(String statType) {
        return LEADERBOARD_CACHE.getIfPresent("leaderboard:" + statType);
    }
    
    /**
     * Caches game browser items
     * 
     * @param gameMode The game mode
     * @param browserItems The game browser items to cache
     */
    public static void cacheGameBrowser(String gameMode, List<Item> browserItems) {
        GAME_BROWSER_CACHE.put("browser:" + gameMode, browserItems);
    }
    
    /**
     * Gets cached game browser items
     * 
     * @param gameMode The game mode
     * @return The cached browser items, or null if not cached
     */
    public static List<Item> getCachedGameBrowser(String gameMode) {
        return GAME_BROWSER_CACHE.getIfPresent("browser:" + gameMode);
    }
    
    /**
     * Creates common border items with different styles
     * 
     * @param borderType The type of border (black, gray, light_gray)
     * @return The border item
     */
    public static Item createBorderItem(String borderType) {
        String key = "border:" + borderType;
        return getCachedGuiItem(key);
    }
    
    /**
     * Creates commonly used button items
     * 
     * @param buttonType The type of button
     * @param displayName The display name
     * @param loreLines The lore lines
     * @return The button item
     */
    public static Item createButtonItem(String buttonType, String displayName, String... loreLines) {
        String key = "button:" + buttonType + ":" + displayName.hashCode();
        
        // Check if we have a pre-registered supplier
        if (ITEM_SUPPLIERS.containsKey(key)) {
            return ITEM_SUPPLIERS.get(key).get();
        }
        
        return getCachedGuiItem(key);
    }
    
    /**
     * Creates navigation items (arrows, page indicators)
     * 
     * @param navType The navigation type (prev, next, page)
     * @param text The text to display
     * @return The navigation item
     */
    public static Item createNavigationItem(String navType, String text) {
        String key = "nav:" + navType + ":" + text.hashCode();
        return getCachedGuiItem(key);
    }
    
    /**
     * Creates player head items for leaderboards
     * 
     * @param playerName The player name
     * @param rank The player's rank
     * @param statValue The statistic value
     * @return The player head item
     */
    public static Item createPlayerHeadItem(String playerName, int rank, String statValue) {
        String key = "head:" + playerName + ":" + rank + ":" + statValue.hashCode();
        return getCachedGuiItem(key);
    }
    
    /**
     * Invalidates specific cache entries
     * 
     * @param cacheType The type of cache to invalidate
     * @param key The specific key to invalidate
     */
    public static void invalidateCache(String cacheType, String key) {
        switch (cacheType.toLowerCase()) {
            case "leaderboard" -> LEADERBOARD_CACHE.invalidate("leaderboard:" + key);
            case "browser" -> GAME_BROWSER_CACHE.invalidate("browser:" + key);
            case "item" -> GUI_ITEM_CACHE.invalidate(key);
            case "itemstack" -> ITEMSTACK_CACHE.invalidate(key);
            default -> logger.warn("Unknown cache type for invalidation: " + cacheType);
        }
    }
    
    /**
     * Invalidates all leaderboard caches
     */
    public static void invalidateAllLeaderboards() {
        LEADERBOARD_CACHE.asMap().keySet().removeIf(key -> key.startsWith("leaderboard:"));
    }
    
    /**
     * Invalidates all game browser caches
     */
    public static void invalidateAllGameBrowsers() {
        GAME_BROWSER_CACHE.asMap().keySet().removeIf(key -> key.startsWith("browser:"));
    }
    
    /**
     * Gets comprehensive cache statistics
     * 
     * @return String containing cache statistics
     */
    public static String getCacheStats() {
        return String.format(
            "GUI Component Cache Stats:\n" +
            "GUI Items - Size: %d, Hit Rate: %.2f%%, Load Count: %d\n" +
            "ItemStacks - Size: %d, Hit Rate: %.2f%%\n" +
            "Leaderboards - Size: %d, Hit Rate: %.2f%%\n" +
            "Game Browsers - Size: %d, Hit Rate: %.2f%%\n" +
            "Registered Suppliers: %d",
            GUI_ITEM_CACHE.estimatedSize(), GUI_ITEM_CACHE.stats().hitRate() * 100, GUI_ITEM_CACHE.stats().loadCount(),
            ITEMSTACK_CACHE.estimatedSize(), ITEMSTACK_CACHE.stats().hitRate() * 100,
            LEADERBOARD_CACHE.estimatedSize(), LEADERBOARD_CACHE.stats().hitRate() * 100,
            GAME_BROWSER_CACHE.estimatedSize(), GAME_BROWSER_CACHE.stats().hitRate() * 100,
            ITEM_SUPPLIERS.size()
        );
    }
    
    /**
     * Performs cache maintenance
     */
    public static void performMaintenance() {
        GUI_ITEM_CACHE.cleanUp();
        ITEMSTACK_CACHE.cleanUp();
        LEADERBOARD_CACHE.cleanUp();
        GAME_BROWSER_CACHE.cleanUp();
        
        logger.debug("GUI cache maintenance completed");
    }
    
    /**
     * Clears all caches
     */
    public static void clearAllCaches() {
        GUI_ITEM_CACHE.invalidateAll();
        ITEMSTACK_CACHE.invalidateAll();
        LEADERBOARD_CACHE.invalidateAll();
        GAME_BROWSER_CACHE.invalidateAll();
        
        logger.info("All GUI caches cleared");
    }
    
    // Private helper methods
    
    private static Item createGuiItem(String itemKey) {
        try {
            // Parse the item key to determine what to create
            String[] parts = itemKey.split(":", 3);
            String type = parts[0];
            
            return switch (type) {
                case "border" -> createBorderItemInternal(parts.length > 1 ? parts[1] : "black");
                case "button" -> createButtonItemInternal(parts.length > 1 ? parts[1] : "default");
                case "nav" -> createNavigationItemInternal(parts.length > 1 ? parts[1] : "default");
                case "head" -> createPlayerHeadItemInternal(parts.length > 1 ? parts[1] : "Unknown");
                default -> createDefaultItem();
            };
        } catch (Exception e) {
            logger.warn("Failed to create GUI item for key: " + itemKey, e);
            return createDefaultItem();
        }
    }
    
    private static Item createBorderItemInternal(String borderType) {
        Material material = switch (borderType) {
            case "gray" -> Material.GRAY_STAINED_GLASS_PANE;
            case "light_gray" -> Material.LIGHT_GRAY_STAINED_GLASS_PANE;
            default -> Material.BLACK_STAINED_GLASS_PANE;
        };
        
        return new SimpleItem(new ItemBuilder(material).setDisplayName(""));
    }
    
    private static Item createButtonItemInternal(String buttonType) {
        Material material = switch (buttonType) {
            case "back" -> Material.ARROW;
            case "refresh" -> Material.CLOCK;
            case "settings" -> Material.REDSTONE_TORCH;
            case "info" -> Material.BOOK;
            default -> Material.STONE_BUTTON;
        };
        
        String displayName = switch (buttonType) {
            case "back" -> "§c§lBack";
            case "refresh" -> "§a§lRefresh";
            case "settings" -> "§e§lSettings";
            case "info" -> "§b§lInfo";
            default -> "§f§lButton";
        };
        
        return new SimpleItem(new ItemBuilder(material).setDisplayName(displayName));
    }
    
    private static Item createNavigationItemInternal(String navType) {
        return switch (navType) {
            case "prev" -> new SimpleItem(new ItemBuilder(Material.ARROW).setDisplayName("§a§lPrevious Page"));
            case "next" -> new SimpleItem(new ItemBuilder(Material.ARROW).setDisplayName("§a§lNext Page"));
            case "page" -> new SimpleItem(new ItemBuilder(Material.PAPER).setDisplayName("§f§lPage"));
            default -> createDefaultItem();
        };
    }
    
    private static Item createPlayerHeadItemInternal(String playerName) {
        return new SimpleItem(new ItemBuilder(Material.PLAYER_HEAD).setDisplayName("§e§l" + playerName));
    }
    
    private static Item createDefaultItem() {
        return new SimpleItem(new ItemBuilder(Material.STONE).setDisplayName("§cError Item"));
    }
    
    private static void registerCommonItemSuppliers() {
        // Register commonly used items for ultra-fast access
        ITEM_SUPPLIERS.put("border:black", () -> createBorderItemInternal("black"));
        ITEM_SUPPLIERS.put("border:gray", () -> createBorderItemInternal("gray"));
        ITEM_SUPPLIERS.put("border:light_gray", () -> createBorderItemInternal("light_gray"));
        
        ITEM_SUPPLIERS.put("button:back", () -> createButtonItemInternal("back"));
        ITEM_SUPPLIERS.put("button:refresh", () -> createButtonItemInternal("refresh"));
        ITEM_SUPPLIERS.put("button:settings", () -> createButtonItemInternal("settings"));
        ITEM_SUPPLIERS.put("button:info", () -> createButtonItemInternal("info"));
        
        ITEM_SUPPLIERS.put("nav:prev", () -> createNavigationItemInternal("prev"));
        ITEM_SUPPLIERS.put("nav:next", () -> createNavigationItemInternal("next"));
        ITEM_SUPPLIERS.put("nav:page", () -> createNavigationItemInternal("page"));
        
        logger.debug("Registered " + ITEM_SUPPLIERS.size() + " common item suppliers");
    }
} 