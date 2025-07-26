package net.lumalyte.lumasg.discord.connection;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.events.session.SessionDisconnectEvent;
import net.dv8tion.jda.api.events.session.SessionResumeEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import net.lumalyte.lumasg.LumaSG;
import net.lumalyte.lumasg.discord.config.DiscordConfig;
import net.lumalyte.lumasg.discord.config.DiscordConfigManager;
import net.lumalyte.lumasg.util.core.DebugLogger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.annotation.Nonnull;

import java.time.Instant;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Manages JDA connection, reconnection, and bot lifecycle.
 * Handles connection health monitoring and exponential backoff reconnection logic.
 */
public class DiscordConnectionManager extends ListenerAdapter {
    
    private final @NotNull LumaSG plugin;
    private final @NotNull DiscordConfigManager configManager;
    private final @NotNull DebugLogger.ContextualLogger logger;
    
    // Connection state
    private final @NotNull AtomicReference<JDA> jda = new AtomicReference<>();
    private final @NotNull AtomicBoolean isConnected = new AtomicBoolean(false);
    private final @NotNull AtomicBoolean isConnecting = new AtomicBoolean(false);
    private final @NotNull AtomicBoolean isShuttingDown = new AtomicBoolean(false);
    
    // Reconnection state
    private final @NotNull AtomicInteger reconnectAttempts = new AtomicInteger(0);
    private final @NotNull AtomicReference<Instant> lastConnectionAttempt = new AtomicReference<>();
    private final @NotNull AtomicReference<Instant> lastSuccessfulConnection = new AtomicReference<>();
    
    // Executors
    private final @NotNull ScheduledExecutorService reconnectExecutor;
    private final @NotNull ExecutorService connectionExecutor;
    
    // Connection monitoring
    private @Nullable ScheduledFuture<?> healthCheckTask;
    private @Nullable ScheduledFuture<?> reconnectTask;
    
    /**
     * Creates a new Discord connection manager.
     * 
     * @param plugin The plugin instance
     * @param configManager The Discord configuration manager
     */
    public DiscordConnectionManager(@NotNull LumaSG plugin, @NotNull DiscordConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.logger = plugin.getDebugLogger().forContext("DiscordConnection");
        
        // Create thread pools with proper naming
        this.reconnectExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "Discord-Reconnect");
            thread.setDaemon(true);
            return thread;
        });
        
        this.connectionExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "Discord-Connection");
            thread.setDaemon(true);
            return thread;
        });
    }
    
    /**
     * Attempts to connect to Discord using the configured bot token.
     * 
     * @return A CompletableFuture that completes with true if connection succeeds, false otherwise
     */
    public @NotNull CompletableFuture<Boolean> connect() {
        if (isShuttingDown.get()) {
            logger.debug("Cannot connect - connection manager is shutting down");
            return CompletableFuture.completedFuture(false);
        }
        
        if (isConnecting.get()) {
            logger.debug("Connection attempt already in progress");
            return CompletableFuture.completedFuture(false);
        }
        
        if (isConnected.get()) {
            logger.debug("Already connected to Discord");
            return CompletableFuture.completedFuture(true);
        }
        
        return CompletableFuture.supplyAsync(this::performConnection, connectionExecutor);
    }
    
    /**
     * Performs the actual connection to Discord.
     * 
     * @return true if connection succeeds, false otherwise
     */
    private boolean performConnection() {
        if (!isConnecting.compareAndSet(false, true)) {
            return false;
        }
        
        try {
            logger.debug("Attempting to connect to Discord...");
            lastConnectionAttempt.set(Instant.now());
            
            // Get configuration
            DiscordConfig config = configManager.getConfig();
            if (config == null || !config.isEnabled()) {
                logger.warn("Discord integration is disabled or not configured");
                return false;
            }
            
            String botToken = configManager.getBotToken();
            if (botToken == null || botToken.isEmpty()) {
                logger.error("Discord bot token is not configured");
                return false;
            }
            
            // Disconnect existing connection if any
            disconnectInternal();
            
            // Build JDA instance
            JDABuilder builder = JDABuilder.createDefault(botToken)
                    .setActivity(Activity.playing("Survival Games"))
                    .enableIntents(
                            GatewayIntent.GUILD_MESSAGES,
                            GatewayIntent.MESSAGE_CONTENT,
                            GatewayIntent.GUILD_MEMBERS
                    )
                    .setMemberCachePolicy(MemberCachePolicy.NONE)
                    .disableCache(
                            CacheFlag.ACTIVITY,
                            CacheFlag.VOICE_STATE,
                            CacheFlag.EMOJI,
                            CacheFlag.CLIENT_STATUS,
                            CacheFlag.ONLINE_STATUS
                    )
                    .addEventListeners(this);
            
            // Set connection timeout
            long timeoutMs = config.getConnectionTimeout();
            if (timeoutMs > 0) {
                builder.setRequestTimeoutRetry(true);
            }
            
            // Build and await ready
            JDA newJda = builder.build();
            
            // Wait for ready
            newJda.awaitReady();
            
            // Store JDA instance
            jda.set(newJda);
            isConnected.set(true);
            reconnectAttempts.set(0);
            lastSuccessfulConnection.set(Instant.now());
            
            logger.info("Successfully connected to Discord as: " + newJda.getSelfUser().getAsTag());
            
            // Start health monitoring
            startHealthMonitoring();
            
            return true;
            
        } catch (InterruptedException e) {
            logger.warn("Discord connection was interrupted");
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            logger.error("Failed to connect to Discord", e);
            return false;
        } finally {
            isConnecting.set(false);
        }
    }
    
    /**
     * Disconnects from Discord and cleans up resources.
     */
    public void disconnect() {
        logger.debug("Disconnecting from Discord...");
        isShuttingDown.set(true);
        
        // Cancel reconnection attempts
        cancelReconnectTask();
        
        // Stop health monitoring
        stopHealthMonitoring();
        
        // Disconnect JDA
        disconnectInternal();
        
        logger.debug("Disconnected from Discord");
    }
    
    /**
     * Internal method to disconnect JDA without affecting shutdown state.
     */
    private void disconnectInternal() {
        JDA currentJda = jda.getAndSet(null);
        if (currentJda != null) {
            isConnected.set(false);
            
            try {
                currentJda.shutdown();
                if (!currentJda.awaitShutdown(10, TimeUnit.SECONDS)) {
                    logger.warn("Discord JDA shutdown timed out, forcing shutdown");
                    currentJda.shutdownNow();
                }
            } catch (InterruptedException e) {
                logger.warn("Discord JDA shutdown was interrupted");
                Thread.currentThread().interrupt();
                currentJda.shutdownNow();
            } catch (Exception e) {
                logger.error("Error during Discord JDA shutdown", e);
            }
        }
    }
    
    /**
     * Checks if the bot is currently connected to Discord.
     * 
     * @return true if connected, false otherwise
     */
    public boolean isConnected() {
        JDA currentJda = jda.get();
        return isConnected.get() && currentJda != null && currentJda.getStatus() == JDA.Status.CONNECTED;
    }
    
    /**
     * Gets the current JDA instance.
     * 
     * @return The JDA instance, or null if not connected
     */
    public @Nullable JDA getJDA() {
        return isConnected() ? jda.get() : null;
    }
    
    /**
     * Gets a text channel by ID.
     * 
     * @param channelId The channel ID
     * @return The text channel, or null if not found or not connected
     */
    public @Nullable TextChannel getTextChannel(@NotNull String channelId) {
        JDA currentJda = getJDA();
        if (currentJda == null) {
            return null;
        }
        
        try {
            return currentJda.getTextChannelById(channelId);
        } catch (Exception e) {
            logger.warn("Failed to get text channel: " + channelId, e);
            return null;
        }
    }
    
    /**
     * Attempts to reconnect to Discord with exponential backoff.
     */
    private void attemptReconnection() {
        if (isShuttingDown.get() || isConnecting.get()) {
            return;
        }
        
        DiscordConfig config = configManager.getConfig();
        if (config == null || !config.isEnabled()) {
            logger.debug("Discord integration is disabled - skipping reconnection");
            return;
        }
        
        int currentAttempts = reconnectAttempts.get();
        int maxAttempts = config.getReconnectAttempts();
        
        if (currentAttempts >= maxAttempts) {
            logger.error("Maximum reconnection attempts (" + maxAttempts + ") reached - giving up");
            return;
        }
        
        reconnectAttempts.incrementAndGet();
        
        // Calculate exponential backoff delay
        long baseDelay = config.getReconnectDelay();
        long delay = Math.min(baseDelay * (1L << Math.min(currentAttempts, 10)), 300000); // Max 5 minutes
        
        logger.info("Attempting Discord reconnection " + (currentAttempts + 1) + "/" + maxAttempts + 
                   " in " + (delay / 1000) + " seconds...");
        
        reconnectTask = reconnectExecutor.schedule(() -> {
            connect().thenAccept(success -> {
                if (!success) {
                    logger.warn("Discord reconnection attempt " + (currentAttempts + 1) + " failed");
                    // Schedule next attempt
                    attemptReconnection();
                } else {
                    logger.info("Discord reconnection successful after " + (currentAttempts + 1) + " attempts");
                }
            });
        }, delay, TimeUnit.MILLISECONDS);
    }
    
    /**
     * Starts health monitoring for the Discord connection.
     */
    private void startHealthMonitoring() {
        stopHealthMonitoring();
        
        healthCheckTask = reconnectExecutor.scheduleWithFixedDelay(() -> {
            try {
                JDA currentJda = jda.get();
                if (currentJda == null || currentJda.getStatus() != JDA.Status.CONNECTED) {
                    logger.warn("Discord connection health check failed - connection lost");
                    isConnected.set(false);
                    
                    if (!isShuttingDown.get()) {
                        attemptReconnection();
                    }
                }
            } catch (Exception e) {
                logger.error("Error during Discord connection health check", e);
            }
        }, 30, 30, TimeUnit.SECONDS); // Check every 30 seconds
    }
    
    /**
     * Stops health monitoring.
     */
    private void stopHealthMonitoring() {
        if (healthCheckTask != null && !healthCheckTask.isCancelled()) {
            healthCheckTask.cancel(false);
            healthCheckTask = null;
        }
    }
    
    /**
     * Cancels any pending reconnection task.
     */
    private void cancelReconnectTask() {
        if (reconnectTask != null && !reconnectTask.isCancelled()) {
            reconnectTask.cancel(false);
            reconnectTask = null;
        }
    }
    
    /**
     * Shuts down the connection manager and cleans up resources.
     */
    public void shutdown() {
        logger.debug("Shutting down Discord connection manager...");
        
        disconnect();
        
        // Shutdown executors
        reconnectExecutor.shutdown();
        connectionExecutor.shutdown();
        
        try {
            if (!reconnectExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                reconnectExecutor.shutdownNow();
            }
            if (!connectionExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                connectionExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            reconnectExecutor.shutdownNow();
            connectionExecutor.shutdownNow();
        }
        
        logger.debug("Discord connection manager shutdown complete");
    }
    
    /**
     * Gets connection statistics for monitoring and debugging.
     * 
     * @return A formatted string with connection statistics
     */
    public @NotNull String getConnectionStats() {
        StringBuilder stats = new StringBuilder();
        stats.append("Discord Connection Statistics:\n");
        stats.append("  Connected: ").append(isConnected()).append("\n");
        stats.append("  Connecting: ").append(isConnecting.get()).append("\n");
        stats.append("  Reconnect Attempts: ").append(reconnectAttempts.get()).append("\n");
        
        Instant lastAttempt = lastConnectionAttempt.get();
        if (lastAttempt != null) {
            stats.append("  Last Connection Attempt: ").append(lastAttempt).append("\n");
        }
        
        Instant lastSuccess = lastSuccessfulConnection.get();
        if (lastSuccess != null) {
            stats.append("  Last Successful Connection: ").append(lastSuccess).append("\n");
        }
        
        JDA currentJda = jda.get();
        if (currentJda != null) {
            stats.append("  JDA Status: ").append(currentJda.getStatus()).append("\n");
            stats.append("  Bot User: ").append(currentJda.getSelfUser().getAsTag()).append("\n");
            stats.append("  Guilds: ").append(currentJda.getGuilds().size()).append("\n");
            stats.append("  Ping: ").append(currentJda.getGatewayPing()).append("ms\n");
        }
        
        return stats.toString();
    }
    
    // JDA Event Handlers
    
    @Override
    public void onReady(@Nonnull ReadyEvent event) {
        logger.info("Discord bot is ready! Connected as: " + event.getJDA().getSelfUser().getAsTag());
        logger.debug("Connected to " + event.getJDA().getGuilds().size() + " guilds");
        
        // Reset reconnection attempts on successful connection
        reconnectAttempts.set(0);
        lastSuccessfulConnection.set(Instant.now());
    }
    
    @Override
    public void onSessionDisconnect(@Nonnull SessionDisconnectEvent event) {
        logger.warn("Discord session disconnected. Code: " + event.getCloseCode());
        
        isConnected.set(false);
        
        // Attempt reconnection if not shutting down
        if (!isShuttingDown.get()) {
            logger.info("Attempting to reconnect to Discord...");
            attemptReconnection();
        }
    }
    
    @Override
    public void onSessionResume(@Nonnull SessionResumeEvent event) {
        logger.info("Discord session resumed successfully");
        isConnected.set(true);
        reconnectAttempts.set(0);
        lastSuccessfulConnection.set(Instant.now());
    }
}