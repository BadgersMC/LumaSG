package net.lumalyte.lumasg.discord.webhooks;

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

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for DiscordWebhookManager.
 * Tests webhook client management, message sending, and fallback functionality.
 */
@ExtendWith(MockitoExtension.class)
class DiscordWebhookManagerTest {
    
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
    private WebhookClient webhookClient;
    
    @Mock
    private MessageChannel fallbackChannel;
    
    @Mock
    private RestAction<Void> restAction;
    
    @Mock
    private MessageEmbed messageEmbed;
    
    private DiscordWebhookManager webhookManager;
    
    @BeforeEach
    void setUp() {
        // Setup debug logger mocking
        when(plugin.getDebugLogger()).thenReturn(debugLogger);
        when(debugLogger.forContext("DiscordWebhooks")).thenReturn(contextualLogger);
        
        // Setup config manager mocking
        when(configManager.getConfig()).thenReturn(config);
        when(config.getWebhooks()).thenReturn(new HashMap<>());
        when(config.isUseWebhooksFirst()).thenReturn(true);
        
        // Create webhook manager
        webhookManager = new DiscordWebhookManager(plugin, configManager);
    }
    
    @Test
    void testConstructorInitializesCorrectly() {
        // Verify logger context is set up
        verify(debugLogger).forContext("DiscordWebhooks");
        verify(contextualLogger).debug("Discord webhook manager initialized");
    }
    
    @Test
    void testInitializeWebhooksFromConfig() {
        // Setup config with webhooks
        Map<String, String> webhooks = new HashMap<>();
        webhooks.put("test-webhook", "https://discord.com/api/webhooks/123456789/abcdefghijk");
        webhooks.put("empty-webhook", "");
        webhooks.put("null-webhook", null);
        
        when(config.getWebhooks()).thenReturn(webhooks);
        
        // Create new manager to test initialization
        try (MockedStatic<WebhookClient> mockedWebhookClient = mockStatic(WebhookClient.class)) {
            mockedWebhookClient.when(() -> WebhookClient.createClient(anyString()))
                              .thenReturn(webhookClient);
            
            DiscordWebhookManager manager = new DiscordWebhookManager(plugin, configManager);
            
            // Verify only valid webhook was created
            assertTrue(manager.hasWebhook("test-webhook"));
            assertFalse(manager.hasWebhook("empty-webhook"));
            assertFalse(manager.hasWebhook("null-webhook"));
            assertEquals(1, manager.getWebhookCount());
        }
    }
    
    @Test
    void testAddWebhookWithValidUrl() {
        String validUrl = "https://discord.com/api/webhooks/123456789/abcdefghijk";
        
        try (MockedStatic<WebhookClient> mockedWebhookClient = mockStatic(WebhookClient.class)) {
            mockedWebhookClient.when(() -> WebhookClient.createClient(validUrl))
                              .thenReturn(webhookClient);
            
            boolean result = webhookManager.addWebhook("test", validUrl);
            
            assertTrue(result);
            assertTrue(webhookManager.hasWebhook("test"));
            assertEquals(1, webhookManager.getWebhookCount());
            verify(contextualLogger).debug(contains("Added webhook client 'test'"));
        }
    }
    
    @Test
    void testAddWebhookWithInvalidUrl() {
        String invalidUrl = "not-a-webhook-url";
        
        boolean result = webhookManager.addWebhook("test", invalidUrl);
        
        assertFalse(result);
        assertFalse(webhookManager.hasWebhook("test"));
        assertEquals(0, webhookManager.getWebhookCount());
        verify(contextualLogger).warn(contains("Invalid webhook URL format"));
    }
    
    @Test
    void testAddWebhookReplacesExisting() {
        String url1 = "https://discord.com/api/webhooks/123456789/abcdefghijk";
        String url2 = "https://discord.com/api/webhooks/987654321/zyxwvutsrqp";
        
        try (MockedStatic<WebhookClient> mockedWebhookClient = mockStatic(WebhookClient.class)) {
            WebhookClient client1 = mock(WebhookClient.class);
            WebhookClient client2 = mock(WebhookClient.class);
            
            mockedWebhookClient.when(() -> WebhookClient.createClient(url1)).thenReturn(client1);
            mockedWebhookClient.when(() -> WebhookClient.createClient(url2)).thenReturn(client2);
            
            // Add first webhook
            assertTrue(webhookManager.addWebhook("test", url1));
            assertEquals(1, webhookManager.getWebhookCount());
            
            // Replace with second webhook
            assertTrue(webhookManager.addWebhook("test", url2));
            assertEquals(1, webhookManager.getWebhookCount());
            
            // Verify first client was closed
            verify(client1).close();
        }
    }
    
    @Test
    void testRemoveWebhook() {
        String validUrl = "https://discord.com/api/webhooks/123456789/abcdefghijk";
        
        try (MockedStatic<WebhookClient> mockedWebhookClient = mockStatic(WebhookClient.class)) {
            mockedWebhookClient.when(() -> WebhookClient.createClient(validUrl))
                              .thenReturn(webhookClient);
            
            // Add webhook
            assertTrue(webhookManager.addWebhook("test", validUrl));
            assertTrue(webhookManager.hasWebhook("test"));
            
            // Remove webhook
            assertTrue(webhookManager.removeWebhook("test"));
            assertFalse(webhookManager.hasWebhook("test"));
            assertEquals(0, webhookManager.getWebhookCount());
            
            // Verify client was closed
            verify(webhookClient).close();
            verify(contextualLogger).debug("Removed webhook client 'test'");
        }
    }
    
    @Test
    void testRemoveNonExistentWebhook() {
        boolean result = webhookManager.removeWebhook("nonexistent");
        assertFalse(result);
    }
    
    @Test
    void testSendWebhookMessageWithEmbed() {
        String validUrl = "https://discord.com/api/webhooks/123456789/abcdefghijk";
        
        try (MockedStatic<WebhookClient> mockedWebhookClient = mockStatic(WebhookClient.class)) {
            mockedWebhookClient.when(() -> WebhookClient.createClient(validUrl))
                              .thenReturn(webhookClient);
            
            when(webhookClient.sendMessage(any(MessageCreateData.class))).thenReturn(restAction);
            when(restAction.complete()).thenReturn(null);
            
            // Add webhook
            webhookManager.addWebhook("test", validUrl);
            
            // Send message
            CompletableFuture<Void> future = webhookManager.sendWebhookMessage("test", messageEmbed);
            
            // Wait for completion
            assertDoesNotThrow(() -> future.join());
            
            // Verify webhook was called
            verify(webhookClient).sendMessage(any(MessageCreateData.class));
            verify(contextualLogger).debug("Successfully sent message via webhook 'test'");
        }
    }
    
    @Test
    void testSendWebhookMessageWithContent() {
        String validUrl = "https://discord.com/api/webhooks/123456789/abcdefghijk";
        String content = "Test message content";
        
        try (MockedStatic<WebhookClient> mockedWebhookClient = mockStatic(WebhookClient.class)) {
            mockedWebhookClient.when(() -> WebhookClient.createClient(validUrl))
                              .thenReturn(webhookClient);
            
            when(webhookClient.sendMessage(any(MessageCreateData.class))).thenReturn(restAction);
            when(restAction.complete()).thenReturn(null);
            
            // Add webhook
            webhookManager.addWebhook("test", validUrl);
            
            // Send message
            CompletableFuture<Void> future = webhookManager.sendWebhookMessage("test", content);
            
            // Wait for completion
            assertDoesNotThrow(() -> future.join());
            
            // Verify webhook was called
            verify(webhookClient).sendMessage(any(MessageCreateData.class));
            verify(contextualLogger).debug("Successfully sent message via webhook 'test'");
        }
    }
    
    @Test
    void testSendWebhookMessageFallsBackOnFailure() {
        String validUrl = "https://discord.com/api/webhooks/123456789/abcdefghijk";
        
        try (MockedStatic<WebhookClient> mockedWebhookClient = mockStatic(WebhookClient.class)) {
            mockedWebhookClient.when(() -> WebhookClient.createClient(validUrl))
                              .thenReturn(webhookClient);
            
            // Setup webhook to fail
            when(webhookClient.sendMessage(any(MessageCreateData.class))).thenReturn(restAction);
            when(restAction.complete()).thenThrow(new RuntimeException("Webhook failed"));
            
            // Setup fallback channel
            when(fallbackChannel.sendMessage(any(MessageCreateData.class))).thenReturn(restAction);
            when(restAction.complete()).thenReturn(null);
            when(fallbackChannel.getId()).thenReturn("123456789");
            
            // Add webhook and set fallback
            webhookManager.addWebhook("test", validUrl);
            webhookManager.setFallbackChannel(fallbackChannel);
            
            // Send message
            CompletableFuture<Void> future = webhookManager.sendWebhookMessage("test", messageEmbed);
            
            // Wait for completion
            assertDoesNotThrow(() -> future.join());
            
            // Verify webhook was attempted and fallback was used
            verify(webhookClient).sendMessage(any(MessageCreateData.class));
            verify(fallbackChannel).sendMessage(any(MessageCreateData.class));
            verify(contextualLogger).warn(contains("Failed to send message via webhook 'test'"));
            verify(contextualLogger).debug("Successfully sent message via fallback after webhook failure");
        }
    }
    
    @Test
    void testSendWebhookMessageWithNonExistentWebhook() {
        // Setup fallback channel
        when(fallbackChannel.sendMessage(any(MessageCreateData.class))).thenReturn(restAction);
        when(restAction.complete()).thenReturn(null);
        when(fallbackChannel.getId()).thenReturn("123456789");
        
        webhookManager.setFallbackChannel(fallbackChannel);
        
        // Send message to non-existent webhook
        CompletableFuture<Void> future = webhookManager.sendWebhookMessage("nonexistent", messageEmbed);
        
        // Wait for completion
        assertDoesNotThrow(() -> future.join());
        
        // Verify fallback was used
        verify(fallbackChannel).sendMessage(any(MessageCreateData.class));
        verify(contextualLogger).debug("Webhook 'nonexistent' not found, using fallback");
    }
    
    @Test
    void testSendWebhookMessageWithWebhooksDisabled() {
        when(config.isUseWebhooksFirst()).thenReturn(false);
        
        // Setup fallback channel
        when(fallbackChannel.sendMessage(any(MessageCreateData.class))).thenReturn(restAction);
        when(restAction.complete()).thenReturn(null);
        when(fallbackChannel.getId()).thenReturn("123456789");
        
        webhookManager.setFallbackChannel(fallbackChannel);
        
        // Send message
        CompletableFuture<Void> future = webhookManager.sendWebhookMessage("test", messageEmbed);
        
        // Wait for completion
        assertDoesNotThrow(() -> future.join());
        
        // Verify fallback was used immediately
        verify(fallbackChannel).sendMessage(any(MessageCreateData.class));
        verify(contextualLogger).debug("Webhooks disabled or not preferred, using fallback immediately");
    }
    
    @Test
    void testSendEmptyMessage() {
        String validUrl = "https://discord.com/api/webhooks/123456789/abcdefghijk";
        
        try (MockedStatic<WebhookClient> mockedWebhookClient = mockStatic(WebhookClient.class)) {
            mockedWebhookClient.when(() -> WebhookClient.createClient(validUrl))
                              .thenReturn(webhookClient);
            
            // Add webhook
            webhookManager.addWebhook("test", validUrl);
            
            // Send empty message
            CompletableFuture<Void> future = webhookManager.sendWebhookMessage("test", "", null);
            
            // Wait for completion
            assertDoesNotThrow(() -> future.join());
            
            // Verify webhook was not called
            verify(webhookClient, never()).sendMessage(any(MessageCreateData.class));
            verify(contextualLogger).warn("Cannot send empty message via webhook 'test'");
        }
    }
    
    @Test
    void testSetAndGetFallbackChannel() {
        assertNull(webhookManager.getFallbackChannel());
        
        webhookManager.setFallbackChannel(fallbackChannel);
        assertEquals(fallbackChannel, webhookManager.getFallbackChannel());
        
        webhookManager.setFallbackChannel(null);
        assertNull(webhookManager.getFallbackChannel());
    }
    
    @Test
    void testIsValidWebhookUrl() {
        // Valid URLs
        assertTrue(webhookManager.isValidWebhookUrl("https://discord.com/api/webhooks/123456789/abcdefghijk"));
        assertTrue(webhookManager.isValidWebhookUrl("https://discord.com/api/webhooks/987654321012345678/aBcDeFgHiJkLmNoPqRsTuVwXyZ123456"));
        
        // Invalid URLs
        assertFalse(webhookManager.isValidWebhookUrl(""));
        assertFalse(webhookManager.isValidWebhookUrl("not-a-url"));
        assertFalse(webhookManager.isValidWebhookUrl("https://example.com/webhook"));
        assertFalse(webhookManager.isValidWebhookUrl("https://discord.com/api/webhooks/invalid"));
        assertFalse(webhookManager.isValidWebhookUrl("https://discord.com/api/webhooks/123/"));
        assertFalse(webhookManager.isValidWebhookUrl("http://discord.com/api/webhooks/123/abc")); // HTTP instead of HTTPS
    }
    
    @Test
    void testTestWebhook() {
        String validUrl = "https://discord.com/api/webhooks/123456789/abcdefghijk";
        
        try (MockedStatic<WebhookClient> mockedWebhookClient = mockStatic(WebhookClient.class)) {
            mockedWebhookClient.when(() -> WebhookClient.createClient(validUrl))
                              .thenReturn(webhookClient);
            
            when(webhookClient.sendMessage(any(MessageCreateData.class))).thenReturn(restAction);
            when(restAction.complete()).thenReturn(null);
            
            // Add webhook
            webhookManager.addWebhook("test", validUrl);
            
            // Test webhook
            CompletableFuture<Boolean> future = webhookManager.testWebhook("test");
            Boolean result = future.join();
            
            assertTrue(result);
            verify(webhookClient).sendMessage(any(MessageCreateData.class));
            verify(contextualLogger).debug("Webhook 'test' test successful");
        }
    }
    
    @Test
    void testTestNonExistentWebhook() {
        CompletableFuture<Boolean> future = webhookManager.testWebhook("nonexistent");
        Boolean result = future.join();
        
        assertFalse(result);
        verify(contextualLogger).debug("Cannot test webhook 'nonexistent' - not found");
    }
    
    @Test
    void testTestWebhookFailure() {
        String validUrl = "https://discord.com/api/webhooks/123456789/abcdefghijk";
        
        try (MockedStatic<WebhookClient> mockedWebhookClient = mockStatic(WebhookClient.class)) {
            mockedWebhookClient.when(() -> WebhookClient.createClient(validUrl))
                              .thenReturn(webhookClient);
            
            when(webhookClient.sendMessage(any(MessageCreateData.class))).thenReturn(restAction);
            when(restAction.complete()).thenThrow(new RuntimeException("Test failed"));
            
            // Add webhook
            webhookManager.addWebhook("test", validUrl);
            
            // Test webhook
            CompletableFuture<Boolean> future = webhookManager.testWebhook("test");
            Boolean result = future.join();
            
            assertFalse(result);
            verify(contextualLogger).warn(contains("Webhook 'test' test failed"));
        }
    }
    
    @Test
    void testGetWebhookStats() {
        String validUrl = "https://discord.com/api/webhooks/123456789/abcdefghijk";
        
        try (MockedStatic<WebhookClient> mockedWebhookClient = mockStatic(WebhookClient.class)) {
            mockedWebhookClient.when(() -> WebhookClient.createClient(validUrl))
                              .thenReturn(webhookClient);
            
            // Add webhook
            webhookManager.addWebhook("test", validUrl);
            
            Map<String, String> stats = webhookManager.getWebhookStats();
            
            assertEquals(1, stats.size());
            assertEquals("Active", stats.get("test"));
        }
    }
    
    @Test
    void testReloadWebhooks() {
        String validUrl = "https://discord.com/api/webhooks/123456789/abcdefghijk";
        
        try (MockedStatic<WebhookClient> mockedWebhookClient = mockStatic(WebhookClient.class)) {
            WebhookClient oldClient = mock(WebhookClient.class);
            WebhookClient newClient = mock(WebhookClient.class);
            
            mockedWebhookClient.when(() -> WebhookClient.createClient(validUrl))
                              .thenReturn(oldClient)
                              .thenReturn(newClient);
            
            // Add webhook
            webhookManager.addWebhook("test", validUrl);
            assertEquals(1, webhookManager.getWebhookCount());
            
            // Setup config for reload
            Map<String, String> webhooks = new HashMap<>();
            webhooks.put("test", validUrl);
            when(config.getWebhooks()).thenReturn(webhooks);
            
            // Reload webhooks
            webhookManager.reloadWebhooks();
            
            // Verify old client was closed and new one created
            verify(oldClient).close();
            assertEquals(1, webhookManager.getWebhookCount());
            verify(contextualLogger).debug("Reloading webhooks from configuration");
            verify(contextualLogger).debug(contains("Webhook reload completed"));
        }
    }
    
    @Test
    void testShutdown() {
        String validUrl = "https://discord.com/api/webhooks/123456789/abcdefghijk";
        
        try (MockedStatic<WebhookClient> mockedWebhookClient = mockStatic(WebhookClient.class)) {
            mockedWebhookClient.when(() -> WebhookClient.createClient(validUrl))
                              .thenReturn(webhookClient);
            
            // Add webhook
            webhookManager.addWebhook("test", validUrl);
            webhookManager.setFallbackChannel(fallbackChannel);
            
            // Shutdown
            webhookManager.shutdown();
            
            // Verify cleanup
            verify(webhookClient).close();
            assertEquals(0, webhookManager.getWebhookCount());
            assertNull(webhookManager.getFallbackChannel());
            verify(contextualLogger).debug("Shutting down Discord webhook manager");
            verify(contextualLogger).debug("Discord webhook manager shutdown completed");
        }
    }
    
    @Test
    void testShutdownWithClientCloseError() {
        String validUrl = "https://discord.com/api/webhooks/123456789/abcdefghijk";
        
        try (MockedStatic<WebhookClient> mockedWebhookClient = mockStatic(WebhookClient.class)) {
            mockedWebhookClient.when(() -> WebhookClient.createClient(validUrl))
                              .thenReturn(webhookClient);
            
            doThrow(new RuntimeException("Close failed")).when(webhookClient).close();
            
            // Add webhook
            webhookManager.addWebhook("test", validUrl);
            
            // Shutdown should not throw
            assertDoesNotThrow(() -> webhookManager.shutdown());
            
            // Verify error was logged
            verify(contextualLogger).warn(contains("Error closing webhook client 'test'"));
        }
    }
}