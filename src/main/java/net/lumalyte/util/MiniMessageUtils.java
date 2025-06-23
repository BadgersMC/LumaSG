package net.lumalyte.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Utility class for handling MiniMessage formatting in Survival Games.
 * 
 * <p>This class provides centralized MiniMessage handling for the plugin, including
 * parsing MiniMessage strings, applying placeholders, and sending formatted messages.
 * It ensures consistent message formatting across the entire plugin.</p>
 * 
 * <p>MiniMessage is a modern text formatting system that supports complex formatting,
 * placeholders, and conditional text, making it much more powerful than legacy color codes.</p>
 * 
 * @author LumaLyte
 * @version 1.0
 * @since 1.0
 */
public class MiniMessageUtils {
    
    /** MiniMessage instance for parsing messages */
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    
    /** Legacy serializer for converting to legacy color codes */
    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.builder()
        .character('§')
        .hexColors()
        .useUnusualXRepeatedCharacterHexFormat()
        .build();
    
    /**
     * Sends a MiniMessage formatted message to a command sender.
     * 
     * @param sender The command sender to send the message to
     * @param message The MiniMessage formatted string
     */
    public static void sendMessage(@NotNull CommandSender sender, @NotNull String message) {
        if (sender == null) {
            throw new IllegalArgumentException("Sender cannot be null");
        }
        if (message == null) {
            throw new IllegalArgumentException("Message cannot be null");
        }
        
        Component component = parseMessage(message);
        sender.sendMessage(component);
    }
    
    /**
     * Sends a MiniMessage formatted message to a player.
     * 
     * @param player The player to send the message to
     * @param message The MiniMessage formatted string
     */
    public static void sendMessage(@NotNull Player player, @NotNull String message) {
        sendMessage((CommandSender) player, message);
    }
    
    /**
     * Sends a MiniMessage formatted message with placeholders to a command sender.
     * 
     * @param sender The command sender to send the message to
     * @param message The MiniMessage formatted string
     * @param placeholders Map of placeholder names to values
     */
    public static void sendMessage(@NotNull CommandSender sender, @NotNull String message, @NotNull Map<String, String> placeholders) {
        if (sender == null) {
            throw new IllegalArgumentException("Sender cannot be null");
        }
        if (message == null) {
            throw new IllegalArgumentException("Message cannot be null");
        }
        if (placeholders == null) {
            throw new IllegalArgumentException("Placeholders cannot be null");
        }
        
        Component component = parseMessage(message, placeholders);
        sender.sendMessage(component);
    }
    
    /**
     * Sends a MiniMessage formatted message with placeholders to a player.
     * 
     * @param player The player to send the message to
     * @param message The MiniMessage formatted string
     * @param placeholders Map of placeholder names to values
     */
    public static void sendMessage(@NotNull Player player, @NotNull String message, @NotNull Map<String, String> placeholders) {
        sendMessage((CommandSender) player, message, placeholders);
    }
    
    /**
     * Parses a MiniMessage string into a Component.
     * 
     * @param message The MiniMessage formatted string
     * @return The parsed Component
     */
    public static @NotNull Component parseMessage(@NotNull String message) {
        if (message == null) {
            throw new IllegalArgumentException("Message cannot be null");
        }
        
        return MINI_MESSAGE.deserialize(message);
    }
    
    /**
     * Parses a MiniMessage string with placeholders into a Component.
     * 
     * @param message The MiniMessage formatted string
     * @param placeholders Map of placeholder names to values
     * @return The parsed Component
     */
    public static @NotNull Component parseMessage(@NotNull String message, @NotNull Map<String, String> placeholders) {
        if (message == null) {
            throw new IllegalArgumentException("Message cannot be null");
        }
        if (placeholders == null) {
            throw new IllegalArgumentException("Placeholders cannot be null");
        }
        
        TagResolver.Builder resolver = TagResolver.builder();
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            resolver.resolver(Placeholder.parsed(entry.getKey(), entry.getValue()));
        }
        
        return MINI_MESSAGE.deserialize(message, resolver.build());
    }
    
    /**
     * Parses a MiniMessage string with a single placeholder into a Component.
     * 
     * @param message The MiniMessage formatted string
     * @param key The placeholder key
     * @param value The placeholder value
     * @return The parsed Component
     */
    public static @NotNull Component parseMessage(@NotNull String message, @NotNull String key, @NotNull String value) {
        if (message == null) {
            throw new IllegalArgumentException("Message cannot be null");
        }
        if (key == null) {
            throw new IllegalArgumentException("Key cannot be null");
        }
        if (value == null) {
            throw new IllegalArgumentException("Value cannot be null");
        }
        
        return MINI_MESSAGE.deserialize(message, Placeholder.parsed(key, value));
    }
    
    /**
     * Converts a legacy color-coded string to MiniMessage format.
     * This is useful for migrating from legacy color codes to MiniMessage.
     * 
     * @param legacyMessage The legacy color-coded string (using &)
     * @return The MiniMessage formatted string
     */
    public static @NotNull String convertLegacyToMiniMessage(@NotNull String legacyMessage) {
        if (legacyMessage == null) {
            throw new IllegalArgumentException("Legacy message cannot be null");
        }
        
        return legacyMessage
            .replace("&0", "<black>")
            .replace("&1", "<dark_blue>")
            .replace("&2", "<dark_green>")
            .replace("&3", "<dark_aqua>")
            .replace("&4", "<dark_red>")
            .replace("&5", "<dark_purple>")
            .replace("&6", "<gold>")
            .replace("&7", "<gray>")
            .replace("&8", "<dark_gray>")
            .replace("&9", "<blue>")
            .replace("&a", "<green>")
            .replace("&b", "<aqua>")
            .replace("&c", "<red>")
            .replace("&d", "<light_purple>")
            .replace("&e", "<yellow>")
            .replace("&f", "<white>")
            .replace("&l", "<bold>")
            .replace("&n", "<underline>")
            .replace("&o", "<italic>")
            .replace("&m", "<strikethrough>")
            .replace("&k", "<obfuscated>")
            .replace("&r", "<reset>");
    }
    
    /**
     * Safely parses a MiniMessage string, returning null if parsing fails.
     * 
     * @param message The MiniMessage formatted string
     * @return The parsed Component, or null if parsing failed
     */
    public static @Nullable Component parseMessageSafe(@Nullable String message) {
        if (message == null) {
            return null;
        }
        
        try {
            return MINI_MESSAGE.deserialize(message);
        } catch (Exception e) {
            // Return null if parsing fails
            return null;
        }
    }
    
    /**
     * Converts an Adventure Component to a MiniMessage string.
     * This utility method converts modern Adventure components back to
     * MiniMessage format strings.
     * 
     * @param component The Adventure Component to convert
     * @return The MiniMessage formatted string
     */
    public static @NotNull String fromComponent(@NotNull Component component) {
        if (component == null) {
            throw new IllegalArgumentException("Component cannot be null");
        }
        
        return MINI_MESSAGE.serialize(component);
    }
    
    /**
     * Converts a MiniMessage formatted string to legacy color codes.
     * This is useful for systems that don't support MiniMessage format.
     * 
     * @param message The MiniMessage formatted string
     * @return The string with legacy color codes
     */
    public static @NotNull String toLegacy(@NotNull String message) {
        if (message == null) {
            throw new IllegalArgumentException("Message cannot be null");
        }
        
        Component component = MINI_MESSAGE.deserialize(message);
        return LEGACY_SERIALIZER.serialize(component);
    }

    /**
     * Converts a Component to a legacy color-coded string.
     * 
     * @param component The Component to convert
     * @return The string with legacy color codes
     */
    public static @NotNull String toLegacy(@NotNull Component component) {
        if (component == null) {
            throw new IllegalArgumentException("Component cannot be null");
        }
        
        return LEGACY_SERIALIZER.serialize(component);
    }

    /**
     * Processes placeholders in a string without MiniMessage parsing.
     * This is useful when you need to replace placeholders in a string
     * but don't want to parse it as MiniMessage.
     * 
     * @param text The text containing placeholders
     * @param placeholders Map of placeholder names to values
     * @return The text with placeholders replaced
     */
    public static @NotNull String processPlaceholders(@NotNull String text, @NotNull Map<String, String> placeholders) {
        if (text == null) {
            throw new IllegalArgumentException("Text cannot be null");
        }
        if (placeholders == null) {
            throw new IllegalArgumentException("Placeholders cannot be null");
        }
        
        String result = text;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace("<" + entry.getKey() + ">", entry.getValue());
        }
        
        return result;
    }
} 