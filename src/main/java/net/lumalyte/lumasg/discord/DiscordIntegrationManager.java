package net.lumalyte.lumasg.discord;

import net.lumalyte.lumasg.LumaSG;
import net.lumalyte.lumasg.discord.config.DiscordConfigManager;
import net.lumalyte.lumasg.discord.connection.DiscordConnectionManager;
import net.lumalyte.lumasg.discord.announcements.DiscordAnnouncementManager;
import net.lumalyte.lumasg.discord.commands.DiscordCommandManager;
import net.lumalyte.lumasg.discord.embeds.DiscordEmbedBuilder;
import net.lumalyte.lumasg.discord.webhooks.DiscordWebhookManager;
import net.lumalyte.lumasg.util.core.DebugLogger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Main orchestrator for Discord integration functionality.
 * Coordinates all Discord-related managers and provides the primary interface
 * for Discord operations within LumaSG.
 */
public class DiscordIntegrationManager {
    
    private final @NotNull LumaSG plugin;
    private final @NotNull DebugLogger.ContextualLogger logger;
    
    // Discord managers
    private final @NotNull DiscordConfigManager configManager;
    private @Nullable DiscordConnectionManager connectionManager;
    private @Nullable DiscordAnnouncementManager announcementManager;
    private @Nullable DiscordCommandManager commandManager;
    private @Nullable DiscordEmbedBuilder embedBuilder;
    private @Nullable DiscordWebhookManager webhookManager;
    
    private boolean initialized = false;
    private boolean enabled = false;
    
    /**
     * Creates a new Discord integration manager.
     * 
     * @param plugin The plugin instance
     */
    public DiscordIntegrationManager(@NotNull LumaSG plugin) {
        this.plugin = plugin;
        this.logger = plugin.getDebugLogger().forContext("DiscordIntegration");
        this.configManager = new DiscordConfigManager(plugin);
    }
    
    /**
     * Initializes the Discord integration system.
     * This method loads configuration and sets up all Discord managers.
     * 
     * @return true if initialization was successful, false otherwise
     */
    public boolean initialize() {
        if (initialized) {
            logger.warn("Discord integration is already initialized");
            return enabled;
        }
        
        logger.debug("Initializing Discord integration...");
        
        try {
            // Load Discord configuration
            if (!configManager.loadConfiguration()) {
                logger.warn("Failed to load Discord configuration - integration disabled");
                initialized = true;
                enabled = false;
                return false;
            }
            
            // Check if Discord integration is enabled
            if (!configManager.isEnabled()) {
                logger.debug("Discord integration is disabled in configuration");
                initialized = true;
                enabled = false;
                return false;
            }
            
            // Validate configuration
            if (!configManager.validateConfiguration()) {
                logger.warn("Discord configuration validation failed - integration disabled");
                initialized = true;
                enabled = false;
                return false;
            }
            
            // Initialize Discord managers
            initializeManagers();
            
            // Attempt to connect to Discord
            if (connectionManager != null && connectionManager.connect().join()) {
                logger.info("Discord integration initialized successfully");
                initialized = true;
                enabled = true;
                return true;
            } else {
                logger.warn("Failed to connect to Discord - integration disabled");
                initialized = true;
                enabled = false;
                return false;
            }
            
        } catch (Exception e) {
            logger.error("Failed to initialize Discord integration", e);
            initialized = true;
            enabled = false;
            return false;
        }
    }
    
    /**
     * Initializes all Discord managers.
     */
    private void initializeManagers() {
        logger.debug("Initializing Discord managers...");
        
        // Initialize connection manager
        connectionManager = new DiscordConnectionManager(plugin, configManager);
        
        // Initialize embed builder
        embedBuilder = new DiscordEmbedBuilder(plugin, configManager);
        
        // Initialize webhook manager
        webhookManager = new DiscordWebhookManager(plugin, configManager);
        
        // Initialize announcement manager
        announcementManager = new DiscordAnnouncementManager(plugin, connectionManager, embedBuilder, webhookManager);
        
        // Initialize command manager
        commandManager = new DiscordCommandManager(plugin, embedBuilder);
        
        logger.debug("Discord managers initialized successfully");
    }
    
    /**
     * Shuts down the Discord integration system.
     * This method disconnects from Discord and cleans up all resources.
     */
    public void shutdown() {
        if (!initialized) {
            return;
        }
        
        logger.debug("Shutting down Discord integration...");
        
        try {
            // Shutdown managers in reverse order
            if (commandManager != null) {
                commandManager.shutdown();
            }
            
            if (announcementManager != null) {
                announcementManager.shutdown();
            }
            
            if (webhookManager != null) {
                webhookManager.shutdown();
            }
            
            if (connectionManager != null) {
                connectionManager.disconnect();
            }
            
            logger.info("Discord integration shut down successfully");
            
        } catch (Exception e) {
            logger.error("Error during Discord integration shutdown", e);
        } finally {
            initialized = false;
            enabled = false;
        }
    }
    
    /**
     * Reloads the Discord configuration and reinitializes if necessary.
     * 
     * @return true if reload was successful, false otherwise
     */
    public boolean reload() {
        logger.debug("Reloading Discord integration...");
        
        // Shutdown current integration
        shutdown();
        
        // Reinitialize
        return initialize();
    }
    
    /**
     * Checks if Discord integration is enabled and functional.
     * 
     * @return true if Discord integration is enabled, false otherwise
     */
    public boolean isEnabled() {
        return enabled;
    }
    
    /**
     * Checks if Discord integration is initialized.
     * 
     * @return true if initialized, false otherwise
     */
    public boolean isInitialized() {
        return initialized;
    }
    
    /**
     * Gets the Discord configuration manager.
     * 
     * @return The configuration manager
     */
    public @NotNull DiscordConfigManager getConfigManager() {
        return configManager;
    }
    
    /**
     * Gets the Discord connection manager.
     * 
     * @return The connection manager, or null if not initialized
     */
    public @Nullable DiscordConnectionManager getConnectionManager() {
        return connectionManager;
    }
    
    /**
     * Gets the Discord announcement manager.
     * 
     * @return The announcement manager, or null if not initialized
     */
    public @Nullable DiscordAnnouncementManager getAnnouncementManager() {
        return announcementManager;
    }
    
    /**
     * Gets the Discord command manager.
     * 
     * @return The command manager, or null if not initialized
     */
    public @Nullable DiscordCommandManager getCommandManager() {
        return commandManager;
    }
    
    /**
     * Gets the Discord embed builder.
     * 
     * @return The embed builder, or null if not initialized
     */
    public @Nullable DiscordEmbedBuilder getEmbedBuilder() {
        return embedBuilder;
    }
    
    /**
     * Gets the Discord webhook manager.
     * 
     * @return The webhook manager, or null if not initialized
     */
    public @Nullable DiscordWebhookManager getWebhookManager() {
        return webhookManager;
    }
}