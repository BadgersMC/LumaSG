package net.lumalyte.lumasg.util

/**
 * Sanitizes a player name for use in a console command argument.
 *
 * Strips whitespace and any character outside the vanilla-name charset
 * [A-Za-z0-9_]. This prevents Bedrock/Geyser names with spaces or dots
 * from injecting extra command tokens.
 */
fun sanitizeCommandArg(raw: String): String =
    raw.filter { it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' || it == '_' }
