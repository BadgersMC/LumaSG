package net.lumalyte;

import net.lumalyte.arena.Arena;
import net.lumalyte.arena.ArenaManager;
import net.lumalyte.game.TeamQueueManager;
import net.lumalyte.chest.ChestManager;
import net.lumalyte.commands.SGCommand;
import net.lumalyte.customitems.CustomItemsManager;
import net.lumalyte.game.GameManager;
import net.lumalyte.gui.MenuUtils;
import net.lumalyte.hooks.HookManager;
import net.lumalyte.listeners.AdminWandListener;
import net.lumalyte.listeners.ChestListener;
import net.lumalyte.listeners.CustomItemListener;
import net.lumalyte.listeners.FishingListener;
import net.lumalyte.listeners.PlayerListener;
import net.lumalyte.statistics.StatisticsManager;
import net.lumalyte.util.AdminWand;
import net.lumalyte.util.ConfigurationManager;
import net.lumalyte.util.DebugLogger;
import net.lumalyte.util.ValidationUtils;
import net.lumalyte.util.SkinCache;
import net.lumalyte.util.PlayerDataCache;
import net.lumalyte.util.InvitationManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.PluginManager;
import org.jetbrains.annotations.NotNull;
import xyz.xenondevs.invui.InvUI;
import net.lumalyte.util.CacheManager;
import net.lumalyte.util.PerformanceProfiler;

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
    private CustomItemsManager customItemsManager;
    private HookManager hookManager;
    private StatisticsManager statisticsManager;
    private AdminWandListener adminWandListener;
    private CustomItemListener customItemListener;
    private DebugLogger debugLogger;
    private AdminWand adminWand;
    private TeamQueueManager teamQueueManager;
    private ConfigurationManager configManager;
    
    @Override
    public void onEnable() {
        // Register this instance for static access
        instance = this;
        
        // Set the plugin instance for InvUI
        InvUI.getInstance().setPlugin(this);
        
        // Initialize debug logger early
        debugLogger = new DebugLogger(this);
        
        // Initialize caching systems
        initializeCachingSystems();
        
        // Initialize configuration manager and update configs
        configManager = new ConfigurationManager(this);
        configManager.updateAllConfigs();
        
        // Initialize managers
        arenaManager = new ArenaManager(this);
        gameManager = new GameManager(this);
        chestManager = new ChestManager(this);
        customItemsManager = new CustomItemsManager(this);
        hookManager = new HookManager(this);
        statisticsManager = new StatisticsManager(this);
        adminWandListener = new AdminWandListener(this);
        customItemListener = new CustomItemListener(this);
        adminWand = new AdminWand(this);
        teamQueueManager = new TeamQueueManager(this);
        
        // Validate managers were created successfully
        ValidationUtils.requireNonNull(configManager, "Configuration Manager", "Plugin Initialization");
        ValidationUtils.requireNonNull(arenaManager, "Arena Manager", "Plugin Initialization");
        ValidationUtils.requireNonNull(gameManager, "Game Manager", "Plugin Initialization");
        ValidationUtils.requireNonNull(chestManager, "Chest Manager", "Plugin Initialization");
        ValidationUtils.requireNonNull(customItemsManager, "Custom Items Manager", "Plugin Initialization");
        ValidationUtils.requireNonNull(hookManager, "Hook Manager", "Plugin Initialization");
        ValidationUtils.requireNonNull(statisticsManager, "Statistics Manager", "Plugin Initialization");
        ValidationUtils.requireNonNull(adminWandListener, "Admin Wand Listener", "Plugin Initialization");
        ValidationUtils.requireNonNull(customItemListener, "Custom Item Listener", "Plugin Initialization");
        ValidationUtils.requireNonNull(adminWand, "Admin Wand", "Plugin Initialization");
        ValidationUtils.requireNonNull(teamQueueManager, "Team Queue Manager", "Plugin Initialization");
        
        // Initialize GUI system
        MenuUtils.initialize(this);
        
        // Start managers
        arenaManager.start();
        hookManager.start();
        
        // Initialize custom items manager FIRST (before chest loading)
        if (!customItemsManager.initialize()) {
            getLogger().log(Level.SEVERE, "Failed to initialize custom items manager");
        }
        
        // Initialize statistics manager (async)
        statisticsManager.initialize().exceptionally(throwable -> {
            getLogger().log(Level.SEVERE, "Failed to initialize statistics manager", throwable);
            return null;
        });
        
        // Load chest items AFTER custom items are initialized
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
            // Shutdown caching systems first to ensure data persistence
            debugLogger.info("Shutting down advanced caching systems...");
            CacheManager.shutdown();
            debugLogger.info("Cache systems shutdown completed");
            
            // Stop managers in reverse order
            if (statisticsManager != null) {
                statisticsManager.shutdown().join(); // Wait for statistics to be saved
            }
            if (customItemListener != null) customItemListener.shutdown();
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
            
            // Update configuration files with any new options
            configManager.updateAllConfigs();
            
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
        SGCommand sgCommand = new SGCommand(this);
        // The command will be registered via the LumaSGBootstrap class
        debugLogger.debug("Created 'sg' command using Paper's command system");
    }
    
    private void registerListeners() {
        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(new PlayerListener(this), this);
        pm.registerEvents(new ChestListener(this), this);
        pm.registerEvents(adminWandListener, this);
        pm.registerEvents(customItemListener, this);
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
    
    public @NotNull CustomItemsManager getCustomItemsManager() {
        return customItemsManager;
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
    
    /**
     * Gets the admin wand instance.
     */
    public @NotNull AdminWand getAdminWand() {
        return adminWand;
    }
    
    public CustomItemListener getCustomItemListener() {
        return customItemListener;
    }
    
    /**
     * Gets the team queue manager instance.
     * 
     * @return The team queue manager
     */
    public @NotNull TeamQueueManager getTeamQueueManager() {
        return teamQueueManager;
    }
    
    /**
     * Gets the configuration manager instance.
     * 
     * @return The configuration manager
     */
    public @NotNull ConfigurationManager getConfigManager() {
        return configManager;
    }
    
    /**
     * Initializes all advanced caching systems for optimal performance
     * Features enterprise-level multi-tier caching with Caffeine
     */
    private void initializeCachingSystems() {
        try {
            // Initialize centralized cache manager with multi-tier architecture
            CacheManager.initialize(this);
            getDebugLogger().info("Initialized CacheManager with advanced multi-tier Caffeine caching");
            
            // Initialize performance profiler with thread-safe metrics
            PerformanceProfiler.initialize(this);
            getDebugLogger().info("Initialized PerformanceProfiler with atomic counters and metrics");
            
            // Initialize core caching systems (already handled by CacheManager)
            getDebugLogger().info("Initialized comprehensive caching architecture:");
            getDebugLogger().info("  ✓ PlayerDataCache - Advanced async loading with write-through");
            getDebugLogger().info("  ✓ SkinCache - High-performance skin data with 6-hour expiration");
            getDebugLogger().info("  ✓ GuiComponentCache - GUI item and leaderboard optimization");
            getDebugLogger().info("  ✓ ScoreboardCache - Scoreboard line and placeholder caching");
            getDebugLogger().info("  ✓ InvitationManager - Caffeine-based invitation system");
            
            getDebugLogger().info("All enterprise caching systems initialized successfully");
            getDebugLogger().info("ExpiringMap dependency eliminated - using pure Caffeine architecture");
        } catch (Exception e) {
            getDebugLogger().error("Failed to initialize advanced caching systems", e);
            throw new RuntimeException("Critical cache initialization failure", e);
        }
    }
} 
