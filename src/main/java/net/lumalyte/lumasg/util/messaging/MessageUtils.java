package net.lumalyte.lumasg.util.messaging;

import net.lumalyte.lumasg.game.core.Game;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import java.util.UUID;

/**
 * Utility class for handling message formatting and sending in Survival Games.
 * 
 * <p>This class provides centralized message handling for the plugin, including
 * color formatting, component serialization, and message broadcasting to games.
 * It ensures consistent message formatting across the entire plugin.</p>
 * 
 * <p>The MessageUtils class supports both legacy color codes and modern Adventure
 * components, providing backward compatibility while allowing for rich text
 * formatting when needed.</p>
 * 
 * @author LumaLyte
 * @version 1.0
 * @since 1.0
 */
public class MessageUtils {
    
    /** Legacy component serializer for converting between legacy and modern text formats */
    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.legacyAmpersand();
    
    /**
     * Sends a formatted message to a command sender.
     * 
     * <p>This method converts legacy color codes (using &) to modern Adventure
     * components and sends the message to the specified sender. It supports
     * both players and console command senders.</p>
     * 
     * @param sender The command sender to send the message to
     * @param message The message with legacy color codes (e.g., "&aHello &cWorld!")
     */
    public static void sendMessage(@NotNull CommandSender sender, @NotNull String message) {
        if (sender == null) {
            throw new IllegalArgumentException("Sender cannot be null");
        }
        if (message == null) {
            throw new IllegalArgumentException("Message cannot be null");
        }
        
        Component component = LEGACY_SERIALIZER.deserialize(message);
        sender.sendMessage(component);
    }
    
    /**
     * Sends a formatted message to a player.
     * 
     * <p>This method is a convenience wrapper around sendMessage(CommandSender, String)
     * specifically for Player instances.</p>
     * 
     * @param player The player to send the message to
     * @param message The message with legacy color codes
     */
    public static void sendMessage(@NotNull Player player, @NotNull String message) {
        sendMessage((CommandSender) player, message);
    }
    
    /**
     * Broadcasts a formatted message to all players in a game.
     * 
     * <p>This method sends a message to all players currently participating
     * in the specified game, including both active players and spectators.</p>
     * 
     * @param game The game to broadcast the message to
     * @param message The message with legacy color codes
     */
    public static void broadcast(@NotNull Game game, @NotNull String message) {
        if (game == null) {
            throw new IllegalArgumentException("Game cannot be null");
        }
        if (message == null) {
            throw new IllegalArgumentException("Message cannot be null");
        }
        
        Component component = LEGACY_SERIALIZER.deserialize(message);
        
        // Send to all players in the game
        for (UUID playerId : game.getPlayers()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                player.sendMessage(component);
            }
        }
        
        // Send to all spectators
        for (UUID spectatorId : game.getSpectators()) {
            Player spectator = Bukkit.getPlayer(spectatorId);
            if (spectator != null && spectator.isOnline()) {
                spectator.sendMessage(component);
            }
        }
    }
    
    /**
     * Broadcasts a formatted message to all players in a game with a prefix.
     * 
     * <p>This method adds a prefix to the message before broadcasting it to
     * all players in the game. The prefix helps identify the source of the
     * message.</p>
     * 
     * @param game The game to broadcast the message to
     * @param prefix The prefix to add to the message
     * @param message The message with legacy color codes
     */
    public static void broadcast(@NotNull Game game, @NotNull String prefix, @NotNull String message) {
        if (game == null) {
            throw new IllegalArgumentException("Game cannot be null");
        }
        if (prefix == null) {
            throw new IllegalArgumentException("Prefix cannot be null");
        }
        if (message == null) {
            throw new IllegalArgumentException("Message cannot be null");
        }
        
        String fullMessage = prefix + " " + message;
        broadcast(game, fullMessage);
    }
    
    /**
     * Broadcasts a formatted message to all online players on the server.
     * 
     * <p>This method sends a message to all players currently online on the
     * server, regardless of whether they are in a game or not.</p>
     * 
     * @param message The message with legacy color codes
     */
    public static void broadcastToAll(@NotNull String message) {
        if (message == null) {
            throw new IllegalArgumentException("Message cannot be null");
        }
        
        Component component = LEGACY_SERIALIZER.deserialize(message);
        
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(component);
        }
    }
    
    /**
     * Broadcasts a formatted message to all online players with a prefix.
     * 
     * @param prefix The prefix to add to the message
     * @param message The message with legacy color codes
     */
    public static void broadcastToAll(@NotNull String prefix, @NotNull String message) {
        if (prefix == null) {
            throw new IllegalArgumentException("Prefix cannot be null");
        }
        if (message == null) {
            throw new IllegalArgumentException("Message cannot be null");
        }
        
        String fullMessage = prefix + " " + message;
        broadcastToAll(fullMessage);
    }
    
    /**
     * Converts a legacy color-coded string to an Adventure Component.
     * 
     * <p>This utility method converts strings with legacy color codes (using &)
     * to modern Adventure components for use with the Paper API.</p>
     * 
     * @param message The message with legacy color codes
     * @return The converted Adventure Component
     */
    public static @NotNull Component toComponent(@NotNull String message) {
        if (message == null) {
            throw new IllegalArgumentException("Message cannot be null");
        }
        
        return LEGACY_SERIALIZER.deserialize(message);
    }
    
    /**
     * Converts an Adventure Component to a legacy color-coded string.
     * 
     * <p>This utility method converts modern Adventure components back to
     * legacy color-coded strings for backward compatibility.</p>
     * 
     * @param component The Adventure Component to convert
     * @return The legacy color-coded string
     */
    public static @NotNull String fromComponent(@NotNull Component component) {
        if (component == null) {
            throw new IllegalArgumentException("Component cannot be null");
        }
        
        return LEGACY_SERIALIZER.serialize(component);
    }
    
    /**
     * Formats a time duration in seconds to a human-readable string.
     * 
     * <p>This method converts a duration in seconds to a formatted string
     * showing minutes and seconds (e.g., "2:30" for 150 seconds).</p>
     * 
     * @param seconds The duration in seconds
     * @return A formatted time string
     */
    public static @NotNull String formatTime(int seconds) {
        if (seconds < 0) {
            return "0:00";
        }
        
        int minutes = seconds / 60;
        int remainingSeconds = seconds % 60;
        
        return String.format("%d:%02d", minutes, remainingSeconds);
    }
    
    /**
     * Formats a player count with proper grammar.
     * 
     * <p>This method formats a player count to show "player" or "players"
     * based on the count (e.g., "1 player" vs "5 players").</p>
     * 
     * @param count The number of players
     * @return A formatted player count string
     */
    public static @NotNull String formatPlayerCount(int count) {
        if (count == 1) {
            return "1 player";
        } else {
            return count + " players";
        }
    }
    
    /**
     * Creates a progress bar string.
     * 
     * <p>This method creates a visual progress bar using block characters
     * to represent progress from 0% to 100%.</p>
     * 
     * @param current The current value
     * @param max The maximum value
     * @param length The length of the progress bar in characters
     * @param filledChar The character to use for filled portions
     * @param emptyChar The character to use for empty portions
     * @return A formatted progress bar string
     */
    public static @NotNull String createProgressBar(int current, int max, int length, @NotNull String filledChar, @NotNull String emptyChar) {
        if (max <= 0) {
            return emptyChar.repeat(length);
        }
        
        double percentage = (double) current / max;
        int filledLength = (int) (percentage * length);
        int emptyLength = length - filledLength;
        
        return filledChar.repeat(filledLength) + emptyChar.repeat(emptyLength);
    }
    
    /**
     * Creates a default progress bar using block characters.
     * 
     * <p>This method creates a progress bar using █ for filled portions
     * and ░ for empty portions.</p>
     * 
     * @param current The current value
     * @param max The maximum value
     * @param length The length of the progress bar in characters
     * @return A formatted progress bar string
     */
    public static @NotNull String createProgressBar(int current, int max, int length) {
        return createProgressBar(current, max, length, "█", "░");
    }
} 

