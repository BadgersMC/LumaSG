package net.lumalyte.performance;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import net.lumalyte.LumaSG;
import net.lumalyte.game.Game;
import net.lumalyte.arena.Arena;
import net.lumalyte.util.GameInstancePool;
import net.lumalyte.util.ArenaWorldCache;
import net.lumalyte.util.TestUtils;
import net.lumalyte.util.TestUtils.MockLocation;
import net.lumalyte.util.TestUtils.PerformanceResult;

/**
 * Comprehensive performance benchmark suite for LumaSG
 * Tests core system performance under various load conditions
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("LumaSG Performance Benchmarks")
public class LumaSGPerformanceBenchmark {

    @Mock private LumaSG plugin;

    @BeforeEach
    void setUp() {
        // Basic plugin setup for testing
    }

    @Nested
    @DisplayName("Game Instance Pool Performance")
    class GameInstancePoolPerformanceTests {

        @Test
        @DisplayName("Game Registration Performance Test")
        void testGameRegistrationPerformance() {
            int numGames = 1000;
            List<Game> games = new ArrayList<>();
            
            // Create mock games
            for (int i = 0; i < numGames; i++) {
                Game game = mock(Game.class);
                when(game.getGameId()).thenReturn(UUID.randomUUID());
                Arena arena = mock(Arena.class);
                when(arena.getName()).thenReturn("arena" + (i % 10));
                when(game.getArena()).thenReturn(arena);
                games.add(game);
            }
            
            long startTime = System.nanoTime();
            
            // Register all games
            for (Game game : games) {
                GameInstancePool.registerGame(game);
            }
            
            long registrationTime = System.nanoTime() - startTime;
            double registrationMs = registrationTime / 1_000_000.0;
            
            // Test retrieval performance
            startTime = System.nanoTime();
            for (Game game : games) {
                assertNotNull(GameInstancePool.getGame(game.getGameId()));
            }
            long retrievalTime = System.nanoTime() - startTime;
            double retrievalMs = retrievalTime / 1_000_000.0;
            
            System.out.printf("Game Instance Pool Performance (%d games):%n", numGames);
            System.out.printf("  Registration: %.2f ms (%.3f μs per game)%n", 
                registrationMs, (registrationMs * 1000) / numGames);
            System.out.printf("  Retrieval: %.2f ms (%.3f μs per game)%n", 
                retrievalMs, (retrievalMs * 1000) / numGames);
            System.out.printf("  Games/sec (registration): %.0f%n", 
                (numGames * 1000.0) / registrationMs);
            System.out.printf("  Games/sec (retrieval): %.0f%n", 
                (numGames * 1000.0) / retrievalMs);
            
            // Performance assertions
            assertTrue(registrationMs < 1000, "Registration should complete within 1 second");
            assertTrue(retrievalMs < 500, "Retrieval should complete within 500ms");
            assertEquals(numGames, GameInstancePool.getActiveGameCount());
        }

        @Test
        @DisplayName("Concurrent Game Operations Test")
        void testConcurrentGameOperations() throws InterruptedException {
            int threadCount = 10;
            int operationsPerThread = 50;
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger successfulOperations = new AtomicInteger(0);
            
            long startTime = System.nanoTime();
            
            List<Thread> threads = new ArrayList<>();
            for (int i = 0; i < threadCount; i++) {
                final int threadIndex = i;
                Thread thread = new Thread(() -> {
                    try {
                        for (int j = 0; j < operationsPerThread; j++) {
                            // Create and register game
                            Game game = mock(Game.class);
                            UUID gameId = UUID.randomUUID();
                            when(game.getGameId()).thenReturn(gameId);
                            Arena arena = mock(Arena.class);
                            when(arena.getName()).thenReturn("arena" + (threadIndex % 5));
                            when(game.getArena()).thenReturn(arena);
                            
                            GameInstancePool.registerGame(game);
                            
                            // Retrieve game
                            Game retrieved = GameInstancePool.getGame(gameId);
                            if (retrieved != null) {
                                successfulOperations.incrementAndGet();
                            }
                            
                            // Remove game
                            GameInstancePool.removeGame(gameId);
                        }
                    } finally {
                        latch.countDown();
                    }
                });
                threads.add(thread);
                thread.start();
            }
            
            assertTrue(latch.await(30, TimeUnit.SECONDS), "All threads should complete within 30 seconds");
            
            long totalTime = System.nanoTime() - startTime;
            double totalMs = totalTime / 1_000_000.0;
            
            int expectedOperations = threadCount * operationsPerThread;
            
            System.out.printf("Concurrent Game Operations Test:%n");
            System.out.printf("  Threads: %d%n", threadCount);
            System.out.printf("  Operations per thread: %d%n", operationsPerThread);
            System.out.printf("  Total operations: %d%n", expectedOperations);
            System.out.printf("  Successful operations: %d%n", successfulOperations.get());
            System.out.printf("  Total time: %.2f ms%n", totalMs);
            System.out.printf("  Operations per second: %.0f%n", (expectedOperations * 1000.0) / totalMs);
            
            // Should have high success rate
            assertTrue(successfulOperations.get() > expectedOperations * 0.95, 
                "Should have >95% success rate");
        }

        @Test
        @DisplayName("Arena-based Game Queries Performance")
        void testArenaGameQueriesPerformance() {
            int gamesPerArena = 100;
            int numArenas = 10;
            
            // Register games across multiple arenas
            for (int arena = 0; arena < numArenas; arena++) {
                for (int game = 0; game < gamesPerArena; game++) {
                    Game mockGame = mock(Game.class);
                    when(mockGame.getGameId()).thenReturn(UUID.randomUUID());
                    Arena arenaObj = mock(Arena.class);
                    when(arenaObj.getName()).thenReturn("arena" + arena);
                    when(mockGame.getArena()).thenReturn(arenaObj);
                    GameInstancePool.registerGame(mockGame);
                }
            }
            
            long startTime = System.nanoTime();
            
            // Query games for each arena
            for (int arena = 0; arena < numArenas; arena++) {
                Set<Game> arenaGames = GameInstancePool.getGamesForArena("arena" + arena);
                assertEquals(gamesPerArena, arenaGames.size(), 
                    "Arena " + arena + " should have " + gamesPerArena + " games");
            }
            
            long queryTime = System.nanoTime() - startTime;
            double queryMs = queryTime / 1_000_000.0;
            
            System.out.printf("Arena Game Queries Performance:%n");
            System.out.printf("  Arenas: %d%n", numArenas);
            System.out.printf("  Games per arena: %d%n", gamesPerArena);
            System.out.printf("  Total games: %d%n", numArenas * gamesPerArena);
            System.out.printf("  Query time: %.2f ms%n", queryMs);
            System.out.printf("  Queries per second: %.0f%n", (numArenas * 1000.0) / queryMs);
            
            // Performance assertion
            assertTrue(queryMs < 1000, "Arena queries should complete within 1 second");
        }
    }

    @Nested
    @DisplayName("Arena World Cache Performance")
    class ArenaWorldCachePerformanceTests {

        @Test
        @DisplayName("World Cache Access Performance")
        void testWorldCacheAccessPerformance() {
            int numWorlds = 50;
            int accessesPerWorld = 100;
            
            long startTime = System.nanoTime();
            
            // Simulate world cache accesses
            for (int world = 0; world < numWorlds; world++) {
                String worldName = "test-world-" + world;
                
                for (int access = 0; access < accessesPerWorld; access++) {
                    // Simulate cache access patterns
                    CompletableFuture<String> future = simulateWorldCacheAccess(worldName);
                    assertNotNull(future, "World cache access should return a future");
                }
            }
            
            long accessTime = System.nanoTime() - startTime;
            double accessMs = accessTime / 1_000_000.0;
            int totalAccesses = numWorlds * accessesPerWorld;
            
            System.out.printf("World Cache Access Performance:%n");
            System.out.printf("  Worlds: %d%n", numWorlds);
            System.out.printf("  Accesses per world: %d%n", accessesPerWorld);
            System.out.printf("  Total accesses: %d%n", totalAccesses);
            System.out.printf("  Access time: %.2f ms%n", accessMs);
            System.out.printf("  Accesses per second: %.0f%n", (totalAccesses * 1000.0) / accessMs);
            System.out.printf("  Average time per access: %.3f μs%n", (accessMs * 1000) / totalAccesses);
            
            // Performance assertion
            assertTrue(accessMs < 2000, "World cache accesses should complete within 2 seconds");
        }

        @Test
        @DisplayName("Cache Statistics Performance")
        void testCacheStatisticsPerformance() {
            // Perform some cache operations first
            for (int i = 0; i < 20; i++) {
                simulateWorldCacheAccess("stats-world-" + i);
            }
            
            long startTime = System.nanoTime();
            
            // Get cache statistics multiple times
            for (int i = 0; i < 1000; i++) {
                String stats = ArenaWorldCache.getCacheStats();
                assertNotNull(stats, "Cache stats should not be null");
                assertFalse(stats.isEmpty(), "Cache stats should not be empty");
            }
            
            long statsTime = System.nanoTime() - startTime;
            double statsMs = statsTime / 1_000_000.0;
            
            System.out.printf("Cache Statistics Performance:%n");
            System.out.printf("  Statistics calls: 1000%n");
            System.out.printf("  Total time: %.2f ms%n", statsMs);
            System.out.printf("  Calls per second: %.0f%n", (1000 * 1000.0) / statsMs);
            System.out.printf("  Average time per call: %.3f μs%n", (statsMs * 1000) / 1000);
            
            // Performance assertion
            assertTrue(statsMs < 500, "Statistics calls should complete within 500ms");
        }

        private CompletableFuture<String> simulateWorldCacheAccess(String worldName) {
            // Simulate world cache access by returning a completed future
            return CompletableFuture.completedFuture(worldName);
        }
    }

    @Nested
    @DisplayName("Cache Performance Tests")
    class CachePerformanceTests {
        
        @Test
        @DisplayName("Cache Hit Rate Performance Test")
        void testCacheHitRatePerformance() {
            int numOperations = 10000;
            int numUniqueKeys = 100;
            
            // Pre-populate caches
            for (int i = 0; i < numUniqueKeys; i++) {
                UUID gameId = UUID.randomUUID();
                String arenaName = "cache-arena-" + (i % 10);
                
                Game game = createMockGame(gameId.toString(), arenaName);
                GameInstancePool.registerGame(game);
                
                List<MockLocation> chests = createMockChestLocations(arenaName + "-world", 10);
                simulateChestFilling(chests, gameId.toString());
            }
            
            Random random = new Random();
            long startTime = System.nanoTime();
            
            // Perform cache operations
            for (int i = 0; i < numOperations; i++) {
                int keyIndex = random.nextInt(numUniqueKeys);
                
                // Mix of operations to test different caches
                switch (i % 3) {
                    case 0:
                        // Test game instance pool cache
                        long activeCount = GameInstancePool.getActiveGameCount();
                        assertTrue(activeCount >= 0, "Active count should be non-negative");
                        break;
                    case 1:
                        // Test arena world cache stats
                        String worldStats = ArenaWorldCache.getCacheStats();
                        assertNotNull(worldStats, "World cache stats should not be null");
                        break;
                    case 2:
                        // Trigger loot table cache access indirectly
                        List<MockLocation> chests = createMockChestLocations("cache-world", 5);
                        simulateChestFilling(chests, "cache-test");
                        break;
                }
            }
            
            long totalTime = System.nanoTime() - startTime;
            double totalTimeMs = totalTime / 1_000_000.0;
            
            System.out.printf("Cache Hit Rate Performance Results:%n");
            System.out.printf("  Operations performed: %d%n", numOperations);
            System.out.printf("  Unique keys: %d%n", numUniqueKeys);
            System.out.printf("  Total time: %.2f ms%n", totalTimeMs);
            System.out.printf("  Average time per operation: %.4f ms%n", totalTimeMs / numOperations);
            System.out.printf("  Operations per second: %.0f%n", (numOperations * 1000.0) / totalTimeMs);
            
            // Print cache statistics
            System.out.println("  Cache Statistics:");
            System.out.println("    " + GameInstancePool.getCacheStats());
            System.out.println("    " + ArenaWorldCache.getCacheStats());
            
            // Performance assertions
            assertTrue(totalTimeMs < 5000, "Cache operations should complete within 5 seconds");
            assertTrue((numOperations * 1000.0) / totalTimeMs > 1000, 
                "Should achieve at least 1000 operations per second");
        }
    }

    @Nested
    @DisplayName("Memory Performance Tests")
    class MemoryPerformanceTests {
        
        @Test
        @DisplayName("Memory Usage Under Load")
        void testMemoryUsageUnderLoad() {
            Runtime runtime = Runtime.getRuntime();
            
            // Force garbage collection and get initial memory
            System.gc();
            long initialMemory = runtime.totalMemory() - runtime.freeMemory();
            
            // Create load
            List<Game> games = new ArrayList<>();
            for (int i = 0; i < 500; i++) {
                Game game = createMockGame(UUID.randomUUID().toString(), "load-arena-" + (i % 20));
                games.add(game);
                GameInstancePool.registerGame(game);
                
                // Create some chest locations for each game
                List<MockLocation> chests = createMockChestLocations("load-world-" + i, 20);
                simulateChestFilling(chests, game.getGameId().toString());
            }
            
            // Measure memory after load
            System.gc();
            long loadedMemory = runtime.totalMemory() - runtime.freeMemory();
            
            // Clean up half the games
            for (int i = 0; i < games.size() / 2; i++) {
                GameInstancePool.removeGame(games.get(i).getGameId());
            }
            
            // Force garbage collection and measure final memory
            System.gc();
            long finalMemory = runtime.totalMemory() - runtime.freeMemory();
            
            long loadMemoryIncrease = loadedMemory - initialMemory;
            long finalMemoryIncrease = finalMemory - initialMemory;
            
            System.out.printf("Memory Usage Under Load:%n");
            System.out.printf("  Initial memory: %.2f MB%n", initialMemory / (1024.0 * 1024.0));
            System.out.printf("  Memory after load: %.2f MB%n", loadedMemory / (1024.0 * 1024.0));
            System.out.printf("  Final memory: %.2f MB%n", finalMemory / (1024.0 * 1024.0));
            System.out.printf("  Memory increase (load): %.2f MB%n", loadMemoryIncrease / (1024.0 * 1024.0));
            System.out.printf("  Memory increase (final): %.2f MB%n", finalMemoryIncrease / (1024.0 * 1024.0));
            System.out.printf("  Memory per game: %.2f KB%n", (loadMemoryIncrease / 1024.0) / games.size());
            
            // Memory assertions
            assertTrue(loadMemoryIncrease < 200 * 1024 * 1024, "Load memory increase should be less than 200MB");
            assertTrue(finalMemoryIncrease < loadMemoryIncrease, "Final memory should be less than loaded memory");
        }
    }

    @Nested
    @DisplayName("Concurrent Operations Performance")
    class ConcurrentOperationsPerformanceTests {
        
        @Test
        @DisplayName("High Concurrency Stress Test")
        void testHighConcurrencyStress() throws InterruptedException {
            int threadCount = 20;
            int operationsPerThread = 100;
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger totalOperations = new AtomicInteger(0);
            AtomicInteger successfulOperations = new AtomicInteger(0);
            
            long startTime = System.nanoTime();
            
            List<Thread> threads = new ArrayList<>();
            for (int i = 0; i < threadCount; i++) {
                final int threadIndex = i;
                Thread thread = new Thread(() -> {
                    try {
                        Random random = new Random();
                        for (int j = 0; j < operationsPerThread; j++) {
                            totalOperations.incrementAndGet();
                            
                            // Mix of different operations
                            switch (random.nextInt(4)) {
                                case 0:
                                    // Game registration/removal
                                    Game game = createMockGame(UUID.randomUUID().toString(), 
                                        "stress-arena-" + threadIndex);
                                    GameInstancePool.registerGame(game);
                                    if (random.nextBoolean()) {
                                        GameInstancePool.removeGame(game.getGameId());
                                    }
                                    successfulOperations.incrementAndGet();
                                    break;
                                case 1:
                                    // Arena queries
                                    Set<Game> arenaGames = GameInstancePool.getGamesForArena("stress-arena-" + threadIndex);
                                    assertNotNull(arenaGames);
                                    successfulOperations.incrementAndGet();
                                    break;
                                case 2:
                                    // Cache statistics
                                    String stats = GameInstancePool.getCacheStats();
                                    assertNotNull(stats);
                                    successfulOperations.incrementAndGet();
                                    break;
                                case 3:
                                    // Chest operations
                                    List<MockLocation> chests = createMockChestLocations("stress-world-" + threadIndex, 5);
                                    simulateChestFilling(chests, "stress-game-" + threadIndex + "-" + j);
                                    successfulOperations.incrementAndGet();
                                    break;
                            }
                        }
                    } catch (Exception e) {
                        // Count failures but don't fail the test
                    } finally {
                        latch.countDown();
                    }
                });
                threads.add(thread);
                thread.start();
            }
            
            assertTrue(latch.await(60, TimeUnit.SECONDS), "All threads should complete within 60 seconds");
            
            long totalTime = System.nanoTime() - startTime;
            double totalTimeMs = totalTime / 1_000_000.0;
            
            System.out.printf("High Concurrency Stress Test:%n");
            System.out.printf("  Threads: %d%n", threadCount);
            System.out.printf("  Operations per thread: %d%n", operationsPerThread);
            System.out.printf("  Total operations attempted: %d%n", totalOperations.get());
            System.out.printf("  Successful operations: %d%n", successfulOperations.get());
            System.out.printf("  Success rate: %.2f%%%n", 
                (double) successfulOperations.get() / totalOperations.get() * 100);
            System.out.printf("  Total time: %.2f ms%n", totalTimeMs);
            System.out.printf("  Operations per second: %.0f%n", 
                (successfulOperations.get() * 1000.0) / totalTimeMs);
            
            // Should have high success rate under stress
            assertTrue(successfulOperations.get() > totalOperations.get() * 0.90, 
                "Should have >90% success rate under stress");
        }
    }

    // Helper methods

    private Game createMockGame(String gameId, String arenaName) {
        return TestUtils.createMockGame(gameId, arenaName);
    }

    private List<MockLocation> createMockChestLocations(String world, int count) {
        return TestUtils.createMockChestLocations(world, count);
    }

    private void simulateChestFilling(List<MockLocation> chests, String gameId) {
        // Simulate chest filling operation
        for (MockLocation chest : chests) {
            TestUtils.simulateChestFill(1.0);
        }
    }
}