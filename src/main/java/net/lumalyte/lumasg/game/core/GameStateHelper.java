package net.lumalyte.lumasg.game.core;

import net.lumalyte.lumasg.LumaSG;
import net.lumalyte.lumasg.util.core.DebugLogger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;

/**
 * Helper class for managing game state transitions and state-related flags.
 * 
 * <p>This class centralizes all state management logic that was previously
 * scattered throughout the Game class. It handles state transitions,
 * validation, and state-related flags like PvP status and grace period.</p>
 * 
 * <p>This is a package-private helper class - it's an implementation detail
 * of the Game class and should not be used directly by external code.</p>
 */
class GameStateHelper {
    private final @NotNull LumaSG plugin;
    private final @NotNull DebugLogger.ContextualLogger logger;
    
    // Game state and related flags
    private @NotNull GameState currentState = GameState.WAITING;
    private boolean pvpEnabled = false;
    private boolean isGracePeriod = false;
    private @Nullable Instant gameStartTime;
    
    /**
     * Creates a new state helper for the specified game.
     * 
     * @param plugin The plugin instance
     * @param gameId The game ID for logging context
     */
    GameStateHelper(@NotNull LumaSG plugin, @NotNull String gameId) {
        this.plugin = plugin;
        this.logger = plugin.getDebugLogger().forContext("GameState-" + gameId);
    }
    
    /**
     * Gets the current game state.
     * 
     * @return The current game state
     */
    @NotNull GameState getCurrentState() {
        return currentState;
    }
    
    /**
     * Checks if the game can transition to the specified state.
     * 
     * @param newState The target state to transition to
     * @return true if the transition is valid, false otherwise
     */
    boolean canTransitionTo(@NotNull GameState newState) {
        // Validate state transitions
        switch (currentState) {
            case WAITING:
                return newState == GameState.COUNTDOWN;
                
            case COUNTDOWN:
                return newState == GameState.GRACE_PERIOD || newState == GameState.WAITING;
                
            case GRACE_PERIOD:
                return newState == GameState.ACTIVE;
                
            case ACTIVE:
                return newState == GameState.DEATHMATCH || newState == GameState.FINISHED;
                
            case DEATHMATCH:
                return newState == GameState.FINISHED;
                
            case FINISHED:
                return false; // Cannot transition from FINISHED
                
            default:
                return false;
        }
    }
    
    /**
     * Transitions the game to a new state.
     * 
     * @param newState The target state to transition to
     * @return true if the transition was successful, false otherwise
     */
    boolean transitionTo(@NotNull GameState newState) {
        if (!canTransitionTo(newState)) {
            logger.warn("Invalid state transition attempted: " + currentState + " -> " + newState);
            return false;
        }
        
        // Update state
        GameState oldState = currentState;
        currentState = newState;
        
        // Update related state flags
        updateStateFlags(newState);
        
        // Log state transition
        logger.debug("Game state transition: " + oldState + " -> " + newState);
        return true;
    }
    
    /**
     * Updates state flags based on the new state.
     * 
     * @param newState The new game state
     */
    private void updateStateFlags(@NotNull GameState newState) {
        switch (newState) {
            case GRACE_PERIOD:
                isGracePeriod = true;
                pvpEnabled = false;
                gameStartTime = Instant.now();
                break;
                
            case ACTIVE:
                isGracePeriod = false;
                pvpEnabled = true;
                break;
                
            case DEATHMATCH:
                isGracePeriod = false;
                pvpEnabled = true;
                break;
                
            case FINISHED:
                isGracePeriod = false;
                pvpEnabled = false;
                break;
                
            default:
                // No flag changes for other states
                break;
        }
    }
    
    /**
     * Checks if players can be added in the current state.
     * 
     * @return true if players can be added, false otherwise
     */
    boolean canAddPlayer() {
        return currentState == GameState.WAITING || currentState == GameState.COUNTDOWN;
    }
    
    /**
     * Checks if PvP is currently enabled.
     * 
     * @return true if PvP is enabled, false otherwise
     */
    boolean isPvpEnabled() {
        return pvpEnabled;
    }
    
    /**
     * Checks if the game is in grace period.
     * 
     * @return true if in grace period, false otherwise
     */
    boolean isGracePeriod() {
        return isGracePeriod;
    }
    
    /**
     * Enables PvP and ends grace period.
     */
    void enablePvP() {
        pvpEnabled = true;
        isGracePeriod = false;
        logger.debug("PvP enabled, grace period ended");
    }
    
    /**
     * Disables PvP.
     */
    void disablePvP() {
        pvpEnabled = false;
        logger.debug("PvP disabled");
    }
    
    /**
     * Gets the game start time.
     * 
     * @return The game start time, or null if the game hasn't started
     */
    @Nullable Instant getGameStartTime() {
        return gameStartTime;
    }
    
    /**
     * Gets the current game duration.
     * 
     * @return The duration since the game started, or Duration.ZERO if not started
     */
    @NotNull Duration getGameDuration() {
        if (gameStartTime == null) {
            return Duration.ZERO;
        }
        return Duration.between(gameStartTime, Instant.now());
    }
    
    /**
     * Resets the state helper to its initial state.
     * Called when the game is cleaned up.
     */
    void reset() {
        currentState = GameState.WAITING;
        pvpEnabled = false;
        isGracePeriod = false;
        gameStartTime = null;
        logger.debug("State helper reset to initial state");
    }
}