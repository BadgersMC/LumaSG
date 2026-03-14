package net.lumalyte.lumasg.statistics

import net.badgersmc.nexus.annotations.Service
import net.lumalyte.lumasg.domain.PlayerStats
import net.lumalyte.lumasg.game.Game
import net.lumalyte.lumasg.persistence.repositories.PlayerStatsRepository
import java.util.UUID

@Service
class StatisticsService(
    private val statsRepo: PlayerStatsRepository
) {
    /** Get or create stats for a player. Called when player joins a game. */
    suspend fun getOrCreate(uuid: UUID, playerName: String): PlayerStats {
        return statsRepo.findByUuid(uuid) ?: PlayerStats(
            uuid = uuid,
            playerName = playerName
        ).also { statsRepo.upsert(it) }
    }

    /** Record a kill for the attacker. */
    suspend fun recordKill(attackerUuid: UUID) {
        val stats = statsRepo.findByUuid(attackerUuid) ?: return
        statsRepo.upsert(stats.copy(kills = stats.kills + 1))
    }

    /** Record game end stats for all participants. */
    suspend fun recordGameEnd(game: Game, winnerUuid: UUID?) {
        game.players.values.forEach { gp ->
            val stats = statsRepo.findByUuid(gp.uuid) ?: return@forEach
            statsRepo.upsert(stats.copy(
                kills = stats.kills + gp.kills,
                deaths = stats.deaths + (if (!gp.isAlive) 1 else 0),
                wins = stats.wins + (if (gp.uuid == winnerUuid) 1 else 0),
                gamesPlayed = stats.gamesPlayed + 1,
                damageDealt = stats.damageDealt + gp.damageDealt,
                damageTaken = stats.damageTaken + gp.damageTaken
            ))
        }
    }

    suspend fun getLeaderboard(limit: Int = 10): List<PlayerStats> =
        statsRepo.getLeaderboard(limit)
}
