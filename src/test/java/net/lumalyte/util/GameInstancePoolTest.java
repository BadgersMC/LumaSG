package net.lumalyte.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import net.lumalyte.game.Game;
import net.lumalyte.arena.Arena;

/**
 * Test class for GameInstancePool functionality.
 * Tests game instance management, arena mapping, and concurrent operations.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GameInstancePool Tests")
public class GameInstancePoolTest {

    private Game mockGame1;
    private Game mockGame2;
    private Game mockGame3;
    private Arena mockArena1;
    private Arena mockArena2;

    @BeforeEach
    void setUp() {
        // Create mock games with proper UUIDs
        mockGame1 = mock(Game.class);
        UUID gameId1 = UUID.randomUUID();
        when(mockGame1.getGameId()).thenReturn(gameId1);
        Arena arena1 = mock(Arena.class);
        when(arena1.getName()).thenReturn("arena1");
        when(mockGame1.getArena()).thenReturn(arena1);
        
        mockGame2 = mock(Game.class);
        UUID gameId2 = UUID.randomUUID();
        when(mockGame2.getGameId()).thenReturn(gameId2);
        Arena arena1_2 = mock(Arena.class);
        when(arena1_2.getName()).thenReturn("arena1");
        when(mockGame2.getArena()).thenReturn(arena1_2);
        
        mockGame3 = mock(Game.class);
        UUID gameId3 = UUID.randomUUID();
        when(mockGame3.getGameId()).thenReturn(gameId3);
        Arena arena2 = mock(Arena.class);
        when(arena2.getName()).thenReturn("arena2");
        when(mockGame3.getArena()).thenReturn(arena2);
        
        // Create mock arenas
        mockArena1 = mock(Arena.class);
        when(mockArena1.getName()).thenReturn("arena1");
        
        mockArena2 = mock(Arena.class);
        when(mockArena2.getName()).thenReturn("arena2");
    }

    @Test
    @DisplayName("Register and Retrieve Game")
    void testRegisterAndGetGame() {
        // Register a game
        GameInstancePool.registerGame(mockGame1);
        
        // Retrieve the game
        Game retrievedGame = GameInstancePool.getGame(mockGame1.getGameId());
        
        assertNotNull(retrievedGame, "Retrieved game should not be null");
        assertEquals(mockGame1.getGameId(), retrievedGame.getGameId(), "Game IDs should match");
    }

    @Test
    @DisplayName("Remove Game")
    void testRemoveGame() {
        // Register and then remove a game
        GameInstancePool.registerGame(mockGame1);
        
        UUID gameId = mockGame1.getGameId();
        
        // Verify game exists before removal
        assertNotNull(GameInstancePool.getGame(gameId), "Game should exist before removal");
        
        // Remove the game
        GameInstancePool.removeGame(gameId);
        
        // Verify game no longer exists
        assertNull(GameInstancePool.getGame(gameId), "Game should not exist after removal");
    }

    @Test
    @DisplayName("Get Games for Arena")
    void testGetGamesForArena() {
        // Register games
        GameInstancePool.registerGame(mockGame1);
        GameInstancePool.registerGame(mockGame2);
        GameInstancePool.registerGame(mockGame3);
        
        // Get games for arena1
        Set<Game> arena1Games = GameInstancePool.getGamesForArena("arena1");
        
        assertNotNull(arena1Games, "Arena1 games should not be null");
        assertEquals(2, arena1Games.size(), "Arena1 should have 2 games");
        
        // Verify all games belong to arena1
        for (Game game : arena1Games) {
            assertEquals("arena1", game.getArena().getName(), "All games should belong to arena1");
        }
        
        // Get games for arena2
        Set<Game> arena2Games = GameInstancePool.getGamesForArena("arena2");
        assertEquals(1, arena2Games.size(), "Arena2 should have 1 game");
        assertEquals("arena2", arena2Games.iterator().next().getArena().getName(), "Game should belong to arena2");
    }

    @Test
    @DisplayName("Get Active Game Count")
    void testGetActiveGameCount() {
        // Initially should be 0
        assertEquals(0, GameInstancePool.getActiveGameCount(), "Initial game count should be 0");
        
        // Register games and verify count
        GameInstancePool.registerGame(mockGame1);
        assertEquals(1, GameInstancePool.getActiveGameCount(), "Game count should be 1 after registering one game");
        
        GameInstancePool.registerGame(mockGame2);
        assertEquals(2, GameInstancePool.getActiveGameCount(), "Game count should be 2 after registering two games");
        
        // Remove a game and verify count
        GameInstancePool.removeGame(mockGame1.getGameId());
        assertEquals(1, GameInstancePool.getActiveGameCount(), "Game count should be 1 after removing one game");
    }

    @Test
    @DisplayName("Concurrent Game Registration")
    void testConcurrentGameRegistration() throws InterruptedException {
        int threadCount = 10;
        int gamesPerThread = 5;
        CountDownLatch latch = new CountDownLatch(threadCount);
        
        List<Thread> threads = new ArrayList<>();
        
        for (int i = 0; i < threadCount; i++) {
            final int threadIndex = i;
            Thread thread = new Thread(() -> {
                try {
                    for (int j = 0; j < gamesPerThread; j++) {
                        Game mockGame = mock(Game.class);
                        UUID gameId = UUID.randomUUID();
                        when(mockGame.getGameId()).thenReturn(gameId);
                        Arena arena = mock(Arena.class);
                        when(arena.getName()).thenReturn("arena" + (threadIndex % 3));
                        when(mockGame.getArena()).thenReturn(arena);
                        
                        GameInstancePool.registerGame(mockGame);
                    }
                } finally {
                    latch.countDown();
                }
            });
            threads.add(thread);
            thread.start();
        }
        
        assertTrue(latch.await(10, TimeUnit.SECONDS), "All threads should complete within 10 seconds");
        
        // Verify expected number of games were registered
        long expectedGames = threadCount * gamesPerThread;
        assertEquals(expectedGames, GameInstancePool.getActiveGameCount(), 
            "Should have registered " + expectedGames + " games");
    }

    @Test
    @DisplayName("Handle Non-Existent Game Retrieval")
    void testGetNonExistentGame() {
        UUID nonExistentGameId = UUID.randomUUID();
        
        Game result = GameInstancePool.getGame(nonExistentGameId);
        
        assertNull(result, "Getting non-existent game should return null");
    }

    @Test
    @DisplayName("Handle Non-Existent Game Removal")
    void testRemoveNonExistentGame() {
        UUID nonExistentGameId = UUID.randomUUID();
        
        // This should not throw an exception
        assertDoesNotThrow(() -> {
            GameInstancePool.removeGame(nonExistentGameId);
        }, "Removing non-existent game should not throw exception");
    }

    @Test
    @DisplayName("Handle Empty Arena Query")
    void testGetGamesForEmptyArena() {
        Set<Game> result = GameInstancePool.getGamesForArena("nonexistent-arena");
        
        assertNotNull(result, "Result should not be null");
        assertTrue(result.isEmpty(), "Result should be empty for non-existent arena");
    }

    @Test
    @DisplayName("Cache Statistics")
    void testCacheStatistics() {
        // Register some games
        for (int i = 0; i < 20; i++) {
            Game mockGame = mock(Game.class);
            UUID gameId = UUID.randomUUID();
            when(mockGame.getGameId()).thenReturn(gameId);
            Arena arena = mock(Arena.class);
            when(arena.getName()).thenReturn("arena" + (i % 10));
            when(mockGame.getArena()).thenReturn(arena);
            
            GameInstancePool.registerGame(mockGame);
        }
        
        // Get cache statistics
        String stats = GameInstancePool.getCacheStats();
        
        assertNotNull(stats, "Cache stats should not be null");
        assertFalse(stats.isEmpty(), "Cache stats should not be empty");
        assertTrue(stats.contains("GamePool"), "Stats should contain GamePool information");
        
        System.out.println("Cache Statistics: " + stats);
    }

    @Test
    @DisplayName("Multiple Arena Operations")
    void testMultipleArenaOperations() {
        // Register games across multiple arenas
        for (int arena = 0; arena < 5; arena++) {
            for (int game = 0; game < 3; game++) {
                Game mockGame = mock(Game.class);
                UUID gameId = UUID.randomUUID();
                when(mockGame.getGameId()).thenReturn(gameId);
                Arena arenaObj = mock(Arena.class);
                when(arenaObj.getName()).thenReturn("arena" + arena);
                when(mockGame.getArena()).thenReturn(arenaObj);
                
                GameInstancePool.registerGame(mockGame);
            }
        }
        
        // Verify each arena has the expected number of games
        for (int arena = 0; arena < 5; arena++) {
            Set<Game> arenaGames = GameInstancePool.getGamesForArena("arena" + arena);
            assertEquals(3, arenaGames.size(), "Arena " + arena + " should have 3 games");
        }
        
        // Verify total game count
        assertEquals(15, GameInstancePool.getActiveGameCount(), "Total should be 15 games");
    }
}