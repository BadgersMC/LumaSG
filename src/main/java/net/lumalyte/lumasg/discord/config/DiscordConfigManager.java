package net.lumalyte.lumasg.discord.config;

import net.lumalyte.lumasg.LumaSG;
import net.lumalyte.lumasg.util.core.DebugLogger;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;

import java.util.Map;

/**
 * Manages Discord-specific configuration with validation and hot-reloading.
 * Handles secure token storage and configuration validation.
 */
public class DiscordConfigManager {
    
    private final @NotNull LumaSG plugin;
    private final @NotNull DebugLogger.ContextualLogger logger;
    
    private @Nullable DiscordConfig config;
    
    /**
     * Creates a new Discord configuration manager.
     * 
     * @param plugin The plugin instance
     */
    public DiscordConfigManager(@NotNull LumaSG plugin) {
        this.plugin = plugin;
        this.logger = plugin.getDebugLogger().forContext("DiscordConfig");
    }
    
    /**
     * Loads the Discord configuration from the plugin's config file.
     * 
     * @return true if configuration was loaded successfully, false otherwise
     */
    public boolean loadConfiguration() {
        try {
            logger.debug("Loading Discord configuration...");
            
            // Reload plugin configuration to get latest values
            plugin.reloadConfig();
            
            ConfigurationSection discordSection = plugin.getConfig().getConfigurationSection("discord");
            if (discordSection == null) {
                logger.warn("Discord configuration section not found in config.yml");
                return false;
            }
            
            // Create new configuration object
            config = new DiscordConfig();
            
            // Load basic settings
            config.setEnabled(discordSection.getBoolean("enabled", false));
            config.setBotToken(discordSection.getString("bot-token", ""));
            
            // Load connection settings
            ConfigurationSection connectionSection = discordSection.getConfigurationSection("connection");
            if (connectionSection != null) {
                config.setReconnectAttempts(connectionSection.getInt("reconnect-attempts", 5));
                config.setReconnectDelay(connectionSection.getLong("reconnect-delay-seconds", 30) * 1000);
                config.setConnectionTimeout(connectionSection.getLong("timeout-seconds", 30) * 1000);
            }
            
            // Load channel configuration
            ConfigurationSection channelsSection = discordSection.getConfigurationSection("channels");
            if (channelsSection != null) {
                Map<String, String> channels = new HashMap<>();
                for (String key : channelsSection.getKeys(false)) {
                    String channelId = channelsSection.getString(key, "");
                    if (!channelId.isEmpty()) {
                        channels.put(key, channelId);
                    }
                }
                config.setChannelMappings(channels);
            }
            
            // Load webhook configuration
            ConfigurationSection webhooksSection = discordSection.getConfigurationSection("webhooks");
            if (webhooksSection != null) {
                Map<String, String> webhooks = new HashMap<>();
                for (String key : webhooksSection.getKeys(false)) {
                    String webhookUrl = webhooksSection.getString(key, "");
                    if (!webhookUrl.isEmpty() && !key.equals("use-webhooks-first")) {
                        webhooks.put(key, webhookUrl);
                    }
                }
                config.setWebhooks(webhooks);
                config.setUseWebhooksFirst(webhooksSection.getBoolean("use-webhooks-first", true));
            }
            
            // Load feature toggles
            ConfigurationSection featuresSection = discordSection.getConfigurationSection("features");
            if (featuresSection != null) {
                config.setAnnounceGameStart(featuresSection.getBoolean("announce-game-start", true));
                config.setAnnounceGameEnd(featuresSection.getBoolean("announce-game-end", true));
                config.setAnnounceDeathmatch(featuresSection.getBoolean("announce-deathmatch", true));
                config.setAnnounceMilestones(featuresSection.getBoolean("announce-milestones", true));
                config.setEnableStatCommands(featuresSection.getBoolean("enable-stat-commands", true));
                config.setEnableAdminCommands(featuresSection.getBoolean("enable-admin-commands", true));
                config.setEnableQueueCommands(featuresSection.getBoolean("enable-queue-commands", true));
            }
            
            // Load announcement settings
            ConfigurationSection announcementsSection = discordSection.getConfigurationSection("announcements");
            if (announcementsSection != null) {
                config.setMilestoneThresholds(announcementsSection.getIntegerList("milestone-thresholds"));
                config.setIncludePlayerList(announcementsSection.getBoolean("include-player-list", true));
                config.setMaxPlayersInEmbed(announcementsSection.getInt("max-players-in-embed", 20));
                config.setUsePlayerAvatars(announcementsSection.getBoolean("use-player-avatars", true));
            }
            
            // Load formatting settings
            ConfigurationSection formattingSection = discordSection.getConfigurationSection("formatting");
            if (formattingSection != null) {
                config.setEmbedColor(formattingSection.getString("embed-color", "#00FF00"));
                config.setServerIconUrl(formattingSection.getString("server-icon-url", ""));
                config.setTimestampFormat(formattingSection.getString("timestamp-format", "yyyy-MM-dd HH:mm:ss"));
            }
            
            // Load permission settings
            ConfigurationSection permissionsSection = discordSection.getConfigurationSection("permissions");
            if (permissionsSection != null) {
                config.setAdminRoles(permissionsSection.getStringList("admin-roles"));
                config.setModeratorRoles(permissionsSection.getStringList("moderator-roles"));
                
                ConfigurationSection rolePermissionsSection = permissionsSection.getConfigurationSection("role-permissions");
                if (rolePermissionsSection != null) {
                    Map<String, String> rolePermissions = new HashMap<>();
                    for (String role : rolePermissionsSection.getKeys(false)) {
                        String permission = rolePermissionsSection.getString(role, "");
                        if (!permission.isEmpty()) {
                            rolePermissions.put(role, permission);
                        }
                    }
                    config.setRolePermissions(rolePermissions);
                }
            }
            
            logger.debug("Discord configuration loaded successfully");
            return true;
            
        } catch (Exception e) {
            logger.error("Failed to load Discord configuration", e);
            return false;
        }
    }
    
    /**
     * Reloads the Discord configuration.
     * 
     * @return true if configuration was reloaded successfully, false otherwise
     */
    public boolean reloadConfiguration() {
        logger.debug("Reloading Discord configuration...");
        return loadConfiguration();
    }
    
    /**
     * Validates the current Discord configuration.
     * 
     * @return true if configuration is valid, false otherwise
     */
    public boolean validateConfiguration() {
        if (config == null) {
            logger.warn("Cannot validate null Discord configuration");
            return false;
        }
        
        if (!config.isEnabled()) {
            logger.debug("Discord integration is disabled - skipping validation");
            return true;
        }
        
        // Use the comprehensive validation framework
        net.lumalyte.lumasg.discord.validation.DiscordConfigValidator validator = 
            new net.lumalyte.lumasg.discord.validation.DiscordConfigValidator(logger);
        
        net.lumalyte.lumasg.discord.validation.DiscordConfigValidator.ValidationResult result = 
            validator.validateConfiguration(config);
        
        // Log validation results
        if (!result.getErrors().isEmpty()) {
            logger.error("Discord configuration validation failed:");
            for (String error : result.getErrors()) {
                logger.error("  - " + error);
            }
        }
        
        if (!result.getWarnings().isEmpty()) {
            logger.warn("Discord configuration validation warnings:");
            for (String warning : result.getWarnings()) {
                logger.warn("  - " + warning);
            }
        }
        
        if (!result.getInfo().isEmpty()) {
            logger.debug("Discord configuration validation info:");
            for (String info : result.getInfo()) {
                logger.debug("  - " + info);
            }
        }
        
        // Apply automatic fixes for common issues
        applyConfigurationFixes(result);
        
        boolean isValid = result.isValid();
        if (isValid) {
            logger.debug("Discord configuration validation completed successfully");
        } else {
            logger.error("Discord configuration validation failed - integration will be disabled");
        }
        
        return isValid;
    }
    
    /**
     * Applies automatic fixes for common configuration issues.
     * 
     * @param result The validation result containing errors and warnings
     */
    private void applyConfigurationFixes(@NotNull net.lumalyte.lumasg.discord.validation.DiscordConfigValidator.ValidationResult result) {
        if (config == null) {
            return;
        }
        
        // Fix connection settings
        if (config.getReconnectAttempts() < 1) {
            logger.warn("Discord reconnect attempts is less than 1, setting to 1");
            config.setReconnectAttempts(1);
        }
        
        if (config.getReconnectDelay() < 1000) {
            logger.warn("Discord reconnect delay is less than 1 second, setting to 1 second");
            config.setReconnectDelay(1000);
        }
        
        if (config.getConnectionTimeout() < 5000) {
            logger.warn("Discord connection timeout is less than 5 seconds, setting to 5 seconds");
            config.setConnectionTimeout(5000);
        }
        
        // Fix embed settings
        if (config.getMaxPlayersInEmbed() < 1) {
            logger.warn("Max players in embed is less than 1, setting to 20");
            config.setMaxPlayersInEmbed(20);
        }
        
        // Fix embed color format
        String embedColor = config.getEmbedColor();
        if (embedColor != null && !embedColor.matches("^#[0-9A-Fa-f]{6}$")) {
            logger.warn("Invalid embed color format: " + embedColor + ", using default #00FF00");
            config.setEmbedColor("#00FF00");
        }
        
        // Fix empty timestamp format
        if (config.getTimestampFormat() == null || config.getTimestampFormat().isEmpty()) {
            logger.warn("Empty timestamp format, using default");
            config.setTimestampFormat("yyyy-MM-dd HH:mm:ss");
        }
    }
    
    /**
     * Gets the current Discord configuration.
     * 
     * @return The Discord configuration, or null if not loaded
     */
    public @Nullable DiscordConfig getConfig() {
        return config;
    }
    
    /**
     * Checks if Discord integration is enabled.
     * 
     * @return true if enabled, false otherwise
     */
    public boolean isEnabled() {
        return config != null && config.isEnabled();
    }
    
    /**
     * Checks if a specific feature is enabled.
     * 
     * @param feature The feature name
     * @return true if the feature is enabled, false otherwise
     */
    public boolean isFeatureEnabled(@NotNull String feature) {
        if (config == null) {
            return false;
        }
        
        return switch (feature.toLowerCase()) {
            case "announce-game-start" -> config.isAnnounceGameStart();
            case "announce-game-end" -> config.isAnnounceGameEnd();
            case "announce-deathmatch" -> config.isAnnounceDeathmatch();
            case "announce-milestones" -> config.isAnnounceMilestones();
            case "stat-commands" -> config.isEnableStatCommands();
            case "admin-commands" -> config.isEnableAdminCommands();
            case "queue-commands" -> config.isEnableQueueCommands();
            default -> false;
        };
    }
    
    /**
     * Gets the bot token securely (never logged).
     * This method ensures the bot token is never exposed in logs or debug output.
     * 
     * @return The bot token, or null if not configured
     */
    public @Nullable String getBotToken() {
        if (config == null) {
            return null;
        }
        
        String token = config.getBotToken();
        
        // Additional security: Never return placeholder tokens
        if (token != null && (token.equals("YOUR_BOT_TOKEN_HERE") || token.isEmpty())) {
            logger.warn("Bot token is not properly configured - using placeholder or empty value");
            return null;
        }
        
        return token;
    }
    
    /**
     * Gets the channel mappings.
     * 
     * @return A map of channel names to channel IDs
     */
    public @NotNull Map<String, String> getChannelMappings() {
        return config != null ? config.getChannelMappings() : new HashMap<>();
    }
    
    /**
     * Gets the role permissions mapping.
     * 
     * @return A map of Discord roles to plugin permissions
     */
    public @NotNull Map<String, String> getRolePermissions() {
        return config != null ? config.getRolePermissions() : new HashMap<>();
    }
    
    /**
     * Reloads configuration and notifies listeners of changes.
     * This method supports hot-reloading by comparing old and new configurations
     * and triggering appropriate updates.
     * 
     * @return true if configuration was reloaded successfully, false otherwise
     */
    public boolean reloadConfigurationWithNotification() {
        logger.debug("Reloading Discord configuration with change notification...");
        
        // Store old configuration for comparison
        DiscordConfig oldConfig = config;
        
        // Reload configuration
        boolean reloadSuccess = reloadConfiguration();
        
        if (reloadSuccess && oldConfig != null && config != null) {
            // Check for significant changes that require reconnection
            boolean requiresReconnection = hasSignificantChanges(oldConfig, config);
            
            if (requiresReconnection) {
                logger.info("Discord configuration changes require reconnection");
                // The DiscordIntegrationManager will handle the reconnection
            } else {
                logger.debug("Discord configuration reloaded with minor changes");
            }
        }
        
        return reloadSuccess;
    }
    
    /**
     * Checks if the configuration changes require a Discord reconnection.
     * 
     * @param oldConfig The previous configuration
     * @param newConfig The new configuration
     * @return true if reconnection is required, false otherwise
     */
    private boolean hasSignificantChanges(@NotNull DiscordConfig oldConfig, @NotNull DiscordConfig newConfig) {
        // Check if Discord was enabled/disabled
        if (oldConfig.isEnabled() != newConfig.isEnabled()) {
            return true;
        }
        
        // Check if bot token changed
        String oldToken = oldConfig.getBotToken();
        String newToken = newConfig.getBotToken();
        if ((oldToken == null && newToken != null) || 
            (oldToken != null && !oldToken.equals(newToken))) {
            return true;
        }
        
        // Check if connection settings changed
        if (oldConfig.getReconnectAttempts() != newConfig.getReconnectAttempts() ||
            oldConfig.getReconnectDelay() != newConfig.getReconnectDelay() ||
            oldConfig.getConnectionTimeout() != newConfig.getConnectionTimeout()) {
            return true;
        }
        
        // Other changes (channels, features, formatting) don't require reconnection
        return false;
    }
    
    /**
     * Checks if the Discord configuration is properly initialized and ready for use.
     * This performs comprehensive validation beyond basic loading.
     * 
     * @return true if configuration is ready for Discord operations, false otherwise
     */
    public boolean isConfigurationReady() {
        if (config == null) {
            logger.debug("Discord configuration is not loaded");
            return false;
        }
        
        if (!config.isEnabled()) {
            logger.debug("Discord integration is disabled");
            return false;
        }
        
        // Check if bot token is properly configured
        String botToken = getBotToken();
        if (botToken == null) {
            logger.warn("Discord bot token is not properly configured");
            return false;
        }
        
        // Validate configuration
        if (!validateConfiguration()) {
            logger.warn("Discord configuration validation failed");
            return false;
        }
        
        logger.debug("Discord configuration is ready for use");
        return true;
    }
    
    /**
     * Gets a summary of the current configuration status.
     * This is useful for debugging and admin commands.
     * 
     * @return A formatted summary of the configuration status
     */
    public @NotNull String getConfigurationSummary() {
        if (config == null) {
            return "Discord configuration: Not loaded";
        }
        
        StringBuilder summary = new StringBuilder();
        summary.append("Discord Configuration Summary:\n");
        summary.append("  Enabled: ").append(config.isEnabled()).append("\n");
        summary.append("  Bot Token: ").append(getBotToken() != null ? "Configured" : "Not configured").append("\n");
        summary.append("  Channels: ").append(config.getChannelMappings().size()).append(" configured\n");
        summary.append("  Webhooks: ").append(config.getWebhooks().size()).append(" configured\n");
        summary.append("  Admin Roles: ").append(config.getAdminRoles().size()).append(" configured\n");
        summary.append("  Moderator Roles: ").append(config.getModeratorRoles().size()).append(" configured\n");
        summary.append("  Role Permissions: ").append(config.getRolePermissions().size()).append(" configured\n");
        
        // Feature status
        summary.append("  Features:\n");
        summary.append("    Game Announcements: ").append(config.isAnnounceGameStart()).append("\n");
        summary.append("    Stat Commands: ").append(config.isEnableStatCommands()).append("\n");
        summary.append("    Admin Commands: ").append(config.isEnableAdminCommands()).append("\n");
        
        return summary.toString();
    }
}