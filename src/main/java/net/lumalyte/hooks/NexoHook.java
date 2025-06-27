package net.lumalyte.hooks;

import com.nexomc.nexo.api.NexoItems;
import net.lumalyte.LumaSG;
import net.lumalyte.util.DebugLogger;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.Objects;
import java.util.Optional;

/**
 * Hook for the Nexo plugin.
 */
public class NexoHook implements PluginHook {
    
    private static final String PLUGIN_NAME = "Nexo";
    private Plugin nexoPlugin;
    private boolean available = false;
    
    /** The debug logger instance for this nexo hook */
    private final DebugLogger.ContextualLogger logger;
    
    public NexoHook(LumaSG plugin) {
        this.logger = plugin.getDebugLogger().forContext("NexoHook");
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
            logger.info("Nexo plugin not found or not enabled. Nexo item support will be disabled.");
            return false;
        }
        
        // Test if we can access the Nexo API by attempting to use it
        try {
            // Try to get a non-existent item - this will throw a normal ItemNotFoundException
            // but will validate that we can access the API
            NexoItems.itemFromId("test_api_access");
            available = true;
            logger.info("Successfully hooked into Nexo!");
            return true;
        } catch (IllegalArgumentException e) {
            // Expected exception for non-existent item - API is working
            available = true;
            logger.info("Successfully hooked into Nexo!");
            return true;
        } catch (Exception e) {
            // Any other exception means we can't access the API
            logger.warn("Failed to access Nexo API. Nexo item support will be disabled.", e);
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
            return Optional.of(Objects.requireNonNull(NexoItems.itemFromId(itemId)).build());
        } catch (Exception e) {
            logger.warn("Failed to get Nexo item: " + itemId, e);
            return Optional.empty();
        }
    }

    @Override
    public boolean enable() {
        return initialize();
    }

    @Override
    public void disable() {
        available = false;
        nexoPlugin = null;
    }
} 
