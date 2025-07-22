package net.lumalyte.lumasg.util.cache;  import net.lumalyte.lumasg.util.core.DebugLogger;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

import org.bukkit.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import net.lumalyte.lumasg.LumaSG;
import net.lumalyte.lumasg.arena.Arena;

/**
 * Caches arena world instances for efficient access.
 * Provides thread-safe caching of world objects to avoid repeated lookups.
 */
public class ArenaWorldCache {
    private static LumaSG plugin;
    private static DebugLogger.ContextualLogger logger;
    private static final ConcurrentMap<String, World> worldCache = new ConcurrentHashMap<>();
    private static final AtomicLong cacheHits = new AtomicLong(0);
    private static final AtomicLong cacheMisses = new AtomicLong(0);
    private static boolean initialized = false;

    /**
     * Initializes the arena world cache.
     */
    public static void initialize(@NotNull LumaSG pluginInstance) {
        if (initialized) {
            return;
        }
        
        plugin = pluginInstance;
        logger = plugin.getDebugLogger().forContext("ArenaWorldCache");
        initialized = true;
        logger.info("Arena world cache initialized");
    }

    /**
     * Gets a world from cache or loads it if not cached.
     */
    public static @Nullable World getWorld(@NotNull String worldName) {
        World cached = worldCache.get(worldName);
        if (cached != null) {
            cacheHits.incrementAndGet();
            return cached;
        }
        
        cacheMisses.incrementAndGet();
        World world = loadWorld(worldName);
        if (world != null) {
            worldCache.put(worldName, world);
        }
        return world;
    }

    /**
     * Gets the world for an arena.
     */
    public static @Nullable World getArenaWorld(@NotNull Arena arena) {
        World world = arena.getWorld();
        if (world == null) {
            logger.warn("Arena " + arena.getName() + " has no world available");
            return null;
        }
        
        // Cache the world by name for future lookups
        String worldName = world.getName();
        worldCache.putIfAbsent(worldName, world);
        cacheHits.incrementAndGet();
        
        return world;
    }

    /**
     * Loads a world from Bukkit.
     */
    private static @Nullable World loadWorld(@NotNull String worldName) {
        try {
            World world = plugin.getServer().getWorld(worldName);
            if (world == null) {
                logger.warn("World not found: " + worldName);
            } else {
                logger.debug("Loaded world: " + worldName);
            }
            return world;
        } catch (Exception e) {
            logger.warn("Failed to load world: " + worldName, e);
            return null;
        }
    }

    /**
     * Clears the world cache.
     */
    public static void clearCache() {
        worldCache.clear();
        logger.debug("World cache cleared");
    }

    /**
     * Removes a specific world from cache.
     */
    public static void removeFromCache(@NotNull String worldName) {
        worldCache.remove(worldName);
        logger.debug("Removed world from cache: " + worldName);
    }

    /**
     * Gets the current cache size.
     */
    public static int getCacheSize() {
        return worldCache.size();
    }

    /**
     * Gets cache statistics.
     */
    public static String getCacheStats() {
        long hits = cacheHits.get();
        long misses = cacheMisses.get();
        long total = hits + misses;
        double hitRate = total > 0 ? (double) hits / total * 100 : 0;
        
        return String.format("ArenaWorldCache - Size: %d, Hits: %d, Misses: %d, Hit Rate: %.2f%%",
            worldCache.size(), hits, misses, hitRate);
    }

    /**
     * Shuts down the arena world cache.
     */
    public static void shutdown() {
        if (!initialized) {
            return;
        }
        
        clearCache();
        initialized = false;
        logger.info("Arena world cache shut down");
    }
} 
