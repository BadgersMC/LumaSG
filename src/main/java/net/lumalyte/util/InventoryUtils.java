package net.lumalyte.util;

import org.bukkit.inventory.ItemStack;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.yaml.snakeyaml.external.biz.base64Coder.Base64Coder;

import java.io.*;
import java.util.HashMap;
import java.util.Map;

public class InventoryUtils {
    
    /**
     * Maximum allowed size for the ItemStack array to prevent memory issues
     */
    private static final int MAX_ARRAY_SIZE = 54; // Maximum size of a double chest
    
    /**
     * Custom ObjectInputStream that only allows specific classes to be deserialized
     */
    private static class ValidatingObjectInputStream extends ObjectInputStream {
        public ValidatingObjectInputStream(InputStream in) throws IOException {
            super(in);
        }
        
        @Override
        protected Class<?> resolveClass(ObjectStreamClass desc) throws IOException, ClassNotFoundException {
            // Only allow byte[] class for ItemStack serialization
            if (!desc.getName().equals("[B")) {
                throw new InvalidClassException(
                    "Unauthorized deserialization attempt",
                    desc.getName()
                );
            }
            return super.resolveClass(desc);
        }
    }
    
    /**
     * Converts an array of ItemStacks to a Base64 string.
     * Uses standard Java serialization instead of deprecated Bukkit methods.
     */
    public static String itemStackArrayToBase64(ItemStack[] items) throws IllegalStateException {
        if (items == null) {
            throw new IllegalArgumentException("ItemStack array cannot be null");
        }
        if (items.length > MAX_ARRAY_SIZE) {
            throw new IllegalArgumentException("ItemStack array size exceeds maximum allowed size");
        }
        
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            ObjectOutputStream dataOutput = new ObjectOutputStream(outputStream);
            
            // Write the length of the array
            dataOutput.writeInt(items.length);
            
            // Serialize each ItemStack using ConfigurationSerialization
            for (ItemStack item : items) {
                if (item != null) {
                    dataOutput.writeObject(item.serializeAsBytes());
                } else {
                    dataOutput.writeObject(null);
                }
            }
            
            dataOutput.close();
            return Base64Coder.encodeLines(outputStream.toByteArray());
        } catch (Exception e) {
            throw new IllegalStateException("Unable to save item stacks.", e);
        }
    }
    
    /**
     * Converts a Base64 string to an array of ItemStacks.
     * Uses secure deserialization with validation.
     */
    public static ItemStack[] itemStackArrayFromBase64(String data) throws IOException {
        if (data == null || data.isEmpty()) {
            throw new IllegalArgumentException("Base64 string cannot be null or empty");
        }
        
        try {
            byte[] decoded = Base64Coder.decodeLines(data);
            if (decoded == null || decoded.length == 0) {
                throw new IllegalArgumentException("Invalid Base64 string");
            }
            
            ByteArrayInputStream inputStream = new ByteArrayInputStream(decoded);
            ObjectInputStream dataInput = new ValidatingObjectInputStream(inputStream);
            
            // Read and validate the array length
            int length = dataInput.readInt();
            if (length < 0 || length > MAX_ARRAY_SIZE) {
                throw new IllegalArgumentException("Invalid array length: " + length);
            }
            
            ItemStack[] items = new ItemStack[length];
    
            // Deserialize each ItemStack with validation
            for (int i = 0; i < items.length; i++) {
                try {
                    byte[] serialized = (byte[]) dataInput.readObject();
                    if (serialized != null) {
                        items[i] = ItemStack.deserializeBytes(serialized);
                    } else {
                        items[i] = null;
                    }
                } catch (ClassCastException e) {
                    throw new IOException("Invalid data format at index " + i, e);
                }
            }
            
            dataInput.close();
            return items;
        } catch (ClassNotFoundException e) {
            throw new IOException("Unable to decode class type.", e);
        }
    }
} 