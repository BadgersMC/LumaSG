package net.lumalyte.lumasg.discord.embeds;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.lumalyte.lumasg.LumaSG;
import net.lumalyte.lumasg.discord.config.DiscordConfig;
import net.lumalyte.lumasg.discord.config.DiscordConfigManager;
import net.lumalyte.lumasg.game.core.Game;
import net.lumalyte.lumasg.game.core.GameMode;
import net.lumalyte.lumasg.statistics.PlayerStats;
import net.lumalyte.lumasg.util.core.DebugLogger;
import net.lumalyte.lumasg.util.security.InputSanitizer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.Color;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Creates rich Discord embeds for various types of content.
 * Handles game embeds, statistics embeds, status embeds, and error embeds.
 * 
 * <p>This class provides methods to create formatted Discord embeds for different
 * types of content including game announcements, player statistics, server status,
 * and error messages. All embeds respect Discord's size limits and configuration
 * settings.</p>
 * 
 * @author LumaLyte
 * @version 1.0
 * @since 1.0
 */
public class DiscordEmbedBuilder {
    
    private final @NotNull LumaSG plugin;
    private final @NotNull DiscordConfigManager configManager;
    private final @NotNull DebugLogger.ContextualLogger logger;
    
    // Discord embed limits
    private static final int MAX_EMBED_TITLE_LENGTH = 256;
    private static final int MAX_EMBED_DESCRIPTION_LENGTH = 4096;
    private static final int MAX_EMBED_FIELD_NAME_LENGTH = 256;
    private static final int MAX_EMBED_FIELD_VALUE_LENGTH = 1024;
    private static final int MAX_EMBED_FIELDS = 25;
    private static final int MAX_EMBED_TOTAL_LENGTH = 6000;
    
    // Default colors
    private static final Color DEFAULT_SUCCESS_COLOR = Color.GREEN;
    private static final Color DEFAULT_ERROR_COLOR = Color.RED;
    private static final Color DEFAULT_WARNING_COLOR = Color.ORANGE;
    private static final Color DEFAULT_INFO_COLOR = Color.BLUE;
    private static final Color DEFAULT_GAME_COLOR = Color.CYAN;
    
    /**
     * Creates a new Discord embed builder.
     * 
     * @param plugin The plugin instance
     * @param configManager The Discord configuration manager
     */
    public DiscordEmbedBuilder(@NotNull LumaSG plugin, @NotNull DiscordConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.logger = plugin.getDebugLogger().forContext("DiscordEmbeds");
    }
    
    // Game-related embeds
    
    /**
     * Creates an embed for game start announcements.
     * 
     * @param game The game that is starting
     * @return The formatted embed
     */
    public @NotNull MessageEmbed createGameStartEmbed(@NotNull Game game) {
        DiscordConfig config = configManager.getConfig();
        EmbedBuilder builder = createBaseEmbed(DEFAULT_GAME_COLOR);
        
        // Title
        String arenaName = InputSanitizer.sanitizeForLogging(game.getArena().getName());
        builder.setTitle("🎮 Game Started!", null);
        
        // Description
        StringBuilder description = new StringBuilder();
        description.append("A new Survival Games match has begun!\n");
        description.append("**Arena:** ").append(arenaName).append("\n");
        
        // Player count
        int playerCount = game.getPlayerCount();
        int maxPlayers = game.getArena().getMaxPlayers();
        description.append("**Players:** ").append(playerCount).append("/").append(maxPlayers).append("\n");
        
        // Game mode (if available)
        description.append("**Mode:** ").append(game.getGameMode().toString()).append("\n");
        
        // Estimated duration
        description.append("**Estimated Duration:** 15-20 minutes\n");
        
        builder.setDescription(truncateText(description.toString(), MAX_EMBED_DESCRIPTION_LENGTH));
        
        // Player list field (if enabled and not too many players)
        if (config.isIncludePlayerList() && playerCount <= config.getMaxPlayersInEmbed()) {
            StringBuilder playerList = new StringBuilder();
            List<String> playerNames = new ArrayList<>();
            
            for (UUID playerId : game.getPlayers()) {
                Player player = getCachedPlayerFromGame(game, playerId);
                if (player != null) {
                    playerNames.add(InputSanitizer.sanitizeForLogging(player.getName()));
                }
            }
            
            if (!playerNames.isEmpty()) {
                playerList.append(String.join(", ", playerNames));
                builder.addField("Players", 
                    truncateText(playerList.toString(), MAX_EMBED_FIELD_VALUE_LENGTH), 
                    false);
            }
        } else if (playerCount > config.getMaxPlayersInEmbed()) {
            builder.addField("Players", 
                playerCount + " players (too many to list)", 
                false);
        }
        
        // Timestamp
        builder.setTimestamp(Instant.now());
        
        // Footer
        builder.setFooter("LumaSG", getServerIconUrl());
        
        logger.debug("Created game start embed for arena: " + arenaName);
        return builder.build();
    }
    
    /**
     * Creates an embed for game end announcements.
     * 
     * @param game The game that ended
     * @param winner The winner of the game, or null if no winner
     * @return The formatted embed
     */
    public @NotNull MessageEmbed createGameEndEmbed(@NotNull Game game, @Nullable Player winner) {
        EmbedBuilder builder = createBaseEmbed(DEFAULT_SUCCESS_COLOR);
        
        // Title
        String arenaName = InputSanitizer.sanitizeForLogging(game.getArena().getName());
        if (winner != null) {
            builder.setTitle("🏆 Game Finished!", null);
        } else {
            builder.setTitle("⏰ Game Ended", null);
        }
        
        // Description
        StringBuilder description = new StringBuilder();
        description.append("The Survival Games match has concluded!\n");
        description.append("**Arena:** ").append(arenaName).append("\n");
        
        // Winner information
        if (winner != null) {
            String winnerName = InputSanitizer.sanitizeForLogging(winner.getName());
            description.append("**Winner:** 🥇 ").append(winnerName).append("\n");
        } else {
            description.append("**Result:** No winner (game ended early)\n");
        }
        
        // Game duration - we'll estimate based on current time since this is called at game end
        // In a real implementation, you'd want to track the actual game start time
        description.append("**Duration:** ").append("~15 minutes").append("\n");
        
        // Final player count
        int finalPlayerCount = game.getPlayerCount();
        description.append("**Final Players:** ").append(finalPlayerCount).append("\n");
        
        builder.setDescription(truncateText(description.toString(), MAX_EMBED_DESCRIPTION_LENGTH));
        
        // Statistics field (if available)
        if (winner != null) {
            // Add winner statistics if available
            // This would require integration with the statistics system
            builder.addField("🎯 Match Stats", 
                "Winner: " + InputSanitizer.sanitizeForLogging(winner.getName()) + "\n" +
                "Players eliminated: " + (game.getArena().getMaxPlayers() - finalPlayerCount), 
                true);
        }
        
        // Timestamp
        builder.setTimestamp(Instant.now());
        
        // Footer
        builder.setFooter("LumaSG", getServerIconUrl());
        
        logger.debug("Created game end embed for arena: " + arenaName + 
                    (winner != null ? " with winner: " + InputSanitizer.sanitizeForLogging(winner.getName()) : " with no winner"));
        return builder.build();
    }
    
    /**
     * Creates an embed for deathmatch phase announcements.
     * 
     * @param game The game entering deathmatch
     * @return The formatted embed
     */
    public @NotNull MessageEmbed createDeathmatchEmbed(@NotNull Game game) {
        EmbedBuilder builder = createBaseEmbed(DEFAULT_WARNING_COLOR);
        
        // Title
        String arenaName = InputSanitizer.sanitizeForLogging(game.getArena().getName());
        builder.setTitle("⚔️ DEATHMATCH PHASE!", null);
        
        // Description
        StringBuilder description = new StringBuilder();
        description.append("The deathmatch phase has begun!\n");
        description.append("**Arena:** ").append(arenaName).append("\n");
        
        // Remaining players
        int remainingPlayers = game.getPlayerCount();
        description.append("**Remaining Players:** ").append(remainingPlayers).append("\n");
        description.append("**Border:** Shrinking to force combat!\n");
        description.append("\n⚠️ **Fight to the death!** ⚠️");
        
        builder.setDescription(truncateText(description.toString(), MAX_EMBED_DESCRIPTION_LENGTH));
        
        // Remaining players field (if not too many)
        DiscordConfig config = configManager.getConfig();
        if (config.isIncludePlayerList() && remainingPlayers <= config.getMaxPlayersInEmbed()) {
            StringBuilder playerList = new StringBuilder();
            List<String> playerNames = new ArrayList<>();
            
            for (UUID playerId : game.getPlayers()) {
                Player player = getCachedPlayerFromGame(game, playerId);
                if (player != null) {
                    playerNames.add(InputSanitizer.sanitizeForLogging(player.getName()));
                }
            }
            
            if (!playerNames.isEmpty()) {
                playerList.append(String.join(", ", playerNames));
                builder.addField("Remaining Players", 
                    truncateText(playerList.toString(), MAX_EMBED_FIELD_VALUE_LENGTH), 
                    false);
            }
        }
        
        // Timestamp
        builder.setTimestamp(Instant.now());
        
        // Footer
        builder.setFooter("LumaSG", getServerIconUrl());
        
        logger.debug("Created deathmatch embed for arena: " + arenaName + " with " + remainingPlayers + " remaining players");
        return builder.build();
    }
    
    /**
     * Creates an embed for player milestone announcements.
     * 
     * @param game The game
     * @param remainingPlayers The number of remaining players
     * @return The formatted embed
     */
    public @NotNull MessageEmbed createPlayerMilestoneEmbed(@NotNull Game game, int remainingPlayers) {
        EmbedBuilder builder = createBaseEmbed(DEFAULT_INFO_COLOR);
        
        // Title
        String arenaName = InputSanitizer.sanitizeForLogging(game.getArena().getName());
        builder.setTitle("📊 Player Milestone", null);
        
        // Description
        StringBuilder description = new StringBuilder();
        description.append("**Arena:** ").append(arenaName).append("\n");
        description.append("**Remaining Players:** ").append(remainingPlayers).append("\n");
        
        // Add appropriate message based on remaining players
        if (remainingPlayers <= 3) {
            description.append("\n🔥 **Final ").append(remainingPlayers).append("!** The end is near!");
        } else if (remainingPlayers <= 5) {
            description.append("\n⚡ **Top ").append(remainingPlayers).append("!** Getting intense!");
        } else if (remainingPlayers <= 10) {
            description.append("\n🎯 **").append(remainingPlayers).append(" players left!** The competition heats up!");
        } else {
            description.append("\n📈 **").append(remainingPlayers).append(" players remaining**");
        }
        
        builder.setDescription(truncateText(description.toString(), MAX_EMBED_DESCRIPTION_LENGTH));
        
        // Timestamp
        builder.setTimestamp(Instant.now());
        
        // Footer
        builder.setFooter("LumaSG", getServerIconUrl());
        
        logger.debug("Created milestone embed for arena: " + arenaName + " with " + remainingPlayers + " remaining players");
        return builder.build();
    }
    
    // Statistics embeds
    
    /**
     * Creates an embed for player statistics.
     * 
     * @param stats The player statistics
     * @return The formatted embed
     */
    public @NotNull MessageEmbed createPlayerStatsEmbed(@NotNull PlayerStats stats) {
        EmbedBuilder builder = createBaseEmbed(DEFAULT_INFO_COLOR);
        
        // Title
        String playerName = InputSanitizer.sanitizeForLogging(stats.getPlayerName());
        builder.setTitle("📊 Player Statistics", null);
        
        // Description
        StringBuilder description = new StringBuilder();
        description.append("Statistics for **").append(playerName).append("**\n");
        
        builder.setDescription(truncateText(description.toString(), MAX_EMBED_DESCRIPTION_LENGTH));
        
        // Game statistics
        builder.addField("🎮 Games", 
            "**Played:** " + stats.getGamesPlayed() + "\n" +
            "**Won:** " + stats.getWins() + "\n" +
            "**Lost:** " + stats.getLosses(), 
            true);
        
        // Combat statistics
        builder.addField("⚔️ Combat", 
            "**Kills:** " + stats.getKills() + "\n" +
            "**Deaths:** " + stats.getDeaths() + "\n" +
            "**K/D Ratio:** " + String.format("%.2f", stats.getKillDeathRatio()), 
            true);
        
        // Performance statistics
        builder.addField("🏆 Performance", 
            "**Win Rate:** " + String.format("%.1f%%", stats.getWinRate()) + "\n" +
            "**Best Placement:** " + (stats.getBestPlacement() > 0 ? "#" + stats.getBestPlacement() : "N/A") + "\n" +
            "**Top 3 Finishes:** " + stats.getTop3Finishes(), 
            true);
        
        // Streak information
        if (stats.getCurrentWinStreak() > 0 || stats.getBestWinStreak() > 0) {
            builder.addField("🔥 Streaks", 
                "**Current Win Streak:** " + stats.getCurrentWinStreak() + "\n" +
                "**Best Win Streak:** " + stats.getBestWinStreak(), 
                true);
        }
        
        // Additional statistics
        if (stats.getChestsOpened() > 0) {
            builder.addField("📦 Other", 
                "**Chests Opened:** " + stats.getChestsOpened() + "\n" +
                "**Avg Kills/Game:** " + String.format("%.1f", stats.getAverageKillsPerGame()), 
                true);
        }
        
        // Player avatar (if enabled)
        DiscordConfig config = configManager.getConfig();
        if (config.isUsePlayerAvatars()) {
            String avatarUrl = "https://mc-heads.net/avatar/" + stats.getPlayerId().toString() + "/64";
            builder.setThumbnail(avatarUrl);
        }
        
        // Timestamp
        builder.setTimestamp(Instant.now());
        
        // Footer
        builder.setFooter("LumaSG", getServerIconUrl());
        
        logger.debug("Created player stats embed for: " + playerName);
        return builder.build();
    }
    
    /**
     * Creates an embed for leaderboards.
     * 
     * @param topPlayers The list of top players
     * @param category The leaderboard category (e.g., "Wins", "Kills", "Win Rate")
     * @return The formatted embed, or multiple embeds if content is too large
     */
    public @NotNull List<MessageEmbed> createLeaderboardEmbeds(@NotNull List<PlayerStats> topPlayers, @NotNull String category) {
        List<MessageEmbed> embeds = new ArrayList<>();
        
        if (topPlayers.isEmpty()) {
            // Create empty leaderboard embed
            EmbedBuilder builder = createBaseEmbed(DEFAULT_INFO_COLOR);
            builder.setTitle("🏆 " + category + " Leaderboard", null);
            builder.setDescription("No statistics available yet.\nPlay some games to see the leaderboard!");
            builder.setTimestamp(Instant.now());
            builder.setFooter("LumaSG", getServerIconUrl());
            embeds.add(builder.build());
            return embeds;
        }
        
        // Split players into chunks to avoid embed limits
        int playersPerEmbed = 10; // Show top 10 per embed
        int totalPages = (int) Math.ceil((double) topPlayers.size() / playersPerEmbed);
        
        for (int page = 0; page < totalPages; page++) {
            EmbedBuilder builder = createBaseEmbed(DEFAULT_INFO_COLOR);
            
            // Title
            String title = "🏆 " + category + " Leaderboard";
            if (totalPages > 1) {
                title += " (Page " + (page + 1) + "/" + totalPages + ")";
            }
            builder.setTitle(title, null);
            
            // Description
            StringBuilder description = new StringBuilder();
            description.append("Top players ranked by ").append(category.toLowerCase()).append("\n\n");
            
            // Add players for this page
            int startIndex = page * playersPerEmbed;
            int endIndex = Math.min(startIndex + playersPerEmbed, topPlayers.size());
            
            for (int i = startIndex; i < endIndex; i++) {
                PlayerStats stats = topPlayers.get(i);
                String playerName = InputSanitizer.sanitizeForLogging(stats.getPlayerName());
                int rank = i + 1;
                
                // Rank emoji
                String rankEmoji = getRankEmoji(rank);
                
                // Get the relevant statistic based on category
                String statValue = getStatisticValue(stats, category);
                
                description.append(rankEmoji).append(" **#").append(rank).append("** ")
                          .append(playerName).append(" - ").append(statValue).append("\n");
            }
            
            builder.setDescription(truncateText(description.toString(), MAX_EMBED_DESCRIPTION_LENGTH));
            
            // Timestamp
            builder.setTimestamp(Instant.now());
            
            // Footer
            builder.setFooter("LumaSG", getServerIconUrl());
            
            embeds.add(builder.build());
        }
        
        logger.debug("Created " + embeds.size() + " leaderboard embed(s) for category: " + category);
        return embeds;
    }
    
    // Status embeds
    
    /**
     * Creates an embed for server status.
     * 
     * @param totalPlayers Total players online
     * @param activeGames Number of active games
     * @param waitingGames Number of games waiting for players
     * @return The formatted embed
     */
    public @NotNull MessageEmbed createServerStatusEmbed(int totalPlayers, int activeGames, int waitingGames) {
        EmbedBuilder builder = createBaseEmbed(DEFAULT_INFO_COLOR);
        
        // Title
        builder.setTitle("🖥️ Server Status", null);
        
        // Description
        StringBuilder description = new StringBuilder();
        description.append("Current server statistics\n\n");
        
        builder.setDescription(truncateText(description.toString(), MAX_EMBED_DESCRIPTION_LENGTH));
        
        // Player information
        builder.addField("👥 Players", 
            "**Online:** " + totalPlayers + "\n" +
            "**In Games:** " + (activeGames * 10) + " (estimated)", // Rough estimate
            true);
        
        // Game information
        builder.addField("🎮 Games", 
            "**Active:** " + activeGames + "\n" +
            "**Waiting:** " + waitingGames + "\n" +
            "**Total:** " + (activeGames + waitingGames), 
            true);
        
        // Server information
        builder.addField("⚙️ Server", 
            "**Version:** LumaSG v1.0\n" +
            "**Uptime:** " + getServerUptime(), 
            true);
        
        // Timestamp
        builder.setTimestamp(Instant.now());
        
        // Footer
        builder.setFooter("LumaSG", getServerIconUrl());
        
        logger.debug("Created server status embed");
        return builder.build();
    }
    
    /**
     * Creates an embed for queue status.
     * 
     * @param queueSize Current queue size
     * @param estimatedWaitTime Estimated wait time in seconds
     * @return The formatted embed
     */
    public @NotNull MessageEmbed createQueueStatusEmbed(int queueSize, int estimatedWaitTime) {
        EmbedBuilder builder = createBaseEmbed(DEFAULT_INFO_COLOR);
        
        // Title
        builder.setTitle("⏳ Queue Status", null);
        
        // Description
        StringBuilder description = new StringBuilder();
        if (queueSize == 0) {
            description.append("No players currently in queue.\n");
            description.append("Join a game to start playing!");
        } else {
            description.append("**Players in Queue:** ").append(queueSize).append("\n");
            description.append("**Estimated Wait Time:** ").append(formatDuration(estimatedWaitTime)).append("\n");
            description.append("\nJoin the server to get in the action!");
        }
        
        builder.setDescription(truncateText(description.toString(), MAX_EMBED_DESCRIPTION_LENGTH));
        
        // Timestamp
        builder.setTimestamp(Instant.now());
        
        // Footer
        builder.setFooter("LumaSG", getServerIconUrl());
        
        logger.debug("Created queue status embed with " + queueSize + " players");
        return builder.build();
    }
    
    // Error and success embeds
    
    /**
     * Creates an error embed.
     * 
     * @param title The error title
     * @param description The error description
     * @return The formatted embed
     */
    public @NotNull MessageEmbed createErrorEmbed(@NotNull String title, @NotNull String description) {
        EmbedBuilder builder = createBaseEmbed(DEFAULT_ERROR_COLOR);
        
        builder.setTitle("❌ " + truncateText(title, MAX_EMBED_TITLE_LENGTH - 3), null);
        builder.setDescription(truncateText(description, MAX_EMBED_DESCRIPTION_LENGTH));
        builder.setTimestamp(Instant.now());
        builder.setFooter("LumaSG", getServerIconUrl());
        
        logger.debug("Created error embed: " + title);
        return builder.build();
    }
    
    /**
     * Creates a success embed.
     * 
     * @param title The success title
     * @param description The success description
     * @return The formatted embed
     */
    public @NotNull MessageEmbed createSuccessEmbed(@NotNull String title, @NotNull String description) {
        EmbedBuilder builder = createBaseEmbed(DEFAULT_SUCCESS_COLOR);
        
        builder.setTitle("✅ " + truncateText(title, MAX_EMBED_TITLE_LENGTH - 3), null);
        builder.setDescription(truncateText(description, MAX_EMBED_DESCRIPTION_LENGTH));
        builder.setTimestamp(Instant.now());
        builder.setFooter("LumaSG", getServerIconUrl());
        
        logger.debug("Created success embed: " + title);
        return builder.build();
    }
    
    /**
     * Creates a warning embed.
     * 
     * @param title The warning title
     * @param description The warning description
     * @return The formatted embed
     */
    public @NotNull MessageEmbed createWarningEmbed(@NotNull String title, @NotNull String description) {
        EmbedBuilder builder = createBaseEmbed(DEFAULT_WARNING_COLOR);
        
        builder.setTitle("⚠️ " + truncateText(title, MAX_EMBED_TITLE_LENGTH - 3), null);
        builder.setDescription(truncateText(description, MAX_EMBED_DESCRIPTION_LENGTH));
        builder.setTimestamp(Instant.now());
        builder.setFooter("LumaSG", getServerIconUrl());
        
        logger.debug("Created warning embed: " + title);
        return builder.build();
    }
    
    // Utility methods
    
    /**
     * Creates a base embed builder with common settings.
     * 
     * @param color The embed color
     * @return The configured embed builder
     */
    private @NotNull EmbedBuilder createBaseEmbed(@NotNull Color color) {
        EmbedBuilder builder = new EmbedBuilder();
        
        // Set color from configuration or use default
        DiscordConfig config = configManager.getConfig();
        try {
            Color configColor = Color.decode(config.getEmbedColor());
            builder.setColor(configColor);
        } catch (NumberFormatException e) {
            builder.setColor(color);
            logger.warn("Invalid embed color in configuration: " + config.getEmbedColor() + ", using default");
        }
        
        return builder;
    }
    
    /**
     * Truncates text to fit within Discord's limits.
     * 
     * @param text The text to truncate
     * @param maxLength The maximum length
     * @return The truncated text
     */
    private @NotNull String truncateText(@NotNull String text, int maxLength) {
        if (text.length() <= maxLength) {
            return text;
        }
        
        return text.substring(0, maxLength - 3) + "...";
    }
    
    /**
     * Gets the server icon URL from configuration.
     * 
     * @return The server icon URL, or null if not configured
     */
    private @Nullable String getServerIconUrl() {
        DiscordConfig config = configManager.getConfig();
        String iconUrl = config.getServerIconUrl();
        return iconUrl.isEmpty() ? null : iconUrl;
    }
    
    /**
     * Formats a duration in seconds to a human-readable string.
     * 
     * @param seconds The duration in seconds
     * @return The formatted duration string
     */
    private @NotNull String formatDuration(long seconds) {
        if (seconds < 60) {
            return seconds + "s";
        } else if (seconds < 3600) {
            long minutes = seconds / 60;
            long remainingSeconds = seconds % 60;
            return minutes + "m " + remainingSeconds + "s";
        } else {
            long hours = seconds / 3600;
            long remainingMinutes = (seconds % 3600) / 60;
            return hours + "h " + remainingMinutes + "m";
        }
    }
    
    /**
     * Gets the appropriate emoji for a leaderboard rank.
     * 
     * @param rank The rank (1-based)
     * @return The rank emoji
     */
    private @NotNull String getRankEmoji(int rank) {
        return switch (rank) {
            case 1 -> "🥇";
            case 2 -> "🥈";
            case 3 -> "🥉";
            default -> "🏅";
        };
    }
    
    /**
     * Gets the statistic value for a specific category.
     * 
     * @param stats The player statistics
     * @param category The category name
     * @return The formatted statistic value
     */
    private @NotNull String getStatisticValue(@NotNull PlayerStats stats, @NotNull String category) {
        return switch (category.toLowerCase()) {
            case "wins" -> String.valueOf(stats.getWins());
            case "kills" -> String.valueOf(stats.getKills());
            case "win rate" -> String.format("%.1f%%", stats.getWinRate());
            case "k/d ratio" -> String.format("%.2f", stats.getKillDeathRatio());
            case "games played" -> String.valueOf(stats.getGamesPlayed());
            case "top 3 finishes" -> String.valueOf(stats.getTop3Finishes());
            default -> "N/A";
        };
    }
    
    /**
     * Gets a rough estimate of server uptime.
     * This is a placeholder implementation.
     * 
     * @return The formatted uptime string
     */
    private @NotNull String getServerUptime() {
        // This is a placeholder - in a real implementation, you'd track server start time
        return "Unknown";
    }
    
    /**
     * Gets a cached player from the game using Bukkit.getPlayer().
     * This is a helper method since Game doesn't expose getCachedPlayer publicly.
     * 
     * @param game The game instance
     * @param playerId The player UUID
     * @return The player instance, or null if not found
     */
    private @Nullable Player getCachedPlayerFromGame(@NotNull Game game, @NotNull UUID playerId) {
        return org.bukkit.Bukkit.getPlayer(playerId);
    }
    
    /**
     * Splits content across multiple embeds if it exceeds Discord's limits.
     * This method ensures that large content is properly split while maintaining readability.
     * 
     * @param title The embed title
     * @param content The content to split
     * @param color The embed color
     * @return A list of embeds containing the split content
     */
    public @NotNull List<MessageEmbed> splitContentAcrossEmbeds(@NotNull String title, @NotNull String content, @NotNull Color color) {
        List<MessageEmbed> embeds = new ArrayList<>();
        
        if (content.length() <= MAX_EMBED_DESCRIPTION_LENGTH) {
            // Content fits in a single embed
            EmbedBuilder builder = createBaseEmbed(color);
            builder.setTitle(truncateText(title, MAX_EMBED_TITLE_LENGTH), null);
            builder.setDescription(content);
            builder.setTimestamp(Instant.now());
            builder.setFooter("LumaSG", getServerIconUrl());
            embeds.add(builder.build());
            return embeds;
        }
        
        // Split content across multiple embeds
        String[] lines = content.split("\n");
        StringBuilder currentContent = new StringBuilder();
        int embedCount = 1;
        
        for (String line : lines) {
            // Check if adding this line would exceed the limit
            if (currentContent.length() + line.length() + 1 > MAX_EMBED_DESCRIPTION_LENGTH) {
                // Create embed with current content
                EmbedBuilder builder = createBaseEmbed(color);
                String embedTitle = title + " (Part " + embedCount + ")";
                builder.setTitle(truncateText(embedTitle, MAX_EMBED_TITLE_LENGTH), null);
                builder.setDescription(currentContent.toString());
                builder.setTimestamp(Instant.now());
                builder.setFooter("LumaSG", getServerIconUrl());
                embeds.add(builder.build());
                
                // Start new embed
                currentContent = new StringBuilder();
                embedCount++;
            }
            
            if (currentContent.length() > 0) {
                currentContent.append("\n");
            }
            currentContent.append(line);
        }
        
        // Add final embed if there's remaining content
        if (currentContent.length() > 0) {
            EmbedBuilder builder = createBaseEmbed(color);
            String embedTitle = title + " (Part " + embedCount + ")";
            builder.setTitle(truncateText(embedTitle, MAX_EMBED_TITLE_LENGTH), null);
            builder.setDescription(currentContent.toString());
            builder.setTimestamp(Instant.now());
            builder.setFooter("LumaSG", getServerIconUrl());
            embeds.add(builder.build());
        }
        
        logger.debug("Split content into " + embeds.size() + " embeds for title: " + title);
        return embeds;
    }
    
    /**
     * Validates that an embed doesn't exceed Discord's total character limit.
     * 
     * @param embed The embed to validate
     * @return true if the embed is within limits, false otherwise
     */
    public boolean validateEmbedSize(@NotNull MessageEmbed embed) {
        int totalLength = 0;
        
        // Title
        if (embed.getTitle() != null) {
            totalLength += embed.getTitle().length();
        }
        
        // Description
        if (embed.getDescription() != null) {
            totalLength += embed.getDescription().length();
        }
        
        // Fields
        for (MessageEmbed.Field field : embed.getFields()) {
            if (field.getName() != null) {
                totalLength += field.getName().length();
            }
            if (field.getValue() != null) {
                totalLength += field.getValue().length();
            }
        }
        
        // Footer
        if (embed.getFooter() != null && embed.getFooter().getText() != null) {
            totalLength += embed.getFooter().getText().length();
        }
        
        // Author
        if (embed.getAuthor() != null && embed.getAuthor().getName() != null) {
            totalLength += embed.getAuthor().getName().length();
        }
        
        boolean isValid = totalLength <= MAX_EMBED_TOTAL_LENGTH && embed.getFields().size() <= MAX_EMBED_FIELDS;
        
        if (!isValid) {
            logger.warn("Embed exceeds Discord limits - Total length: " + totalLength + "/" + MAX_EMBED_TOTAL_LENGTH + 
                       ", Fields: " + embed.getFields().size() + "/" + MAX_EMBED_FIELDS);
        }
        
        return isValid;
    }
    
    /**
     * Creates a "no data" embed for when there's no information to display.
     * 
     * @param title The embed title
     * @param message The message to display
     * @return The formatted embed
     */
    public @NotNull MessageEmbed createNoDataEmbed(@NotNull String title, @NotNull String message) {
        EmbedBuilder builder = createBaseEmbed(DEFAULT_INFO_COLOR);
        
        builder.setTitle("ℹ️ " + truncateText(title, MAX_EMBED_TITLE_LENGTH - 3), null);
        builder.setDescription(truncateText(message, MAX_EMBED_DESCRIPTION_LENGTH));
        builder.setTimestamp(Instant.now());
        builder.setFooter("LumaSG", getServerIconUrl());
        
        logger.debug("Created no data embed: " + title);
        return builder.build();
    }
    
    /**
     * Creates an embed for player not found errors.
     * 
     * @param playerName The player name that wasn't found
     * @return The formatted embed
     */
    public @NotNull MessageEmbed createPlayerNotFoundEmbed(@NotNull String playerName) {
        String sanitizedName = InputSanitizer.sanitizeForLogging(playerName);
        return createErrorEmbed("Player Not Found", 
            "Could not find statistics for player: **" + sanitizedName + "**\n\n" +
            "This could mean:\n" +
            "• The player has never played on this server\n" +
            "• The player name is misspelled\n" +
            "• Statistics are disabled");
    }
    
    /**
     * Creates an embed for when statistics are disabled.
     * 
     * @return The formatted embed
     */
    public @NotNull MessageEmbed createStatisticsDisabledEmbed() {
        return createWarningEmbed("Statistics Disabled", 
            "Player statistics are currently disabled on this server.\n\n" +
            "Contact a server administrator if you believe this is an error.");
    }
}