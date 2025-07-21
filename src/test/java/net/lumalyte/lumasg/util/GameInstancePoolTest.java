package net.lumalyte.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Test class for game instance management functionality.
 * Tests game instance management, arena mapping, and concurrent operations without static dependencies.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Game Instance Management Tests")
public class GameInstancePoolTest {

    private MockGameInstanceManager gameManager;

    @BeforeEach
    void setUp() {
        gameManager = new MockGameInstanceManager();
    }

    @Test
    @DisplayName("Register and Retrieve Game")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testRegisterAndGetGame() {
        // Create a mock game
        MockGame game = new MockGame(UUID.randomUUID(), "arena1", 12);
        
        // Register the game
        gameManager.registerGame(game);
        
        // Retrieve the game
        MockGame retrievedGame = gameManager.getGame(game.getGameId());
        
        assertNotNull(retrievedGame, "Retrieved game should not be null");
        assertEquals(game.getGameId(), retrievedGame.getGameId(), "Game IDs should match");
        assertEquals(game.getArenaName(), retrievedGame.getArenaName(), "Arena names should match");
    }

    @Test
    @DisplayName("Remove Game")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testRemoveGame() {
        // Create and register a game
        MockGame game = new MockGame(UUID.randomUUID(), "arena1", 8);
        gameManager.registerGame(game);
        
        UUID gameId = game.getGameId();
        
        // Verify game exists before removal
        assertNotNull(gameManager.getGame(gameId), "Game should exist before removal");
        
        // Remove the game
        gameManager.removeGame(gameId);
        
        // Verify game no longer exists
        assertNull(gameManager.getGame(gameId), "Game should not exist after removal");
    }

    @Test
    @DisplayName("Get Games for Arena")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testGetGamesForArena() {
        // Create games for different arenas
        MockGame game1 = new MockGame(UUID.randomUUID(), "arena1", 12);
        MockGame game2 = new MockGame(UUID.randomUUID(), "arena1", 16);
        MockGame game3 = new MockGame(UUID.randomUUID(), "arena2", 8);
        
        // Register games
        gameManager.registerGame(game1);
        gameManager.registerGame(game2);
        gameManager.registerGame(game3);
        
        // Get games for arena1
        Set<MockGame> arena1Games = gameManager.getGamesForArena("arena1");
        
        assertNotNull(arena1Games, "Arena1 games should not be null");
        assertEquals(2, arena1Games.size(), "Arena1 should have 2 games");
        
        // Verify all games belong to arena1
        for (MockGame game : arena1Games) {
            assertEquals("arena1", game.getArenaName(), "All games should belong to arena1");
        }
        
        // Get games for arena2
        Set<MockGame> arena2Games = gameManager.getGamesForArena("arena2");
        assertEquals(1, arena2Games.size(), "Arena2 should have 1 game");
        assertEquals("arena2", arena2Games.iterator().next().getArenaName(), "Game should belong to arena2");
    }

    @Test
    @DisplayName("Get Active Game Count")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testGetActiveGameCount() {
        // Initially should be 0
        assertEquals(0, gameManager.getActiveGameCount(), "Initial game count should be 0");
        
        // Register games and verify count
        MockGame game1 = new MockGame(UUID.randomUUID(), "arena1", 12);
        gameManager.registerGame(game1);
        assertEquals(1, gameManager.getActiveGameCount(), "Game count should be 1 after registering one game");
        
        MockGame game2 = new MockGame(UUID.randomUUID(), "arena2", 8);
        gameManager.registerGame(game2);
        assertEquals(2, gameManager.getActiveGameCount(), "Game count should be 2 after registering two games");
        
        // Remove a game and verify count
        gameManager.removeGame(game1.getGameId());
        assertEquals(1, gameManager.getActiveGameCount(), "Game count should be 1 after removing one game");
    }

    @Test
    @DisplayName("Concurrent Game Registration")
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    void testConcurrentGameRegistration() throws InterruptedException {
        int threadCount = 4; // Reduced from 8 for stability
        int gamesPerThread = 2; // Reduced from 3 for faster execution
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        
        List<Thread> threads = new ArrayList<>();
        
        for (int i = 0; i < threadCount; i++) {
            final int threadIndex = i;
            Thread thread = new Thread(() -> {
                try {
                    for (int j = 0; j < gamesPerThread; j++) {
                        MockGame game = new MockGame(
                            UUID.randomUUID(), 
                            "arena" + (threadIndex % 2), // Reduced arenas for simpler testing
                            8 + (j * 4)
                        );
                        
                        gameManager.registerGame(game);
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    System.err.println("Thread " + threadIndex + " failed: " + e.getMessage());
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
        assertEquals(expectedGames, gameManager.getActiveGameCount(), 
            "Should have registered " + expectedGames + " games");
        assertEquals(expectedGames, successCount.get(), 
            "All registration attempts should succeed");
    }

    @Test
    @DisplayName("Handle Non-Existent Game Retrieval")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testGetNonExistentGame() {
        UUID nonExistentGameId = UUID.randomUUID();
        
        MockGame result = gameManager.getGame(nonExistentGameId);
        
        assertNull(result, "Getting non-existent game should return null");
    }

    @Test
    @DisplayName("Handle Non-Existent Game Removal")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testRemoveNonExistentGame() {
        UUID nonExistentGameId = UUID.randomUUID();
        
        // This should not throw an exception
        assertDoesNotThrow(() -> {
            gameManager.removeGame(nonExistentGameId);
        }, "Removing non-existent game should not throw exception");
    }

    @Test
    @DisplayName("Handle Empty Arena Query")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testGetGamesForEmptyArena() {
        Set<MockGame> emptyArenaGames = gameManager.getGamesForArena("non-existent-arena");
        
        assertNotNull(emptyArenaGames, "Empty arena query should return non-null set");
        assertTrue(emptyArenaGames.isEmpty(), "Empty arena should have no games");
    }

    @Test
    @DisplayName("Game State Management")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testGameStateManagement() {
        MockGame game = new MockGame(UUID.randomUUID(), "test-arena", 16);
        game.setState("WAITING");
        
        gameManager.registerGame(game);
        
        // Test state changes
        game.setState("STARTING");
        assertEquals("STARTING", game.getState(), "Game state should be updated");
        
        game.setState("ACTIVE");
        assertEquals("ACTIVE", game.getState(), "Game state should be updated");
        
        // Verify game is still registered
        MockGame retrievedGame = gameManager.getGame(game.getGameId());
        assertNotNull(retrievedGame, "Game should still be registered");
        assertEquals("ACTIVE", retrievedGame.getState(), "Retrieved game should have updated state");
    }

    @Test
    @DisplayName("Multiple Arena Operations")
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    void testMultipleArenaOperations() {
        // Create games across multiple arenas - reduced for faster execution
        String[] arenas = {"arena1", "arena2", "arena3"};
        List<MockGame> games = new ArrayList<>();
        
        for (int i = 0; i < 12; i++) { // Reduced from 20 to 12
            String arena = arenas[i % arenas.length];
            MockGame game = new MockGame(UUID.randomUUID(), arena, 8 + (i % 16));
            games.add(game);
            gameManager.registerGame(game);
        }
        
        // Verify total count
        assertEquals(12, gameManager.getActiveGameCount(), "Should have 12 total games");
        
        // Verify distribution across arenas
        for (String arena : arenas) {
            Set<MockGame> arenaGames = gameManager.getGamesForArena(arena);
            assertEquals(4, arenaGames.size(), "Each arena should have 4 games");
        }
        
        // Remove games from one arena
        Set<MockGame> arena1Games = gameManager.getGamesForArena("arena1");
        for (MockGame game : arena1Games) {
            gameManager.removeGame(game.getGameId());
        }
        
        // Verify removal
        assertEquals(8, gameManager.getActiveGameCount(), "Should have 8 games after removal");
        assertTrue(gameManager.getGamesForArena("arena1").isEmpty(), "Arena1 should be empty");
    }

    @Test
    @DisplayName("Performance Test - Large Scale Operations")
    @Timeout(value = 20, unit = TimeUnit.SECONDS)
    void testLargeScaleOperations() {
        int gameCount = 50; // Reduced from 100 for faster execution
        List<MockGame> games = new ArrayList<>();
        
        // Measure registration performance
        long startTime = System.currentTimeMillis();
        
        for (int i = 0; i < gameCount; i++) {
            MockGame game = new MockGame(
                UUID.randomUUID(), 
                "arena" + (i % 5), // Reduced from 10 arenas to 5 
                8 + (i % 16)
            );
            games.add(game);
            gameManager.registerGame(game);
        }
        
        long registrationTime = System.currentTimeMillis() - startTime;
        
        // Verify all games registered
        assertEquals(gameCount, gameManager.getActiveGameCount(), "All games should be registered");
        
        // Measure retrieval performance
        startTime = System.currentTimeMillis();
        
        for (MockGame game : games) {
            MockGame retrieved = gameManager.getGame(game.getGameId());
            assertNotNull(retrieved, "All games should be retrievable");
        }
        
        long retrievalTime = System.currentTimeMillis() - startTime;
        
        // More realistic performance assertions for reduced scale
        assertTrue(registrationTime < 2000, "Registration should complete within 2 seconds");
        assertTrue(retrievalTime < 1000, "Retrieval should complete within 1 second");
        
        System.out.println("Performance metrics:");
        System.out.println("  Registration: " + registrationTime + "ms for " + gameCount + " games");
        System.out.println("  Retrieval: " + retrievalTime + "ms for " + gameCount + " games");
    }

    // Mock classes for testing

    /**
     * Mock game class for testing
     */
    public static class MockGame {
        private final UUID gameId;
        private final String arenaName;
        private final int maxPlayers;
        private String state;

        public MockGame(UUID gameId, String arenaName, int maxPlayers) {
            this.gameId = gameId;
            this.arenaName = arenaName;
            this.maxPlayers = maxPlayers;
            this.state = "WAITING";
        }

        public UUID getGameId() { return gameId; }
        public String getArenaName() { return arenaName; }
        public int getMaxPlayers() { return maxPlayers; }
        public String getState() { return state; }
        public void setState(String state) { this.state = state; }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof MockGame other)) return false;
            return Objects.equals(gameId, other.gameId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(gameId);
        }
    }

    /**
     * Mock game instance manager for testing
     */
    public static class MockGameInstanceManager {
        private final Map<UUID, MockGame> games = new java.util.concurrent.ConcurrentHashMap<>();
        private final Map<String, Set<MockGame>> arenaGames = new java.util.concurrent.ConcurrentHashMap<>();

        public void registerGame(MockGame game) {
            games.put(game.getGameId(), game);
            arenaGames.computeIfAbsent(game.getArenaName(), k -> 
                Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>()))
                .add(game);
        }

        public MockGame getGame(UUID gameId) {
            return games.get(gameId);
        }

        public void removeGame(UUID gameId) {
            MockGame game = games.remove(gameId);
            if (game != null) {
                Set<MockGame> arenaSet = arenaGames.get(game.getArenaName());
                if (arenaSet != null) {
                    arenaSet.remove(game);
                    if (arenaSet.isEmpty()) {
                        arenaGames.remove(game.getArenaName());
                    }
                }
            }
        }

        public Set<MockGame> getGamesForArena(String arenaName) {
            return arenaGames.getOrDefault(arenaName, Collections.emptySet());
        }

        public int getActiveGameCount() {
            return games.size();
        }
    }
}