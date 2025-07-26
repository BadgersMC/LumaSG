package net.lumalyte.lumasg.discord.embeds;

import net.dv8tion.jda.api.entities.MessageEmbed;
import net.lumalyte.lumasg.LumaSG;
import net.lumalyte.lumasg.arena.Arena;
import net.lumalyte.lumasg.discord.config.DiscordConfig;
import net.lumalyte.lumasg.discord.config.DiscordConfigManager;
import net.lumalyte.lumasg.game.core.Game;
import net.lumalyte.lumasg.game.core.GameMode;
import net.lumalyte.lumasg.statistics.PlayerStats;
import net.lumalyte.lumasg.util.core.DebugLogger;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.awt.Color;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for DiscordEmbedBuilder.
 * Tests embed creation, formatting, size limits, and content splitting.
 */
@ExtendWith(MockitoExtension.class)
class DiscordEmbedBuilderTest {
    
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
    
    @Mock
    private Game game;
    
    @Mock
    private Arena arena;
    

    
    @Mock
    private Player player1;
    
    @Mock
    private Player player2;
    
    private DiscordEmbedBuilder embedBuilder;
    
    @BeforeEach
    void setUp() {
        // Setup mocks
        when(plugin.getDebugLogger()).thenReturn(debugLogger);
        when(debugLogger.forContext("DiscordEmbeds")).thenReturn(contextualLogger);
        when(configManager.getConfig()).thenReturn(config);
        
        // Setup default config values
        when(config.getEmbedColor()).thenReturn("#00FF00");
        when(config.getServerIconUrl()).thenReturn("https://example.com/icon.png");
        when(config.isIncludePlayerList()).thenReturn(true);
        when(config.getMaxPlayersInEmbed()).thenReturn(20);
        when(config.isUsePlayerAvatars()).thenReturn(true);
        
        // Setup game mocks
        when(game.getArena()).thenReturn(arena);
        when(game.getPlayerCount()).thenReturn(12);
        when(game.getPlayers()).thenReturn(Set.of(player1.getUniqueId(), player2.getUniqueId()));
        when(game.getGameMode()).thenReturn(GameMode.SOLO);
        when(arena.getName()).thenReturn("TestArena");
        when(arena.getMaxPlayers()).thenReturn(24);
        
        // Setup player mocks
        when(player1.getName()).thenReturn("TestPlayer1");
        when(player1.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player2.getName()).thenReturn("TestPlayer2");
        when(player2.getUniqueId()).thenReturn(UUID.randomUUID());
        
        embedBuilder = new DiscordEmbedBuilder(plugin, configManager);
    }
    
    @Test
    void testCreateGameStartEmbed() {
        // Execute
        MessageEmbed embed = embedBuilder.createGameStartEmbed(game);
        
        // Verify
        assertNotNull(embed);
        assertEquals("🎮 Game Started!", embed.getTitle());
        assertNotNull(embed.getDescription());
        assertTrue(embed.getDescription().contains("TestArena"));
        assertTrue(embed.getDescription().contains("12/24"));
        assertTrue(embed.getDescription().contains("SOLO"));
        assertNotNull(embed.getTimestamp());
        assertEquals("LumaSG", embed.getFooter().getText());
        
        verify(contextualLogger).debug(contains("Created game start embed"));
    }
    
    @Test
    void testCreateGameStartEmbedTooManyPlayers() {
        // Setup - too many players to list
        when(game.getPlayerCount()).thenReturn(25);
        when(config.getMaxPlayersInEmbed()).thenReturn(20);
        
        // Execute
        MessageEmbed embed = embedBuilder.createGameStartEmbed(game);
        
        // Verify
        assertNotNull(embed);
        assertTrue(embed.getDescription().contains("25/24"));
        
        // Verify player list shows "too many to list"
        boolean hasTooManyMessage = embed.getFields().stream()
                .anyMatch(field -> field.getValue().contains("too many to list"));
        assertTrue(hasTooManyMessage);
    }
    
    @Test
    void testCreateGameEndEmbedWithWinner() {
        // Setup
        when(game.getPlayerCount()).thenReturn(1);
        
        // Execute
        MessageEmbed embed = embedBuilder.createGameEndEmbed(game, player1);
        
        // Verify
        assertNotNull(embed);
        assertEquals("🏆 Game Finished!", embed.getTitle());
        assertNotNull(embed.getDescription());
        assertTrue(embed.getDescription().contains("TestPlayer1"));
        assertTrue(embed.getDescription().contains("TestArena"));
        assertTrue(embed.getDescription().contains("Duration:"));
        
        // Verify match stats field
        boolean hasStatsField = embed.getFields().stream()
                .anyMatch(field -> "🎯 Match Stats".equals(field.getName()));
        assertTrue(hasStatsField);
        
        verify(contextualLogger).debug(contains("Created game end embed"));
    }
    
    @Test
    void testCreateGameEndEmbedNoWinner() {
        // Setup
        when(game.getPlayerCount()).thenReturn(0);
        
        // Execute
        MessageEmbed embed = embedBuilder.createGameEndEmbed(game, null);
        
        // Verify
        assertNotNull(embed);
        assertEquals("⏰ Game Ended", embed.getTitle());
        assertTrue(embed.getDescription().contains("No winner"));
        
        // Verify no match stats field for no winner
        boolean hasStatsField = embed.getFields().stream()
                .anyMatch(field -> "🎯 Match Stats".equals(field.getName()));
        assertFalse(hasStatsField);
    }
    
    @Test
    void testCreateDeathmatchEmbed() {
        // Setup
        when(game.getPlayerCount()).thenReturn(3);
        when(game.getPlayers()).thenReturn(Set.of(player1.getUniqueId(), player2.getUniqueId()));
        
        try (var bukkit = mockStatic(org.bukkit.Bukkit.class)) {
            bukkit.when(() -> org.bukkit.Bukkit.getPlayer(player1.getUniqueId())).thenReturn(player1);
            bukkit.when(() -> org.bukkit.Bukkit.getPlayer(player2.getUniqueId())).thenReturn(player2);
        
            // Execute
            MessageEmbed embed = embedBuilder.createDeathmatchEmbed(game);
            
            // Verify
            assertNotNull(embed);
            assertEquals("⚔️ DEATHMATCH PHASE!", embed.getTitle());
            assertTrue(embed.getDescription().contains("deathmatch phase"));
            assertTrue(embed.getDescription().contains("3"));
            assertTrue(embed.getDescription().contains("Fight to the death"));
            
            // Verify remaining players field
            boolean hasPlayersField = embed.getFields().stream()
                    .anyMatch(field -> "Remaining Players".equals(field.getName()));
            assertTrue(hasPlayersField);
            
            verify(contextualLogger).debug(contains("Created deathmatch embed"));
        }
    }
    
    @Test
    void testCreatePlayerMilestoneEmbed() {
        // Execute
        MessageEmbed embed = embedBuilder.createPlayerMilestoneEmbed(game, 5);
        
        // Verify
        assertNotNull(embed);
        assertEquals("📊 Player Milestone", embed.getTitle());
        assertTrue(embed.getDescription().contains("5"));
        assertTrue(embed.getDescription().contains("Top 5"));
        
        verify(contextualLogger).debug(contains("Created milestone embed"));
    }
    
    @Test
    void testCreatePlayerStatsEmbed() {
        // Setup
        UUID playerId = UUID.randomUUID();
        PlayerStats stats = new PlayerStats(playerId, "TestPlayer");
        stats.setWins(10);
        stats.setLosses(5);
        stats.setKills(25);
        stats.setDeaths(8);
        stats.setGamesPlayed(15);
        stats.setCurrentWinStreak(3);
        stats.setBestWinStreak(7);
        stats.setTop3Finishes(12);
        stats.setChestsOpened(150);
        
        // Execute
        MessageEmbed embed = embedBuilder.createPlayerStatsEmbed(stats);
        
        // Verify
        assertNotNull(embed);
        assertEquals("📊 Player Statistics", embed.getTitle());
        assertTrue(embed.getDescription().contains("TestPlayer"));
        
        // Verify all expected fields are present
        List<String> expectedFields = Arrays.asList("🎮 Games", "⚔️ Combat", "🏆 Performance", "🔥 Streaks", "📦 Other");
        for (String expectedField : expectedFields) {
            boolean hasField = embed.getFields().stream()
                    .anyMatch(field -> field.getName().equals(expectedField));
            assertTrue(hasField, "Missing field: " + expectedField);
        }
        
        // Verify thumbnail (player avatar)
        assertNotNull(embed.getThumbnail());
        assertTrue(embed.getThumbnail().getUrl().contains(playerId.toString()));
        
        verify(contextualLogger).debug(contains("Created player stats embed"));
    }
    
    @Test
    void testCreateLeaderboardEmbedsEmpty() {
        // Execute
        List<MessageEmbed> embeds = embedBuilder.createLeaderboardEmbeds(List.of(), "Wins");
        
        // Verify
        assertEquals(1, embeds.size());
        MessageEmbed embed = embeds.get(0);
        assertEquals("🏆 Wins Leaderboard", embed.getTitle());
        assertTrue(embed.getDescription().contains("No statistics available"));
    }
    
    @Test
    void testCreateLeaderboardEmbedsSinglePage() {
        // Setup
        List<PlayerStats> players = Arrays.asList(
                createMockPlayerStats("Player1", 10, 25),
                createMockPlayerStats("Player2", 8, 20),
                createMockPlayerStats("Player3", 6, 15)
        );
        
        // Execute
        List<MessageEmbed> embeds = embedBuilder.createLeaderboardEmbeds(players, "Wins");
        
        // Verify
        assertEquals(1, embeds.size());
        MessageEmbed embed = embeds.get(0);
        assertEquals("🏆 Wins Leaderboard", embed.getTitle());
        assertTrue(embed.getDescription().contains("Player1"));
        assertTrue(embed.getDescription().contains("🥇 **#1**"));
        assertTrue(embed.getDescription().contains("🥈 **#2**"));
        assertTrue(embed.getDescription().contains("🥉 **#3**"));
    }
    
    @Test
    void testCreateServerStatusEmbed() {
        // Execute
        MessageEmbed embed = embedBuilder.createServerStatusEmbed(50, 3, 2);
        
        // Verify
        assertNotNull(embed);
        assertEquals("🖥️ Server Status", embed.getTitle());
        
        // Verify all expected fields
        List<String> expectedFields = Arrays.asList("👥 Players", "🎮 Games", "⚙️ Server");
        for (String expectedField : expectedFields) {
            boolean hasField = embed.getFields().stream()
                    .anyMatch(field -> field.getName().equals(expectedField));
            assertTrue(hasField, "Missing field: " + expectedField);
        }
        
        verify(contextualLogger).debug("Created server status embed");
    }
    
    @Test
    void testCreateQueueStatusEmbedEmpty() {
        // Execute
        MessageEmbed embed = embedBuilder.createQueueStatusEmbed(0, 0);
        
        // Verify
        assertNotNull(embed);
        assertEquals("⏳ Queue Status", embed.getTitle());
        assertTrue(embed.getDescription().contains("No players currently in queue"));
    }
    
    @Test
    void testCreateQueueStatusEmbedWithPlayers() {
        // Execute
        MessageEmbed embed = embedBuilder.createQueueStatusEmbed(5, 120);
        
        // Verify
        assertNotNull(embed);
        assertTrue(embed.getDescription().contains("5"));
        assertTrue(embed.getDescription().contains("2m 0s"));
    }
    
    @Test
    void testCreateErrorEmbed() {
        // Execute
        MessageEmbed embed = embedBuilder.createErrorEmbed("Test Error", "This is a test error message");
        
        // Verify
        assertNotNull(embed);
        assertEquals("❌ Test Error", embed.getTitle());
        assertEquals("This is a test error message", embed.getDescription());
        assertEquals(Color.RED.getRGB(), embed.getColor().getRGB());
        
        verify(contextualLogger).debug("Created error embed: Test Error");
    }
    
    @Test
    void testCreateSuccessEmbed() {
        // Execute
        MessageEmbed embed = embedBuilder.createSuccessEmbed("Test Success", "This is a test success message");
        
        // Verify
        assertNotNull(embed);
        assertEquals("✅ Test Success", embed.getTitle());
        assertEquals("This is a test success message", embed.getDescription());
        assertEquals(Color.GREEN.getRGB(), embed.getColor().getRGB());
        
        verify(contextualLogger).debug("Created success embed: Test Success");
    }
    
    @Test
    void testCreateWarningEmbed() {
        // Execute
        MessageEmbed embed = embedBuilder.createWarningEmbed("Test Warning", "This is a test warning message");
        
        // Verify
        assertNotNull(embed);
        assertEquals("⚠️ Test Warning", embed.getTitle());
        assertEquals("This is a test warning message", embed.getDescription());
        assertEquals(Color.ORANGE.getRGB(), embed.getColor().getRGB());
        
        verify(contextualLogger).debug("Created warning embed: Test Warning");
    }
    
    @Test
    void testSplitContentAcrossEmbedsSingleEmbed() {
        // Setup
        String shortContent = "This is a short content that fits in one embed.";
        
        // Execute
        List<MessageEmbed> embeds = embedBuilder.splitContentAcrossEmbeds("Test Title", shortContent, Color.BLUE);
        
        // Verify
        assertEquals(1, embeds.size());
        MessageEmbed embed = embeds.get(0);
        assertEquals("Test Title", embed.getTitle());
        assertEquals(shortContent, embed.getDescription());
    }
    
    @Test
    void testSplitContentAcrossEmbedsMultipleEmbeds() {
        // Setup - create content that exceeds the limit
        StringBuilder longContent = new StringBuilder();
        for (int i = 0; i < 500; i++) {
            longContent.append("This is line ").append(i).append(" of very long content.\n");
        }
        
        // Execute
        List<MessageEmbed> embeds = embedBuilder.splitContentAcrossEmbeds("Long Content", longContent.toString(), Color.BLUE);
        
        // Verify
        assertTrue(embeds.size() > 1);
        
        // Verify all embeds have proper titles
        for (int i = 0; i < embeds.size(); i++) {
            MessageEmbed embed = embeds.get(i);
            assertTrue(embed.getTitle().contains("Long Content"));
            assertTrue(embed.getTitle().contains("Part " + (i + 1)));
            assertNotNull(embed.getDescription());
            assertTrue(embed.getDescription().length() <= 4096); // Discord limit
        }
        
        verify(contextualLogger).debug(contains("Split content into"));
    }
    
    @Test
    void testValidateEmbedSizeValid() {
        // Setup
        MessageEmbed embed = embedBuilder.createSuccessEmbed("Short Title", "Short description");
        
        // Execute
        boolean isValid = embedBuilder.validateEmbedSize(embed);
        
        // Verify
        assertTrue(isValid);
    }
    
    @Test
    void testCreatePlayerNotFoundEmbed() {
        // Execute
        MessageEmbed embed = embedBuilder.createPlayerNotFoundEmbed("NonExistentPlayer");
        
        // Verify
        assertNotNull(embed);
        assertEquals("❌ Player Not Found", embed.getTitle());
        assertTrue(embed.getDescription().contains("NonExistentPlayer"));
        assertTrue(embed.getDescription().contains("never played"));
        assertEquals(Color.RED.getRGB(), embed.getColor().getRGB());
    }
    
    @Test
    void testCreateStatisticsDisabledEmbed() {
        // Execute
        MessageEmbed embed = embedBuilder.createStatisticsDisabledEmbed();
        
        // Verify
        assertNotNull(embed);
        assertEquals("⚠️ Statistics Disabled", embed.getTitle());
        assertTrue(embed.getDescription().contains("currently disabled"));
        assertEquals(Color.ORANGE.getRGB(), embed.getColor().getRGB());
    }
    
    @Test
    void testCreateNoDataEmbed() {
        // Execute
        MessageEmbed embed = embedBuilder.createNoDataEmbed("No Data", "There is no data to display");
        
        // Verify
        assertNotNull(embed);
        assertEquals("ℹ️ No Data", embed.getTitle());
        assertEquals("There is no data to display", embed.getDescription());
        assertEquals(Color.BLUE.getRGB(), embed.getColor().getRGB());
        
        verify(contextualLogger).debug("Created no data embed: No Data");
    }
    
    @Test
    void testTextTruncation() {
        // Setup - create very long title and description
        String longTitle = "A".repeat(300); // Exceeds 256 character limit
        String longDescription = "B".repeat(5000); // Exceeds 4096 character limit
        
        // Execute
        MessageEmbed embed = embedBuilder.createErrorEmbed(longTitle, longDescription);
        
        // Verify
        assertTrue(embed.getTitle().length() <= 256);
        assertTrue(embed.getDescription().length() <= 4096);
        assertTrue(embed.getTitle().endsWith("..."));
        assertTrue(embed.getDescription().endsWith("..."));
    }
    
    @Test
    void testInvalidEmbedColorFallback() {
        // Setup - invalid color in config
        when(config.getEmbedColor()).thenReturn("invalid-color");
        
        // Execute
        MessageEmbed embed = embedBuilder.createSuccessEmbed("Test", "Test message");
        
        // Verify - should fallback to default color
        assertNotNull(embed);
        assertEquals(Color.GREEN.getRGB(), embed.getColor().getRGB());
        
        verify(contextualLogger).warn(contains("Invalid embed color"));
    }
    
    // Helper methods
    
    private PlayerStats createMockPlayerStats(String name, int wins, int kills) {
        UUID playerId = UUID.randomUUID();
        PlayerStats stats = new PlayerStats(playerId, name);
        stats.setWins(wins);
        stats.setKills(kills);
        stats.setGamesPlayed(wins + 5); // Some losses
        return stats;
    }
}