package net.lumalyte.lumasg.discord.announcements;

import net.lumalyte.lumasg.LumaSG;
import net.lumalyte.lumasg.discord.connection.DiscordConnectionManager;
import net.lumalyte.lumasg.discord.embeds.DiscordEmbedBuilder;
import net.lumalyte.lumasg.discord.webhooks.DiscordWebhookManager;
import net.lumalyte.lumasg.util.core.DebugLogger;
import org.jetbrains.annotations.NotNull;

/**
 * Handles all game-related announcements to Discord channels.
 * Manages game start, end, deathmatch, and milestone announcements.
 */
public class DiscordAnnouncementManager {
    
    private final @NotNull LumaSG plugin;
    private final @NotNull DiscordConnectionManager connectionManager;
    private final @NotNull DiscordEmbedBuilder embedBuilder;
    private final @NotNull DiscordWebhookManager webhookManager;
    private final @NotNull DebugLogger.ContextualLogger logger;
    
    /**
     * Creates a new Discord announcement manager.
     * 
     * @param plugin The plugin instance
     * @param connectionManager The Discord connection manager
     * @param embedBuilder The Discord embed builder
     * @param webhookManager The Discord webhook manager
     */
    public DiscordAnnouncementManager(@NotNull LumaSG plugin, 
                                    @NotNull DiscordConnectionManager connectionManager,
                                    @NotNull DiscordEmbedBuilder embedBuilder,
                                    @NotNull DiscordWebhookManager webhookManager) {
        this.plugin = plugin;
        this.connectionManager = connectionManager;
        this.embedBuilder = embedBuilder;
        this.webhookManager = webhookManager;
        this.logger = plugin.getDebugLogger().forContext("DiscordAnnouncements");
    }
    
    /**
     * Shuts down the announcement manager.
     */
    public void shutdown() {
        logger.debug("Discord announcement manager shutdown will be implemented in task 6");
    }
}