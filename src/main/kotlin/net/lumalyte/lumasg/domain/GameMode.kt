package net.lumalyte.lumasg.domain

sealed class GameMode(val displayName: String, val teamSize: Int) {
    data object Solo  : GameMode("Solo", 1)
    data object Duos  : GameMode("Duos", 2)
    data object Trios : GameMode("Trios", 3)
}
