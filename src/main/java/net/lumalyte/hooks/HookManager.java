package net.lumalyte.hooks;

import net.lumalyte.LumaSG;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

/**
 * Manages hooks to other plugins.
 */
public class HookManager {
    private final @NotNull LumaSG plugin;
    private final @NotNull Map<String, PluginHook> hooks;
    private PlaceholderAPIHook placeholderAPIHook;
    private AuraSkillsHook auraSkillsHook;
    
    public HookManager(@NotNull LumaSG plugin) {
        this.plugin = plugin;
        this.hooks = new HashMap<>();
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
                plugin.getLogger().log(Level.WARNING, "Failed to disable hook: " + hook.getPluginName(), e);
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
        
        // Initialize AuraSkills hook
        initializeAuraSkillsHook();
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
                    plugin.getLogger().info("Successfully enabled Nexo hook!");
                } else {
                    plugin.getLogger().warning("Failed to enable Nexo hook!");
                }
            } else {
                plugin.getLogger().info("Nexo plugin not found or not compatible.");
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to initialize Nexo hook", e);
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
                    plugin.getLogger().info("Successfully registered PlaceholderAPI hook!");
                } else {
                    plugin.getLogger().warning("Failed to register PlaceholderAPI hook!");
                    placeholderAPIHook = null;
                }
            } else {
                plugin.getLogger().info("PlaceholderAPI plugin not found.");
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to initialize PlaceholderAPI hook", e);
            placeholderAPIHook = null;
        }
    }
    
    /**
     * Initializes the AuraSkills hook.
     */
    private void initializeAuraSkillsHook() {
        try {
            auraSkillsHook = new AuraSkillsHook(plugin);
            if (auraSkillsHook.initialize()) {
                if (auraSkillsHook.enable()) {
                    hooks.put(auraSkillsHook.getPluginName(), auraSkillsHook);
                    plugin.getLogger().info("Successfully enabled AuraSkills hook!");
                } else {
                    plugin.getLogger().warning("Failed to enable AuraSkills hook!");
                    auraSkillsHook = null;
                }
            } else {
                plugin.getLogger().info("AuraSkills plugin not found or not compatible.");
                auraSkillsHook = null;
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to initialize AuraSkills hook", e);
            auraSkillsHook = null;
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
     * Gets the AuraSkills hook.
     * 
     * @return The AuraSkills hook, or null if not available
     */
    public @Nullable AuraSkillsHook getAuraSkillsHook() {
        return auraSkillsHook;
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
     * Resets a player's stats for the duration of a game.
     * This will use hooks to reset any stat modifiers from other plugins.
     * 
     * @param player The player to reset stats for
     */
    public void resetPlayerStats(@NotNull Player player) {
        if (auraSkillsHook != null && auraSkillsHook.isAvailable()) {
            auraSkillsHook.resetPlayerStats(player);
        }
    }
    
    /**
     * Restores a player's stats after a game.
     * This will use hooks to restore any stat modifiers from other plugins.
     * 
     * @param player The player to restore stats for
     */
    public void restorePlayerStats(@NotNull Player player) {
        if (auraSkillsHook != null && auraSkillsHook.isAvailable()) {
            auraSkillsHook.restorePlayerStats(player);
        }
    }
} 
