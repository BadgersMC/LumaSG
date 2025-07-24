package net.lumalyte.lumasg.game.player;

import net.lumalyte.lumasg.LumaSG;
import net.lumalyte.lumasg.arena.Arena;
import net.lumalyte.lumasg.util.core.DebugLogger;
import net.lumalyte.lumasg.game.core.GameState;
import net.lumalyte.lumasg.util.serialization.InventorySerializer;
import net.lumalyte.lumasg.util.serialization.PotionEffectSerializer;
import net.lumalyte.lumasg.util.security.InputSanitizer;
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
 * Thread-safe implementation with proper synchronization for compound
 * operations.
 */
public class GamePlayerManager {
    private final @NotNull LumaSG plugin;
    private final @NotNull Arena arena;

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

    /**
     * Map of player UUIDs to their kill counts - using AtomicInteger for
     * thread-safe increments
     */
    private final @NotNull Map<UUID, AtomicInteger> playerKills;

    /**
     * Map of player UUIDs to their original inventories (for restoration) -
     * serialized as binary
     */
    private final @NotNull Map<UUID, byte[]> inventories;

    /**
     * Map of player UUIDs to their original armor contents (for restoration) -
     * serialized as binary
     */
    private final @NotNull Map<UUID, byte[]> armorContents;

    /** Map of player UUIDs to their experience levels before joining the game */
    private final @NotNull Map<UUID, Integer> playerExperienceLevels;

    /** Map of player UUIDs to their experience points before joining the game */
    private final @NotNull Map<UUID, Float> playerExperiencePoints;

    /** Map of player UUIDs to their locations before joining the game */
    private final @NotNull Map<UUID, Location> previousLocations;

    /** Map of player UUIDs to their food levels before joining the game */
    private final @NotNull Map<UUID, Integer> playerFoodLevels;

    /** Map of player UUIDs to their saturation levels before joining the game */
    private final @NotNull Map<UUID, Float> playerSaturationLevels;

    /**
     * Map of player UUIDs to their active potion effects before joining the game -
     * serialized as binary
     */
    private final @NotNull Map<UUID, byte[]> playerPotionEffects;

    /** Set of players who disconnected during the game */
    private final @NotNull Set<UUID> disconnectedPlayers;

    /** Cache for player objects to avoid repeated lookups */
    private final @NotNull Map<UUID, Player> playerCache = new ConcurrentHashMap<>();

    /**
     * ReadWriteLock for complex operations that need consistency across multiple
     * data structures
     */
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    public GamePlayerManager(@NotNull LumaSG plugin, @NotNull Arena arena, @NotNull UUID gameId) {
        this.plugin = plugin;
        this.arena = arena;

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
        this.playerExperienceLevels = new ConcurrentHashMap<>();
        this.playerExperiencePoints = new ConcurrentHashMap<>();
        this.previousLocations = new ConcurrentHashMap<>();
        this.playerFoodLevels = new ConcurrentHashMap<>();
        this.playerSaturationLevels = new ConcurrentHashMap<>();
        this.playerPotionEffects = new ConcurrentHashMap<>();

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
            if (!canJoinAsPlayer(currentState)) {
                addSpectatorInternal(player);
                return false;
            }

            if (players.contains(player.getUniqueId())) {
                return false;
            }

            // Check if we have enough spawn points
            if (!hasAvailableSpawnPoints()) {
                player.sendMessage(net.kyori.adventure.text.Component.text(
                        "This arena is full! Maximum players: " + arena.getSpawnPoints().size(),
                        net.kyori.adventure.text.format.NamedTextColor.RED));
                return false;
            }

            // Store and prepare player for the game
            storePlayerState(player);
            addPlayerToGame(player, currentState);

            return true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Checks if a player can join as an active player based on the current game
     * state.
     */
    private boolean canJoinAsPlayer(@NotNull GameState currentState) {
        return currentState == GameState.WAITING || currentState == GameState.COUNTDOWN;
    }

    /**
     * Checks if there are available spawn points for new players.
     */
    private boolean hasAvailableSpawnPoints() {
        return players.size() < arena.getSpawnPoints().size();
    }

    /**
     * Stores the player's current state before joining the game.
     */
    private void storePlayerState(@NotNull Player player) {
        // Store player's previous state
        previousLocations.put(player.getUniqueId(), player.getLocation());
        playerGameModes.put(player.getUniqueId(), player.getGameMode());

        // Store inventory and experience if configured
        if (plugin.getConfig().getBoolean("game.clear-inventory", true)) {
            storeAndClearPlayerData(player);
        }
    }

    /**
     * Stores and clears player data (inventory, experience, effects, etc.).
     */
    private void storeAndClearPlayerData(@NotNull Player player) {
        try {
            storePlayerInventoryAndExperience(player);
            storePlayerHungerAndEffects(player);
            clearPlayerState(player);

            logger.debug("Saved and cleared inventory/experience/hunger/effects for player: "
                    + InputSanitizer.sanitizeForLogging(player.getName()));
        } catch (Exception e) {
            logger.warn("Failed to save inventory for player: " + InputSanitizer.sanitizeForLogging(player.getName()),
                    e);
        }
    }

    /**
     * Stores player's inventory and experience data.
     */
    private void storePlayerInventoryAndExperience(@NotNull Player player) {
        // Serialize and store inventory using Kryo binary serialization
        byte[] serializedInventory = InventorySerializer.serializeInventory(player.getInventory().getContents());
        byte[] serializedArmor = InventorySerializer.serializeInventory(player.getInventory().getArmorContents());

        if (serializedInventory != null) {
            inventories.put(player.getUniqueId(), serializedInventory);
        }
        if (serializedArmor != null) {
            armorContents.put(player.getUniqueId(), serializedArmor);
        }

        // Store experience
        playerExperienceLevels.put(player.getUniqueId(), player.getLevel());
        playerExperiencePoints.put(player.getUniqueId(), player.getExp());
    }

    /**
     * Stores player's hunger and potion effects.
     */
    private void storePlayerHungerAndEffects(@NotNull Player player) {
        // Store hunger, saturation, and potion effects
        playerFoodLevels.put(player.getUniqueId(), player.getFoodLevel());
        playerSaturationLevels.put(player.getUniqueId(), player.getSaturation());

        // Serialize and store potion effects using Kryo binary serialization
        byte[] serializedEffects = PotionEffectSerializer.serializePotionEffects(player.getActivePotionEffects());
        if (serializedEffects != null) {
            playerPotionEffects.put(player.getUniqueId(), serializedEffects);
        }
    }

    /**
     * Clears the player's state for the game.
     */
    private void clearPlayerState(@NotNull Player player) {
        // Clear inventory and experience
        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
        player.setLevel(0);
        player.setExp(0.0f);

        // Set full hunger but zero saturation so hunger starts depleting immediately
        // when grace period begins
        player.setFoodLevel(20);
        player.setSaturation(0.0f); // No saturation - hunger will start depleting right away

        // Remove all potion effects
        for (org.bukkit.potion.PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }
    }

    /**
     * Adds the player to the game with proper setup.
     */
    private void addPlayerToGame(@NotNull Player player, @NotNull GameState currentState) {
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

            // Set full hunger and saturation
            player.setFoodLevel(20);
            player.setSaturation(20.0f);

            // Remove all potion effects for spectators
            for (org.bukkit.potion.PotionEffect effect : player.getActivePotionEffects()) {
                player.removePotionEffect(effect.getType());
            }

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
     * Restores a player's state from before they joined the game.
     */
    private void restorePlayer(@NotNull Player player) {
        UUID playerId = player.getUniqueId();

        try {
            // Reset player's scoreboard to server default
            player.setScoreboard(org.bukkit.Bukkit.getScoreboardManager().getMainScoreboard());

            // Always clear current inventory first
            player.getInventory().clear();
            player.getInventory().setArmorContents(null);

            restoreGameMode(player);

            // Restore inventory if configured
            if (plugin.getConfig().getBoolean("game.restore-inventory", true)) {
                restoreInventoryAndExperience(player);
                restoreHungerAndEffects(player);
            }

            // Handle location restoration based on configuration
            if (plugin.getConfig().getBoolean("lobby.teleport-on-leave", true)) {
                teleportToLobby(player);
            } else if (plugin.getConfig().getBoolean("game.save-location", true)) {
                restoreLocation(player);
            }

            // Clear all stored data for this player
            clearStoredPlayerData(playerId);

            logger.debug(
                    "Successfully restored player state for: " + InputSanitizer.sanitizeForLogging(player.getName()));
        } catch (Exception e) {
            logger.error("Failed to restore player state for: " + InputSanitizer.sanitizeForLogging(player.getName()),
                    e);
            // Attempt basic restoration even if full restore fails
            performBasicRestore(player);
        }
    }

    /**
     * Restores the player's game mode.
     */
    private void restoreGameMode(@NotNull Player player) {
        GameMode previousMode = playerGameModes.get(player.getUniqueId());
        if (previousMode != null) {
            player.setGameMode(previousMode);
        } else {
            player.setGameMode(GameMode.SURVIVAL);
        }
    }

    /**
     * Restores the player's location.
     */
    private void restoreLocation(@NotNull Player player) {
        Location previousLocation = previousLocations.get(player.getUniqueId());
        if (previousLocation != null) {
            player.teleport(previousLocation);
        } else {
            teleportToLobby(player);
        }
    }

    /**
     * Restores the player's inventory and experience.
     */
    private void restoreInventoryAndExperience(@NotNull Player player) {
        UUID playerId = player.getUniqueId();

        // Restore inventory contents using Kryo binary deserialization
        byte[] serializedInventory = inventories.get(playerId);
        byte[] serializedArmor = armorContents.get(playerId);

        if (serializedInventory != null) {
            try {
                ItemStack[] contents = InventorySerializer.deserializeInventory(serializedInventory);
                if (contents != null) {
                    player.getInventory().setContents(contents);
                }
            } catch (Exception e) {
                logger.warn("Failed to restore inventory contents for player: "
                        + InputSanitizer.sanitizeForLogging(player.getName()), e);
            }
        }

        if (serializedArmor != null) {
            try {
                ItemStack[] armor = InventorySerializer.deserializeInventory(serializedArmor);
                if (armor != null) {
                    player.getInventory().setArmorContents(armor);
                }
            } catch (Exception e) {
                logger.warn("Failed to restore armor contents for player: "
                        + InputSanitizer.sanitizeForLogging(player.getName()), e);
            }
        }

        // If either restoration failed, clear inventory as fallback
        if (serializedInventory == null && serializedArmor == null) {
            player.getInventory().clear();
        }

        // Restore experience
        player.setLevel(playerExperienceLevels.getOrDefault(playerId, 0));
        player.setExp(playerExperiencePoints.getOrDefault(playerId, 0.0f));
    }

    /**
     * Restores the player's hunger and potion effects.
     */
    private void restoreHungerAndEffects(@NotNull Player player) {
        UUID playerId = player.getUniqueId();

        // Restore hunger and saturation
        player.setFoodLevel(playerFoodLevels.getOrDefault(playerId, 20));
        player.setSaturation(playerSaturationLevels.getOrDefault(playerId, 5.0f)); // Restore original saturation, not
                                                                                   // max

        // Restore potion effects using Kryo binary deserialization
        byte[] serializedEffects = playerPotionEffects.get(playerId);
        if (serializedEffects != null) {
            try {
                // Remove current effects first
                player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType()));

                // Apply stored effects
                org.bukkit.potion.PotionEffect[] effects = PotionEffectSerializer
                        .deserializePotionEffects(serializedEffects);
                if (effects != null) {
                    for (org.bukkit.potion.PotionEffect effect : effects) {
                        player.addPotionEffect(effect);
                    }
                }
            } catch (Exception e) {
                logger.warn("Failed to restore potion effects for player: "
                        + InputSanitizer.sanitizeForLogging(player.getName()), e);
            }
        }
    }

    /**
     * Performs a basic restore if full restoration fails.
     */
    private void performBasicRestore(@NotNull Player player) {
        player.setGameMode(GameMode.SURVIVAL);
        player.getInventory().clear();
        player.setLevel(0);
        player.setExp(0.0f);
        player.setFoodLevel(20);
        // Don't set saturation to max - let hunger work normally
        player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType()));
        teleportToLobby(player);
    }

    /**
     * Clears all stored data for a player.
     */
    private void clearStoredPlayerData(@NotNull UUID playerId) {
        playerLocations.remove(playerId);
        playerGameModes.remove(playerId);
        inventories.remove(playerId);
        armorContents.remove(playerId);
        playerExperienceLevels.remove(playerId);
        playerExperiencePoints.remove(playerId);
        previousLocations.remove(playerId);
        playerFoodLevels.remove(playerId);
        playerSaturationLevels.remove(playerId);
        playerPotionEffects.remove(playerId);
        playerCache.remove(playerId);
    }

    /**
     * Assigns a spawn point to a player.
     */
    private void assignSpawnPoint(@NotNull Player player) {
        List<Location> spawnPoints = arena.getSpawnPoints();
        if (!spawnPoints.isEmpty()) {
            // Find all unused spawn points
            Set<Location> usedSpawns = new HashSet<>(playerLocations.values());
            List<Location> availableSpawns = new ArrayList<>();

            for (Location spawn : spawnPoints) {
                if (!usedSpawns.contains(spawn)) {
                    availableSpawns.add(spawn);
                }
            }

            // Randomly select from available spawn points
            // Note: availableSpawns should never be empty since addPlayer() enforces spawn
            // point limits
            Random random = new Random();
            Location finalSpawnPoint = availableSpawns.get(random.nextInt(availableSpawns.size()));

            logger.debug("Randomly assigned spawn point " + (spawnPoints.indexOf(finalSpawnPoint) + 1) +
                    " of " + spawnPoints.size() + " to " + InputSanitizer.sanitizeForLogging(player.getName()));

            // Store and teleport to spawn point
            final Location spawnPoint = finalSpawnPoint;
            playerLocations.put(player.getUniqueId(), spawnPoint);

            // Add small delay to ensure teleport happens after other state changes
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    player.teleport(spawnPoint);
                    logger.debug("Teleported " + InputSanitizer.sanitizeForLogging(player.getName())
                            + " to spawn point at: " +
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
        if (!player.isOnline()) {
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

        logger.debug(
                "Attempting to teleport " + InputSanitizer.sanitizeForLogging(player.getName()) + " to lobby at: " +
                        String.format("World: %s, X: %.2f, Y: %.2f, Z: %.2f",
                                lobbyLocation.getWorld().getName(),
                                lobbyLocation.getX(),
                                lobbyLocation.getY(),
                                lobbyLocation.getZ()));

        // Add a small delay to ensure teleport happens after other cleanup
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            try {
                player.teleport(lobbyLocation);
                logger.debug(
                        "Successfully teleported " + InputSanitizer.sanitizeForLogging(player.getName()) + " to lobby");
            } catch (Exception e) {
                logger.warn("Failed to teleport " + InputSanitizer.sanitizeForLogging(player.getName()) + " to lobby: "
                        + e.getMessage());
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
                        (float) plugin.getConfig().getDouble("lobby.pitch", 0.0));
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
     * Cleans up all player data and sends all players back to lobby.
     */
    public void cleanup() {
        // Restore and teleport all remaining players and spectators
        Set<UUID> allPlayers = new HashSet<>();
        allPlayers.addAll(players);
        allPlayers.addAll(spectators);

        for (UUID playerId : allPlayers) {
            Player player = getCachedPlayer(playerId);
            if (player != null && player.isOnline()) {
                try {
                    restorePlayer(player);
                    logger.debug(
                            "Cleaned up and restored player: " + InputSanitizer.sanitizeForLogging(player.getName()));
                } catch (Exception e) {
                    logger.warn("Failed to cleanup player " + InputSanitizer.sanitizeForLogging(player.getName()), e);
                    // Fallback: at least try to teleport them to lobby
                    try {
                        teleportToLobby(player);
                    } catch (Exception ex) {
                        logger.severe("Failed to teleport player " + InputSanitizer.sanitizeForLogging(player.getName())
                                + " to lobby during cleanup", ex);
                    }
                }
            }
        }

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
        playerExperienceLevels.clear();
        playerExperiencePoints.clear();
        previousLocations.clear();
        playerFoodLevels.clear();
        playerSaturationLevels.clear();
        playerPotionEffects.clear();
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

    public @NotNull Map<UUID, byte[]> getSerializedInventories() {
        return Collections.unmodifiableMap(inventories);
    }

    public @NotNull Map<UUID, byte[]> getSerializedArmorContents() {
        return Collections.unmodifiableMap(armorContents);
    }

    public @NotNull Map<UUID, Integer> getPlayerExperienceLevels() {
        return Collections.unmodifiableMap(playerExperienceLevels);
    }

    public @NotNull Map<UUID, Float> getPlayerExperiencePoints() {
        return Collections.unmodifiableMap(playerExperiencePoints);
    }

    public @NotNull Map<UUID, Location> getPreviousLocations() {
        return Collections.unmodifiableMap(previousLocations);
    }

    public @NotNull Map<UUID, Integer> getPlayerFoodLevels() {
        return Collections.unmodifiableMap(playerFoodLevels);
    }

    public @NotNull Map<UUID, Float> getPlayerSaturationLevels() {
        return Collections.unmodifiableMap(playerSaturationLevels);
    }

    public @NotNull Map<UUID, byte[]> getPlayerPotionEffects() {
        return Collections.unmodifiableMap(playerPotionEffects);
    }
}
