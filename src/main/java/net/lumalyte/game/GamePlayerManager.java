package net.lumalyte.game;

import net.lumalyte.LumaSG;
import net.lumalyte.arena.Arena;
import net.lumalyte.util.DebugLogger;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Manages players within a game instance.
 * Handles player joining, leaving, inventory management, and location tracking.
 * 
 * Thread-safe implementation with proper synchronization for compound operations.
 */
public class GamePlayerManager {
    private final @NotNull LumaSG plugin;
    private final @NotNull Arena arena;
    private final @NotNull UUID gameId;
    
    /** Debug logger instance for game player management */
    private final @NotNull DebugLogger.ContextualLogger logger;
    
    /** Set of all players currently participating in the game */
    private final @NotNull Set<UUID> players;
    
    /** Set of all spectators watching the game */
    private final @NotNull Set<UUID> spectators;
    
    /** Map of player UUIDs to their spawn locations */
    private final @NotNull Map<UUID, Location> playerLocations;
    
    /** Map of player UUIDs to their original game modes (for restoration) */
    private final @NotNull Map<UUID, GameMode> playerGameModes;
    
    /** Map of player UUIDs to their kill counts - using AtomicInteger for thread-safe increments */
    private final @NotNull Map<UUID, AtomicInteger> playerKills;
    
    /** Map of player UUIDs to their original inventories (for restoration) */
    private final @NotNull Map<UUID, ItemStack[]> inventories;
    
    /** Map of player UUIDs to their original armor contents (for restoration) */
    private final @NotNull Map<UUID, ItemStack[]> armorContents;
    
    /** Map of player UUIDs to their locations before joining the game */
    private final @NotNull Map<UUID, Location> previousLocations;
    
    /** Set of players who disconnected during the game */
    private final @NotNull Set<UUID> disconnectedPlayers;
    
    /** Cache for player objects to avoid repeated lookups */
    private final @NotNull Map<UUID, Player> playerCache = new ConcurrentHashMap<>();
    
    /** ReadWriteLock for complex operations that need consistency across multiple data structures */
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    
    public GamePlayerManager(@NotNull LumaSG plugin, @NotNull Arena arena, @NotNull UUID gameId) {
        this.plugin = plugin;
        this.arena = arena;
        this.gameId = gameId;
        
        // Initialize player collections with thread-safe implementations
        this.players = Collections.newSetFromMap(new ConcurrentHashMap<>());
        this.spectators = Collections.newSetFromMap(new ConcurrentHashMap<>());
        this.playerLocations = new ConcurrentHashMap<>();
        this.playerGameModes = new ConcurrentHashMap<>();
        this.playerKills = new ConcurrentHashMap<>();
        this.disconnectedPlayers = Collections.newSetFromMap(new ConcurrentHashMap<>());
        
        // Initialize inventory tracking with thread-safe maps
        this.inventories = new ConcurrentHashMap<>();
        this.armorContents = new ConcurrentHashMap<>();
        this.previousLocations = new ConcurrentHashMap<>();
        
        // Initialize logger
        this.logger = plugin.getDebugLogger().forContext("GamePlayerManager");
    }
    
    /**
     * Adds a player to the game.
     * Thread-safe implementation using write lock for compound operations.
     */
    public boolean addPlayer(@NotNull Player player, @NotNull GameState currentState) {
        lock.writeLock().lock();
        try {
            // If game has started (past countdown), only allow spectators
            if (currentState != GameState.WAITING && currentState != GameState.COUNTDOWN) {
                addSpectatorInternal(player);
                return false;
            }

            if (!players.contains(player.getUniqueId())) {
                // Check if we have enough spawn points
                if (players.size() >= arena.getSpawnPoints().size()) {
                    player.sendMessage(net.kyori.adventure.text.Component.text("This arena is full! Maximum players: " + arena.getSpawnPoints().size(), net.kyori.adventure.text.format.NamedTextColor.RED));
                    return false;
                }

                // Store player's previous state
                previousLocations.put(player.getUniqueId(), player.getLocation());
                playerGameModes.put(player.getUniqueId(), player.getGameMode());
                
                // Store inventory and exp
                if (plugin.getConfig().getBoolean("game.clear-inventory", true)) {
                    try {
                        inventories.put(player.getUniqueId(), player.getInventory().getContents());
                        armorContents.put(player.getUniqueId(), player.getInventory().getArmorContents());
                        player.getInventory().clear();
                        player.getInventory().setArmorContents(null);
                    } catch (Exception e) {
                        logger.warn("Failed to save inventory for player: " + player.getName());
                    }
                }
                
                // Reset player stats from external plugins
                plugin.getHookManager().resetPlayerStats(player);
                
                // Add player to game - all operations are now atomic within the lock
                players.add(player.getUniqueId());
                playerKills.put(player.getUniqueId(), new AtomicInteger(0));
                playerCache.put(player.getUniqueId(), player);
                
                // Set game mode
                player.setGameMode(GameMode.ADVENTURE);
                
                // Assign spawn point if in waiting/countdown
                if (currentState == GameState.WAITING || currentState == GameState.COUNTDOWN) {
                    assignSpawnPoint(player);
                }
                
                return true;
            }
            
            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Adds a player as a spectator to the game.
     * Public method with proper locking.
     */
    public void addSpectator(@NotNull Player player) {
        lock.writeLock().lock();
        try {
            addSpectatorInternal(player);
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Internal method for adding spectators (assumes lock is already held).
     */
    private void addSpectatorInternal(@NotNull Player player) {
        if (!spectators.contains(player.getUniqueId())) {
            spectators.add(player.getUniqueId());
            playerCache.put(player.getUniqueId(), player);
            
            // Clear inventory before setting to spectator mode
            player.getInventory().clear();
            player.getInventory().setArmorContents(null);
            
            player.setGameMode(GameMode.SPECTATOR);
            
            // Teleport to spectator spawn if available
            if (arena.getSpectatorSpawn() != null) {
                player.teleport(arena.getSpectatorSpawn());
            }
        }
    }
    
    /**
     * Removes a player from the game.
     * Thread-safe implementation with proper locking.
     */
    public void removePlayer(@NotNull Player player, boolean isDisconnect, boolean isShuttingDown) {
        lock.writeLock().lock();
        try {
            if (!players.contains(player.getUniqueId()) && !spectators.contains(player.getUniqueId())) {
                return;
            }
            
            // Only handle cleanup if the game isn't ending
            if (!isShuttingDown && !isDisconnect && player.isOnline()) {
                restorePlayer(player);
            }
            
            // Remove player from game - atomic operations
            players.remove(player.getUniqueId());
            spectators.remove(player.getUniqueId());
            playerCache.remove(player.getUniqueId());
            
            if (isDisconnect) {
                disconnectedPlayers.add(player.getUniqueId());
            }
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Eliminates a player from the game.
     * Thread-safe implementation.
     */
    public void eliminatePlayer(@NotNull Player player) {
        lock.writeLock().lock();
        try {
            if (players.contains(player.getUniqueId())) {
                // Remove player from active players
                players.remove(player.getUniqueId());
                
                // Add as spectator
                addSpectatorInternal(player);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Restores a player's state when they leave the game.
     */
    private void restorePlayer(@NotNull Player player) {
        // Reset player's scoreboard to server default
        player.setScoreboard(org.bukkit.Bukkit.getScoreboardManager().getMainScoreboard());
        
        // Always clear current inventory first
        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
        
        // Restore original inventory if it was saved
        if (plugin.getConfig().getBoolean("game.restore-inventory", true)) {
            try {
                ItemStack[] inv = inventories.get(player.getUniqueId());
                ItemStack[] armor = armorContents.get(player.getUniqueId());
                if (inv != null) player.getInventory().setContents(inv);
                if (armor != null) player.getInventory().setArmorContents(armor);
            } catch (Exception e) {
                logger.warn("Failed to restore inventory for player: " + player.getName());
            }
        }
        
        // Restore game mode
        GameMode previousMode = playerGameModes.get(player.getUniqueId());
        if (previousMode != null) {
            player.setGameMode(previousMode);
        }
        
        // Restore player stats from external plugins
        plugin.getHookManager().restorePlayerStats(player);
        
        // Teleport to configured location
        if (plugin.getConfig().getBoolean("lobby.teleport-on-leave", true)) {
            teleportToLobby(player);
        } else if (plugin.getConfig().getBoolean("game.save-location", true)) {
            Location originalLocation = previousLocations.get(player.getUniqueId());
            if (originalLocation != null) {
                player.teleport(originalLocation);
            }
        }
    }
    
    /**
     * Assigns a spawn point to a player.
     */
    private void assignSpawnPoint(@NotNull Player player) {
        List<Location> spawnPoints = arena.getSpawnPoints();
        if (!spawnPoints.isEmpty()) {
            // Find an unused spawn point
            Set<Location> usedSpawns = new HashSet<>(playerLocations.values());
            
            Location finalSpawnPoint = null;
            for (Location spawn : spawnPoints) {
                if (!usedSpawns.contains(spawn)) {
                    finalSpawnPoint = spawn;
                    break;
                }
            }
            
            // If no unused spawn found, use random available spawn
            if (finalSpawnPoint == null) {
                finalSpawnPoint = spawnPoints.get(new Random().nextInt(spawnPoints.size()));
                logger.warn("No unused spawn points found for " + player.getName() + ", using random spawn point");
            }
            
            // Store and teleport to spawn point
            final Location spawnPoint = finalSpawnPoint;
            playerLocations.put(player.getUniqueId(), spawnPoint);
            
            // Add small delay to ensure teleport happens after other state changes
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    player.teleport(spawnPoint);
                    logger.debug("Teleported " + player.getName() + " to spawn point at: " + 
                        String.format("World: %s, X: %.2f, Y: %.2f, Z: %.2f", 
                            spawnPoint.getWorld().getName(),
                            spawnPoint.getX(),
                            spawnPoint.getY(),
                            spawnPoint.getZ()));
                }
            }, 2L);
        } else {
            logger.warn("No spawn points configured for arena " + arena.getName());
        }
    }
    
    /**
     * Teleports a player to the lobby if enabled.
     */
    private void teleportToLobby(@NotNull Player player) {
        if (player == null || !player.isOnline()) {
            logger.warn("Attempted to teleport null or offline player to lobby");
            return;
        }

        Location lobbyLocation = getLobbyLocation();
        if (lobbyLocation == null) {
            logger.warn("Lobby location is null! Check config.yml lobby settings");
            return;
        }

        if (lobbyLocation.getWorld() == null) {
            logger.warn("Lobby world is null! Check config.yml lobby.world setting");
            return;
        }

        logger.debug("Attempting to teleport " + player.getName() + " to lobby at: " + 
            String.format("World: %s, X: %.2f, Y: %.2f, Z: %.2f", 
                lobbyLocation.getWorld().getName(),
                lobbyLocation.getX(),
                lobbyLocation.getY(),
                lobbyLocation.getZ()));

        // Add a small delay to ensure teleport happens after other cleanup
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            try {
                player.teleport(lobbyLocation);
                logger.debug("Successfully teleported " + player.getName() + " to lobby");
            } catch (Exception e) {
                logger.warn("Failed to teleport " + player.getName() + " to lobby: " + e.getMessage());
            }
        }, 2L);
    }
    
    /**
     * Gets the lobby location from configuration.
     */
    private @Nullable Location getLobbyLocation() {
        if (plugin.getConfig().getBoolean("lobby.enabled", true)) {
            String worldName = plugin.getConfig().getString("lobby.world", "world");
            org.bukkit.World world = org.bukkit.Bukkit.getWorld(worldName);
            if (world != null) {
                return new Location(
                    world,
                    plugin.getConfig().getDouble("lobby.x", 0.0),
                    plugin.getConfig().getDouble("lobby.y", 64.0),
                    plugin.getConfig().getDouble("lobby.z", 0.0),
                    (float) plugin.getConfig().getDouble("lobby.yaw", 0.0),
                    (float) plugin.getConfig().getDouble("lobby.pitch", 0.0)
                );
            } else {
                logger.warn("Could not find lobby world: " + worldName);
            }
        }
        return null;
    }
    
    /**
     * Gets a cached player object or fetches it from Bukkit.
     */
    public @Nullable Player getCachedPlayer(@NotNull UUID playerId) {
        Player cached = playerCache.get(playerId);
        if (cached != null && cached.isOnline()) {
            return cached;
        }
        
        Player player = org.bukkit.Bukkit.getPlayer(playerId);
        if (player != null && player.isOnline()) {
            playerCache.put(playerId, player);
            return player;
        } else {
            playerCache.remove(playerId);
            return null;
        }
    }
    
    /**
     * Cleans up all player data.
     */
    public void cleanup() {
        // Clear player cache
        playerCache.clear();
        
        // Clear collections
        players.clear();
        spectators.clear();
        playerLocations.clear();
        playerGameModes.clear();
        playerKills.clear();
        disconnectedPlayers.clear();
        inventories.clear();
        armorContents.clear();
        previousLocations.clear();
    }
    
    // Getters
    public @NotNull Set<UUID> getPlayers() {
        return Collections.unmodifiableSet(players);
    }
    
    public @NotNull Set<UUID> getSpectators() {
        return Collections.unmodifiableSet(spectators);
    }
    
    public @NotNull Set<UUID> getDisconnectedPlayers() {
        return Collections.unmodifiableSet(disconnectedPlayers);
    }
    
    public int getPlayerCount() {
        lock.readLock().lock();
        try {
            return players.size();
        } finally {
            lock.readLock().unlock();
        }
    }
    
    public int getPlayerKills(@NotNull UUID playerId) {
        AtomicInteger kills = playerKills.get(playerId);
        return kills != null ? kills.get() : 0;
    }
    
    public void incrementKills(@NotNull UUID playerId) {
        playerKills.computeIfAbsent(playerId, k -> new AtomicInteger(0)).incrementAndGet();
    }
    
    public @NotNull Map<UUID, Location> getPlayerLocations() {
        return Collections.unmodifiableMap(playerLocations);
    }
    
    public @NotNull Map<UUID, GameMode> getPlayerGameModes() {
        return Collections.unmodifiableMap(playerGameModes);
    }
    
    public @NotNull Map<UUID, ItemStack[]> getInventories() {
        return Collections.unmodifiableMap(inventories);
    }
    
    public @NotNull Map<UUID, ItemStack[]> getArmorContents() {
        return Collections.unmodifiableMap(armorContents);
    }
    
    public @NotNull Map<UUID, Location> getPreviousLocations() {
        return Collections.unmodifiableMap(previousLocations);
    }
} 