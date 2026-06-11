package net.lumalyte.lumasg.statistics

import net.badgersmc.nexus.annotations.Service
import net.lumalyte.lumasg.config.LumaSGConfig
import net.lumalyte.lumasg.domain.LootMode
import net.lumalyte.lumasg.domain.PlayerStats
import net.lumalyte.lumasg.domain.StatType
import net.lumalyte.lumasg.game.Game
import net.lumalyte.lumasg.persistence.repositories.PlayerStatsRepository
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Service
class StatisticsService(
    private val statsRepo: PlayerStatsRepository,
    private val config: LumaSGConfig
) {
    private data class CacheKey(val uuid: UUID, val mode: LootMode)
    private val cache = ConcurrentHashMap<CacheKey, PlayerStats>()

    /** Get or create stats for a player. Called when player joins a game. */
    suspend fun getOrCreate(uuid: UUID, playerName: String, lootMode: LootMode = LootMode.MODERN): PlayerStats {
        val key = CacheKey(uuid, lootMode)
        cache[key]?.let { return it }
        val stats = statsRepo.findByUuid(uuid, lootMode) ?: PlayerStats(
            uuid = uuid,
            playerName = playerName,
            lootMode = lootMode
        ).also { statsRepo.upsert(it) }
        cache[key] = stats
        return stats
    }

    /** Get cached stats without DB lookup. */
    fun getCachedPlayerStats(uuid: UUID, lootMode: LootMode = LootMode.MODERN): PlayerStats? =
        cache[CacheKey(uuid, lootMode)]

    /** Preload stats for a player (call on join). */
    suspend fun preloadPlayerStats(uuid: UUID, playerName: String) {
        // Preload all three modes
        for (mode in LootMode.entries) {
            getOrCreate(uuid, playerName, mode)
        }
    }

    /** Remove player from cache (call on quit). */
    fun uncachePlayer(uuid: UUID) {
        LootMode.entries.forEach { mode -> cache.remove(CacheKey(uuid, mode)) }
    }

    /** Record a kill for the attacker. */
    suspend fun recordKill(attackerUuid: UUID, lootMode: LootMode = LootMode.MODERN) {
        val stats = statsRepo.findByUuid(attackerUuid, lootMode) ?: return
        statsRepo.upsert(stats.copy(kills = stats.kills + 1))
    }

    /** Record a death for a player. */
    suspend fun recordDeath(uuid: UUID, lootMode: LootMode = LootMode.MODERN) {
        val stats = statsRepo.findByUuid(uuid, lootMode) ?: return
        statsRepo.upsert(stats.copy(deaths = stats.deaths + 1))
    }

    /** Record damage dealt by a player. */
    suspend fun recordDamageDealt(uuid: UUID, amount: Double, lootMode: LootMode = LootMode.MODERN) {
        val stats = statsRepo.findByUuid(uuid, lootMode) ?: return
        statsRepo.upsert(stats.copy(damageDealt = stats.damageDealt + amount))
    }

    /** Record damage taken by a player. */
    suspend fun recordDamageTaken(uuid: UUID, amount: Double, lootMode: LootMode = LootMode.MODERN) {
        val stats = statsRepo.findByUuid(uuid, lootMode) ?: return
        statsRepo.upsert(stats.copy(damageTaken = stats.damageTaken + amount))
    }

    /** Record a chest opened by a player. */
    suspend fun recordChestOpened(uuid: UUID, lootMode: LootMode = LootMode.MODERN) {
        val stats = statsRepo.findByUuid(uuid, lootMode) ?: return
        statsRepo.upsert(stats.copy(chestsOpened = stats.chestsOpened + 1))
    }

    /** Record game end stats for all participants. */
    suspend fun recordGameEnd(game: Game, winnerUuid: UUID?) {
        if (!config.statistics.enabled) return
        val lootMode = game.lootMode
        val totalPlayers = game.players.size

        game.players.values.forEach { gp ->
            val stats = statsRepo.findByUuid(gp.uuid, lootMode) ?: PlayerStats(
                uuid = gp.uuid,
                playerName = gp.name,
                lootMode = lootMode
            )
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
            cache.remove(CacheKey(gp.uuid, lootMode))
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
        LootMode.entries.forEach { mode ->
            cache[CacheKey(uuid, mode)]?.let { statsRepo.upsert(it) }
        }
    }

    suspend fun getLeaderboard(statType: StatType, limit: Int = 10, lootMode: LootMode? = null): List<PlayerStats> =
        statsRepo.getLeaderboard(statType, limit, lootMode)

    suspend fun getLeaderboard(limit: Int = 10): List<PlayerStats> =
        statsRepo.getLeaderboard(limit)

    suspend fun getTotalPlayerCount(): Long =
        statsRepo.getTotalPlayerCount()

    /** Get combined stats across all modes for a player. */
    suspend fun getAggregatedStats(uuid: UUID, playerName: String): PlayerStats {
        val allModeStats = LootMode.entries.mapNotNull { mode ->
            statsRepo.findByUuid(uuid, mode)
        }
        if (allModeStats.isEmpty()) return PlayerStats(uuid = uuid, playerName = playerName)

        return PlayerStats(
            uuid = uuid,
            playerName = playerName,
            lootMode = LootMode.MODERN, // placeholder — aggregate has no single mode
            kills = allModeStats.sumOf { it.kills },
            deaths = allModeStats.sumOf { it.deaths },
            wins = allModeStats.sumOf { it.wins },
            losses = allModeStats.sumOf { it.losses },
            gamesPlayed = allModeStats.sumOf { it.gamesPlayed },
            damageDealt = allModeStats.sumOf { it.damageDealt },
            damageTaken = allModeStats.sumOf { it.damageTaken },
            totalTimePlayed = allModeStats.sumOf { it.totalTimePlayed },
            bestPlacement = allModeStats.minOfOrNull { it.bestPlacement.takeIf { p -> p > 0 } ?: Int.MAX_VALUE }?.takeIf { it != Int.MAX_VALUE } ?: 0,
            currentWinStreak = allModeStats.maxOf { it.currentWinStreak },
            bestWinStreak = allModeStats.maxOf { it.bestWinStreak },
            top3Finishes = allModeStats.sumOf { it.top3Finishes },
            chestsOpened = allModeStats.sumOf { it.chestsOpened }
        )
    }
}
