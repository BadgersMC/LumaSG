package net.lumalyte.util;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalListener;

import net.lumalyte.LumaSG;

/**
 * High-performance arena world caching system using Caffeine.
 * Reduces world loading/unloading overhead for multi-world arena setups.
 */
public class ArenaWorldCache {
    
    private static final Cache<String, World> WORLD_CACHE = Caffeine.newBuilder()
            .maximumSize(30)  // Support up to 30 concurrent worlds
            .expireAfterAccess(Duration.ofMinutes(10))
            .removalListener((RemovalListener<String, World>) (key, value, cause) -> {
                if (value != null && key != null) {
                    // Schedule world unloading on main thread
                    scheduleWorldUnload(key, value);
                }
            })
            .recordStats()
            .build();
    
    private static final Cache<String, CompletableFuture<World>> LOADING_CACHE = Caffeine.newBuilder()
            .maximumSize(50)
            .expireAfterWrite(Duration.ofMinutes(5))
            .build();
    
    private static final ConcurrentHashMap<String, AtomicInteger> WORLD_USAGE_COUNT = new ConcurrentHashMap<>();
    
    private static final ScheduledExecutorService CLEANUP_EXECUTOR = 
            Executors.newScheduledThreadPool(1, r -> {
                Thread t = new Thread(r, "WorldCache-Cleanup");
                t.setDaemon(true);
                return t;
            });
    
    private static DebugLogger.ContextualLogger logger;
    private static LumaSG pluginInstance;
    
    /**
     * Initializes the arena world cache
     * 
     * @param plugin The plugin instance
     */
    public static void initialize(@NotNull LumaSG plugin) {
        pluginInstance = plugin;
        logger = plugin.getDebugLogger().forContext("ArenaWorldCache");
        
        // Schedule periodic cleanup of unused worlds
        CLEANUP_EXECUTOR.scheduleAtFixedRate(() -> {
            try {
                cleanupUnusedWorlds();
            } catch (Exception e) {
                if (logger != null) {
                    logger.error("Error during world cache cleanup", e);
                }
            }
        }, 5, 5, TimeUnit.MINUTES);
        
        logger.info("ArenaWorldCache initialized with capacity for 30 concurrent worlds");
    }
    
    /**
     * Gets or loads a world asynchronously
     * 
     * @param worldName The name of the world to get/load
     * @return CompletableFuture containing the world instance
     */
    public static CompletableFuture<World> getOrLoadWorld(@NotNull String worldName) {
        // Check if world is already cached
        World cachedWorld = WORLD_CACHE.getIfPresent(worldName);
        if (cachedWorld != null) {
            incrementUsageCount(worldName);
            return CompletableFuture.completedFuture(cachedWorld);
        }
        
        // Check if world is currently being loaded
        CompletableFuture<World> loadingFuture = LOADING_CACHE.getIfPresent(worldName);
        if (loadingFuture != null) {
            return loadingFuture.thenApply(world -> {
                if (world != null) {
                    incrementUsageCount(worldName);
                }
                return world;
            });
        }
        
        // Start loading the world
        CompletableFuture<World> future = CompletableFuture.supplyAsync(() -> {
            try {
                return loadWorldSafely(worldName);
            } catch (Exception e) {
                if (logger != null) {
                    logger.error("Failed to load world: " + worldName, e);
                }
                return null;
            }
        });
        
        // Cache the loading future to prevent duplicate loads
        LOADING_CACHE.put(worldName, future);
        
        return future.thenApply(world -> {
            // Remove from loading cache once complete
            LOADING_CACHE.invalidate(worldName);
            
            if (world != null) {
                // Cache the loaded world
                WORLD_CACHE.put(worldName, world);
                incrementUsageCount(worldName);
                
                if (logger != null) {
                    logger.debug("Cached world: " + worldName);
                }
            }
            
            return world;
        });
    }
    
    /**
     * Safely loads a world on the main thread
     * 
     * @param worldName The name of the world to load
     * @return The loaded world or null if loading failed
     */
    @Nullable
    private static World loadWorldSafely(@NotNull String worldName) {
        // Check if world is already loaded by Bukkit
        World existingWorld = Bukkit.getWorld(worldName);
        if (existingWorld != null) {
            return existingWorld;
        }
        
        // Load world on main thread
        try {
            CompletableFuture<World> mainThreadLoad = new CompletableFuture<>();
            
            Bukkit.getScheduler().runTask(pluginInstance, () -> {
                try {
                    WorldCreator creator = new WorldCreator(worldName);
                    World world = creator.createWorld();
                    mainThreadLoad.complete(world);
                } catch (Exception e) {
                    mainThreadLoad.completeExceptionally(e);
                }
            });
            
            // Wait for world loading to complete (with timeout)
            return mainThreadLoad.get(30, TimeUnit.SECONDS);
            
        } catch (Exception e) {
            if (logger != null) {
                logger.error("Failed to load world " + worldName + " on main thread", e);
            }
            return null;
        }
    }
    
    /**
     * Gets a cached world without loading it
     * 
     * @param worldName The world name
     * @return The cached world or null if not cached
     */
    @Nullable
    public static World getCachedWorld(@NotNull String worldName) {
        World world = WORLD_CACHE.getIfPresent(worldName);
        if (world != null) {
            incrementUsageCount(worldName);
        }
        return world;
    }
    
    /**
     * Marks a world as no longer in use
     * 
     * @param worldName The world name
     */
    public static void releaseWorld(@NotNull String worldName) {
        AtomicInteger count = WORLD_USAGE_COUNT.get(worldName);
        if (count != null) {
            int newCount = count.decrementAndGet();
            if (newCount <= 0) {
                WORLD_USAGE_COUNT.remove(worldName);
                
                if (logger != null) {
                    logger.debug("Released world: " + worldName + " (usage count: 0)");
                }
            }
        }
    }
    
    /**
     * Forces a world to be cached (useful for preloading)
     * 
     * @param world The world to cache
     */
    public static void cacheWorld(@NotNull World world) {
        WORLD_CACHE.put(world.getName(), world);
        incrementUsageCount(world.getName());
        
        if (logger != null) {
            logger.debug("Force-cached world: " + world.getName());
        }
    }
    
    /**
     * Removes a world from the cache and unloads it
     * 
     * @param worldName The world name to remove
     * @return CompletableFuture that completes when the world is unloaded
     */
    public static CompletableFuture<Boolean> unloadWorld(@NotNull String worldName) {
        World world = WORLD_CACHE.getIfPresent(worldName);
        if (world == null) {
            return CompletableFuture.completedFuture(true);
        }
        
        // Remove from cache first
        WORLD_CACHE.invalidate(worldName);
        WORLD_USAGE_COUNT.remove(worldName);
        
        // Unload on main thread
        return CompletableFuture.supplyAsync(() -> {
            CompletableFuture<Boolean> mainThreadUnload = new CompletableFuture<>();
            
            Bukkit.getScheduler().runTask(pluginInstance, () -> {
                try {
                    boolean success = Bukkit.unloadWorld(world, true);
                    mainThreadUnload.complete(success);
                    
                    if (logger != null) {
                        if (success) {
                            logger.debug("Successfully unloaded world: " + worldName);
                        } else {
                            logger.warn("Failed to unload world: " + worldName);
                        }
                    }
                } catch (Exception e) {
                    if (logger != null) {
                        logger.error("Error unloading world: " + worldName, e);
                    }
                    mainThreadUnload.complete(false);
                }
            });
            
            try {
                return mainThreadUnload.get(15, TimeUnit.SECONDS);
            } catch (Exception e) {
                if (logger != null) {
                    logger.error("Timeout unloading world: " + worldName, e);
                }
                return false;
            }
        });
    }
    
    /**
     * Increments the usage count for a world
     * 
     * @param worldName The world name
     */
    private static void incrementUsageCount(@NotNull String worldName) {
        WORLD_USAGE_COUNT.computeIfAbsent(worldName, k -> new AtomicInteger(0)).incrementAndGet();
    }
    
    /**
     * Schedules world unloading on the main thread
     * 
     * @param worldName The world name
     * @param world The world instance
     */
    private static void scheduleWorldUnload(@NotNull String worldName, @NotNull World world) {
        // Check if world is still in use
        AtomicInteger usageCount = WORLD_USAGE_COUNT.get(worldName);
        if (usageCount != null && usageCount.get() > 0) {
            if (logger != null) {
                logger.debug("Skipping unload of world " + worldName + " - still in use (count: " + usageCount.get() + ")");
            }
            return;
        }
        
        // Schedule unload with a delay to allow for brief re-access
        CLEANUP_EXECUTOR.schedule(() -> {
            // Double-check usage count
            AtomicInteger finalUsageCount = WORLD_USAGE_COUNT.get(worldName);
            if (finalUsageCount == null || finalUsageCount.get() <= 0) {
                unloadWorld(worldName);
            }
        }, 30, TimeUnit.SECONDS);
    }
    
    /**
     * Cleans up unused worlds from the cache
     */
    private static void cleanupUnusedWorlds() {
        WORLD_CACHE.asMap().entrySet().removeIf(entry -> {
            String worldName = entry.getKey();
            AtomicInteger usageCount = WORLD_USAGE_COUNT.get(worldName);
            
            // Remove worlds with no usage
            if (usageCount == null || usageCount.get() <= 0) {
                scheduleWorldUnload(worldName, entry.getValue());
                return true;
            }
            
            return false;
        });
    }
    
    /**
     * Gets cache statistics for monitoring
     * 
     * @return String containing cache statistics
     */
    public static String getCacheStats() {
        return String.format(
                "WorldCache - Cached Worlds: %d, Hit Rate: %.2f%%, Active Usages: %d",
                WORLD_CACHE.estimatedSize(),
                WORLD_CACHE.stats().hitRate() * 100,
                WORLD_USAGE_COUNT.size()
        );
    }
    
    /**
     * Gets the current number of cached worlds
     * 
     * @return Number of cached worlds
     */
    public static long getCachedWorldCount() {
        return WORLD_CACHE.estimatedSize();
    }
    
    /**
     * Preloads worlds for better performance
     * 
     * @param worldNames Array of world names to preload
     * @return CompletableFuture that completes when all worlds are loaded
     */
    public static CompletableFuture<Void> preloadWorlds(@NotNull String... worldNames) {
        CompletableFuture<?>[] futures = new CompletableFuture[worldNames.length];
        
        for (int i = 0; i < worldNames.length; i++) {
            futures[i] = getOrLoadWorld(worldNames[i]);
        }
        
        return CompletableFuture.allOf(futures).thenRun(() -> {
            if (logger != null) {
                logger.info("Preloaded " + worldNames.length + " worlds");
            }
        });
    }
    
    /**
     * Shuts down the arena world cache
     */
    public static void shutdown() {
        if (logger != null) {
            logger.info("Shutting down ArenaWorldCache...");
        }
        
        // Unload all cached worlds
        WORLD_CACHE.asMap().keySet().forEach(worldName -> {
            try {
                unloadWorld(worldName).get(10, TimeUnit.SECONDS);
            } catch (Exception e) {
                if (logger != null) {
                    logger.error("Error unloading world during shutdown: " + worldName, e);
                }
            }
        });
        
        // Clear caches
        WORLD_CACHE.invalidateAll();
        LOADING_CACHE.invalidateAll();
        WORLD_USAGE_COUNT.clear();
        
        // Shutdown executor
        CLEANUP_EXECUTOR.shutdown();
        try {
            if (!CLEANUP_EXECUTOR.awaitTermination(5, TimeUnit.SECONDS)) {
                CLEANUP_EXECUTOR.shutdownNow();
            }
        } catch (InterruptedException e) {
            CLEANUP_EXECUTOR.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        if (logger != null) {
            logger.info("ArenaWorldCache shutdown complete");
        }
    }
} 