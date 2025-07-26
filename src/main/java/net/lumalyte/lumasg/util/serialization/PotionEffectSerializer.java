package net.lumalyte.lumasg.util.serialization;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.potion.PotionEffect;
import org.jetbrains.annotations.Nullable;

/**
 * Potion effect serialization using YAML configuration and Base64 format.
 * 
 * Uses Bukkit's YAML configuration system which properly handles PotionEffect serialization
 * without using deprecated APIs. This avoids the deprecated BukkitObjectOutputStream.
 * 
 * Benefits:
 * - Uses recommended Bukkit serialization approach
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
public class PotionEffectSerializer {
    private static final Logger LOGGER = Logger.getLogger("LumaSG");

    /**
     * Serializes a collection of PotionEffects to Base64 format.
     * 
     * @param effects The collection of potion effects to serialize
     * @return Serialized byte array (Base64 encoded), or null if serialization fails or collection is empty
     */
    @Nullable
    public static byte[] serializePotionEffects(@Nullable Collection<PotionEffect> effects) {
        if (effects == null || effects.isEmpty()) {
            return null;
        }

        try {
            // Create a YAML configuration to handle serialization
            YamlConfiguration config = new YamlConfiguration();
            
            // Convert collection to list for YAML serialization
            List<PotionEffect> effectList = new ArrayList<>(effects);
            config.set("effects", effectList);
            
            // Convert to YAML string and encode to Base64
            String yamlString = config.saveToString();
            return Base64.getEncoder().encode(yamlString.getBytes(StandardCharsets.UTF_8));

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "PotionEffectSerializer Error: Failed to serialize potion effects", e);

            // Fallback: Return empty effects array
            try {
                return serializePotionEffects(java.util.Collections.emptyList());
            } catch (Exception fallbackException) {
                LOGGER.log(Level.SEVERE, "Even empty potion effects serialization failed", fallbackException);
                return null;
            }
        }
    }

    /**
     * Deserializes Base64 data back to a PotionEffect array.
     * 
     * @param data The serialized byte array (Base64 encoded)
     * @return Deserialized PotionEffect array, or null if deserialization fails
     */
    @Nullable
    @SuppressWarnings("unchecked")
    public static PotionEffect[] deserializePotionEffects(@Nullable byte[] data) {
        if (data == null || data.length == 0) {
            return null;
        }

        try {
            // Decode from Base64
            byte[] decodedData = Base64.getDecoder().decode(data);
            String yamlString = new String(decodedData, StandardCharsets.UTF_8);

            // Load YAML configuration from string
            YamlConfiguration config = new YamlConfiguration();
            config.loadFromString(yamlString);
            
            // Get the effects list from the configuration
            List<?> effectsList = config.getList("effects");
            if (effectsList == null || effectsList.isEmpty()) {
                return null;
            }
            
            // Convert to PotionEffect array
            PotionEffect[] result = new PotionEffect[effectsList.size()];
            for (int i = 0; i < effectsList.size(); i++) {
                Object effectObj = effectsList.get(i);
                if (effectObj instanceof PotionEffect) {
                    result[i] = (PotionEffect) effectObj;
                } else {
                    LOGGER.log(Level.WARNING, "Invalid PotionEffect object at index " + i);
                    return null;
                }
            }

            return result;

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "PotionEffectSerializer Error: Failed to deserialize potion effects", e);
            return null;
        }
    }

    /**
     * Serializes a single PotionEffect to Base64 format.
     * 
     * @param effect The potion effect to serialize
     * @return Serialized byte array (Base64 encoded), or null if serialization fails
     */
    @Nullable
    public static byte[] serializePotionEffect(@Nullable PotionEffect effect) {
        if (effect == null) {
            return null;
        }

        try {
            // Create a YAML configuration to handle serialization
            YamlConfiguration config = new YamlConfiguration();
            config.set("effect", effect);
            
            // Convert to YAML string and encode to Base64
            String yamlString = config.saveToString();
            return Base64.getEncoder().encode(yamlString.getBytes(StandardCharsets.UTF_8));

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "PotionEffectSerializer Error: Failed to serialize single potion effect", e);
            return null;
        }
    }

    /**
     * Deserializes Base64 data back to a single PotionEffect.
     * 
     * @param data The serialized byte array (Base64 encoded)
     * @return Deserialized PotionEffect, or null if deserialization fails
     */
    @Nullable
    public static PotionEffect deserializePotionEffect(@Nullable byte[] data) {
        if (data == null || data.length == 0) {
            return null;
        }

        try {
            // Decode from Base64
            byte[] decodedData = Base64.getDecoder().decode(data);
            String yamlString = new String(decodedData, StandardCharsets.UTF_8);

            // Load YAML configuration from string
            YamlConfiguration config = new YamlConfiguration();
            config.loadFromString(yamlString);
            
            // Get the effect from the configuration
            Object effectObj = config.get("effect");
            if (effectObj instanceof PotionEffect) {
                return (PotionEffect) effectObj;
            } else {
                LOGGER.log(Level.WARNING, "Deserialized object is not a PotionEffect");
                return null;
            }

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "PotionEffectSerializer Error: Failed to deserialize single potion effect", e);
            return null;
        }
    }
}