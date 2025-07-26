package net.lumalyte.lumasg.discord.connection;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.lumalyte.lumasg.LumaSG;
import net.lumalyte.lumasg.discord.config.DiscordConfig;
import net.lumalyte.lumasg.discord.config.DiscordConfigManager;
import net.lumalyte.lumasg.util.core.DebugLogger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Integration tests for DiscordConnectionManager.
 * Tests the complete connection lifecycle and integration with other components.
 */
@ExtendWith(MockitoExtension.class)
class DiscordConnectionIntegrationTest {
    
    @Mock
    private LumaSG plugin;
    
    @Mock
    private DebugLogger debugLogger;
    
    @Mock
    private DebugLogger.ContextualLogger contextualLogger;
    
    private DiscordConfigManager configManager;
    private DiscordConnectionManager connectionManager;
    
    @BeforeEach
    void setUp() {
        // Setup mocks
        when(plugin.getDebugLogger()).thenReturn(debugLogger);
        when(debugLogger.forContext(anyString())).thenReturn(contextualLogger);
        
        // Create real config manager for integration testing
        configManager = new DiscordConfigManager(plugin);
        
        // Create connection manager
        connectionManager = new DiscordConnectionManager(plugin, configManager);
    }
    
    @AfterEach
    void tearDown() {
        if (connectionManager != null) {
            connectionManager.shutdown();
        }
    }
    
    @Test
    void testConnectionLifecycleWithDisabledConfig() {
        // Mock config manager to return disabled config
        DiscordConfig config = new DiscordConfig();
        config.setEnabled(false);
        
        configManager = spy(configManager);
        lenient().doReturn(config).when(configManager).getConfig();
        lenient().doReturn(null).when(configManager).getBotToken();
        
        // Create new connection manager with mocked config manager
        connectionManager = new DiscordConnectionManager(plugin, configManager);
        
        // Attempt connection
        CompletableFuture<Boolean> result = connectionManager.connect();
        
        // Verify connection fails gracefully
        assertFalse(result.join());
        assertFalse(connectionManager.isConnected());
        assertNull(connectionManager.getJDA());
        
        // Verify appropriate logging
        verify(contextualLogger).warn("Discord integration is disabled or not configured");
    }
    
    @Test
    void testConnectionLifecycleWithInvalidToken() {
        // Mock config manager to return config with invalid token
        DiscordConfig config = new DiscordConfig();
        config.setEnabled(true);
        config.setConnectionTimeout(1000L); // Short timeout for testing
        config.setReconnectAttempts(1);
        config.setReconnectDelay(1000L);
        
        configManager = spy(configManager);
        lenient().doReturn(config).when(configManager).getConfig();
        lenient().doReturn("invalid-token").when(configManager).getBotToken();
        
        // Create new connection manager with mocked config manager
        connectionManager = new DiscordConnectionManager(plugin, configManager);
        
        // Attempt connection
        CompletableFuture<Boolean> result = connectionManager.connect();
        
        // Verify connection fails
        assertFalse(result.join());
        assertFalse(connectionManager.isConnected());
        assertNull(connectionManager.getJDA());
        
        // Verify error logging
        verify(contextualLogger).error(eq("Failed to connect to Discord"), any(Exception.class));
    }
    
    @Test
    void testConnectionStatsIntegration() {
        // Mock config manager to return disabled config
        DiscordConfig config = new DiscordConfig();
        config.setEnabled(false);
        
        configManager = spy(configManager);
        lenient().doReturn(config).when(configManager).getConfig();
        
        // Create new connection manager with mocked config manager
        connectionManager = new DiscordConnectionManager(plugin, configManager);
        
        // Get connection stats
        String stats = connectionManager.getConnectionStats();
        
        // Verify stats format and content
        assertNotNull(stats);
        assertTrue(stats.contains("Discord Connection Statistics:"));
        assertTrue(stats.contains("Connected: false"));
        assertTrue(stats.contains("Connecting: false"));
        assertTrue(stats.contains("Reconnect Attempts: 0"));
        
        // Stats should not contain JDA-specific information when not connected
        assertFalse(stats.contains("JDA Status:"));
        assertFalse(stats.contains("Bot User:"));
        assertFalse(stats.contains("Guilds:"));
        assertFalse(stats.contains("Ping:"));
    }
    
    @Test
    void testConfigurationReload() {
        // Mock config manager to return disabled config initially
        DiscordConfig config1 = new DiscordConfig();
        config1.setEnabled(false);
        
        configManager = spy(configManager);
        lenient().doReturn(config1).when(configManager).getConfig();
        lenient().doReturn(null).when(configManager).getBotToken();
        
        // Create new connection manager with mocked config manager
        connectionManager = new DiscordConnectionManager(plugin, configManager);
        
        // Attempt connection - should fail
        CompletableFuture<Boolean> result1 = connectionManager.connect();
        assertFalse(result1.join());
        
        // Verify appropriate logging
        verify(contextualLogger).warn("Discord integration is disabled or not configured");
    }
    
    @Test
    void testShutdownIntegration() {
        // Mock config manager to return disabled config
        DiscordConfig config = new DiscordConfig();
        config.setEnabled(false);
        
        configManager = spy(configManager);
        lenient().doReturn(config).when(configManager).getConfig();
        
        // Create new connection manager with mocked config manager
        connectionManager = new DiscordConnectionManager(plugin, configManager);
        
        // Verify initial state
        assertFalse(connectionManager.isConnected());
        
        // Shutdown
        connectionManager.shutdown();
        
        // Verify shutdown state
        assertFalse(connectionManager.isConnected());
        assertNull(connectionManager.getJDA());
        
        // Attempt connection after shutdown - should fail
        CompletableFuture<Boolean> result = connectionManager.connect();
        assertFalse(result.join());
        
        // Verify appropriate logging
        verify(contextualLogger).debug("Shutting down Discord connection manager...");
        verify(contextualLogger).debug("Discord connection manager shutdown complete");
        verify(contextualLogger).debug("Cannot connect - connection manager is shutting down");
    }
    
    @Test
    void testMultipleShutdowns() {
        // Mock config manager to return disabled config
        DiscordConfig config = new DiscordConfig();
        config.setEnabled(false);
        
        configManager = spy(configManager);
        lenient().doReturn(config).when(configManager).getConfig();
        
        // Create new connection manager with mocked config manager
        connectionManager = new DiscordConnectionManager(plugin, configManager);
        
        // Multiple shutdowns should be safe
        connectionManager.shutdown();
        connectionManager.shutdown();
        connectionManager.shutdown();
        
        // Verify state remains consistent
        assertFalse(connectionManager.isConnected());
        assertNull(connectionManager.getJDA());
        
        // Verify shutdown logging (may be called multiple times)
        verify(contextualLogger, atLeastOnce()).debug("Shutting down Discord connection manager...");
        verify(contextualLogger, atLeastOnce()).debug("Discord connection manager shutdown complete");
    }
    
    @Test
    void testConnectionManagerWithNullConfig() {
        // Mock config manager to return null config
        configManager = spy(configManager);
        lenient().doReturn(null).when(configManager).getConfig();
        
        // Create new connection manager with mocked config manager
        connectionManager = new DiscordConnectionManager(plugin, configManager);
        
        // Attempt connection
        CompletableFuture<Boolean> result = connectionManager.connect();
        
        // Verify connection fails gracefully
        assertFalse(result.join());
        assertFalse(connectionManager.isConnected());
        
        // Verify appropriate logging
        verify(contextualLogger).warn("Discord integration is disabled or not configured");
    }
    
    @Test
    void testGetTextChannelIntegration() {
        // Mock config manager to return disabled config
        DiscordConfig config = new DiscordConfig();
        config.setEnabled(false);
        
        configManager = spy(configManager);
        lenient().doReturn(config).when(configManager).getConfig();
        
        // Create new connection manager with mocked config manager
        connectionManager = new DiscordConnectionManager(plugin, configManager);
        
        // Attempt to get text channel when not connected
        TextChannel channel = connectionManager.getTextChannel("123456789");
        
        // Verify returns null
        assertNull(channel);
        
        // No error logging should occur for this case
        verify(contextualLogger, never()).error(anyString(), any(Exception.class));
        verify(contextualLogger, never()).warn(contains("Failed to get text channel"));
    }
    
    @Test
    void testConnectionTimeoutConfiguration() {
        // Mock config manager to return config with custom timeout
        DiscordConfig config = new DiscordConfig();
        config.setEnabled(true);
        config.setConnectionTimeout(500L); // Very short timeout
        config.setReconnectAttempts(1);
        config.setReconnectDelay(100L);
        
        configManager = spy(configManager);
        lenient().doReturn(config).when(configManager).getConfig();
        lenient().doReturn("invalid-token").when(configManager).getBotToken();
        
        // Create new connection manager with mocked config manager
        connectionManager = new DiscordConnectionManager(plugin, configManager);
        
        // Measure connection attempt time
        long startTime = System.currentTimeMillis();
        CompletableFuture<Boolean> result = connectionManager.connect();
        boolean success = result.join();
        long duration = System.currentTimeMillis() - startTime;
        
        // Verify connection fails
        assertFalse(success);
        
        // Verify error logging
        verify(contextualLogger).error(eq("Failed to connect to Discord"), any(Exception.class));
    }
    
    @Test
    void testReconnectionConfigurationIntegration() {
        // Mock config manager to return config with specific reconnection settings
        DiscordConfig config = new DiscordConfig();
        config.setEnabled(true);
        config.setConnectionTimeout(100L); // Very short timeout
        config.setReconnectAttempts(2); // Only 2 attempts
        config.setReconnectDelay(50L); // Very short delay
        
        configManager = spy(configManager);
        lenient().doReturn(config).when(configManager).getConfig();
        lenient().doReturn("invalid-token").when(configManager).getBotToken();
        
        // Create new connection manager with mocked config manager
        connectionManager = new DiscordConnectionManager(plugin, configManager);
        
        // Attempt connection (will fail)
        CompletableFuture<Boolean> result = connectionManager.connect();
        assertFalse(result.join());
        
        // Verify the configuration values are accessible
        assertEquals(2, config.getReconnectAttempts());
        assertEquals(50L, config.getReconnectDelay());
        assertEquals(100L, config.getConnectionTimeout());
    }
}