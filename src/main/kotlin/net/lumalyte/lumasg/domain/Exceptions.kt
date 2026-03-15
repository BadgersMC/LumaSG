package net.lumalyte.lumasg.domain

/** Base exception for all LumaSG errors. */
open class LumaSGException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/** Thrown when a game-related operation fails. */
class GameException(message: String, cause: Throwable? = null) : LumaSGException(message, cause)

/** Thrown when an arena operation fails (e.g., arena not found, invalid config). */
class ArenaException(message: String, cause: Throwable? = null) : LumaSGException(message, cause)

/** Thrown when a team operation fails (e.g., team full, invalid invite). */
class TeamException(message: String, cause: Throwable? = null) : LumaSGException(message, cause)

/** Thrown when a player is not in the expected state. */
class PlayerStateException(message: String, cause: Throwable? = null) : LumaSGException(message, cause)

/** Thrown when configuration is invalid or missing. */
class ConfigurationException(message: String, cause: Throwable? = null) : LumaSGException(message, cause)
