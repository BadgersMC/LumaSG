package net.lumalyte.lumasg.persistence.repositories

import net.badgersmc.nexus.annotations.Repository
import net.lumalyte.lumasg.domain.LootMode
import net.lumalyte.lumasg.domain.PlayerStats
import net.lumalyte.lumasg.domain.StatType
import net.lumalyte.lumasg.persistence.DatabaseService
import net.lumalyte.lumasg.persistence.dbQuery
import net.lumalyte.lumasg.persistence.tables.PlayerStatsTable
import org.jetbrains.exposed.sql.*
import java.time.Instant
import java.util.UUID

@Repository
class PlayerStatsRepository(@Suppress("unused") private val db: DatabaseService) {

    suspend fun findByUuid(uuid: UUID, lootMode: LootMode = LootMode.MODERN): PlayerStats? = dbQuery {
        PlayerStatsTable
            .selectAll()
            .where {
                (PlayerStatsTable.uuid eq uuid.toString()) and
                (PlayerStatsTable.lootMode eq lootMode.name)
            }
            .singleOrNull()
            ?.toPlayerStats()
    }

    suspend fun upsert(stats: PlayerStats): Unit = dbQuery {
        val now = Instant.now()
        PlayerStatsTable.upsert {
            it[uuid] = stats.uuid.toString()
            it[lootMode] = stats.lootMode.name
            it[playerName] = stats.playerName
            it[kills] = stats.kills
            it[deaths] = stats.deaths
            it[wins] = stats.wins
            it[losses] = stats.losses
            it[gamesPlayed] = stats.gamesPlayed
            it[damageDealt] = stats.damageDealt
            it[damageTaken] = stats.damageTaken
            it[totalTimePlayed] = stats.totalTimePlayed
            it[bestPlacement] = stats.bestPlacement
            it[currentWinStreak] = stats.currentWinStreak
            it[bestWinStreak] = stats.bestWinStreak
            it[top3Finishes] = stats.top3Finishes
            it[chestsOpened] = stats.chestsOpened
            it[lastSeen] = now
            it[lastPlayed] = stats.lastPlayed
            it[createdAt] = stats.createdAt
        }
    }

    suspend fun getLeaderboard(statType: StatType, limit: Int = 10, lootMode: LootMode? = null): List<PlayerStats> = dbQuery {
        val baseQuery = if (lootMode != null) {
            PlayerStatsTable.selectAll().where { PlayerStatsTable.lootMode eq lootMode.name }
        } else {
            PlayerStatsTable.selectAll()
        }

        when (statType) {
            StatType.KILL_DEATH_RATIO -> {
                // Fetch candidate set ordered by kills DESC; sort in Kotlin by computed KDR.
                // Bounded fetch (limit*5) avoids loading full table for every leaderboard call.
                val candidates = baseQuery
                    .orderBy(PlayerStatsTable.kills to SortOrder.DESC)
                    .limit(limit * 5)
                    .map { it.toPlayerStats() }
                candidates.sortedByDescending { stats ->
                    if (stats.deaths == 0) Double.MAX_VALUE else stats.kills.toDouble() / stats.deaths
                }.take(limit)
            }

            StatType.WIN_RATE -> {
                // Fetch candidate set ordered by wins DESC; sort in Kotlin by computed win rate.
                val candidates = baseQuery
                    .orderBy(PlayerStatsTable.wins to SortOrder.DESC)
                    .limit(limit * 5)
                    .map { it.toPlayerStats() }
                candidates.sortedByDescending { stats ->
                    if (stats.gamesPlayed == 0) 0.0 else stats.wins.toDouble() / stats.gamesPlayed
                }.take(limit)
            }

            else -> {
                val orderColumn: Expression<*> = when (statType) {
                    StatType.WINS -> PlayerStatsTable.wins
                    StatType.KILLS -> PlayerStatsTable.kills
                    StatType.GAMES_PLAYED -> PlayerStatsTable.gamesPlayed
                    StatType.TIME_PLAYED -> PlayerStatsTable.totalTimePlayed
                    StatType.BEST_PLACEMENT -> PlayerStatsTable.bestPlacement
                    StatType.WIN_STREAK -> PlayerStatsTable.bestWinStreak
                    StatType.TOP3_FINISHES -> PlayerStatsTable.top3Finishes
                    StatType.DAMAGE_DEALT -> PlayerStatsTable.damageDealt
                    StatType.CHESTS_OPENED -> PlayerStatsTable.chestsOpened
                    else -> PlayerStatsTable.kills // unreachable; exhaustiveness guard
                }
                baseQuery.orderBy(orderColumn to SortOrder.DESC)
                    .limit(limit)
                    .map { it.toPlayerStats() }
            }
        }
    }

    /** Backwards-compatible overload — defaults to kills leaderboard. */
    suspend fun getLeaderboard(limit: Int = 10): List<PlayerStats> =
        getLeaderboard(StatType.KILLS, limit, null)

    suspend fun getTotalPlayerCount(): Long = dbQuery {
        PlayerStatsTable.selectAll().count()
    }

    suspend fun savePlayerStatsBatch(statsList: List<PlayerStats>): Unit = dbQuery {
        val now = Instant.now()
        for (stats in statsList) {
            PlayerStatsTable.upsert {
                it[uuid] = stats.uuid.toString()
                it[lootMode] = stats.lootMode.name
                it[playerName] = stats.playerName
                it[kills] = stats.kills
                it[deaths] = stats.deaths
                it[wins] = stats.wins
                it[losses] = stats.losses
                it[gamesPlayed] = stats.gamesPlayed
                it[damageDealt] = stats.damageDealt
                it[damageTaken] = stats.damageTaken
                it[totalTimePlayed] = stats.totalTimePlayed
                it[bestPlacement] = stats.bestPlacement
                it[currentWinStreak] = stats.currentWinStreak
                it[bestWinStreak] = stats.bestWinStreak
                it[top3Finishes] = stats.top3Finishes
                it[chestsOpened] = stats.chestsOpened
                it[lastSeen] = now
                it[lastPlayed] = stats.lastPlayed
                it[createdAt] = stats.createdAt
            }
        }
    }

    private fun ResultRow.toPlayerStats() = PlayerStats(
        uuid = UUID.fromString(this[PlayerStatsTable.uuid]),
        playerName = this[PlayerStatsTable.playerName],
        lootMode = LootMode.fromString(this[PlayerStatsTable.lootMode]) ?: LootMode.MODERN,
        kills = this[PlayerStatsTable.kills],
        deaths = this[PlayerStatsTable.deaths],
        wins = this[PlayerStatsTable.wins],
        losses = this[PlayerStatsTable.losses],
        gamesPlayed = this[PlayerStatsTable.gamesPlayed],
        damageDealt = this[PlayerStatsTable.damageDealt],
        damageTaken = this[PlayerStatsTable.damageTaken],
        totalTimePlayed = this[PlayerStatsTable.totalTimePlayed],
        bestPlacement = this[PlayerStatsTable.bestPlacement],
        currentWinStreak = this[PlayerStatsTable.currentWinStreak],
        bestWinStreak = this[PlayerStatsTable.bestWinStreak],
        top3Finishes = this[PlayerStatsTable.top3Finishes],
        chestsOpened = this[PlayerStatsTable.chestsOpened],
        createdAt = this[PlayerStatsTable.createdAt],
        lastPlayed = this[PlayerStatsTable.lastPlayed],
        lastUpdated = this[PlayerStatsTable.lastSeen]
    )
}
