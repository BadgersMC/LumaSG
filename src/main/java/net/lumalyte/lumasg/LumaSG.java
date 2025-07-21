package net.lumalyte.lumasg;

import java.util.logging.Level;
import java.util.logging.Logger;

import net.lumalyte.lumasg.util.cache.*;
import net.lumalyte.lumasg.util.config.*;
import net.lumalyte.lumasg.util.core.*;
import net.lumalyte.lumasg.util.concurrent.*;
import net.lumalyte.lumasg.util.game.*;
import net.lumalyte.lumasg.util.serialization.*;
import net.lumalyte.lumasg.util.validation.*;
import net.lumalyte.lumasg.util.validation.ConfigValidator;
import net.lumalyte.lumasg.util.performance.*;
import net.lumalyte.lumasg.util.cache.ArenaWorldCache;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import net.lumalyte.lumasg.arena.Arena;
import net.lumalyte.lumasg.arena.ArenaManager;
import net.lumalyte.lumasg.chest.ChestManager;
import net.lumalyte.lumasg.commands.SGCommand;
import net.lumalyte.lumasg.customitems.CustomItemsManager;
import net.lumalyte.lumasg.game.GameManager;
import net.lumalyte.lumasg.game.TeamQueueManager;
import net.lumalyte.lumasg.gui.MenuUtils;
import net.lumalyte.lumasg.hooks.HookManager;
import net.lumalyte.lumasg.listeners.AdminWandListener;
import net.lumalyte.lumasg.listeners.ChestListener;
import net.lumalyte.lumasg.listeners.CustomItemListener;
import net.lumalyte.lumasg.listeners.FishingListener;
import net.lumalyte.lumasg.listeners.PlayerListener;
import net.lumalyte.lumasg.statistics.StatisticsManager;

import xyz.xenondevs.invui.InvUI;

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
        
        // Initialize Kryo serialization manager early
        KryoManager.initialize(this);
        
        // Initialize core systems
        initializeCoreComponents();
        
        // Initialize and start managers
        initializeAndStartManagers();
        
        // Setup periodic tasks and integrations
        setupPeriodicTasks();
        setupExternalIntegrations();

        // IT'S.... ALIIIIVEE! 
        debugLogger.startup("LumaSG has been enabled!");
    }
    
    /**
     * Initializes core components including caching, configuration, and GUI systems.
     */
    private void initializeCoreComponents() {
        // Initialize caching systems
        initializeCachingSystems();
        
        // Initialize configuration manager and update configs
        configManager = new ConfigurationManager(this);
        configManager.updateAllConfigs();
        
        // Validate configuration before proceeding
        validateConfiguration();
        
        // Initialize GUI system
        MenuUtils.initialize(this);
    }
    
    /**
     * Initializes, validates, and starts all manager components.
     */
    private void initializeAndStartManagers() {
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
        validateManagers();
        
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
        
        // Initialize chest optimization systems after chest manager is ready
        initializeChestOptimizations();
        
        // Register commands and event listeners
        registerCommands();
        registerListeners();
    }
    
    /**
     * Validates the plugin configuration for safety and correctness.
     * This prevents the plugin from starting with dangerous or invalid settings.
     */
    private void validateConfiguration() {
        ConfigValidator validator = new ConfigValidator(this);
        
        if (!validator.validateConfiguration()) {
            // Configuration has critical errors - cannot continue
            debugLogger.severe("Configuration validation failed! Plugin cannot start with invalid configuration.");
            debugLogger.severe("Please fix the configuration errors listed above and restart the server.");
            
            // Disable the plugin to prevent issues
            getServer().getPluginManager().disablePlugin(this);
            throw new IllegalStateException("Plugin disabled due to invalid configuration");
        }
        
        debugLogger.info("Configuration validation completed successfully");
    }
    
    /**
     * Validates that all manager instances were created successfully.
     */
    private void validateManagers() {
        // I imagine someone will look at this one day and ask "is this really necessary?"
        // I like knowing when something is wrong on startup.
        // Stick around, and I will make a believer out of you too!
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
    }
    
    /**
     * Sets up periodic cleanup and maintenance tasks.
     */
    private void setupPeriodicTasks() {
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
    }
    
    /**
     * Sets up integrations with external plugins.
     */
    private void setupExternalIntegrations() {
        // Check for PlaceholderAPI
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            debugLogger.startup("PlaceholderAPI found! Registering placeholders...");
        } else {
            debugLogger.warn("PlaceholderAPI not found. Placeholders will not be available.");
            debugLogger.warn("Download PlaceholderAPI from: https://www.spigotmc.org/resources/placeholderapi.6245/");
        }
    }
    
    @Override
    public void onDisable() {
        try {
            // Shutdown caching systems first to ensure data persistence
            debugLogger.info("Shutting down advanced caching systems...");
            
            // Shutdown scaling optimization systems
            GameInstancePool.shutdown();
            ArenaWorldCache.shutdown();
            ConcurrentChestFiller.shutdown();
            LootTableCache.shutdown();
            debugLogger.info("Scaling optimization systems shutdown completed");
            
            // Shutdown core caching systems
            CacheManager.shutdown();
            debugLogger.info("Cache systems shutdown completed");
            
            // Shutdown Kryo serialization manager
            KryoManager.shutdown();
            debugLogger.info("Kryo serialization manager shutdown completed");
            
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
        new SGCommand(this);
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
     * Features enterprise-level multi-tier caching with Caffeine optimized for multiple concurrent games
     */
    private void initializeCachingSystems() {
        try {
            // Initialize centralized cache manager with multi-tier architecture
            CacheManager.initialize(this);
            getDebugLogger().info("Initialized CacheManager with advanced multi-tier Caffeine caching");
            
            // Initialize performance profiler with thread-safe metrics
            PerformanceProfiler.initialize(this);
            getDebugLogger().info("Initialized PerformanceProfiler with atomic counters and metrics");
            
            // Initialize scaling optimization systems
            GameInstancePool.initialize(this);
            ArenaWorldCache.initialize(this);
            getDebugLogger().info("Initialized scaling systems for 15-20 concurrent games:");
            getDebugLogger().info("  ✓ GameInstancePool - Caffeine-based game lifecycle management");
            getDebugLogger().info("  ✓ ArenaWorldCache - Multi-world arena caching system");
            
            // Initialize core caching systems (already handled by CacheManager)
            getDebugLogger().info("Initialized comprehensive caching architecture:");
            getDebugLogger().info("  ✓ PlayerDataCache - Advanced async loading with write-through");
            getDebugLogger().info("  ✓ SkinCache - High-performance skin data with 6-hour expiration");
            getDebugLogger().info("  ✓ GuiComponentCache - GUI item and leaderboard optimization");
            getDebugLogger().info("  ✓ ScoreboardCache - Scoreboard line and placeholder caching");
            getDebugLogger().info("  ✓ InvitationManager - Caffeine-based invitation system");
            
            getDebugLogger().info("All enterprise caching systems initialized successfully");
            getDebugLogger().info("ExpiringMap dependency eliminated - using pure Caffeine architecture");
            getDebugLogger().info("System optimized for high-concurrency server environments");
        } catch (Exception e) {
            getDebugLogger().error("Failed to initialize advanced caching systems", e);
            throw new IllegalStateException("Critical cache initialization failure", e);
        }
    }
    
    /**
     * Initializes chest optimization systems for concurrent game support
     */
    private void initializeChestOptimizations() {
        try {
            // Initialize concurrent chest filler with the chest manager
            ConcurrentChestFiller.initialize(this, chestManager);
            
            // Initialize loot table cache for pre-generated loot
            LootTableCache.initialize(this, chestManager);
            
            // Pre-generate loot tables asynchronously
            LootTableCache.preGenerateLootTables().thenRun(() -> {
                debugLogger.info("Pre-generated loot tables for all tiers completed");
            });
            
            // Pre-cache tier loot for concurrent chest filler
            ConcurrentChestFiller.precacheTierLoot().thenRun(() -> {
                debugLogger.info("Pre-cached tier loot for concurrent chest filling");
            });
            
            debugLogger.info("Chest optimization systems initialized:");
            debugLogger.info("  ✓ ConcurrentChestFiller - Adaptive thread pool (" + 
                ConcurrentChestFiller.getCurrentThreadPoolSize() + " threads, " +
                (ConcurrentChestFiller.isUsingAutoCalculation() ? "auto-calculated" : "configured") + ")");
            debugLogger.info("  ✓ LootTableCache - Pre-generated loot tables (50 chests per tier)");
            debugLogger.info("  ✓ Tier caching - Optimized loot distribution system");
        } catch (Exception e) {
            debugLogger.error("Failed to initialize chest optimization systems", e);
        }
    }
} 
