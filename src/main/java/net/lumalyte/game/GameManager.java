package net.lumalyte.game;

import net.lumalyte.LumaSG;
import net.lumalyte.arena.Arena;
import net.lumalyte.exception.LumaSGException;
import net.lumalyte.util.ErrorHandlingUtils;
import net.lumalyte.util.ValidationUtils;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Manages all active games and game lifecycle operations.
 * 
 * <p>This class is responsible for creating, tracking, and managing all active
 * Survival Games instances. It provides methods to start new games, find existing
 * games, and handle game state transitions.</p>
 * 
 * <p>The GameManager maintains a registry of all active games and provides
 * utility methods for game-related operations such as finding games by player
 * or arena.</p>
 * 
 * @author LumaLyte
 * @version 1.0
 * @since 1.0
 */
public class GameManager {
    
    /** The plugin instance for logging and configuration access */
    private final @NotNull LumaSG plugin;
    
    /** Registry of all currently active games, mapped by game ID */
    private final @NotNull Map<String, Game> activeGames;
    
    /** List of all games that have been created (including finished ones) */
    private final @NotNull List<Game> allGames;
    
    /** Circuit breaker for game creation failures */
    private final @NotNull ErrorHandlingUtils.CircuitBreaker gameCreationCircuitBreaker;

    /**
     * Constructs a new GameManager instance.
     * 
     * @param plugin The plugin instance
     * @throws LumaSGException if plugin is null
     */
    public GameManager(@NotNull LumaSG plugin) {
        ValidationUtils.requireNonNull(plugin, "Plugin Instance", "GameManager Creation");
        
        this.plugin = plugin;
        this.activeGames = new ConcurrentHashMap<>();
        this.allGames = new CopyOnWriteArrayList<>();
        this.gameCreationCircuitBreaker = new ErrorHandlingUtils.CircuitBreaker(5, 60000L); // 5 failures, 1 minute reset
        
        plugin.getLogger().info("GameManager initialized successfully");
    }

    /**
     * Creates and starts a new game in the specified arena.
     * 
     * <p>This method creates a new Game instance, registers it with the manager,
     * and initiates the game startup process. The game will be automatically
     * tracked and managed by this GameManager.</p>
     * 
     * @param arena The arena where the game will be played
     * @return The newly created Game instance, or null if creation failed
     * 
     * @throws LumaSGException if arena is null
     */
    public @Nullable Game createGame(@NotNull Arena arena) {
        try {
            ValidationUtils.requireNonNull(arena, "Arena", "Game Creation");
            
            return gameCreationCircuitBreaker.execute(() -> {
                try {
                    return ErrorHandlingUtils.executeWithRetry(() -> {
                        try {
                            return createGameInternal(arena);
                        } catch (LumaSGException e) {
                            throw new RuntimeException("Game creation failed", e);
                        }
                    }, plugin.getLogger(), "Game Creation in Arena: " + arena.getName());
                } catch (Exception e) {
                    if (e.getCause() instanceof LumaSGException) {
                        throw new RuntimeException("Game creation failed", e.getCause());
                    }
                    throw new RuntimeException("Game creation failed", e);
                }
            }, plugin.getLogger(), "Game Creation Circuit Breaker");
            
        } catch (RuntimeException e) {
            // Handle our wrapped LumaSGException
            if (e.getCause() instanceof LumaSGException) {
                ErrorHandlingUtils.logError(plugin.getLogger(), "Game Creation", 
                    "Arena: " + arena.getName() + ", Cause: " + e.getCause().getMessage(), e.getCause());
            } else {
                ErrorHandlingUtils.logError(plugin.getLogger(), "Game Creation", 
                    "Arena: " + arena.getName(), e);
            }
            return null;
        } catch (Exception e) {
            ErrorHandlingUtils.logError(plugin.getLogger(), "Game Creation", 
                "Arena: " + arena.getName(), e);
            return null;
        }
    }
    
    /**
     * Internal method for creating a game with proper error handling.
     * 
     * @param arena The arena to create the game in
     * @return The created Game instance
     * @throws LumaSGException if game creation fails
     */
    private @NotNull Game createGameInternal(@NotNull Arena arena) throws LumaSGException {
        plugin.getLogger().info("Creating new game in arena: " + arena.getName());
        
        // Validate arena state
        validateArenaForGameCreation(arena);
        
        // Check if arena already has an active game
        if (hasActiveGames(arena)) {
            throw LumaSGException.gameError("Arena '" + arena.getName() + "' already has an active game");
        }
        
        // Create the game instance
        Game game;
        try {
            game = new Game(plugin, arena);
        } catch (Exception e) {
            throw LumaSGException.gameError("Failed to instantiate Game object for arena: " + arena.getName(), e);
        }
        
        // Validate game state after creation
        validateGameState(game);
        
        // Register the game
        String gameId = game.getGameId().toString();
        ValidationUtils.requireNonEmpty(gameId, "Game ID", "Game Registration");
        
        // Check for duplicate game IDs (should be extremely rare with UUIDs)
        if (activeGames.containsKey(gameId)) {
            throw LumaSGException.gameError("Duplicate game ID detected: " + gameId);
        }
        
        activeGames.put(gameId, game);
        allGames.add(game);
        
        plugin.getLogger().info("Successfully created game: " + gameId + " in arena: " + arena.getName());
        return game;
    }
    
    /**
     * Validates that an arena is suitable for game creation.
     * 
     * @param arena The arena to validate
     * @throws LumaSGException if the arena is invalid for game creation
     */
    private void validateArenaForGameCreation(@NotNull Arena arena) throws LumaSGException {
        // Check if arena has required components
        if (arena.getSpawnPoints().isEmpty()) {
            throw LumaSGException.arenaError("Arena '" + arena.getName() + "' has no spawn points configured");
        }
        
        if (arena.getCenter() == null) {
            throw LumaSGException.arenaError("Arena '" + arena.getName() + "' has no center location configured");
        }
        
        if (arena.getWorld() == null) {
            throw LumaSGException.arenaError("Arena '" + arena.getName() + "' world is not loaded");
        }
        
        // Check minimum requirements
        if (arena.getSpawnPoints().size() < arena.getMinPlayers()) {
            throw LumaSGException.arenaError("Arena '" + arena.getName() + "' has insufficient spawn points (" + 
                arena.getSpawnPoints().size() + ") for minimum players (" + arena.getMinPlayers() + ")");
        }
    }
    
    /**
     * Validates the state of a newly created game.
     * 
     * @param game The game to validate
     * @throws LumaSGException if the game state is invalid
     */
    private void validateGameState(@NotNull Game game) throws LumaSGException {
        if (game.getGameId() == null) {
            throw LumaSGException.gameError("Game has null ID");
        }
        
        if (game.getArena() == null) {
            throw LumaSGException.gameError("Game has null arena");
        }
        
        if (game.getState() == null) {
            throw LumaSGException.gameError("Game has null state");
        }
        
        // Additional state validation can be added here
    }

    /**
     * Gets a game by its unique identifier.
     * 
     * @param gameId The unique identifier of the game
     * @return The Game instance, or null if not found
     * @throws LumaSGException if gameId is null or empty
     */
    public @Nullable Game getGame(@NotNull String gameId) {
        ValidationUtils.requireNonEmpty(gameId, "Game ID", "Getting Game");
        
        return ErrorHandlingUtils.safeExecute(() -> {
            Game game = activeGames.get(gameId);
            if (game != null) {
                // Validate game state integrity
                validateGameIntegrity(game);
            }
            return game;
        }, null, plugin.getLogger(), "Get Game: " + gameId);
    }
    
    /**
     * Validates the integrity of a game's state.
     * 
     * @param game The game to validate
     * @throws RuntimeException if the game state is corrupted
     */
    private void validateGameIntegrity(@NotNull Game game) {
        if (game.getGameId() == null) {
            throw new RuntimeException("Game has corrupted state: null game ID");
        }
        
        if (game.getArena() == null) {
            throw new RuntimeException("Game has corrupted state: null arena");
        }
        
        if (game.getState() == null) {
            throw new RuntimeException("Game has corrupted state: null game state");
        }
    }

    /**
     * Gets all currently active games.
     * 
     * @return A list of all active games (defensive copy)
     */
    public @NotNull List<Game> getActiveGames() {
        List<Game> validGames = new ArrayList<>();
        List<String> corruptedGameIds = new ArrayList<>();
        
        for (Map.Entry<String, Game> entry : activeGames.entrySet()) {
            String gameId = entry.getKey();
            Game game = entry.getValue();
            
            if (game == null) {
                corruptedGameIds.add(gameId);
                continue;
            }
            
            try {
                validateGameIntegrity(game);
                validGames.add(game);
            } catch (Exception e) {
                ErrorHandlingUtils.logError(plugin.getLogger(), Level.WARNING, 
                    "Game Integrity Check", "Game ID: " + gameId, e);
                corruptedGameIds.add(gameId);
            }
        }
        
        // Remove corrupted games
        if (!corruptedGameIds.isEmpty()) {
            plugin.getLogger().warning("Removing " + corruptedGameIds.size() + " corrupted games: " + corruptedGameIds);
            for (String corruptedId : corruptedGameIds) {
                activeGames.remove(corruptedId);
            }
        }
        
        return validGames;
    }

    /**
     * Gets all games that have been created (including finished ones).
     * 
     * @return A list of all games (defensive copy)
     */
    public @NotNull List<Game> getAllGames() {
        return new ArrayList<>(allGames);
    }

    /**
     * Finds the game that a specific player is currently participating in.
     * 
     * <p>This method searches through all active games to find which one
     * contains the specified player.</p>
     * 
     * @param player The player to search for
     * @return The Game instance the player is in, or null if not found
     * @throws LumaSGException if player is null
     */
    public @Nullable Game getGameByPlayer(@NotNull Player player) {
        ValidationUtils.requireNonNull(player, "Player", "Finding Game by Player");
        
        return ErrorHandlingUtils.safeExecute(() -> {
            for (Game game : getActiveGames()) { // Use getActiveGames() for corruption detection
                if (game != null && game.getPlayers().contains(player.getUniqueId())) {
                    return game;
                }
            }
            return null;
        }, null, plugin.getLogger(), "Find Game by Player: " + player.getName());
    }

    /**
     * Finds all active games in a specific arena.
     * 
     * <p>This method returns all games that are currently running in the
     * specified arena. Typically, there should only be one active game per arena.</p>
     * 
     * @param arena The arena to search in
     * @return A list of active games in the arena
     * @throws LumaSGException if arena is null
     */
    public @NotNull List<Game> getGamesByArena(@NotNull Arena arena) {
        ValidationUtils.requireNonNull(arena, "Arena", "Finding Games by Arena");
        
        return ErrorHandlingUtils.safeExecute(() -> {
            List<Game> games = new ArrayList<>();
            for (Game game : getActiveGames()) { // Use getActiveGames() for corruption detection
                if (game != null && game.getArena().equals(arena)) {
                    games.add(game);
                }
            }
            return games;
        }, new ArrayList<>(), plugin.getLogger(), "Find Games by Arena: " + arena.getName());
    }

    /**
     * Gets the first active game in a specific arena.
     * 
     * <p>This method returns the first active game found in the specified arena,
     * or null if no games are active in that arena. Typically, there should only
     * be one active game per arena.</p>
     * 
     * @param arena The arena to search in
     * @return The first active game in the arena, or null if none found
     * @throws LumaSGException if arena is null
     */
    public @Nullable Game getGameByArena(@NotNull Arena arena) {
        ValidationUtils.requireNonNull(arena, "Arena", "Finding Game by Arena");
        
        List<Game> games = getGamesByArena(arena);
        return games.isEmpty() ? null : games.get(0);
    }

    /**
     * Removes a game from the active games registry.
     * 
     * <p>This method is typically called when a game ends or is cancelled.
     * The game will be removed from the active games list but will remain
     * in the allGames list for historical purposes.</p>
     * 
     * @param game The game to remove
     * @return true if the game was successfully removed, false otherwise
     * @throws LumaSGException if game is null
     */
    public boolean removeGame(@NotNull Game game) {
        ValidationUtils.requireNonNull(game, "Game", "Removing Game");
        
        return ErrorHandlingUtils.safeExecute(() -> {
            String gameId = game.getGameId().toString();
            ValidationUtils.requireNonEmpty(gameId, "Game ID", "Removing Game");
            
            Game removed = activeGames.remove(gameId);
            if (removed != null) {
                plugin.getLogger().info("Successfully removed game: " + gameId);
                
                // Perform cleanup on the removed game
                try {
                    if (removed != game) {
                        plugin.getLogger().warning("Removed game instance differs from requested game for ID: " + gameId);
                    }
                    
                    // Additional cleanup can be added here
                    return true;
                } catch (Exception e) {
                    ErrorHandlingUtils.logError(plugin.getLogger(), Level.WARNING, 
                        "Game Cleanup", "Game ID: " + gameId, e);
                    return true; // Still consider removal successful
                }
            } else {
                plugin.getLogger().warning("Attempted to remove non-existent game: " + gameId);
                return false;
            }
        }, false, plugin.getLogger(), "Remove Game: " + game.getGameId());
    }

    /**
     * Gets the total number of currently active games.
     * 
     * @return The number of active games
     */
    public int getActiveGameCount() {
        return activeGames.size();
    }

    /**
     * Gets the total number of games that have been created.
     * 
     * @return The total number of games (including finished ones)
     */
    public int getTotalGameCount() {
        return allGames.size();
    }

    /**
     * Checks if a player is currently participating in any active game.
     * 
     * @param player The player to check
     * @return true if the player is in an active game, false otherwise
     * @throws LumaSGException if player is null
     */
    public boolean isPlayerInGame(@NotNull Player player) {
        ValidationUtils.requireNonNull(player, "Player", "Checking Player Game Status");
        return getGameByPlayer(player) != null;
    }

    /**
     * Gets the number of active games in a specific arena.
     * 
     * @param arena The arena to check
     * @return The number of active games in the arena
     * @throws LumaSGException if arena is null
     */
    public int getActiveGameCountInArena(@NotNull Arena arena) {
        ValidationUtils.requireNonNull(arena, "Arena", "Getting Active Game Count");
        return getGamesByArena(arena).size();
    }

    /**
     * Checks if an arena currently has any active games.
     * 
     * @param arena The arena to check
     * @return true if the arena has active games, false otherwise
     * @throws LumaSGException if arena is null
     */
    public boolean hasActiveGames(@NotNull Arena arena) {
        ValidationUtils.requireNonNull(arena, "Arena", "Checking Active Games");
        return !getGamesByArena(arena).isEmpty();
    }

    /**
     * Shuts down all active games and cleans up resources.
     * 
     * <p>This method should be called when the plugin is being disabled
     * to ensure all games are properly terminated and resources are freed.</p>
     */
    public void shutdown() {
        plugin.getLogger().info("Shutting down GameManager...");
        
        // End all active games with null safety
        List<Game> gamesToEnd = new ArrayList<>(activeGames.values());
        for (Game game : gamesToEnd) {
            try {
                if (game != null) {
                    game.endGame(null);
                }
            } catch (Exception e) {
                ErrorHandlingUtils.logError(plugin.getLogger(), Level.WARNING, 
                    "Game Shutdown", "Game ID: " + (game != null ? game.getGameId() : "unknown"), e);
            }
        }
        
        // Clear all collections
        activeGames.clear();
        allGames.clear();
        
        plugin.getLogger().info("GameManager shutdown complete");
    }

    /**
     * Gets an existing game in the specified arena or creates a new one if none exists.
     * 
     * <p>This method first checks if there's an active game in the specified arena.
     * If one exists, it returns that game. Otherwise, it creates a new game
     * in the arena.</p>
     * 
     * @param arena The arena to get or create a game in
     * @return The existing or newly created Game instance, or null if creation failed
     * @throws LumaSGException if arena is null
     */
    public @Nullable Game getOrCreateGame(@NotNull Arena arena) {
        ValidationUtils.requireNonNull(arena, "Arena", "Get or Create Game");
        
        // Check if there's an existing game in this arena
        Game existingGame = getGameByArena(arena);
        if (existingGame != null) {
            return existingGame;
        }
        
        // No existing game, create a new one
        return createGame(arena);
    }

    /**
     * Gets all active games.
     * 
     * @return A collection of all active games
     */
    public @NotNull Collection<Game> getGames() {
        return Collections.unmodifiableCollection(activeGames.values());
    }
} 
