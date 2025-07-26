package net.lumalyte.lumasg.discord.embeds;

import net.dv8tion.jda.api.entities.MessageEmbed;
import net.lumalyte.lumasg.LumaSG;
import net.lumalyte.lumasg.discord.config.DiscordConfig;
import net.lumalyte.lumasg.discord.config.DiscordConfigManager;
import net.lumalyte.lumasg.statistics.PlayerStats;
import net.lumalyte.lumasg.util.core.DebugLogger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.awt.Color;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for Discord embed formatting and content handling.
 * Focuses on edge cases, formatting, and Discord-specific requirements.
 */
@ExtendWith(MockitoExtension.class)
class DiscordEmbedFormattingTest {
    
    @Mock
    private LumaSG plugin;
    
    @Mock
    private DiscordConfigManager configManager;
    
    @Mock
    private DiscordConfig config;
    
    @Mock
    private DebugLogger debugLogger;
    
    @Mock
    private DebugLogger.ContextualLogger contextualLogger;
    
    private DiscordEmbedBuilder embedBuilder;
    
    @BeforeEach
    void setUp() {
        when(plugin.getDebugLogger()).thenReturn(debugLogger);
        when(debugLogger.forContext("DiscordEmbeds")).thenReturn(contextualLogger);
        when(configManager.getConfig()).thenReturn(config);
        
        // Setup default config values
        when(config.getEmbedColor()).thenReturn("#00FF00");
        when(config.getServerIconUrl()).thenReturn("");
        when(config.isIncludePlayerList()).thenReturn(true);
        when(config.getMaxPlayersInEmbed()).thenReturn(20);
        when(config.isUsePlayerAvatars()).thenReturn(false);
        
        embedBuilder = new DiscordEmbedBuilder(plugin, configManager);
    }
    
    @Test
    void testEmbedColorParsing() {
        // Test valid hex colors
        String[] validColors = {"#FF0000", "#00FF00", "#0000FF", "#FFFFFF", "#000000"};
        
        for (String color : validColors) {
            when(config.getEmbedColor()).thenReturn(color);
            MessageEmbed embed = embedBuilder.createSuccessEmbed("Test", "Test message");
            
            assertNotNull(embed.getColor());
            assertEquals(Color.decode(color).getRGB(), embed.getColor().getRGB());
        }
    }
    
    @Test
    void testEmbedColorParsingInvalid() {
        // Test invalid colors that should fallback to default
        String[] invalidColors = {"red", "invalid", "#GGGGGG", "not-a-color", ""};
        
        for (String color : invalidColors) {
            when(config.getEmbedColor()).thenReturn(color);
            MessageEmbed embed = embedBuilder.createSuccessEmbed("Test", "Test message");
            
            assertNotNull(embed.getColor());
            // Should fallback to success color (green)
            assertEquals(Color.GREEN.getRGB(), embed.getColor().getRGB());
        }
        
        verify(contextualLogger, atLeastOnce()).warn(contains("Invalid embed color"));
    }
    
    @Test
    void testLeaderboardRankEmojis() {
        // Setup players
        List<PlayerStats> players = Arrays.asList(
                createPlayerStats("First", 100),
                createPlayerStats("Second", 90),
                createPlayerStats("Third", 80),
                createPlayerStats("Fourth", 70)
        );
        
        // Execute
        List<MessageEmbed> embeds = embedBuilder.createLeaderboardEmbeds(players, "Wins");
        
        // Verify
        assertEquals(1, embeds.size());
        MessageEmbed embed = embeds.get(0);
        String description = embed.getDescription();
        
        // Check for rank emojis
        assertTrue(description.contains("🥇 **#1**"));
        assertTrue(description.contains("🥈 **#2**"));
        assertTrue(description.contains("🥉 **#3**"));
        assertTrue(description.contains("🏅 **#4**"));
    }
    
    @Test
    void testLeaderboardStatisticFormatting() {
        // Setup player with various statistics
        PlayerStats stats = createPlayerStats("TestPlayer", 50);
        stats.setKills(150);
        stats.setDeaths(30);
        stats.setGamesPlayed(75);
        
        List<PlayerStats> players = List.of(stats);
        
        // Test different categories
        String[] categories = {"Wins", "Kills", "Win Rate", "K/D Ratio", "Games Played"};
        String[] expectedValues = {"50", "150", "66.7%", "5.00", "75"};
        
        for (int i = 0; i < categories.length; i++) {
            List<MessageEmbed> embeds = embedBuilder.createLeaderboardEmbeds(players, categories[i]);
            MessageEmbed embed = embeds.get(0);
            
            assertTrue(embed.getDescription().contains(expectedValues[i]), 
                      "Category " + categories[i] + " should contain " + expectedValues[i]);
        }
    }
    
    @Test
    void testPlayerStatsFieldFormatting() {
        // Setup player with comprehensive stats
        PlayerStats stats = createPlayerStats("TestPlayer", 25);
        stats.setLosses(10);
        stats.setKills(75);
        stats.setDeaths(15);
        stats.setGamesPlayed(35);
        stats.setCurrentWinStreak(5);
        stats.setBestWinStreak(12);
        stats.setTop3Finishes(28);
        stats.setChestsOpened(420);
        stats.setBestPlacement(1);
        
        // Execute
        MessageEmbed embed = embedBuilder.createPlayerStatsEmbed(stats);
        
        // Verify field formatting
        MessageEmbed.Field gamesField = findField(embed, "🎮 Games");
        assertNotNull(gamesField);
        assertTrue(gamesField.getValue().contains("**Played:** 35"));
        assertTrue(gamesField.getValue().contains("**Won:** 25"));
        assertTrue(gamesField.getValue().contains("**Lost:** 10"));
        
        MessageEmbed.Field combatField = findField(embed, "⚔️ Combat");
        assertNotNull(combatField);
        assertTrue(combatField.getValue().contains("**Kills:** 75"));
        assertTrue(combatField.getValue().contains("**Deaths:** 15"));
        assertTrue(combatField.getValue().contains("**K/D Ratio:** 5.00"));
        
        MessageEmbed.Field performanceField = findField(embed, "🏆 Performance");
        assertNotNull(performanceField);
        assertTrue(performanceField.getValue().contains("**Win Rate:** 71.4%"));
        assertTrue(performanceField.getValue().contains("**Best Placement:** #1"));
        assertTrue(performanceField.getValue().contains("**Top 3 Finishes:** 28"));
        
        MessageEmbed.Field streaksField = findField(embed, "🔥 Streaks");
        assertNotNull(streaksField);
        assertTrue(streaksField.getValue().contains("**Current Win Streak:** 5"));
        assertTrue(streaksField.getValue().contains("**Best Win Streak:** 12"));
        
        MessageEmbed.Field otherField = findField(embed, "📦 Other");
        assertNotNull(otherField);
        assertTrue(otherField.getValue().contains("**Chests Opened:** 420"));
        assertTrue(otherField.getValue().contains("**Avg Kills/Game:** 2.1"));
    }
    
    @Test
    void testPlayerStatsNoStreaks() {
        // Setup player with no win streaks
        PlayerStats stats = createPlayerStats("TestPlayer", 0);
        stats.setCurrentWinStreak(0);
        stats.setBestWinStreak(0);
        
        // Execute
        MessageEmbed embed = embedBuilder.createPlayerStatsEmbed(stats);
        
        // Verify no streaks field
        MessageEmbed.Field streaksField = findField(embed, "🔥 Streaks");
        assertNull(streaksField);
    }
    
    @Test
    void testPlayerStatsNoChests() {
        // Setup player with no chests opened
        PlayerStats stats = createPlayerStats("TestPlayer", 5);
        stats.setChestsOpened(0);
        
        // Execute
        MessageEmbed embed = embedBuilder.createPlayerStatsEmbed(stats);
        
        // Verify no other field
        MessageEmbed.Field otherField = findField(embed, "📦 Other");
        assertNull(otherField);
    }
    
    @Test
    void testDurationFormatting() {
        // Test various duration formats through server status
        MessageEmbed embed1 = embedBuilder.createQueueStatusEmbed(5, 30); // 30 seconds
        assertTrue(embed1.getDescription().contains("30s"));
        
        MessageEmbed embed2 = embedBuilder.createQueueStatusEmbed(5, 90); // 1 minute 30 seconds
        assertTrue(embed2.getDescription().contains("1m 30s"));
        
        MessageEmbed embed3 = embedBuilder.createQueueStatusEmbed(5, 3665); // 1 hour 1 minute 5 seconds
        assertTrue(embed3.getDescription().contains("1h 1m"));
    }
    
    @Test
    void testMilestoneMessages() {
        // Test different milestone messages
        MessageEmbed embed1 = embedBuilder.createPlayerMilestoneEmbed(null, 2);
        assertTrue(embed1.getDescription().contains("Final 2"));
        assertTrue(embed1.getDescription().contains("The end is near"));
        
        MessageEmbed embed2 = embedBuilder.createPlayerMilestoneEmbed(null, 5);
        assertTrue(embed2.getDescription().contains("Top 5"));
        assertTrue(embed2.getDescription().contains("Getting intense"));
        
        MessageEmbed embed3 = embedBuilder.createPlayerMilestoneEmbed(null, 8);
        assertTrue(embed3.getDescription().contains("8 players left"));
        assertTrue(embed3.getDescription().contains("competition heats up"));
        
        MessageEmbed embed4 = embedBuilder.createPlayerMilestoneEmbed(null, 15);
        assertTrue(embed4.getDescription().contains("15 players remaining"));
    }
    
    @Test
    void testEmbedSizeLimits() {
        // Test title length limit
        String longTitle = "A".repeat(300);
        MessageEmbed embed1 = embedBuilder.createErrorEmbed(longTitle, "Short description");
        assertTrue(embed1.getTitle().length() <= 256);
        assertTrue(embed1.getTitle().endsWith("..."));
        
        // Test description length limit
        String longDescription = "B".repeat(5000);
        MessageEmbed embed2 = embedBuilder.createErrorEmbed("Short title", longDescription);
        assertTrue(embed2.getDescription().length() <= 4096);
        assertTrue(embed2.getDescription().endsWith("..."));
    }
    
    @Test
    void testContentSplittingPreservesLines() {
        // Setup content with clear line breaks
        StringBuilder content = new StringBuilder();
        for (int i = 1; i <= 100; i++) {
            content.append("Line ").append(i).append(": This is a test line with some content.\n");
        }
        
        // Execute
        List<MessageEmbed> embeds = embedBuilder.splitContentAcrossEmbeds("Test", content.toString(), Color.BLUE);
        
        // Verify
        assertTrue(embeds.size() > 1);
        
        // Verify each embed has complete lines (no partial lines)
        for (MessageEmbed embed : embeds) {
            String description = embed.getDescription();
            String[] lines = description.split("\n");
            
            // Each line should be complete (start with "Line" and end with "content.")
            for (String line : lines) {
                if (!line.isEmpty()) {
                    assertTrue(line.startsWith("Line"), "Line should be complete: " + line);
                    assertTrue(line.endsWith("content."), "Line should be complete: " + line);
                }
            }
        }
    }
    
    @Test
    void testPlayerAvatarConfiguration() {
        // Test with avatars enabled
        when(config.isUsePlayerAvatars()).thenReturn(true);
        
        UUID playerId = UUID.randomUUID();
        PlayerStats stats = new PlayerStats(playerId, "TestPlayer");
        
        MessageEmbed embed = embedBuilder.createPlayerStatsEmbed(stats);
        
        assertNotNull(embed.getThumbnail());
        assertTrue(embed.getThumbnail().getUrl().contains(playerId.toString()));
        assertTrue(embed.getThumbnail().getUrl().contains("mc-heads.net"));
        
        // Test with avatars disabled
        when(config.isUsePlayerAvatars()).thenReturn(false);
        
        MessageEmbed embed2 = embedBuilder.createPlayerStatsEmbed(stats);
        assertNull(embed2.getThumbnail());
    }
    
    @Test
    void testServerIconConfiguration() {
        // Test with server icon configured
        when(config.getServerIconUrl()).thenReturn("https://example.com/icon.png");
        
        MessageEmbed embed1 = embedBuilder.createSuccessEmbed("Test", "Test");
        assertNotNull(embed1.getFooter());
        assertEquals("https://example.com/icon.png", embed1.getFooter().getIconUrl());
        
        // Test with no server icon
        when(config.getServerIconUrl()).thenReturn("");
        
        MessageEmbed embed2 = embedBuilder.createSuccessEmbed("Test", "Test");
        assertNotNull(embed2.getFooter());
        assertNull(embed2.getFooter().getIconUrl());
    }
    
    // Helper methods
    
    private PlayerStats createPlayerStats(String name, int wins) {
        UUID playerId = UUID.randomUUID();
        PlayerStats stats = new PlayerStats(playerId, name);
        stats.setWins(wins);
        return stats;
    }
    
    private MessageEmbed.Field findField(MessageEmbed embed, String name) {
        return embed.getFields().stream()
                .filter(field -> name.equals(field.getName()))
                .findFirst()
                .orElse(null);
    }
}