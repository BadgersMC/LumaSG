package net.lumalyte.lumasg.exception;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Base exception class for all LumaSG-specific exceptions.
 * This class provides a hierarchy of exceptions for better error handling and debugging.
 */
public class LumaSGException extends Exception {
    
    /**
     * Creates a new LumaSGException with the specified message.
     *
     * @param message The exception message
     */
    public LumaSGException(@NotNull String message) {
        super(message);
    }
    
    /**
     * Creates a new LumaSGException with the specified message and cause.
     *
     * @param message The exception message
     * @param cause The underlying cause
     */
    public LumaSGException(@NotNull String message, @Nullable Throwable cause) {
        super(message, cause);
    }
    
    // Factory methods for common exception types
    
    /**
     * Creates an exception for arena-related errors.
     */
    public static @NotNull ArenaException arenaError(@NotNull String message) {
        return new ArenaException(message);
    }
    
    /**
     * Creates an exception for arena-related errors with a cause.
     */
    public static @NotNull ArenaException arenaError(@NotNull String message, @Nullable Throwable cause) {
        return new ArenaException(message, cause);
    }
    
    /**
     * Creates an exception for chest-related errors.
     */
    public static @NotNull ChestException chestError(@NotNull String message, @NotNull String location) {
        return new ChestException(message, location);
    }
    
    /**
     * Creates an exception for chest-related errors with a cause.
     */
    public static @NotNull ChestException chestError(@NotNull String message, @NotNull String location, @Nullable Throwable cause) {
        return new ChestException(message, location, cause);
    }
    
    /**
     * Creates an exception for game-related errors.
     */
    public static @NotNull GameException gameError(@NotNull String message) {
        return new GameException(message);
    }
    
    /**
     * Creates an exception for game-related errors with a cause.
     */
    public static @NotNull GameException gameError(@NotNull String message, @Nullable Throwable cause) {
        return new GameException(message, cause);
    }
    
    /**
     * Creates an exception for configuration-related errors.
     */
    public static @NotNull ConfigurationException configError(@NotNull String message) {
        return new ConfigurationException(message);
    }
    
    /**
     * Creates an exception for configuration-related errors with a cause.
     */
    public static @NotNull ConfigurationException configError(@NotNull String message, @Nullable Throwable cause) {
        return new ConfigurationException(message, cause);
    }
    
    /**
     * Creates an exception for configuration-related errors with context.
     */
    public static @NotNull ConfigurationException configurationError(@NotNull String message, @NotNull String context) {
        return new ConfigurationException(message + " (Context: " + context + ")");
    }
    
    /**
     * Creates an exception for configuration-related errors with context and cause.
     */
    public static @NotNull ConfigurationException configurationError(@NotNull String message, @NotNull String context, @Nullable Throwable cause) {
        return new ConfigurationException(message + " (Context: " + context + ")", cause);
    }
    
    /**
     * Creates an exception for player-related errors.
     */
    public static @NotNull PlayerException playerError(@NotNull String message) {
        return new PlayerException(message);
    }
    
    /**
     * Creates an exception for player-related errors with a cause.
     */
    public static @NotNull PlayerException playerError(@NotNull String message, @Nullable Throwable cause) {
        return new PlayerException(message, cause);
    }
    
    /**
     * Creates an exception for validation errors.
     */
    public static @NotNull ValidationException validationError(@NotNull String message) {
        return new ValidationException(message);
    }
    
    /**
     * Specific exception for arena-related errors.
     */
    public static class ArenaException extends LumaSGException {
        public ArenaException(@NotNull String message) {
            super("Arena Error: " + message);
        }
        
        public ArenaException(@NotNull String message, @Nullable Throwable cause) {
            super("Arena Error: " + message, cause);
        }
    }
    
    /**
     * Specific exception for chest-related errors.
     */
    public static class ChestException extends LumaSGException {
        private final @NotNull String location;
        
        public ChestException(@NotNull String message, @NotNull String location) {
            super("Chest Error at " + location + ": " + message);
            this.location = location;
        }
        
        public ChestException(@NotNull String message, @NotNull String location, @Nullable Throwable cause) {
            super("Chest Error at " + location + ": " + message, cause);
            this.location = location;
        }
        
        public @NotNull String getLocation() {
            return location;
        }
    }
    
    /**
     * Specific exception for game-related errors.
     */
    public static class GameException extends LumaSGException {
        public GameException(@NotNull String message) {
            super("Game Error: " + message);
        }
        
        public GameException(@NotNull String message, @Nullable Throwable cause) {
            super("Game Error: " + message, cause);
        }
    }
    
    /**
     * Specific exception for configuration-related errors.
     */
    public static class ConfigurationException extends LumaSGException {
        public ConfigurationException(@NotNull String message) {
            super("Configuration Error: " + message);
        }
        
        public ConfigurationException(@NotNull String message, @Nullable Throwable cause) {
            super("Configuration Error: " + message, cause);
        }
    }
    
    /**
     * Specific exception for player-related errors.
     */
    public static class PlayerException extends LumaSGException {
        public PlayerException(@NotNull String message) {
            super("Player Error: " + message);
        }
        
        public PlayerException(@NotNull String message, @Nullable Throwable cause) {
            super("Player Error: " + message, cause);
        }
    }
    
    /**
     * Specific exception for validation errors.
     */
    public static class ValidationException extends LumaSGException {
        public ValidationException(@NotNull String message) {
            super("Validation Error: " + message);
        }
    }
    
    /**
     * Specific exception for database-related errors.
     */
    public static class DatabaseException extends LumaSGException {
        public DatabaseException(@NotNull String message) {
            super("Database Error: " + message);
        }
        
        public DatabaseException(@NotNull String message, @Nullable Throwable cause) {
            super("Database Error: " + message, cause);
        }
    }
    
    /**
     * Creates an exception for database-related errors.
     */
    public static @NotNull DatabaseException databaseError(@NotNull String message) {
        return new DatabaseException(message);
    }
    
    /**
     * Creates an exception for database-related errors with a cause.
     */
    public static @NotNull DatabaseException databaseError(@NotNull String message, @Nullable Throwable cause) {
        return new DatabaseException(message, cause);
    }
} 
