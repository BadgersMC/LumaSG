package net.lumalyte.lumasg.hooks;

import net.lumalyte.lumasg.LumaSG;
import net.lumalyte.lumasg.util.core.DebugLogger;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.ConcurrentHashMap;
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
        this.hooks = new ConcurrentHashMap<>();
        this.logger = plugin.getDebugLogger().forContext("HookManager");
    }
    
    /**
     * Starts all plugin hooks.
     */
    public void start() {
        logger.info("Starting plugin hooks...");
        
        // Initialize PlaceholderAPI hook (different pattern since it extends PlaceholderExpansion)
        if (plugin.getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            placeholderAPIHook = new PlaceholderAPIHook(plugin);
            if (placeholderAPIHook.register()) {
                logger.info("Successfully registered PlaceholderAPI hook!");
            } else {
                logger.warn("Failed to register PlaceholderAPI hook!");
                placeholderAPIHook = null;
            }
        }
        
        // Initialize Nexo hook
        PluginHook nexoHook = new NexoHook(plugin);
        if (nexoHook.enable()) {
            hooks.put("Nexo", nexoHook);
        }
        
        // Initialize KingdomsX hook
        PluginHook kingdomsXHook = new KingdomsXHook(plugin);
        if (kingdomsXHook.enable()) {
            hooks.put("KingdomsX", kingdomsXHook);
        }
        
        logger.info("Started " + hooks.size() + " plugin hooks");
    }
    
    /**
     * Stops all plugin hooks.
     */
    public void stop() {
        logger.info("Stopping plugin hooks...");
        
        // Stop PlaceholderAPI hook
        if (placeholderAPIHook != null) {
            placeholderAPIHook.unregister();
            placeholderAPIHook = null;
        }
        
        // Stop other hooks
        for (PluginHook hook : hooks.values()) {
            try {
                hook.disable();
            } catch (Exception e) {
                logger.error("Error stopping hook for " + hook.getPluginName(), e);
            }
        }
        
        hooks.clear();
        
        logger.info("Stopped all plugin hooks");
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
     * Gets the KingdomsX hook.
     * 
     * @return The KingdomsX hook, or null if not available
     */
    public @Nullable KingdomsXHook getKingdomsXHook() {
        PluginHook hook = hooks.get("KingdomsX");
        return hook instanceof KingdomsXHook ? (KingdomsXHook) hook : null;
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
    
    /**
     * Checks if a player is in an active survival game with PvP enabled.
     * This is a convenience method that delegates to the KingdomsX hook if available.
     * 
     * @param player The player to check
     * @return true if the player is in an active survival game with PvP enabled
     */
    public boolean isPlayerInActivePvPGame(@NotNull Player player) {
        KingdomsXHook kingdomsXHook = getKingdomsXHook();
        return kingdomsXHook != null && kingdomsXHook.isPlayerInActivePvPGame(player);
    }
    
    /**
     * Checks if two players are in the same active survival game with PvP enabled.
     * This is a convenience method that delegates to the KingdomsX hook if available.
     * 
     * @param player1 First player
     * @param player2 Second player
     * @return true if both players are in the same active survival game with PvP enabled
     */
    public boolean arePlayersInSamePvPGame(@NotNull Player player1, @NotNull Player player2) {
        KingdomsXHook kingdomsXHook = getKingdomsXHook();
        if (kingdomsXHook == null || !kingdomsXHook.isAvailable()) {
            return false;
        }
        
        return kingdomsXHook.isPlayerInActivePvPGame(player1) && 
               kingdomsXHook.isPlayerInActivePvPGame(player2);
    }
} 
