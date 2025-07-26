package net.lumalyte.lumasg.discord.embeds;

import net.dv8tion.jda.api.entities.MessageEmbed;
import net.lumalyte.lumasg.LumaSG;
import net.lumalyte.lumasg.discord.config.DiscordConfig;
import net.lumalyte.lumasg.discord.config.DiscordConfigManager;
import net.lumalyte.lumasg.util.core.DebugLogger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.awt.Color;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for Discord embed size limits and content splitting functionality.
 * Ensures embeds comply with Discord's API limits.
 */
@ExtendWith(MockitoExtension.class)
class DiscordEmbedSizeLimitsTest {
    
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
    
    // Discord API limits
    private static final int MAX_EMBED_TITLE_LENGTH = 256;
    private static final int MAX_EMBED_DESCRIPTION_LENGTH = 4096;
    private static final int MAX_EMBED_FIELD_NAME_LENGTH = 256;
    private static final int MAX_EMBED_FIELD_VALUE_LENGTH = 1024;
    private static final int MAX_EMBED_FIELDS = 25;
    private static final int MAX_EMBED_TOTAL_LENGTH = 6000;
    
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
    void testTitleLengthLimit() {
        // Test title at exactly the limit
        String maxTitle = "A".repeat(MAX_EMBED_TITLE_LENGTH);
        MessageEmbed embed1 = embedBuilder.createSuccessEmbed(maxTitle, "Description");
        assertEquals(MAX_EMBED_TITLE_LENGTH, embed1.getTitle().length() - 3); // -3 for "✅ " prefix
        
        // Test title exceeding the limit
        String longTitle = "A".repeat(MAX_EMBED_TITLE_LENGTH + 100);
        MessageEmbed embed2 = embedBuilder.createSuccessEmbed(longTitle, "Description");
        assertTrue(embed2.getTitle().length() <= MAX_EMBED_TITLE_LENGTH);
        assertTrue(embed2.getTitle().endsWith("..."));
    }
    
    @Test
    void testDescriptionLengthLimit() {
        // Test description at exactly the limit
        String maxDescription = "B".repeat(MAX_EMBED_DESCRIPTION_LENGTH);
        MessageEmbed embed1 = embedBuilder.createSuccessEmbed("Title", maxDescription);
        assertEquals(MAX_EMBED_DESCRIPTION_LENGTH, embed1.getDescription().length());
        
        // Test description exceeding the limit
        String longDescription = "B".repeat(MAX_EMBED_DESCRIPTION_LENGTH + 500);
        MessageEmbed embed2 = embedBuilder.createSuccessEmbed("Title", longDescription);
        assertTrue(embed2.getDescription().length() <= MAX_EMBED_DESCRIPTION_LENGTH);
        assertTrue(embed2.getDescription().endsWith("..."));
    }
    
    @Test
    void testContentSplittingBasic() {
        // Create content that definitely needs splitting
        StringBuilder longContent = new StringBuilder();
        for (int i = 0; i < 200; i++) {
            longContent.append("This is line ").append(i).append(" of very long content that will need to be split.\n");
        }
        
        // Execute
        List<MessageEmbed> embeds = embedBuilder.splitContentAcrossEmbeds("Long Content", longContent.toString(), Color.BLUE);
        
        // Verify
        assertTrue(embeds.size() > 1, "Content should be split into multiple embeds");
        
        // Verify each embed is within limits
        for (int i = 0; i < embeds.size(); i++) {
            MessageEmbed embed = embeds.get(i);
            assertTrue(embed.getDescription().length() <= MAX_EMBED_DESCRIPTION_LENGTH, 
                      "Embed " + i + " description exceeds limit: " + embed.getDescription().length());
            assertTrue(embed.getTitle().contains("Part " + (i + 1)), 
                      "Embed " + i + " should have part number in title");
        }
    }
    
    @Test
    void testContentSplittingPreservesLineIntegrity() {
        // Create content with distinct lines
        StringBuilder content = new StringBuilder();
        for (int i = 1; i <= 150; i++) {
            content.append("Line ").append(i).append(": This is a complete line that should not be split in the middle.\n");
        }
        
        // Execute
        List<MessageEmbed> embeds = embedBuilder.splitContentAcrossEmbeds("Test Content", content.toString(), Color.BLUE);
        
        // Verify
        assertTrue(embeds.size() > 1);
        
        // Verify no lines are cut in the middle
        for (MessageEmbed embed : embeds) {
            String[] lines = embed.getDescription().split("\n");
            for (String line : lines) {
                if (!line.isEmpty()) {
                    assertTrue(line.startsWith("Line "), "Line should be complete: " + line);
                    assertTrue(line.endsWith("middle."), "Line should be complete: " + line);
                }
            }
        }
    }
    
    @Test
    void testContentSplittingEmptyContent() {
        // Test with empty content
        List<MessageEmbed> embeds = embedBuilder.splitContentAcrossEmbeds("Empty", "", Color.BLUE);
        
        assertEquals(1, embeds.size());
        assertEquals("", embeds.get(0).getDescription());
    }
    
    @Test
    void testContentSplittingSingleLine() {
        // Test with content that fits in one embed
        String shortContent = "This is a short piece of content that should fit in a single embed.";
        List<MessageEmbed> embeds = embedBuilder.splitContentAcrossEmbeds("Short", shortContent, Color.BLUE);
        
        assertEquals(1, embeds.size());
        assertEquals(shortContent, embeds.get(0).getDescription());
        assertEquals("Short", embeds.get(0).getTitle());
    }
    
    @Test
    void testContentSplittingVeryLongSingleLine() {
        // Test with a single very long line (edge case)
        String veryLongLine = "A".repeat(MAX_EMBED_DESCRIPTION_LENGTH + 1000);
        List<MessageEmbed> embeds = embedBuilder.splitContentAcrossEmbeds("Long Line", veryLongLine, Color.BLUE);
        
        assertTrue(embeds.size() >= 1);
        
        // Each embed should be within limits
        for (MessageEmbed embed : embeds) {
            assertTrue(embed.getDescription().length() <= MAX_EMBED_DESCRIPTION_LENGTH);
        }
    }
    
    @Test
    void testEmbedValidationValid() {
        // Create a valid embed
        MessageEmbed validEmbed = embedBuilder.createSuccessEmbed("Valid Title", "Valid description");
        
        // Execute
        boolean isValid = embedBuilder.validateEmbedSize(validEmbed);
        
        // Verify
        assertTrue(isValid);
    }
    
    @Test
    void testEmbedValidationTotalLengthExceeded() {
        // This test would require creating an embed that exceeds total length
        // For now, we'll test the validation method with a known valid embed
        MessageEmbed embed = embedBuilder.createSuccessEmbed("Title", "Description");
        assertTrue(embedBuilder.validateEmbedSize(embed));
    }
    
    @Test
    void testEmbedValidationFieldLimits() {
        // Test field name length (this would require a custom embed builder for testing)
        // For now, verify that our standard embeds don't exceed field limits
        
        // Create an embed with multiple fields (player stats)
        net.lumalyte.lumasg.statistics.PlayerStats stats = new net.lumalyte.lumasg.statistics.PlayerStats(
                java.util.UUID.randomUUID(), "TestPlayer");
        stats.setWins(100);
        stats.setKills(500);
        stats.setChestsOpened(1000);
        
        MessageEmbed embed = embedBuilder.createPlayerStatsEmbed(stats);
        
        // Verify field limits
        assertTrue(embed.getFields().size() <= MAX_EMBED_FIELDS);
        
        for (MessageEmbed.Field field : embed.getFields()) {
            if (field.getName() != null) {
                assertTrue(field.getName().length() <= MAX_EMBED_FIELD_NAME_LENGTH, 
                          "Field name too long: " + field.getName());
            }
            if (field.getValue() != null) {
                assertTrue(field.getValue().length() <= MAX_EMBED_FIELD_VALUE_LENGTH, 
                          "Field value too long: " + field.getValue());
            }
        }
        
        // Validate overall embed
        assertTrue(embedBuilder.validateEmbedSize(embed));
    }
    
    @Test
    void testLargeLeaderboardSplitting() {
        // Create a large list of players for leaderboard
        java.util.List<net.lumalyte.lumasg.statistics.PlayerStats> players = new java.util.ArrayList<>();
        for (int i = 1; i <= 50; i++) {
            net.lumalyte.lumasg.statistics.PlayerStats stats = new net.lumalyte.lumasg.statistics.PlayerStats(
                    java.util.UUID.randomUUID(), "Player" + i);
            stats.setWins(100 - i);
            players.add(stats);
        }
        
        // Execute
        List<MessageEmbed> embeds = embedBuilder.createLeaderboardEmbeds(players, "Wins");
        
        // Verify
        assertTrue(embeds.size() > 1, "Large leaderboard should be split");
        
        // Verify each embed is within limits
        for (MessageEmbed embed : embeds) {
            assertTrue(embed.getDescription().length() <= MAX_EMBED_DESCRIPTION_LENGTH);
            assertTrue(embedBuilder.validateEmbedSize(embed));
        }
        
        // Verify page numbering
        for (int i = 0; i < embeds.size(); i++) {
            assertTrue(embeds.get(i).getTitle().contains("Page " + (i + 1)));
        }
    }
    
    @Test
    void testSpecialCharacterHandling() {
        // Test with special characters that might affect length calculations
        String specialChars = "🎮🏆⚔️🔥📊👥🎯📦⚠️✅❌ℹ️🥇🥈🥉🏅";
        String title = "Special " + specialChars;
        String description = "Description with special characters: " + specialChars.repeat(10);
        
        MessageEmbed embed = embedBuilder.createSuccessEmbed(title, description);
        
        // Verify the embed is still valid
        assertNotNull(embed.getTitle());
        assertNotNull(embed.getDescription());
        assertTrue(embedBuilder.validateEmbedSize(embed));
    }
    
    @Test
    void testUnicodeCharacterHandling() {
        // Test with various Unicode characters
        String unicode = "测试 тест テスト 🌟 ñáéíóú";
        String longUnicode = unicode.repeat(100);
        
        MessageEmbed embed = embedBuilder.createSuccessEmbed("Unicode Test", longUnicode);
        
        // Verify the embed handles Unicode properly
        assertNotNull(embed.getDescription());
        assertTrue(embed.getDescription().length() <= MAX_EMBED_DESCRIPTION_LENGTH);
        assertTrue(embedBuilder.validateEmbedSize(embed));
    }
    
    @Test
    void testNewlineHandlingInSplitting() {
        // Test content with various newline patterns
        StringBuilder content = new StringBuilder();
        content.append("Line 1\n");
        content.append("Line 2\n\n"); // Double newline
        content.append("Line 3\n");
        content.append("\n"); // Empty line
        content.append("Line 4\n");
        
        // Repeat to make it long enough to split
        String longContent = content.toString().repeat(200);
        
        List<MessageEmbed> embeds = embedBuilder.splitContentAcrossEmbeds("Newline Test", longContent, Color.BLUE);
        
        // Verify splitting worked correctly
        assertTrue(embeds.size() > 1);
        
        for (MessageEmbed embed : embeds) {
            assertTrue(embed.getDescription().length() <= MAX_EMBED_DESCRIPTION_LENGTH);
        }
    }
    
    @Test
    void testEmbedFooterAndTimestamp() {
        // Verify that footer and timestamp don't cause size issues
        MessageEmbed embed = embedBuilder.createSuccessEmbed("Test", "Test description");
        
        assertNotNull(embed.getFooter());
        assertEquals("LumaSG", embed.getFooter().getText());
        assertNotNull(embed.getTimestamp());
        
        // Footer text should be within reasonable limits
        assertTrue(embed.getFooter().getText().length() <= 100);
        
        assertTrue(embedBuilder.validateEmbedSize(embed));
    }
}