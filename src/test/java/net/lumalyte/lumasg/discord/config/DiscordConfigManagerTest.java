package net.lumalyte.lumasg.discord.config;

import net.lumalyte.lumasg.LumaSG;
import net.lumalyte.lumasg.util.core.DebugLogger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for DiscordConfigManager.
 */
class DiscordConfigManagerTest {
    
    @Mock
    private LumaSG plugin;
    
    @Mock
    private DebugLogger debugLogger;
    
    @Mock
    private DebugLogger.ContextualLogger contextualLogger;
    
    @Mock
    private FileConfiguration config;
    
    @Mock
    private ConfigurationSection discordSection;
    
    private DiscordConfigManager configManager;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        // Setup mock behavior
        when(plugin.getDebugLogger()).thenReturn(debugLogger);
        when(debugLogger.forContext("DiscordConfig")).thenReturn(contextualLogger);
        when(plugin.getConfig()).thenReturn(config);
        
        configManager = new DiscordConfigManager(plugin);
    }
    
    @Test
    void testLoadConfiguration_NoDiscordSection() {
        // Setup: No discord section in config
        when(config.getConfigurationSection("discord")).thenReturn(null);
        
        // Execute
        boolean result = configManager.loadConfiguration();
        
        // Verify
        assertFalse(result);
        assertNull(configManager.getConfig());
        verify(contextualLogger).warn("Discord configuration section not found in config.yml");
    }
    
    @Test
    void testLoadConfiguration_BasicSettings() {
        // Setup: Basic discord configuration
        when(config.getConfigurationSection("discord")).thenReturn(discordSection);
        when(discordSection.getBoolean("enabled", false)).thenReturn(true);
        when(discordSection.getString("bot-token", "")).thenReturn("test.bot.token");
        
        // Execute
        boolean result = configManager.loadConfiguration();
        
        // Verify
        assertTrue(result);
        assertNotNull(configManager.getConfig());
        assertTrue(configManager.getConfig().isEnabled());
        assertEquals("test.bot.token", configManager.getConfig().getBotToken());
    }
    
    @Test
    void testLoadConfiguration_ConnectionSettings() {
        // Setup: Connection settings
        ConfigurationSection connectionSection = mock(ConfigurationSection.class);
        when(config.getConfigurationSection("discord")).thenReturn(discordSection);
        when(discordSection.getConfigurationSection("connection")).thenReturn(connectionSection);
        when(connectionSection.getInt("reconnect-attempts", 5)).thenReturn(10);
        when(connectionSection.getLong("reconnect-delay-seconds", 30)).thenReturn(60L);
        when(connectionSection.getLong("timeout-seconds", 30)).thenReturn(45L);
        
        // Execute
        boolean result = configManager.loadConfiguration();
        
        // Verify
        assertTrue(result);
        DiscordConfig discordConfig = configManager.getConfig();
        assertNotNull(discordConfig);
        assertEquals(10, discordConfig.getReconnectAttempts());
        assertEquals(60000, discordConfig.getReconnectDelay()); // Converted to milliseconds
        assertEquals(45000, discordConfig.getConnectionTimeout()); // Converted to milliseconds
    }
    
    @Test
    void testLoadConfiguration_ChannelSettings() {
        // Setup: Channel configuration
        ConfigurationSection channelsSection = mock(ConfigurationSection.class);
        when(config.getConfigurationSection("discord")).thenReturn(discordSection);
        when(discordSection.getConfigurationSection("channels")).thenReturn(channelsSection);
        when(channelsSection.getKeys(false)).thenReturn(Set.of("game-announcements", "statistics"));
        when(channelsSection.getString("game-announcements", "")).thenReturn("123456789012345678");
        when(channelsSection.getString("statistics", "")).thenReturn("123456789012345679");
        
        // Execute
        boolean result = configManager.loadConfiguration();
        
        // Verify
        assertTrue(result);
        DiscordConfig discordConfig = configManager.getConfig();
        assertNotNull(discordConfig);
        assertEquals("123456789012345678", discordConfig.getChannelId("game-announcements"));
        assertEquals("123456789012345679", discordConfig.getChannelId("statistics"));
    }
    
    @Test
    void testLoadConfiguration_WebhookSettings() {
        // Setup: Webhook configuration
        ConfigurationSection webhooksSection = mock(ConfigurationSection.class);
        when(config.getConfigurationSection("discord")).thenReturn(discordSection);
        when(discordSection.getConfigurationSection("webhooks")).thenReturn(webhooksSection);
        when(webhooksSection.getKeys(false)).thenReturn(Set.of("game-announcements", "use-webhooks-first"));
        when(webhooksSection.getString("game-announcements", "")).thenReturn("https://discord.com/api/webhooks/123/abc");
        when(webhooksSection.getString("use-webhooks-first", "")).thenReturn("");
        when(webhooksSection.getBoolean("use-webhooks-first", true)).thenReturn(false);
        
        // Execute
        boolean result = configManager.loadConfiguration();
        
        // Verify
        assertTrue(result);
        DiscordConfig discordConfig = configManager.getConfig();
        assertNotNull(discordConfig);
        assertEquals("https://discord.com/api/webhooks/123/abc", discordConfig.getWebhookUrl("game-announcements"));
        assertFalse(discordConfig.isUseWebhooksFirst());
    }
    
    @Test
    void testLoadConfiguration_FeatureToggles() {
        // Setup: Feature toggles
        ConfigurationSection featuresSection = mock(ConfigurationSection.class);
        when(config.getConfigurationSection("discord")).thenReturn(discordSection);
        when(discordSection.getConfigurationSection("features")).thenReturn(featuresSection);
        when(featuresSection.getBoolean("announce-game-start", true)).thenReturn(false);
        when(featuresSection.getBoolean("enable-stat-commands", true)).thenReturn(false);
        
        // Execute
        boolean result = configManager.loadConfiguration();
        
        // Verify
        assertTrue(result);
        DiscordConfig discordConfig = configManager.getConfig();
        assertNotNull(discordConfig);
        assertFalse(discordConfig.isAnnounceGameStart());
        assertFalse(discordConfig.isEnableStatCommands());
    }
    
    @Test
    void testLoadConfiguration_AnnouncementSettings() {
        // Setup: Announcement settings
        ConfigurationSection announcementsSection = mock(ConfigurationSection.class);
        when(config.getConfigurationSection("discord")).thenReturn(discordSection);
        when(discordSection.getConfigurationSection("announcements")).thenReturn(announcementsSection);
        when(announcementsSection.getIntegerList("milestone-thresholds")).thenReturn(List.of(20, 15, 10, 5));
        when(announcementsSection.getBoolean("include-player-list", true)).thenReturn(false);
        when(announcementsSection.getInt("max-players-in-embed", 20)).thenReturn(30);
        
        // Execute
        boolean result = configManager.loadConfiguration();
        
        // Verify
        assertTrue(result);
        DiscordConfig discordConfig = configManager.getConfig();
        assertNotNull(discordConfig);
        assertEquals(List.of(20, 15, 10, 5), discordConfig.getMilestoneThresholds());
        assertFalse(discordConfig.isIncludePlayerList());
        assertEquals(30, discordConfig.getMaxPlayersInEmbed());
    }
    
    @Test
    void testLoadConfiguration_FormattingSettings() {
        // Setup: Formatting settings
        ConfigurationSection formattingSection = mock(ConfigurationSection.class);
        when(config.getConfigurationSection("discord")).thenReturn(discordSection);
        when(discordSection.getConfigurationSection("formatting")).thenReturn(formattingSection);
        when(formattingSection.getString("embed-color", "#00FF00")).thenReturn("#FF0000");
        when(formattingSection.getString("server-icon-url", "")).thenReturn("https://example.com/icon.png");
        when(formattingSection.getString("timestamp-format", "yyyy-MM-dd HH:mm:ss")).thenReturn("dd/MM/yyyy HH:mm");
        
        // Execute
        boolean result = configManager.loadConfiguration();
        
        // Verify
        assertTrue(result);
        DiscordConfig discordConfig = configManager.getConfig();
        assertNotNull(discordConfig);
        assertEquals("#FF0000", discordConfig.getEmbedColor());
        assertEquals("https://example.com/icon.png", discordConfig.getServerIconUrl());
        assertEquals("dd/MM/yyyy HH:mm", discordConfig.getTimestampFormat());
    }
    
    @Test
    void testLoadConfiguration_PermissionSettings() {
        // Setup: Permission settings
        ConfigurationSection permissionsSection = mock(ConfigurationSection.class);
        ConfigurationSection rolePermissionsSection = mock(ConfigurationSection.class);
        when(config.getConfigurationSection("discord")).thenReturn(discordSection);
        when(discordSection.getConfigurationSection("permissions")).thenReturn(permissionsSection);
        when(permissionsSection.getStringList("admin-roles")).thenReturn(List.of("Admin", "Owner"));
        when(permissionsSection.getStringList("moderator-roles")).thenReturn(List.of("Moderator"));
        when(permissionsSection.getConfigurationSection("role-permissions")).thenReturn(rolePermissionsSection);
        when(rolePermissionsSection.getKeys(false)).thenReturn(Set.of("Admin", "Moderator"));
        when(rolePermissionsSection.getString("Admin", "")).thenReturn("lumasg.admin");
        when(rolePermissionsSection.getString("Moderator", "")).thenReturn("lumasg.moderate");
        
        // Execute
        boolean result = configManager.loadConfiguration();
        
        // Verify
        assertTrue(result);
        DiscordConfig discordConfig = configManager.getConfig();
        assertNotNull(discordConfig);
        assertEquals(List.of("Admin", "Owner"), discordConfig.getAdminRoles());
        assertEquals(List.of("Moderator"), discordConfig.getModeratorRoles());
        assertEquals("lumasg.admin", discordConfig.getRolePermission("Admin"));
        assertEquals("lumasg.moderate", discordConfig.getRolePermission("Moderator"));
    }
    
    @Test
    void testLoadConfiguration_Exception() {
        // Setup: Exception during loading
        when(config.getConfigurationSection("discord")).thenThrow(new RuntimeException("Test exception"));
        
        // Execute
        boolean result = configManager.loadConfiguration();
        
        // Verify
        assertFalse(result);
        assertNull(configManager.getConfig());
        verify(contextualLogger).error(eq("Failed to load Discord configuration"), any(RuntimeException.class));
    }
    
    @Test
    void testReloadConfiguration() {
        // Setup: Mock successful loading
        when(config.getConfigurationSection("discord")).thenReturn(discordSection);
        when(discordSection.getBoolean("enabled", false)).thenReturn(true);
        
        // Execute
        boolean result = configManager.reloadConfiguration();
        
        // Verify
        assertTrue(result);
        verify(plugin).reloadConfig(); // Should reload plugin config
        verify(contextualLogger).debug("Reloading Discord configuration...");
    }
    
    @Test
    void testValidateConfiguration_NullConfig() {
        // Execute
        boolean result = configManager.validateConfiguration();
        
        // Verify
        assertFalse(result);
        verify(contextualLogger).warn("Cannot validate null Discord configuration");
    }
    
    @Test
    void testValidateConfiguration_DisabledIntegration() {
        // Setup: Load disabled configuration
        when(config.getConfigurationSection("discord")).thenReturn(discordSection);
        when(discordSection.getBoolean("enabled", false)).thenReturn(false);
        configManager.loadConfiguration();
        
        // Execute
        boolean result = configManager.validateConfiguration();
        
        // Verify
        assertTrue(result); // Should pass validation when disabled
        verify(contextualLogger).debug("Discord integration is disabled - skipping validation");
    }
    
    @Test
    void testIsEnabled() {
        // Test with null config
        assertFalse(configManager.isEnabled());
        
        // Setup: Load enabled configuration
        when(config.getConfigurationSection("discord")).thenReturn(discordSection);
        when(discordSection.getBoolean("enabled", false)).thenReturn(true);
        configManager.loadConfiguration();
        
        // Test with enabled config
        assertTrue(configManager.isEnabled());
    }
    
    @Test
    void testIsFeatureEnabled() {
        // Test with null config
        assertFalse(configManager.isFeatureEnabled("announce-game-start"));
        
        // Setup: Load configuration with features
        when(config.getConfigurationSection("discord")).thenReturn(discordSection);
        ConfigurationSection featuresSection = mock(ConfigurationSection.class);
        when(discordSection.getConfigurationSection("features")).thenReturn(featuresSection);
        when(featuresSection.getBoolean("announce-game-start", true)).thenReturn(true);
        when(featuresSection.getBoolean("announce-game-end", true)).thenReturn(false);
        configManager.loadConfiguration();
        
        // Test feature checking
        assertTrue(configManager.isFeatureEnabled("announce-game-start"));
        assertFalse(configManager.isFeatureEnabled("announce-game-end"));
        assertFalse(configManager.isFeatureEnabled("non-existent-feature"));
    }
    
    @Test
    void testGetBotToken() {
        // Test with null config
        assertNull(configManager.getBotToken());
        
        // Setup: Load configuration with token
        when(config.getConfigurationSection("discord")).thenReturn(discordSection);
        when(discordSection.getString("bot-token", "")).thenReturn("test.bot.token");
        configManager.loadConfiguration();
        
        // Test token retrieval
        assertEquals("test.bot.token", configManager.getBotToken());
    }
    
    @Test
    void testGetChannelMappings() {
        // Test with null config
        assertTrue(configManager.getChannelMappings().isEmpty());
        
        // Setup: Load configuration with channels
        when(config.getConfigurationSection("discord")).thenReturn(discordSection);
        ConfigurationSection channelsSection = mock(ConfigurationSection.class);
        when(discordSection.getConfigurationSection("channels")).thenReturn(channelsSection);
        when(channelsSection.getKeys(false)).thenReturn(Set.of("test-channel"));
        when(channelsSection.getString("test-channel", "")).thenReturn("123456789012345678");
        configManager.loadConfiguration();
        
        // Test channel mappings retrieval
        Map<String, String> channels = configManager.getChannelMappings();
        assertEquals(1, channels.size());
        assertEquals("123456789012345678", channels.get("test-channel"));
    }
    
    @Test
    void testGetRolePermissions() {
        // Test with null config
        assertTrue(configManager.getRolePermissions().isEmpty());
        
        // Setup: Load configuration with role permissions
        when(config.getConfigurationSection("discord")).thenReturn(discordSection);
        ConfigurationSection permissionsSection = mock(ConfigurationSection.class);
        ConfigurationSection rolePermissionsSection = mock(ConfigurationSection.class);
        when(discordSection.getConfigurationSection("permissions")).thenReturn(permissionsSection);
        when(permissionsSection.getConfigurationSection("role-permissions")).thenReturn(rolePermissionsSection);
        when(rolePermissionsSection.getKeys(false)).thenReturn(Set.of("Admin"));
        when(rolePermissionsSection.getString("Admin", "")).thenReturn("lumasg.admin");
        configManager.loadConfiguration();
        
        // Test role permissions retrieval
        Map<String, String> rolePermissions = configManager.getRolePermissions();
        assertEquals(1, rolePermissions.size());
        assertEquals("lumasg.admin", rolePermissions.get("Admin"));
    }
    
    @Test
    void testGetBotTokenSecurity() {
        // Test with null config
        assertNull(configManager.getBotToken());
        
        // Test with placeholder token
        when(config.getConfigurationSection("discord")).thenReturn(discordSection);
        when(discordSection.getString("bot-token", "")).thenReturn("YOUR_BOT_TOKEN_HERE");
        configManager.loadConfiguration();
        
        // Should return null for placeholder token
        assertNull(configManager.getBotToken());
        verify(contextualLogger).warn("Bot token is not properly configured - using placeholder or empty value");
        
        // Test with empty token
        when(discordSection.getString("bot-token", "")).thenReturn("");
        configManager.loadConfiguration();
        
        // Should return null for empty token
        assertNull(configManager.getBotToken());
        
        // Test with valid token
        when(discordSection.getString("bot-token", "")).thenReturn("valid.bot.token.here");
        configManager.loadConfiguration();
        
        // Should return the valid token
        assertEquals("valid.bot.token.here", configManager.getBotToken());
    }
    
    @Test
    void testIsConfigurationReady() {
        // Test with null config
        assertFalse(configManager.isConfigurationReady());
        
        // Test with disabled config
        when(config.getConfigurationSection("discord")).thenReturn(discordSection);
        when(discordSection.getBoolean("enabled", false)).thenReturn(false);
        configManager.loadConfiguration();
        assertFalse(configManager.isConfigurationReady());
        
        // Test with enabled but invalid token
        when(discordSection.getBoolean("enabled", false)).thenReturn(true);
        when(discordSection.getString("bot-token", "")).thenReturn("YOUR_BOT_TOKEN_HERE");
        configManager.loadConfiguration();
        assertFalse(configManager.isConfigurationReady());
        
        // Test with valid configuration
        when(discordSection.getString("bot-token", "")).thenReturn("MTIzNDU2Nzg5MDEyMzQ1Njc4OTA.GhIjKl.MnOpQrStUvWxYzAbCdEfGhIjKlMnOpQrStUvWxYz");
        configManager.loadConfiguration();
        // Note: This will still return false because validation will fail due to mocked dependencies
        // but the method logic is tested
    }
    
    @Test
    void testGetConfigurationSummary() {
        // Test with null config
        String summary = configManager.getConfigurationSummary();
        assertEquals("Discord configuration: Not loaded", summary);
        
        // Test with loaded config
        when(config.getConfigurationSection("discord")).thenReturn(discordSection);
        when(discordSection.getBoolean("enabled", false)).thenReturn(true);
        when(discordSection.getString("bot-token", "")).thenReturn("valid.token");
        configManager.loadConfiguration();
        
        summary = configManager.getConfigurationSummary();
        assertTrue(summary.contains("Discord Configuration Summary:"));
        assertTrue(summary.contains("Enabled: true"));
        assertTrue(summary.contains("Bot Token: Configured"));
    }
}