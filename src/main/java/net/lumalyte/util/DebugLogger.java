package net.lumalyte.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.lumalyte.LumaSG;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A debug-aware logging utility that respects the plugin's debug configuration.
 * 
 * <p>This logger provides different logging levels and only outputs debug information
 * when the debug mode is enabled in the configuration. It provides clean console
 * output without color codes for better readability in server logs.</p>
 * 
 * <p>Log levels hierarchy (from least to most severe):
 * <ul>
 *   <li>DEBUG - Only shown when debug mode is enabled</li>
 *   <li>INFO - Important information (always shown)</li>
 *   <li>WARN - Warnings that don't break functionality</li>
 *   <li>ERROR - Errors that may affect functionality</li>
 *   <li>SEVERE - Critical errors that may break the plugin</li>
 * </ul>
 * </p>
 * 
 * @author LumaLyte
 * @version 1.0
 * @since 1.0
 */
public class DebugLogger {
    
    /** The plugin instance for configuration access */
    private final @NotNull LumaSG plugin;
    
    /** The underlying Java logger */
    private final @NotNull Logger logger;
    
    /** Cached debug enabled state to avoid repeated config lookups */
    private volatile boolean debugEnabled;
    
    /** Last time we checked the debug configuration */
    private volatile long lastConfigCheck = 0;
    
    /** How often to check the config for debug changes (in milliseconds) */
    private static final long CONFIG_CHECK_INTERVAL = 30000; // 30 seconds
    
    /**
     * Creates a new DebugLogger instance.
     * 
     * @param plugin The plugin instance
     */
    public DebugLogger(@NotNull LumaSG plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        updateDebugState();
    }
    
    /**
     * Updates the cached debug state from configuration.
     * This is called periodically to allow runtime debug toggling.
     */
    private void updateDebugState() {
        long now = System.currentTimeMillis();
        if (now - lastConfigCheck > CONFIG_CHECK_INTERVAL) {
            debugEnabled = plugin.getConfig().getBoolean("debug.enabled", false);
            lastConfigCheck = now;
        }
    }
    
    /**
     * Formats a log message with level and context information.
     * 
     * @param level The log level
     * @param context The context (optional)
     * @param message The message
     * @return The formatted message
     */
    private String formatMessage(@NotNull String level, @Nullable String context, @NotNull String message) {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(level).append("]");
        if (context != null && !context.isEmpty()) {
            sb.append(" [").append(context).append("]");
        }
        sb.append(" ").append(message);
        return sb.toString();
    }
    
    /**
     * Logs a debug message. Only shown when debug mode is enabled.
     * 
     * @param message The message to log
     */
    public void debug(@NotNull String message) {
        updateDebugState();
        if (debugEnabled) {
            logger.info(formatMessage("DEBUG", null, message));
        }
    }
    
    /**
     * Logs a debug message with context. Only shown when debug mode is enabled.
     * 
     * @param context The context (e.g., class name, method name)
     * @param message The message to log
     */
    public void debug(@NotNull String context, @NotNull String message) {
        updateDebugState();
        if (debugEnabled) {
            logger.info(formatMessage("DEBUG", context, message));
        }
    }
    
    /**
     * Logs a debug message with an exception. Only shown when debug mode is enabled.
     * 
     * @param message The message to log
     * @param throwable The exception to log
     */
    public void debug(@NotNull String message, @NotNull Throwable throwable) {
        updateDebugState();
        if (debugEnabled) {
            logger.log(Level.INFO, formatMessage("DEBUG", null, message), throwable);
        }
    }
    
    /**
     * Logs an info message. Always shown.
     * 
     * @param message The message to log
     */
    public void info(@NotNull String message) {
        logger.info(formatMessage("INFO", null, message));
    }
    
    /**
     * Logs an info message with context. Always shown.
     * 
     * @param context The context (e.g., class name, method name)
     * @param message The message to log
     */
    public void info(@NotNull String context, @NotNull String message) {
        logger.info(formatMessage("INFO", context, message));
    }
    
    /**
     * Logs a warning message. Always shown.
     * 
     * @param message The message to log
     */
    public void warn(@NotNull String message) {
        logger.warning(formatMessage("WARN", null, message));
    }
    
    /**
     * Logs a warning message with context. Always shown.
     * 
     * @param context The context (e.g., class name, method name)
     * @param message The message to log
     */
    public void warn(@NotNull String context, @NotNull String message) {
        logger.warning(formatMessage("WARN", context, message));
    }
    
    /**
     * Logs a warning message with an exception. Always shown.
     * 
     * @param message The message to log
     * @param throwable The exception to log
     */
    public void warn(@NotNull String message, @NotNull Throwable throwable) {
        logger.log(Level.WARNING, formatMessage("WARN", null, message), throwable);
    }
    
    /**
     * Logs a warning message with context and exception. Always shown.
     * 
     * @param context The context (e.g., class name, method name)
     * @param message The message to log
     * @param throwable The exception to log
     */
    public void warn(@NotNull String context, @NotNull String message, @NotNull Throwable throwable) {
        logger.log(Level.WARNING, formatMessage("WARN", context, message), throwable);
    }
    
    /**
     * Logs an error message. Always shown.
     * 
     * @param message The message to log
     */
    public void error(@NotNull String message) {
        logger.severe(formatMessage("ERROR", null, message));
    }
    
    /**
     * Logs an error message with context. Always shown.
     * 
     * @param context The context (e.g., class name, method name)
     * @param message The message to log
     */
    public void error(@NotNull String context, @NotNull String message) {
        logger.severe(formatMessage("ERROR", context, message));
    }
    
    /**
     * Logs an error message with an exception. Always shown.
     * 
     * @param message The message to log
     * @param throwable The exception to log
     */
    public void error(@NotNull String message, @NotNull Throwable throwable) {
        logger.log(Level.SEVERE, formatMessage("ERROR", null, message), throwable);
    }
    
    /**
     * Logs an error message with context and exception. Always shown.
     * 
     * @param context The context (e.g., class name, method name)
     * @param message The message to log
     * @param throwable The exception to log
     */
    public void error(@NotNull String context, @NotNull String message, @NotNull Throwable throwable) {
        logger.log(Level.SEVERE, formatMessage("ERROR", context, message), throwable);
    }
    
    /**
     * Logs a severe/critical error message. Always shown.
     * 
     * @param message The message to log
     */
    public void severe(@NotNull String message) {
        logger.severe(formatMessage("SEVERE", null, message));
    }
    
    /**
     * Logs a severe/critical error message with context. Always shown.
     * 
     * @param context The context (e.g., class name, method name)
     * @param message The message to log
     */
    public void severe(@NotNull String context, @NotNull String message) {
        logger.severe(formatMessage("SEVERE", context, message));
    }
    
    /**
     * Logs a severe/critical error message with an exception. Always shown.
     * 
     * @param message The message to log
     * @param throwable The exception to log
     */
    public void severe(@NotNull String message, @NotNull Throwable throwable) {
        logger.log(Level.SEVERE, formatMessage("SEVERE", null, message), throwable);
    }
    
    /**
     * Logs a severe/critical error message with context and exception. Always shown.
     * 
     * @param context The context (e.g., class name, method name)
     * @param message The message to log
     * @param throwable The exception to log
     */
    public void severe(@NotNull String context, @NotNull String message, @NotNull Throwable throwable) {
        logger.log(Level.SEVERE, formatMessage("SEVERE", context, message), throwable);
    }
    
    /**
     * Logs a startup message. Always shown and formatted specially.
     * 
     * @param message The startup message to log
     */
    public void startup(@NotNull String message) {
        logger.info(formatMessage("STARTUP", null, message));
    }
    
    /**
     * Logs a shutdown message. Always shown and formatted specially.
     * 
     * @param message The shutdown message to log
     */
    public void shutdown(@NotNull String message) {
        logger.info(formatMessage("SHUTDOWN", null, message));
    }
    
    /**
     * Checks if debug mode is currently enabled.
     * 
     * @return true if debug mode is enabled, false otherwise
     */
    public boolean isDebugEnabled() {
        updateDebugState();
        return debugEnabled;
    }
    
    /**
     * Forces a refresh of the debug configuration state.
     * Useful when you know the configuration has changed.
     */
    public void refreshConfig() {
        debugEnabled = plugin.getConfig().getBoolean("debug.enabled", false);
        lastConfigCheck = System.currentTimeMillis();
    }
    
    /**
     * Creates a contextual logger for a specific class or component.
     * This returns a wrapper that automatically includes context in all log messages.
     * 
     * @param context The context (usually class name)
     * @return A contextual logger wrapper
     */
    public @NotNull ContextualLogger forContext(@NotNull String context) {
        return new ContextualLogger(this, context);
    }
    
    /**
     * Creates a contextual logger for a specific class.
     * This returns a wrapper that automatically includes the class name in all log messages.
     * 
     * @param clazz The class to create a logger for
     * @return A contextual logger wrapper
     */
    public @NotNull ContextualLogger forClass(@NotNull Class<?> clazz) {
        return new ContextualLogger(this, clazz.getSimpleName());
    }
    
    /**
     * A contextual logger that automatically includes context in all log messages.
     */
    public static class ContextualLogger {
        private final @NotNull DebugLogger logger;
        private final @NotNull String context;
        
        private ContextualLogger(@NotNull DebugLogger logger, @NotNull String context) {
            this.logger = logger;
            this.context = context;
        }
        
        public void debug(@NotNull String message) {
            logger.debug(context, message);
        }
        
        public void debug(@NotNull String message, @NotNull Throwable throwable) {
            logger.debug(message, throwable);
        }
        
        public void info(@NotNull String message) {
            logger.info(context, message);
        }
        
        public void warn(@NotNull String message) {
            logger.warn(context, message);
        }
        
        public void warn(@NotNull String message, @NotNull Throwable throwable) {
            logger.warn(context, message, throwable);
        }
        
        public void error(@NotNull String message) {
            logger.error(context, message);
        }
        
        public void error(@NotNull String message, @NotNull Throwable throwable) {
            logger.error(context, message, throwable);
        }
        
        public void severe(@NotNull String message) {
            logger.severe(context, message);
        }
        
        public void severe(@NotNull String message, @NotNull Throwable throwable) {
            logger.severe(context, message, throwable);
        }
        
        public boolean isDebugEnabled() {
            return logger.isDebugEnabled();
        }
    }
} 