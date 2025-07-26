package net.lumalyte.lumasg.discord.connection;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.SelfUser;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.events.session.SessionDisconnectEvent;
import net.dv8tion.jda.api.events.session.SessionResumeEvent;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for DiscordConnectionManager.
 * Tests connection management, reconnection logic, and error handling.
 */
@ExtendWith(MockitoExtension.class)
class DiscordConnectionManagerTest {
    
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
    
    @Mock
    private TextChannel textChannel;
    
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
        lenient().when(config.getReconnectAttempts()).thenReturn(5);
        lenient().when(config.getReconnectDelay()).thenReturn(5000L);
        
        // Setup JDA mocks
        lenient().when(jda.getSelfUser()).thenReturn(selfUser);
        lenient().when(selfUser.getAsTag()).thenReturn("TestBot#1234");
        lenient().when(jda.getStatus()).thenReturn(JDA.Status.CONNECTED);
        lenient().when(jda.getTextChannelById(anyString())).thenReturn(textChannel);
        
        connectionManager = new DiscordConnectionManager(plugin, configManager);
    }
    
    @AfterEach
    void tearDown() {
        if (connectionManager != null) {
            connectionManager.shutdown();
        }
    }
    
    @Test
    void testInitialState() {
        // Test initial state
        assertFalse(connectionManager.isConnected());
        assertNull(connectionManager.getJDA());
        assertNull(connectionManager.getTextChannel("123456789"));
        
        // Verify connection stats contain expected information
        String stats = connectionManager.getConnectionStats();
        assertNotNull(stats);
        assertTrue(stats.contains("Connected: false"));
        assertTrue(stats.contains("Connecting: false"));
        assertTrue(stats.contains("Reconnect Attempts: 0"));
    }
    
    @Test
    void testConnectWhenDisabled() {
        // Setup disabled config
        when(config.isEnabled()).thenReturn(false);
        
        // Attempt connection
        CompletableFuture<Boolean> result = connectionManager.connect();
        
        // Verify connection fails
        assertFalse(result.join());
        assertFalse(connectionManager.isConnected());
        
        // Verify appropriate logging
        verify(contextualLogger).warn("Discord integration is disabled or not configured");
    }
    
    @Test
    void testConnectWithNullToken() {
        // Setup null token
        when(configManager.getBotToken()).thenReturn(null);
        
        // Attempt connection
        CompletableFuture<Boolean> result = connectionManager.connect();
        
        // Verify connection fails
        assertFalse(result.join());
        assertFalse(connectionManager.isConnected());
        
        // Verify appropriate logging
        verify(contextualLogger).error("Discord bot token is not configured");
    }
    
    @Test
    void testConnectWithEmptyToken() {
        // Setup empty token
        when(configManager.getBotToken()).thenReturn("");
        
        // Attempt connection
        CompletableFuture<Boolean> result = connectionManager.connect();
        
        // Verify connection fails
        assertFalse(result.join());
        assertFalse(connectionManager.isConnected());
        
        // Verify appropriate logging
        verify(contextualLogger).error("Discord bot token is not configured");
    }
    
    @Test
    void testMultipleConnectAttempts() {
        // First connection attempt
        CompletableFuture<Boolean> result1 = connectionManager.connect();
        
        // Second connection attempt while first is in progress
        CompletableFuture<Boolean> result2 = connectionManager.connect();
        
        // Second attempt should return false immediately
        assertFalse(result2.join());
        
        // The first attempt will fail due to invalid token, so we just verify both return false
        assertFalse(result1.join());
    }
    
    @Test
    void testConnectWhenAlreadyConnected() {
        // Mock successful connection state by setting up JDA properly
        connectionManager = spy(connectionManager);
        
        // Use reflection to set the JDA and connection state
        try {
            java.lang.reflect.Field jdaField = DiscordConnectionManager.class.getDeclaredField("jda");
            jdaField.setAccessible(true);
            java.util.concurrent.atomic.AtomicReference<JDA> jdaRef = 
                (java.util.concurrent.atomic.AtomicReference<JDA>) jdaField.get(connectionManager);
            jdaRef.set(jda);
            
            java.lang.reflect.Field connectedField = DiscordConnectionManager.class.getDeclaredField("isConnected");
            connectedField.setAccessible(true);
            java.util.concurrent.atomic.AtomicBoolean connectedRef = 
                (java.util.concurrent.atomic.AtomicBoolean) connectedField.get(connectionManager);
            connectedRef.set(true);
        } catch (Exception e) {
            fail("Failed to set up connection state: " + e.getMessage());
        }
        
        // Attempt connection
        CompletableFuture<Boolean> result = connectionManager.connect();
        
        // Verify returns true immediately
        assertTrue(result.join());
        
        // Verify appropriate logging
        verify(contextualLogger).debug("Already connected to Discord");
    }
    
    @Test
    void testConnectWhenShuttingDown() {
        // Shutdown the connection manager
        connectionManager.shutdown();
        
        // Attempt connection
        CompletableFuture<Boolean> result = connectionManager.connect();
        
        // Verify connection fails
        assertFalse(result.join());
        
        // Verify appropriate logging
        verify(contextualLogger).debug("Cannot connect - connection manager is shutting down");
    }
    
    @Test
    void testDisconnect() {
        // Test disconnect
        connectionManager.disconnect();
        
        // Verify state
        assertFalse(connectionManager.isConnected());
        assertNull(connectionManager.getJDA());
        
        // Verify appropriate logging
        verify(contextualLogger).debug("Disconnecting from Discord...");
        verify(contextualLogger).debug("Disconnected from Discord");
    }
    
    @Test
    void testGetTextChannelWhenNotConnected() {
        // Test getting text channel when not connected
        TextChannel channel = connectionManager.getTextChannel("123456789");
        
        // Verify returns null
        assertNull(channel);
    }
    
    @Test
    void testGetTextChannelWithException() {
        // Mock connected state
        connectionManager = spy(connectionManager);
        lenient().doReturn(true).when(connectionManager).isConnected();
        lenient().doReturn(jda).when(connectionManager).getJDA();
        
        // Mock exception when getting channel
        when(jda.getTextChannelById(anyString())).thenThrow(new RuntimeException("Test exception"));
        
        // Test getting text channel
        TextChannel channel = connectionManager.getTextChannel("123456789");
        
        // Verify returns null and logs warning
        assertNull(channel);
        verify(contextualLogger).warn(eq("Failed to get text channel: 123456789"), any(RuntimeException.class));
    }
    
    @Test
    void testOnReadyEvent() {
        // Create ready event
        ReadyEvent event = mock(ReadyEvent.class);
        when(event.getJDA()).thenReturn(jda);
        when(jda.getGuilds()).thenReturn(java.util.Collections.emptyList());
        
        // Handle ready event
        connectionManager.onReady(event);
        
        // Verify appropriate logging
        verify(contextualLogger).info("Discord bot is ready! Connected as: TestBot#1234");
        verify(contextualLogger).debug("Connected to 0 guilds");
    }
    
    @Test
    void testOnSessionDisconnect() {
        // Create session disconnect event
        SessionDisconnectEvent event = mock(SessionDisconnectEvent.class);
        when(event.getCloseCode()).thenReturn(CloseCode.UNKNOWN_ERROR);
        
        // Handle session disconnect event
        connectionManager.onSessionDisconnect(event);
        
        // Verify appropriate logging (CloseCode toString includes more details)
        verify(contextualLogger).warn(contains("Discord session disconnected. Code:"));
        verify(contextualLogger).info("Attempting to reconnect to Discord...");
    }
    
    @Test
    void testOnSessionDisconnectLogging() {
        // Create session disconnect event
        SessionDisconnectEvent event = mock(SessionDisconnectEvent.class);
        when(event.getCloseCode()).thenReturn(CloseCode.UNKNOWN_ERROR);
        
        // Handle session disconnect event
        connectionManager.onSessionDisconnect(event);
        
        // Verify appropriate logging (CloseCode toString includes more details)
        verify(contextualLogger).warn(contains("Discord session disconnected. Code:"));
    }
    
    @Test
    void testOnSessionResume() {
        // Create session resume event
        SessionResumeEvent event = mock(SessionResumeEvent.class);
        
        // Handle session resume event
        connectionManager.onSessionResume(event);
        
        // Verify appropriate logging
        verify(contextualLogger).info("Discord session resumed successfully");
    }
    
    @Test
    void testConnectionStatsWhenConnected() {
        // Mock connected state by setting up JDA properly
        connectionManager = spy(connectionManager);
        when(jda.getGuilds()).thenReturn(java.util.Collections.emptyList());
        when(jda.getGatewayPing()).thenReturn(50L);
        
        // Use reflection to set the JDA and connection state
        try {
            java.lang.reflect.Field jdaField = DiscordConnectionManager.class.getDeclaredField("jda");
            jdaField.setAccessible(true);
            java.util.concurrent.atomic.AtomicReference<JDA> jdaRef = 
                (java.util.concurrent.atomic.AtomicReference<JDA>) jdaField.get(connectionManager);
            jdaRef.set(jda);
            
            java.lang.reflect.Field connectedField = DiscordConnectionManager.class.getDeclaredField("isConnected");
            connectedField.setAccessible(true);
            java.util.concurrent.atomic.AtomicBoolean connectedRef = 
                (java.util.concurrent.atomic.AtomicBoolean) connectedField.get(connectionManager);
            connectedRef.set(true);
        } catch (Exception e) {
            fail("Failed to set up connection state: " + e.getMessage());
        }
        
        // Get connection stats
        String stats = connectionManager.getConnectionStats();
        
        // Verify stats contain expected information
        assertNotNull(stats);
        assertTrue(stats.contains("Connected: true"));
        assertTrue(stats.contains("JDA Status: CONNECTED"));
        assertTrue(stats.contains("Bot User: TestBot#1234"));
        assertTrue(stats.contains("Guilds: 0"));
        assertTrue(stats.contains("Ping: 50ms"));
    }
    
    @Test
    void testShutdown() {
        // Test shutdown
        connectionManager.shutdown();
        
        // Verify state
        assertFalse(connectionManager.isConnected());
        
        // Verify appropriate logging
        verify(contextualLogger).debug("Shutting down Discord connection manager...");
        verify(contextualLogger).debug("Discord connection manager shutdown complete");
    }
    
    @Test
    void testShutdownWithConnectedJDA() throws InterruptedException {
        // Mock connected JDA by setting it in the atomic reference
        when(jda.awaitShutdown(anyLong(), any(TimeUnit.class))).thenReturn(true);
        
        // Use reflection to set the JDA
        try {
            java.lang.reflect.Field jdaField = DiscordConnectionManager.class.getDeclaredField("jda");
            jdaField.setAccessible(true);
            java.util.concurrent.atomic.AtomicReference<JDA> jdaRef = 
                (java.util.concurrent.atomic.AtomicReference<JDA>) jdaField.get(connectionManager);
            jdaRef.set(jda);
        } catch (Exception e) {
            fail("Failed to set up JDA: " + e.getMessage());
        }
        
        // Test shutdown
        connectionManager.shutdown();
        
        // Verify JDA shutdown was called
        verify(jda).shutdown();
        verify(jda).awaitShutdown(10, TimeUnit.SECONDS);
    }
    
    @Test
    void testShutdownWithJDATimeout() throws InterruptedException {
        // Mock connected JDA that times out
        when(jda.awaitShutdown(anyLong(), any(TimeUnit.class))).thenReturn(false);
        
        // Use reflection to set the JDA
        try {
            java.lang.reflect.Field jdaField = DiscordConnectionManager.class.getDeclaredField("jda");
            jdaField.setAccessible(true);
            java.util.concurrent.atomic.AtomicReference<JDA> jdaRef = 
                (java.util.concurrent.atomic.AtomicReference<JDA>) jdaField.get(connectionManager);
            jdaRef.set(jda);
        } catch (Exception e) {
            fail("Failed to set up JDA: " + e.getMessage());
        }
        
        // Test shutdown
        connectionManager.shutdown();
        
        // Verify forced shutdown was called
        verify(jda).shutdown();
        verify(jda).awaitShutdown(10, TimeUnit.SECONDS);
        verify(jda).shutdownNow();
        verify(contextualLogger).warn("Discord JDA shutdown timed out, forcing shutdown");
    }
    
    @Test
    void testShutdownWithInterruption() throws InterruptedException {
        // Mock connected JDA that gets interrupted
        when(jda.awaitShutdown(anyLong(), any(TimeUnit.class))).thenThrow(new InterruptedException("Test interruption"));
        
        // Use reflection to set the JDA
        try {
            java.lang.reflect.Field jdaField = DiscordConnectionManager.class.getDeclaredField("jda");
            jdaField.setAccessible(true);
            java.util.concurrent.atomic.AtomicReference<JDA> jdaRef = 
                (java.util.concurrent.atomic.AtomicReference<JDA>) jdaField.get(connectionManager);
            jdaRef.set(jda);
        } catch (Exception e) {
            fail("Failed to set up JDA: " + e.getMessage());
        }
        
        // Test shutdown
        connectionManager.shutdown();
        
        // Verify forced shutdown was called
        verify(jda).shutdown();
        verify(jda).shutdownNow();
        verify(contextualLogger).warn("Discord JDA shutdown was interrupted");
    }
}