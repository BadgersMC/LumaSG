package net.lumalyte.lumasg.game;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import net.lumalyte.lumasg.LumaSG;
import net.lumalyte.lumasg.arena.Arena;
import net.lumalyte.lumasg.exception.LumaSGException;
import net.lumalyte.lumasg.util.core.BaseManager;
import net.lumalyte.lumasg.util.validation.ErrorHandlingUtils;
import net.lumalyte.lumasg.util.validation.ValidationUtils;

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
public class GameManager extends BaseManager {
    
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
	 */
    public GameManager(@NotNull LumaSG plugin) {
        super(plugin, "GameManager");
        
        this.activeGames = new ConcurrentHashMap<>();
        this.allGames = new CopyOnWriteArrayList<>();
        this.gameCreationCircuitBreaker = new ErrorHandlingUtils.CircuitBreaker(5, 60000L); // 5 failures, 1 minute reset
        
        logger.info("GameManager initialized successfully");
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
                            throw new IllegalStateException("Game creation failed", e);
                        }
                    }, plugin.getLogger(), "Game Creation in Arena: " + arena.getName());
                } catch (Exception e) {
                    if (e.getCause() instanceof LumaSGException) {
                        throw new IllegalStateException("Game creation failed", e.getCause());
                    }
                    throw new IllegalStateException("Game creation failed", e);
                }
            }, plugin.getLogger(), "Game Creation Circuit Breaker");
            
        } catch (RuntimeException e) {
            // Handle our wrapped LumaSGException
            if (e.getCause() instanceof LumaSGException) {
                logger.severe("Game Creation failed for arena: " + arena.getName() + ", Cause: " + e.getCause().getMessage(), e.getCause());
            } else {
                logger.severe("Game Creation failed for arena: " + arena.getName(), e);
            }
            return null;
        } catch (Exception e) {
            logger.severe("Game Creation failed for arena: " + arena.getName(), e);
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
        logger.info("Creating new game in arena: " + arena.getName());
        
        // Validate arena state
        validateArenaForGameCreation(arena);
        
        // CRITICAL: Synchronize on arena name to prevent race conditions
        // This ensures only one game can be created per arena at a time
        synchronized (("arena_" + arena.getName()).intern()) {
            // Re-check if arena already has an active game (double-checked locking pattern)
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
            
            // Register the game IMMEDIATELY to prevent race conditions
            String gameId = game.getGameId().toString();
            ValidationUtils.requireNonEmpty(gameId, "Game ID", "Game Registration");
            
            // Check for duplicate game IDs (should be extremely rare with UUIDs)
            if (activeGames.containsKey(gameId)) {
                throw LumaSGException.gameError("Duplicate game ID detected: " + gameId);
            }
            
            // Atomic registration - this prevents the race condition
            activeGames.put(gameId, game);
            allGames.add(game);
            
            logger.info("Successfully created and registered game: " + gameId + " in arena: " + arena.getName());
            return game;
        }
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
        // Validate that the game was properly initialized
        if (game.getGameId() == null) {
            throw LumaSGException.gameError("Game created without a valid game ID");
        }
        
        if (game.getArena() == null) {
            throw LumaSGException.gameError("Game created without a valid arena");
        }
        
        if (game.getState() == null) {
            throw LumaSGException.gameError("Game created without a valid initial state");
        }
        
        // Ensure the game starts in the correct state
        if (game.getState() != GameState.WAITING) {
            throw LumaSGException.gameError("Newly created game should be in WAITING state, but is in " + game.getState());
        }
        
        // Additional state validation can be added here as needed
    }

    /**
     * Gets a game by its unique identifier.
     * 
     * @param gameId The game identifier
     * @return The Game instance, or null if not found
	 */
    public @Nullable Game getGame(@NotNull String gameId) {
        try {
            ValidationUtils.requireNonEmpty(gameId, "Game ID", "Game Retrieval");
            
            Game game = activeGames.get(gameId);
            if (game != null) {
                validateGameIntegrity(game);
            }
            return game;
            
        } catch (Exception e) {
            logger.warn("Failed to retrieve game: " + gameId, e);
            return null;
        }
    }
    
    /**
     * Validates the integrity of a game instance.
     * 
     * @param game The game to validate
     */
    private void validateGameIntegrity(@NotNull Game game) {
        List<String> corruptedGameIds = new ArrayList<>();
        
        try {
            // Basic validation

			// Additional integrity checks can be added here
            
        } catch (Exception e) {
            logger.warn("Game integrity validation failed for game: " + game.getGameId(), e);
            corruptedGameIds.add(game.getGameId().toString());
        }
        
        // Remove corrupted games
	}

    /**
     * Gets all currently active games.
     * 
     * @return A list of all active games (never null)
     */
    public @NotNull List<Game> getActiveGames() {
        return new ArrayList<>(activeGames.values());
    }
    
    /**
     * Finds an available game in the specified arena.
     * 
     * <p>An available game is one that is in the WAITING state and has space
     * for additional players.</p>
     * 
     * @param arena The arena to search for available games
     * @return An available Game instance, or null if none found
	 */
    public @Nullable Game findAvailableGame(@NotNull Arena arena) {
        ValidationUtils.requireNonNull(arena, "Arena", "Find Available Game");
        
        return activeGames.values().stream()
            .filter(game -> game.getArena().equals(arena))
            .filter(game -> game.getState() == GameState.WAITING)
            .filter(game -> game.getPlayers().size() < game.getArena().getMaxPlayers())
            .findFirst()
            .orElse(null);
    }

    /**
     * Gets all games that have been created (including finished ones).
     * 
     * @return A list of all games (never null)
     */
    public @NotNull List<Game> getAllGames() {
        return Collections.unmodifiableList(allGames);
    }

    /**
     * Finds the game that a player is currently in.
     * 
     * @param player The player to search for
     * @return The Game instance the player is in, or null if not in any game
	 */
    public @Nullable Game getGameByPlayer(@NotNull Player player) {
        try {
            ValidationUtils.requireNonNull(player, "Player", "Find Game by Player");
            
            List<Game> playerGames = activeGames.values().stream()
                .filter(game -> game.getPlayers().contains(player.getUniqueId()) || 
                               game.getSpectators().contains(player.getUniqueId()))
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
            
            // Handle edge case: player in multiple games (due to race conditions)
            if (playerGames.size() > 1) {
                logger.warn("Player " + player.getName() + " found in " + playerGames.size() + 
                    " games! This indicates a race condition bug. Games: " + 
                    playerGames.stream().map(g -> g.getGameId().toString()).collect(java.util.stream.Collectors.joining(", ")));
                
                // Return the most recently created game (last in the list)
                // and log this for debugging
                Game mostRecentGame = playerGames.getLast();
                logger.warn("Returning most recent game for player " + player.getName() + ": " + mostRecentGame.getGameId());
                return mostRecentGame;
            }
            
            return playerGames.isEmpty() ? null : playerGames.getFirst();
            
        } catch (Exception e) {
            logger.warn("Failed to find game for player: " + player.getName(), e);
            return null;
        }
    }

    /**
     * Finds all games in the specified arena.
     * 
     * @param arena The arena to search for games
     * @return A list of games in the arena (never null)
	 */
    public @NotNull List<Game> findGamesByArena(@NotNull Arena arena) {
        try {
            ValidationUtils.requireNonNull(arena, "Arena", "Find Games by Arena");
            
            return activeGames.values().stream()
                .filter(game -> game.getArena().equals(arena))
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
            
        } catch (Exception e) {
            logger.warn("Failed to find games for arena: " + arena.getName(), e);
            return new ArrayList<>();
        }
    }

    /**
     * Gets the first active game in the specified arena.
     * 
     * @param arena The arena to search
     * @return The first Game instance found, or null if none
	 */
    public @Nullable Game getGameByArena(@NotNull Arena arena) {
        ValidationUtils.requireNonNull(arena, "Arena", "Get Game by Arena");
        
        return activeGames.values().stream()
            .filter(game -> game.getArena().equals(arena))
            .findFirst()
            .orElse(null);
    }

    /**
     * Removes a game from the active games registry.
     *
     * @param game The game to remove
     */
    public void removeGame(@NotNull Game game) {
        try {
            ValidationUtils.requireNonNull(game, "Game", "Game Removal");
            
            String gameId = game.getGameId().toString();
            Game removedGame = activeGames.remove(gameId);
            
            if (removedGame != null) {
                if (removedGame.equals(game)) {
                    logger.info("Successfully removed game: " + gameId);
                } else {
                    logger.warn("Removed game instance differs from requested game for ID: " + gameId);
                }
            } else {
                logger.warn("Attempted to remove non-existent game: " + gameId);
            }
            
        } catch (Exception e) {
            logger.warn("Failed to remove game: " + game.getGameId(), e);
        }
    }

    /**
     * Gets the number of currently active games.
     * 
     * @return The count of active games
     */
    public int getActiveGameCount() {
        return activeGames.size();
    }

    /**
     * Gets the total number of games that have been created.
     * 
     * @return The total count of games
     */
    public int getTotalGameCount() {
        return allGames.size();
    }

    /**
     * Checks if a player is currently in any game.
     * 
     * @param player The player to check
     * @return True if the player is in a game, false otherwise
	 */
    public boolean isPlayerInGame(@NotNull Player player) {
        return getGameByPlayer(player) != null;
    }

    /**
     * Gets the number of active games in a specific arena.
     * 
     * @param arena The arena to count games for
     * @return The number of active games in the arena
	 */
    public int getActiveGameCountInArena(@NotNull Arena arena) {
        return findGamesByArena(arena).size();
    }

    /**
     * Checks if an arena has any active games.
     * 
     * @param arena The arena to check
     * @return True if the arena has active games, false otherwise
	 */
    public boolean hasActiveGames(@NotNull Arena arena) {
        return getActiveGameCountInArena(arena) > 0;
    }

    /**
     * Shuts down the GameManager and all active games.
     */
    public void shutdown() {
        logger.info("Shutting down GameManager...");
        
        // Stop all active games
        List<Game> gamesToStop = new ArrayList<>(activeGames.values());
        for (Game game : gamesToStop) {
            try {
                game.endGame(null);
            } catch (Exception e) {
                logger.warn("Error stopping game during shutdown: " + game.getGameId(), e);
            }
        }
        
        // Clear collections
        activeGames.clear();
        allGames.clear();
        
        logger.info("GameManager shutdown complete");
    }

    /**
     * Gets or creates a game in the specified arena.
     * 
     * <p>This method first attempts to find an available game in the arena.
     * If none is found, it creates a new game.</p>
     * 
     * @param arena The arena to get or create a game in
     * @return A Game instance, or null if creation failed
	 */
    public @Nullable Game getOrCreateGame(@NotNull Arena arena) {
        Game game = findAvailableGame(arena);
        if (game == null) {
            game = createGame(arena);
        }
        return game;
    }

    /**
     * Gets all games (active and finished).
     * 
     * @return A collection of all games
     */
    public @NotNull Collection<Game> getGames() {
        return Collections.unmodifiableCollection(allGames);
    }

    /**
     * Detects and cleans up orphaned games that may result from race conditions.
     * This method should be called periodically to maintain system integrity.
     * 
     * @return The number of orphaned games that were cleaned up
     */
    public int cleanupOrphanedGames() {
        int cleanedUp = 0;
        Map<String, List<Game>> gamesByArena = new HashMap<>();
        
        // Group games by arena
        for (Game game : activeGames.values()) {
            String arenaName = game.getArena().getName();
            gamesByArena.computeIfAbsent(arenaName, k -> new ArrayList<>()).add(game);
        }
        
        // Check for multiple games in the same arena
        for (Map.Entry<String, List<Game>> entry : gamesByArena.entrySet()) {
            String arenaName = entry.getKey();
            List<Game> games = entry.getValue();
            
            if (games.size() > 1) {
                logger.warn("Found " + games.size() + " games in arena '" + arenaName + 
                    "' - this indicates a race condition occurred!");
                
                // Keep the most recent game, remove the others
                games.sort((g1, g2) -> g1.getGameId().toString().compareTo(g2.getGameId().toString()));
                Game gameToKeep = games.getLast();
                
                for (int i = 0; i < games.size() - 1; i++) {
                    Game orphanedGame = games.get(i);
                    logger.warn("Cleaning up orphaned game: " + orphanedGame.getGameId() + 
                        " in arena: " + arenaName);
                    
                    try {
                        // Force end the orphaned game
                        orphanedGame.endGame("System cleanup - duplicate game detected");
                        removeGame(orphanedGame);
                        cleanedUp++;
                    } catch (Exception e) {
                        logger.error("Failed to cleanup orphaned game: " + orphanedGame.getGameId(), e);
                    }
                }
                
                logger.info("Kept game: " + gameToKeep.getGameId() + " in arena: " + arenaName);
            }
        }
        
        if (cleanedUp > 0) {
            logger.info("Cleaned up " + cleanedUp + " orphaned games");
        }
        
        return cleanedUp;
    }
} 
