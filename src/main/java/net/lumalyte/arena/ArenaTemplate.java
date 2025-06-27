package net.lumalyte.arena;

import net.lumalyte.LumaSG;
import net.lumalyte.exception.LumaSGException;
import net.lumalyte.util.ValidationUtils;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Represents an arena template that can be used to create new arenas quickly.
 * 
 * <p>Arena templates allow server administrators to create reusable arena configurations
 * that can be applied to different worlds or locations. This is useful for creating
 * multiple arenas with the same layout but in different locations.</p>
 * 
 * <p>Templates store relative positions from a center point, making them world-agnostic
 * and easily adaptable to different locations.</p>
 * 
 * @author LumaSG Team
 * @since 1.0.0
 */
public class ArenaTemplate {
    
    /** The unique name of this template */
    private final @NotNull String name;
    
    /** Description of this template */
    private final @NotNull String description;
    
    /** The author/creator of this template */
    private final @NotNull String author;
    
    /** Version of this template */
    private final @NotNull String version;
    
    /** Relative spawn point positions from center (x, y, z) */
    private final @NotNull List<RelativePosition> spawnPoints;
    
    /** Relative chest locations from center (x, y, z) */
    private final @NotNull List<RelativePosition> chestLocations;
    
    /** Relative lobby spawn position from center */
    private @Nullable RelativePosition lobbySpawn;
    
    /** Relative spectator spawn position from center */
    private @Nullable RelativePosition spectatorSpawn;
    
    /** Arena radius */
    private int radius;
    
    /** Maximum players for this template */
    private int maxPlayers;
    
    /** Minimum players for this template */
    private int minPlayers;
    
    /** Allowed blocks for this template */
    private final @NotNull Set<Material> allowedBlocks;
    
    /** Template creation timestamp */
    private final long createdAt;
    
    /** Last modified timestamp */
    private long lastModified;
    
    /**
     * Creates a new arena template.
     * 
     * @param name The unique name of the template
     * @param description Description of the template
     * @param author The author/creator
     * @param version Template version
	 */
    public ArenaTemplate(@NotNull String name, @NotNull String description, @NotNull String author, @NotNull String version) {
        ValidationUtils.requireNonEmpty(name, "Template Name", "Template Creation");
        ValidationUtils.requireNonEmpty(description, "Template Description", "Template Creation");
        ValidationUtils.requireNonEmpty(author, "Template Author", "Template Creation");
        ValidationUtils.requireNonEmpty(version, "Template Version", "Template Creation");
        
        this.name = name;
        this.description = description;
        this.author = author;
        this.version = version;
        this.spawnPoints = new ArrayList<>();
        this.chestLocations = new ArrayList<>();
        this.allowedBlocks = new HashSet<>();
        this.radius = 100;
        this.maxPlayers = 24;
        this.minPlayers = 2;
        this.createdAt = System.currentTimeMillis();
        this.lastModified = this.createdAt;
    }
    
    /**
     * Creates an arena template from an existing arena.
     * 
     * @param arena The arena to create a template from
     * @param name The name for the template
     * @param description Description of the template
     * @param author The author/creator
     * @param version Template version
     * @return The created template
	 */
    public static @NotNull ArenaTemplate fromArena(@NotNull Arena arena, @NotNull String name, 
                                                  @NotNull String description, @NotNull String author, 
                                                  @NotNull String version) throws LumaSGException.ArenaException {
        ValidationUtils.requireNonNull(arena, "Arena", "Template Creation from Arena");
        
        Location center = arena.getCenter();
        if (center == null) {
            throw LumaSGException.arenaError("Arena '" + arena.getName() + "' has no center location set");
        }
        
        ArenaTemplate template = new ArenaTemplate(name, description, author, version);
        
        // Convert spawn points to relative positions
        for (Location spawnPoint : arena.getSpawnPoints()) {
            RelativePosition relPos = RelativePosition.fromLocation(spawnPoint, center);
            template.spawnPoints.add(relPos);
        }
        
        // Convert chest locations to relative positions
        for (Location chestLocation : arena.getChestLocations()) {
            RelativePosition relPos = RelativePosition.fromLocation(chestLocation, center);
            template.chestLocations.add(relPos);
        }
        
        // Convert lobby spawn
        if (arena.getLobbySpawn() != null) {
            template.lobbySpawn = RelativePosition.fromLocation(arena.getLobbySpawn(), center);
        }
        
        // Convert spectator spawn
        if (arena.getSpectatorSpawn() != null) {
            template.spectatorSpawn = RelativePosition.fromLocation(arena.getSpectatorSpawn(), center);
        }
        
        // Copy other properties
        template.radius = arena.getRadius();
        template.maxPlayers = arena.getMaxPlayers();
        template.minPlayers = arena.getMinPlayers();
        template.allowedBlocks.addAll(arena.getAllowedBlocks());
        
        return template;
    }
    
    /**
     * Creates an arena from this template at the specified center location.
     * 
     * @param plugin The plugin instance
     * @param arenaName The name for the new arena
     * @param center The center location for the new arena
     * @return The created arena
	 */
    public @NotNull Arena createArena(@NotNull LumaSG plugin, @NotNull String arenaName, @NotNull Location center) {
        ValidationUtils.requireNonNull(plugin, "Plugin", "Arena Creation from Template");
        ValidationUtils.requireNonEmpty(arenaName, "Arena Name", "Arena Creation from Template");
        ValidationUtils.requireNonNull(center, "Center Location", "Arena Creation from Template");
        
        Arena arena = new Arena(arenaName, plugin, maxPlayers, minPlayers);
        arena.setCenter(center);
        arena.setRadius(radius);
        
        // Convert relative positions back to absolute locations
        for (RelativePosition relPos : spawnPoints) {
            Location spawnPoint = relPos.toLocation(center);
            arena.addSpawnPoint(spawnPoint);
        }
        
        for (RelativePosition relPos : chestLocations) {
            Location chestLocation = relPos.toLocation(center);
            arena.addChestLocation(chestLocation);
        }
        
        // Set lobby spawn
        if (lobbySpawn != null) {
            arena.setLobbySpawn(lobbySpawn.toLocation(center));
        }
        
        // Set spectator spawn
        if (spectatorSpawn != null) {
            arena.setSpectatorSpawn(spectatorSpawn.toLocation(center));
        }
        
        // Add allowed blocks
        for (Material material : allowedBlocks) {
            arena.addAllowedBlock(material);
        }
        
        return arena;
    }
    
    /**
     * Saves this template to a file.
     * 
     * @param file The file to save to
     * @throws LumaSGException if saving fails
     */
    public void saveToFile(@NotNull File file) throws LumaSGException {
        ValidationUtils.requireNonNull(file, "File", "Template Saving");
        
        try {
            YamlConfiguration config = new YamlConfiguration();
            saveToConfig(config);
            config.save(file);
        } catch (IOException e) {
            throw LumaSGException.configError("Failed to save template to file: " + file.getAbsolutePath(), e);
        }
    }
    
    /**
     * Loads a template from a file.
     * 
     * @param file The file to load from
     * @return The loaded template
     * @throws LumaSGException if loading fails
     */
    public static @NotNull ArenaTemplate loadFromFile(@NotNull File file) throws LumaSGException {
        ValidationUtils.requireNonNull(file, "File", "Template Loading");
        
        if (!file.exists()) {
            throw LumaSGException.configError("Template file does not exist: " + file.getAbsolutePath());
        }
        
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        return loadFromConfig(config);
    }
    
    /**
     * Saves this template to a configuration section.
     * 
     * @param config The configuration to save to
     */
    public void saveToConfig(@NotNull ConfigurationSection config) {
        ValidationUtils.requireNonNull(config, "Configuration", "Template Config Saving");
        
        // Metadata
        config.set("name", name);
        config.set("description", description);
        config.set("author", author);
        config.set("version", version);
        config.set("created-at", createdAt);
        config.set("last-modified", System.currentTimeMillis());
        
        // Arena properties
        config.set("radius", radius);
        config.set("max-players", maxPlayers);
        config.set("min-players", minPlayers);
        
        // Spawn points
        ConfigurationSection spawnSection = config.createSection("spawn-points");
        for (int i = 0; i < spawnPoints.size(); i++) {
            RelativePosition pos = spawnPoints.get(i);
            ConfigurationSection posSection = spawnSection.createSection(String.valueOf(i));
            pos.saveToConfig(posSection);
        }
        
        // Chest locations
        ConfigurationSection chestSection = config.createSection("chest-locations");
        for (int i = 0; i < chestLocations.size(); i++) {
            RelativePosition pos = chestLocations.get(i);
            ConfigurationSection posSection = chestSection.createSection(String.valueOf(i));
            pos.saveToConfig(posSection);
        }
        
        // Lobby spawn
        if (lobbySpawn != null) {
            ConfigurationSection lobbySection = config.createSection("lobby-spawn");
            lobbySpawn.saveToConfig(lobbySection);
        }
        
        // Spectator spawn
        if (spectatorSpawn != null) {
            ConfigurationSection spectatorSection = config.createSection("spectator-spawn");
            spectatorSpawn.saveToConfig(spectatorSection);
        }
        
        // Allowed blocks
        List<String> blockNames = new ArrayList<>();
        for (Material material : allowedBlocks) {
            blockNames.add(material.name());
        }
        config.set("allowed-blocks", blockNames);
    }
    
    /**
     * Loads a template from a configuration section.
     * 
     * @param config The configuration to load from
     * @return The loaded template
     * @throws LumaSGException if loading fails
     */
    public static @NotNull ArenaTemplate loadFromConfig(@NotNull ConfigurationSection config) throws LumaSGException {
        ValidationUtils.requireNonNull(config, "Configuration", "Template Config Loading");
        
        String name = config.getString("name");
        String description = config.getString("description");
        String author = config.getString("author");
        String version = config.getString("version");
        
        if (name == null || description == null || author == null || version == null) {
            throw LumaSGException.configError("Template configuration is missing required fields");
        }
        
        ArenaTemplate template = new ArenaTemplate(name, description, author, version);
        
        // Load arena properties
        template.radius = config.getInt("radius", 100);
        template.maxPlayers = config.getInt("max-players", 24);
        template.minPlayers = config.getInt("min-players", 2);
        template.lastModified = config.getLong("last-modified", System.currentTimeMillis());
        
        // Load spawn points
        ConfigurationSection spawnSection = config.getConfigurationSection("spawn-points");
        if (spawnSection != null) {
            for (String key : spawnSection.getKeys(false)) {
                ConfigurationSection posSection = spawnSection.getConfigurationSection(key);
                if (posSection != null) {
                    RelativePosition pos = RelativePosition.loadFromConfig(posSection);
                    template.spawnPoints.add(pos);
                }
            }
        }
        
        // Load chest locations
        ConfigurationSection chestSection = config.getConfigurationSection("chest-locations");
        if (chestSection != null) {
            for (String key : chestSection.getKeys(false)) {
                ConfigurationSection posSection = chestSection.getConfigurationSection(key);
                if (posSection != null) {
                    RelativePosition pos = RelativePosition.loadFromConfig(posSection);
                    template.chestLocations.add(pos);
                }
            }
        }
        
        // Load lobby spawn
        ConfigurationSection lobbySection = config.getConfigurationSection("lobby-spawn");
        if (lobbySection != null) {
            template.lobbySpawn = RelativePosition.loadFromConfig(lobbySection);
        }
        
        // Load spectator spawn
        ConfigurationSection spectatorSection = config.getConfigurationSection("spectator-spawn");
        if (spectatorSection != null) {
            template.spectatorSpawn = RelativePosition.loadFromConfig(spectatorSection);
        }
        
        // Load allowed blocks
        List<String> blockNames = config.getStringList("allowed-blocks");
        for (String blockName : blockNames) {
            try {
                Material material = Material.valueOf(blockName.toUpperCase());
                template.allowedBlocks.add(material);
            } catch (IllegalArgumentException e) {
                // Skip invalid materials
            }
        }
        
        return template;
    }
    
    // Getters and setters
    
    public @NotNull String getName() {
        return name;
    }
    
    public @NotNull String getDescription() {
        return description;
    }
    
    public @NotNull String getAuthor() {
        return author;
    }
    
    public @NotNull String getVersion() {
        return version;
    }
    
    public int getRadius() {
        return radius;
    }
    
    public void setRadius(int radius) {
        ValidationUtils.requirePositive(radius, "Radius", "Template Radius Setting");
        this.radius = radius;
        this.lastModified = System.currentTimeMillis();
    }
    
    public int getMaxPlayers() {
        return maxPlayers;
    }
    
    public void setMaxPlayers(int maxPlayers) {
        ValidationUtils.requirePositive(maxPlayers, "Max Players", "Template Max Players Setting");
        this.maxPlayers = maxPlayers;
        this.lastModified = System.currentTimeMillis();
    }
    
    public int getMinPlayers() {
        return minPlayers;
    }
    
    public void setMinPlayers(int minPlayers) {
        ValidationUtils.requirePositive(minPlayers, "Min Players", "Template Min Players Setting");
        this.minPlayers = minPlayers;
        this.lastModified = System.currentTimeMillis();
    }
    
    public @NotNull List<RelativePosition> getSpawnPoints() {
        return Collections.unmodifiableList(spawnPoints);
    }
    
    public @NotNull List<RelativePosition> getChestLocations() {
        return Collections.unmodifiableList(chestLocations);
    }
    
    public @Nullable RelativePosition getLobbySpawn() {
        return lobbySpawn;
    }
    
    public void setLobbySpawn(@Nullable RelativePosition lobbySpawn) {
        this.lobbySpawn = lobbySpawn;
        this.lastModified = System.currentTimeMillis();
    }
    
    public @Nullable RelativePosition getSpectatorSpawn() {
        return spectatorSpawn;
    }
    
    public void setSpectatorSpawn(@Nullable RelativePosition spectatorSpawn) {
        this.spectatorSpawn = spectatorSpawn;
        this.lastModified = System.currentTimeMillis();
    }
    
    public @NotNull Set<Material> getAllowedBlocks() {
        return Collections.unmodifiableSet(allowedBlocks);
    }
    
    public void addAllowedBlock(@NotNull Material material) {
        ValidationUtils.requireNonNull(material, "Material", "Template Allowed Block Addition");
        allowedBlocks.add(material);
        this.lastModified = System.currentTimeMillis();
    }
    
    public boolean removeAllowedBlock(@NotNull Material material) {
        ValidationUtils.requireNonNull(material, "Material", "Template Allowed Block Removal");
        boolean removed = allowedBlocks.remove(material);
        if (removed) {
            this.lastModified = System.currentTimeMillis();
        }
        return removed;
    }
    
    public long getCreatedAt() {
        return createdAt;
    }
    
    public long getLastModified() {
        return lastModified;
    }
    
    /**
     * Represents a relative position from a center point.
     */
    public static class RelativePosition {
        private final double x;
        private final double y;
        private final double z;
        private final float yaw;
        private final float pitch;
        
        public RelativePosition(double x, double y, double z, float yaw, float pitch) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.pitch = pitch;
        }
        
        public RelativePosition(double x, double y, double z) {
            this(x, y, z, 0f, 0f);
        }
        
        /**
         * Creates a relative position from a location relative to a center.
         * 
         * @param location The absolute location
         * @param center The center point
         * @return The relative position
         */
        public static @NotNull RelativePosition fromLocation(@NotNull Location location, @NotNull Location center) {
            ValidationUtils.requireNonNull(location, "Location", "Relative Position Creation");
            ValidationUtils.requireNonNull(center, "Center", "Relative Position Creation");
            
            double relX = location.getX() - center.getX();
            double relY = location.getY() - center.getY();
            double relZ = location.getZ() - center.getZ();
            
            return new RelativePosition(relX, relY, relZ, location.getYaw(), location.getPitch());
        }
        
        /**
         * Converts this relative position to an absolute location.
         * 
         * @param center The center point
         * @return The absolute location
         */
        public @NotNull Location toLocation(@NotNull Location center) {
            ValidationUtils.requireNonNull(center, "Center", "Absolute Location Creation");
            
            double absX = center.getX() + x;
            double absY = center.getY() + y;
            double absZ = center.getZ() + z;
            
            return new Location(center.getWorld(), absX, absY, absZ, yaw, pitch);
        }
        
        /**
         * Saves this relative position to a configuration section.
         * 
         * @param config The configuration section
         */
        public void saveToConfig(@NotNull ConfigurationSection config) {
            config.set("x", x);
            config.set("y", y);
            config.set("z", z);
            config.set("yaw", yaw);
            config.set("pitch", pitch);
        }
        
        /**
         * Loads a relative position from a configuration section.
         * 
         * @param config The configuration section
         * @return The relative position
         */
        public static @NotNull RelativePosition loadFromConfig(@NotNull ConfigurationSection config) {
            double x = config.getDouble("x");
            double y = config.getDouble("y");
            double z = config.getDouble("z");
            float yaw = (float) config.getDouble("yaw", 0.0);
            float pitch = (float) config.getDouble("pitch", 0.0);
            
            return new RelativePosition(x, y, z, yaw, pitch);
        }
        
        // Getters
        public double getX() { return x; }
        public double getY() { return y; }
        public double getZ() { return z; }
        public float getYaw() { return yaw; }
        public float getPitch() { return pitch; }
    }
} 