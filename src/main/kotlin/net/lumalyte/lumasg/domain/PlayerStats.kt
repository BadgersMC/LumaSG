package net.lumalyte.lumasg.domain

import java.time.Instant
import java.util.UUID

data class PlayerStats(
    val uuid: UUID,
    val playerName: String,
    val kills: Int = 0,
    val deaths: Int = 0,
    val wins: Int = 0,
    val gamesPlayed: Int = 0,
    val damageDealt: Double = 0.0,
    val damageTaken: Double = 0.0,
    val createdAt: Instant = Instant.now()
) {
    val kdr: Double get() = if (deaths == 0) kills.toDouble() else kills.toDouble() / deaths
    val winRate: Double get() = if (gamesPlayed == 0) 0.0 else wins.toDouble() / gamesPlayed
}
