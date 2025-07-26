package net.lumalyte.lumasg.discord.connection;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.SelfUser;
import net.dv8tion.jda.api.events.session.SessionDisconnectEvent;
import net.dv8tion.jda.api.requests.CloseCode;
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
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.*;

/**
 * Unit tests for DiscordConnectionManager reconnection logic.
 * Tests exponential backoff, maximum attempts, and reconnection scenarios.
 */
@ExtendWith(MockitoExtension.class)
class DiscordConnectionReconnectionTest {
    
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
    private JDA jda;
    
    @Mock
    private SelfUser selfUser;
    
    private DiscordConnectionManager connectionManager;
    
    @BeforeEach
    void setUp() {
        // Setup mocks
        lenient().when(plugin.getDebugLogger()).thenReturn(debugLogger);
        lenient().when(debugLogger.forContext(anyString())).thenReturn(contextualLogger);
        lenient().when(configManager.getConfig()).thenReturn(config);
        lenient().when(configManager.getBotToken()).thenReturn("test-bot-token");
        
        // Setup default config values
        lenient().when(config.isEnabled()).thenReturn(true);
        lenient().when(config.getConnectionTimeout()).thenReturn(30000L);
        lenient().when(config.getReconnectAttempts()).thenReturn(3);
        lenient().when(config.getReconnectDelay()).thenReturn(1000L); // 1 second for faster tests
        
        // Setup JDA mocks
        lenient().when(jda.getSelfUser()).thenReturn(selfUser);
        lenient().when(selfUser.getAsTag()).thenReturn("TestBot#1234");
        lenient().when(jda.getStatus()).thenReturn(JDA.Status.CONNECTED);
        
        connectionManager = new DiscordConnectionManager(plugin, configManager);
    }
    
    @AfterEach
    void tearDown() {
        if (connectionManager != null) {
            connectionManager.shutdown();
        }
    }
    
    @Test
    void testReconnectionAfterDisconnect() throws InterruptedException {
        // Create session disconnect event
        SessionDisconnectEvent event = mock(SessionDisconnectEvent.class);
        lenient().when(event.getCloseCode()).thenReturn(CloseCode.UNKNOWN_ERROR);
        
        // Mock connection manager to spy on reconnection attempts
        connectionManager = spy(connectionManager);
        lenient().doReturn(CompletableFuture.completedFuture(true)).when(connectionManager).connect();
        
        // Handle session disconnect event
        connectionManager.onSessionDisconnect(event);
        
        // Wait a bit for reconnection to be scheduled
        Thread.sleep(100);
        
        // Verify reconnection logging
        verify(contextualLogger).warn(contains("Discord session disconnected. Code:"));
        verify(contextualLogger).info("Attempting to reconnect to Discord...");
    }
    
    @Test
    void testReconnectionWhenDisabled() {
        // Setup disabled config
        when(config.isEnabled()).thenReturn(false);
        
        // Create session disconnect event
        SessionDisconnectEvent event = mock(SessionDisconnectEvent.class);
        when(event.getCloseCode()).thenReturn(CloseCode.UNKNOWN_ERROR);
        
        // Handle session disconnect event
        connectionManager.onSessionDisconnect(event);
        
        // Verify no reconnection attempt is made
        verify(contextualLogger).debug("Discord integration is disabled - skipping reconnection");
    }
    
    @Test
    void testMaximumReconnectionAttempts() throws InterruptedException {
        // Setup config with 2 max attempts
        when(config.getReconnectAttempts()).thenReturn(2);
        
        // Mock connection manager to always fail connection
        connectionManager = spy(connectionManager);
        doReturn(CompletableFuture.completedFuture(false)).when(connectionManager).connect();
        
        // Simulate multiple disconnections to trigger reconnection attempts
        SessionDisconnectEvent event = mock(SessionDisconnectEvent.class);
        when(event.getCloseCode()).thenReturn(CloseCode.UNKNOWN_ERROR);
        
        // First disconnect - should trigger first reconnection attempt
        connectionManager.onSessionDisconnect(event);
        Thread.sleep(50);
        
        // Second disconnect - should trigger second reconnection attempt
        connectionManager.onSessionDisconnect(event);
        Thread.sleep(50);
        
        // Third disconnect - should reach maximum attempts
        connectionManager.onSessionDisconnect(event);
        Thread.sleep(50);
        
        // Verify maximum attempts reached message
        verify(contextualLogger, timeout(2000)).error("Maximum reconnection attempts (2) reached - giving up");
    }
    
    @Test
    void testExponentialBackoffDelay() throws InterruptedException {
        // Setup config with longer base delay for testing
        when(config.getReconnectDelay()).thenReturn(100L); // 100ms base delay
        
        // Mock connection manager to track reconnection timing
        connectionManager = spy(connectionManager);
        doReturn(CompletableFuture.completedFuture(false)).when(connectionManager).connect();
        
        // Create session disconnect event
        SessionDisconnectEvent event = mock(SessionDisconnectEvent.class);
        when(event.getCloseCode()).thenReturn(CloseCode.UNKNOWN_ERROR);
        
        long startTime = System.currentTimeMillis();
        
        // Trigger first reconnection attempt
        connectionManager.onSessionDisconnect(event);
        
        // Wait for first attempt to be scheduled and fail
        Thread.sleep(200);
        
        // Verify first attempt was logged with base delay
        verify(contextualLogger, timeout(1000)).info(contains("Attempting Discord reconnection 1/3 in 0 seconds"));
        
        // Trigger second reconnection attempt
        connectionManager.onSessionDisconnect(event);
        
        // Wait for second attempt
        Thread.sleep(300);
        
        // Verify second attempt was logged with exponential backoff
        verify(contextualLogger, timeout(1000)).info(contains("Attempting Discord reconnection 2/3"));
    }
    
    @Test
    void testSuccessfulReconnection() throws InterruptedException {
        // Mock connection manager to succeed on second attempt
        connectionManager = spy(connectionManager);
        doReturn(CompletableFuture.completedFuture(false))
            .doReturn(CompletableFuture.completedFuture(true))
            .when(connectionManager).connect();
        
        // Create session disconnect event
        SessionDisconnectEvent event = mock(SessionDisconnectEvent.class);
        when(event.getCloseCode()).thenReturn(CloseCode.UNKNOWN_ERROR);
        
        // Trigger reconnection
        connectionManager.onSessionDisconnect(event);
        
        // Wait for reconnection attempts
        Thread.sleep(200);
        
        // Wait for reconnection attempts to complete
        try {
            Thread.sleep(1500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Verify successful reconnection message (may take time due to async nature)
        verify(contextualLogger, timeout(3000)).info(contains("Discord reconnection successful after"));
    }
    
    @Test
    void testReconnectionWhileShuttingDown() {
        // Shutdown the connection manager
        connectionManager.shutdown();
        
        // Create session disconnect event
        SessionDisconnectEvent event = mock(SessionDisconnectEvent.class);
        when(event.getCloseCode()).thenReturn(CloseCode.UNKNOWN_ERROR);
        
        // Handle session disconnect event
        connectionManager.onSessionDisconnect(event);
        
        // Verify no reconnection attempt is made (no logging for reconnection)
        verify(contextualLogger, never()).info(contains("Attempting Discord reconnection"));
    }
    
    @Test
    void testReconnectionWhileConnecting() {
        // Mock connection manager to be in connecting state
        connectionManager = spy(connectionManager);
        
        // Simulate connecting state by making connect() block
        doReturn(new CompletableFuture<>()).when(connectionManager).connect(); // Never completes
        
        // Start a connection attempt
        connectionManager.connect();
        
        // Create session disconnect event
        SessionDisconnectEvent event = mock(SessionDisconnectEvent.class);
        when(event.getCloseCode()).thenReturn(CloseCode.UNKNOWN_ERROR);
        
        // Handle session disconnect event while connecting
        connectionManager.onSessionDisconnect(event);
        
        // Verify disconnect is logged but no reconnection attempt is made
        verify(contextualLogger).warn(contains("Discord session disconnected"));
        // Should not attempt reconnection while already connecting
    }
    
    @Test
    void testHealthMonitoringDetectsDisconnection() throws InterruptedException {
        // This test would require more complex mocking to test the health monitoring
        // For now, we'll test that the connection manager can detect when JDA status changes
        
        // Mock connection manager with health monitoring
        connectionManager = spy(connectionManager);
        
        // Mock JDA to return disconnected status
        lenient().when(jda.getStatus()).thenReturn(JDA.Status.DISCONNECTED);
        
        // The health monitoring runs every 30 seconds, so we can't easily test it in unit tests
        // Instead, we'll verify that the isConnected() method properly checks JDA status
        lenient().doReturn(jda).when(connectionManager).getJDA();
        
        // Even though we have a JDA instance, isConnected should return false due to status
        assertFalse(connectionManager.isConnected());
    }
    
    @Test
    void testConnectionStatsWithReconnectionAttempts() {
        // Mock some reconnection attempts by triggering disconnects
        SessionDisconnectEvent event = mock(SessionDisconnectEvent.class);
        lenient().when(event.getCloseCode()).thenReturn(CloseCode.UNKNOWN_ERROR);
        
        // Mock connection manager to fail reconnection
        connectionManager = spy(connectionManager);
        lenient().doReturn(CompletableFuture.completedFuture(false)).when(connectionManager).connect();
        
        // Trigger a disconnect to increment reconnection attempts
        connectionManager.onSessionDisconnect(event);
        
        // Get connection stats
        String stats = connectionManager.getConnectionStats();
        
        // Wait a bit for the disconnect to be processed
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Get connection stats after disconnect
        stats = connectionManager.getConnectionStats();
        
        // Verify stats show reconnection attempts
        assertNotNull(stats);
        assertTrue(stats.contains("Reconnect Attempts:"));
        // Last connection attempt may or may not be present depending on timing
        assertTrue(stats.contains("Connected: false"));
    }
}