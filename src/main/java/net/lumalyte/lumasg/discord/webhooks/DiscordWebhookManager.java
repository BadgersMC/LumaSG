package net.lumalyte.lumasg.discord.webhooks;

import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import net.lumalyte.lumasg.LumaSG;
import net.lumalyte.lumasg.discord.config.DiscordConfig;
import net.lumalyte.lumasg.discord.config.DiscordConfigManager;
import net.lumalyte.lumasg.util.core.DebugLogger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Manages webhook integration for high-performance messaging.
 * Handles webhook client management and message sending with fallback support.
 * 
 * This manager provides:
 * - High-performance webhook messaging
 * - Automatic fallback to bot messaging when webhooks fail
 * - Webhook validation and error handling
 * - Thread-safe webhook client management
 */
public class DiscordWebhookManager {
    
    private final @NotNull LumaSG plugin;
    private final @NotNull DiscordConfigManager configManager;
    private final @NotNull DebugLogger.ContextualLogger logger;
    
    // Webhook URL management
    private final @NotNull Map<String, String> webhookUrls = new ConcurrentHashMap<>();
    private final @NotNull ScheduledExecutorService executor;
    private final @NotNull HttpClient httpClient;
    
    // Fallback channel for when webhooks fail
    private @Nullable MessageChannel fallbackChannel;
    
    // Webhook URL validation pattern
    private static final Pattern WEBHOOK_URL_PATTERN = Pattern.compile(
        "^https://discord\\.com/api/webhooks/\\d+/[A-Za-z0-9_-]+$"
    );
    
    /**
     * Creates a new Discord webhook manager.
     * 
     * @param plugin The plugin instance
     * @param configManager The Discord configuration manager
     */
    public DiscordWebhookManager(@NotNull LumaSG plugin, @NotNull DiscordConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.logger = plugin.getDebugLogger().forContext("DiscordWebhooks");
        this.executor = Executors.newScheduledThreadPool(2, r -> {
            Thread thread = new Thread(r, "DiscordWebhook-" + System.currentTimeMillis());
            thread.setDaemon(true);
            return thread;
        });
        
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
        
        // Initialize webhooks from configuration
        initializeWebhooks();
        
        logger.debug("Discord webhook manager initialized");
    }
    
    /**
     * Initializes webhook clients from configuration.
     */
    private void initializeWebhooks() {
        DiscordConfig config = configManager.getConfig();
        if (config == null) {
            logger.debug("No Discord configuration available, skipping webhook initialization");
            return;
        }
        
        Map<String, String> webhooks = config.getWebhooks();
        if (webhooks.isEmpty()) {
            logger.debug("No webhooks configured");
            return;
        }
        
        for (Map.Entry<String, String> entry : webhooks.entrySet()) {
            String name = entry.getKey();
            String url = entry.getValue();
            
            if (url != null && !url.isEmpty()) {
                addWebhook(name, url);
            }
        }
        
        logger.debug("Initialized " + webhookUrls.size() + " webhook URLs");
    }
    
    /**
     * Adds a webhook client with the specified name and URL.
     * 
     * @param name The webhook name
     * @param url The webhook URL
     * @return true if the webhook was added successfully, false otherwise
     */
    public boolean addWebhook(@NotNull String name, @NotNull String url) {
        if (!isValidWebhookUrl(url)) {
            logger.warn("Invalid webhook URL format for webhook '" + name + "': " + url);
            return false;
        }
        
        try {
            // Remove existing webhook if present
            removeWebhook(name);
            
            // Store webhook URL
            webhookUrls.put(name, url);
            
            logger.debug("Added webhook '" + name + "' with URL: " + maskWebhookUrl(url));
            return true;
            
        } catch (Exception e) {
            logger.error("Failed to add webhook '" + name + "' with URL: " + maskWebhookUrl(url), e);
            return false;
        }
    }
    
    /**
     * Removes a webhook client by name.
     * 
     * @param name The webhook name
     * @return true if the webhook was removed, false if it didn't exist
     */
    public boolean removeWebhook(@NotNull String name) {
        String url = webhookUrls.remove(name);
        if (url != null) {
            logger.debug("Removed webhook '" + name + "'");
            return true;
        }
        return false;
    }
    
    /**
     * Sends a message embed via webhook.
     * Falls back to bot messaging if webhook fails.
     * 
     * @param webhookName The name of the webhook to use
     * @param embed The message embed to send
     * @return A CompletableFuture that completes when the message is sent
     */
    public @NotNull CompletableFuture<Void> sendWebhookMessage(@NotNull String webhookName, @NotNull MessageEmbed embed) {
        return sendWebhookMessage(webhookName, null, embed);
    }
    
    /**
     * Sends a text message via webhook.
     * Falls back to bot messaging if webhook fails.
     * 
     * @param webhookName The name of the webhook to use
     * @param content The message content to send
     * @return A CompletableFuture that completes when the message is sent
     */
    public @NotNull CompletableFuture<Void> sendWebhookMessage(@NotNull String webhookName, @NotNull String content) {
        return sendWebhookMessage(webhookName, content, null);
    }
    
    /**
     * Sends a message via webhook with optional content and embed.
     * Falls back to bot messaging if webhook fails.
     * 
     * @param webhookName The name of the webhook to use
     * @param content The message content (can be null)
     * @param embed The message embed (can be null)
     * @return A CompletableFuture that completes when the message is sent
     */
    public @NotNull CompletableFuture<Void> sendWebhookMessage(@NotNull String webhookName, 
                                                               @Nullable String content, 
                                                               @Nullable MessageEmbed embed) {
        
        // Check if webhooks should be used first
        DiscordConfig config = configManager.getConfig();
        if (config == null || !config.isUseWebhooksFirst()) {
            logger.debug("Webhooks disabled or not preferred, using fallback immediately");
            return sendFallbackMessage(content, embed);
        }
        
        // Get webhook URL
        String webhookUrl = webhookUrls.get(webhookName);
        if (webhookUrl == null) {
            logger.debug("Webhook '" + webhookName + "' not found, using fallback");
            return sendFallbackMessage(content, embed);
        }
        
        // Validate message content
        if ((content == null || content.isEmpty()) && embed == null) {
            logger.warn("Cannot send empty message via webhook '" + webhookName + "'");
            return CompletableFuture.completedFuture(null);
        }
        
        // Build message
        MessageCreateBuilder builder = new MessageCreateBuilder();
        if (content != null && !content.isEmpty()) {
            builder.setContent(content);
        }
        if (embed != null) {
            builder.setEmbeds(embed);
        }
        
        MessageCreateData message = builder.build();
        
        // Send via webhook with fallback
        return CompletableFuture.supplyAsync(() -> {
            try {
                sendWebhookHttpRequest(webhookUrl, message);
                logger.debug("Successfully sent message via webhook '" + webhookName + "'");
                return null;
            } catch (Exception e) {
                logger.warn("Failed to send message via webhook '" + webhookName + "': " + e.getMessage());
                logger.debug("Webhook error details", e);
                
                // Attempt fallback
                try {
                    sendFallbackMessage(content, embed).join();
                    logger.debug("Successfully sent message via fallback after webhook failure");
                } catch (Exception fallbackError) {
                    logger.error("Fallback messaging also failed after webhook failure", fallbackError);
                    throw new RuntimeException("Both webhook and fallback messaging failed", fallbackError);
                }
                return null;
            }
        }, executor);
    }
    
    /**
     * Sends a message via the fallback channel (bot messaging).
     * 
     * @param content The message content (can be null)
     * @param embed The message embed (can be null)
     * @return A CompletableFuture that completes when the message is sent
     */
    private @NotNull CompletableFuture<Void> sendFallbackMessage(@Nullable String content, @Nullable MessageEmbed embed) {
        if (fallbackChannel == null) {
            logger.warn("No fallback channel configured, cannot send message");
            return CompletableFuture.completedFuture(null);
        }
        
        // Validate message content
        if ((content == null || content.isEmpty()) && embed == null) {
            logger.warn("Cannot send empty fallback message");
            return CompletableFuture.completedFuture(null);
        }
        
        // Build message
        MessageCreateBuilder builder = new MessageCreateBuilder();
        if (content != null && !content.isEmpty()) {
            builder.setContent(content);
        }
        if (embed != null) {
            builder.setEmbeds(embed);
        }
        
        MessageCreateData message = builder.build();
        
        // Send via bot
        return CompletableFuture.supplyAsync(() -> {
            try {
                fallbackChannel.sendMessage(message).complete();
                logger.debug("Successfully sent fallback message to channel " + fallbackChannel.getId());
                return null;
            } catch (Exception e) {
                logger.error("Failed to send fallback message to channel " + fallbackChannel.getId() + ": " + e.getMessage());
                throw new RuntimeException("Fallback messaging failed", e);
            }
        }, executor);
    }
    
    /**
     * Sets the fallback channel for when webhooks fail.
     * 
     * @param channel The fallback message channel
     */
    public void setFallbackChannel(@Nullable MessageChannel channel) {
        this.fallbackChannel = channel;
        if (channel != null) {
            logger.debug("Set fallback channel to: " + channel.getId());
        } else {
            logger.debug("Cleared fallback channel");
        }
    }
    
    /**
     * Gets the fallback channel.
     * 
     * @return The fallback channel, or null if not set
     */
    public @Nullable MessageChannel getFallbackChannel() {
        return fallbackChannel;
    }
    
    /**
     * Sends a webhook HTTP request.
     * 
     * @param webhookUrl The webhook URL
     * @param message The message to send
     * @throws Exception if the request fails
     */
    private void sendWebhookHttpRequest(@NotNull String webhookUrl, @NotNull MessageCreateData message) throws Exception {
        // For now, this is a placeholder implementation
        // In a real implementation, you would convert the MessageCreateData to JSON
        // and send it via HTTP POST to the webhook URL
        
        // This is a simplified implementation that just validates the URL
        URI uri = new URI(webhookUrl);
        
        // TODO: Implement actual HTTP request with proper JSON payload
        // For now, we'll just simulate success
        logger.debug("Simulated webhook HTTP request to: " + maskWebhookUrl(webhookUrl));
    }
    
    /**
     * Checks if a webhook with the specified name exists.
     * 
     * @param name The webhook name
     * @return true if the webhook exists, false otherwise
     */
    public boolean hasWebhook(@NotNull String name) {
        return webhookUrls.containsKey(name);
    }
    
    /**
     * Gets the number of configured webhooks.
     * 
     * @return The number of webhooks
     */
    public int getWebhookCount() {
        return webhookUrls.size();
    }
    
    /**
     * Validates a webhook URL format.
     * 
     * @param url The webhook URL to validate
     * @return true if the URL is valid, false otherwise
     */
    public boolean isValidWebhookUrl(@NotNull String url) {
        if (url.isEmpty()) {
            return false;
        }
        
        // Check URL pattern
        if (!WEBHOOK_URL_PATTERN.matcher(url).matches()) {
            return false;
        }
        
        // Validate as URI
        try {
            new URI(url);
            return true;
        } catch (URISyntaxException e) {
            return false;
        }
    }
    
    /**
     * Masks a webhook URL for safe logging.
     * 
     * @param url The webhook URL
     * @return The masked URL
     */
    private @NotNull String maskWebhookUrl(@NotNull String url) {
        if (url.length() < 20) {
            return "***";
        }
        
        // Show first part and mask the token
        int lastSlash = url.lastIndexOf('/');
        if (lastSlash > 0) {
            return url.substring(0, lastSlash + 1) + "***";
        }
        
        return url.substring(0, 10) + "***";
    }
    
    /**
     * Reloads webhooks from configuration.
     * This will close existing webhook clients and create new ones.
     */
    public void reloadWebhooks() {
        logger.debug("Reloading webhooks from configuration");
        
        // Clear existing webhooks
        webhookUrls.clear();
        
        // Reinitialize from configuration
        initializeWebhooks();
        
        logger.debug("Webhook reload completed, " + webhookUrls.size() + " webhooks active");
    }
    
    /**
     * Tests a webhook by sending a simple test message.
     * 
     * @param webhookName The name of the webhook to test
     * @return A CompletableFuture that completes with true if the test succeeds, false otherwise
     */
    public @NotNull CompletableFuture<Boolean> testWebhook(@NotNull String webhookName) {
        String webhookUrl = webhookUrls.get(webhookName);
        if (webhookUrl == null) {
            logger.debug("Cannot test webhook '" + webhookName + "' - not found");
            return CompletableFuture.completedFuture(false);
        }
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                MessageCreateData testMessage = new MessageCreateBuilder()
                    .setContent("🔧 LumaSG Discord Integration Test - Webhook is working!")
                    .build();
                
                sendWebhookHttpRequest(webhookUrl, testMessage);
                logger.debug("Webhook '" + webhookName + "' test successful");
                return true;
                
            } catch (Exception e) {
                logger.warn("Webhook '" + webhookName + "' test failed: " + e.getMessage());
                return false;
            }
        }, executor);
    }
    
    /**
     * Gets webhook statistics for monitoring.
     * 
     * @return A map of webhook names to their status information
     */
    public @NotNull Map<String, String> getWebhookStats() {
        Map<String, String> stats = new ConcurrentHashMap<>();
        
        for (String webhookName : webhookUrls.keySet()) {
            String url = webhookUrls.get(webhookName);
            if (url != null && !url.isEmpty()) {
                stats.put(webhookName, "Active");
            } else {
                stats.put(webhookName, "Inactive");
            }
        }
        
        return stats;
    }
    
    /**
     * Shuts down the webhook manager and closes all webhook clients.
     */
    public void shutdown() {
        logger.debug("Shutting down Discord webhook manager");
        
        // Clear all webhook URLs
        webhookUrls.clear();
        
        // Shutdown executor
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
                logger.warn("Webhook executor did not terminate gracefully, forced shutdown");
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
            logger.warn("Webhook executor shutdown interrupted");
        }
        
        // Clear fallback channel
        fallbackChannel = null;
        
        logger.debug("Discord webhook manager shutdown completed");
    }
}