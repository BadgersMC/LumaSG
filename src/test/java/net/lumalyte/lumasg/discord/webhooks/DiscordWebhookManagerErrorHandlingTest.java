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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Error handling and edge case tests for DiscordWebhookManager.
 * Tests various failure scenarios and error recovery mechanisms.
 */
@ExtendWith(MockitoExtension.class)
class DiscordWebhookManagerErrorHandlingTest {
    
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
    void testInitializationWithNullConfig() {
        when(configManager.getConfig()).thenReturn(null);
        
        // Should not throw exception
        assertDoesNotThrow(() -> new DiscordWebhookManager(plugin, configManager));
        
        verify(contextualLogger).debug("No Discord configuration available, skipping webhook initialization");
    }
    
    @Test
    void testAddWebhookWithWebhookClientCreationFailure() {
        String validUrl = "https://discord.com/api/webhooks/123456789/abcdefghijk";
        
        try (MockedStatic<WebhookClient> mockedWebhookClient = mockStatic(WebhookClient.class)) {
            mockedWebhookClient.when(() -> WebhookClient.createClient(validUrl))
                              .thenThrow(new RuntimeException("Failed to create webhook client"));
            
            boolean result = webhookManager.addWebhook("test", validUrl);
            
            assertFalse(result);
            assertFalse(webhookManager.hasWebhook("test"));
            verify(contextualLogger).error(contains("Failed to create webhook client 'test'"), any(RuntimeException.class));
        }
    }
    
    @Test
    void testRemoveWebhookWithCloseFailure() {
        String validUrl = "https://discord.com/api/webhooks/123456789/abcdefghijk";
        
        try (MockedStatic<WebhookClient> mockedWebhookClient = mockStatic(WebhookClient.class)) {
            mockedWebhookClient.when(() -> WebhookClient.createClient(validUrl))
                              .thenReturn(webhookClient);
            
            doThrow(new RuntimeException("Close failed")).when(webhookClient).close();
            
            // Add webhook
            assertTrue(webhookManager.addWebhook("test", validUrl));
            
            // Remove webhook - should still return true even if close fails
            assertTrue(webhookManager.removeWebhook("test"));
            assertFalse(webhookManager.hasWebhook("test"));
            
            verify(contextualLogger).warn(contains("Error closing webhook client 'test'"));
        }
    }
    
    @Test
    void testSendWebhookMessageWithBothWebhookAndFallbackFailure() {
        String validUrl = "https://discord.com/api/webhooks/123456789/abcdefghijk";
        
        try (MockedStatic<WebhookClient> mockedWebhookClient = mockStatic(WebhookClient.class)) {
            mockedWebhookClient.when(() -> WebhookClient.createClient(validUrl))
                              .thenReturn(webhookClient);
            
            // Setup webhook to fail
            when(webhookClient.sendMessage(any(MessageCreateData.class))).thenReturn(restAction);
            when(restAction.complete()).thenThrow(new RuntimeException("Webhook failed"));
            
            // Setup fallback channel to also fail
            when(fallbackChannel.sendMessage(any(MessageCreateData.class))).thenReturn(restAction);
            when(restAction.complete()).thenThrow(new RuntimeException("Fallback failed"));
            when(fallbackChannel.getId()).thenReturn("123456789");
            
            // Add webhook and set fallback
            webhookManager.addWebhook("test", validUrl);
            webhookManager.setFallbackChannel(fallbackChannel);
            
            // Send message - should throw exception
            CompletableFuture<Void> future = webhookManager.sendWebhookMessage("test", messageEmbed);
            
            ExecutionException exception = assertThrows(ExecutionException.class, future::get);
            assertTrue(exception.getCause() instanceof RuntimeException);
            assertEquals("Both webhook and fallback messaging failed", exception.getCause().getMessage());
            
            // Verify both were attempted
            verify(webhookClient).sendMessage(any(MessageCreateData.class));
            verify(fallbackChannel).sendMessage(any(MessageCreateData.class));
            verify(contextualLogger).error(contains("Fallback messaging also failed"), any(RuntimeException.class));
        }
    }
    
    @Test
    void testSendWebhookMessageWithNoFallbackChannel() {
        // Don't set fallback channel
        CompletableFuture<Void> future = webhookManager.sendWebhookMessage("nonexistent", messageEmbed);
        
        // Should complete without error
        assertDoesNotThrow(() -> future.join());
        
        verify(contextualLogger).warn("No fallback channel configured, cannot send message");
    }
    
    @Test
    void testSendFallbackMessageWithEmptyContent() {
        when(fallbackChannel.getId()).thenReturn("123456789");
        webhookManager.setFallbackChannel(fallbackChannel);
        
        // Send empty message
        CompletableFuture<Void> future = webhookManager.sendWebhookMessage("nonexistent", "", null);
        
        // Should complete without error
        assertDoesNotThrow(() -> future.join());
        
        // Verify fallback channel was not called
        verify(fallbackChannel, never()).sendMessage(any(MessageCreateData.class));
        verify(contextualLogger).warn("Cannot send empty fallback message");
    }
    
    @Test
    void testSendWebhookMessageWithNullConfig() {
        when(configManager.getConfig()).thenReturn(null);
        
        when(fallbackChannel.sendMessage(any(MessageCreateData.class))).thenReturn(restAction);
        when(restAction.complete()).thenReturn(null);
        when(fallbackChannel.getId()).thenReturn("123456789");
        
        webhookManager.setFallbackChannel(fallbackChannel);
        
        // Send message
        CompletableFuture<Void> future = webhookManager.sendWebhookMessage("test", messageEmbed);
        
        // Should use fallback immediately
        assertDoesNotThrow(() -> future.join());
        verify(fallbackChannel).sendMessage(any(MessageCreateData.class));
    }
    
    @Test
    void testReloadWebhooksWithCloseErrors() {
        String validUrl = "https://discord.com/api/webhooks/123456789/abcdefghijk";
        
        try (MockedStatic<WebhookClient> mockedWebhookClient = mockStatic(WebhookClient.class)) {
            WebhookClient oldClient = mock(WebhookClient.class);
            WebhookClient newClient = mock(WebhookClient.class);
            
            mockedWebhookClient.when(() -> WebhookClient.createClient(validUrl))
                              .thenReturn(oldClient)
                              .thenReturn(newClient);
            
            doThrow(new RuntimeException("Close failed")).when(oldClient).close();
            
            // Add webhook
            webhookManager.addWebhook("test", validUrl);
            
            // Setup config for reload
            when(config.getWebhooks()).thenReturn(java.util.Map.of("test", validUrl));
            
            // Reload should not throw
            assertDoesNotThrow(() -> webhookManager.reloadWebhooks());
            
            // Verify error was logged but reload continued
            verify(contextualLogger).warn(contains("Error closing webhook client 'test'"));
            assertEquals(1, webhookManager.getWebhookCount());
        }
    }
    
    @Test
    void testConcurrentWebhookOperations() throws InterruptedException {
        String validUrl = "https://discord.com/api/webhooks/123456789/abcdefghijk";
        
        try (MockedStatic<WebhookClient> mockedWebhookClient = mockStatic(WebhookClient.class)) {
            mockedWebhookClient.when(() -> WebhookClient.createClient(validUrl))
                              .thenReturn(webhookClient);
            
            when(webhookClient.sendMessage(any(MessageCreateData.class))).thenReturn(restAction);
            when(restAction.complete()).thenReturn(null);
            
            // Add webhook
            webhookManager.addWebhook("test", validUrl);
            
            // Create multiple threads to send messages concurrently
            Thread[] threads = new Thread[10];
            for (int i = 0; i < threads.length; i++) {
                final int threadId = i;
                threads[i] = new Thread(() -> {
                    CompletableFuture<Void> future = webhookManager.sendWebhookMessage("test", "Message " + threadId);
                    assertDoesNotThrow(() -> future.join());
                });
            }
            
            // Start all threads
            for (Thread thread : threads) {
                thread.start();
            }
            
            // Wait for all threads to complete
            for (Thread thread : threads) {
                thread.join(5000); // 5 second timeout
                assertFalse(thread.isAlive(), "Thread should have completed");
            }
            
            // Verify webhook was called multiple times
            verify(webhookClient, times(10)).sendMessage(any(MessageCreateData.class));
        }
    }
    
    @Test
    void testWebhookUrlValidationEdgeCases() {
        // Test various edge cases for URL validation
        assertFalse(webhookManager.isValidWebhookUrl(""));
        assertFalse(webhookManager.isValidWebhookUrl("   "));
        assertFalse(webhookManager.isValidWebhookUrl("https://discord.com/api/webhooks/"));
        assertFalse(webhookManager.isValidWebhookUrl("https://discord.com/api/webhooks/123"));
        assertFalse(webhookManager.isValidWebhookUrl("https://discord.com/api/webhooks/123/"));
        assertFalse(webhookManager.isValidWebhookUrl("https://discord.com/api/webhooks/abc/def"));
        assertFalse(webhookManager.isValidWebhookUrl("https://discordapp.com/api/webhooks/123/abc")); // Wrong domain
        assertFalse(webhookManager.isValidWebhookUrl("ftp://discord.com/api/webhooks/123/abc")); // Wrong protocol
        
        // Valid cases
        assertTrue(webhookManager.isValidWebhookUrl("https://discord.com/api/webhooks/123456789012345678/abcdefghijklmnopqrstuvwxyz"));
        assertTrue(webhookManager.isValidWebhookUrl("https://discord.com/api/webhooks/1/a"));
    }
    
    @Test
    void testGetWebhookStatsWithRemovedWebhook() {
        String validUrl = "https://discord.com/api/webhooks/123456789/abcdefghijk";
        
        try (MockedStatic<WebhookClient> mockedWebhookClient = mockStatic(WebhookClient.class)) {
            mockedWebhookClient.when(() -> WebhookClient.createClient(validUrl))
                              .thenReturn(webhookClient);
            
            // Add and then remove webhook
            webhookManager.addWebhook("test", validUrl);
            webhookManager.removeWebhook("test");
            
            // Stats should be empty
            assertTrue(webhookManager.getWebhookStats().isEmpty());
        }
    }
    
    @Test
    void testShutdownWithExecutorTimeout() {
        // This test verifies that shutdown handles executor timeout gracefully
        // The actual timeout behavior is hard to test without making the test slow,
        // but we can verify the shutdown method completes without hanging
        
        assertDoesNotThrow(() -> {
            webhookManager.shutdown();
        });
        
        verify(contextualLogger).debug("Discord webhook manager shutdown completed");
    }
    
    @Test
    void testMultipleShutdownCalls() {
        // Multiple shutdown calls should be safe
        assertDoesNotThrow(() -> {
            webhookManager.shutdown();
            webhookManager.shutdown();
            webhookManager.shutdown();
        });
        
        // Should log shutdown message multiple times
        verify(contextualLogger, atLeast(3)).debug("Shutting down Discord webhook manager");
    }
    
    @Test
    void testOperationsAfterShutdown() {
        webhookManager.shutdown();
        
        // Operations after shutdown should not crash
        assertDoesNotThrow(() -> {
            assertFalse(webhookManager.addWebhook("test", "https://discord.com/api/webhooks/123/abc"));
            assertFalse(webhookManager.removeWebhook("test"));
            assertFalse(webhookManager.hasWebhook("test"));
            assertEquals(0, webhookManager.getWebhookCount());
            assertTrue(webhookManager.getWebhookStats().isEmpty());
        });
    }
}