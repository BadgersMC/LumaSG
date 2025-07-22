package net.lumalyte.lumasg.game.core;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import net.lumalyte.lumasg.game.ui.CelebrationManager;
import net.lumalyte.lumasg.game.ui.DeathMessageManager;
import net.lumalyte.lumasg.game.ui.GameScoreboardManager;
import net.lumalyte.lumasg.game.player.GamePlayerManager;
import net.lumalyte.lumasg.game.player.GameNameplateManager;
import net.lumalyte.lumasg.game.mechanics.GameTimerManager;
import net.lumalyte.lumasg.game.mechanics.GameEliminationManager;
import net.lumalyte.lumasg.game.world.GameWorldManager;
import net.lumalyte.lumasg.game.team.GameTeamManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import net.lumalyte.lumasg.LumaSG;
import net.lumalyte.lumasg.arena.Arena;
import net.lumalyte.lumasg.util.security.InputSanitizer;
import net.lumalyte.lumasg.util.core.DebugLogger;
import net.lumalyte.lumasg.util.serialization.InventorySerializer;
import net.lumalyte.lumasg.util.messaging.MiniMessageUtils;
import net.lumalyte.lumasg.game.core.GameState;
import net.lumalyte.lumasg.game.core.GameMode;

/**
 * Represents a single instance of a Survival Games match.
 * 
 * <p>
 * This class manages the complete lifecycle of a Survival Games game, including
 * player management, game state transitions, countdowns, PvP phases, and
 * victory
 * conditions. Each game instance is independent and manages its own players,
 * timers, and game logic.
 * </p>
 * 
 * <p>
 * The game follows a specific state flow: WAITING → COUNTDOWN → GRACE_PERIOD →
 * ACTIVE → DEATHMATCH → FINISHED. Each state has different rules and behaviors.
 * </p>
 * 
 * @author LumaLyte
 * @version 1.0
 * @since 1.0
 */
public class Game {
    /** The plugin instance for configuration and server access */
    private final @NotNull LumaSG plugin;

    /** The debug logger instance for this game */
    private final @NotNull DebugLogger.ContextualLogger logger;

    /** The arena where this game is being played */
    private final @NotNull Arena arena;

    /** Unique identifier for this game instance */
    private final @NotNull UUID gameId;

    /** Current state of the game (waiting, countdown, active, etc.) */
    private @NotNull GameState state;

    /** Whether PvP is currently enabled */
    private boolean pvpEnabled;

    /** Whether the game is currently in grace period */
    private boolean isGracePeriod;

    /** Whether the game is shutting down */
    private boolean isShuttingDown = false;

    /** Flag indicating if this game has been properly set up by a ranked player */
    private volatile boolean setupComplete = false;

    /** Game start time for accurate duration calculation */
    private @Nullable Instant gameStartTime = null;

    // Component managers for different aspects of the game
    private final @NotNull GamePlayerManager playerManager;
    private final @NotNull GameTimerManager timerManager;
    private final @NotNull GameWorldManager worldManager;
    private final @NotNull GameScoreboardManager scoreboardManager;
    private final @NotNull CelebrationManager celebrationManager;
    private final @NotNull GameEliminationManager eliminationManager;
    private final DeathMessageManager deathMessageManager;
    private final GameNameplateManager gameNameplateManager;
    private final @NotNull GameTeamManager teamManager;

    // Memory management: Track async operations for cleanup
    private final @NotNull Set<CompletableFuture<?>> activeFutures = ConcurrentHashMap.newKeySet();

    // Task management: Track scheduled tasks for cleanup
    private final @NotNull Map<Integer, BukkitTask> activeTasks = new ConcurrentHashMap<>();

    // Barrier block management for movement restriction during countdown
    private final @NotNull Map<Location, Material> originalBlocks = new ConcurrentHashMap<>();
    private final @NotNull Set<Location> barrierBlocks = ConcurrentHashMap.newKeySet();

    /**
     * Map of player UUIDs to their original inventories (for restoration) -
     * serialized with Kryo
     */
    private final @NotNull Map<UUID, byte[]> inventories = new ConcurrentHashMap<>();

    /**
     * Map of player UUIDs to their original armor contents (for restoration) -
     * serialized with Kryo
     */
    private final @NotNull Map<UUID, byte[]> armorContents = new ConcurrentHashMap<>();

    /** Map of player UUIDs to their experience levels before joining the game */
    private final @NotNull Map<UUID, Integer> playerExperienceLevels = new ConcurrentHashMap<>();

    /** Map of player UUIDs to their experience points before joining the game */
    private final @NotNull Map<UUID, Float> playerExperiencePoints = new ConcurrentHashMap<>();

    /** Map of player UUIDs to their locations before joining the game */
    private final @NotNull Map<UUID, Location> previousLocations = new ConcurrentHashMap<>();

    /** Set of players who disconnected during the game */
    private final @NotNull Set<UUID> disconnectedPlayers = ConcurrentHashMap.newKeySet();

    /** Ordered list of eliminated players (first eliminated = last place) */
    private final @NotNull List<UUID> eliminationOrder = new ArrayList<>();

    /** Map of player UUIDs to their damage dealt during the game */
    private final @NotNull Map<UUID, Double> playerDamageDealt = new ConcurrentHashMap<>();

    /** Map of player UUIDs to their damage taken during the game */
    private final @NotNull Map<UUID, Double> playerDamageTaken = new ConcurrentHashMap<>();

    /** Map of player UUIDs to their chests opened during the game */
    private final @NotNull Map<UUID, Integer> playerChestsOpened = new ConcurrentHashMap<>();

    /** Secure random number generator for cryptographically secure operations */
    private static final SecureRandom secureRandom = new SecureRandom();

    /**
     * Constructs a new Game instance in the specified arena.
     * 
     * <p>
     * The game starts in INACTIVE state and must be manually configured and
     * started by calling activateGame() when ready to accept players.
     * </p>
     * 
     * @param plugin The plugin instance
     * @param arena  The arena where the game will be played
     * 
     * @throws IllegalArgumentException if plugin or arena is null
     */
    public Game(@NotNull LumaSG plugin, @NotNull Arena arena) {
        this.plugin = plugin;
        this.arena = arena;
        this.gameId = UUID.randomUUID();
        this.state = GameState.WAITING; // Start in WAITING state - games are activated immediately after creation

        // Initialize contextual logger for this game
        this.logger = plugin.getDebugLogger().forContext("Game-" + arena.getName());

        // Initialize game state
        this.pvpEnabled = false;
        this.isGracePeriod = false;

        // Initialize component managers
        this.playerManager = new GamePlayerManager(plugin, arena, gameId);
        this.timerManager = new GameTimerManager(plugin, playerManager);
        this.worldManager = new GameWorldManager(plugin, arena);
        this.scoreboardManager = new GameScoreboardManager(plugin, arena, gameId, playerManager, timerManager);
        this.celebrationManager = new CelebrationManager(plugin, playerManager);
        this.eliminationManager = new GameEliminationManager(plugin, gameId.toString(), playerManager);
        this.deathMessageManager = new DeathMessageManager(plugin, playerManager);
        this.gameNameplateManager = new GameNameplateManager(plugin, arena, gameId, playerManager);
        this.teamManager = new GameTeamManager(plugin, this, GameMode.SOLO); // Default to solo mode

        logger.info("Created new game with ID: " + gameId + " in arena: " + arena.getName());
    }

    /**
     * Gets the set of players who disconnected during the game.
     * 
     * <p>
     * This is used to track players who left during gameplay and may need
     * special handling when they rejoin.
     * </p>
     * 
     * @return An unmodifiable set of disconnected player UUIDs
     */
    public @NotNull Set<UUID> getDisconnectedPlayers() {
        return playerManager.getDisconnectedPlayers();
    }

    /**
     * Checks if the game is currently in grace period.
     * 
     * <p>
     * During grace period, PvP is disabled and players have time to prepare
     * before combat begins.
     * </p>
     * 
     * @return true if the game is in grace period, false otherwise
     */
    public boolean isGracePeriod() {
        return isGracePeriod;
    }

    /**
     * Adds a player as a spectator to the game.
     * 
     * <p>
     * Spectators can watch the game but cannot participate in combat or
     * interact with the game world. They are teleported to the spectator spawn
     * point if one is configured.
     * </p>
     * 
     * @param player The player to add as a spectator
     * 
     * @throws IllegalArgumentException if player is null
     */
    public void addSpectator(@NotNull Player player) {
        playerManager.addSpectator(player);

        // Broadcast spectator message
        broadcastMessage(Component.text()
                .append(player.displayName())
                .append(Component.text(" is now spectating!", NamedTextColor.GRAY))
                .build());
    }

    /**
     * Starts the countdown with a custom duration.
     * 
     * <p>
     * This method allows starting the countdown with a specific duration,
     * overriding the default countdown time from configuration.
     * </p>
     * 
     * @param seconds The duration of the countdown in seconds
     * 
     * @throws IllegalArgumentException if seconds is negative
     */
    public void startCountdown(int seconds) {
        if (seconds < 0) {
            throw new IllegalArgumentException("Countdown duration cannot be negative");
        }

        if (state != GameState.WAITING) {
            return; // Can only start countdown from waiting state
        }

        timerManager.startCountdown(seconds, this::startGame);
        state = GameState.COUNTDOWN;
        scoreboardManager.setCurrentGameState(state);
        timerManager.setCurrentGameState(state);
    }

    /**
     * Creates a 3x3x3 barrier box around the specified location.
     * 
     * @param center The center location for the barrier box
     */
    private void createBarrierBoxAroundLocation(@NotNull Location center) {
        World world = center.getWorld();
        if (world == null)
            return;

        int centerX = center.getBlockX();
        int centerY = center.getBlockY();
        int centerZ = center.getBlockZ();

        // Create barriers on all 4 sides, 3 blocks high
        for (int y = 0; y < 3; y++) {
            // North side (Z-)
            placeBarrierBlock(world, centerX, centerY + y, centerZ - 1);
            // South side (Z+)
            placeBarrierBlock(world, centerX, centerY + y, centerZ + 1);
            // East side (X+)
            placeBarrierBlock(world, centerX + 1, centerY + y, centerZ);
            // West side (X-)
            placeBarrierBlock(world, centerX - 1, centerY + y, centerZ);

            // Corner blocks for complete enclosure
            placeBarrierBlock(world, centerX - 1, centerY + y, centerZ - 1); // NW
            placeBarrierBlock(world, centerX + 1, centerY + y, centerZ - 1); // NE
            placeBarrierBlock(world, centerX - 1, centerY + y, centerZ + 1); // SW
            placeBarrierBlock(world, centerX + 1, centerY + y, centerZ + 1); // SE
        }
    }

    /**
     * Places a barrier block at the specified location, storing the original block
     * for restoration.
     * 
     * @param world The world to place the block in
     * @param x     The X coordinate
     * @param y     The Y coordinate
     * @param z     The Z coordinate
     */
    private void placeBarrierBlock(@NotNull World world, int x, int y, int z) {
        Location loc = new Location(world, x, y, z);
        Material originalMaterial = loc.getBlock().getType();

        // Only place barriers on air blocks or replace non-solid blocks
        if (originalMaterial == Material.AIR || !originalMaterial.isSolid()) {
            // Store original block for restoration
            originalBlocks.put(loc, originalMaterial);
            barrierBlocks.add(loc);

            // Place barrier block (invisible to players)
            loc.getBlock().setType(Material.BARRIER);

            // Track in world manager for safety cleanup
            worldManager.trackBarrierBlock(loc);

            logger.debug("Placed barrier block at (" + x + ", " + y + ", " + z + "), replacing " + originalMaterial);
        }
    }

    /**
     * Removes all barrier blocks and restores original blocks.
     * 
     * <p>
     * This method is called when the grace period starts, allowing players to move
     * freely.
     * </p>
     */
    private void removeSpawnBarriers() {
        logger.debug("Removing spawn barriers - grace period starting");

        for (Location loc : barrierBlocks) {
            if (loc.getWorld() != null) {
                Material originalMaterial = originalBlocks.getOrDefault(loc, Material.AIR);
                loc.getBlock().setType(originalMaterial);
                worldManager.untrackBarrierBlock(loc);
                logger.debug("Restored block at " + loc + " to " + originalMaterial);
            }
        }

        // Clear tracking collections
        originalBlocks.clear();
        barrierBlocks.clear();

        logger.info("Removed all spawn barriers - players can now move freely");
    }

    /**
     * Removes barrier blocks around a specific spawn location and restores the
     * original blocks.
     * This is used when a player leaves the game to clean up their specific spawn
     * point.
     * 
     * @param center The center location (spawn point) to remove barriers around
     */
    private void removeBarriersAroundLocation(@NotNull Location center) {
        World world = center.getWorld();
        if (world == null) {
            return;
        }

        logger.debug("Removing barriers around location: " + center);

        // Remove barriers in a 3x3x3 box around the spawn point
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                for (int y = 0; y <= 2; y++) { // 3 blocks high
                    Location barrierLoc = center.clone().add(x, y, z);

                    // Only remove if it's actually a barrier block we placed
                    if (barrierBlocks.contains(barrierLoc)) {
                        // Get the original block material
                        Material originalMaterial = originalBlocks.get(barrierLoc);
                        if (originalMaterial != null) {
                            // Restore the original block
                            world.getBlockAt(barrierLoc).setType(originalMaterial);
                            originalBlocks.remove(barrierLoc);
                        } else {
                            // Default to air if no original block was stored
                            world.getBlockAt(barrierLoc).setType(Material.AIR);
                        }

                        barrierBlocks.remove(barrierLoc);
                        worldManager.untrackBarrierBlock(barrierLoc);
                    }
                }
            }
        }

        logger.debug("Removed barriers around spawn point");
    }

    /**
     * Starts periodic enforcement of spawn point restrictions during WAITING and
     * COUNTDOWN states.
     * This runs every 2 seconds to check if players have somehow escaped their
     * spawn points.
     */
    private void startSpawnPointEnforcement() {
        // Only run enforcement during WAITING and COUNTDOWN states
        BukkitTask enforcementTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            // Stop enforcement if game has progressed past countdown
            if (state != GameState.WAITING && state != GameState.COUNTDOWN) {
                return;
            }

            // Check each player's position
            for (UUID playerId : playerManager.getPlayers()) {
                Player player = playerManager.getCachedPlayer(playerId);
                Location spawnLoc = playerManager.getPlayerLocations().get(playerId);

                if (player != null && spawnLoc != null) {
                    Location playerLoc = player.getLocation();

                    // Check if player is more than 1.5 blocks away from their spawn point
                    if (playerLoc.distance(spawnLoc) > 1.5) {
                        // Teleport them back to their spawn point
                        player.teleport(spawnLoc);
                        logger.debug("Teleported " + InputSanitizer.sanitizeForLogging(player.getName())
                                + " back to spawn point - was " +
                                String.format("%.2f", playerLoc.distance(spawnLoc)) + " blocks away");
                    }
                }
            }
        }, 40L, 40L); // Run every 2 seconds (40 ticks)

        // Track the task for cleanup
        activeTasks.put(enforcementTask.getTaskId(), enforcementTask);

        logger.debug("Started spawn point enforcement task");
    }

    /**
     * Starts the countdown using the default duration from configuration.
     * 
     * <p>
     * This method initiates the countdown phase, transitioning the game from
     * WAITING to COUNTDOWN state. During countdown, players see title messages
     * showing the remaining time until the game starts.
     * </p>
     */
    public void startCountdown() {
        if (state != GameState.WAITING) {
            return; // Can only start countdown from waiting state
        }

        // Tell players that chests are being filled
        broadcastMessage(Component.text()
                .append(Component.text("Preparing the arena...", NamedTextColor.GOLD))
                .build());

        // Fill all chests in the arena with random loot asynchronously before starting
        // countdown
        fillArenaChestsAsync().thenRun(() -> {
            // Run on main thread
            Bukkit.getScheduler().runTask(plugin, () -> {
                // Notify players that chests are filled and countdown is starting
                broadcastMessage(Component.text()
                        .append(Component.text("Arena prepared! ", NamedTextColor.GREEN))
                        .append(Component.text("Starting countdown...", NamedTextColor.YELLOW))
                        .build());

                // Now start the actual countdown using timer manager
                state = GameState.COUNTDOWN;
                scoreboardManager.setCurrentGameState(state);
                timerManager.setCurrentGameState(state);

                // Barriers are already in place from when players joined
                // No need to create them again during countdown

                timerManager.startCountdown(this::startGame);
            });
        });
    }

    /**
     * Cancels the countdown and returns the game to waiting state.
     * 
     * <p>
     * This method is typically called when there are not enough players
     * to start the game or when the game is manually cancelled.
     * </p>
     */
    public void cancelCountdown() {
        if (state == GameState.COUNTDOWN) {
            state = GameState.WAITING;
            scoreboardManager.setCurrentGameState(state);
            timerManager.setCurrentGameState(state);

            // Set up world settings and borders
            worldManager.setupWorld();

            // Remove spawn barriers if countdown was cancelled, then recreate them for
            // WAITING state
            removeSpawnBarriers();

            // Recreate barriers for all players since we're back in WAITING state
            // This ensures players remain locked at their spawn points
            for (UUID playerId : playerManager.getPlayers()) {
                Location spawnLoc = playerManager.getPlayerLocations().get(playerId);
                if (spawnLoc != null && spawnLoc.getWorld() != null) {
                    createBarrierBoxAroundLocation(spawnLoc);
                }
            }

            timerManager.cancelCountdown();

            // Clear any displayed titles for all players
            for (UUID playerId : playerManager.getPlayers()) {
                Player player = playerManager.getCachedPlayer(playerId);
                if (player != null) {
                    player.clearTitle();
                }
            }
        }
    }

    /**
     * Starts the game.
     */
    private void startGame() {
        if (state != GameState.COUNTDOWN) {
            return;
        }

        state = GameState.GRACE_PERIOD;
        scoreboardManager.setCurrentGameState(state);
        timerManager.setCurrentGameState(state);

        // Set player game mode when the game starts
        for (UUID playerId : playerManager.getPlayers()) {
            Player player = playerManager.getCachedPlayer(playerId);
            if (player != null) {
                player.setGameMode(org.bukkit.GameMode.ADVENTURE);
            }
        }

        // Start grace period immediately - chests are already filled
        startGracePeriod();

        // DON'T schedule deathmatch here - it should only start after grace period ends
        // This was causing the timer to start before players were freed from spawn
        // barriers

        // Create scoreboard team for nameplate control
        scoreboardManager.createNameplateTeam();

        // Add all players to the team
        for (UUID playerId : playerManager.getPlayers()) {
            Player player = playerManager.getCachedPlayer(playerId);
            if (player != null) {
                scoreboardManager.addPlayerToTeam(player);
            }
        }
    }

    /**
     * Fills all chests in the arena with random loot asynchronously.
     * This is done before the countdown starts to ensure all chests are ready
     * before players can access them.
     * 
     * @return A CompletableFuture that completes when all chests are filled
     */
    private CompletableFuture<Void> fillArenaChestsAsync() {
        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
            logger.debug("Starting to fill chests in arena: " + arena.getName());

            // Ensure chests are scanned
            if (!ensureChestsScanned()) {
                return;
            }

            List<Location> chestLocations = arena.getChestLocations();
            if (chestLocations.isEmpty()) {
                logger.warn("No chests found in arena " + arena.getName() + " - chests will not be filled!");
                return;
            }

            logger.debug("Found " + chestLocations.size() + " chests to fill in arena " + arena.getName());

            // Ensure chest items are loaded
            if (!ensureChestItemsLoaded()) {
                return;
            }

            // Process chests in batches
            int batchSize = 5; // Process 5 chests at a time
            List<List<Location>> batches = new ArrayList<>();
            for (int i = 0; i < chestLocations.size(); i += batchSize) {
                batches.add(chestLocations.subList(i, Math.min(chestLocations.size(), i + batchSize)));
            }

            AtomicInteger filledChests = new AtomicInteger(0);
            AtomicInteger failedChests = new AtomicInteger(0);

            // Process each batch sequentially to avoid overwhelming the server
            for (List<Location> batch : batches) {
                try {
                    processBatch(batch, filledChests, failedChests);
                    // Add a small delay between batches to reduce server load
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logger.warn("Chest filling interrupted", e);
                    break;
                }
            }

            logger.debug("Chest filling completed for arena " + arena.getName() +
                    " - Filled: " + filledChests.get() + ", Failed: " + failedChests.get() +
                    ", Total: " + chestLocations.size());
        });

        activeFutures.add(future);
        future.whenComplete((result, throwable) -> activeFutures.remove(future));

        return future;
    }

    /**
     * Ensures that chests are scanned in the arena.
     * 
     * @return true if chests are available, false if scanning failed
     */
    private boolean ensureChestsScanned() {
        if (!arena.getChestLocations().isEmpty()) {
            return true;
        }

        logger.debug("No chest locations found, scanning arena for chests...");
        try {
            CompletableFuture<Integer> scanFuture = new CompletableFuture<>();
            activeFutures.add(scanFuture);

            Bukkit.getScheduler().runTask(plugin, () -> {
                int count = arena.scanForChests();
                logger.debug("Found " + count + " chests in arena " + arena.getName());
                scanFuture.complete(count);
            });

            int chestsFound = scanFuture.get();
            activeFutures.remove(scanFuture);

            if (chestsFound == 0) {
                logger.warn("No chests found in arena " + arena.getName() + " after scanning!");
                return false;
            }
            return true;
        } catch (Exception e) {
            logger.severe("Error scanning for chests", e);
            return false;
        }
    }

    /**
     * Ensures that chest items are loaded.
     * 
     * @return true if items are loaded successfully, false otherwise
     */
    private boolean ensureChestItemsLoaded() {
        if (!plugin.getChestManager().getChestItems().isEmpty()) {
            return true;
        }

        logger.warn("No chest items loaded! Attempting to load chest items...");
        try {
            CompletableFuture<Void> loadFuture = plugin.getChestManager().loadChestItems();
            activeFutures.add(loadFuture);
            loadFuture.get();
            activeFutures.remove(loadFuture);
            logger.debug("Chest items loaded successfully");
            return true;
        } catch (Exception e) {
            logger.severe("Failed to load chest items", e);
            return false;
        }
    }

    /**
     * Processes a batch of chest locations.
     * 
     * @param batch        The batch of locations to process
     * @param filledChests Counter for successfully filled chests
     * @param failedChests Counter for failed chest fills
     */
    private void processBatch(List<Location> batch, AtomicInteger filledChests, AtomicInteger failedChests) {
        List<CompletableFuture<Boolean>> batchFutures = new ArrayList<>();

        for (Location chestLoc : batch) {
            String tier = selectChestTier();
            logger.debug("Attempting to fill chest at " + chestLoc + " with tier: " + tier);

            CompletableFuture<Boolean> chestFuture = new CompletableFuture<>();
            activeFutures.add(chestFuture);
            final Location finalChestLoc = chestLoc.clone();

            Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    boolean success = plugin.getChestManager().fillChest(finalChestLoc, tier);
                    if (success) {
                        logger.debug("Successfully filled chest at " + finalChestLoc + " with tier: " + tier);
                    } else {
                        logger.warn("Failed to fill chest at " + finalChestLoc + " with tier: " + tier);
                    }
                    chestFuture.complete(success);
                } catch (Exception e) {
                    logger.severe("Failed to fill chest at " + finalChestLoc + " with tier: " + tier, e);
                    chestFuture.complete(false);
                }
            });

            batchFutures.add(chestFuture);
        }

        // Wait for all chests in this batch to be filled
        for (CompletableFuture<Boolean> chestFuture : batchFutures) {
            try {
                if (chestFuture.get()) {
                    filledChests.incrementAndGet();
                } else {
                    failedChests.incrementAndGet();
                }
            } catch (Exception e) {
                logger.severe("Error filling chest", e);
                failedChests.incrementAndGet();
            } finally {
                activeFutures.remove(chestFuture);
            }
        }
    }

    /**
     * Selects a random chest tier based on predefined weights.
     * 
     * @return The selected tier
     */
    private String selectChestTier() {
        String[] tiers = { "common", "uncommon", "rare" };
        int[] weights = { 70, 25, 5 }; // 70% common, 25% uncommon, 5% rare

        int totalWeight = Arrays.stream(weights).sum();
        int randomValue = secureRandom.nextInt(totalWeight);

        int currentWeight = 0;
        for (int i = 0; i < weights.length; i++) {
            currentWeight += weights[i];
            if (randomValue < currentWeight) {
                return tiers[i];
            }
        }

        return tiers[0]; // Default to common
    }

    /**
     * Starts the grace period.
     */
    private void startGracePeriod() {
        logger.debug("Starting grace period for game: " + gameId);

        // Disable PvP during grace period
        pvpEnabled = false;
        isGracePeriod = true;

        // Set game start time for accurate duration calculation
        gameStartTime = Instant.now();

        // Reset timer manager start time for accurate game time tracking
        timerManager.resetStartTime();

        // Broadcast grace period start message
        broadcastMessage(Component.text()
                .append(Component.text("Grace Period: ", NamedTextColor.YELLOW))
                .append(Component.text("PvP is disabled! Gather resources and prepare for battle.",
                        NamedTextColor.WHITE))
                .build());

        // Remove spawn barriers so players can move freely
        removeSpawnBarriers();

        // Start grace period using timer manager
        timerManager.startGracePeriod(this::endGracePeriod);

        logger.debug("Grace period started");
    }

    /**
     * Ends the grace period and enables PvP.
     */
    private void endGracePeriod() {
        logger.debug("Ending grace period for game: " + gameId);

        // Enable PvP - use synchronized block to ensure thread safety
        synchronized (this) {
            pvpEnabled = true;
            isGracePeriod = false;
        }

        // Update game state
        state = GameState.ACTIVE;
        scoreboardManager.setCurrentGameState(state);
        timerManager.setCurrentGameState(state);

        // NOW schedule deathmatch and game end - this ensures the timer only starts
        // after the grace period ends and players are freed from spawn barriers
        timerManager.scheduleDeathmatch(this::startDeathmatch, () -> endGame(null));

        // Start periodic game end checking to catch solo scenarios and edge cases
        startPeriodicGameEndChecking();

        // Broadcast PvP enabled message with title
        Title title = Title.title(
                Component.text("Grace Period Ended!", NamedTextColor.RED, TextDecoration.BOLD),
                Component.text("PvP is now enabled!", NamedTextColor.YELLOW),
                Title.Times.times(Duration.ofMillis(500), Duration.ofMillis(3000), Duration.ofMillis(500)));

        for (UUID playerId : playerManager.getPlayers()) {
            Player player = playerManager.getCachedPlayer(playerId);
            if (player != null) {
                player.showTitle(title);
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 1.0f);
            }
        }

        logger.debug("Grace period ended, PvP is now enabled");
    }

    /**
     * Skips the grace period and immediately enables PvP.
     * This is primarily used for debugging and testing purposes.
     */
    public void skipGracePeriod() {
        if (!isGracePeriod) {
            logger.debug("Cannot skip grace period - not currently in grace period");
            return;
        }

        logger.debug("Skipping grace period for game: " + gameId);

        // Cancel any existing tasks (this will include grace period timer)
        timerManager.cancelCountdown();

        // Immediately end the grace period
        endGracePeriod();

        // Broadcast debug message
        broadcastMessage(Component.text()
                .append(Component.text("[DEBUG] ", NamedTextColor.GRAY))
                .append(Component.text("Grace period skipped! PvP enabled immediately.", NamedTextColor.YELLOW))
                .build());
    }

    /**
     * Starts the deathmatch phase.
     */
    private void startDeathmatch() {
        if (state != GameState.ACTIVE) {
            return;
        }

        state = GameState.DEATHMATCH;
        scoreboardManager.setCurrentGameState(state);
        timerManager.setCurrentGameState(state);

        // Set up deathmatch world border
        worldManager.setupDeathmatchBorder();

        // Teleport all players to spawn points
        List<Location> spawnPoints = arena.getSpawnPoints();
        if (spawnPoints.isEmpty()) {
            broadcastMessage(Component.text("No spawn points set for deathmatch! Ending game.", NamedTextColor.RED));
            endGame(null);
            return;
        }

        // Deathmatch announcements
        Title title = Title.title(
                Component.text("DEATHMATCH", NamedTextColor.RED, TextDecoration.BOLD),
                Component.text("Fight to the death!", NamedTextColor.GOLD),
                Title.Times.times(Duration.ofMillis(500), Duration.ofMillis(3000), Duration.ofMillis(500)));

        int spawnIndex = 0;
        for (UUID playerId : playerManager.getPlayers()) {
            Player player = playerManager.getCachedPlayer(playerId);
            if (player != null) {
                // Show title
                player.showTitle(title);

                // Play sound
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 1.0f);

                // Teleport to spawn points
                player.teleport(spawnPoints.get(spawnIndex % spawnPoints.size()));
                spawnIndex++;
            }
        }
    }

    /**
     * Checks if the game should end (e.g., only one player left).
     */
    private void checkGameEnd() {
        // Allow game end checks in more states, especially for solo testing
        if (state == GameState.FINISHED || isShuttingDown) {
            logger.debug("Game end check skipped - game already finished or shutting down");
            return;
        }

        int playerCount = playerManager.getPlayerCount();
        logger.debug("Checking game end conditions - Active players: " + playerCount + ", Game state: " + state);

        // Pre-cache player skins when 3 players remain for faster winner celebration
        if (playerCount == 3 && (state == GameState.ACTIVE || state == GameState.DEATHMATCH)) {
            logger.debug("3 players remaining - pre-caching skins for winner celebration");
            // Pre-cache player skins for better performance (removed in simplified
            // celebration manager)
        }

        // End game immediately if there's only one player left (regardless of game
        // state)
        // This handles solo testing and prevents timer showing zero for extended
        // periods
        if (playerCount <= 1) {
            logger.debug(
                    "Ending game immediately - only " + playerCount + " player(s) remaining (state: " + state + ")");
            endGame(null);
            return;
        }

        // In WAITING state, check if we have enough players to continue
        if (state == GameState.WAITING) {
            int minPlayers = plugin.getConfig().getInt("game.min-players", 2);
            if (playerCount < minPlayers) {
                logger.debug("Not enough players to start game - " + playerCount + "/" + minPlayers);
                // Don't end the game, just wait for more players
                return;
            }
        }

        // In COUNTDOWN state, if we drop below minimum players, cancel countdown
        if (state == GameState.COUNTDOWN) {
            int minPlayers = plugin.getConfig().getInt("game.min-players", 2);
            if (playerCount < minPlayers) {
                logger.debug("Cancelling countdown - not enough players: " + playerCount + "/" + minPlayers);
                cancelCountdown();
                return;
            }
        }
    }

    /**
     * Clears all ground items in the arena world.
     */
    private void clearGroundItems() {
        org.bukkit.World arenaWorld = arena.getWorld();
        if (arenaWorld != null) {
            for (org.bukkit.entity.Entity entity : arenaWorld.getEntities()) {
                if (entity instanceof org.bukkit.entity.Item) {
                    entity.remove();
                }
            }
            logger.info("Cleared all ground items in arena world: " + arenaWorld.getName());
        } else {
            logger.warn("Could not clear ground items - arena world is null");
        }
    }

    /**
     * Ends the game and returns players to their original locations.
     */
    public void endGame(Object o) {
        if (state == GameState.FINISHED || isShuttingDown) {
            return;
        }

        logger.info("Ending game: " + gameId);
        initializeGameShutdown();

        // Clear all ground items in the arena world
        clearGroundItems();

        // Disable PvP immediately
        disablePvpAndGracePeriod();

        // Cancel all scheduled tasks using timer manager
        cleanupManagers();

        // Set all players and spectators to adventure mode for safety during
        // celebration
        preparePlayersForEndGame();

        // Set world border to safe size for celebration (500 blocks)
        worldManager.setCelebrationBorder();

        // Record game statistics if enabled
        recordStatisticsIfEnabled();

        // CELEBRATION PHASE - Announce winner and show fireworks
        handleCelebration();

        // Wait for celebration to finish, then teleport everyone out
        scheduleGameCleanup();
    }

    /**
     * Initializes the game shutdown process by setting appropriate flags and
     * states.
     */
    private void initializeGameShutdown() {
        isShuttingDown = true;
        state = GameState.FINISHED;
        scoreboardManager.setCurrentGameState(state);
        timerManager.setCurrentGameState(state);
    }

    /**
     * Disables PvP and grace period for the end game phase.
     */
    private void disablePvpAndGracePeriod() {
        pvpEnabled = false;
        isGracePeriod = false;
    }

    /**
     * Cleans up timer and scoreboard managers.
     */
    private void cleanupManagers() {
        timerManager.cleanup();
        scoreboardManager.cleanup();
    }

    /**
     * Saves a player's inventory and armor contents using Kryo serialization.
     * 
     * @param player The player whose inventory to save
     */
    public void savePlayerInventory(@NotNull Player player) {
        UUID playerId = player.getUniqueId();

        try {
            // Save main inventory
            byte[] inventoryData = InventorySerializer.serializeInventory(player.getInventory().getContents());
            if (inventoryData != null) {
                inventories.put(playerId, inventoryData);
                logger.debug(
                        "Saved inventory for player: " + InputSanitizer.sanitizeForLogging(player.getName()) + " ("
                                + inventoryData.length + " bytes)");
            }

            // Save armor contents
            byte[] armorData = InventorySerializer.serializeInventory(player.getInventory().getArmorContents());
            if (armorData != null) {
                armorContents.put(playerId, armorData);
                logger.debug("Saved armor for player: " + InputSanitizer.sanitizeForLogging(player.getName()) + " ("
                        + armorData.length + " bytes)");
            }

            // Save experience
            playerExperienceLevels.put(playerId, player.getLevel());
            playerExperiencePoints.put(playerId, player.getExp());

            // Save location
            previousLocations.put(playerId, player.getLocation().clone());

        } catch (Exception e) {
            logger.error("Failed to save inventory for player: " + InputSanitizer.sanitizeForLogging(player.getName()),
                    e);
        }
    }

    /**
     * Restores a player's inventory and armor contents from Kryo serialization.
     * 
     * @param player The player whose inventory to restore
     */
    public void restorePlayerInventory(@NotNull Player player) {
        UUID playerId = player.getUniqueId();

        try {
            // Restore main inventory
            byte[] inventoryData = inventories.get(playerId);
            if (inventoryData != null) {
                ItemStack[] items = InventorySerializer.deserializeInventory(inventoryData);
                if (items != null) {
                    player.getInventory().setContents(items);
                    logger.debug(
                            "Restored inventory for player: " + InputSanitizer.sanitizeForLogging(player.getName()));
                }
                inventories.remove(playerId);
            }

            // Restore armor contents
            byte[] armorData = armorContents.get(playerId);
            if (armorData != null) {
                ItemStack[] armor = InventorySerializer.deserializeInventory(armorData);
                if (armor != null) {
                    player.getInventory().setArmorContents(armor);
                    logger.debug("Restored armor for player: " + InputSanitizer.sanitizeForLogging(player.getName()));
                }
                armorContents.remove(playerId);
            }

            // Restore experience
            Integer level = playerExperienceLevels.remove(playerId);
            if (level != null) {
                player.setLevel(level);
            }

            Float exp = playerExperiencePoints.remove(playerId);
            if (exp != null) {
                player.setExp(exp);
            }

            // Restore location
            Location previousLocation = previousLocations.remove(playerId);
            if (previousLocation != null) {
                player.teleport(previousLocation);
                logger.debug("Restored location for player: " + InputSanitizer.sanitizeForLogging(player.getName()));
            }

        } catch (Exception e) {
            logger.error(
                    "Failed to restore inventory for player: " + InputSanitizer.sanitizeForLogging(player.getName()),
                    e);
        }
    }

    /**
     * Clears stored inventory data for a player (cleanup).
     * 
     * @param playerId The player's UUID
     */
    public void clearPlayerInventoryData(@NotNull UUID playerId) {
        inventories.remove(playerId);
        armorContents.remove(playerId);
        playerExperienceLevels.remove(playerId);
        playerExperiencePoints.remove(playerId);
        previousLocations.remove(playerId);

        logger.debug("Cleared inventory data for player: " + playerId);
    }

    /**
     * Prepares all players and spectators for end game by setting them to adventure
     * mode
     * and clearing their inventories.
     */
    private void preparePlayersForEndGame() {
        preparePlayersForEndGameCleanup(playerManager.getPlayers());
        preparePlayersForEndGameCleanup(playerManager.getSpectators());
    }

    /**
     * Helper method to prepare a collection of player UUIDs for end game cleanup.
     * 
     * @param playerIds The collection of player UUIDs to prepare
     */
    private void preparePlayersForEndGameCleanup(Set<UUID> playerIds) {
        for (UUID playerId : playerIds) {
            Player player = playerManager.getCachedPlayer(playerId);
            if (player != null && player.isOnline()) {

                player.setGameMode(org.bukkit.GameMode.ADVENTURE);

                // Clear inventory immediately to prevent items from being kept
                player.getInventory().clear();
                // Note: Inventories can be null when empty - this is handled gracefully by the
                // system
                player.getInventory().setArmorContents(null);
            }
        }
    }

    /**
     * Records game statistics if statistics are enabled in the configuration.
     */
    private void recordStatisticsIfEnabled() {
        if (plugin.getConfig().getBoolean("statistics.enabled", true)) {
            long gameTimeSeconds = gameStartTime != null ? Duration.between(gameStartTime, Instant.now()).getSeconds()
                    : 0;
            eliminationManager.recordFinalStatistics(gameTimeSeconds);
        }
    }

    /**
     * Handles the celebration phase by determining if there's a winner and
     * triggering
     * the appropriate celebration.
     */
    private void handleCelebration() {
        if (playerManager.getPlayerCount() == 1) {
            celebrateWinner();
        } else {
            // Handle no winner case - broadcast end message
            broadcastMessage(Component.text("Game Over! No winner!", NamedTextColor.RED));
        }
    }

    /**
     * Celebrates the game winner if one exists.
     */
    private void celebrateWinner() {
        UUID winnerId = playerManager.getPlayers().iterator().next();
        Player winner = playerManager.getCachedPlayer(winnerId);
        if (winner != null) {
            celebrationManager.celebrateWinner(winner);
        }
    }

    /**
     * Schedules the final cleanup tasks to run after the celebration period.
     */
    private void scheduleGameCleanup() {
        // This gives time for fireworks and title to be seen
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            // Restore world settings using world manager (including original border)
            worldManager.restoreWorld();

            // Return all players to lobby or original locations using player manager
            playerManager.cleanup();

            // Remove from game manager
            plugin.getGameManager().removeGame(this);

            logger.info("Cleaned up game: " + gameId);
        }, 120L); // 6 second delay (120 ticks) - gives time for celebration
    }

    /**
     * Broadcasts a message to all players in the game.
     * 
     * @param message The message to broadcast
     */
    public void broadcastMessage(@NotNull Component message) {
        for (UUID playerId : playerManager.getPlayers()) {
            Player player = playerManager.getCachedPlayer(playerId);
            if (player != null) {
                player.sendMessage(message);
            }
        }

        for (UUID spectatorId : playerManager.getSpectators()) {
            Player spectator = playerManager.getCachedPlayer(spectatorId);
            if (spectator != null) {
                spectator.sendMessage(message);
            }
        }
    }

    /**
     * Removes a player from the game.
     * 
     * @param player       The player to remove
     * @param isDisconnect Whether the player disconnected
     */
    public synchronized void removePlayer(Player player, boolean isDisconnect) {
        removePlayer(player, isDisconnect, false);
    }

    /**
     * Removes a player from the game.
     * 
     * @param player         The player to remove
     * @param isDisconnect   Whether the player disconnected
     * @param isShuttingDown Whether the game is shutting down
     */
    public synchronized void removePlayer(Player player, boolean isDisconnect, boolean isShuttingDown) {
        if (!playerManager.getPlayers().contains(player.getUniqueId()) &&
                !playerManager.getSpectators().contains(player.getUniqueId())) {
            return;
        }

        // Clean up any hook-related metadata
        String kingdomsXMetadataKey = "lumasg_pvp_notified_" + arena.getName();
        if (player.hasMetadata(kingdomsXMetadataKey)) {
            player.removeMetadata(kingdomsXMetadataKey, plugin);
        }

        // Remove player from scoreboard first
        scoreboardManager.removePlayerFromScoreboard(player);

        // Get player's spawn location before removing them (for barrier cleanup)
        Location playerSpawnLoc = playerManager.getPlayerLocations().get(player.getUniqueId());

        // Delegate to player manager
        playerManager.removePlayer(player, isDisconnect, isShuttingDown);

        // Clean up barriers around the player's spawn point if they were in WAITING or
        // COUNTDOWN
        if (playerSpawnLoc != null && (state == GameState.WAITING || state == GameState.COUNTDOWN)) {
            removeBarriersAroundLocation(playerSpawnLoc);
            logger.debug("Cleaned up barriers for " + InputSanitizer.sanitizeForLogging(player.getName()) + " at "
                    + playerSpawnLoc);
        }

        // Only broadcast leave message and check game end if game isn't ending
        if (!isShuttingDown) {
            String leaveMsg = plugin.getConfig().getString("messages.player-leave",
                    "<gray><player> <yellow>has left the game! <gray>(<current>/<max>)");
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("player", player.getName());
            placeholders.put("current", String.valueOf(playerManager.getPlayerCount()));
            placeholders.put("max", String.valueOf(arena.getSpawnPoints().size()));
            broadcastMessage(MiniMessageUtils.parseMessage(leaveMsg, placeholders));

            // Check if game should end
            checkGameEnd();

            // Cancel countdown if not enough players
            int minPlayers = plugin.getConfig().getInt("game.min-players", 2);
            if (logger.isDebugEnabled()) {
                minPlayers = 1;
            }
            if (playerManager.getPlayerCount() < minPlayers && state == GameState.COUNTDOWN) {
                cancelCountdown();
            }
        }
    }

    /**
     * Adds a player to the game.
     * 
     * @param player The player to add
     */
    public synchronized void addPlayer(@NotNull Player player) {
        // If game has started (past countdown), only allow spectators
        if (state != GameState.WAITING && state != GameState.COUNTDOWN) {
            addSpectator(player);
            return;
        }

        // Delegate to player manager
        boolean added = playerManager.addPlayer(player, state);
        if (!added) {
            return; // Player couldn't be added (arena full, etc.)
        }

        // Set player's food level to full but saturation to zero so hunger starts
        // immediately
        player.setFoodLevel(20);
        player.setSaturation(0.0f);

        // Create barrier cage around the new player's spawn point immediately
        // This prevents them from moving off their spawn platform
        Location spawnLoc = playerManager.getPlayerLocations().get(player.getUniqueId());
        if (spawnLoc != null && spawnLoc.getWorld() != null) {
            createBarrierBoxAroundLocation(spawnLoc);

            // Ensure player is exactly at their spawn point (prevent any exploitation)
            player.teleport(spawnLoc);
            logger.debug("Locked " + InputSanitizer.sanitizeForLogging(player.getName())
                    + " at spawn point with barriers: " + spawnLoc);
        }

        // Broadcast join message
        broadcastMessage(Component.text()
                .append(player.displayName())
                .append(Component.text(" has joined the game! ", NamedTextColor.GREEN))
                .append(Component.text("(" + playerManager.getPlayerCount() + "/" + arena.getSpawnPoints().size() + ")",
                        NamedTextColor.GRAY))
                .build());

        // Countdown logic: start or restart if enough players
        int minPlayers = plugin.getConfig().getInt("game.min-players", 2);
        if (logger.isDebugEnabled()) {
            minPlayers = 1;
        }
        if (playerManager.getPlayerCount() >= minPlayers && state == GameState.WAITING) {
            startCountdown();
            // Don't restart countdown if already in progress
            // This prevents chest filling from restarting when new players join
        }

        // Add player to the nameplate-hiding team if game is active
        scoreboardManager.addPlayerToTeam(player);

        // Start periodic spawn point enforcement if this is the first player
        if (playerManager.getPlayerCount() == 1) {
            startSpawnPointEnforcement();
        }
    }

    /**
     * Eliminates a player from the game.
     * 
     * <p>
     * This method handles the elimination of a player during an active game.
     * The player is moved to spectator mode and their elimination is recorded
     * for placement tracking and statistics.
     * </p>
     * 
     * @param player The player to eliminate
     */
    public void eliminatePlayer(@NotNull Player player) {
        if (eliminationManager.eliminatePlayer(player)) {
            // Check if game should end
            checkGameEnd();
        }
    }

    /**
     * Gets the remaining time in seconds.
     * 
     * @return The remaining time in seconds
     */
    public int getTimeRemaining() {
        return timerManager.getTimeRemaining();
    }

    /**
     * Gets the number of kills for a specific player.
     * 
     * @param playerId The UUID of the player
     * @return The number of kills for the player, or 0 if the player has no kills
     *         recorded
     */
    public int getPlayerKills(@NotNull UUID playerId) {
        return playerManager.getPlayerKills(playerId);
    }

    /**
     * Records damage dealt by a player during the game.
     * 
     * @param playerId The player who dealt damage
     * @param damage   The amount of damage dealt
     */
    public void recordDamageDealt(@NotNull UUID playerId, double damage) {
        eliminationManager.recordDamageDealt(playerId, damage);
    }

    /**
     * Records damage taken by a player during the game.
     * 
     * @param playerId The player who took damage
     * @param damage   The amount of damage taken
     */
    public void recordDamageTaken(@NotNull UUID playerId, double damage) {
        eliminationManager.recordDamageTaken(playerId, damage);
    }

    /**
     * Records a chest opened by a player during the game.
     * 
     * @param playerId The player who opened the chest
     */
    public void recordChestOpened(@NotNull UUID playerId) {
        eliminationManager.recordChestOpened(playerId);
    }

    /**
     * Gets the damage dealt by a player during this game.
     * 
     * @param playerId The player's UUID
     * @return The damage dealt, or 0.0 if not tracked
     */
    public double getPlayerDamageDealt(@NotNull UUID playerId) {
        return eliminationManager.getPlayerDamageDealt(playerId);
    }

    /**
     * Gets the damage taken by a player during this game.
     * 
     * @param playerId The player's UUID
     * @return The damage taken, or 0.0 if not tracked
     */
    public double getPlayerDamageTaken(@NotNull UUID playerId) {
        return eliminationManager.getPlayerDamageTaken(playerId);
    }

    /**
     * Gets the number of chests opened by a player during this game.
     * 
     * @param playerId The player's UUID
     * @return The number of chests opened, or 0 if not tracked
     */
    public int getPlayerChestsOpened(@NotNull UUID playerId) {
        return eliminationManager.getPlayerChestsOpened(playerId);
    }

    // Getters

    public @NotNull Arena getArena() {
        return arena;
    }

    public @NotNull UUID getGameId() {
        return gameId;
    }

    public @NotNull GameState getState() {
        return state;
    }

    /**
     * Gets the current game mode.
     * 
     * @return The current game mode
     */
    public @NotNull GameMode getGameMode() {
        return teamManager.getGameMode();
    }

    public Set<UUID> getPlayers() {
        return playerManager.getPlayers();
    }

    public Set<UUID> getSpectators() {
        return playerManager.getSpectators();
    }

    public synchronized boolean isPvpEnabled() {
        return pvpEnabled;
    }

    public int getPlayerCount() {
        return playerManager.getPlayerCount();
    }

    /**
     * Gets the map of player UUIDs to their assigned spawn locations.
     * 
     * @return An unmodifiable map of player UUIDs to their spawn locations
     */
    public @NotNull Map<UUID, Location> getPlayerLocations() {
        return playerManager.getPlayerLocations();
    }

    public boolean isShuttingDown() {
        return isShuttingDown;
    }

    /**
     * Cleans up all resources used by this game.
     * This should be called when the game ends or is cancelled.
     */
    public void cleanup() {
        if (isShuttingDown) {
            return; // Prevent double cleanup
        }
        isShuttingDown = true;

        // Cancel all active CompletableFuture operations
        for (CompletableFuture<?> future : activeFutures) {
            if (!future.isDone()) {
                future.cancel(true);
            }
        }
        activeFutures.clear();

        // Cancel all active BukkitTask operations
        for (BukkitTask task : activeTasks.values()) {
            if (!task.isCancelled()) {
                task.cancel();
            }
        }
        activeTasks.clear();

        // Remove any remaining barrier blocks
        removeSpawnBarriers();

        // Delegate cleanup to managers
        worldManager.cleanup();
        timerManager.cleanup();
        playerManager.cleanup();
        scoreboardManager.cleanup();
        celebrationManager.cleanup();
        teamManager.cleanup();

        // Remove from game manager
        plugin.getGameManager().removeGame(this);

        logger.info("Cleaned up game: " + gameId);
    }

    /**
     * Gets a cached player object or fetches it from Bukkit.
     * This method is thread-safe.
     */
    private @Nullable Player getCachedPlayer(@NotNull UUID playerId) {
        return playerManager.getCachedPlayer(playerId);
    }

    /**
     * Checks if a block type is allowed to be placed during the game.
     * 
     * @param material The material to check
     * @return True if the block can be placed
     */
    public boolean isBlockAllowed(@NotNull Material material) {
        return arena.isBlockAllowed(material);
    }

    /**
     * Tracks a placed block during the game.
     * 
     * @param location The location where the block was placed
     */
    public void trackPlacedBlock(@NotNull Location location) {
        worldManager.trackPlacedBlock(location);
    }

    /**
     * Starts periodic game end checking during active gameplay.
     * This catches solo scenarios and other edge cases where players aren't
     * eliminated/leaving.
     */
    private void startPeriodicGameEndChecking() {
        // Check every 5 seconds during active gameplay
        final int[] taskIdRef = new int[1];
        BukkitTask gameEndCheckTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            try {
                // Only check if game is still active or in deathmatch
                if (state == GameState.ACTIVE || state == GameState.DEATHMATCH) {
                    checkGameEnd();
                } else {
                    // Game is no longer active, cancel this task
                    BukkitTask task = activeTasks.remove(taskIdRef[0]);
                    if (task != null) {
                        task.cancel();
                    }
                }
            } catch (Exception e) {
                logger.warn("Error in periodic game end checking", e);
            }
        }, 100L, 100L); // Start after 5 seconds, repeat every 5 seconds

        taskIdRef[0] = gameEndCheckTask.getTaskId();
        activeTasks.put(gameEndCheckTask.getTaskId(), gameEndCheckTask);
        logger.debug("Started periodic game end checking (every 5 seconds)");
    }

    public @NotNull DeathMessageManager getDeathMessageManager() {
        return deathMessageManager;
    }

    public @NotNull GameEliminationManager getEliminationManager() {
        return eliminationManager;
    }

    public @NotNull CelebrationManager getCelebrationManager() {
        return celebrationManager;
    }

    public @NotNull GameTeamManager getTeamManager() {
        return teamManager;
    }

    /**
     * Configures the game with the specified game mode.
     * Games now start in WAITING state, so this method just sets the game mode.
     * 
     * @param gameMode The game mode to set for this game
     */
    public void activateGame(@NotNull GameMode gameMode) {
        if (state != GameState.WAITING && state != GameState.INACTIVE) {
            logger.warn("Cannot configure game - current state is " + state);
            return;
        }

        // Set the game mode
        teamManager.setGameMode(gameMode);

        // Ensure we're in WAITING state (games now start in WAITING)
        if (state != GameState.WAITING) {
            state = GameState.WAITING;
            scoreboardManager.setCurrentGameState(state);
            timerManager.setCurrentGameState(state);
        }

        logger.info("Game configured with mode: " + gameMode.getDisplayName());

        // Start broadcasting this game
        if (plugin.getTeamQueueManager() != null) {
            plugin.getTeamQueueManager().updateGameBroadcast(this);
        }
    }

    /**
     * Gets whether this game has been properly set up by a ranked player.
     * 
     * @return true if the game has been set up and is ready for players
     */
    public boolean isSetupComplete() {
        return setupComplete;
    }

    /**
     * Marks this game as properly set up by a ranked player.
     * This allows the game to appear in the Game Browser for regular players.
     */
    public void markSetupComplete() {
        this.setupComplete = true;
        logger.info("Game " + gameId + " marked as setup complete");
    }

    /**
     * Clears the setup complete flag.
     * This hides the game from regular players until setup is completed again.
     */
    public void clearSetupComplete() {
        this.setupComplete = false;
        logger.info("Game " + gameId + " setup cleared");
    }
}
