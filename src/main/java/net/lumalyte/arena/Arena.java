package net.lumalyte.arena;

import net.lumalyte.LumaSG;
import net.lumalyte.exception.LumaSGException;
import net.lumalyte.util.DebugLogger;
import net.lumalyte.util.ValidationUtils;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import org.bukkit.Particle;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.entity.Player;
import org.bukkit.Material;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Represents a Survival Games arena with spawn points and configuration.
 * 
 * <p>This class manages the physical layout and properties of a single arena,
 * including spawn point locations for players and various arena-specific settings.
 * Each arena can have multiple spawn points to accommodate different player counts.</p>
 * 
 * <p>The arena is serializable to/from configuration files, allowing for
 * persistent storage and easy configuration management.</p>
 * 
 * @author LumaLyte
 * @version 1.0
 * @since 1.0
 */
public class Arena {
    
    /** The unique identifier for this arena */
    private final @NotNull String name;
    private final @NotNull UUID id;
    private final @NotNull List<Location> spawnPoints;
    private final @NotNull List<Location> chestLocations;
    private final @NotNull Set<Material> allowedBlocks;
    private volatile @Nullable Location center;
    private volatile @Nullable Location spectatorSpawn;
    private volatile @Nullable Location lobbySpawn;
    private volatile @Nullable World world;
    private volatile int radius;
    private final @NotNull LumaSG plugin;
    private final @NotNull DebugLogger.ContextualLogger logger;
    private final int maxPlayers;
    private final int minPlayers;
    private final Map<Location, BukkitTask> beamTasks = new ConcurrentHashMap<>();
    private volatile File configFile;
    
    /**
     * Constructs a new Arena with the specified properties.
     * 
     * @param name The unique name/identifier for this arena
     * @param plugin The plugin instance
     * @param spawnPoints Array of spawn point locations for players
     * @param maxPlayers Maximum number of players this arena can support
     * @param minPlayers Minimum number of players required to start a game
     *
	 */
    public Arena(@NotNull String name, @NotNull LumaSG plugin, @NotNull Location[] spawnPoints, int maxPlayers, int minPlayers) {
        // Validate input parameters using ValidationUtils
        ValidationUtils.requireNonEmpty(name, "Arena Name", "Arena Construction");
        ValidationUtils.requireNonNull(plugin, "Plugin Instance", "Arena Construction");
        ValidationUtils.requireNonEmpty(spawnPoints, "Spawn Points Array", "Arena Construction");
        ValidationUtils.requirePositive(maxPlayers, "Max Players", "Arena Construction");
        ValidationUtils.requirePositive(minPlayers, "Min Players", "Arena Construction");
        ValidationUtils.requireTrue(minPlayers <= maxPlayers, 
            "Min players (" + minPlayers + ") cannot exceed max players (" + maxPlayers + ")", 
            "Arena Construction");
        
        // Validate that all spawn points are not null
        for (int i = 0; i < spawnPoints.length; i++) {
            ValidationUtils.requireNonNull(spawnPoints[i], "Spawn Point " + i, "Arena Construction");
        }
        
        this.name = name;
        this.plugin = plugin;
        this.logger = plugin.getDebugLogger().forContext("Arena-" + name);
        this.id = UUID.randomUUID();
        this.spawnPoints = new CopyOnWriteArrayList<>();
        this.chestLocations = new CopyOnWriteArrayList<>();
        this.allowedBlocks = new HashSet<>();
        this.radius = ValidationUtils.nullSafeInt(plugin.getConfig().getInt("arena.default-radius"), 100);
        this.maxPlayers = maxPlayers;
        this.minPlayers = minPlayers;
        
        // Add spawn points with null safety
        for (Location location : spawnPoints) {
			this.spawnPoints.add(location.clone());
		}
        
        // Ensure we have at least one spawn point
        ValidationUtils.requireNonEmpty(this.spawnPoints, "Spawn Points List", "Arena Construction");
    }

    /**
     * Constructs a new Arena with default settings.
     * 
     * @param name The unique name/identifier for this arena
     * @param plugin The plugin instance
	 */
    public Arena(@NotNull String name, @NotNull LumaSG plugin) {
        ValidationUtils.requireNonEmpty(name, "Arena Name", "Arena Construction");
        ValidationUtils.requireNonNull(plugin, "Plugin Instance", "Arena Construction");
        
        this.name = name;
        this.plugin = plugin;
        this.logger = plugin.getDebugLogger().forContext("Arena-" + name);
        this.id = UUID.randomUUID();
        this.spawnPoints = new CopyOnWriteArrayList<>();
        this.chestLocations = new CopyOnWriteArrayList<>();
        this.allowedBlocks = new HashSet<>();
        this.radius = ValidationUtils.nullSafeInt(plugin.getConfig().getInt("arena.default-radius"), 100);
        this.maxPlayers = ValidationUtils.nullSafeInt(plugin.getConfig().getInt("arena.default-max-players"), 24);
        this.minPlayers = ValidationUtils.nullSafeInt(plugin.getConfig().getInt("arena.default-min-players"), 2);
    }

    /**
     * Constructs a new Arena with specified player limits.
     * 
     * @param name The unique name/identifier for this arena
     * @param plugin The plugin instance
     * @param maxPlayers Maximum number of players
     * @param minPlayers Minimum number of players
	 */
    public Arena(@NotNull String name, @NotNull LumaSG plugin, int maxPlayers, int minPlayers) {
        ValidationUtils.requireNonEmpty(name, "Arena Name", "Arena Construction");
        ValidationUtils.requireNonNull(plugin, "Plugin Instance", "Arena Construction");
        ValidationUtils.requirePositive(maxPlayers, "Max Players", "Arena Construction");
        ValidationUtils.requirePositive(minPlayers, "Min Players", "Arena Construction");
        ValidationUtils.requireTrue(minPlayers <= maxPlayers, 
            "Min players (" + minPlayers + ") cannot exceed max players (" + maxPlayers + ")", 
            "Arena Construction");
        
        this.name = name;
        this.plugin = plugin;
        this.logger = plugin.getDebugLogger().forContext("Arena-" + name);
        this.id = UUID.randomUUID();
        this.spawnPoints = new CopyOnWriteArrayList<>();
        this.chestLocations = new CopyOnWriteArrayList<>();
        this.allowedBlocks = new HashSet<>();
        this.radius = ValidationUtils.nullSafeInt(plugin.getConfig().getInt("arena.default-radius"), 100);
        this.maxPlayers = maxPlayers;
        this.minPlayers = minPlayers;
    }

    /**
     * Creates a new arena with a center location and radius.
     *
     * @param name The name of the arena
     * @param plugin The plugin instance
     * @param center The center location
     * @param radius The radius from the center
	 */
    public Arena(@NotNull String name, @NotNull LumaSG plugin, @NotNull Location center, int radius) throws LumaSGException.ConfigurationException {
        this(name, plugin);
        ValidationUtils.requireNonNull(center, "Center Location", "Arena Construction");
        ValidationUtils.requirePositive(radius, "Radius", "Arena Construction");
        
        this.center = center;
        this.radius = radius;
    }
    
    /**
     * Loads an arena from a configuration section.
     *
     * @param plugin The plugin instance
     * @param section The configuration section
     * @param arenaName The name of the arena
     * @return The loaded arena, or null if invalid
     */
    public static @Nullable Arena fromConfig(@NotNull LumaSG plugin, @NotNull ConfigurationSection section, @NotNull String arenaName) {
        try {
            ValidationUtils.requireNonNull(plugin, "Plugin Instance", "Arena Loading");
            ValidationUtils.requireNonNull(section, "Configuration Section", "Arena Loading");
            ValidationUtils.requireNonEmpty(arenaName, "Arena Name", "Arena Loading");
            
            DebugLogger.ContextualLogger logger = plugin.getDebugLogger().forContext("Arena-" + arenaName);
            logger.debug("Loading arena: " + arenaName);
            
            // Create basic arena with configured values
            Arena arena = createBasicArena(plugin, section, arenaName);
            
            // Load all locations and configurations
            loadArenaLocations(arena, section, logger, arenaName);
            loadAllowedBlocks(arena, section, logger, arenaName);
            determineArenaWorld(arena, logger, arenaName);
            
            logger.info("Successfully loaded arena " + arenaName + " with " + 
                arena.spawnPoints.size() + " spawn points and " + 
                arena.chestLocations.size() + " chest locations");
            
            // Log if no chest locations are found (but don't auto-scan to prevent save conflicts)
            if (arena.chestLocations.isEmpty() && arena.center != null && arena.radius > 0) {
                logger.warn("No chest locations found for arena " + arenaName + 
                    ". Use '/sg scanchests " + arenaName + "' to scan for chests.");
            }
            
            return arena;
        } catch (Exception e) {
            plugin.getDebugLogger().severe("Unexpected error loading arena: " + arenaName, e);
            return null;
        }
    }
    
    /**
     * Creates a basic arena with configured values.
     */
    private static @NotNull Arena createBasicArena(@NotNull LumaSG plugin, @NotNull ConfigurationSection section, @NotNull String arenaName) {
        // Get configuration values
        int maxPlayers = section.getInt("max-players", plugin.getConfig().getInt("arena.default-max-players", 24));
        int minPlayers = section.getInt("min-players", plugin.getConfig().getInt("arena.default-min-players", 2));
        int radius = section.getInt("radius", plugin.getConfig().getInt("arena.default-radius", 100));
        
        // Create arena with the configured values
        Arena arena = new Arena(arenaName, plugin, maxPlayers, minPlayers);
        arena.radius = radius;
        
        return arena;
    }
    
    /**
     * Loads all location data for the arena.
     */
    private static void loadArenaLocations(@NotNull Arena arena, @NotNull ConfigurationSection section, @NotNull DebugLogger.ContextualLogger logger, @NotNull String arenaName) {
        loadCenterLocation(arena, section, logger, arenaName);
        loadSpawnPoints(arena, section, logger, arenaName);
        loadChestLocations(arena, section, logger, arenaName);
        loadLobbySpawn(arena, section, logger, arenaName);
        loadSpectatorSpawn(arena, section, logger, arenaName);
    }
    
    /**
     * Loads the center location for the arena.
     */
    private static void loadCenterLocation(@NotNull Arena arena, @NotNull ConfigurationSection section, @NotNull DebugLogger.ContextualLogger logger, @NotNull String arenaName) {
        ConfigurationSection centerSection = section.getConfigurationSection("center");
        if (centerSection != null) {
            arena.center = loadLocation(centerSection);
            logger.debug("Loaded center location for arena: " + arenaName);
        }
    }
    
    /**
     * Loads spawn points for the arena.
     */
    private static void loadSpawnPoints(@NotNull Arena arena, @NotNull ConfigurationSection section, @NotNull DebugLogger.ContextualLogger logger, @NotNull String arenaName) {
        ConfigurationSection spawnSection = section.getConfigurationSection("spawn-points");
        if (spawnSection != null && !spawnSection.getKeys(false).isEmpty()) {
            for (String key : spawnSection.getKeys(false)) {
                Location location = loadLocation(spawnSection.getConfigurationSection(key));
                if (location != null) {
                    arena.spawnPoints.add(location);
                    logger.debug("Loaded spawn point " + key + " for arena: " + arenaName);
                }
            }
        } else {
            logger.debug("No spawn points found for arena: " + arenaName);
        }
    }
    
    /**
     * Loads chest locations for the arena.
     */
    private static void loadChestLocations(@NotNull Arena arena, @NotNull ConfigurationSection section, @NotNull DebugLogger.ContextualLogger logger, @NotNull String arenaName) {
        ConfigurationSection chestSection = section.getConfigurationSection("chest-locations");
        if (chestSection != null && !chestSection.getKeys(false).isEmpty()) {
            for (String key : chestSection.getKeys(false)) {
                Location location = loadLocation(chestSection.getConfigurationSection(key));
                if (location != null) {
                    arena.chestLocations.add(location);
                    logger.debug("Loaded chest location " + key + " for arena: " + arenaName);
                }
            }
        } else {
            logger.debug("No chest locations found for arena: " + arenaName);
        }
    }
    
    /**
     * Loads lobby spawn location for the arena.
     */
    private static void loadLobbySpawn(@NotNull Arena arena, @NotNull ConfigurationSection section, @NotNull DebugLogger.ContextualLogger logger, @NotNull String arenaName) {
        ConfigurationSection lobbySection = section.getConfigurationSection("lobby-spawn");
        if (lobbySection != null) {
            arena.lobbySpawn = loadLocation(lobbySection);
            logger.debug("Loaded lobby spawn for arena: " + arenaName);
        }
    }
    
    /**
     * Loads spectator spawn location for the arena.
     */
    private static void loadSpectatorSpawn(@NotNull Arena arena, @NotNull ConfigurationSection section, @NotNull DebugLogger.ContextualLogger logger, @NotNull String arenaName) {
        ConfigurationSection spectatorSection = section.getConfigurationSection("spectator-spawn");
        if (spectatorSection != null) {
            arena.spectatorSpawn = loadLocation(spectatorSection);
            logger.debug("Loaded spectator spawn for arena: " + arenaName);
        }
    }
    
    /**
     * Loads allowed blocks for the arena.
     */
    private static void loadAllowedBlocks(@NotNull Arena arena, @NotNull ConfigurationSection section, @NotNull DebugLogger.ContextualLogger logger, @NotNull String arenaName) {
        List<String> allowedBlocksList = section.getStringList("allowed-blocks");
        if (!allowedBlocksList.isEmpty()) {
            for (String materialName : allowedBlocksList) {
                try {
                    Material material = Material.valueOf(materialName.toUpperCase());
                    arena.addAllowedBlock(material);
                    logger.debug("Loaded allowed block " + materialName + " for arena: " + arenaName);
                } catch (IllegalArgumentException e) {
                    logger.warn("Invalid material name in arena config: " + materialName);
                }
            }
        } else {
            addDefaultAllowedBlocks(arena, logger, arenaName);
        }
    }
    
    /**
     * Adds default allowed blocks to the arena.
     */
    private static void addDefaultAllowedBlocks(@NotNull Arena arena, @NotNull DebugLogger.ContextualLogger logger, @NotNull String arenaName) {
        arena.addAllowedBlock(Material.COBWEB);
        arena.addAllowedBlock(Material.CRAFTING_TABLE);
        arena.addAllowedBlock(Material.ANVIL);
        arena.addAllowedBlock(Material.LADDER);
        arena.addAllowedBlock(Material.TORCH);
        arena.addAllowedBlock(Material.FURNACE);
        logger.debug("Added default allowed blocks for arena: " + arenaName);
    }
    
    /**
     * Determines and sets the world for the arena based on loaded locations.
     */
    private static void determineArenaWorld(@NotNull Arena arena, @NotNull DebugLogger.ContextualLogger logger, @NotNull String arenaName) {
        World arenaWorld = null;
        
        if (arena.center != null) {
            arenaWorld = arena.center.getWorld();
        } else if (!arena.spawnPoints.isEmpty()) {
            arenaWorld = arena.spawnPoints.get(0).getWorld();
        } else if (!arena.chestLocations.isEmpty()) {
            arenaWorld = arena.chestLocations.get(0).getWorld();
        } else if (arena.lobbySpawn != null) {
            arenaWorld = arena.lobbySpawn.getWorld();
        } else if (arena.spectatorSpawn != null) {
            arenaWorld = arena.spectatorSpawn.getWorld();
        }
        
        if (arenaWorld != null) {
            arena.world = arenaWorld;
            logger.debug("Set arena world to: " + arenaWorld.getName());
        } else {
            logger.warn("Could not determine world for arena: " + arenaName + " (no valid locations found)");
        }
    }
    
    /**
     * Saves the arena to a configuration section.
     *
     * @param section The configuration section
	 */
    public void saveToConfig(@NotNull ConfigurationSection section) {
        ValidationUtils.requireNonNull(section, "Configuration Section", "Arena Saving");
        
        logger.debug("Saving arena: " + name);
        
        section.set("max-players", maxPlayers);
        section.set("min-players", minPlayers);
        section.set("radius", radius);
        
        // Save center location
        if (center != null) {
            ConfigurationSection centerSection = section.getConfigurationSection("center");
            if (centerSection == null) {
                centerSection = section.createSection("center");
            }
            saveLocation(centerSection, center);
            logger.debug("Saved center location");
        }
        
        // Save spawn points with null safety
        ConfigurationSection spawnSection = section.getConfigurationSection("spawn-points");
        if (spawnSection == null) {
            spawnSection = section.createSection("spawn-points");
        }
        for (int i = 0; i < spawnPoints.size(); i++) {
            Location location = spawnPoints.get(i);
            if (location != null) {
                ConfigurationSection pointSection = spawnSection.createSection(String.valueOf(i));
                saveLocation(pointSection, location);
                logger.debug("Saved spawn point " + i);
            }
        }
        
        // Clear existing chest locations section before saving new ones
        section.set("chest-locations", null);
        ConfigurationSection chestSection = section.createSection("chest-locations");
        
        // Save chest locations with null safety
        for (int i = 0; i < chestLocations.size(); i++) {
            Location location = chestLocations.get(i);
            if (location != null) {
                ConfigurationSection locationSection = chestSection.createSection(String.valueOf(i));
                saveLocation(locationSection, location);
                logger.debug("Saved chest location " + i);
            }
        }
        
        // Save lobby spawn with null safety
        if (lobbySpawn != null) {
            ConfigurationSection lobbySection = section.getConfigurationSection("lobby-spawn");
            if (lobbySection == null) {
                lobbySection = section.createSection("lobby-spawn");
            }
            saveLocation(lobbySection, lobbySpawn);
            logger.debug("Saved lobby spawn");
        }
        
        // Save spectator spawn with null safety
        if (spectatorSpawn != null) {
            ConfigurationSection spectatorSection = section.getConfigurationSection("spectator-spawn");
            if (spectatorSection == null) {
                spectatorSection = section.createSection("spectator-spawn");
            }
            saveLocation(spectatorSection, spectatorSpawn);
            logger.debug("Saved spectator spawn");
        }
        
        // Save allowed blocks
        List<String> allowedBlocksList = new ArrayList<>();
        for (Material material : allowedBlocks) {
            allowedBlocksList.add(material.name());
        }
        section.set("allowed-blocks", allowedBlocksList);
        
        logger.info("Successfully saved arena " + name + " with " + 
            spawnPoints.size() + " spawn points and " + 
            chestLocations.size() + " chest locations");
    }
    
    /**
     * Loads a location from a configuration section.
     *
     * @param section The configuration section
     * @return The loaded location, or null if invalid
     */
    private static @Nullable Location loadLocation(@Nullable ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        
        String worldName = section.getString("world");
        if (worldName == null) {
            return null;
        }
        
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            // Log the issue for debugging - this could happen if worlds aren't loaded yet during startup
            System.err.println("[LumaSG] Warning: Could not find world '" + worldName + "' when loading location. " +
                "This may be due to world loading timing during server startup.");
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
     * Saves a location to a configuration section.
     *
     * @param section The configuration section
     * @param location The location to save
	 */
    private static void saveLocation(@NotNull ConfigurationSection section, @NotNull Location location) {
        section.set("world", location.getWorld().getName());
        section.set("x", location.getX());
        section.set("y", location.getY());
        section.set("z", location.getZ());
        section.set("yaw", location.getYaw());
        section.set("pitch", location.getPitch());
    }
    
    /**
     * Gets the name of the arena.
     *
     * @return The name
     */
    public @NotNull String getName() {
        return name;
    }
    
    /**
     * Gets the unique ID of the arena.
     *
     * @return The unique ID
     */
    public @NotNull UUID getId() {
        return id;
    }
    
    /**
     * Gets the world of this arena.
     *
     * @return The world, or null if not set
     */
    public @Nullable World getWorld() {
        return world;
    }
    
    /**
     * Sets the world for this arena.
     * 
     * @param world The world
     */
    public synchronized void setWorld(@NotNull World world) {
        ValidationUtils.requireNonNull(world, "World", "Arena.setWorld");
        this.world = world;
    }
    
    /**
     * Gets the center location of the arena.
     *
     * @return The center location, or null if not set
     */
    public @Nullable Location getCenter() {
        return center != null ? center.clone() : null;
    }
    
    /**
     * Sets the center location for this arena.
     * 
     * @param center The center location
     */
    public synchronized void setCenter(@Nullable Location center) {
        this.center = center != null ? center.clone() : null;
    }
    
    /**
     * Gets the spawn points of the arena.
     *
     * @return An unmodifiable list of spawn points
     */
    public @NotNull List<Location> getSpawnPoints() {
        return Collections.unmodifiableList(spawnPoints);
    }
    
    /**
     * Adds a spawn point to this arena.
     * 
     * @param location The spawn point location
     */
    public synchronized void addSpawnPoint(@NotNull Location location) {
        ValidationUtils.requireNonNull(location, "Location", "Arena.addSpawnPoint");
        
        // Clone the location to prevent external modification
        Location clonedLocation = location.clone();
        
        // Check if this location already exists
        for (Location existing : spawnPoints) {
            if (existing.equals(clonedLocation)) {
                return; // Already exists, don't add duplicate
            }
        }
        
        spawnPoints.add(clonedLocation);
        
        // Save the arena if auto-save is enabled
        if (plugin.getConfig().getBoolean("arena.auto-save", true)) {
            plugin.getArenaManager().saveArenas();
        }
    }
    
    /**
     * Removes a spawn point from this arena.
     *
     * @param index The index of the spawn point to remove
     */
    public synchronized void removeSpawnPoint(int index) {
        if (index < 0 || index >= spawnPoints.size()) {
            return;
        }
        
        spawnPoints.remove(index);
        
        // Save the arena if auto-save is enabled
        if (plugin.getConfig().getBoolean("arena.auto-save", true)) {
            plugin.getArenaManager().saveArenas();
        }

    }
    
    /**
     * Gets the chest locations of the arena.
     *
     * @return An unmodifiable list of chest locations
     */
    public @NotNull List<Location> getChestLocations() {
        return Collections.unmodifiableList(chestLocations);
    }
    
    /**
     * Adds a chest location to this arena.
     * 
     * @param location The chest location
     */
    public synchronized void addChestLocation(@NotNull Location location) {
        ValidationUtils.requireNonNull(location, "Location", "Arena.addChestLocation");
        chestLocations.add(location.clone());
        
        // Save the arena if auto-save is enabled
        if (plugin.getConfig().getBoolean("arena.auto-save", true)) {
            plugin.getArenaManager().saveArenas();
        }
    }
    
    /**
     * Removes a chest location from this arena.
     * 
     * @param index The index of the chest location to remove
     * @return true if removed successfully, false otherwise
     */
    public synchronized boolean removeChestLocation(int index) {
        if (index < 0 || index >= chestLocations.size()) {
            return false;
        }
        
        chestLocations.remove(index);
        
        // Save the arena if auto-save is enabled
        if (plugin.getConfig().getBoolean("arena.auto-save", true)) {
            plugin.getArenaManager().saveArenas();
        }
        
        return true;
    }
    
    /**
     * Gets the lobby spawn location.
     *
     * @return The lobby spawn location, or null if not set
     */
    public @Nullable Location getLobbySpawn() {
        return lobbySpawn != null ? lobbySpawn.clone() : null;
    }
    
    /**
     * Sets the lobby spawn location for this arena.
     * 
     * @param location The lobby spawn location
     */
    public synchronized void setLobbySpawn(@Nullable Location location) {
        this.lobbySpawn = location != null ? location.clone() : null;
    }
    
    /**
     * Gets the spectator spawn location.
     *
     * @return The spectator spawn location, or null if not set
     */
    public @Nullable Location getSpectatorSpawn() {
        return spectatorSpawn != null ? spectatorSpawn.clone() : null;
    }
    
    /**
     * Sets the spectator spawn location for this arena.
     * 
     * @param location The spectator spawn location
     */
    public synchronized void setSpectatorSpawn(@Nullable Location location) {
        this.spectatorSpawn = location != null ? location.clone() : null;
    }
    
    /**
     * Gets the maximum number of players.
     * 
     * @return The maximum number of players
     */
    public int getMaxPlayers() {
        return maxPlayers;
    }

    /**
     * Gets the minimum number of players required to start a game in this arena.
     * 
     * @return The minimum player count
     */
    public int getMinPlayers() {
        return minPlayers;
    }
    
    /**
     * Gets the radius of this arena.
     * 
     * @return The arena radius
     */
    public int getRadius() {
        return radius;
    }
    
    /**
     * Sets the radius of this arena.
     * 
     * @param radius The new radius
     */
    public synchronized void setRadius(int radius) {
        this.radius = radius;
    }

    /**
     * Checks if this arena can accommodate the specified number of players.
     * 
     * @param playerCount The number of players to check
     * @return true if the arena can support this many players, false otherwise
	 */
    public boolean canSupportPlayers(int playerCount) {
        ValidationUtils.requireNonNegative(playerCount, "Player Count", "Checking Player Support");
        return playerCount >= minPlayers && playerCount <= maxPlayers;
    }



    /**
     * Returns a string representation of this arena.
     * 
     * @return A string containing the arena name and player limits
     */
    @Override
    public @NotNull String toString() {
        return String.format("Arena{name='%s', maxPlayers=%d, minPlayers=%d, spawnPoints=%d}",
                name, maxPlayers, minPlayers, spawnPoints.size());
    }

    /**
     * Checks if this arena is equal to another object.
     * 
     * <p>Arenas are considered equal if they have the same name.</p>
     * 
     * @param obj The object to compare with
     * @return true if the objects are equal, false otherwise
     */
    @Override
    public boolean equals(@Nullable Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        Arena arena = (Arena) obj;
        return name.equals(arena.name);
    }

    /**
     * Returns a hash code for this arena.
     * 
     * @return The hash code based on the arena name
     */
    @Override
    public int hashCode() {
        return name.hashCode();
    }

    /**
     * Scans for chests in the arena's world within the specified radius.
     * 
     * @return The number of chests found
     */
    public synchronized int scanForChests() {
        if (center == null || world == null) {
            logger.warn("Cannot scan for chests: center or world is null in arena " + name);
            return 0;
        }

        // Clear existing chest locations
        chestLocations.clear();

        // Scan in a cube around the center (more efficient than checking every block in a sphere)
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    Location loc = center.clone().add(x, y, z);
                    
                    // Only check blocks within the spherical radius and if they are chests
                    if (loc.distance(center) <= radius && loc.getBlock().getType() == org.bukkit.Material.CHEST) {
                        chestLocations.add(loc.clone());
                    }
                }
            }
        }

        int chestCount = chestLocations.size();
        logger.info("Found " + chestCount + " chests in arena " + name);
        
        // Schedule a debounced save to persist chest locations
        plugin.getArenaManager().saveArenas().thenRun(() -> 
            logger.debug("Scheduled save for chest locations in arena " + name));
            
        return chestCount;
    }

    /**
     * Cleans up all beam effects.
     * Should be called when the arena is being removed or the plugin is disabling.
     */
    public void cleanup() {
        // Stop all beam tasks
        for (BukkitTask task : beamTasks.values()) {
            if (task != null && !task.isCancelled()) {
                task.cancel();
            }
        }
        beamTasks.clear();
        
        logger.info("Cleaned up arena: " + name);
    }
    
    /**
     * Shows visual indicators at all spawn points.
     */
    public synchronized void showSpawnPoints() {
        // Cancel existing beam tasks
        hideSpawnPoints();
        
        logger.debug("Showing spawn points for arena " + name + " (" + spawnPoints.size() + " points)");
        
        for (Location location : spawnPoints) {
            if (location != null && location.getWorld() != null) {
                createBeaconAt(location);
            } else {
                logger.warn("Invalid spawn point location in arena " + name + ": " + location);
            }
        }
    }
    
    /**
     * Hides all visual indicators at spawn points.
     */
    public synchronized void hideSpawnPoints() {
        logger.debug("Hiding spawn points for arena " + name + " (clearing " + beamTasks.size() + " tasks)");
        for (BukkitTask task : beamTasks.values()) {
            if (task != null && !task.isCancelled()) {
                task.cancel();
            }
        }
        beamTasks.clear();
    }
    
    /**
     * Creates a beacon effect at the specified location.
     * This method spawns particles to mark spawn points.
     */
    private void createBeaconAt(@NotNull Location location) {
        if (location.getWorld() == null) {
            logger.warn("Cannot create beacon: world is null for location " + location);
            return;
        }
        
        // Create a repeating task to spawn particles
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (location.getWorld() != null) {
                // Use the spawn point location directly since it's already centered
                // The spawn point is created at clickedBlock.getLocation().add(0.5, 1, 0.5)
                // so we don't need to add any more offset
                Location particleLoc = location.clone();
                
                // Only show particles to admins
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (player.hasPermission("lumasg.admin")) {
                        // Create a vertical beam of particles
                        for (double y = 0; y <= 3; y += 0.25) {
                            player.spawnParticle(
                                Particle.END_ROD,
                                particleLoc.clone().add(0, y, 0),
                                2, // Count (increased for better visibility)
                                0, 0, 0, // Offset
                                0.01 // Speed (slight movement for better visibility)
                            );
                        }
                        
                        // Also spawn some particles at the base for better visibility
                        player.spawnParticle(
                            Particle.FIREWORK,
                            particleLoc,
                            1,
                            0.5, 0, 0.5, // Offset
                            0.1 // Speed
                        );
                    }
                }
            }
        }, 0L, 5L); // Every 5 ticks (1/4 second) for smoother effect
        
        beamTasks.put(location, task);
    }
    
    /**
     * Removes the beacon effect at the specified location.
     */
    private void removeBeaconAt(@NotNull Location location) {
        BukkitTask task = beamTasks.remove(location);
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
    }

    /**
     * Sets the configuration file for this arena.
     *
     * @param file The configuration file
     */
    public synchronized void setConfigFile(@NotNull File file) {
        ValidationUtils.requireNonNull(file, "File", "Arena.setConfigFile");
        this.configFile = file;
    }

    /**
     * Gets the configuration file for this arena.
     *
     * @return The configuration file, or null if not set
     */
    public @Nullable File getConfigFile() {
        return configFile;
    }

    /**
     * Gets the set of blocks that are allowed to be placed in this arena.
     *
     * @return An unmodifiable set of allowed block materials
     */
    public @NotNull Set<Material> getAllowedBlocks() {
        return Collections.unmodifiableSet(allowedBlocks);
    }

    /**
     * Adds a block type to the list of allowed blocks.
     *
     * @param material The material to allow
     */
    public synchronized void addAllowedBlock(@NotNull Material material) {
        ValidationUtils.requireNonNull(material, "Material", "Add Allowed Block");
        allowedBlocks.add(material);
    }

    /**
     * Removes a block type from the list of allowed blocks.
     *
     * @param material The material to disallow
     * @return True if the material was removed, false if it wasn't allowed
     */
    public synchronized boolean removeAllowedBlock(@NotNull Material material) {
        ValidationUtils.requireNonNull(material, "Material", "Remove Allowed Block");
        return allowedBlocks.remove(material);
    }

    /**
     * Checks if a block type is allowed to be placed in this arena.
     *
     * @param material The material to check
     * @return True if the block can be placed
     */
    public boolean isBlockAllowed(@NotNull Material material) {
        return allowedBlocks.contains(material);
    }
} 
