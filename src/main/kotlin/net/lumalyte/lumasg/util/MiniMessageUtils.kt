package net.lumalyte.lumasg.util

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.command.CommandSender

/**
 * Utility object for handling MiniMessage formatting in Survival Games.
 *
 * Provides centralized MiniMessage handling for the plugin, including
 * parsing MiniMessage strings, applying placeholders, and sending formatted messages.
 * Ensures consistent message formatting across the entire plugin.
 */
object MiniMessageUtils {

    /** MiniMessage instance for parsing messages. */
    private val miniMessage: MiniMessage = MiniMessage.miniMessage()

    /** Legacy serializer for converting to legacy section-sign color codes. */
    private val legacySerializer: LegacyComponentSerializer = LegacyComponentSerializer.builder()
        .character('\u00A7')
        .hexColors()
        .useUnusualXRepeatedCharacterHexFormat()
        .build()

    // ── Sending ─────────────────────────────────────────────────────────────

    /**
     * Sends a MiniMessage formatted message to a command sender.
     *
     * @param sender The command sender to send the message to
     * @param message The MiniMessage formatted string
     */
    fun sendMessage(sender: CommandSender, message: String) {
        sender.sendMessage(parseMessage(message))
    }

    /**
     * Sends a MiniMessage formatted message with placeholders to a command sender.
     *
     * @param sender The command sender to send the message to
     * @param message The MiniMessage formatted string
     * @param placeholders Map of placeholder names to values
     */
    fun sendMessage(sender: CommandSender, message: String, placeholders: Map<String, String>) {
        sender.sendMessage(parseMessage(message, placeholders))
    }

    // ── Parsing ─────────────────────────────────────────────────────────────

    /**
     * Parses a MiniMessage string into a [Component].
     *
     * @param message The MiniMessage formatted string
     * @return The parsed Component
     */
    fun parseMessage(message: String): Component =
        miniMessage.deserialize(message)

    /**
     * Parses a MiniMessage string with placeholders into a [Component].
     *
     * @param message The MiniMessage formatted string
     * @param placeholders Map of placeholder names to values
     * @return The parsed Component
     */
    fun parseMessage(message: String, placeholders: Map<String, String>): Component {
        val resolver = TagResolver.builder().apply {
            placeholders.forEach { (key, value) ->
                resolver(Placeholder.parsed(key, value))
            }
        }.build()
        return miniMessage.deserialize(message, resolver)
    }

    /**
     * Parses a MiniMessage string with a single placeholder into a [Component].
     *
     * @param message The MiniMessage formatted string
     * @param key The placeholder key
     * @param value The placeholder value
     * @return The parsed Component
     */
    fun parseMessage(message: String, key: String, value: String): Component =
        miniMessage.deserialize(message, Placeholder.parsed(key, value))

    /**
     * Safely parses a MiniMessage string, returning null if the input is null or parsing fails.
     *
     * @param message The MiniMessage formatted string, or null
     * @return The parsed Component, or null if parsing failed
     */
    fun parseMessageSafe(message: String?): Component? =
        message?.let {
            try {
                miniMessage.deserialize(it)
            } catch (_: Exception) {
                null
            }
        }

    // ── Serialization / conversion ──────────────────────────────────────────

    /**
     * Converts an Adventure [Component] back to a MiniMessage string.
     *
     * @param component The Adventure Component to convert
     * @return The MiniMessage formatted string
     */
    fun fromComponent(component: Component): String =
        miniMessage.serialize(component)

    /**
     * Converts a MiniMessage formatted string to legacy section-sign color codes.
     *
     * @param message The MiniMessage formatted string
     * @return The string with legacy color codes
     */
    fun toLegacy(message: String): String {
        val component = miniMessage.deserialize(message)
        return legacySerializer.serialize(component)
    }

    /**
     * Converts a [Component] to a legacy section-sign color-coded string.
     *
     * @param component The Component to convert
     * @return The string with legacy color codes
     */
    fun toLegacy(component: Component): String =
        legacySerializer.serialize(component)

    /**
     * Converts a legacy ampersand-coded string to MiniMessage format.
     * Useful for migrating from legacy color codes to MiniMessage.
     *
     * @param legacyMessage The legacy color-coded string (using &)
     * @return The MiniMessage formatted string
     */
    fun convertLegacyToMiniMessage(legacyMessage: String): String =
        legacyMessage
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
            .replace("&r", "<reset>")

    /**
     * Processes placeholders in a raw string without MiniMessage parsing.
     * Replaces `<key>` tokens with the corresponding values.
     *
     * @param text The text containing placeholders
     * @param placeholders Map of placeholder names to values
     * @return The text with placeholders replaced
     */
    fun processPlaceholders(text: String, placeholders: Map<String, String>): String {
        var result = text
        placeholders.forEach { (key, value) ->
            result = result.replace("<$key>", value)
        }
        return result
    }

    /**
     * Deserializes a legacy ampersand-coded string to a [Component].
     * Converts `&` color codes to section-sign codes, then parses via the legacy serializer.
     *
     * @param legacyText The legacy color-coded text (using & symbols)
     * @return The Adventure Component
     */
    fun deserializeLegacy(legacyText: String): Component =
        legacySerializer.deserialize(legacyText.replace('&', '\u00A7'))
}
