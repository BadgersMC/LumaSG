package net.lumalyte.lumasg.util

/**
 * Sanitizes a player name for use in a console command argument.
 *
 * Strips whitespace and any character outside the vanilla-name charset
 * [A-Za-z0-9_]. This prevents Bedrock/Geyser names with spaces or dots
 * from injecting extra command tokens.
 *
 * TODO(bedrock): Bedrock/Geyser names use a `.` prefix (e.g. ".PlayerName"),
 * which this strips — so a reward command would target "PlayerName", not the
 * real account. If Bedrock players are supported, switch to quoting/escaping
 * the argument instead of charset-filtering so their identity is preserved.
 */
fun sanitizeCommandArg(raw: String): String =
    raw.filter { it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' || it == '_' }
