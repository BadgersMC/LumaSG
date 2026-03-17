package net.lumalyte.lumasg.statistics

import net.badgersmc.nexus.annotations.Service
import net.lumalyte.lumasg.config.LumaSGConfig
import net.lumalyte.lumasg.domain.PlayerStats
import net.lumalyte.lumasg.domain.StatType
import net.lumalyte.lumasg.game.Game
import net.lumalyte.lumasg.persistence.repositories.PlayerStatsRepository
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Service
class StatisticsService(
    private val statsRepo: PlayerStatsRepository,
    private val config: LumaSGConfig
) {
    private val cache = ConcurrentHashMap<UUID, PlayerStats>()

    /** Get or create stats for a player. Called when player joins a game. */
    suspend fun getOrCreate(uuid: UUID, playerName: String): PlayerStats {
        cache[uuid]?.let { return it }
        val stats = statsRepo.findByUuid(uuid) ?: PlayerStats(
            uuid = uuid,
            playerName = playerName
        ).also { statsRepo.upsert(it) }
        cache[uuid] = stats
        return stats
    }

    /** Get cached stats without DB lookup. */
    fun getCachedPlayerStats(uuid: UUID): PlayerStats? = cache[uuid]

    /** Preload stats for a player (call on join). */
    suspend fun preloadPlayerStats(uuid: UUID, playerName: String) {
        getOrCreate(uuid, playerName)
    }

    /** Remove player from cache (call on quit). */
    fun uncachePlayer(uuid: UUID) {
        cache.remove(uuid)
    }

    /** Record a kill for the attacker. */
    suspend fun recordKill(attackerUuid: UUID) {
        val stats = statsRepo.findByUuid(attackerUuid) ?: return
        statsRepo.upsert(stats.copy(kills = stats.kills + 1))
    }

    /** Record a death for a player. */
    suspend fun recordDeath(uuid: UUID) {
        val stats = statsRepo.findByUuid(uuid) ?: return
        statsRepo.upsert(stats.copy(deaths = stats.deaths + 1))
    }

    /** Record damage dealt by a player. */
    suspend fun recordDamageDealt(uuid: UUID, amount: Double) {
        val stats = statsRepo.findByUuid(uuid) ?: return
        statsRepo.upsert(stats.copy(damageDealt = stats.damageDealt + amount))
    }

    /** Record damage taken by a player. */
    suspend fun recordDamageTaken(uuid: UUID, amount: Double) {
        val stats = statsRepo.findByUuid(uuid) ?: return
        statsRepo.upsert(stats.copy(damageTaken = stats.damageTaken + amount))
    }

    /** Record a chest opened by a player. */
    suspend fun recordChestOpened(uuid: UUID) {
        val stats = statsRepo.findByUuid(uuid) ?: return
        statsRepo.upsert(stats.copy(chestsOpened = stats.chestsOpened + 1))
    }

    /** Record game end stats for all participants. */
    suspend fun recordGameEnd(game: Game, winnerUuid: UUID?) {
        if (!config.statistics.enabled) return
        val gameTime = game.getTimeRemaining().toLong()
        val totalPlayers = game.players.size

        game.players.values.forEach { gp ->
            val stats = statsRepo.findByUuid(gp.uuid) ?: return@forEach
            val isWinner = gp.uuid == winnerUuid
            val isDead = !gp.isAlive
            val placement = gp.placement.takeIf { it > 0 } ?: totalPlayers
            val newWinStreak = if (isWinner) stats.currentWinStreak + 1 else 0

            val now = Instant.now()
            statsRepo.upsert(stats.copy(
                kills = stats.kills + gp.kills,
                deaths = stats.deaths + (if (isDead) 1 else 0),
                wins = stats.wins + (if (isWinner) 1 else 0),
                losses = stats.losses + (if (!isWinner) 1 else 0),
                gamesPlayed = stats.gamesPlayed + 1,
                damageDealt = stats.damageDealt + gp.damageDealt,
                damageTaken = stats.damageTaken + gp.damageTaken,
                chestsOpened = stats.chestsOpened + gp.chestsOpened,
                bestPlacement = if (stats.bestPlacement == 0 || placement < stats.bestPlacement) placement else stats.bestPlacement,
                currentWinStreak = newWinStreak,
                bestWinStreak = maxOf(stats.bestWinStreak, newWinStreak),
                top3Finishes = stats.top3Finishes + (if (placement <= 3) 1 else 0),
                lastPlayed = now,
                lastUpdated = now
            ))

            // Update cache
            cache.remove(gp.uuid)
        }
    }

    /** Save all cached stats to database. */
    suspend fun saveAllPendingStats() {
        val pending = cache.values.toList()
        if (pending.isNotEmpty()) {
            statsRepo.savePlayerStatsBatch(pending)
        }
    }

    /** Save a single player's stats. */
    suspend fun savePlayerStats(uuid: UUID) {
        cache[uuid]?.let { statsRepo.upsert(it) }
    }

    suspend fun getLeaderboard(statType: StatType, limit: Int = 10): List<PlayerStats> =
        statsRepo.getLeaderboard(statType, limit)

    suspend fun getLeaderboard(limit: Int = 10): List<PlayerStats> =
        statsRepo.getLeaderboard(limit)

    suspend fun getTotalPlayerCount(): Long =
        statsRepo.getTotalPlayerCount()
}
