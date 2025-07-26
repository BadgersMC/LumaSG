package net.lumalyte.lumasg.discord.commands;

import net.lumalyte.lumasg.LumaSG;
import net.lumalyte.lumasg.discord.embeds.DiscordEmbedBuilder;
import net.lumalyte.lumasg.util.core.DebugLogger;
import org.jetbrains.annotations.NotNull;

/**
 * Handles Discord slash commands and interactions.
 * Manages command registration and processing for statistics, admin, and queue commands.
 */
public class DiscordCommandManager {
    
    private final @NotNull LumaSG plugin;
    private final @NotNull DiscordEmbedBuilder embedBuilder;
    private final @NotNull DebugLogger.ContextualLogger logger;
    
    /**
     * Creates a new Discord command manager.
     * 
     * @param plugin The plugin instance
     * @param embedBuilder The Discord embed builder
     */
    public DiscordCommandManager(@NotNull LumaSG plugin, @NotNull DiscordEmbedBuilder embedBuilder) {
        this.plugin = plugin;
        this.embedBuilder = embedBuilder;
        this.logger = plugin.getDebugLogger().forContext("DiscordCommands");
    }
    
    /**
     * Shuts down the command manager.
     */
    public void shutdown() {
        logger.debug("Discord command manager shutdown will be implemented in task 7");
    }
}