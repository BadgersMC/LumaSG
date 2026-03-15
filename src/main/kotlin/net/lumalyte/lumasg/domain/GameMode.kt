package net.lumalyte.lumasg.domain

sealed class GameMode(val displayName: String, val teamSize: Int, val description: String) {
    data object Solo  : GameMode("Solo", 1, "Free-for-all survival")
    data object Duos  : GameMode("Duos", 2, "Fight in teams of two")
    data object Trios : GameMode("Trios", 3, "Fight in teams of three")

    /** Whether this mode uses teams (teamSize > 1). */
    val isTeamMode: Boolean get() = teamSize > 1

    /** Maximum number of teams for a given player count. */
    fun getMaxTeams(playerCount: Int): Int = playerCount / teamSize

    /** Ideal player count for this mode given an arena's max capacity. */
    fun getIdealPlayerCount(maxPlayers: Int): Int = (maxPlayers / teamSize) * teamSize

    companion object {
        /** All game mode instances. */
        val entries: List<GameMode> = listOf(Solo, Duos, Trios)

        /** Find a game mode by display name (case-insensitive). */
        fun fromDisplayName(name: String): GameMode? =
            entries.firstOrNull { it.displayName.equals(name, ignoreCase = true) }
    }
}
