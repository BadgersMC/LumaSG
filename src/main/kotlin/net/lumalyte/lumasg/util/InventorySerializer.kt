package net.lumalyte.lumasg.util

import org.bukkit.inventory.ItemStack
import org.slf4j.LoggerFactory
import java.nio.charset.StandardCharsets
import java.util.Base64

/**
 * Inventory serialization using `ItemStack.serializeAsBytes()` and Base64 format.
 *
 * Uses Bukkit 1.21's recommended `ItemStack.serializeAsBytes()` method which is
 * safer and more reliable than the deprecated `BukkitObjectOutputStream`.
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
object InventorySerializer {

    private val logger = LoggerFactory.getLogger(InventorySerializer::class.java)

    /**
     * Serializes an [ItemStack] array (inventory contents) to Base64 format.
     *
     * @param items The ItemStack array to serialize
     * @return Serialized byte array (Base64 encoded), or null if items is null or serialization fails
     */
    fun serializeInventory(items: Array<ItemStack?>?): ByteArray? {
        if (items == null) return null

        return try {
            val serializedData = ItemStack.serializeItemsAsBytes(items.toList())
            val base64 = Base64.getEncoder().encodeToString(serializedData)
            base64.toByteArray(StandardCharsets.UTF_8)
        } catch (e: Exception) {
            logger.error("Failed to serialize inventory", e)
            // Fallback: attempt serialization with an empty inventory of the same size
            try {
                val emptyItems = arrayOfNulls<ItemStack>(items.size)
                val serializedData = ItemStack.serializeItemsAsBytes(emptyItems.toList())
                val base64 = Base64.getEncoder().encodeToString(serializedData)
                base64.toByteArray(StandardCharsets.UTF_8)
            } catch (fallback: Exception) {
                logger.error("Even empty inventory serialization failed", fallback)
                null
            }
        }
    }

    /**
     * Deserializes Base64 data back to an [ItemStack] array.
     *
     * @param data The serialized byte array (Base64 encoded)
     * @return Deserialized ItemStack array, or null if data is null/empty or deserialization fails
     */
    @Suppress("UNCHECKED_CAST")
    fun deserializeInventory(data: ByteArray?): Array<ItemStack?>? {
        if (data == null || data.isEmpty()) return null

        return try {
            val base64 = String(data, StandardCharsets.UTF_8)
            val decodedData = Base64.getDecoder().decode(base64)
            ItemStack.deserializeItemsFromBytes(decodedData) as Array<ItemStack?>
        } catch (e: Exception) {
            logger.error("Failed to deserialize inventory", e)
            null
        }
    }

    /**
     * Serializes a single [ItemStack] to Base64 format.
     *
     * @param item The ItemStack to serialize
     * @return Serialized byte array (Base64 encoded), or null if item is null or serialization fails
     */
    fun serializeItem(item: ItemStack?): ByteArray? {
        if (item == null) return null

        return try {
            val serializedData = item.serializeAsBytes()
            val base64 = Base64.getEncoder().encodeToString(serializedData)
            base64.toByteArray(StandardCharsets.UTF_8)
        } catch (e: Exception) {
            logger.error("Failed to serialize ItemStack", e)
            null
        }
    }

    /**
     * Deserializes Base64 data back to a single [ItemStack].
     *
     * @param data The serialized byte array (Base64 encoded)
     * @return Deserialized ItemStack, or null if data is null/empty or deserialization fails
     */
    fun deserializeItem(data: ByteArray?): ItemStack? {
        if (data == null || data.isEmpty()) return null

        return try {
            val base64 = String(data, StandardCharsets.UTF_8)
            val decodedData = Base64.getDecoder().decode(base64)
            ItemStack.deserializeBytes(decodedData)
        } catch (e: Exception) {
            logger.error("Failed to deserialize ItemStack", e)
            null
        }
    }

    /**
     * Calculates the size of Base64 serialized data for an inventory.
     *
     * @param items The ItemStack array to analyze
     * @return Formatted string with size information
     */
    fun calculateSize(items: Array<ItemStack?>?): String {
        if (items == null) return "No items to analyze"

        return try {
            val base64Data = serializeInventory(items)
                ?: return "Failed to serialize with Base64"

            "Serialization Info:\n" +
                "  Base64 size: ${base64Data.size} bytes\n" +
                "  Item count: ${items.size}\n" +
                "  Average per item: ${"%.1f".format(base64Data.size.toDouble() / items.size)} bytes"
        } catch (e: Exception) {
            "Failed to calculate size: ${e.message}"
        }
    }
}
