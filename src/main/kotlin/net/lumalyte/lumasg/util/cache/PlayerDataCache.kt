package net.lumalyte.lumasg.util.cache

import com.github.benmanes.caffeine.cache.AsyncLoadingCache
import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.LoadingCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.future.future
import net.badgersmc.nexus.annotations.PostConstruct
import net.badgersmc.nexus.annotations.PreDestroy
import net.badgersmc.nexus.annotations.Service
import net.lumalyte.lumasg.domain.PlayerStats
import net.lumalyte.lumasg.persistence.repositories.PlayerStatsRepository
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.concurrent.ForkJoinPool

@Service
class PlayerDataCache(
    private val plugin: JavaPlugin,
    private val playerStatsRepository: PlayerStatsRepository,
    private val nexusScope: CoroutineScope
) {
    private val logger = LoggerFactory.getLogger(PlayerDataCache::class.java)
    private val executor: Executor = ForkJoinPool.commonPool()

    private lateinit var statsCache: AsyncLoadingCache<UUID, PlayerStats>
    private lateinit var permissionCache: LoadingCache<String, Boolean>

    private val displayNameCache: Cache<UUID, String> = Caffeine.newBuilder()
        .maximumSize(1_000)
        .expireAfterWrite(Duration.ofMinutes(30))
        .recordStats()
        .build()

    @PostConstruct
    fun init() {
        statsCache = Caffeine.newBuilder()
            .maximumSize(500)
            .expireAfterWrite(Duration.ofMinutes(30))
            .expireAfterAccess(Duration.ofMinutes(15))
            .refreshAfterWrite(Duration.ofMinutes(10))
            .recordStats()
            .executor(executor)
            .buildAsync { uuid: UUID, exec: Executor ->
                nexusScope.future { loadPlayerStatsFromDatabase(uuid) }
            }

        permissionCache = Caffeine.newBuilder()
            .maximumSize(2_000)
            .expireAfterWrite(Duration.ofMinutes(10))
            .expireAfterAccess(Duration.ofMinutes(5))
            .recordStats()
            .build(::loadPermissionFromBukkit)

        logger.info("PlayerDataCache initialized with refresh-ahead patterns")
    }

    @PreDestroy
    fun shutdown() {
        clearCache()
        logger.info("PlayerDataCache shut down")
    }

    /**
     * Gets cached player statistics with automatic loading and refresh-ahead.
     * Returns a default [PlayerStats] on failure.
     */
    fun getCachedStats(uuid: UUID): CompletableFuture<PlayerStats> =
        statsCache.get(uuid).exceptionally { throwable ->
            logger.warn("Failed to load stats for UUID: {}", uuid, throwable)
            PlayerStats(uuid = uuid, playerName = "Unknown Player")
        }

    /**
     * Gets cached permission check result with automatic loading.
     */
    fun getCachedPermission(player: Player, permission: String): Boolean {
        val key = "${player.uniqueId}:$permission"
        return permissionCache.get(key) ?: false
    }

    /**
     * Bulk loads and caches multiple permissions for a player.
     */
    fun bulkCachePermissions(player: Player, vararg permissions: String) {
        for (permission in permissions) {
            val key = "${player.uniqueId}:$permission"
            permissionCache.put(key, player.hasPermission(permission))
        }
    }

    /**
     * Gets cached display name with fallback loading.
     */
    fun getCachedDisplayName(uuid: UUID): CompletableFuture<String> {
        displayNameCache.getIfPresent(uuid)?.let {
            return CompletableFuture.completedFuture(it)
        }

        return CompletableFuture.supplyAsync({
            val player = plugin.server.getPlayer(uuid)
            val displayName = player?.displayName()?.toString()
                ?: plugin.server.getOfflinePlayer(uuid).name

            if (displayName != null) {
                displayNameCache.put(uuid, displayName)
            }
            displayName ?: "Unknown Player"
        }, executor)
    }

    /**
     * Invalidates all cached data for a specific player.
     */
    fun invalidatePlayer(uuid: UUID) {
        statsCache.synchronous().invalidate(uuid)
        displayNameCache.invalidate(uuid)

        // Invalidate all permission entries for this player
        val prefix = uuid.toString()
        permissionCache.asMap().keys.removeIf { it.startsWith(prefix) }
    }

    /**
     * Forces refresh of player statistics by invalidating and re-loading.
     */
    fun refreshPlayerStats(uuid: UUID): CompletableFuture<PlayerStats> {
        statsCache.synchronous().invalidate(uuid)
        return statsCache.get(uuid)
    }

    /**
     * Preloads essential data for a player when they join.
     */
    fun preloadPlayerData(player: Player) {
        val uuid = player.uniqueId

        // Asynchronously preload stats
        statsCache.get(uuid)

        // Preload display name
        displayNameCache.put(uuid, player.displayName().toString())

        // Bulk preload common permissions
        bulkCachePermissions(
            player,
            "lumasg.admin", "lumasg.moderator", "lumasg.vip",
            "lumasg.join", "lumasg.spectate", "lumasg.setup.game",
            "lumasg.arena.create", "lumasg.arena.edit", "lumasg.arena.delete"
        )

        logger.debug("Preloaded essential data for player: {}", player.name)
    }

    /**
     * Clears all caches.
     */
    fun clearCache() {
        statsCache.synchronous().invalidateAll()
        permissionCache.invalidateAll()
        displayNameCache.invalidateAll()
        logger.info("All player data caches cleared")
    }

    /**
     * Returns human-readable cache statistics for monitoring.
     */
    fun getCacheStats(): String {
        val statsSync = statsCache.synchronous()
        val sStats = statsSync.stats()
        val pStats = permissionCache.stats()
        val dStats = displayNameCache.stats()

        return buildString {
            appendLine("Player Data Cache Stats:")
            appendLine("  Stats Cache — Size: ${statsSync.estimatedSize()}, Hit Rate: ${"%.2f".format(sStats.hitRate() * 100)}%, Loads: ${sStats.loadCount()}")
            appendLine("  Permission Cache — Size: ${permissionCache.estimatedSize()}, Hit Rate: ${"%.2f".format(pStats.hitRate() * 100)}%, Loads: ${pStats.loadCount()}")
            append("  Display Name Cache — Size: ${displayNameCache.estimatedSize()}, Hit Rate: ${"%.2f".format(dStats.hitRate() * 100)}%")
        }
    }

    // ── Private helpers ──────────────────────────────────────────────────

    private suspend fun loadPlayerStatsFromDatabase(uuid: UUID): PlayerStats =
        try {
            playerStatsRepository.findByUuid(uuid)
                ?: PlayerStats(uuid = uuid, playerName = "Unknown Player")
        } catch (e: Exception) {
            logger.error("Failed to load player stats from database for UUID: {}", uuid, e)
            PlayerStats(uuid = uuid, playerName = "Unknown Player")
        }

    private fun loadPermissionFromBukkit(permissionKey: String): Boolean =
        try {
            val (uuidStr, permission) = permissionKey.split(":", limit = 2)
            val uuid = UUID.fromString(uuidStr)
            val player = plugin.server.getPlayer(uuid)
            player?.hasPermission(permission) ?: false
        } catch (e: Exception) {
            logger.warn("Failed to load permission: {}", permissionKey, e)
            false
        }
}
