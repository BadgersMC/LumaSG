package net.lumalyte.lumasg.util.serialization;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.potion.PotionEffect;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * Potion effect serialization using Kryo binary format.
 * 
 * Replaces manual JSON-like string parsing with binary serialization
 * for better performance and reliability.
 * 
 * Performance Benefits:
 * - No string parsing overhead
 * - Direct binary serialization/deserialization
 * - Smaller data size compared to JSON strings
 * - Type-safe operations (no parsing errors)
 * 
 * Thread Safety:
 * - Uses KryoManager's thread-safe pooling
 * - No shared state between operations
 * - Safe for concurrent use across multiple games
 */
public class PotionEffectSerializer {
    private static final Logger LOGGER = Logger.getLogger("LumaSG");
    
    /**
     * Serializes a collection of PotionEffects to binary format.
     * 
     * @param effects The collection of potion effects to serialize
     * @return Serialized byte array, or null if serialization fails or collection is empty
     */
    @Nullable
    public static byte[] serializePotionEffects(@Nullable Collection<PotionEffect> effects) {
        if (effects == null || effects.isEmpty()) {
            return null;
        }
        
        try {
            // Convert to array for more efficient serialization
            PotionEffect[] effectArray = effects.toArray(new PotionEffect[0]);
            return KryoManager.serialize(effectArray);
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "PotionEffectSerializer Error: Failed to serialize potion effects", e);
            return null;
        }
    }
    
    /**
     * Deserializes binary data back to a PotionEffect array.
     * 
     * @param data The serialized byte array
     * @return Deserialized PotionEffect array, or null if deserialization fails
     */
    @Nullable
    public static PotionEffect[] deserializePotionEffects(@Nullable byte[] data) {
        if (data == null || data.length == 0) {
            return null;
        }
        
        try {
            return KryoManager.deserialize(data, PotionEffect[].class);
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "PotionEffectSerializer Error: Failed to deserialize potion effects", e);
            return null;
        }
    }
    
    /**
     * Serializes a single PotionEffect to binary format.
     * 
     * @param effect The potion effect to serialize
     * @return Serialized byte array, or null if serialization fails
     */
    @Nullable
    public static byte[] serializePotionEffect(@Nullable PotionEffect effect) {
        if (effect == null) {
            return null;
        }
        
        try {
            return KryoManager.serialize(effect);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "PotionEffectSerializer Error: Failed to serialize single potion effect", e);
            return null;
        }
    }
    
    /**
     * Deserializes binary data back to a single PotionEffect.
     * 
     * @param data The serialized byte array
     * @return Deserialized PotionEffect, or null if deserialization fails
     */
    @Nullable
    public static PotionEffect deserializePotionEffect(@Nullable byte[] data) {
        if (data == null || data.length == 0) {
            return null;
        }
        
        try {
            return KryoManager.deserialize(data, PotionEffect.class);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "PotionEffectSerializer Error: Failed to deserialize single potion effect", e);
            return null;
        }
    }
}