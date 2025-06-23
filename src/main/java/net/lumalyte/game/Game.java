package net.lumalyte.game;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import net.lumalyte.LumaSG;
import net.lumalyte.arena.Arena;
import net.lumalyte.util.DebugLogger;
import net.lumalyte.util.MiniMessageUtils;
import net.lumalyte.statistics.StatType;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scoreboard.Criteria;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Represents a single instance of a Survival Games match.
 * 
 * <p>This class manages the complete lifecycle of a Survival Games game, including
 * player management, game state transitions, countdowns, PvP phases, and victory
 * conditions. Each game instance is independent and manages its own players,
 * timers, and game logic.</p>
 * 
 * <p>The game follows a specific state flow: WAITING → COUNTDOWN → GRACE_PERIOD → 
 * ACTIVE → DEATHMATCH → FINISHED. Each state has different rules and behaviors.</p>
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
    
    // Component managers for different aspects of the game
    private final @NotNull GamePlayerManager playerManager;
    private final @NotNull GameTimerManager timerManager;
    private final @NotNull GameWorldManager worldManager;
    private final @NotNull GameScoreboardManager scoreboardManager;
    private final @NotNull GameCelebrationManager celebrationManager;
    
    // Memory management: Track async operations for cleanup
    private final @NotNull Set<CompletableFuture<?>> activeFutures = ConcurrentHashMap.newKeySet();
    
    /**
     * Constructs a new Game instance in the specified arena.
     * 
     * <p>The game starts in WAITING state and must be manually started by calling
     * startCountdown() when enough players have joined.</p>
     * 
     * @param plugin The plugin instance
     * @param arena The arena where the game will be played
     * 
     * @throws IllegalArgumentException if plugin or arena is null
     */
    public Game(@NotNull LumaSG plugin, @NotNull Arena arena) {
        if (plugin == null) {
            throw new IllegalArgumentException("Plugin cannot be null");
        }
        if (arena == null) {
            throw new IllegalArgumentException("Arena cannot be null");
        }
        
        this.plugin = plugin;
        this.arena = arena;
        this.gameId = UUID.randomUUID();
        this.state = GameState.WAITING;
        
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
        this.celebrationManager = new GameCelebrationManager(plugin, playerManager);
        
        logger.info("Created new game with ID: " + gameId + " in arena: " + arena.getName());
    }
    
    /**
     * Gets the set of players who disconnected during the game.
     * 
     * <p>This is used to track players who left during gameplay and may need
     * special handling when they rejoin.</p>
     * 
     * @return An unmodifiable set of disconnected player UUIDs
     */
    public @NotNull Set<UUID> getDisconnectedPlayers() {
        return playerManager.getDisconnectedPlayers();
    }
    
    /**
     * Checks if the game is currently in grace period.
     * 
     * <p>During grace period, PvP is disabled and players have time to prepare
     * before combat begins.</p>
     * 
     * @return true if the game is in grace period, false otherwise
     */
    public boolean isGracePeriod() {
        return isGracePeriod;
    }
    
    /**
     * Adds a player as a spectator to the game.
     * 
     * <p>Spectators can watch the game but cannot participate in combat or
     * interact with the game world. They are teleported to the spectator spawn
     * point if one is configured.</p>
     * 
     * @param player The player to add as a spectator
     * 
     * @throws IllegalArgumentException if player is null
     */
    public void addSpectator(@NotNull Player player) {
        if (player == null) {
            throw new IllegalArgumentException("Player cannot be null");
        }
        
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
     * <p>This method allows starting the countdown with a specific duration,
     * overriding the default countdown time from configuration.</p>
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
    }
    
    /**
     * Starts the countdown using the default duration from configuration.
     * 
     * <p>This method initiates the countdown phase, transitioning the game from
     * WAITING to COUNTDOWN state. During countdown, players see title messages
     * showing the remaining time until the game starts.</p>
     */
    public void startCountdown() {
        if (state != GameState.WAITING) {
            return; // Can only start countdown from waiting state
        }
        
        // Tell players that chests are being filled
        broadcastMessage(Component.text()
            .append(Component.text("Preparing the arena...", NamedTextColor.GOLD))
            .build());
        
        // Fill all chests in the arena with random loot asynchronously before starting countdown
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
                timerManager.startCountdown(this::startGame);
            });
        });
    }
    
    /**
     * Cancels the countdown and returns the game to waiting state.
     * 
     * <p>This method is typically called when there are not enough players
     * to start the game or when the game is manually cancelled.</p>
     */
    public void cancelCountdown() {
        if (state == GameState.COUNTDOWN) {
            state = GameState.WAITING;
            scoreboardManager.setCurrentGameState(state);

            // Set up world settings and borders
            worldManager.setupWorld();
            
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
        
        // Ensure all players are in adventure mode
        for (UUID playerId : playerManager.getPlayers()) {
            Player player = playerManager.getCachedPlayer(playerId);
            if (player != null) {
                player.setGameMode(GameMode.ADVENTURE);
            }
        }
        
        // Start grace period immediately - chests are already filled
        startGracePeriod();
        
        // Schedule deathmatch and game end using timer manager
        timerManager.scheduleDeathmatch(this::startDeathmatch, () -> endGame(null));
        
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
     * This is done before the countdown starts to ensure all chests are ready before players can access them.
     * 
     * @return A CompletableFuture that completes when all chests are filled
     */
    private CompletableFuture<Void> fillArenaChestsAsync() {
        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
            logger.debug("Starting to fill chests in arena: " + arena.getName());
            
            // First, scan for chests if none are registered
            if (arena.getChestLocations().isEmpty()) {
                logger.debug("No chest locations found, scanning arena for chests...");
                // Run on main thread since scanning involves world operations
                try {
                    CompletableFuture<Integer> scanFuture = new CompletableFuture<>();
                    activeFutures.add(scanFuture);
                    
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        int count = arena.scanForChests();
                        logger.debug("Found " + count + " chests in arena " + arena.getName());
                        scanFuture.complete(count);
                    });
                    // Wait for scan to complete
                    int chestsFound = scanFuture.get();
                    activeFutures.remove(scanFuture);
                    
                    if (chestsFound == 0) {
                        logger.warn("No chests found in arena " + arena.getName() + " after scanning!");
                    }
                } catch (Exception e) {
                    logger.severe("Error scanning for chests", e);
                }
            }
            
            List<Location> chestLocations = arena.getChestLocations();
            if (chestLocations.isEmpty()) {
                logger.warn("No chests found in arena " + arena.getName() + " - chests will not be filled!");
                return;
            }
            
            logger.debug("Found " + chestLocations.size() + " chests to fill in arena " + arena.getName());
            
            // Check if chest items are loaded
            if (plugin.getChestManager().getChestItems().isEmpty()) {
                logger.warn("No chest items loaded! Attempting to load chest items...");
                try {
                    CompletableFuture<Void> loadFuture = plugin.getChestManager().loadChestItems();
                    activeFutures.add(loadFuture);
                    loadFuture.get(); // Wait for completion
                    activeFutures.remove(loadFuture);
                    logger.debug("Chest items loaded successfully");
                } catch (Exception e) {
                    logger.severe("Failed to load chest items", e);
                    return;
                }
            }
            
            Random random = new Random();
            String[] tiers = {"common", "uncommon", "rare"};
            int[] weights = {70, 25, 5}; // 70% common, 25% uncommon, 5% rare
            
            int filledChests = 0;
            int failedChests = 0;
            
            // Process chests in batches to avoid overwhelming the server
            List<CompletableFuture<Boolean>> futures = new ArrayList<>();
            
            for (Location chestLoc : chestLocations) {
                // Select a random tier based on weights
                int totalWeight = 0;
                for (int weight : weights) {
                    totalWeight += weight;
                }
                
                int randomValue = random.nextInt(totalWeight);
                String selectedTier = tiers[0]; // Default to common
                
                int currentWeight = 0;
                for (int i = 0; i < weights.length; i++) {
                    currentWeight += weights[i];
                    if (randomValue < currentWeight) {
                        selectedTier = tiers[i];
                        break;
                    }
                }
                
                final String tier = selectedTier;
                logger.debug("Attempting to fill chest at " + chestLoc + " with tier: " + tier);
                
                // Fill the chest with items from the selected tier on the main thread
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
                
                futures.add(chestFuture);
            }
            
            // Wait for all chests to be filled
            for (CompletableFuture<Boolean> chestFuture : futures) {
                try {
                    if (chestFuture.get()) {
                        filledChests++;
                    } else {
                        failedChests++;
                    }
                } catch (Exception e) {
                    logger.severe("Error filling chest", e);
                    failedChests++;
                } finally {
                    activeFutures.remove(chestFuture);
                }
            }
            
            logger.debug("Chest filling completed for arena " + arena.getName() + 
                " - Filled: " + filledChests + ", Failed: " + failedChests + ", Total: " + chestLocations.size());
        });
        
        activeFutures.add(future);
        future.whenComplete((result, throwable) -> activeFutures.remove(future));
        
        return future;
    }
    
    /**
     * Starts the grace period.
     */
    private void startGracePeriod() {
        isGracePeriod = true;
        pvpEnabled = false;
        
        // Start grace period using timer manager
        timerManager.startGracePeriod(this::endGracePeriod);
    }
    
    /**
     * Ends the grace period.
     */
    private void endGracePeriod() {
        isGracePeriod = false;
        pvpEnabled = true;
        state = GameState.ACTIVE; // Transition to ACTIVE state
        scoreboardManager.setCurrentGameState(state);
        
        Title title = Title.title(
            Component.text("Grace Period Ended!", NamedTextColor.RED, TextDecoration.BOLD),
            Component.text("PvP is now enabled!", NamedTextColor.YELLOW),
            Title.Times.times(Duration.ofMillis(500), Duration.ofMillis(3000), Duration.ofMillis(500))
        );
        
        for (UUID playerId : playerManager.getPlayers()) {
            Player player = playerManager.getCachedPlayer(playerId);
            if (player != null) {
                player.showTitle(title);
            }
        }
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
            Title.Times.times(Duration.ofMillis(500), Duration.ofMillis(3000), Duration.ofMillis(500))
        );
        
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
        if (state != GameState.ACTIVE && state != GameState.DEATHMATCH) {
            logger.debug("Game end check skipped - game not in ACTIVE or DEATHMATCH state (current state: " + state + ")");
            return;
        }
        
        int playerCount = playerManager.getPlayerCount();
        logger.debug("Checking game end conditions - Active players: " + playerCount);
        
        // End game if there's only one player left
        if (playerCount <= 1) {
            logger.debug("Ending game - only " + playerCount + " player(s) remaining");
            endGame(null);
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
        isShuttingDown = true;
        state = GameState.FINISHED;
        scoreboardManager.setCurrentGameState(state);
        
        // Disable PvP immediately
        pvpEnabled = false;
        isGracePeriod = false;
        
        // Cancel all scheduled tasks using timer manager
        timerManager.cleanup();
        scoreboardManager.cleanup();
        
        // Set all players and spectators to adventure mode for safety during celebration
        for (UUID playerId : playerManager.getPlayers()) {
            Player player = playerManager.getCachedPlayer(playerId);
            if (player != null && player.isOnline()) {
                player.setGameMode(GameMode.ADVENTURE);
                
                // Clear inventory immediately to prevent items from being kept
                player.getInventory().clear();
                player.getInventory().setArmorContents(null);
            }
        }
        for (UUID spectatorId : playerManager.getSpectators()) {
            Player spectator = playerManager.getCachedPlayer(spectatorId);
            if (spectator != null && spectator.isOnline()) {
                spectator.setGameMode(GameMode.ADVENTURE);
                
                // Clear inventory for spectators too
                spectator.getInventory().clear();
                spectator.getInventory().setArmorContents(null);
            }
        }
        
        // Restore world settings using world manager
        worldManager.restoreWorld();
        
        // Record game statistics if enabled
        if (plugin.getConfig().getBoolean("statistics.enabled", true)) {
            recordGameStatistics();
        }
        
        // CELEBRATION PHASE - Announce winner and show fireworks
        if (playerManager.getPlayerCount() == 1) {
            UUID winnerId = playerManager.getPlayers().iterator().next();
            Player winner = playerManager.getCachedPlayer(winnerId);
            if (winner != null) {
                celebrationManager.celebrateWinner(winner);
            }
        } else {
            celebrationManager.celebrateNoWinner();
        }
        
        // Wait for celebration to finish, then teleport everyone out
        // This gives time for fireworks and title to be seen
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
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
     * @param player The player to remove
     * @param isDisconnect Whether the player disconnected
     */
    public synchronized void removePlayer(Player player, boolean isDisconnect) {
        if (!playerManager.getPlayers().contains(player.getUniqueId()) && 
            !playerManager.getSpectators().contains(player.getUniqueId())) {
            return;
        }
        
        // Remove player from scoreboard first
        scoreboardManager.removePlayerFromScoreboard(player);
        
        // Delegate to player manager
        playerManager.removePlayer(player, isDisconnect, isShuttingDown);
        
        // Only broadcast leave message and check game end if game isn't ending
        if (!isShuttingDown) {
            String leaveMsg = plugin.getConfig().getString("messages.player-leave", "<gray><player> <yellow>has left the game! <gray>(<current>/<max>)");
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
        
        // Broadcast join message
        broadcastMessage(Component.text()
            .append(player.displayName())
            .append(Component.text(" has joined the game! ", NamedTextColor.GREEN))
            .append(Component.text("(" + playerManager.getPlayerCount() + "/" + arena.getSpawnPoints().size() + ")", NamedTextColor.GRAY))
            .build());

        // Countdown logic: start or restart if enough players
        int minPlayers = plugin.getConfig().getInt("game.min-players", 2);
        if (logger.isDebugEnabled()) {
            minPlayers = 1;
        }
        if (playerManager.getPlayerCount() >= minPlayers) {
            if (state == GameState.WAITING) {
                startCountdown();
            }
            // Don't restart countdown if already in progress
            // This prevents chest filling from restarting when new players join
        }
        
        // Add player to the nameplate-hiding team if game is active
        scoreboardManager.addPlayerToTeam(player);
    }

    /**
     * Eliminates a player from the game.
     * 
     * @param player The player to eliminate
     */
    public void eliminatePlayer(@NotNull Player player) {
        if (playerManager.getPlayers().contains(player.getUniqueId())) {
            // Delegate to player manager
            playerManager.eliminatePlayer(player);
            
            // Broadcast elimination message
            broadcastMessage(Component.text()
                .append(player.displayName())
                .append(Component.text(" has been eliminated!", NamedTextColor.RED))
                .build());
            
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
     * Updates the scoreboard for all players in the game.
     */
    private void updateScoreboard() {
        scoreboardManager.setCurrentGameState(state);
        // Scoreboard is automatically updated by the manager
    }
    
    /**
     * Updates which players can see the scoreboard.
     */
    private void updateScoreboardVisibility() {
        // Visibility is automatically handled by the scoreboard manager
    }
    
    /**
     * Forces the game scoreboard to be displayed for a specific player.
     * 
     * @param player The player to update the scoreboard for
     */
    public void forceScoreboardUpdate(@NotNull Player player) {
        scoreboardManager.forceScoreboardUpdate(player);
    }
    
    /**
     * Gets the number of kills for a specific player.
     * 
     * @param playerId The UUID of the player
     * @return The number of kills for the player, or 0 if the player has no kills recorded
     */
    public int getPlayerKills(@NotNull UUID playerId) {
        return playerManager.getPlayerKills(playerId);
    }
    
    // Getters
    
    public Arena getArena() {
        return arena;
    }
    
    public UUID getGameId() {
        return gameId;
    }
    
    public GameState getState() {
        return state;
    }
    
    public Set<UUID> getPlayers() {
        return playerManager.getPlayers();
    }
    
    public Set<UUID> getSpectators() {
        return playerManager.getSpectators();
    }
    
    public boolean isPvpEnabled() {
        return pvpEnabled;
    }
    
    public int getPlayerCount() {
        return playerManager.getPlayerCount();
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
        
        // Delegate cleanup to managers
        worldManager.cleanup();
        timerManager.cleanup();
        playerManager.cleanup();
        scoreboardManager.cleanup();
        celebrationManager.cleanup();
        
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
     * Updates the player cache by removing offline players.
     * This method is thread-safe.
     */
    private void updatePlayerCache() {
        // This is now handled by the player manager
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
     * Records game statistics for all players who participated.
     */
    private void recordGameStatistics() {
        try {
            // Get all players (both active and spectators who were eliminated)
            Set<UUID> allParticipants = new HashSet<>(playerManager.getPlayers());
            allParticipants.addAll(playerManager.getSpectators());
            allParticipants.addAll(playerManager.getDisconnectedPlayers());
            
            // Calculate game duration (approximate)
            long gameTimeSeconds = Duration.between(
                java.time.Instant.now().minusSeconds(timerManager.getTimeRemaining()),
                java.time.Instant.now()
            ).getSeconds();
            
            // Determine placements
            List<UUID> finalRankings = new ArrayList<>();
            
            // Winner(s) first
            finalRankings.addAll(playerManager.getPlayers());
            
            // Then spectators (eliminated players) - these would need to be ordered by elimination time
            // For now, we'll just add them in arbitrary order
            finalRankings.addAll(playerManager.getSpectators());
            
            // Then disconnected players last
            finalRankings.addAll(playerManager.getDisconnectedPlayers());
            
            // Record statistics for each player
            for (int i = 0; i < finalRankings.size(); i++) {
                UUID playerId = finalRankings.get(i);
                int placement = i + 1;
                int kills = playerManager.getPlayerKills(playerId);
                
                // For now, we don't have damage tracking in the game itself
                // These would need to be tracked during the game if needed
                double damageDealt = 0.0;
                double damageTaken = 0.0;
                int chestsOpened = 0; // This would need to be tracked per player
                
                plugin.getStatisticsManager().recordGameResult(
                    playerId, 
                    placement, 
                    kills, 
                    damageDealt, 
                    damageTaken, 
                    chestsOpened, 
                    gameTimeSeconds
                );
            }
            
            logger.debug("Recorded statistics for " + finalRankings.size() + " players in game " + gameId);
        } catch (Exception e) {
            logger.warn("Failed to record game statistics for game " + gameId, e);
        }
    }
} 
