package net.lumalyte.lumasg.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import net.lumalyte.lumasg.arena.Arena;
import net.lumalyte.lumasg.game.core.Game;

/**
 * Common test utilities to reduce code duplication across test files
 */
public class TestUtils {

    /**
         * Mock location class to replace Bukkit Location in tests
         */
        public record MockLocation(String world, int x, int y, int z) {

        @Override
            public String toString() {
                return world + ":" + x + "," + y + "," + z;
            }

        @Override
            public boolean equals(Object obj) {
                if (this == obj) return true;
                if (!(obj instanceof MockLocation)) return false;
                MockLocation other = (MockLocation) obj;
                return Objects.equals(world, other.world) && x == other.x && y == other.y && z == other.z;
            }

    }

    /**
         * Performance test result container
         */
        public record PerformanceResult(String operation, long durationNanos, int itemsProcessed, boolean success) {

        public double getDurationMs() {
                return durationNanos / 1_000_000.0;
            }

        public double getItemsPerSecond() {
                return (itemsProcessed * 1_000_000_000.0) / durationNanos;
            }

        @Override
            public String toString() {
                return String.format("%s: %.2f ms, %d items, %.0f items/sec, %s",
                        operation, getDurationMs(), itemsProcessed, getItemsPerSecond(),
                        success ? "SUCCESS" : "FAILED");
            }
        }
    
    /**
     * Creates a mock game with the specified ID and arena name
     */
    public static net.lumalyte.lumasg.game.core.Game createMockGame(String gameId, String arenaName) {
        Game game = mock(Game.class);
        when(game.getGameId()).thenReturn(UUID.fromString(gameId));
        Arena arena = mock(Arena.class);
        when(arena.getName()).thenReturn(arenaName);
        when(game.getArena()).thenReturn(arena);
        return game;
    }
    
    /**
     * Creates a mock game with random UUID and specified arena name
     */
    public static Game createMockGame(String arenaName) {
        Game game = mock(Game.class);
        when(game.getGameId()).thenReturn(UUID.randomUUID());
        Arena arena = mock(Arena.class);
        when(arena.getName()).thenReturn(arenaName);
        when(game.getArena()).thenReturn(arena);
        return game;
    }
    
    /**
     * Creates a list of mock chest locations for testing
     */
    public static List<MockLocation> createMockChestLocations(String world, int count) {
        List<MockLocation> locations = new ArrayList<>();
        Random random = new Random(42); // Fixed seed for consistent tests
        
        for (int i = 0; i < count; i++) {
            int x = random.nextInt(1000) - 500;
            int y = random.nextInt(100) + 1;
            int z = random.nextInt(1000) - 500;
            locations.add(new MockLocation(world, x, y, z));
        }
        
        return locations;
    }
    
    /**
     * Simulates chest filling operation with specified duration
     */
    public static void simulateChestFill(double durationMs) {
        try {
            // Simulate work with a mix of CPU and I/O operations
            long startTime = System.nanoTime();
            long targetNanos = (long) (durationMs * 1_000_000);
            
            // Do some CPU work
            int dummy = 0;
            while (System.nanoTime() - startTime < targetNanos * 0.8) {
                dummy += (int) (Math.random() * 1000);
            }
            
            // Simulate I/O wait
            Thread.sleep(Math.max(1, (long) (durationMs * 0.2)));
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * Measures performance of a runnable operation
     */
    public static PerformanceResult measurePerformance(String operation, int itemCount, Runnable task) {
        long startTime = System.nanoTime();
        boolean success = true;
        
        try {
            task.run();
        } catch (Exception e) {
            success = false;
        }
        
        long duration = System.nanoTime() - startTime;
        return new PerformanceResult(operation, duration, itemCount, success);
    }
    
    /**
     * Runs concurrent performance test with specified parameters
     */
    public static List<PerformanceResult> runConcurrentPerformanceTest(
            String operation, int threadCount, int operationsPerThread, 
            java.util.function.Supplier<Runnable> taskSupplier) throws InterruptedException {
        
        CountDownLatch latch = new CountDownLatch(threadCount);
        List<PerformanceResult> results = Collections.synchronizedList(new ArrayList<>());
        List<Thread> threads = new ArrayList<>();
        
        for (int i = 0; i < threadCount; i++) {
            Thread thread = new Thread(() -> {
                try {
                    for (int j = 0; j < operationsPerThread; j++) {
                        PerformanceResult result = measurePerformance(operation, 1, taskSupplier.get());
                        results.add(result);
                    }
                } finally {
                    latch.countDown();
                }
            });
            threads.add(thread);
            thread.start();
        }
        
        latch.await(30, TimeUnit.SECONDS);
        return results;
    }
    
    /**
     * Calculates statistics from performance results
     */
    public static String calculatePerformanceStats(List<PerformanceResult> results) {
        if (results.isEmpty()) {
            return "No results";
        }
        
        double totalMs = results.stream().mapToDouble(PerformanceResult::getDurationMs).sum();
        double avgMs = totalMs / results.size();
        double minMs = results.stream().mapToDouble(PerformanceResult::getDurationMs).min().orElse(0);
        double maxMs = results.stream().mapToDouble(PerformanceResult::getDurationMs).max().orElse(0);
        long successCount = results.stream().mapToLong(r -> r.success() ? 1 : 0).sum();
        
        return String.format(
            "Results: %d operations, %.2f ms avg, %.2f ms min, %.2f ms max, %d/%d successful (%.1f%%)",
            results.size(), avgMs, minMs, maxMs, successCount, results.size(), 
            (successCount * 100.0) / results.size()
        );
    }
    
    /**
     * Simulates expensive data generation for cache testing
     */
    public static String simulateExpensiveDataGeneration(String key) {
        // Simulate computational work
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            result.append(key).append("-").append(i).append("-").append(Math.random());
        }
        
        // Simulate I/O delay
        try {
            Thread.sleep(1);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        return result.toString();
    }
    
    /**
     * Generates realistic cache keys for testing
     */
    public static String generateRealisticCacheKey(int operation, int uniqueKeys) {
        String[] prefixes = {"player", "game", "arena", "chest", "item"};
        String[] operations = {"data", "stats", "config", "state", "meta"};
        
        String prefix = prefixes[operation % prefixes.length];
        String op = operations[operation % operations.length];
        int id = operation % uniqueKeys;
        
        return prefix + ":" + op + ":" + id;
    }
    
    /**
     * Prints formatted performance results
     */
    public static void printPerformanceResults(String testName, List<PerformanceResult> results) {
        System.out.printf("%n%s Performance Results:%n", testName);
        System.out.println("━".repeat(50));
        System.out.println(calculatePerformanceStats(results));
        
        if (results.size() <= 10) {
            results.forEach(System.out::println);
        } else {
            System.out.println("First 5 results:");
            results.subList(0, 5).forEach(System.out::println);
            System.out.println("...");
            System.out.println("Last 5 results:");
            results.subList(results.size() - 5, results.size()).forEach(System.out::println);
        }
    }
    
    /**
     * Simulates work for performance testing with precise timing.
     * Handles both millisecond and sub-millisecond delays efficiently.
     * 
     * @param milliseconds The duration to simulate work for
     */
    public static void simulateWork(double milliseconds) {
        try {
            if (milliseconds >= 1) {
                // For delays >= 1ms, use Thread.sleep for efficiency
                Thread.sleep((long) milliseconds);
            } else {
                // For sub-millisecond delays, use busy waiting for precision
                long nanos = (long) (milliseconds * 1_000_000);
                long start = System.nanoTime();
                while (System.nanoTime() - start < nanos) {
                    // Busy wait for precise timing
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * Simulates work for performance testing with integer milliseconds.
     * 
     * @param durationMs The duration in milliseconds
     */
    public static void simulateWork(int durationMs) {
        simulateWork((double) durationMs);
    }
} 
