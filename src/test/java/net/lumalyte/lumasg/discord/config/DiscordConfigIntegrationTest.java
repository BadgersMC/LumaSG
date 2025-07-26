package net.lumalyte.lumasg.discord.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for Discord configuration functionality.
 * Tests the complete configuration loading and validation flow.
 */
class DiscordConfigIntegrationTest {
    
    private DiscordConfig config;
    
    @BeforeEach
    void setUp() {
        config = new DiscordConfig();
    }
    
    @Test
    void testCompleteConfigurationFlow() {
        // Test initial state
        assertFalse(config.isEnabled());
        assertNull(config.getBotToken());
        
        // Configure Discord integration
        config.setEnabled(true);
        config.setBotToken("test.bot.token.here");
        
        // Configure connection settings
        config.setReconnectAttempts(3);
        config.setReconnectDelay(15000);
        config.setConnectionTimeout(20000);
        
        // Configure channels
        Map<String, String> channels = new HashMap<>();
        channels.put("game-announcements", "123456789012345678");
        channels.put("statistics", "123456789012345679");
        config.setChannelMappings(channels);
        
        // Configure webhooks
        Map<String, String> webhooks = new HashMap<>();
        webhooks.put("game-announcements", "https://discord.com/api/webhooks/123/abc");
        config.setWebhooks(webhooks);
        config.setUseWebhooksFirst(true);
        
        // Configure features
        config.setAnnounceGameStart(true);
        config.setAnnounceGameEnd(true);
        config.setAnnounceDeathmatch(true);
        config.setEnableStatCommands(true);
        
        // Configure announcements
        config.setMilestoneThresholds(List.of(15, 10, 5, 2));
        config.setMaxPlayersInEmbed(25);
        config.setIncludePlayerList(true);
        
        // Configure formatting
        config.setEmbedColor("#FF5500");
        config.setServerIconUrl("https://example.com/icon.png");
        config.setTimestampFormat("dd/MM/yyyy HH:mm:ss");
        
        // Configure permissions
        config.setAdminRoles(List.of("Admin", "Owner"));
        config.setModeratorRoles(List.of("Moderator", "Staff"));
        Map<String, String> rolePermissions = new HashMap<>();
        rolePermissions.put("Admin", "lumasg.admin");
        rolePermissions.put("Moderator", "lumasg.moderate");
        config.setRolePermissions(rolePermissions);
        
        // Verify all settings
        assertTrue(config.isEnabled());
        assertEquals("test.bot.token.here", config.getBotToken());
        assertEquals(3, config.getReconnectAttempts());
        assertEquals(15000, config.getReconnectDelay());
        assertEquals(20000, config.getConnectionTimeout());
        
        assertEquals("123456789012345678", config.getChannelId("game-announcements"));
        assertEquals("123456789012345679", config.getChannelId("statistics"));
        assertNull(config.getChannelId("non-existent"));
        
        assertEquals("https://discord.com/api/webhooks/123/abc", config.getWebhookUrl("game-announcements"));
        assertTrue(config.isUseWebhooksFirst());
        
        assertTrue(config.isAnnounceGameStart());
        assertTrue(config.isAnnounceGameEnd());
        assertTrue(config.isAnnounceDeathmatch());
        assertTrue(config.isEnableStatCommands());
        
        assertEquals(List.of(15, 10, 5, 2), config.getMilestoneThresholds());
        assertEquals(25, config.getMaxPlayersInEmbed());
        assertTrue(config.isIncludePlayerList());
        
        assertEquals("#FF5500", config.getEmbedColor());
        assertEquals("https://example.com/icon.png", config.getServerIconUrl());
        assertEquals("dd/MM/yyyy HH:mm:ss", config.getTimestampFormat());
        
        assertTrue(config.isAdminRole("Admin"));
        assertTrue(config.isAdminRole("Owner"));
        assertFalse(config.isAdminRole("User"));
        
        assertTrue(config.isModeratorRole("Moderator"));
        assertTrue(config.isModeratorRole("Staff"));
        assertFalse(config.isModeratorRole("User"));
        
        assertEquals("lumasg.admin", config.getRolePermission("Admin"));
        assertEquals("lumasg.moderate", config.getRolePermission("Moderator"));
        assertNull(config.getRolePermission("User"));
    }
    
    @Test
    void testConfigurationDefaults() {
        // Test that all default values are sensible
        assertFalse(config.isEnabled());
        assertNull(config.getBotToken());
        
        assertEquals(5, config.getReconnectAttempts());
        assertEquals(30000, config.getReconnectDelay());
        assertEquals(30000, config.getConnectionTimeout());
        
        assertTrue(config.getChannelMappings().isEmpty());
        assertTrue(config.getWebhooks().isEmpty());
        assertTrue(config.isUseWebhooksFirst());
        
        assertTrue(config.isAnnounceGameStart());
        assertTrue(config.isAnnounceGameEnd());
        assertTrue(config.isAnnounceDeathmatch());
        assertTrue(config.isAnnounceMilestones());
        assertTrue(config.isEnableStatCommands());
        assertTrue(config.isEnableAdminCommands());
        assertTrue(config.isEnableQueueCommands());
        
        assertEquals(List.of(10, 5, 3, 2), config.getMilestoneThresholds());
        assertTrue(config.isIncludePlayerList());
        assertEquals(20, config.getMaxPlayersInEmbed());
        assertTrue(config.isUsePlayerAvatars());
        
        assertEquals("#00FF00", config.getEmbedColor());
        assertEquals("", config.getServerIconUrl());
        assertEquals("yyyy-MM-dd HH:mm:ss", config.getTimestampFormat());
        
        assertTrue(config.getAdminRoles().isEmpty());
        assertTrue(config.getModeratorRoles().isEmpty());
        assertTrue(config.getRolePermissions().isEmpty());
    }
    
    @Test
    void testConfigurationValidation() {
        // Test that configuration handles edge cases properly
        
        // Test empty/null values
        config.setBotToken("");
        assertEquals("", config.getBotToken());
        
        config.setBotToken(null);
        assertNull(config.getBotToken());
        
        // Test boundary values
        config.setReconnectAttempts(0);
        assertEquals(0, config.getReconnectAttempts());
        
        config.setReconnectDelay(0);
        assertEquals(0, config.getReconnectDelay());
        
        config.setMaxPlayersInEmbed(0);
        assertEquals(0, config.getMaxPlayersInEmbed());
        
        // Test null collections are handled properly
        config.setChannelMappings(null);
        assertNotNull(config.getChannelMappings());
        assertTrue(config.getChannelMappings().isEmpty());
        
        config.setWebhooks(null);
        assertNotNull(config.getWebhooks());
        assertTrue(config.getWebhooks().isEmpty());
        
        config.setAdminRoles(null);
        assertNotNull(config.getAdminRoles());
        assertTrue(config.getAdminRoles().isEmpty());
        
        config.setModeratorRoles(null);
        assertNotNull(config.getModeratorRoles());
        assertTrue(config.getModeratorRoles().isEmpty());
        
        config.setRolePermissions(null);
        assertNotNull(config.getRolePermissions());
        assertTrue(config.getRolePermissions().isEmpty());
    }
}