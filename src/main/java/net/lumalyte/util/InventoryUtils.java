package net.lumalyte.util;

import org.bukkit.inventory.ItemStack;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.yaml.snakeyaml.external.biz.base64Coder.Base64Coder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.HashMap;
import java.util.Map;

public class InventoryUtils {
    
    /**
     * Converts an array of ItemStacks to a Base64 string.
     * Uses standard Java serialization instead of deprecated Bukkit methods.
     */
    public static String itemStackArrayToBase64(ItemStack[] items) throws IllegalStateException {
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
     * Uses standard Java serialization instead of deprecated Bukkit methods.
     */
    public static ItemStack[] itemStackArrayFromBase64(String data) throws IOException {
        try {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64Coder.decodeLines(data));
            ObjectInputStream dataInput = new ObjectInputStream(inputStream);
            
            // Read the length of the array
            ItemStack[] items = new ItemStack[dataInput.readInt()];
    
            // Deserialize each ItemStack
            for (int i = 0; i < items.length; i++) {
                byte[] serialized = (byte[]) dataInput.readObject();
                if (serialized != null) {
                    items[i] = ItemStack.deserializeBytes(serialized);
                } else {
                    items[i] = null;
                }
            }
            
            dataInput.close();
            return items;
        } catch (ClassNotFoundException e) {
            throw new IOException("Unable to decode class type.", e);
        }
    }
} 