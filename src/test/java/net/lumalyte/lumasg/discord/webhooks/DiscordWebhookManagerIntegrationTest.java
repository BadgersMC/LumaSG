package net.lumalyte.lumasg.discord.webhooks;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.WebhookClient;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import net.lumalyte.lumasg.LumaSG;
import net.lumalyte.lumasg.discord.config.DiscordConfig;
import net.lumalyte.lumasg.discord.config.DiscordConfigManager;
import net.lumalyte.lumasg.util.core.DebugLogger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Integration tests for DiscordWebhookManager.
 * Tests realistic scenarios and integration with Discord configuration.
 */
@ExtendWith(MockitoExtension.class)
class DiscordWebhookManagerIntegrationTest {
    
    @Mock
    private LumaSG plugin;
    
    @Mock
    private DiscordConfigManager configManager;
    
    @Mock
    private DebugLogger debugLogger;
    
    @Mock
    private DebugLogger.ContextualLogger contextualLogger;
    
    @Mock
    private DiscordConfig config;
    
    @Mock
    private WebhookClient gameWebhookClient;
    
    @Mock
    private WebhookClient statsWebhookClient;
    
    @Mock
    private MessageChannel fallbackChannel;
    
    @Mock
    private RestAction<Void> restAction;
    
    private DiscordWebhookManager webhookManager;
    
    @BeforeEach
    void setUp() {
        // Setup debug logger mocking
        when(plugin.getDebugLogger()).thenReturn(debugLogger);
        when(debugLogger.forContext("DiscordWebhooks")).thenReturn(contextualLogger);
        
        // Setup config manager mocking
        when(configManager.getConfig()).thenReturn(config);
        when(config.isUseWebhooksFirst()).thenReturn(true);
    }
    
    @Test
    void testFullWebhookConfigurationScenario() {
        // Setup realistic webhook configuration
        Map<String, String> webhooks = new HashMap<>();
        webhooks.put("game-announcements", "https://discord.com/api/webhooks/123456789/gameWebhookToken");
        webhooks.put("statistics", "https://discord.com/api/webhooks/987654321/statsWebhookToken");
        webhooks.put("admin", "https://discord.com/api/webhooks/555666777/adminWebhookToken");
        
        when(config.getWebhooks()).thenReturn(webhooks);
        
        try (MockedStatic<WebhookClient> mockedWebhookClient = mockStatic(WebhookClient.class)) {
            mockedWebhookClient.when(() -> WebhookClient.createClient("https://discord.com/api/webhooks/123456789/gameWebhookToken"))
                              .thenReturn(gameWebhookClient);
            mockedWebhookClient.when(() -> WebhookClient.createClient("https://discord.com/api/webhooks/987654321/statsWebhookToken"))
                              .thenReturn(statsWebhookClient);
            mockedWebhookClient.when(() -> WebhookClient.createClient("https://discord.com/api/webhooks/555666777/adminWebhookToken"))
                              .thenReturn(mock(WebhookClient.class));
            
            // Create webhook manager - should initialize all webhooks
            webhookManager = new DiscordWebhookManager(plugin, configManager);
            
            // Verify all webhooks were created
            assertEquals(3, webhookManager.getWebhookCount());
            assertTrue(webhookManager.hasWebhook("game-announcements"));
            assertTrue(webhookManager.hasWebhook("statistics"));
            assertTrue(webhookManager.hasWebhook("admin"));
            
            // Verify stats
            Map<String, String> stats = webhookManager.getWebhookStats();
            assertEquals(3, stats.size());
            assertEquals("Active", stats.get("game-announcements"));
            assertEquals("Active", stats.get("statistics"));
            assertEquals("Active", stats.get("admin"));
        }
    }
    
    @Test
    void testGameAnnouncementWorkflow() {
        // Setup game announcement webhook
        Map<String, String> webhooks = new HashMap<>();
        webhooks.put("game-announcements", "https://discord.com/api/webhooks/123456789/gameWebhookToken");
        when(config.getWebhooks()).thenReturn(webhooks);
        
        try (MockedStatic<WebhookClient> mockedWebhookClient = mockStatic(WebhookClient.class)) {
            mockedWebhookClient.when(() -> WebhookClient.createClient(anyString()))
                              .thenReturn(gameWebhookClient);
            
            when(gameWebhookClient.sendMessage(any(MessageCreateData.class))).thenReturn(restAction);
            when(restAction.complete()).thenReturn(null);
            
            webhookManager = new DiscordWebhookManager(plugin, configManager);
            
            // Create a realistic game announcement embed
            MessageEmbed gameStartEmbed = new EmbedBuilder()
                .setTitle("🎮 Game Started!")
                .setDescription("A new Survival Games match has begun!")
                .addField("Arena", "SkyIslands", true)
                .addField("Players", "12/24", true)
                .addField("Mode", "Solo", true)
                .setColor(Color.GREEN)
                .setTimestamp(java.time.Instant.now())
                .build();
            
            // Send game announcement
            CompletableFuture<Void> future = webhookManager.sendWebhookMessage("game-announcements", gameStartEmbed);
            
            // Verify successful sending
            assertDoesNotThrow(() -> future.join());
            verify(gameWebhookClient).sendMessage(any(MessageCreateData.class));
            verify(contextualLogger).debug("Successfully sent message via webhook 'game-announcements'");
        }
    }
    
    @Test
    void testStatisticsAnnouncementWorkflow() {
        // Setup statistics webhook
        Map<String, String> webhooks = new HashMap<>();
        webhooks.put("statistics", "https://discord.com/api/webhooks/987654321/statsWebhookToken");
        when(config.getWebhooks()).thenReturn(webhooks);
        
        try (MockedStatic<WebhookClient> mockedWebhookClient = mockStatic(WebhookClient.class)) {
            mockedWebhookClient.when(() -> WebhookClient.createClient(anyString()))
                              .thenReturn(statsWebhookClient);
            
            when(statsWebhookClient.sendMessage(any(MessageCreateData.class))).thenReturn(restAction);
            when(restAction.complete()).thenReturn(null);
            
            webhookManager = new DiscordWebhookManager(plugin, configManager);
            
            // Create a realistic statistics embed
            MessageEmbed statsEmbed = new EmbedBuilder()
                .setTitle("📊 Player Statistics")
                .setDescription("Statistics for player: TestPlayer")
                .addField("Wins", "15", true)
                .addField("Kills", "47", true)
                .addField("Deaths", "23", true)
                .addField("Win Rate", "65.2%", true)
                .addField("K/D Ratio", "2.04", true)
                .addField("Games Played", "23", true)
                .setColor(Color.BLUE)
                .setTimestamp(java.time.Instant.now())
                .build();
            
            // Send statistics announcement
            CompletableFuture<Void> future = webhookManager.sendWebhookMessage("statistics", statsEmbed);
            
            // Verify successful sending
            assertDoesNotThrow(() -> future.join());
            verify(statsWebhookClient).sendMessage(any(MessageCreateData.class));
            verify(contextualLogger).debug("Successfully sent message via webhook 'statistics'");
        }
    }
    
    @Test
    void testWebhookFailureWithFallbackScenario() {
        // Setup webhook that will fail
        Map<String, String> webhooks = new HashMap<>();
        webhooks.put("game-announcements", "https://discord.com/api/webhooks/123456789/gameWebhookToken");
        when(config.getWebhooks()).thenReturn(webhooks);
        
        try (MockedStatic<WebhookClient> mockedWebhookClient = mockStatic(WebhookClient.class)) {
            mockedWebhookClient.when(() -> WebhookClient.createClient(anyString()))
                              .thenReturn(gameWebhookClient);
            
            // Setup webhook to fail
            when(gameWebhookClient.sendMessage(any(MessageCreateData.class))).thenReturn(restAction);
            when(restAction.complete()).thenThrow(new RuntimeException("Webhook rate limited"));
            
            // Setup fallback channel
            when(fallbackChannel.sendMessage(any(MessageCreateData.class))).thenReturn(restAction);
            when(restAction.complete()).thenReturn(null);
            when(fallbackChannel.getId()).thenReturn("123456789");
            
            webhookManager = new DiscordWebhookManager(plugin, configManager);
            webhookManager.setFallbackChannel(fallbackChannel);
            
            // Create message
            MessageEmbed embed = new EmbedBuilder()
                .setTitle("Test Message")
                .setDescription("This should fallback to bot messaging")
                .setColor(Color.ORANGE)
                .build();
            
            // Send message - should fallback
            CompletableFuture<Void> future = webhookManager.sendWebhookMessage("game-announcements", embed);
            
            // Verify fallback was used
            assertDoesNotThrow(() -> future.join());
            verify(gameWebhookClient).sendMessage(any(MessageCreateData.class));
            verify(fallbackChannel).sendMessage(any(MessageCreateData.class));
            verify(contextualLogger).warn(contains("Failed to send message via webhook 'game-announcements'"));
            verify(contextualLogger).debug("Successfully sent message via fallback after webhook failure");
        }
    }
    
    @Test
    void testConfigurationReloadScenario() {
        // Initial configuration
        Map<String, String> initialWebhooks = new HashMap<>();
        initialWebhooks.put("game-announcements", "https://discord.com/api/webhooks/123456789/gameWebhookToken");
        when(config.getWebhooks()).thenReturn(initialWebhooks);
        
        try (MockedStatic<WebhookClient> mockedWebhookClient = mockStatic(WebhookClient.class)) {
            WebhookClient oldClient = mock(WebhookClient.class);
            WebhookClient newClient = mock(WebhookClient.class);
            WebhookClient additionalClient = mock(WebhookClient.class);
            
            mockedWebhookClient.when(() -> WebhookClient.createClient("https://discord.com/api/webhooks/123456789/gameWebhookToken"))
                              .thenReturn(oldClient)
                              .thenReturn(newClient);
            mockedWebhookClient.when(() -> WebhookClient.createClient("https://discord.com/api/webhooks/987654321/statsWebhookToken"))
                              .thenReturn(additionalClient);
            
            webhookManager = new DiscordWebhookManager(plugin, configManager);
            assertEquals(1, webhookManager.getWebhookCount());
            
            // Update configuration to add another webhook
            Map<String, String> updatedWebhooks = new HashMap<>();
            updatedWebhooks.put("game-announcements", "https://discord.com/api/webhooks/123456789/gameWebhookToken");
            updatedWebhooks.put("statistics", "https://discord.com/api/webhooks/987654321/statsWebhookToken");
            when(config.getWebhooks()).thenReturn(updatedWebhooks);
            
            // Reload webhooks
            webhookManager.reloadWebhooks();
            
            // Verify old client was closed and new ones created
            verify(oldClient).close();
            assertEquals(2, webhookManager.getWebhookCount());
            assertTrue(webhookManager.hasWebhook("game-announcements"));
            assertTrue(webhookManager.hasWebhook("statistics"));
        }
    }
    
    @Test
    void testWebhookTestingScenario() {
        // Setup webhook for testing
        Map<String, String> webhooks = new HashMap<>();
        webhooks.put("test-webhook", "https://discord.com/api/webhooks/123456789/testWebhookToken");
        when(config.getWebhooks()).thenReturn(webhooks);
        
        try (MockedStatic<WebhookClient> mockedWebhookClient = mockStatic(WebhookClient.class)) {
            mockedWebhookClient.when(() -> WebhookClient.createClient(anyString()))
                              .thenReturn(gameWebhookClient);
            
            when(gameWebhookClient.sendMessage(any(MessageCreateData.class))).thenReturn(restAction);
            when(restAction.complete()).thenReturn(null);
            
            webhookManager = new DiscordWebhookManager(plugin, configManager);
            
            // Test webhook
            CompletableFuture<Boolean> testResult = webhookManager.testWebhook("test-webhook");
            
            // Verify test was successful
            assertTrue(testResult.join());
            verify(gameWebhookClient).sendMessage(argThat(message -> {
                // Verify test message content
                String content = message.getContent();
                return content != null && content.contains("LumaSG Discord Integration Test");
            }));
            verify(contextualLogger).debug("Webhook 'test-webhook' test successful");
        }
    }
    
    @Test
    void testMixedContentAndEmbedMessage() {
        // Setup webhook
        Map<String, String> webhooks = new HashMap<>();
        webhooks.put("mixed-content", "https://discord.com/api/webhooks/123456789/mixedWebhookToken");
        when(config.getWebhooks()).thenReturn(webhooks);
        
        try (MockedStatic<WebhookClient> mockedWebhookClient = mockStatic(WebhookClient.class)) {
            mockedWebhookClient.when(() -> WebhookClient.createClient(anyString()))
                              .thenReturn(gameWebhookClient);
            
            when(gameWebhookClient.sendMessage(any(MessageCreateData.class))).thenReturn(restAction);
            when(restAction.complete()).thenReturn(null);
            
            webhookManager = new DiscordWebhookManager(plugin, configManager);
            
            // Create message with both content and embed
            String content = "🎮 **Game Update**";
            MessageEmbed embed = new EmbedBuilder()
                .setTitle("Game Status")
                .setDescription("Current game information")
                .addField("Status", "Active", true)
                .setColor(Color.GREEN)
                .build();
            
            // Send mixed message
            CompletableFuture<Void> future = webhookManager.sendWebhookMessage("mixed-content", content, embed);
            
            // Verify successful sending
            assertDoesNotThrow(() -> future.join());
            verify(gameWebhookClient).sendMessage(argThat(message -> {
                // Verify both content and embed are present
                return message.getContent() != null && 
                       message.getContent().contains("Game Update") &&
                       !message.getEmbeds().isEmpty();
            }));
        }
    }
    
    @Test
    void testWebhookManagerLifecycleIntegration() {
        // Setup configuration
        Map<String, String> webhooks = new HashMap<>();
        webhooks.put("lifecycle-test", "https://discord.com/api/webhooks/123456789/lifecycleToken");
        when(config.getWebhooks()).thenReturn(webhooks);
        
        try (MockedStatic<WebhookClient> mockedWebhookClient = mockStatic(WebhookClient.class)) {
            mockedWebhookClient.when(() -> WebhookClient.createClient(anyString()))
                              .thenReturn(gameWebhookClient);
            
            // Initialize
            webhookManager = new DiscordWebhookManager(plugin, configManager);
            assertEquals(1, webhookManager.getWebhookCount());
            
            // Add additional webhook
            assertTrue(webhookManager.addWebhook("runtime-webhook", "https://discord.com/api/webhooks/999888777/runtimeToken"));
            assertEquals(2, webhookManager.getWebhookCount());
            
            // Remove webhook
            assertTrue(webhookManager.removeWebhook("lifecycle-test"));
            assertEquals(1, webhookManager.getWebhookCount());
            
            // Shutdown
            webhookManager.shutdown();
            assertEquals(0, webhookManager.getWebhookCount());
            
            // Verify cleanup
            verify(gameWebhookClient, times(2)).close(); // Once for removal, once for shutdown
            verify(contextualLogger).debug("Discord webhook manager shutdown completed");
        }
    }
    
    @Test
    void testWebhookUrlMaskingInLogs() {
        // This test verifies that webhook URLs are properly masked in logs for security
        String sensitiveUrl = "https://discord.com/api/webhooks/123456789/verySensitiveTokenThatShouldNotAppearInLogs";
        
        try (MockedStatic<WebhookClient> mockedWebhookClient = mockStatic(WebhookClient.class)) {
            mockedWebhookClient.when(() -> WebhookClient.createClient(sensitiveUrl))
                              .thenReturn(gameWebhookClient);
            
            webhookManager = new DiscordWebhookManager(plugin, configManager);
            
            // Add webhook
            webhookManager.addWebhook("sensitive", sensitiveUrl);
            
            // Verify that the full token doesn't appear in logs
            verify(contextualLogger).debug(argThat(message -> 
                message.contains("Added webhook client 'sensitive'") && 
                !message.contains("verySensitiveTokenThatShouldNotAppearInLogs") &&
                message.contains("***")
            ));
        }
    }
}