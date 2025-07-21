package net.lumalyte.util;

import org.jetbrains.annotations.NotNull;

import net.lumalyte.LumaSG;

/**
 * Base class for all manager components in LumaSG.
 * 
 * <p>This class provides common initialization patterns and utility methods
 * that are shared across all manager classes, reducing code duplication and
 * ensuring consistent patterns throughout the codebase.</p>
 * 
 * <p>Features provided by this base class:</p>
 * <ul>
 *   <li>Plugin instance management</li>
 *   <li>Contextual logger initialization</li>
 *   <li>Standardized validation patterns</li>
 *   <li>Common lifecycle methods</li>
 * </ul>
 * 
 * @author LumaSG Team
 * @since 1.0.0
 */
public abstract class BaseManager {
    
    /** The plugin instance for configuration and server access */
    protected final @NotNull LumaSG plugin;
    
    /** The contextual logger instance for this manager */
    protected final @NotNull DebugLogger.ContextualLogger logger;
    
    /**
     * Constructs a new BaseManager instance.
     * 
     * @param plugin The plugin instance
     * @param managerName The name of the manager for logging context
     */
    protected BaseManager(@NotNull LumaSG plugin, @NotNull String managerName) {
        ValidationUtils.requireNonNull(plugin, "Plugin", managerName + " Construction");
        ValidationUtils.requireNonEmpty(managerName, "Manager Name", "BaseManager Construction");
        
        this.plugin = plugin;
        this.logger = plugin.getDebugLogger().forContext(managerName);
        
        logger.debug(managerName + " base initialization completed");
    }
    
    /**
     * Gets the plugin instance.
     * 
     * @return The plugin instance
     */
    protected final @NotNull LumaSG getPlugin() {
        return plugin;
    }
    
    /**
     * Gets the logger instance.
     * 
     * @return The contextual logger
     */
    protected final @NotNull DebugLogger.ContextualLogger getLogger() {
        return logger;
    }
    
    /**
     * Initializes the manager. Override this method in subclasses
     * to provide specific initialization logic.
     * 
     * @throws IllegalStateException if initialization fails
     */
    public void initialize() {
        logger.debug("Manager initialized successfully");
    }
    
    /**
     * Starts the manager operations. Override this method in subclasses
     * to provide specific startup logic.
     */
    public void start() {
        logger.debug("Manager started successfully");
    }
    
    /**
     * Stops the manager operations and performs cleanup. Override this method
     * in subclasses to provide specific shutdown logic.
     */
    public void stop() {
        logger.debug("Manager stopped successfully");
    }
    
    /**
     * Shuts down the manager completely. This method calls stop() by default
     * but can be overridden for more complex shutdown procedures.
     */
    public void shutdown() {
        stop();
        logger.debug("Manager shutdown completed");
    }
} 