package net.lumalyte.lumasg.discord.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DiscordConfig data class.
 */
class DiscordConfigTest {
    
    private DiscordConfig config;
    
    @BeforeEach
    void setUp() {
        config = new DiscordConfig();
    }
    
    @Test
    void testDefaultValues() {
        // Test default connection settings
        assertFalse(config.isEnabled());
        assertNull(config.getBotToken());
        assertEquals(5, config.getReconnectAttempts());
        assertEquals(30000, config.getReconnectDelay());
        assertEquals(30000, config.getConnectionTimeout());
        
        // Test default feature toggles
        assertTrue(config.isAnnounceGameStart());
        assertTrue(config.isAnnounceGameEnd());
        assertTrue(config.isAnnounceDeathmatch());
        assertTrue(config.isAnnounceMilestones());
        assertTrue(config.isEnableStatCommands());
        assertTrue(config.isEnableAdminCommands());
        assertTrue(config.isEnableQueueCommands());
        
        // Test default announcement settings
        assertEquals(List.of(10, 5, 3, 2), config.getMilestoneThresholds());
        assertTrue(config.isIncludePlayerList());
        assertEquals(20, config.getMaxPlayersInEmbed());
        assertTrue(config.isUsePlayerAvatars());
        
        // Test default formatting
        assertEquals("#00FF00", config.getEmbedColor());
        assertEquals("", config.getServerIconUrl());
        assertEquals("yyyy-MM-dd HH:mm:ss", config.getTimestampFormat());
        
        // Test default collections
        assertTrue(config.getChannelMappings().isEmpty());
        assertTrue(config.getWebhooks().isEmpty());
        assertTrue(config.getAdminRoles().isEmpty());
        assertTrue(config.getModeratorRoles().isEmpty());
        assertTrue(config.getRolePermissions().isEmpty());
    }
    
    @Test
    void testConnectionSettings() {
        // Test enabled setting
        config.setEnabled(true);
        assertTrue(config.isEnabled());
        
        // Test bot token
        String testToken = "test.token.here";
        config.setBotToken(testToken);
        assertEquals(testToken, config.getBotToken());
        
        // Test reconnect attempts
        config.setReconnectAttempts(10);
        assertEquals(10, config.getReconnectAttempts());
        
        // Test reconnect delay
        config.setReconnectDelay(60000);
        assertEquals(60000, config.getReconnectDelay());
        
        // Test connection timeout
        config.setConnectionTimeout(45000);
        assertEquals(45000, config.getConnectionTimeout());
    }
    
    @Test
    void testChannelMappings() {
        Map<String, String> channels = new HashMap<>();
        channels.put("game-announcements", "123456789012345678");
        channels.put("statistics", "123456789012345679");
        
        config.setChannelMappings(channels);
        assertEquals(channels, config.getChannelMappings());
        
        // Test channel ID retrieval
        assertEquals("123456789012345678", config.getChannelId("game-announcements"));
        assertEquals("123456789012345679", config.getChannelId("statistics"));
        assertNull(config.getChannelId("non-existent"));
    }
    
    @Test
    void testWebhookConfiguration() {
        Map<String, String> webhooks = new HashMap<>();
        webhooks.put("game-announcements", "https://discord.com/api/webhooks/123/abc");
        webhooks.put("statistics", "https://discord.com/api/webhooks/456/def");
        
        config.setWebhooks(webhooks);
        assertEquals(webhooks, config.getWebhooks());
        
        // Test webhook URL retrieval
        assertEquals("https://discord.com/api/webhooks/123/abc", config.getWebhookUrl("game-announcements"));
        assertEquals("https://discord.com/api/webhooks/456/def", config.getWebhookUrl("statistics"));
        assertNull(config.getWebhookUrl("non-existent"));
        
        // Test use webhooks first setting
        config.setUseWebhooksFirst(false);
        assertFalse(config.isUseWebhooksFirst());
    }
    
    @Test
    void testFeatureToggles() {
        // Test game announcement toggles
        config.setAnnounceGameStart(false);
        assertFalse(config.isAnnounceGameStart());
        
        config.setAnnounceGameEnd(false);
        assertFalse(config.isAnnounceGameEnd());
        
        config.setAnnounceDeathmatch(false);
        assertFalse(config.isAnnounceDeathmatch());
        
        config.setAnnounceMilestones(false);
        assertFalse(config.isAnnounceMilestones());
        
        // Test command toggles
        config.setEnableStatCommands(false);
        assertFalse(config.isEnableStatCommands());
        
        config.setEnableAdminCommands(false);
        assertFalse(config.isEnableAdminCommands());
        
        config.setEnableQueueCommands(false);
        assertFalse(config.isEnableQueueCommands());
    }
    
    @Test
    void testAnnouncementSettings() {
        // Test milestone thresholds
        List<Integer> thresholds = List.of(20, 15, 10, 5, 2);
        config.setMilestoneThresholds(thresholds);
        assertEquals(thresholds, config.getMilestoneThresholds());
        
        // Test include player list
        config.setIncludePlayerList(false);
        assertFalse(config.isIncludePlayerList());
        
        // Test max players in embed
        config.setMaxPlayersInEmbed(30);
        assertEquals(30, config.getMaxPlayersInEmbed());
        
        // Test use player avatars
        config.setUsePlayerAvatars(false);
        assertFalse(config.isUsePlayerAvatars());
    }
    
    @Test
    void testFormattingSettings() {
        // Test embed color
        config.setEmbedColor("#FF0000");
        assertEquals("#FF0000", config.getEmbedColor());
        
        // Test server icon URL
        String iconUrl = "https://example.com/icon.png";
        config.setServerIconUrl(iconUrl);
        assertEquals(iconUrl, config.getServerIconUrl());
        
        // Test timestamp format
        String timestampFormat = "dd/MM/yyyy HH:mm";
        config.setTimestampFormat(timestampFormat);
        assertEquals(timestampFormat, config.getTimestampFormat());
    }
    
    @Test
    void testPermissionSettings() {
        // Test admin roles
        List<String> adminRoles = List.of("Admin", "Owner", "Developer");
        config.setAdminRoles(adminRoles);
        assertEquals(adminRoles, config.getAdminRoles());
        
        // Test admin role checking
        assertTrue(config.isAdminRole("Admin"));
        assertTrue(config.isAdminRole("Owner"));
        assertFalse(config.isAdminRole("Moderator"));
        
        // Test moderator roles
        List<String> moderatorRoles = List.of("Moderator", "Staff");
        config.setModeratorRoles(moderatorRoles);
        assertEquals(moderatorRoles, config.getModeratorRoles());
        
        // Test moderator role checking
        assertTrue(config.isModeratorRole("Moderator"));
        assertTrue(config.isModeratorRole("Staff"));
        assertFalse(config.isModeratorRole("Admin"));
        
        // Test role permissions
        Map<String, String> rolePermissions = new HashMap<>();
        rolePermissions.put("Admin", "lumasg.admin");
        rolePermissions.put("Moderator", "lumasg.moderate");
        config.setRolePermissions(rolePermissions);
        assertEquals(rolePermissions, config.getRolePermissions());
        
        // Test role permission retrieval
        assertEquals("lumasg.admin", config.getRolePermission("Admin"));
        assertEquals("lumasg.moderate", config.getRolePermission("Moderator"));
        assertNull(config.getRolePermission("User"));
    }
    
    @Test
    void testNullHandling() {
        // Test null bot token
        config.setBotToken(null);
        assertNull(config.getBotToken());
        
        // Test null channel mappings
        config.setChannelMappings(null);
        assertNotNull(config.getChannelMappings());
        
        // Test null webhooks
        config.setWebhooks(null);
        assertNotNull(config.getWebhooks());
        
        // Test null role lists
        config.setAdminRoles(null);
        assertNotNull(config.getAdminRoles());
        
        config.setModeratorRoles(null);
        assertNotNull(config.getModeratorRoles());
        
        // Test null role permissions
        config.setRolePermissions(null);
        assertNotNull(config.getRolePermissions());
    }
    
    @Test
    void testBotTokenValidation() {
        // Test null/empty tokens
        config.setBotToken(null);
        assertFalse(config.isValidBotToken());
        
        config.setBotToken("");
        assertFalse(config.isValidBotToken());
        
        // Test placeholder tokens
        config.setBotToken("YOUR_BOT_TOKEN_HERE");
        assertFalse(config.isValidBotToken());
        
        config.setBotToken("your_bot_token_here");
        assertFalse(config.isValidBotToken());
        
        config.setBotToken("REPLACE_WITH_YOUR_TOKEN");
        assertFalse(config.isValidBotToken());
        
        // Test short token
        config.setBotToken("short.token");
        assertFalse(config.isValidBotToken());
        
        // Test valid token (50+ characters)
        config.setBotToken("MTIzNDU2Nzg5MDEyMzQ1Njc4OTA.GhIjKl.MnOpQrStUvWxYzAbCdEfGhIjKlMnOpQrStUvWxYz");
        assertTrue(config.isValidBotToken());
    }
    
    @Test
    void testConfigurationChecks() {
        // Test empty configuration
        assertFalse(config.hasConfiguredChannels());
        assertFalse(config.hasConfiguredWebhooks());
        assertFalse(config.hasAdminRoles());
        assertTrue(config.getConfiguredChannelNames().isEmpty());
        assertTrue(config.getConfiguredWebhookNames().isEmpty());
        
        // Test with configured channels
        Map<String, String> channels = new HashMap<>();
        channels.put("announcements", "123456789012345678");
        channels.put("empty-channel", "");
        channels.put("null-channel", null);
        config.setChannelMappings(channels);
        
        assertTrue(config.hasConfiguredChannels());
        assertEquals(List.of("announcements"), config.getConfiguredChannelNames());
        
        // Test with configured webhooks
        Map<String, String> webhooks = new HashMap<>();
        webhooks.put("webhook1", "https://discord.com/api/webhooks/123/abc");
        webhooks.put("empty-webhook", "");
        config.setWebhooks(webhooks);
        
        assertTrue(config.hasConfiguredWebhooks());
        assertEquals(List.of("webhook1"), config.getConfiguredWebhookNames());
        
        // Test with admin roles
        config.setAdminRoles(List.of("Admin", "Owner"));
        assertTrue(config.hasAdminRoles());
    }
}