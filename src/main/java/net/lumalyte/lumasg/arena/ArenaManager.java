package net.lumalyte.arena;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import net.lumalyte.LumaSG;
import net.lumalyte.util.BaseManager;
import net.lumalyte.util.ErrorHandlingUtils;

/**
 * Manages Survival Games arenas, including loading, saving, and providing access to all arenas.
 * Handles asynchronous disk operations for arena persistence.
 */
public class ArenaManager extends BaseManager {
    /**
     * List of all loaded arenas.
     */
    private final @NotNull List<Arena> arenas;
    /**
     * Folder where arena configuration files are stored.
     */
    private final @NotNull File arenaFolder;
    
    /** Debouncing mechanism to prevent excessive saves */
    private volatile BukkitTask pendingSaveTask = null;
    private final AtomicBoolean saveScheduled = new AtomicBoolean(false);
    
    /**
     * Constructs a new ArenaManager.
     *
     * @param plugin The main plugin instance
     */
    public ArenaManager(@NotNull LumaSG plugin) {
        super(plugin, "ArenaManager");
        
        this.arenas = new CopyOnWriteArrayList<>();
        this.arenaFolder = new File(plugin.getDataFolder(), "arenas");
        
        // Create arenas folder if it doesn't exist
        if (!arenaFolder.exists()) {
            arenaFolder.mkdirs();
        }
    }
    
    /**
     * Starts the arena manager by creating the arena folder (if needed) and loading all arenas from disk.
     * This method is called during plugin startup.
     */
    public void start() {
        // Create arena folder if it doesn't exist
        if (!arenaFolder.exists() && !arenaFolder.mkdirs()) {
            logger.severe("Failed to create arena folder!");
            return;
        }
        
        // Load arenas asynchronously
        loadArenas().thenRun(() -> {
            logger.info("Loaded " + arenas.size() + " arenas.");
            // Save arenas after loading to ensure they're in the latest format
            saveArenas().thenRun(() -> 
                logger.debug("Saved " + arenas.size() + " arenas after loading."));
        });
    }
    
    /**
     * Stops the arena manager by saving all arenas to disk and clearing the arena list.
     * This method is called during plugin shutdown.
     */
    public void stop() {
        logger.info("Saving " + arenas.size() + " arenas before shutdown...");
        
        // Cancel any pending save task
        if (pendingSaveTask != null && !pendingSaveTask.isCancelled()) {
            pendingSaveTask.cancel();
        }
        
        // Save arenas synchronously before clearing
        saveArenasImmediately().join();
        arenas.clear();
        logger.info("Arena save complete, cleared arena list.");
    }
    
    /**
     * Loads all arenas asynchronously from the arena folder.
     *
     * @return A future that completes when all arenas are loaded
     */
    public @NotNull CompletableFuture<Void> loadArenas() {
        return CompletableFuture.runAsync(() -> {
            File[] files = arenaFolder.listFiles((dir, name) -> name.endsWith(".yml"));
            if (files == null) {
                logger.warn("No arena files found in " + arenaFolder.getPath());
                return;
            }
            
            logger.debug("Found " + files.length + " arena files to load.");
            int successfulLoads = 0;
            int failedLoads = 0;
            
            for (File file : files) {
                String arenaName = file.getName().replace(".yml", "");
                
                try {
                    logger.debug("Loading arena from file: " + file.getName());
                    
                    // Load configuration with validation
                    YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
                    if (config.getKeys(false).isEmpty()) {
                        logger.warn("Arena file " + file.getName() + " is empty - skipping");
                        failedLoads++;
                        continue;
                    }
                    
                    // Attempt to load arena with retry mechanism
                    Arena arena = loadArenaWithRetry(config, arenaName);
                    if (arena != null) {
                        arena.setConfigFile(file);
                        arenas.add(arena);
                        successfulLoads++;
                        logger.debug("Successfully loaded arena: " + arenaName);
                    } else {
                        failedLoads++;
                        logger.severe("Failed to load arena: " + arenaName + " after all retry attempts");
                    }
                    
                } catch (Exception e) {
                    failedLoads++;
                    logger.severe("Critical error loading arena: " + arenaName, e);
                }
            }
            
            logger.info("Arena loading completed: " + successfulLoads + " successful, " + failedLoads + " failed");
            
            if (successfulLoads == 0 && files.length > 0) {
                logger.severe("Failed to load any arenas! Please check your arena configurations.");
            }
        }).exceptionally(e -> {
            logger.severe("Failed to load arenas", e);
            return null;
        });
    }
    
    /**
     * Loads an arena with retry mechanism for recoverable errors.
     */
    private @Nullable Arena loadArenaWithRetry(@NotNull YamlConfiguration config, @NotNull String arenaName) {
        try {
            return ErrorHandlingUtils.executeWithRetry(
                () -> {
                    Arena arena = Arena.fromConfig(plugin, config, arenaName);
                    if (arena == null) {
                        throw new RuntimeException("Arena.fromConfig returned null for " + arenaName);
                    }
                    return arena;
                },
                3, // maxRetries
                100L, // initialDelayMs
                2.0, // backoffMultiplier
                plugin.getLogger(),
                "Arena Loading: " + arenaName
            );
        } catch (Exception e) {
            logger.severe("Failed to load arena " + arenaName + " after all retry attempts: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Schedules a debounced save of all arenas to prevent excessive disk I/O.
     * Multiple calls within the delay period will only result in one save operation.
     *
     * @return A future that completes when the save is scheduled (not when it's completed)
     */
    public @NotNull CompletableFuture<Void> saveArenas() {
        return CompletableFuture.runAsync(() -> {
            // Use atomic operation to prevent race conditions
            if (saveScheduled.compareAndSet(false, true)) {
                // Cancel any existing pending save
                if (pendingSaveTask != null && !pendingSaveTask.isCancelled()) {
                    pendingSaveTask.cancel();
                }
                
                long saveDelayTicks = plugin.getConfig().getInt("arena.save-delay-seconds", 3) * 20L;
                logger.debug("Scheduling arena save in " + (saveDelayTicks / 20.0) + " seconds...");
                
                // Schedule the actual save operation
                pendingSaveTask = Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> {
                    try {
                        saveScheduled.set(false);
                        performArenaSave();
                    } catch (Exception e) {
                        logger.severe("Error during scheduled arena save", e);
                        saveScheduled.set(false);
                    }
                }, saveDelayTicks);
            } else {
                logger.debug("Arena save already scheduled, skipping duplicate request");
            }
        });
    }
    
    /**
     * Immediately saves all arenas without debouncing.
     * This should only be used during shutdown or when immediate persistence is required.
     *
     * @return A future that completes when all arenas are saved
     */
    public @NotNull CompletableFuture<Void> saveArenasImmediately() {
        return CompletableFuture.runAsync(() -> {
            // Cancel any pending debounced save
            if (pendingSaveTask != null && !pendingSaveTask.isCancelled()) {
                pendingSaveTask.cancel();
            }
            saveScheduled.set(false);
            
            performArenaSave();
        });
    }
    
    /**
     * Performs the actual arena save operation.
     */
    private void performArenaSave() {
        logger.debug("Starting to save " + arenas.size() + " arenas...");
        
        for (Arena arena : arenas) {
            try {
                File file = new File(arenaFolder, arena.getName() + ".yml");
                YamlConfiguration config;
                
                // Load existing config if it exists
                if (file.exists()) {
                    config = YamlConfiguration.loadConfiguration(file);
                } else {
                    config = new YamlConfiguration();
                }
                
                arena.saveToConfig(config);
                config.save(file);
                
                logger.debug("Successfully saved arena: " + arena.getName() + " to " + file.getName());
            } catch (Exception e) {
                logger.severe("Failed to save arena: " + arena.getName(), e);
            }
        }
        
        logger.debug("Finished saving all arenas.");
    }
    
    /**
     * Gets an arena by name (case-insensitive).
     *
     * @param name The name of the arena
     * @return The arena, or null if not found
     */
    public @Nullable Arena getArena(@NotNull String name) {
        return arenas.stream()
            .filter(arena -> arena.getName().equalsIgnoreCase(name))
            .findFirst()
            .orElse(null);
    }
    
    /**
     * Gets an unmodifiable list of all loaded arenas.
     *
     * @return An unmodifiable list of all arenas
     */
    public @NotNull List<Arena> getArenas() {
        return Collections.unmodifiableList(arenas);
    }
    
    /**
     * Adds an arena to the manager and saves all arenas asynchronously.
     *
     * @param arena The arena to add
     */
    public void addArena(@NotNull Arena arena) {
        logger.info("Adding new arena: " + arena.getName());
        arenas.add(arena);
        // Save arenas immediately when a new one is added
        saveArenas().thenRun(() -> 
            logger.debug("Saved arenas after adding: " + arena.getName()));
    }
    
    /**
     * Removes an arena from the manager and deletes its file asynchronously.
     *
     * @param arena The arena to remove
     */
    public void removeArena(@NotNull Arena arena) {
        logger.info("Removing arena: " + arena.getName());
        arenas.remove(arena);
        
        // Delete arena file asynchronously
        CompletableFuture.runAsync(() -> {
            File file = new File(arenaFolder, arena.getName() + ".yml");
            if (file.exists()) {
                if (file.delete()) {
                    logger.debug("Successfully deleted arena file: " + file.getName());
                } else {
                    logger.warn("Failed to delete arena file: " + file.getName());
                }
            }
        }).exceptionally(e -> {
            logger.severe("Failed to delete arena file: " + arena.getName(), e);
            return null;
        });
        
        // Save remaining arenas
        saveArenas().thenRun(() -> 
            logger.debug("Saved remaining arenas after removing: " + arena.getName()));
    }
    
    /**
     * Gets the selected arena for a player.
     * 
     * @param player The player to get the selected arena for
     * @return The selected arena, or null if none is selected
     */
    public @Nullable Arena getSelectedArena(@NotNull Player player) {
        return plugin.getAdminWandListener().getSelectedArena(player);
    }
    
    /**
     * Creates a new arena with the given name, center location, and radius.
     * 
     * @param name The name of the arena
     * @param center The center location of the arena
     * @param radius The radius of the arena
     * @return The newly created arena, or null if creation failed
     */
    public @Nullable Arena createArena(@NotNull String name, @NotNull org.bukkit.Location center, int radius) {
        // Check if arena with this name already exists
        if (getArena(name) != null) {
            logger.warn("Arena with name " + name + " already exists!");
            return null;
        }
        
        try {
            // Create new arena
            Arena arena = new Arena(name, plugin, center, radius);
            
            // Set the world for the arena
            arena.setWorld(center.getWorld());
            
            // Add arena to manager
            addArena(arena);
            
            // Scan for chests automatically
            logger.debug("Scanning for chests in newly created arena: " + name);
            int chestCount = arena.scanForChests();
            logger.debug("Found " + chestCount + " chests in arena: " + name);
            
            logger.info("Successfully created arena: " + name);
            return arena;
        } catch (Exception e) {
            logger.severe("Failed to create arena: " + name, e);
            return null;
        }
    }
} 
