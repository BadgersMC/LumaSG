package net.lumalyte.util;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.bukkit.Bukkit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalListener;

import net.lumalyte.LumaSG;
import net.lumalyte.arena.Arena;
import net.lumalyte.game.Game;
import net.lumalyte.game.GameState;

/**
 * High-performance game instance pooling system using Caffeine caching.
 * Optimized for handling 15-20 concurrent games with efficient resource management.
 */
public class GameInstancePool {
    
    private static final Cache<UUID, Game> ACTIVE_GAMES = Caffeine.newBuilder()
            .maximumSize(50)  // Support up to 50 concurrent games
            .expireAfterAccess(Duration.ofMinutes(30))
            .removalListener((RemovalListener<UUID, Game>) (key, value, cause) -> {
                if (value != null && !value.isShuttingDown()) {
                    // Cleanup game instance safely
                    cleanupGameInstance(value);
                }
            })
            .recordStats()
            .build();
    
    private static final Cache<String, Set<UUID>> ARENA_GAMES = Caffeine.newBuilder()
            .maximumSize(100)  // Support mapping for many arenas
            .expireAfterWrite(Duration.ofMinutes(45))
            .build();
    
    private static final ScheduledExecutorService CLEANUP_EXECUTOR = 
            Executors.newScheduledThreadPool(2, r -> {
                Thread t = new Thread(r, "GamePool-Cleanup");
                t.setDaemon(true);
                return t;
            });
    
    private static DebugLogger.ContextualLogger logger;
    private static LumaSG pluginInstance;
    
    /**
     * Initializes the game instance pool
     * 
     * @param plugin The plugin instance
     */
    public static void initialize(@NotNull LumaSG plugin) {
        pluginInstance = plugin;
        logger = plugin.getDebugLogger().forContext("GameInstancePool");
        
        // Schedule periodic cleanup of finished games
        CLEANUP_EXECUTOR.scheduleAtFixedRate(() -> {
            try {
                cleanupFinishedGames();
            } catch (Exception e) {
                if (logger != null) {
                    logger.error("Error during periodic game cleanup", e);
                }
            }
        }, 30, 30, TimeUnit.SECONDS);
        
        logger.info("GameInstancePool initialized with capacity for 50 concurrent games");
    }
    
    /**
     * Gets or creates a game instance for the specified arena
     * 
     * @param arena The arena to get/create a game for
     * @return CompletableFuture containing the game instance
     */
    public static CompletableFuture<Game> getOrCreateGame(@NotNull Arena arena) {
        return CompletableFuture.supplyAsync(() -> {
            // First, try to find an existing available game in this arena
            Set<UUID> arenaGameIds = ARENA_GAMES.getIfPresent(arena.getName());
            if (arenaGameIds != null) {
                for (UUID gameId : arenaGameIds) {
                    Game existingGame = ACTIVE_GAMES.getIfPresent(gameId);
                    if (existingGame != null && isGameAvailable(existingGame)) {
                        return existingGame;
                    }
                }
            }
            
            // Create new game instance
            try {
                Game newGame = new Game(pluginInstance, arena);
                registerGame(newGame);
                return newGame;
            } catch (Exception e) {
                if (logger != null) {
                    logger.error("Failed to create game instance for arena: " + arena.getName(), e);
                }
                return null;
            }
        });
    }
    
    /**
     * Registers a game instance in the pool
     * 
     * @param game The game to register
     */
    public static void registerGame(@NotNull Game game) {
        UUID gameId = game.getGameId();
        String arenaName = game.getArena().getName();
        
        // Add to active games cache
        ACTIVE_GAMES.put(gameId, game);
        
        // Add to arena mapping
        ARENA_GAMES.asMap().computeIfAbsent(arenaName, k -> ConcurrentHashMap.newKeySet()).add(gameId);
        
        if (logger != null) {
            logger.debug("Registered game " + gameId + " for arena " + arenaName);
        }
    }
    
    /**
     * Gets a game instance by ID
     * 
     * @param gameId The game ID
     * @return The game instance or null if not found
     */
    @Nullable
    public static Game getGame(@NotNull UUID gameId) {
        return ACTIVE_GAMES.getIfPresent(gameId);
    }
    
    /**
     * Gets all active games for an arena
     * 
     * @param arenaName The arena name
     * @return Set of game instances for the arena
     */
    @NotNull
    public static Set<Game> getGamesForArena(@NotNull String arenaName) {
        Set<UUID> gameIds = ARENA_GAMES.getIfPresent(arenaName);
        if (gameIds == null) {
            return Set.of();
        }
        
        return gameIds.stream()
                .map(ACTIVE_GAMES::getIfPresent)
                .filter(game -> game != null && !game.isShuttingDown())
                .collect(java.util.stream.Collectors.toSet());
    }
    
    /**
     * Removes a game from the pool
     * 
     * @param gameId The game ID to remove
     */
    public static void removeGame(@NotNull UUID gameId) {
        Game game = ACTIVE_GAMES.getIfPresent(gameId);
        if (game != null) {
            String arenaName = game.getArena().getName();
            
            // Remove from active games
            ACTIVE_GAMES.invalidate(gameId);
            
            // Remove from arena mapping
            Set<UUID> arenaGames = ARENA_GAMES.getIfPresent(arenaName);
            if (arenaGames != null) {
                arenaGames.remove(gameId);
                if (arenaGames.isEmpty()) {
                    ARENA_GAMES.invalidate(arenaName);
                }
            }
            
            if (logger != null) {
                logger.debug("Removed game " + gameId + " from pool");
            }
        }
    }
    
    /**
     * Processes batch game ticks for all active games
     * 
     * @return CompletableFuture that completes when all ticks are processed
     */
    public static CompletableFuture<Void> processBatchGameTicks() {
        return CompletableFuture.runAsync(() -> {
            ACTIVE_GAMES.asMap().values().parallelStream()
                    .filter(game -> !game.isShuttingDown())
                    .filter(game -> game.getState() == GameState.ACTIVE || 
                                   game.getState() == GameState.GRACE_PERIOD ||
                                   game.getState() == GameState.DEATHMATCH)
                    .forEach(game -> {
                        try {
                            // Process game-specific tick operations
                            processGameTick(game);
                        } catch (Exception e) {
                            if (logger != null) {
                                logger.error("Error processing tick for game " + game.getGameId(), e);
                            }
                        }
                    });
        });
    }
    
    /**
     * Processes a single game tick
     * 
     * @param game The game to process
     */
    private static void processGameTick(@NotNull Game game) {
        // This would be called by a central game tick scheduler
        // Individual game managers handle their own tick logic
        
        // For now, this is a placeholder for future batch processing optimizations
        // The actual game tick logic remains in the individual Game class
    }
    
    /**
     * Checks if a game is available for new players
     * 
     * @param game The game to check
     * @return true if the game can accept new players
     */
    private static boolean isGameAvailable(@NotNull Game game) {
        GameState state = game.getState();
        return (state == GameState.WAITING || state == GameState.COUNTDOWN) &&
               game.getPlayerCount() < game.getArena().getMaxPlayers() &&
               !game.isShuttingDown();
    }
    
    /**
     * Cleans up finished games from the pool
     */
    private static void cleanupFinishedGames() {
        ACTIVE_GAMES.asMap().entrySet().removeIf(entry -> {
            Game game = entry.getValue();
            if (game.getState() == GameState.FINISHED || game.isShuttingDown()) {
                cleanupGameInstance(game);
                return true;
            }
            return false;
        });
        
        // Clean up empty arena mappings
        ARENA_GAMES.asMap().entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }
    
    /**
     * Safely cleans up a game instance
     * 
     * @param game The game to cleanup
     */
    private static void cleanupGameInstance(@NotNull Game game) {
        try {
            if (!game.isShuttingDown()) {
                // Schedule cleanup on main thread if needed
                if (Bukkit.isPrimaryThread()) {
                    game.cleanup();
                } else {
                    Bukkit.getScheduler().runTask(pluginInstance, game::cleanup);
                }
            }
        } catch (Exception e) {
            if (logger != null) {
                logger.error("Error cleaning up game instance " + game.getGameId(), e);
            }
        }
    }
    
    /**
     * Gets cache statistics for monitoring
     * 
     * @return String containing cache statistics
     */
    public static String getCacheStats() {
        return String.format(
                "GamePool - Active Games: %d, Hit Rate: %.2f%%, Arena Mappings: %d",
                ACTIVE_GAMES.estimatedSize(),
                ACTIVE_GAMES.stats().hitRate() * 100,
                ARENA_GAMES.estimatedSize()
        );
    }
    
    /**
     * Gets the current number of active games
     * 
     * @return Number of active games
     */
    public static long getActiveGameCount() {
        return ACTIVE_GAMES.estimatedSize();
    }
    
    /**
     * Shuts down the game instance pool
     */
    public static void shutdown() {
        if (logger != null) {
            logger.info("Shutting down GameInstancePool...");
        }
        
        // Cleanup all active games
        ACTIVE_GAMES.asMap().values().forEach(GameInstancePool::cleanupGameInstance);
        
        // Clear caches
        ACTIVE_GAMES.invalidateAll();
        ARENA_GAMES.invalidateAll();
        
        // Shutdown executor
        CLEANUP_EXECUTOR.shutdown();
        try {
            if (!CLEANUP_EXECUTOR.awaitTermination(10, TimeUnit.SECONDS)) {
                CLEANUP_EXECUTOR.shutdownNow();
            }
        } catch (InterruptedException e) {
            CLEANUP_EXECUTOR.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        if (logger != null) {
            logger.info("GameInstancePool shutdown complete");
        }
    }
} 