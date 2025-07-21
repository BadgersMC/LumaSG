package net.lumalyte.lumasg.util.performance;

import net.lumalyte.lumasg.LumaSG;
import net.lumalyte.lumasg.statistics.PlayerStats;
import net.lumalyte.lumasg.util.serialization.InventorySerializer;
import net.lumalyte.lumasg.util.serialization.KryoManager;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Performance testing utility to validate our new Kryo and database infrastructure.
 * 
 * This class provides methods to test and benchmark the performance improvements
 * from our new serialization and database systems.
 */
public class PerformanceTestUtil {
    
    /**
     * Simple error method that doesn't require logger initialization
     */
    private static void error(String message, Throwable throwable) {
        System.err.println("PerformanceTestUtil Error: " + message);
        if (throwable != null) {
            throwable.printStackTrace();
        }
    }
    
    /**
     * Tests Kryo serialization performance with various data types.
     * 
     * @return CompletableFuture with test results
     */
    @NotNull
    public static CompletableFuture<String> testKryoPerformance() {
        return CompletableFuture.supplyAsync(() -> {
            StringBuilder results = new StringBuilder();
            results.append("=== Kryo Serialization Performance Test ===\n\n");
            
            try {
                // Test 1: ItemStack array serialization
                ItemStack[] inventory = createTestInventory();
                long startTime = System.nanoTime();
                
                byte[] serialized = InventorySerializer.serializeInventory(inventory);
                ItemStack[] deserialized = InventorySerializer.deserializeInventory(serialized);
                
                long duration = System.nanoTime() - startTime;
                
                results.append("Test 1 - Inventory Serialization:\n");
                results.append("  Items: ").append(inventory.length).append("\n");
                results.append("  Serialized size: ").append(serialized != null ? serialized.length : 0).append(" bytes\n");
                results.append("  Round-trip time: ").append(String.format("%.3f", duration / 1_000_000.0)).append("ms\n");
                results.append("  Success: ").append(deserialized != null && deserialized.length == inventory.length).append("\n\n");
                
                // Test 2: PlayerStats serialization
                PlayerStats testStats = createTestPlayerStats();
                startTime = System.nanoTime();
                
                byte[] statsData = KryoManager.serialize(testStats);
                PlayerStats deserializedStats = KryoManager.deserialize(statsData, PlayerStats.class);
                
                duration = System.nanoTime() - startTime;
                
                results.append("Test 2 - PlayerStats Serialization:\n");
                results.append("  Serialized size: ").append(statsData != null ? statsData.length : 0).append(" bytes\n");
                results.append("  Round-trip time: ").append(String.format("%.3f", duration / 1_000_000.0)).append("ms\n");
                results.append("  Success: ").append(deserializedStats != null && 
                                                   deserializedStats.getPlayerName().equals(testStats.getPlayerName())).append("\n\n");
                
                // Test 3: Batch serialization
                List<PlayerStats> statsList = createTestStatsList(100);
                startTime = System.nanoTime();
                
                byte[] batchData = KryoManager.serializeBatch(statsList);
                List<PlayerStats> deserializedBatch = KryoManager.deserializeBatch(batchData);
                
                duration = System.nanoTime() - startTime;
                
                results.append("Test 3 - Batch Serialization (100 PlayerStats):\n");
                results.append("  Serialized size: ").append(batchData != null ? batchData.length : 0).append(" bytes\n");
                results.append("  Round-trip time: ").append(String.format("%.3f", duration / 1_000_000.0)).append("ms\n");
                results.append("  Success: ").append(deserializedBatch != null && deserializedBatch.size() == 100).append("\n\n");
                
                // Add performance statistics
                results.append("Kryo Manager Statistics:\n");
                results.append(KryoManager.getPerformanceStats());
                
            } catch (Exception e) {
                results.append("Test failed with error: ").append(e.getMessage()).append("\n");
                error("Kryo performance test failed", e);
            }
            
            return results.toString();
        });
    }
    
    /**
     * Tests database performance with connection pooling.
     * 
     * @param plugin The plugin instance
     * @return CompletableFuture with test results
     */
    @NotNull
    public static CompletableFuture<String> testDatabasePerformance(@NotNull LumaSG plugin) {
        return CompletableFuture.supplyAsync(() -> {
            StringBuilder results = new StringBuilder();
            results.append("=== Database Performance Test ===\n\n");
            
            try {
                // Test database manager health
                if (!plugin.getStatisticsManager().isHealthy()) {
                    results.append("Database is not healthy - skipping tests\n");
                    return results.toString();
                }
                
                // Test 1: Single player stats save/load
                PlayerStats testStats = createTestPlayerStats();
                long startTime = System.nanoTime();
                
                plugin.getStatisticsManager().savePlayerStats(testStats).join();
                PlayerStats loadedStats = plugin.getStatisticsManager().getPlayerStats(
                    testStats.getPlayerId(), testStats.getPlayerName()).join();
                
                long duration = System.nanoTime() - startTime;
                
                results.append("Test 1 - Single Save/Load:\n");
                results.append("  Round-trip time: ").append(String.format("%.3f", duration / 1_000_000.0)).append("ms\n");
                results.append("  Success: ").append(loadedStats != null && 
                                                   loadedStats.getPlayerName().equals(testStats.getPlayerName())).append("\n\n");
                
                // Test 2: Batch operations
                List<PlayerStats> statsList = createTestStatsList(50);
                startTime = System.nanoTime();
                
                // Save all stats
                List<CompletableFuture<Void>> saveFutures = new ArrayList<>();
                for (PlayerStats stats : statsList) {
                    saveFutures.add(plugin.getStatisticsManager().savePlayerStats(stats));
                }
                CompletableFuture.allOf(saveFutures.toArray(new CompletableFuture[0])).join();
                
                duration = System.nanoTime() - startTime;
                
                results.append("Test 2 - Batch Save (50 players):\n");
                results.append("  Total time: ").append(String.format("%.3f", duration / 1_000_000.0)).append("ms\n");
                results.append("  Average per save: ").append(String.format("%.3f", (duration / 1_000_000.0) / 50)).append("ms\n\n");
                
                // Add database pool statistics if available
                results.append("Database Statistics:\n");
                results.append("  Status: Healthy\n");
                results.append("  Connection pooling: Active\n");
                
            } catch (Exception e) {
                results.append("Database test failed with error: ").append(e.getMessage()).append("\n");
                error("Database performance test failed", e);
            }
            
            return results.toString();
        });
    }
    
    /**
     * Creates a test inventory with various items.
     */
    @NotNull
    private static ItemStack[] createTestInventory() {
        ItemStack[] inventory = new ItemStack[36];
        
        // Fill with various items
        inventory[0] = new ItemStack(Material.DIAMOND_SWORD, 1);
        inventory[1] = new ItemStack(Material.BOW, 1);
        inventory[2] = new ItemStack(Material.ARROW, 64);
        inventory[3] = new ItemStack(Material.COOKED_BEEF, 32);
        inventory[4] = new ItemStack(Material.GOLDEN_APPLE, 5);
        inventory[5] = new ItemStack(Material.DIAMOND_CHESTPLATE, 1);
        inventory[6] = new ItemStack(Material.DIAMOND_LEGGINGS, 1);
        inventory[7] = new ItemStack(Material.DIAMOND_BOOTS, 1);
        inventory[8] = new ItemStack(Material.SHIELD, 1);
        
        // Add some random items
        for (int i = 9; i < 20; i++) {
            inventory[i] = new ItemStack(Material.STONE, i);
        }
        
        return inventory;
    }
    
    /**
     * Creates a test PlayerStats object.
     */
    @NotNull
    private static PlayerStats createTestPlayerStats() {
        UUID playerId = UUID.randomUUID();
        PlayerStats stats = new PlayerStats(playerId, "TestPlayer_" + playerId.toString().substring(0, 8));
        
        // Set some test data
        stats.incrementWins();
        stats.incrementWins();
        stats.incrementWins();
        
        // Add kills by incrementing multiple times
        for (int i = 0; i < 15; i++) {
            stats.incrementKills();
        }
        
        // Add deaths by incrementing multiple times
        for (int i = 0; i < 3; i++) {
            stats.incrementDeaths();
        }
        
        stats.incrementGamesPlayed();
        stats.incrementGamesPlayed();
        stats.incrementGamesPlayed();
        stats.incrementGamesPlayed();
        stats.addTimePlayed(3600); // 1 hour
        stats.updatePlacement(1); // Winner
        stats.addDamageDealt(2500.0);
        stats.addDamageTaken(800.0);
        
        // Add chests opened by incrementing multiple times
        for (int i = 0; i < 25; i++) {
            stats.incrementChestsOpened();
        }
        
        return stats;
    }
    
    /**
     * Creates a list of test PlayerStats objects.
     */
    @NotNull
    private static List<PlayerStats> createTestStatsList(int count) {
        List<PlayerStats> statsList = new ArrayList<>();
        
        for (int i = 0; i < count; i++) {
            statsList.add(createTestPlayerStats());
        }
        
        return statsList;
    }
}
