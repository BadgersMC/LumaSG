package net.lumalyte;

import net.lumalyte.arena.Arena;
import net.lumalyte.arena.ArenaManager;
import net.lumalyte.chest.ChestManager;
import net.lumalyte.commands.SGBrigadierCommand;
import net.lumalyte.game.GameManager;
import net.lumalyte.gui.MenuUtils;
import net.lumalyte.hooks.HookManager;
import net.lumalyte.listeners.AdminWandListener;
import net.lumalyte.listeners.ChestListener;
import net.lumalyte.listeners.FishingListener;
import net.lumalyte.listeners.PlayerListener;
import net.lumalyte.statistics.StatisticsManager;
import net.lumalyte.util.DebugLogger;
import net.lumalyte.util.ValidationUtils;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.PluginManager;
import org.jetbrains.annotations.NotNull;
import xyz.xenondevs.invui.InvUI;
import org.bukkit.configuration.serialization.ConfigurationSerialization;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Main plugin class for LumaSG (Survival Games).
 */
public class LumaSG extends JavaPlugin {
    
    // Single 4 lyfe
    /** Static instance for global access */
    private static LumaSG instance;
    
    private ArenaManager arenaManager;
    private GameManager gameManager;
    private ChestManager chestManager;
    private HookManager hookManager;
    private StatisticsManager statisticsManager;
    private AdminWandListener adminWandListener;
    private DebugLogger debugLogger;
    
    @Override
    public void onEnable() {
        // Register this instance for static access
        instance = this;
        
        // Set the plugin instance for InvUI
        InvUI.getInstance().setPlugin(this);
        

        
        // Load configuration
        saveDefaultConfig();
        saveResource("chest.yml", false);
        saveResource("fishing.yml", false);
        saveResource("config.yml", false);
        
        // Initialize debug logger early
        debugLogger = new DebugLogger(this);
        
        // Initialize managers
        arenaManager = new ArenaManager(this);
        gameManager = new GameManager(this);
        chestManager = new ChestManager(this);
        hookManager = new HookManager(this);
        statisticsManager = new StatisticsManager(this);
        adminWandListener = new AdminWandListener(this);
        
        // Validate managers were created successfully
        ValidationUtils.requireNonNull(arenaManager, "Arena Manager", "Plugin Initialization");
        ValidationUtils.requireNonNull(gameManager, "Game Manager", "Plugin Initialization");
        ValidationUtils.requireNonNull(chestManager, "Chest Manager", "Plugin Initialization");
        ValidationUtils.requireNonNull(hookManager, "Hook Manager", "Plugin Initialization");
        ValidationUtils.requireNonNull(statisticsManager, "Statistics Manager", "Plugin Initialization");
        ValidationUtils.requireNonNull(adminWandListener, "Admin Wand Listener", "Plugin Initialization");
        
        // Initialize GUI system
        MenuUtils.initialize(this);
        
        // Start managers
        arenaManager.start();
        hookManager.start();
        
        // Initialize statistics manager (async)
        statisticsManager.initialize().exceptionally(throwable -> {
            getLogger().log(Level.SEVERE, "Failed to initialize statistics manager", throwable);
            return null;
        });
        
        // Load chest items
        chestManager.loadChestItems();
        
        // Register commands
        registerCommands();
        
        // Register event listeners
        registerListeners();
        
        // Start periodic cleanup task for orphaned games (every 5 minutes)
        getServer().getScheduler().runTaskTimer(this, () -> {
            try {
                int cleanedUp = gameManager.cleanupOrphanedGames();
                if (cleanedUp > 0) {
                    debugLogger.warn("Periodic cleanup removed " + cleanedUp + " orphaned games");
                }
            } catch (Exception e) {
                debugLogger.error("Error during periodic game cleanup", e);
            }
        }, 6000L, 6000L); // First run after 5 minutes, then every 5 minutes
        
        // Check for PlaceholderAPI
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            debugLogger.startup("PlaceholderAPI found! Registering placeholders...");
        } else {
            debugLogger.warn("PlaceholderAPI not found. Placeholders will not be available.");
            debugLogger.warn("Download PlaceholderAPI from: https://www.spigotmc.org/resources/placeholderapi.6245/");
        }
        
        debugLogger.startup("LumaSG has been enabled!");
    }
    
    @Override
    public void onDisable() {
        try {
            // Stop managers in reverse order
            if (statisticsManager != null) {
                statisticsManager.shutdown().join(); // Wait for statistics to be saved
            }
            if (hookManager != null) hookManager.stop();
            if (gameManager != null) gameManager.shutdown();
            if (chestManager != null) chestManager.stop();
            
            // Clean up all arena effects
            if (arenaManager != null) {
                for (Arena arena : arenaManager.getArenas()) {
                    arena.cleanup();
                }
                arenaManager.stop();
            }
            
            debugLogger.shutdown("LumaSG has been disabled successfully!");
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Error during plugin shutdown", e);
        }
    }
    
    /**
     * Reloads the plugin configuration and data.
     */
    public void reload() {
        try {
            // Reload configuration
            reloadConfig();
            
            // Stop and restart managers
            if (hookManager != null) hookManager.stop();
            if (chestManager != null) chestManager.stop();
            
            // Restart managers
            chestManager.start();
            hookManager.start();
            
            debugLogger.info("LumaSG has been reloaded successfully!");
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Error during plugin reload", e);
        }
    }
    
    private void registerCommands() {
        // Register the SG command using Paper's command system
        SGBrigadierCommand sgCommand = new SGBrigadierCommand(this);
        sgCommand.register();
        debugLogger.debug("Registered 'sg' command using Paper's command system");
    }
    
    private void registerListeners() {
        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(new PlayerListener(this), this);
        pm.registerEvents(new ChestListener(this), this);
        pm.registerEvents(adminWandListener, this);
        pm.registerEvents(new FishingListener(this), this);
    }
    
    /**
     * Gets the admin wand listener instance.
     */
    public @NotNull AdminWandListener getAdminWandListener() {
        return adminWandListener;
    }
    
    /**
     * Gets the plugin's logger instance.
     */
    public @NotNull Logger getPluginLogger() {
        return getLogger();
    }
    
    public @NotNull ArenaManager getArenaManager() {
        return arenaManager;
    }
    
    public @NotNull GameManager getGameManager() {
        return gameManager;
    }
    
    public @NotNull ChestManager getChestManager() {
        return chestManager;
    }
    
    public @NotNull HookManager getHookManager() {
        return hookManager;
    }
    
    public @NotNull StatisticsManager getStatisticsManager() {
        return statisticsManager;
    }
    
    /**
     * Gets the debug logger instance.
     */
    public @NotNull DebugLogger getDebugLogger() {
        return debugLogger;
    }
    
    /**
     * Gets the static instance of the plugin.
     * 
     * @return The plugin instance
     */
    public static @NotNull LumaSG getInstance() {
        return instance;
    }
} 
