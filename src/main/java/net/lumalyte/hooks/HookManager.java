package net.lumalyte.hooks;

import net.lumalyte.LumaSG;
import net.lumalyte.util.DebugLogger;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Manages hooks to other plugins.
 */
public class HookManager {
    private final @NotNull LumaSG plugin;
    private final @NotNull Map<String, PluginHook> hooks;
    private PlaceholderAPIHook placeholderAPIHook;
    
    /** The debug logger instance for this hook manager */
    private final @NotNull DebugLogger.ContextualLogger logger;
    
    public HookManager(@NotNull LumaSG plugin) {
        this.plugin = plugin;
        this.hooks = new HashMap<>();
        this.logger = plugin.getDebugLogger().forContext("HookManager");
    }
    
    /**
     * Starts the hook manager.
     */
    public void start() {
        initializeHooks();
    }
    
    /**
     * Stops the hook manager.
     */
    public void stop() {
        // Disable all hooks
        for (PluginHook hook : hooks.values()) {
            try {
                hook.disable();
            } catch (Exception e) {
                logger.warn("Failed to disable hook: " + hook.getPluginName(), e);
            }
        }
        
        hooks.clear();
    }
    
    /**
     * Initializes all hooks.
     */
    private void initializeHooks() {
        // Initialize Nexo hook
        initializeNexoHook();
        
        // Initialize PlaceholderAPI hook
        initializePlaceholderAPIHook();
    }
    
    /**
     * Initializes the Nexo hook.
     */
    private void initializeNexoHook() {
        try {
            NexoHook nexoHook = new NexoHook(plugin);
            if (nexoHook.initialize()) {
                if (nexoHook.enable()) {
                    hooks.put(nexoHook.getPluginName(), nexoHook);
                    logger.info("Successfully enabled Nexo hook!");
                } else {
                    logger.warn("Failed to enable Nexo hook!");
                }
            } else {
                logger.info("Nexo plugin not found or not compatible.");
            }
        } catch (Exception e) {
            logger.warn("Failed to initialize Nexo hook", e);
        }
    }
    
    /**
     * Initializes the PlaceholderAPI hook.
     */
    private void initializePlaceholderAPIHook() {
        try {
            Plugin placeholderPlugin = plugin.getServer().getPluginManager().getPlugin("PlaceholderAPI");
            if (placeholderPlugin != null) {
                placeholderAPIHook = new PlaceholderAPIHook(plugin);
                if (placeholderAPIHook.register()) {
                    logger.info("Successfully registered PlaceholderAPI hook!");
                } else {
                    logger.warn("Failed to register PlaceholderAPI hook!");
                    placeholderAPIHook = null;
                }
            } else {
                logger.info("PlaceholderAPI plugin not found.");
            }
        } catch (Exception e) {
            logger.warn("Failed to initialize PlaceholderAPI hook", e);
            placeholderAPIHook = null;
        }
    }
    

    
    /**
     * Gets the Nexo hook.
     * 
     * @return The Nexo hook, or null if not available
     */
    public @Nullable NexoHook getNexoHook() {
        PluginHook hook = hooks.get("Nexo");
        return hook instanceof NexoHook ? (NexoHook) hook : null;
    }
    

    
    /**
     * Checks if a hook is available.
     * 
     * @param hookName The name of the hook
     * @return True if the hook is available, false otherwise
     */
    public boolean isHookAvailable(@NotNull String hookName) {
        PluginHook hook = hooks.get(hookName);
        return hook != null && hook.isAvailable();
    }
    

} 
