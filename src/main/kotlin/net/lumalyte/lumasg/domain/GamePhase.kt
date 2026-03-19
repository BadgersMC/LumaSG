package net.lumalyte.lumasg.domain

import java.util.UUID

/** Represents the lifecycle phase of a game instance. */
sealed class GamePhase {
    data object Waiting : GamePhase()
    data class  Countdown(val secondsLeft: Int) : GamePhase()
    data class  Grace(val secondsRemaining: Int) : GamePhase()
    data class  Active(val secondsRemaining: Int) : GamePhase()
    data class  Deathmatch(val secondsRemaining: Int) : GamePhase()
    data class  Ended(val winner: UUID?) : GamePhase()
}
