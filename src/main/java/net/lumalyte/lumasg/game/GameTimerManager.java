package net.lumalyte.lumasg.game;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import net.lumalyte.lumasg.LumaSG;
import net.lumalyte.lumasg.util.core.DebugLogger;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages all timing aspects of a game instance.
 * Handles countdowns, game timers, and scheduled tasks.
 */
public class GameTimerManager {
    private final @NotNull LumaSG plugin;
    private final @NotNull GamePlayerManager playerManager;
    
    /** The debug logger instance for this timer manager */
    private final @NotNull DebugLogger.ContextualLogger logger;
    
    /** Map of active scheduled tasks for proper cleanup */
    private final @NotNull Map<Integer, BukkitTask> activeTasks = new ConcurrentHashMap<>();
    
    /** Current countdown value in seconds */
    private int countdown;
    
    /** Total game time in seconds */
    private final int gameTime;
    
    /** Grace period duration in seconds (PvP disabled) */
    private final int gracePeriod;
    
    /** Deathmatch duration in seconds */
    private final int deathmatchTime;
    
    /** Timestamp when the game started (for duration tracking) */
    private long startTime;
    
    /** Flag to indicate if the game has ended early (prevents timer from continuing) */
    private boolean gameEndedEarly = false;
    
    /** Current game state for proper timer display */
    private @NotNull GameState currentGameState = GameState.WAITING;
    
    /** Title display timing configuration for countdown messages */
    private static final Title.Times TITLE_TIMES = Title.Times.times(
        Duration.ofMillis(500),  // Fade in time
        Duration.ofMillis(1500), // Stay time
        Duration.ofMillis(500)   // Fade out time
    );
    
    private final @NotNull DeathmatchReminderConfig reminderConfig;
    
    public GameTimerManager(@NotNull LumaSG plugin, @NotNull GamePlayerManager playerManager) {
        this.plugin = plugin;
        this.playerManager = playerManager;
        this.logger = plugin.getDebugLogger().forContext("GameTimerManager");
        this.reminderConfig = new DeathmatchReminderConfig(plugin);
        
        // Load configuration values
        this.countdown = plugin.getConfig().getInt("game.countdown-seconds", 30);
        this.gameTime = plugin.getConfig().getInt("game.game-time-minutes", 20) * 60;
        this.gracePeriod = plugin.getConfig().getInt("game.grace-period-seconds", 30);
        this.deathmatchTime = plugin.getConfig().getInt("game.deathmatch-time-minutes", 5) * 60;
        
        this.startTime = System.currentTimeMillis();
    }
    
    /**
     * Sets the current game state for proper timer display.
     */
    public void setCurrentGameState(@NotNull GameState state) {
        this.currentGameState = state;
        logger.debug("Timer manager game state updated to: " + state);
    }
    
    /**
     * Starts the countdown with a custom duration.
     */
    public void startCountdown(int seconds, @NotNull Runnable onComplete) {
        if (seconds < 0) {
            throw new IllegalArgumentException("Countdown duration cannot be negative");
        }
        
        this.countdown = seconds;
        startCountdown(onComplete);
    }
    
    /**
     * Starts the countdown using the default duration from configuration.
     */
    public void startCountdown(@NotNull Runnable onComplete) {
        // Cancel any existing countdown task
        cancelAllTasks();
        
        // Start new countdown task that runs every second (20 ticks)
        final int[] taskIdRef = new int[1];
        BukkitTask countdownTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            try {
                if (countdown <= 0) {
                    // Countdown finished, start the game
                    BukkitTask task = activeTasks.remove(taskIdRef[0]);
                    if (task != null) {
                        task.cancel();
                    }
                    onComplete.run();
                    return;
                }
                
                // Show countdown title to players (every 10 seconds or last 10 seconds)
                if (countdown <= 10 || countdown % 10 == 0) {
                    Title title = Title.title(
                        Component.text("Game Starting", NamedTextColor.GREEN, TextDecoration.BOLD),
                        Component.text("in " + countdown + " seconds", NamedTextColor.YELLOW),
                        TITLE_TIMES
                    );
                    
                    // Show title to all players in the game
                    for (UUID playerId : playerManager.getPlayers()) {
                        Player player = playerManager.getCachedPlayer(playerId);
                        if (player != null) {
                            player.showTitle(title);
                        }
                    }
                }
                
                countdown--;
            } catch (Exception e) {
                logger.severe("Error in countdown task", e);
                BukkitTask task = activeTasks.remove(taskIdRef[0]);
                if (task != null) {
                    task.cancel();
                }
            }
        }, 0L, 20L); // Run every 20 ticks (1 second)
        
        taskIdRef[0] = countdownTask.getTaskId();
        activeTasks.put(countdownTask.getTaskId(), countdownTask);
    }
    
    /**
     * Cancels the countdown.
     */
    public void cancelCountdown() {
        cancelAllTasks();
        
        // Clear any displayed titles
        for (UUID playerId : playerManager.getPlayers()) {
            Player player = playerManager.getCachedPlayer(playerId);
            if (player != null) {
                player.clearTitle();
            }
        }
    }
    
    /**
     * Starts the grace period timer.
     */
    public void startGracePeriod(@NotNull Runnable onComplete) {
        // Inform players that grace period has started
        Title title = Title.title(
            Component.text("Grace Period", NamedTextColor.GREEN, TextDecoration.BOLD),
            Component.text("The hunt begins!", NamedTextColor.YELLOW),
            TITLE_TIMES
        );
        
        // Show title to all players
        for (UUID playerId : playerManager.getPlayers()) {
            Player player = playerManager.getCachedPlayer(playerId);
            if (player != null) {
                player.showTitle(title);
                // Play a sound to indicate game has started
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
            }
        }
        
        BukkitTask gracePeriodTask = plugin.getServer().getScheduler().runTaskLater(plugin, 
            onComplete, gracePeriod * 20L);
        activeTasks.put(gracePeriodTask.getTaskId(), gracePeriodTask);
    }
    
    /**
     * Schedules the deathmatch to start.
     */
    public void scheduleDeathmatch(@NotNull Runnable onDeathmatch, @NotNull Runnable onGameEnd) {
        // Calculate when the deathmatch should start
        int deathmatchStartTime = gameTime - deathmatchTime;
        
        // Add debug logging
        logger.debug("Game timing: Total game time: " + gameTime + " seconds");
        logger.debug("Game timing: Deathmatch time: " + deathmatchTime + " seconds");
        logger.debug("Game timing: Deathmatch will start after: " + deathmatchStartTime + " seconds");
        
        // Schedule deathmatch reminders
        scheduleDeathmatchReminders(deathmatchStartTime);
        
        // Only schedule deathmatch start - the deathmatch will handle its own end timing
        BukkitTask deathmatchTask = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            // Start deathmatch
            onDeathmatch.run();
            // Then schedule the game end after deathmatch duration
            scheduleDeathmatchEnd(onGameEnd);
        }, deathmatchStartTime * 20L);
        activeTasks.put(deathmatchTask.getTaskId(), deathmatchTask);
    }
    
    /**
     * Schedules deathmatch reminder messages.
     */
    private void scheduleDeathmatchReminders(int deathmatchStartTime) {
        // Check if deathmatch reminders are enabled
        if (!reminderConfig.isEnabled()) {
            logger.debug("Deathmatch reminders are disabled in config");
            return;
        }
        
        // Get reminder times from config
        List<Integer> reminderTimes = reminderConfig.getReminderTimes();
        
        for (int reminderTime : reminderTimes) {
            // Calculate when to send the reminder (game start + deathmatch start time - reminder time)
            int reminderDelay = deathmatchStartTime - reminderTime;
            
            // Only schedule reminders that are in the future
            if (reminderDelay > 0) {
                BukkitTask reminderTask = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    sendDeathmatchReminder(reminderTime);
                }, reminderDelay * 20L);
                
                activeTasks.put(reminderTask.getTaskId(), reminderTask);
                logger.debug("Scheduled deathmatch reminder for " + reminderTime + " seconds before deathmatch (delay: " + reminderDelay + "s)");
            }
        }
    }
    
    /**
     * Sends a deathmatch reminder message to all players.
     */
    private void sendDeathmatchReminder(int secondsUntilDeathmatch) {
        // Format time and get color
        var timeFormat = reminderConfig.formatDeathmatchTime(secondsUntilDeathmatch);
        String timeText = timeFormat.getKey();
        String timeColorTag = timeFormat.getValue();
        
        // Create the message component
        Component message = reminderConfig.createDeathmatchMessage(timeText, timeColorTag);
        
        // Send message to all players and play sounds
        for (UUID playerId : playerManager.getPlayers()) {
            Player player = playerManager.getCachedPlayer(playerId);
            if (player != null) {
                player.sendMessage(message);
                reminderConfig.playDeathmatchSound(player, secondsUntilDeathmatch);
            }
        }
        
        // Also send to spectators (without sounds)
        for (UUID spectatorId : playerManager.getSpectators()) {
            Player spectator = playerManager.getCachedPlayer(spectatorId);
            if (spectator != null) {
                spectator.sendMessage(message);
            }
        }
        
        logger.debug("Sent deathmatch reminder: " + timeText + " remaining");
    }
    
    /**
     * Schedules the game to end after deathmatch duration.
     */
    public void scheduleDeathmatchEnd(@NotNull Runnable onGameEnd) {
        logger.debug("Scheduling game end after deathmatch: " + deathmatchTime + " seconds");
        
        BukkitTask deathmatchEndTask = plugin.getServer().getScheduler().runTaskLater(plugin, 
            onGameEnd, 
            deathmatchTime * 20L);
        activeTasks.put(deathmatchEndTask.getTaskId(), deathmatchEndTask);
    }
    
    /**
     * Gets the remaining time in seconds.
     */
    public int getTimeRemaining() {
        // If game ended early, return 0 to stop timer display
        if (gameEndedEarly) {
            return 0;
        }
        
        // During countdown phase, return the countdown time instead of game time
        if (currentGameState == GameState.COUNTDOWN) {
            logger.debug("Timer Debug - Countdown: " + countdown + "s remaining");
            return countdown;
        }
        
        // During waiting phase, return 0 (no timer should be shown)
        if (currentGameState == GameState.WAITING) {
            return 0;
        }
        
        // For all other states (GRACE_PERIOD, ACTIVE, DEATHMATCH), show game time remaining
        // Use the same gameTime that was loaded in constructor to ensure consistency
        long elapsedTime = (System.currentTimeMillis() - startTime) / 1000;
        int remaining = Math.max(0, gameTime - (int)elapsedTime);
        
        logger.debug("Timer Debug - State: " + currentGameState + ", Total: " + gameTime + "s, Elapsed: " + elapsedTime + "s, Remaining: " + remaining + "s");
        
        return remaining;
    }
    
    /**
     * Resets the start time (called when game actually starts).
     */
    public void resetStartTime() {
        this.startTime = System.currentTimeMillis();
    }
    
    /**
     * Marks the game as ended early (stops timer display).
     */
    public void markGameEndedEarly() {
        this.gameEndedEarly = true;
        logger.debug("Game marked as ended early - timer display will stop");
    }
    
    /**
     * Cancels all active scheduled tasks.
     */
    private void cancelAllTasks() {
        for (BukkitTask task : activeTasks.values()) {
            if (!task.isCancelled()) {
                task.cancel();
            }
        }
        activeTasks.clear();
    }
    
    /**
     * Cleans up all scheduled tasks.
     */
    public void cleanup() {
        markGameEndedEarly(); // Stop timer display immediately
        cancelAllTasks();
    }
    
    // Getters
    public int getCountdown() {
        return countdown;
    }
    
    public int getGameTime() {
        return gameTime;
    }
    
    public int getGracePeriod() {
        return gracePeriod;
    }
    
    public int getDeathmatchTime() {
        return deathmatchTime;
    }
    
    public long getStartTime() {
        return startTime;
    }
} 
