package net.lumalyte.game;

import net.lumalyte.LumaSG;
import net.lumalyte.arena.Arena;
import net.lumalyte.util.MiniMessageUtils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Team;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Manages scoreboard functionality for a game instance.
 * Handles scoreboard creation, updates, and player visibility.
 */
public class GameScoreboardManager {
    private final @NotNull LumaSG plugin;
    private final @NotNull Arena arena;
    private final @NotNull UUID gameId;
    private final @NotNull GamePlayerManager playerManager;
    private final @NotNull GameTimerManager timerManager;
    
    /** Scoreboard for the game */
    private org.bukkit.scoreboard.Scoreboard gameScoreboard;
    
    /** Scoreboard objective for the game */
    private org.bukkit.scoreboard.Objective objective;
    
    /** Scoreboard team for nameplate control */
    private org.bukkit.scoreboard.Team gameTeam;
    
    /** ID of the scoreboard task (for cancellation) */
    private int scoreboardTaskId = -1;
    
    public GameScoreboardManager(@NotNull LumaSG plugin, @NotNull Arena arena, @NotNull UUID gameId,
                                @NotNull GamePlayerManager playerManager, @NotNull GameTimerManager timerManager) {
        this.plugin = plugin;
        this.arena = arena;
        this.gameId = gameId;
        this.playerManager = playerManager;
        this.timerManager = timerManager;
        
        // Initialize scoreboard if enabled
        if (plugin.getConfig().getBoolean("scoreboard.enabled", true)) {
            initializeScoreboard();
            startScoreboardUpdates();
        }
    }
    
    /**
     * Initializes the game scoreboard.
     */
    private void initializeScoreboard() {
        gameScoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
        objective = gameScoreboard.registerNewObjective(
            "sg_" + gameId.toString().substring(0, 8),
            Criteria.DUMMY,
            MiniMessageUtils.parseMessage(plugin.getConfig().getString("scoreboard.title", "<gold>Survival Games</gold>"))
        );
        objective.setDisplaySlot(org.bukkit.scoreboard.DisplaySlot.SIDEBAR);
    }
    
    /**
     * Starts automatic scoreboard updates.
     */
    private void startScoreboardUpdates() {
        int updateInterval = plugin.getConfig().getInt("scoreboard.update-interval", 40);
        scoreboardTaskId = plugin.getServer().getScheduler().scheduleSyncRepeatingTask(plugin, 
            this::updateScoreboard, 
            20L, // Initial delay (1 second)
            updateInterval); // Update interval (default 40 ticks = 2 seconds)
    }
    
    /**
     * Creates the nameplate-hiding team.
     */
    public void createNameplateTeam() {
        if (gameScoreboard != null) {
            gameTeam = gameScoreboard.registerNewTeam("sg_game_" + gameId.toString().substring(0, 8));
            gameTeam.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
            
            // Add all players to the team
            for (UUID playerId : playerManager.getPlayers()) {
                Player player = playerManager.getCachedPlayer(playerId);
                if (player != null) {
                    gameTeam.addEntry(player.getName());
                }
            }
        }
    }
    
    /**
     * Adds a player to the nameplate-hiding team.
     */
    public void addPlayerToTeam(@NotNull Player player) {
        if (gameTeam != null) {
            gameTeam.addEntry(player.getName());
        }
    }
    
    /**
     * Removes a player from the nameplate-hiding team.
     */
    public void removePlayerFromTeam(@NotNull Player player) {
        if (gameTeam != null) {
            gameTeam.removeEntry(player.getName());
        }
    }
    
    /**
     * Updates the scoreboard for all players in the game.
     */
    private void updateScoreboard() {
        // Skip if scoreboard is disabled in config
        if (!plugin.getConfig().getBoolean("scoreboard.enabled", true)) {
            return;
        }
        
        if (!ensureScoreboardInitialized()) {
            return;
        }
        
        clearExistingScores();
        updateScoreboardContent();
        updateScoreboardVisibility();
    }
    
    /**
     * Ensures the scoreboard is properly initialized.
     */
    private boolean ensureScoreboardInitialized() {
        if (gameScoreboard == null || objective == null) {
            initializeScoreboard();
            return gameScoreboard != null && objective != null;
        }
        return true;
    }
    
    /**
     * Clears existing scores from the scoreboard.
     */
    private void clearExistingScores() {
        for (String entry : new HashSet<>(gameScoreboard.getEntries())) {
            gameScoreboard.resetScores(entry);
        }
    }
    
    /**
     * Updates the content displayed on the scoreboard.
     */
    private void updateScoreboardContent() {
        // Get regular scoreboard lines from config
        List<String> lines = plugin.getConfig().getStringList("scoreboard.lines");
        
        // Process placeholders in lines
        Map<String, String> placeholders = createPlaceholders();
        
        // Create display lines with deathmatch content if applicable
        List<String> displayLines = createDisplayLines(lines);
        
        // Add scores to the scoreboard
        addScoresToObjective(displayLines, placeholders);
    }
    
    /**
     * Creates the final list of lines to display on the scoreboard.
     */
    private @NotNull List<String> createDisplayLines(@NotNull List<String> baseLines) {
        List<String> displayLines = new ArrayList<>(baseLines);
        
        // If in deathmatch, add the deathmatch lines
        if (getCurrentGameState() == GameState.DEATHMATCH) {
            List<String> deathmatchLines = plugin.getConfig().getStringList("scoreboard.deathmatch-lines");
            if (!deathmatchLines.isEmpty()) {
                displayLines.addAll(deathmatchLines);
            }
        }
        
        return displayLines;
    }
    
    /**
     * Adds processed lines as scores to the objective.
     */
    private void addScoresToObjective(@NotNull List<String> displayLines, @NotNull Map<String, String> placeholders) {
        int score = displayLines.size();
        for (String line : displayLines) {
            // First replace placeholders, then parse MiniMessage formatting
            String processedLine = MiniMessageUtils.processPlaceholders(line, placeholders);
            // Convert to legacy format for scoreboard compatibility
            String legacyLine = MiniMessageUtils.toLegacy(processedLine);
            objective.getScore(legacyLine).setScore(score--);
        }
    }
    
    /**
     * Creates placeholders for scoreboard text.
     */
    private @NotNull Map<String, String> createPlaceholders() {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("players", String.valueOf(playerManager.getPlayerCount()));
        placeholders.put("max_players", String.valueOf(arena.getSpawnPoints().size()));
        placeholders.put("arena", arena.getName());
        
        // Format time remaining
        int timeRemaining = timerManager.getTimeRemaining();
        int minutes = timeRemaining / 60;
        int seconds = timeRemaining % 60;
        String timeStr = String.format("%02d:%02d", minutes, seconds);
        placeholders.put("time", timeStr);
        
        return placeholders;
    }
    
    /**
     * Updates which players can see the scoreboard.
     */
    private void updateScoreboardVisibility() {
        // Skip if scoreboard is disabled in config
        if (!plugin.getConfig().getBoolean("scoreboard.enabled", true) || gameScoreboard == null) {
            return;
        }
        
        // Show scoreboard to all players in the game
        for (UUID playerId : playerManager.getPlayers()) {
            Player player = playerManager.getCachedPlayer(playerId);
            if (player != null && player.isOnline()) {
                player.setScoreboard(gameScoreboard);
            }
        }
        
        // Show scoreboard to spectators if configured
        if (plugin.getConfig().getBoolean("spectator.enabled", true)) {
            for (UUID spectatorId : playerManager.getSpectators()) {
                Player spectator = playerManager.getCachedPlayer(spectatorId);
                if (spectator != null && spectator.isOnline()) {
                    spectator.setScoreboard(gameScoreboard);
                }
            }
        }
    }
    
    /**
     * Forces the game scoreboard to be displayed for a specific player.
     */
    public void forceScoreboardUpdate(@NotNull Player player) {
        // Skip if scoreboard is disabled in config
        if (!plugin.getConfig().getBoolean("scoreboard.enabled", true) || gameScoreboard == null) {
            return;
        }
        
        // First reset to main scoreboard to clear any other plugin's scoreboard
        player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        
        // Then set to our scoreboard
        player.setScoreboard(gameScoreboard);
    }
    
    /**
     * Removes a specific player from the game scoreboard and resets them to server default.
     */
    public void removePlayerFromScoreboard(@NotNull Player player) {
        if (player.isOnline()) {
            // Remove from team if they're in it
            removePlayerFromTeam(player);
            
            // Reset to server default scoreboard
            player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }
    }
    
    /**
     * Gets the current game state.
     */
    private GameState getCurrentGameState() {
        return currentGameState;
    }
    
    /**
     * Sets the current game state for scoreboard updates.
     */
    private GameState currentGameState = GameState.WAITING;
    
    public void setCurrentGameState(@NotNull GameState state) {
        this.currentGameState = state;
    }
    
    /**
     * Cleans up all scoreboard resources.
     */
    public void cleanup() {
        // Cancel scoreboard task if it's still running
        if (scoreboardTaskId != -1) {
            plugin.getServer().getScheduler().cancelTask(scoreboardTaskId);
            scoreboardTaskId = -1;
        }
        
        // Clean up the team
        if (gameTeam != null) {
            gameTeam.unregister();
            gameTeam = null;
        }
        
        // Reset all players' scoreboards to server default
        for (UUID playerId : playerManager.getPlayers()) {
            Player player = playerManager.getCachedPlayer(playerId);
            if (player != null && player.isOnline()) {
                player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            }
        }
        
        for (UUID spectatorId : playerManager.getSpectators()) {
            Player spectator = playerManager.getCachedPlayer(spectatorId);
            if (spectator != null && spectator.isOnline()) {
                spectator.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            }
        }
    }
    
    // Getters
    public org.bukkit.scoreboard.Scoreboard getGameScoreboard() {
        return gameScoreboard;
    }
    
    public Objective getObjective() {
        return objective;
    }
    
    public Team getGameTeam() {
        return gameTeam;
    }
} 