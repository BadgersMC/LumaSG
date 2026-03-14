package net.lumalyte.lumasg.persistence.repositories

import net.badgersmc.nexus.annotations.Repository
import net.lumalyte.lumasg.domain.PlayerStats
import net.lumalyte.lumasg.persistence.dbQuery
import net.lumalyte.lumasg.persistence.tables.PlayerStatsTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import java.time.Instant
import java.util.UUID

@Repository
class PlayerStatsRepository {

    suspend fun findByUuid(uuid: UUID): PlayerStats? = dbQuery {
        PlayerStatsTable
            .selectAll()
            .where { PlayerStatsTable.uuid eq uuid.toString() }
            .singleOrNull()
            ?.toPlayerStats()
    }

    suspend fun upsert(stats: PlayerStats): Unit = dbQuery {
        PlayerStatsTable.upsert {
            it[uuid] = stats.uuid.toString()
            it[playerName] = stats.playerName
            it[kills] = stats.kills
            it[deaths] = stats.deaths
            it[wins] = stats.wins
            it[gamesPlayed] = stats.gamesPlayed
            it[damageDealt] = stats.damageDealt
            it[damageTaken] = stats.damageTaken
            it[lastSeen] = Instant.now()
            it[createdAt] = stats.createdAt
        }
    }

    suspend fun getLeaderboard(limit: Int = 10): List<PlayerStats> = dbQuery {
        PlayerStatsTable
            .selectAll()
            .orderBy(PlayerStatsTable.kills, SortOrder.DESC)
            .limit(limit)
            .map { it.toPlayerStats() }
    }

    private fun ResultRow.toPlayerStats() = PlayerStats(
        uuid = UUID.fromString(this[PlayerStatsTable.uuid]),
        playerName = this[PlayerStatsTable.playerName],
        kills = this[PlayerStatsTable.kills],
        deaths = this[PlayerStatsTable.deaths],
        wins = this[PlayerStatsTable.wins],
        gamesPlayed = this[PlayerStatsTable.gamesPlayed],
        damageDealt = this[PlayerStatsTable.damageDealt],
        damageTaken = this[PlayerStatsTable.damageTaken],
        createdAt = this[PlayerStatsTable.createdAt]
    )
}
