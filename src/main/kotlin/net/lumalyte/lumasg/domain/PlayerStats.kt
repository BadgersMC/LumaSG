package net.lumalyte.lumasg.domain

import java.time.Instant
import java.util.UUID

data class PlayerStats(
    val uuid: UUID,
    val playerName: String,
    val kills: Int = 0,
    val deaths: Int = 0,
    val wins: Int = 0,
    val losses: Int = 0,
    val gamesPlayed: Int = 0,
    val damageDealt: Double = 0.0,
    val damageTaken: Double = 0.0,
    val totalTimePlayed: Long = 0L,
    val bestPlacement: Int = 0,
    val currentWinStreak: Int = 0,
    val bestWinStreak: Int = 0,
    val top3Finishes: Int = 0,
    val chestsOpened: Int = 0,
    val createdAt: Instant = Instant.now(),
    val lastPlayed: Instant = Instant.now(),
    val lastUpdated: Instant = Instant.now()
) {
    val kdr: Double get() = if (deaths == 0) kills.toDouble() else kills.toDouble() / deaths
    val winRate: Double get() = if (gamesPlayed == 0) 0.0 else wins.toDouble() / gamesPlayed
    val top3Rate: Double get() = if (gamesPlayed == 0) 0.0 else top3Finishes.toDouble() / gamesPlayed
    val averageKillsPerGame: Double get() = if (gamesPlayed == 0) 0.0 else kills.toDouble() / gamesPlayed
    val averageGameTime: Double get() = if (gamesPlayed == 0) 0.0 else totalTimePlayed.toDouble() / gamesPlayed
}
