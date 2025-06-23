package net.lumalyte.arena;

import net.lumalyte.LumaSG;
import net.lumalyte.exception.LumaSGException;
import net.lumalyte.util.ValidationUtils;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

/**
 * Manages Survival Games arenas, including loading, saving, and providing access to all arenas.
 * Handles asynchronous disk operations for arena persistence.
 */
public class ArenaManager {
    /**
     * Reference to the main plugin instance.
     */
    private final @NotNull LumaSG plugin;
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
        this.plugin = plugin;
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
            plugin.getLogger().severe("Failed to create arena folder!");
            return;
        }
        
        // Load arenas asynchronously
        loadArenas().thenRun(() -> {
            plugin.getLogger().info("Loaded " + arenas.size() + " arenas.");
            // Save arenas after loading to ensure they're in the latest format
            saveArenas().thenRun(() -> 
                plugin.getLogger().info("Saved " + arenas.size() + " arenas after loading."));
        });
    }
    
    /**
     * Stops the arena manager by saving all arenas to disk and clearing the arena list.
     * This method is called during plugin shutdown.
     */
    public void stop() {
        plugin.getLogger().info("Saving " + arenas.size() + " arenas before shutdown...");
        
        // Cancel any pending save task
        if (pendingSaveTask != null && !pendingSaveTask.isCancelled()) {
            pendingSaveTask.cancel();
        }
        
        // Save arenas synchronously before clearing
        saveArenasImmediately().join();
        arenas.clear();
        plugin.getLogger().info("Arena save complete, cleared arena list.");
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
                plugin.getLogger().warning("No arena files found in " + arenaFolder.getPath());
                return;
            }
            
            plugin.getLogger().info("Found " + files.length + " arena files to load.");
            int successfulLoads = 0;
            int failedLoads = 0;
            
            for (File file : files) {
                String arenaName = file.getName().replace(".yml", "");
                
                try {
                    plugin.getLogger().info("Loading arena from file: " + file.getName());
                    
                    // Load configuration with validation
                    YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
                    if (config.getKeys(false).isEmpty()) {
                        plugin.getLogger().warning("Arena file " + file.getName() + " is empty - skipping");
                        failedLoads++;
                        continue;
                    }
                    
                    // Attempt to load arena with retry mechanism
                    Arena arena = loadArenaWithRetry(config, arenaName, 3);
                    if (arena != null) {
                        arena.setConfigFile(file);
                        arenas.add(arena);
                        successfulLoads++;
                        plugin.getLogger().info("Successfully loaded arena: " + arenaName);
                    } else {
                        failedLoads++;
                        plugin.getLogger().severe("Failed to load arena: " + arenaName + " after all retry attempts");
                    }
                    
                } catch (Exception e) {
                    failedLoads++;
                    plugin.getLogger().log(Level.SEVERE, "Critical error loading arena: " + arenaName, e);
                }
            }
            
            plugin.getLogger().info("Arena loading completed: " + successfulLoads + " successful, " + failedLoads + " failed");
            
            if (successfulLoads == 0 && files.length > 0) {
                plugin.getLogger().severe("Failed to load any arenas! Please check your arena configurations.");
            }
        }).exceptionally(e -> {
            plugin.getLogger().log(Level.SEVERE, "Failed to load arenas", e);
            return null;
        });
    }
    
    /**
     * Loads an arena with retry mechanism for recoverable errors.
     */
    private @Nullable Arena loadArenaWithRetry(@NotNull YamlConfiguration config, @NotNull String arenaName, int maxRetries) {
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                Arena arena = Arena.fromConfig(plugin, config, arenaName);
                if (arena != null) {
                    return arena;
                }
                
                if (attempt < maxRetries) {
                    plugin.getLogger().warning("Arena loading attempt " + attempt + " failed for " + arenaName + ", retrying...");
                    
                    // Brief delay before retry
                    try {
                        Thread.sleep(100 * attempt); // Exponential backoff
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
                
            } catch (Exception e) {
                if (isRecoverableError(e)) {
                    if (attempt < maxRetries) {
                        plugin.getLogger().warning("Recoverable error loading arena " + arenaName + 
                            " (attempt " + attempt + "/" + maxRetries + "): " + e.getMessage());
                        
                        try {
                            Thread.sleep(200 * attempt);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    } else {
                        plugin.getLogger().severe("Arena " + arenaName + " failed to load after " + maxRetries + " attempts: " + e.getMessage());
                    }
                } else {
                    // Non-recoverable error, don't retry
                    plugin.getLogger().log(Level.SEVERE, "Non-recoverable error loading arena " + arenaName + ": " + e.getMessage(), e);
                    break;
                }
            }
        }
        
        return null;
    }
    
    /**
     * Determines if an error is recoverable and worth retrying.
     */
    private boolean isRecoverableError(@NotNull Throwable error) {
        // Configuration errors are typically not recoverable
        if (error instanceof LumaSGException.ConfigurationException) {
            return false;
        }
        
        // Arena errors might be recoverable if they're related to world loading
        String message = error.getMessage().toLowerCase();
        if (message.contains("world") || message.contains("location")) {
            return true;
        }
        
        // IO errors might be temporary
        if (error instanceof java.io.IOException) {
            return true;
        }
        
        // Other errors are generally not recoverable
        return false;
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
                plugin.getLogger().info("Scheduling arena save in " + (saveDelayTicks / 20.0) + " seconds...");
                
                // Schedule the actual save operation
                pendingSaveTask = Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> {
                    try {
                        saveScheduled.set(false);
                        performArenaSave();
                    } catch (Exception e) {
                        plugin.getLogger().log(Level.SEVERE, "Error during scheduled arena save", e);
                        saveScheduled.set(false);
                    }
                }, saveDelayTicks);
            } else {
                plugin.getLogger().info("Arena save already scheduled, skipping duplicate request");
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
        plugin.getLogger().info("Starting to save " + arenas.size() + " arenas...");
        
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
                
                plugin.getLogger().info("Successfully saved arena: " + arena.getName() + " to " + file.getName());
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to save arena: " + arena.getName(), e);
            }
        }
        
        plugin.getLogger().info("Finished saving all arenas.");
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
        plugin.getLogger().info("Adding new arena: " + arena.getName());
        arenas.add(arena);
        // Save arenas immediately when a new one is added
        saveArenas().thenRun(() -> 
            plugin.getLogger().info("Saved arenas after adding: " + arena.getName()));
    }
    
    /**
     * Removes an arena from the manager and deletes its file asynchronously.
     *
     * @param arena The arena to remove
     */
    public void removeArena(@NotNull Arena arena) {
        plugin.getLogger().info("Removing arena: " + arena.getName());
        arenas.remove(arena);
        
        // Delete arena file asynchronously
        CompletableFuture.runAsync(() -> {
            File file = new File(arenaFolder, arena.getName() + ".yml");
            if (file.exists()) {
                if (file.delete()) {
                    plugin.getLogger().info("Successfully deleted arena file: " + file.getName());
                } else {
                    plugin.getLogger().warning("Failed to delete arena file: " + file.getName());
                }
            }
        }).exceptionally(e -> {
            plugin.getLogger().log(Level.SEVERE, "Failed to delete arena file: " + arena.getName(), e);
            return null;
        });
        
        // Save remaining arenas
        saveArenas().thenRun(() -> 
            plugin.getLogger().info("Saved remaining arenas after removing: " + arena.getName()));
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
            plugin.getLogger().warning("Arena with name " + name + " already exists!");
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
            plugin.getLogger().info("Scanning for chests in newly created arena: " + name);
            int chestCount = arena.scanForChests();
            plugin.getLogger().info("Found " + chestCount + " chests in arena: " + name);
            
            plugin.getLogger().info("Successfully created arena: " + name);
            return arena;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create arena: " + name, e);
            return null;
        }
    }
} 
