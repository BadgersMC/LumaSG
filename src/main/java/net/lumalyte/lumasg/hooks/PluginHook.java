package net.lumalyte.lumasg.hooks;

import org.bukkit.plugin.Plugin;

/**
 * Interface for plugin hooks.
 */
public interface PluginHook {
    
    /**
     * Gets the name of the plugin this hook is for.
     * 
     * @return The plugin name
     */
    String getPluginName();
    
    /**
     * Checks if the plugin is available.
     * 
     * @return True if the plugin is available, false otherwise
     */
    boolean isAvailable();
    
    /**
     * Gets the plugin instance.
     * 
     * @return The plugin instance
     */
    Plugin getPlugin();
    
    /**
     * Initializes the hook.
     * 
     * @return True if initialization was successful, false otherwise
     */
    boolean initialize();
    
    /**
     * Enables the hook.
     * 
     * @return True if enabling was successful, false otherwise
     */
    boolean enable();
    
    /**
     * Disables the hook.
     */
    void disable();
} 
