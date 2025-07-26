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
import org.bukkit.entity.Player;
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
import net.lumalyte.lumasg.util.messaging.MiniMessageUtils;

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

    // Game state, PvP status, and grace period are now handled by GameStateHelper

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

    // Barrier management is now handled by GameBarrierHelper
    private final @NotNull GameBarrierHelper barrierHelper;

    // State management is now handled by GameStateHelper
    private final @NotNull GameStateHelper stateHelper;

    // Event broadcasting is now handled by GameEventHelper
    private final @NotNull GameEventHelper eventHelper;

    // Cleanup and shutdown is now handled by GameCleanupHelper
    private final @NotNull GameCleanupHelper cleanupHelper;

    // Spawn enforcement is now handled by GameSpawnHelper
    private final @NotNull GameSpawnHelper spawnHelper;

    // Inventory management is now handled by GamePlayerManager

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
        // Initialize contextual logger for this game
        this.logger = plugin.getDebugLogger().forContext("Game-" + arena.getName());

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

        // Initialize helper classes for implementation details
        this.barrierHelper = new GameBarrierHelper(plugin, arena, worldManager, gameId.toString());
        this.stateHelper = new GameStateHelper(plugin, gameId.toString());
        this.eventHelper = new GameEventHelper(plugin, playerManager, gameId.toString());
        this.cleanupHelper = new GameCleanupHelper(plugin, gameId.toString(), playerManager,
                timerManager, scoreboardManager, worldManager,
                eliminationManager, celebrationManager, eventHelper);
        this.spawnHelper = new GameSpawnHelper(plugin, gameId.toString(), playerManager, stateHelper);

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
        return stateHelper.isGracePeriod();
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

        if (!stateHelper.canTransitionTo(GameState.COUNTDOWN)) {
            return; // Can only start countdown from waiting state
        }

        timerManager.startCountdown(seconds, this::startGame);
        stateHelper.transitionTo(GameState.COUNTDOWN);
        scoreboardManager.setCurrentGameState(stateHelper.getCurrentState());
        timerManager.setCurrentGameState(stateHelper.getCurrentState());
    }

    // Barrier management methods moved to GameBarrierHelper

    /**
     * Starts periodic enforcement of spawn point restrictions during WAITING and
     * COUNTDOWN states.
     */
    private void startSpawnPointEnforcement() {
        spawnHelper.startSpawnPointEnforcement();
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
        if (!stateHelper.canTransitionTo(GameState.COUNTDOWN)) {
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
                stateHelper.transitionTo(GameState.COUNTDOWN);
                scoreboardManager.setCurrentGameState(stateHelper.getCurrentState());
                timerManager.setCurrentGameState(stateHelper.getCurrentState());

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
        if (stateHelper.getCurrentState() == GameState.COUNTDOWN) {
            stateHelper.transitionTo(GameState.WAITING);
            scoreboardManager.setCurrentGameState(stateHelper.getCurrentState());
            timerManager.setCurrentGameState(stateHelper.getCurrentState());

            // Set up world settings and borders
            worldManager.setupWorld();

            // Remove spawn barriers if countdown was cancelled, then recreate them for
            // WAITING state
            barrierHelper.removeAllBarriers();

            // Recreate barriers for all players since we're back in WAITING state
            // This ensures players remain locked at their spawn points
            for (UUID playerId : playerManager.getPlayers()) {
                Location spawnLoc = playerManager.getPlayerLocations().get(playerId);
                if (spawnLoc != null && spawnLoc.getWorld() != null) {
                    barrierHelper.createBarrierBoxAroundLocation(spawnLoc);
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
        if (!stateHelper.canTransitionTo(GameState.GRACE_PERIOD)) {
            return;
        }

        stateHelper.transitionTo(GameState.GRACE_PERIOD);
        scoreboardManager.setCurrentGameState(stateHelper.getCurrentState());
        timerManager.setCurrentGameState(stateHelper.getCurrentState());

        // Remove barriers around spawn points to allow players to move
        worldManager.removeSpawnBarriers();

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
        logger.debug("Starting to fill chests in arena: " + arena.getName() + " using GameChestFiller");

        // Use the GameChestFiller to fill chests asynchronously
        CompletableFuture<Void> future = worldManager.getChestManager().fillArenaChestsAsync();

        // Track the future for cleanup
        activeFutures.add(future);
        future.whenComplete((result, throwable) -> {
            activeFutures.remove(future);
            if (throwable != null) {
                logger.warn("Error filling chests", throwable);
            } else {
                int filledCount = worldManager.getChestManager().getFilledChestCount();
                logger.info("Successfully filled " + filledCount + " chests in arena " + arena.getName());
            }
        });

        return future;
    }

    // Chest filling and tier selection is now handled by GameChestFiller

    /**
     * Starts the grace period.
     */
    private void startGracePeriod() {
        logger.debug("Starting grace period for game: " + gameId);

        // Grace period is handled by the state helper during state transition

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
        barrierHelper.removeAllBarriers();

        // Start grace period using timer manager
        timerManager.startGracePeriod(this::endGracePeriod);

        logger.debug("Grace period started");
    }

    /**
     * Ends the grace period and enables PvP.
     */
    private void endGracePeriod() {
        logger.debug("Ending grace period for game: " + gameId);

        // Enable PvP - handled by state helper
        stateHelper.enablePvP();

        // Update game state
        stateHelper.transitionTo(GameState.ACTIVE);
        scoreboardManager.setCurrentGameState(stateHelper.getCurrentState());
        timerManager.setCurrentGameState(stateHelper.getCurrentState());

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
        if (!stateHelper.isGracePeriod()) {
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
        if (!stateHelper.canTransitionTo(GameState.DEATHMATCH)) {
            return;
        }

        stateHelper.transitionTo(GameState.DEATHMATCH);
        scoreboardManager.setCurrentGameState(stateHelper.getCurrentState());
        timerManager.setCurrentGameState(stateHelper.getCurrentState());

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
        if (stateHelper.getCurrentState() == GameState.FINISHED || isShuttingDown) {
            logger.debug("Game end check skipped - game already finished or shutting down");
            return;
        }

        int playerCount = playerManager.getPlayerCount();
        logger.debug("Checking game end conditions - Active players: " + playerCount + ", Game state: "
                + stateHelper.getCurrentState());

        // Pre-cache player skins when 3 players remain for faster winner celebration
        GameState currentState = stateHelper.getCurrentState();
        if (playerCount == 3 && (currentState == GameState.ACTIVE || currentState == GameState.DEATHMATCH)) {
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
                    "Ending game immediately - only " + playerCount + " player(s) remaining (state: "
                            + stateHelper.getCurrentState() + ")");
            endGame(null);
            return;
        }

        // In WAITING state, check if we have enough players to continue
        if (stateHelper.getCurrentState() == GameState.WAITING) {
            int minPlayers = plugin.getConfig().getInt("game.min-players", 2);
            if (plugin.getDebugLogger().isDebugEnabled()) {
                minPlayers = 1;
            }
            if (playerCount < minPlayers) {
                logger.debug("Not enough players to start game - " + playerCount + "/" + minPlayers);
                // Don't end the game, just wait for more players
                return;
            }
        }

        // In COUNTDOWN state, if we drop below minimum players, cancel countdown
        if (stateHelper.getCurrentState() == GameState.COUNTDOWN) {
            int minPlayers = plugin.getConfig().getInt("game.min-players", 2);
            if (plugin.getDebugLogger().isDebugEnabled()) {
                minPlayers = 1;
            }
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
        if (stateHelper.getCurrentState() == GameState.FINISHED || isShuttingDown) {
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
        stateHelper.transitionTo(GameState.FINISHED);
        scoreboardManager.setCurrentGameState(stateHelper.getCurrentState());
        timerManager.setCurrentGameState(stateHelper.getCurrentState());
    }

    /**
     * Disables PvP and grace period for the end game phase.
     */
    private void disablePvpAndGracePeriod() {
        stateHelper.disablePvP();
    }

    /**
     * Cleans up timer and scoreboard managers.
     */
    private void cleanupManagers() {
        cleanupHelper.cleanupManagers();
    }

    // Player inventory management is now handled by GamePlayerManager
    // These methods have been removed to eliminate duplication

    /**
     * Prepares all players and spectators for end game by setting them to adventure
     * mode and clearing their inventories.
     */
    private void preparePlayersForEndGame() {
        cleanupHelper.preparePlayersForEndGame();
    }

    /**
     * Records game statistics if statistics are enabled in the configuration.
     */
    private void recordStatisticsIfEnabled() {
        if (gameStartTime != null) {
            cleanupHelper.recordStatisticsIfEnabled(gameStartTime);
        }
    }

    /**
     * Handles the celebration phase by determining if there's a winner and
     * triggering the appropriate celebration.
     */
    private void handleCelebration() {
        cleanupHelper.handleCelebration();
    }

    // Celebration is now handled by GameCleanupHelper

    /**
     * Schedules the final cleanup tasks to run after the celebration period.
     */
    private void scheduleGameCleanup() {
        cleanupHelper.scheduleGameCleanup(this);
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
        GameState currentState = stateHelper.getCurrentState();
        if (playerSpawnLoc != null && (currentState == GameState.WAITING || currentState == GameState.COUNTDOWN)) {
            barrierHelper.removeBarriersAroundLocation(playerSpawnLoc);
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
            if (plugin.getDebugLogger().isDebugEnabled()) {
                minPlayers = 1;
            }
            if (playerManager.getPlayerCount() < minPlayers && stateHelper.getCurrentState() == GameState.COUNTDOWN) {
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
        if (!stateHelper.canAddPlayer()) {
            addSpectator(player);
            return;
        }

        // Delegate to player manager
        boolean added = playerManager.addPlayer(player, stateHelper.getCurrentState());
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
            barrierHelper.createBarrierBoxAroundLocation(spawnLoc);

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
        if (plugin.getDebugLogger().isDebugEnabled()) {
            minPlayers = 1;
        }
        if (playerManager.getPlayerCount() >= minPlayers && stateHelper.getCurrentState() == GameState.WAITING) {
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
        return stateHelper.getCurrentState();
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
        return stateHelper.isPvpEnabled();
    }

    /**
     * Gets the current game state.
     * 
     * @return The current game state
     */
    public @NotNull GameState getCurrentState() {
        return stateHelper.getCurrentState();
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
        barrierHelper.cleanup();

        // Stop spawn enforcement
        spawnHelper.cleanup();

        // Perform immediate cleanup using helper
        cleanupHelper.performImmediateCleanup(this);
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
                GameState currentState = stateHelper.getCurrentState();
                if (currentState == GameState.ACTIVE || currentState == GameState.DEATHMATCH) {
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
     * Gets the world manager for this game.
     * 
     * @return The world manager
     */
    public @NotNull GameWorldManager getWorldManager() {
        return worldManager;
    }

    /**
     * Configures the game with the specified game mode.
     * Games now start in WAITING state, so this method just sets the game mode.
     * 
     * @param gameMode The game mode to set for this game
     */
    public void activateGame(@NotNull GameMode gameMode) {
        if (stateHelper.getCurrentState() != GameState.WAITING) {
            logger.warn("Cannot configure game - current state is " + stateHelper.getCurrentState());
            return;
        }

        // Set the game mode
        teamManager.setGameMode(gameMode);

        // Ensure we're in WAITING state (games now start in WAITING)
        if (stateHelper.getCurrentState() != GameState.WAITING) {
            stateHelper.transitionTo(GameState.WAITING);
            scoreboardManager.setCurrentGameState(stateHelper.getCurrentState());
            timerManager.setCurrentGameState(stateHelper.getCurrentState());
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
