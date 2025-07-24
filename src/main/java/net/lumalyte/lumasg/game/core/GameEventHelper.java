package net.lumalyte.lumasg.game.core;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import net.lumalyte.lumasg.LumaSG;
import net.lumalyte.lumasg.game.player.GamePlayerManager;
import net.lumalyte.lumasg.util.core.DebugLogger;
import net.lumalyte.lumasg.util.security.InputSanitizer;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Helper class for broadcasting game events to players.
 * 
 * <p>This class centralizes all event broadcasting logic that was previously
 * scattered throughout the Game class. It handles titles, sounds, and messages
 * for various game events like game start, PvP enable, deathmatch, etc.</p>
 * 
 * <p>This is a package-private helper class - it's an implementation detail
 * of the Game class and should not be used directly by external code.</p>
 */
class GameEventHelper {
    private final @NotNull LumaSG plugin;
    private final @NotNull GamePlayerManager playerManager;
    private final @NotNull DebugLogger.ContextualLogger logger;
    
    /**
     * Creates a new event helper for the specified game.
     * 
     * @param plugin The plugin instance
     * @param playerManager The player manager for this game
     * @param gameId The game ID for logging context
     */
    GameEventHelper(@NotNull LumaSG plugin, @NotNull GamePlayerManager playerManager, @NotNull String gameId) {
        this.plugin = plugin;
        this.playerManager = playerManager;
        this.logger = plugin.getDebugLogger().forContext("GameEvents-" + gameId);
    }
    
    /**
     * Broadcasts a message to all players and spectators in the game.
     * 
     * @param message The message to broadcast
     */
    void broadcastMessage(@NotNull Component message) {
        // Get all players and spectators
        Set<UUID> allPlayers = new HashSet<>();
        allPlayers.addAll(playerManager.getPlayers());
        allPlayers.addAll(playerManager.getSpectators());
        
        // Send message to all players
        for (UUID playerId : allPlayers) {
            Player player = playerManager.getCachedPlayer(playerId);
            if (player != null) {
                player.sendMessage(message);
            }
        }
    }
    
    /**
     * Fires an event when a player joins the game.
     * 
     * @param player The player who joined
     */
    void firePlayerJoinEvent(@NotNull Player player) {
        // Broadcast join message
        broadcastMessage(Component.text()
            .append(player.displayName())
            .append(Component.text(" has joined the game!", NamedTextColor.GREEN))
            .build());
        
        // Play sound for all players
        for (UUID playerId : playerManager.getPlayers()) {
            Player p = playerManager.getCachedPlayer(playerId);
            if (p != null) {
                p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
            }
        }
        
        logger.debug("Player join event fired for: " + InputSanitizer.sanitizeForLogging(player.getName()));
    }
    
    /**
     * Fires an event when the game starts.
     */
    void fireGameStartEvent() {
        // Show title to all players
        Title title = Title.title(
            Component.text("Game Started!", NamedTextColor.GREEN, TextDecoration.BOLD),
            Component.text("Grace period has begun", NamedTextColor.YELLOW),
            Title.Times.times(Duration.ofMillis(500), Duration.ofMillis(3000), Duration.ofMillis(500)));
        
        // Send title and play sound
        for (UUID playerId : playerManager.getPlayers()) {
            Player player = playerManager.getCachedPlayer(playerId);
            if (player != null) {
                player.showTitle(title);
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
            }
        }
        
        // Broadcast message
        broadcastMessage(Component.text("The game has started! Grace period is active.", NamedTextColor.GREEN));
        
        logger.debug("Game start event fired");
    }
    
    /**
     * Fires an event when PvP is enabled.
     */
    void firePvPEnabledEvent() {
        // Show title to all players
        Title title = Title.title(
            Component.text("Grace Period Ended!", NamedTextColor.RED, TextDecoration.BOLD),
            Component.text("PvP is now enabled!", NamedTextColor.YELLOW),
            Title.Times.times(Duration.ofMillis(500), Duration.ofMillis(3000), Duration.ofMillis(500)));
        
        // Send title and play sound
        for (UUID playerId : playerManager.getPlayers()) {
            Player player = playerManager.getCachedPlayer(playerId);
            if (player != null) {
                player.showTitle(title);
                player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 1.0f);
            }
        }
        
        // Broadcast message
        broadcastMessage(Component.text("Grace period has ended! PvP is now enabled!", NamedTextColor.RED));
        
        logger.debug("PvP enabled event fired");
    }
    
    /**
     * Fires an event when deathmatch starts.
     */
    void fireDeathmatchStartEvent() {
        // Show title to all players
        Title title = Title.title(
            Component.text("DEATHMATCH", NamedTextColor.RED, TextDecoration.BOLD),
            Component.text("Fight to the death!", NamedTextColor.GOLD),
            Title.Times.times(Duration.ofMillis(500), Duration.ofMillis(3000), Duration.ofMillis(500)));
        
        // Send title and play sound
        for (UUID playerId : playerManager.getPlayers()) {
            Player player = playerManager.getCachedPlayer(playerId);
            if (player != null) {
                player.showTitle(title);
                player.playSound(player.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1.0f, 1.0f);
            }
        }
        
        // Broadcast message
        broadcastMessage(Component.text("Deathmatch has begun! The border is shrinking!", NamedTextColor.RED));
        
        logger.debug("Deathmatch start event fired");
    }
    
    /**
     * Fires an event when a player wins the game.
     * 
     * @param winner The winning player
     */
    void fireGameWinEvent(@NotNull Player winner) {
        // Show title to all players
        Title title = Title.title(
            Component.text("GAME OVER", NamedTextColor.GOLD, TextDecoration.BOLD),
            Component.text(winner.getName() + " has won the game!", NamedTextColor.YELLOW),
            Title.Times.times(Duration.ofMillis(500), Duration.ofMillis(5000), Duration.ofMillis(500)));
        
        // Send title and play sound to all players and spectators
        Set<UUID> allPlayers = new HashSet<>();
        allPlayers.addAll(playerManager.getPlayers());
        allPlayers.addAll(playerManager.getSpectators());
        
        for (UUID playerId : allPlayers) {
            Player player = playerManager.getCachedPlayer(playerId);
            if (player != null) {
                player.showTitle(title);
                player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
            }
        }
        
        // Broadcast message
        broadcastMessage(Component.text()
            .append(Component.text("GAME OVER! ", NamedTextColor.GOLD, TextDecoration.BOLD))
            .append(winner.displayName())
            .append(Component.text(" has won the game!", NamedTextColor.YELLOW))
            .build());
        
        logger.debug("Game win event fired for: " + InputSanitizer.sanitizeForLogging(winner.getName()));
    }
    
    /**
     * Fires an event when the game ends.
     */
    void fireGameEndEvent() {
        // Broadcast message
        broadcastMessage(Component.text("The game has ended. Thanks for playing!", NamedTextColor.AQUA));
        
        logger.debug("Game end event fired");
    }
}