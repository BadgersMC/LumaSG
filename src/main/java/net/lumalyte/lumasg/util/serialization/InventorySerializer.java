package net.lumalyte.lumasg.util.serialization;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Inventory serialization using ItemStack.serializeAsBytes() and Base64 format.
 * 
 * Uses Bukkit 1.21's recommended ItemStack.serializeAsBytes() method which is
 * safer and more reliable than the deprecated BukkitObjectOutputStream.
 * 
 * Benefits:
 * - Uses recommended Bukkit 1.21+ serialization approach
 * - No deprecated API usage
 * - Reliable across all Java versions
 * - No module system access issues
 * - Human-readable Base64 format for debugging
 * - No external dependencies beyond Bukkit
 * 
 * Thread Safety:
 * - No shared state between operations
 * - Safe for concurrent use across multiple games
 */
public class InventorySerializer {
    private static final Logger LOGGER = Logger.getLogger("LumaSG");

    /**
     * Simple debug method that doesn't require logger initialization
     */
    @SuppressWarnings("unused")
    private static void debug(String message) {
        // Debug disabled for utility class
    }

    /**
     * Log an error message with the plugin logger
     */
    private static void error(String message, Throwable throwable) {
        LOGGER.log(Level.SEVERE, "InventorySerializer Error: " + message, throwable);
    }

    /**
     * Serializes an ItemStack array (inventory contents) to Base64 format.
     * 
     * @param items The ItemStack array to serialize
     * @return Serialized byte array (Base64 encoded), or null if serialization
     *         fails
     */
    @Nullable
    public static byte[] serializeInventory(@Nullable ItemStack[] items) {
        if (items == null) {
            return null;
        }

        try {
            // Use Bukkit 1.21's recommended ItemStack.serializeItemsAsBytes() method
            byte[] serializedData = ItemStack.serializeItemsAsBytes(java.util.Arrays.asList(items));

            // Encode to Base64 and return as bytes
            String base64 = Base64.getEncoder().encodeToString(serializedData);
            byte[] result = base64.getBytes(StandardCharsets.UTF_8);

            debug("Serialized inventory with " + items.length + " slots to " +
                    result.length + " bytes (Base64)");

            return result;

        } catch (Exception e) {
            error("Failed to serialize inventory", e);

            // Fallback: Create empty inventory
            try {
                ItemStack[] emptyItems = new ItemStack[items.length];
                return serializeInventory(emptyItems);
            } catch (Exception fallbackException) {
                error("Even empty inventory serialization failed", fallbackException);
                return null;
            }
        }
    }

    /**
     * Deserializes Base64 data back to an ItemStack array.
     * 
     * @param data The serialized byte array (Base64 encoded)
     * @return Deserialized ItemStack array, or null if deserialization fails
     */
    @Nullable
    public static ItemStack[] deserializeInventory(@Nullable byte[] data) {
        if (data == null || data.length == 0) {
            return null;
        }

        try {
            // Decode from Base64
            String base64 = new String(data, StandardCharsets.UTF_8);
            byte[] decodedData = Base64.getDecoder().decode(base64);

            // Use Bukkit 1.21's recommended ItemStack.deserializeItemsFromBytes() method
            ItemStack[] result = ItemStack.deserializeItemsFromBytes(decodedData);

            debug("Deserialized inventory with " + result.length + " slots from " +
                    data.length + " bytes (Base64)");

            return result;

        } catch (Exception e) {
            error("Failed to deserialize inventory", e);
            return null;
        }
    }

    /**
     * Serializes a single ItemStack to Base64 format.
     * 
     * @param item The ItemStack to serialize
     * @return Serialized byte array (Base64 encoded), or null if serialization
     *         fails
     */
    @Nullable
    public static byte[] serializeItem(@Nullable ItemStack item) {
        if (item == null) {
            return null;
        }

        try {
            // Use Bukkit 1.21's recommended ItemStack.serializeAsBytes() method
            byte[] serializedData = item.serializeAsBytes();

            // Encode to Base64 and return as bytes
            String base64 = Base64.getEncoder().encodeToString(serializedData);
            return base64.getBytes(StandardCharsets.UTF_8);

        } catch (Exception e) {
            error("Failed to serialize ItemStack", e);
            return null;
        }
    }

    /**
     * Deserializes Base64 data back to a single ItemStack.
     * 
     * @param data The serialized byte array (Base64 encoded)
     * @return Deserialized ItemStack, or null if deserialization fails
     */
    @Nullable
    public static ItemStack deserializeItem(@Nullable byte[] data) {
        if (data == null || data.length == 0) {
            return null;
        }

        try {
            // Decode from Base64
            String base64 = new String(data, StandardCharsets.UTF_8);
            byte[] decodedData = Base64.getDecoder().decode(base64);

            // Use Bukkit 1.21's recommended ItemStack.deserializeBytes() method
            ItemStack result = ItemStack.deserializeBytes(decodedData);
            return result;

        } catch (Exception e) {
            error("Failed to deserialize ItemStack", e);
            return null;
        }
    }

    /**
     * Calculates the size of Base64 serialized data.
     * 
     * @param items The ItemStack array to analyze
     * @return Formatted string with size information
     */
    @NotNull
    public static String calculateSize(@Nullable ItemStack[] items) {
        if (items == null) {
            return "No items to analyze";
        }

        try {
            // Serialize with Base64
            byte[] base64Data = serializeInventory(items);
            if (base64Data == null) {
                return "Failed to serialize with Base64";
            }

            return String.format(
                    "Serialization Info:\n" +
                            "  Base64 size: %d bytes\n" +
                            "  Item count: %d\n" +
                            "  Average per item: %.1f bytes",
                    base64Data.length,
                    items.length,
                    (double) base64Data.length / items.length);

        } catch (Exception e) {
            return "Failed to calculate size: " + e.getMessage();
        }
    }
}