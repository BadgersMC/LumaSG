package net.lumalyte.arena;

import net.lumalyte.util.DebugLogger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Helper class for arena configuration operations.
 */
public class ArenaConfigurationHelper {
    private final @NotNull DebugLogger.ContextualLogger logger;
    
    public ArenaConfigurationHelper(@NotNull DebugLogger.ContextualLogger logger) {
        this.logger = logger;
    }
    
    /**
     * Saves basic arena properties to configuration.
     */
    public void saveBasicProperties(
            @NotNull ConfigurationSection section,
            int maxPlayers,
            int minPlayers,
            int radius) {
        section.set("max-players", maxPlayers);
        section.set("min-players", minPlayers);
        section.set("radius", radius);
    }
    
    /**
     * Saves a location to configuration with proper error handling.
     */
    public void saveLocation(
            @NotNull ConfigurationSection parent,
            @NotNull String sectionName,
            @Nullable Location location) {
        if (location != null) {
            ConfigurationSection section = getOrCreateSection(parent, sectionName);
            section.set("world", location.getWorld().getName());
            section.set("x", location.getX());
            section.set("y", location.getY());
            section.set("z", location.getZ());
            section.set("yaw", location.getYaw());
            section.set("pitch", location.getPitch());
            logger.debug("Saved location to section: " + sectionName);
        }
    }
    
    /**
     * Saves a list of locations to configuration.
     */
    public void saveLocationList(
            @NotNull ConfigurationSection parent,
            @NotNull String sectionName,
            @NotNull List<Location> locations) {
        // Clear existing section before saving new locations
        parent.set(sectionName, null);
        ConfigurationSection section = parent.createSection(sectionName);
        
        for (int i = 0; i < locations.size(); i++) {
            Location location = locations.get(i);
            if (location != null) {
                saveLocation(section, String.valueOf(i), location);
            }
        }
        
        logger.debug("Saved " + locations.size() + " locations to section: " + sectionName);
    }
    
    /**
     * Saves allowed blocks to configuration.
     */
    public void saveAllowedBlocks(
            @NotNull ConfigurationSection section,
            @NotNull List<Material> allowedBlocks) {
        List<String> allowedBlocksList = new ArrayList<>();
        for (Material material : allowedBlocks) {
            allowedBlocksList.add(material.name());
        }
        section.set("allowed-blocks", allowedBlocksList);
        logger.debug("Saved " + allowedBlocksList.size() + " allowed blocks");
    }
    
    /**
     * Loads a location from configuration.
     */
    public @Nullable Location loadLocation(@Nullable ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        
        String worldName = section.getString("world");
        if (worldName == null) {
            return null;
        }
        
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            logger.warn("Could not find world '" + worldName + "' when loading location");
            return null;
        }
        
        double x = section.getDouble("x");
        double y = section.getDouble("y");
        double z = section.getDouble("z");
        float yaw = (float) section.getDouble("yaw", 0.0);
        float pitch = (float) section.getDouble("pitch", 0.0);
        
        return new Location(world, x, y, z, yaw, pitch);
    }
    
    /**
     * Gets or creates a configuration section.
     */
    private @NotNull ConfigurationSection getOrCreateSection(
            @NotNull ConfigurationSection parent,
            @NotNull String name) {
        ConfigurationSection section = parent.getConfigurationSection(name);
        return section != null ? section : parent.createSection(name);
    }
} 