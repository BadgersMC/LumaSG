package net.lumalyte.hooks;

import net.lumalyte.LumaSG;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.logging.Level;

/**
 * Hook for the Nexo plugin.
 */
public class NexoHook implements PluginHook {
    
    private static final String PLUGIN_NAME = "Nexo";
    private Plugin nexoPlugin;
    private boolean available = false;
    private Method getItemMethod;
    
    private final LumaSG plugin;
    
    public NexoHook(LumaSG plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public String getPluginName() {
        return PLUGIN_NAME;
    }
    
    @Override
    public boolean isAvailable() {
        return available;
    }
    
    @Override
    public Plugin getPlugin() {
        return nexoPlugin;
    }
    
    @Override
    public boolean initialize() {
        nexoPlugin = Bukkit.getPluginManager().getPlugin(PLUGIN_NAME);
        
        if (nexoPlugin == null || !nexoPlugin.isEnabled()) {
            plugin.getLogger().info("Nexo plugin not found or not enabled. Nexo item support will be disabled.");
            return false;
        }
        
        try {
            // Try to access the Nexo API class using reflection in a safer way
            Class<?> nexoAPIClass = null;
            try {
                // Try to get the class from the plugin's classloader first
                nexoAPIClass = nexoPlugin.getClass().getClassLoader().loadClass("com.nexo.api.NexoAPI");
            } catch (ClassNotFoundException e) {
                // If that fails, try the standard class loading approach
                try {
                    nexoAPIClass = Class.forName("com.nexo.api.NexoAPI");
                } catch (ClassNotFoundException ex) {
                    plugin.getLogger().warning("Nexo API class not found. Nexo item support will be disabled.");
                    return false;
                }
            }
            
            // Get the getItem method
            getItemMethod = nexoAPIClass.getMethod("getItem", String.class);
            
            available = true;
            plugin.getLogger().info("Successfully hooked into Nexo!");
            return true;
        } catch (NoSuchMethodException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to hook into Nexo API. Nexo item support will be disabled.", e);
            return false;
        }
    }
    
    /**
     * Gets a Nexo item by its ID.
     * 
     * @param itemId The ID of the Nexo item
     * @return The ItemStack, or empty if the item could not be found
     */
    public Optional<ItemStack> getNexoItem(String itemId) {
        if (!isAvailable()) {
            return Optional.empty();
        }
        
        try {
            Object result = getItemMethod.invoke(null, itemId);
            if (result instanceof ItemStack) {
                return Optional.of((ItemStack) result);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to get Nexo item: " + itemId, e);
        }
        
        return Optional.empty();
    }

    @Override
    public boolean enable() {
        return initialize();
    }

    @Override
    public void disable() {
        available = false;
        nexoPlugin = null;
        getItemMethod = null;
    }
} 
