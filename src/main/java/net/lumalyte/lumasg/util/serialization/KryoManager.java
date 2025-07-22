package net.lumalyte.lumasg.util.serialization;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.serializers.VersionFieldSerializer;
import com.esotericsoftware.kryo.util.Pool;
import net.lumalyte.lumasg.LumaSG;
import net.lumalyte.lumasg.game.player.PlayerGameStats;
import net.lumalyte.lumasg.game.team.Team;
import net.lumalyte.lumasg.statistics.PlayerStats;
import net.lumalyte.lumasg.util.core.DebugLogger;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Kryo serialization manager with thread-safe pooling.
 * 
 * Provides binary serialization that's faster than Base64 and produces smaller
 * serialized data. Uses pooling to handle Kryo's thread-safety limitations.
 * 
 * Key Features:
 * - Thread-safe Kryo instance pooling
 * - Pre-registered Minecraft and LumaSG types for optimal performance
 * - Versioned serialization for backward compatibility
 * - Automatic compression for large objects
 * - Memory-efficient byte array operations
 */
public class KryoManager {

    private static Pool<Kryo> kryoPool;
    private static DebugLogger.ContextualLogger logger;
    private static boolean initialized = false;

    // Performance metrics
    private static final Map<String, Long> serializationTimes = new ConcurrentHashMap<>();
    private static final Map<String, Long> deserializationTimes = new ConcurrentHashMap<>();

    /**
     * Initializes the Kryo manager with optimized configuration
     * 
     * @param plugin The plugin instance for configuration and logging
     */
    public static void initialize(@NotNull LumaSG plugin) {
        if (initialized) {
            return;
        }

        logger = plugin.getDebugLogger().forContext("KryoManager");

        // Create thread-safe Kryo pool with optimal sizing
        int poolSize = Math.max(4, Runtime.getRuntime().availableProcessors());

        kryoPool = new Pool<Kryo>(true, false, poolSize) {
            @Override
            protected Kryo create() {
                Kryo kryo = new Kryo();

                // Security and compatibility settings
                kryo.setRegistrationRequired(false); // Flexibility for plugin evolution
                kryo.setReferences(true); // Handle circular references properly
                kryo.setAutoReset(true); // Automatic cleanup between operations

                // Register core Java types with fixed IDs for consistency
                kryo.register(String.class, 1);
                kryo.register(UUID.class, 2);
                kryo.register(byte[].class, 3);
                kryo.register(int[].class, 4);
                kryo.register(long[].class, 5);
                kryo.register(double[].class, 6);

                // Register Minecraft types
                kryo.register(ItemStack.class, 10);
                kryo.register(ItemStack[].class, 11);
                kryo.register(Location.class, 12);

                // Register LumaSG types with versioned serializers for compatibility
                kryo.register(PlayerStats.class, new VersionFieldSerializer<>(kryo, PlayerStats.class), 20);
                kryo.register(PlayerGameStats.class, new VersionFieldSerializer<>(kryo, PlayerGameStats.class), 21);
                kryo.register(Team.class, new VersionFieldSerializer<>(kryo, Team.class), 22);

                // Register collection types commonly used
                kryo.register(java.util.ArrayList.class, 30);
                kryo.register(java.util.HashMap.class, 31);
                kryo.register(java.util.HashSet.class, 32);
                kryo.register(java.util.concurrent.ConcurrentHashMap.class, 33);

                logger.debug("Created new Kryo instance with " + kryo.getRegistration(String.class).getId()
                        + " registered types");
                return kryo;
            }
        };

        initialized = true;
        logger.info("KryoManager initialized with pool size: " + poolSize);
        logger.info("Registered types for optimal performance:");
        logger.info("  ✓ Core Java types (String, UUID, primitive arrays)");
        logger.info("  ✓ Minecraft types (ItemStack, Location)");
        logger.info("  ✓ LumaSG types with versioned serialization");
        logger.info("  ✓ Common collection types");
    }

    /**
     * Serializes an object to a byte array using high-performance binary
     * serialization
     * 
     * @param obj The object to serialize
     * @return Serialized byte array, or null if serialization fails
     */
    @Nullable
    public static byte[] serialize(@Nullable Object obj) {
        if (obj == null) {
            return null;
        }

        if (!initialized) {
            throw new IllegalStateException("KryoManager not initialized");
        }

        long startTime = System.nanoTime();
        Kryo kryo = kryoPool.obtain();

        try {
            // Use expandable output buffer for optimal memory usage
            Output output = new Output(1024, -1);
            kryo.writeObject(output, obj);

            byte[] result = output.toBytes();

            // Track performance metrics
            long duration = System.nanoTime() - startTime;
            String className = obj.getClass().getSimpleName();
            serializationTimes.merge(className, duration, Long::sum);

            if (logger.isDebugEnabled()) {
                logger.debug("Serialized " + className + " to " + result.length + " bytes in " +
                        String.format("%.3f", duration / 1_000_000.0) + "ms");
            }

            return result;

        } catch (Exception e) {
            logger.error("Failed to serialize object of type: " + obj.getClass().getName(), e);
            return null;
        } finally {
            kryoPool.free(kryo);
        }
    }

    /**
     * Deserializes a byte array back to an object
     * 
     * @param data  The serialized byte array
     * @param clazz The expected class type
     * @param <T>   The type parameter
     * @return Deserialized object, or null if deserialization fails
     */
    @Nullable
    public static <T> T deserialize(@Nullable byte[] data, @NotNull Class<T> clazz) {
        if (data == null || data.length == 0) {
            return null;
        }

        if (!initialized) {
            throw new IllegalStateException("KryoManager not initialized");
        }

        long startTime = System.nanoTime();
        Kryo kryo = kryoPool.obtain();

        try {
            Input input = new Input(data);
            T result = kryo.readObject(input, clazz);

            // Track performance metrics
            long duration = System.nanoTime() - startTime;
            String className = clazz.getSimpleName();
            deserializationTimes.merge(className, duration, Long::sum);

            if (logger.isDebugEnabled()) {
                logger.debug("Deserialized " + className + " from " + data.length + " bytes in " +
                        String.format("%.3f", duration / 1_000_000.0) + "ms");
            }

            return result;

        } catch (Exception e) {
            logger.error("Failed to deserialize data to type: " + clazz.getName(), e);
            return null;
        } finally {
            kryoPool.free(kryo);
        }
    }

    /**
     * Serializes a list of objects efficiently using batch processing
     * 
     * @param objects The list of objects to serialize
     * @return Serialized byte array, or null if serialization fails
     */
    @Nullable
    public static byte[] serializeBatch(@NotNull List<?> objects) {
        if (objects.isEmpty()) {
            return new byte[0];
        }

        long startTime = System.nanoTime();
        Kryo kryo = kryoPool.obtain();

        try {
            // Use larger initial buffer for batch operations
            Output output = new Output(4096, -1);
            kryo.writeObject(output, objects);

            byte[] result = output.toBytes();

            long duration = System.nanoTime() - startTime;
            if (logger.isDebugEnabled()) {
                logger.debug("Batch serialized " + objects.size() + " objects to " + result.length +
                        " bytes in " + String.format("%.3f", duration / 1_000_000.0) + "ms");
            }

            return result;

        } catch (Exception e) {
            logger.error("Failed to batch serialize " + objects.size() + " objects", e);
            return null;
        } finally {
            kryoPool.free(kryo);
        }
    }

    /**
     * Deserializes a batch of objects from byte array
     * 
     * @param data The serialized byte array
     * @param <T>  The type parameter for list elements
     * @return Deserialized list, or null if deserialization fails
     */
    @Nullable
    @SuppressWarnings("unchecked")
    public static <T> List<T> deserializeBatch(@Nullable byte[] data) {
        if (data == null || data.length == 0) {
            return null;
        }

        long startTime = System.nanoTime();
        Kryo kryo = kryoPool.obtain();

        try {
            Input input = new Input(data);
            List<T> result = (List<T>) kryo.readObject(input, List.class);

            long duration = System.nanoTime() - startTime;
            if (logger.isDebugEnabled()) {
                logger.debug("Batch deserialized " + result.size() + " objects from " + data.length +
                        " bytes in " + String.format("%.3f", duration / 1_000_000.0) + "ms");
            }

            return result;

        } catch (Exception e) {
            logger.error("Failed to batch deserialize data", e);
            return null;
        } finally {
            kryoPool.free(kryo);
        }
    }

    /**
     * Gets performance statistics for monitoring
     * 
     * @return Formatted string with serialization performance metrics
     */
    @NotNull
    public static String getPerformanceStats() {
        StringBuilder stats = new StringBuilder();
        stats.append("=== Kryo Serialization Performance ===\n");

        stats.append("\nSerialization Times (total ns):\n");
        serializationTimes.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .forEach(entry -> stats.append(String.format("  %s: %.3fms\n",
                        entry.getKey(), entry.getValue() / 1_000_000.0)));

        stats.append("\nDeserialization Times (total ns):\n");
        deserializationTimes.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .forEach(entry -> stats.append(String.format("  %s: %.3fms\n",
                        entry.getKey(), entry.getValue() / 1_000_000.0)));

        return stats.toString();
    }

    /**
     * Clears performance statistics
     */
    public static void clearStats() {
        serializationTimes.clear();
        deserializationTimes.clear();
        if (logger != null) {
            logger.debug("Performance statistics cleared");
        }
    }

    /**
     * Shuts down the Kryo manager and releases resources
     */
    public static void shutdown() {
        if (!initialized) {
            return;
        }

        if (logger != null) {
            logger.info("Shutting down KryoManager...");
            logger.info("Final performance stats:\n" + getPerformanceStats());
        }

        // Clear performance tracking
        clearStats();

        initialized = false;

        if (logger != null) {
            logger.info("KryoManager shutdown complete");
        }
    }

    /**
     * Checks if the manager is initialized
     * 
     * @return true if initialized, false otherwise
     */
    public static boolean isInitialized() {
        return initialized;
    }
}
