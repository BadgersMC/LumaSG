package net.lumalyte.lumasg.discord.config;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Data class representing Discord integration configuration.
 * Contains all settings for Discord bot connection, channels, features, and formatting.
 */
public class DiscordConfig {
    
    // Connection settings
    private boolean enabled = false;
    private @Nullable String botToken;
    private int reconnectAttempts = 5;
    private long reconnectDelay = 30000; // 30 seconds in milliseconds
    private long connectionTimeout = 30000; // 30 seconds in milliseconds
    
    // Channel configuration
    private @NotNull Map<String, String> channelMappings = new HashMap<>();
    
    // Webhook configuration
    private @NotNull Map<String, String> webhooks = new HashMap<>();
    private boolean useWebhooksFirst = true;
    
    // Feature toggles
    private boolean announceGameStart = true;
    private boolean announceGameEnd = true;
    private boolean announceDeathmatch = true;
    private boolean announceMilestones = true;
    private boolean enableStatCommands = true;
    private boolean enableAdminCommands = true;
    private boolean enableQueueCommands = true;
    
    // Announcement settings
    private @NotNull List<Integer> milestoneThresholds = List.of(10, 5, 3, 2);
    private boolean includePlayerList = true;
    private int maxPlayersInEmbed = 20;
    private boolean usePlayerAvatars = true;
    
    // Formatting options
    private @NotNull String embedColor = "#00FF00";
    private @NotNull String serverIconUrl = "";
    private @NotNull String timestampFormat = "yyyy-MM-dd HH:mm:ss";
    
    // Permission mapping
    private @NotNull List<String> adminRoles = new ArrayList<>();
    private @NotNull List<String> moderatorRoles = new ArrayList<>();
    private @NotNull Map<String, String> rolePermissions = new HashMap<>();
    
    // Getters and setters
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    public @Nullable String getBotToken() {
        return botToken;
    }
    
    public void setBotToken(@Nullable String botToken) {
        this.botToken = botToken;
    }
    
    public int getReconnectAttempts() {
        return reconnectAttempts;
    }
    
    public void setReconnectAttempts(int reconnectAttempts) {
        this.reconnectAttempts = reconnectAttempts;
    }
    
    public long getReconnectDelay() {
        return reconnectDelay;
    }
    
    public void setReconnectDelay(long reconnectDelay) {
        this.reconnectDelay = reconnectDelay;
    }
    
    public long getConnectionTimeout() {
        return connectionTimeout;
    }
    
    public void setConnectionTimeout(long connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }
    
    public @NotNull Map<String, String> getChannelMappings() {
        return channelMappings;
    }
    
    public void setChannelMappings(@Nullable Map<String, String> channelMappings) {
        this.channelMappings = channelMappings != null ? channelMappings : new HashMap<>();
    }
    
    public @NotNull Map<String, String> getWebhooks() {
        return webhooks;
    }
    
    public void setWebhooks(@Nullable Map<String, String> webhooks) {
        this.webhooks = webhooks != null ? webhooks : new HashMap<>();
    }
    
    public boolean isUseWebhooksFirst() {
        return useWebhooksFirst;
    }
    
    public void setUseWebhooksFirst(boolean useWebhooksFirst) {
        this.useWebhooksFirst = useWebhooksFirst;
    }
    
    public boolean isAnnounceGameStart() {
        return announceGameStart;
    }
    
    public void setAnnounceGameStart(boolean announceGameStart) {
        this.announceGameStart = announceGameStart;
    }
    
    public boolean isAnnounceGameEnd() {
        return announceGameEnd;
    }
    
    public void setAnnounceGameEnd(boolean announceGameEnd) {
        this.announceGameEnd = announceGameEnd;
    }
    
    public boolean isAnnounceDeathmatch() {
        return announceDeathmatch;
    }
    
    public void setAnnounceDeathmatch(boolean announceDeathmatch) {
        this.announceDeathmatch = announceDeathmatch;
    }
    
    public boolean isAnnounceMilestones() {
        return announceMilestones;
    }
    
    public void setAnnounceMilestones(boolean announceMilestones) {
        this.announceMilestones = announceMilestones;
    }
    
    public boolean isEnableStatCommands() {
        return enableStatCommands;
    }
    
    public void setEnableStatCommands(boolean enableStatCommands) {
        this.enableStatCommands = enableStatCommands;
    }
    
    public boolean isEnableAdminCommands() {
        return enableAdminCommands;
    }
    
    public void setEnableAdminCommands(boolean enableAdminCommands) {
        this.enableAdminCommands = enableAdminCommands;
    }
    
    public boolean isEnableQueueCommands() {
        return enableQueueCommands;
    }
    
    public void setEnableQueueCommands(boolean enableQueueCommands) {
        this.enableQueueCommands = enableQueueCommands;
    }
    
    public @NotNull List<Integer> getMilestoneThresholds() {
        return milestoneThresholds;
    }
    
    public void setMilestoneThresholds(@NotNull List<Integer> milestoneThresholds) {
        this.milestoneThresholds = milestoneThresholds;
    }
    
    public boolean isIncludePlayerList() {
        return includePlayerList;
    }
    
    public void setIncludePlayerList(boolean includePlayerList) {
        this.includePlayerList = includePlayerList;
    }
    
    public int getMaxPlayersInEmbed() {
        return maxPlayersInEmbed;
    }
    
    public void setMaxPlayersInEmbed(int maxPlayersInEmbed) {
        this.maxPlayersInEmbed = maxPlayersInEmbed;
    }
    
    public boolean isUsePlayerAvatars() {
        return usePlayerAvatars;
    }
    
    public void setUsePlayerAvatars(boolean usePlayerAvatars) {
        this.usePlayerAvatars = usePlayerAvatars;
    }
    
    public @NotNull String getEmbedColor() {
        return embedColor;
    }
    
    public void setEmbedColor(@NotNull String embedColor) {
        this.embedColor = embedColor;
    }
    
    public @NotNull String getServerIconUrl() {
        return serverIconUrl;
    }
    
    public void setServerIconUrl(@NotNull String serverIconUrl) {
        this.serverIconUrl = serverIconUrl;
    }
    
    public @NotNull String getTimestampFormat() {
        return timestampFormat;
    }
    
    public void setTimestampFormat(@NotNull String timestampFormat) {
        this.timestampFormat = timestampFormat;
    }
    
    public @NotNull List<String> getAdminRoles() {
        return adminRoles;
    }
    
    public void setAdminRoles(@Nullable List<String> adminRoles) {
        this.adminRoles = adminRoles != null ? adminRoles : new ArrayList<>();
    }
    
    public @NotNull List<String> getModeratorRoles() {
        return moderatorRoles;
    }
    
    public void setModeratorRoles(@Nullable List<String> moderatorRoles) {
        this.moderatorRoles = moderatorRoles != null ? moderatorRoles : new ArrayList<>();
    }
    
    public @NotNull Map<String, String> getRolePermissions() {
        return rolePermissions;
    }
    
    public void setRolePermissions(@Nullable Map<String, String> rolePermissions) {
        this.rolePermissions = rolePermissions != null ? rolePermissions : new HashMap<>();
    }
    
    /**
     * Gets a channel ID by name.
     * 
     * @param channelName The channel name
     * @return The channel ID, or null if not found
     */
    public @Nullable String getChannelId(@NotNull String channelName) {
        return channelMappings.get(channelName);
    }
    
    /**
     * Gets a webhook URL by name.
     * 
     * @param webhookName The webhook name
     * @return The webhook URL, or null if not found
     */
    public @Nullable String getWebhookUrl(@NotNull String webhookName) {
        return webhooks.get(webhookName);
    }
    
    /**
     * Checks if a Discord role has admin permissions.
     * 
     * @param roleName The role name
     * @return true if the role has admin permissions, false otherwise
     */
    public boolean isAdminRole(@NotNull String roleName) {
        return adminRoles.contains(roleName);
    }
    
    /**
     * Checks if a Discord role has moderator permissions.
     * 
     * @param roleName The role name
     * @return true if the role has moderator permissions, false otherwise
     */
    public boolean isModeratorRole(@NotNull String roleName) {
        return moderatorRoles.contains(roleName);
    }
    
    /**
     * Gets the plugin permission for a Discord role.
     * 
     * @param roleName The role name
     * @return The plugin permission, or null if not mapped
     */
    public @Nullable String getRolePermission(@NotNull String roleName) {
        return rolePermissions.get(roleName);
    }
    
    /**
     * Validates that the bot token is not a placeholder or empty.
     * This helps prevent common configuration mistakes.
     * 
     * @return true if the bot token appears to be valid, false otherwise
     */
    public boolean isValidBotToken() {
        if (botToken == null || botToken.isEmpty()) {
            return false;
        }
        
        // Check for common placeholder values
        return !botToken.contains("DISCORD_BOT_TOKEN") && 
               !botToken.contains("REPLACE_WITH_YOUR_TOKEN") &&
               !botToken.isEmpty() &&
               botToken.length() >= 50; // Discord bot tokens are typically 59+ characters
    }
    
    /**
     * Checks if any channels are configured for announcements.
     * 
     * @return true if at least one channel is configured, false otherwise
     */
    public boolean hasConfiguredChannels() {
        return !channelMappings.isEmpty() && 
               channelMappings.values().stream().anyMatch(id -> id != null && !id.isEmpty());
    }
    
    /**
     * Checks if any webhooks are configured.
     * 
     * @return true if at least one webhook is configured, false otherwise
     */
    public boolean hasConfiguredWebhooks() {
        return !webhooks.isEmpty() && 
               webhooks.values().stream().anyMatch(url -> url != null && !url.isEmpty());
    }
    
    /**
     * Checks if any admin roles are configured.
     * 
     * @return true if at least one admin role is configured, false otherwise
     */
    public boolean hasAdminRoles() {
        return !adminRoles.isEmpty();
    }
    
    /**
     * Gets a list of all configured channel names.
     * 
     * @return A list of channel names that have non-empty IDs
     */
    public @NotNull List<String> getConfiguredChannelNames() {
        return channelMappings.entrySet().stream()
                .filter(entry -> entry.getValue() != null && !entry.getValue().isEmpty())
                .map(Map.Entry::getKey)
                .toList();
    }
    
    /**
     * Gets a list of all configured webhook names.
     * 
     * @return A list of webhook names that have non-empty URLs
     */
    public @NotNull List<String> getConfiguredWebhookNames() {
        return webhooks.entrySet().stream()
                .filter(entry -> entry.getValue() != null && !entry.getValue().isEmpty())
                .map(Map.Entry::getKey)
                .toList();
    }
}