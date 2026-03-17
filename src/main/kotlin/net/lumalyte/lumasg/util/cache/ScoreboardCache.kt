package net.lumalyte.lumasg.util.cache

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import net.badgersmc.nexus.annotations.Service
import org.bukkit.entity.Player
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * High-performance scoreboard caching system.
 * Reduces scoreboard computation overhead and improves update performance.
 */
@Service
class ScoreboardCache {

    private val logger = LoggerFactory.getLogger(ScoreboardCache::class.java)

    /** Cache for scoreboard line content to avoid string concatenation overhead. */
    private val scoreboardLineCache: Cache<String, String> = Caffeine.newBuilder()
        .maximumSize(1000)
        .expireAfterWrite(Duration.ofSeconds(30))
        .recordStats()
        .build()

    /** Cache for computed placeholder values. */
    private val placeholderCache: Cache<String, String> = Caffeine.newBuilder()
        .maximumSize(500)
        .expireAfterWrite(Duration.ofSeconds(10))
        .recordStats()
        .build()

    /** Cache for formatted time strings (up to 1 hour of formats). */
    private val timeFormatCache: Cache<Int, String> = Caffeine.newBuilder()
        .maximumSize(3600)
        .expireAfterWrite(Duration.ofMinutes(5))
        .recordStats()
        .build()

    /** Per-player scoreboard data. */
    private val playerScoreboardData = ConcurrentHashMap<UUID, PlayerScoreboardData>()

    /** Player-specific scoreboard data container. */
    class PlayerScoreboardData {
        val cachedLines = ConcurrentHashMap<String, String>()
        var lastUpdate: Long = 0L
            private set
        var needsRefresh: Boolean = true
            private set

        fun setCachedLine(key: String, value: String) {
            cachedLines[key] = value
        }

        fun getCachedLine(key: String): String? = cachedLines[key]

        fun markForRefresh() {
            needsRefresh = true
            lastUpdate = System.currentTimeMillis()
        }

        fun needsRefresh(updateInterval: Long): Boolean =
            needsRefresh || (System.currentTimeMillis() - lastUpdate) > updateInterval

        fun refreshCompleted() {
            needsRefresh = false
            lastUpdate = System.currentTimeMillis()
        }
    }

    // ── Public API ──────────────────────────────────────────────────────────

    /** Gets cached scoreboard line content, generating via [generator] if absent. */
    fun getCachedScoreboardLine(key: String, generator: () -> String): String =
        scoreboardLineCache.get(key) { generator() }!!

    /** Gets cached placeholder value, generating via [generator] if absent. */
    fun getCachedPlaceholder(key: String, generator: () -> String): String =
        placeholderCache.get(key) { generator() }!!

    /** Gets a cached formatted time string (MM:SS). */
    fun getCachedTimeFormat(timeInSeconds: Int): String =
        timeFormatCache.get(timeInSeconds) { time ->
            val minutes = time / 60
            val seconds = time % 60
            "%02d:%02d".format(minutes, seconds)
        }!!

    /** Checks if a player's scoreboard needs updating based on the interval. */
    fun needsScoreboardUpdate(playerId: UUID, updateInterval: Long): Boolean =
        getOrCreatePlayerData(playerId).needsRefresh(updateInterval)

    /** Marks a player's scoreboard as needing refresh. */
    fun markScoreboardForRefresh(playerId: UUID) {
        getOrCreatePlayerData(playerId).markForRefresh()
    }

    /** Marks a player's scoreboard refresh as completed. */
    fun markScoreboardRefreshCompleted(playerId: UUID) {
        getOrCreatePlayerData(playerId).refreshCompleted()
    }

    /** Caches a scoreboard line for a specific player. */
    fun cachePlayerScoreboardLine(playerId: UUID, lineKey: String, lineContent: String) {
        getOrCreatePlayerData(playerId).setCachedLine(lineKey, lineContent)
    }

    /** Gets a cached scoreboard line for a specific player, or null if not cached. */
    fun getCachedPlayerScoreboardLine(playerId: UUID, lineKey: String): String? =
        playerScoreboardData[playerId]?.getCachedLine(lineKey)

    // ── Invalidation ────────────────────────────────────────────────────────

    /** Invalidates all cached data for a player. */
    fun invalidatePlayer(playerId: UUID) {
        playerScoreboardData.remove(playerId)

        val playerPrefix = playerId.toString()
        scoreboardLineCache.asMap().keys.removeIf { it.startsWith(playerPrefix) }
        placeholderCache.asMap().keys.removeIf { it.startsWith(playerPrefix) }
    }

    /** Invalidates cached placeholders matching a prefix. */
    fun invalidatePlaceholders(prefix: String) {
        placeholderCache.asMap().keys.removeIf { it.startsWith(prefix) }
    }

    // ── Optimized helpers ───────────────────────────────────────────────────

    /** Creates an optimized, cached scoreboard line for a game context. */
    fun createOptimizedScoreboardLine(gameId: String, lineType: String, value: String): String {
        val cacheKey = "$gameId:$lineType:$value"
        return getCachedScoreboardLine(cacheKey) {
            when (lineType) {
                "players" -> "\u00a7f\u00a7lPlayers: \u00a7e$value"
                "time" -> "\u00a7f\u00a7lTime: \u00a7c$value"
                "arena" -> "\u00a7f\u00a7lArena: \u00a7b$value"
                "alive" -> "\u00a7f\u00a7lAlive: \u00a7a$value"
                else -> "\u00a7f$value"
            }
        }
    }

    /** Bulk updates scoreboards for multiple players, only refreshing when lines actually changed. */
    fun bulkUpdateScoreboards(players: Iterable<Player>, lineUpdates: Map<String, String>) {
        for (player in players) {
            val playerId = player.uniqueId
            val data = getOrCreatePlayerData(playerId)

            val hasChanges = lineUpdates.any { (key, value) ->
                data.getCachedLine(key) != value
            }

            if (hasChanges) {
                lineUpdates.forEach { (key, value) -> data.setCachedLine(key, value) }
                data.markForRefresh()
            }
        }
    }

    // ── Maintenance ─────────────────────────────────────────────────────────

    /** Clears all caches and player data. */
    fun clearAllCaches() {
        scoreboardLineCache.invalidateAll()
        placeholderCache.invalidateAll()
        timeFormatCache.invalidateAll()
        playerScoreboardData.clear()
        logger.info("All scoreboard caches cleared")
    }

    /** Returns comprehensive cache statistics. */
    fun getCacheStats(): String {
        val lineStats = scoreboardLineCache.stats()
        val phStats = placeholderCache.stats()
        val tfStats = timeFormatCache.stats()

        return """
            Scoreboard Cache Stats:
            Scoreboard Lines - Size: ${scoreboardLineCache.estimatedSize()}, Hit Rate: ${"%.2f".format(lineStats.hitRate() * 100)}%
            Placeholders - Size: ${placeholderCache.estimatedSize()}, Hit Rate: ${"%.2f".format(phStats.hitRate() * 100)}%
            Time Formats - Size: ${timeFormatCache.estimatedSize()}, Hit Rate: ${"%.2f".format(tfStats.hitRate() * 100)}%
            Player Data Entries: ${playerScoreboardData.size}
        """.trimIndent()
    }

    /** Performs cache maintenance: evicts expired entries and cleans up inactive players. */
    fun performMaintenance() {
        scoreboardLineCache.cleanUp()
        placeholderCache.cleanUp()
        timeFormatCache.cleanUp()

        // Clean up inactive player data (older than 15 minutes)
        val cutoffTime = System.currentTimeMillis() - Duration.ofMinutes(15).toMillis()
        playerScoreboardData.entries.removeIf { it.value.lastUpdate < cutoffTime }

        logger.debug("Scoreboard cache maintenance completed")
    }

    // ── Internal ────────────────────────────────────────────────────────────

    private fun getOrCreatePlayerData(playerId: UUID): PlayerScoreboardData =
        playerScoreboardData.computeIfAbsent(playerId) { PlayerScoreboardData() }
}
