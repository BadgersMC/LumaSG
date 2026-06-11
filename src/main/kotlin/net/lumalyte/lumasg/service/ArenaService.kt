package net.lumalyte.lumasg.service

import net.badgersmc.nexus.annotations.PostConstruct
import net.badgersmc.nexus.annotations.PreDestroy
import net.badgersmc.nexus.annotations.Service
import net.lumalyte.lumasg.config.LumaSGConfig
import net.lumalyte.lumasg.domain.Arena
import net.lumalyte.lumasg.domain.SerializableLocation
import net.lumalyte.lumasg.persistence.repositories.ArenaRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.bukkit.Location
import org.bukkit.entity.Player
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Service
class ArenaService(
    private val arenaRepo: ArenaRepository,
    private val config: LumaSGConfig,
    private val scope: CoroutineScope
) {
    private val logger = LoggerFactory.getLogger(ArenaService::class.java)
    private val cache = ConcurrentHashMap<String, Arena>()
    private val selectedArenas = ConcurrentHashMap<UUID, String>()

    private companion object {
        const val MAX_PERSIST_ATTEMPTS = 3
    }

    @PostConstruct
    suspend fun loadAll() {
        arenaRepo.findAll().forEach { cache[it.name.lowercase()] = it }
        logger.info("Loaded ${cache.size} arenas from database")
    }

    @PreDestroy
    fun stop() {
        cache.clear()
        selectedArenas.clear()
        logger.info("ArenaService shut down")
    }

    fun getAvailableArenas(): List<Arena> = cache.values.filter { it.enabled }

    fun getAllArenas(): List<Arena> = cache.values.toList()

    fun getArena(name: String): Arena? = cache[name.lowercase()]

    suspend fun save(arena: Arena) {
        arenaRepo.save(arena)
        cache[arena.name.lowercase()] = arena
    }

    suspend fun saveAll() {
        cache.values.forEach { arenaRepo.save(it) }
    }

    /**
     * Update the in-memory arena and persist it asynchronously.
     *
     * Callers are synchronous Bukkit event handlers (e.g. the admin wand), so the DB write
     * is fire-and-forget on the service scope rather than a suspend call. Without the
     * persist, wand edits survived only until the next reload/crash (H12).
     */
    fun addToCache(arena: Arena) {
        cache[arena.name.lowercase()] = arena
        scope.launch {
            // Retry transient DB failures with exponential backoff so a brief blip doesn't
            // silently drop the edit (the cache is already updated above for immediate use).
            var lastError: Throwable? = null
            for (attempt in 1..MAX_PERSIST_ATTEMPTS) {
                val result = runCatching { arenaRepo.save(arena) }
                if (result.isSuccess) { lastError = null; break }
                lastError = result.exceptionOrNull()
                logger.warn("Persist attempt $attempt/$MAX_PERSIST_ATTEMPTS failed for arena '${arena.name}'", lastError)
                if (attempt < MAX_PERSIST_ATTEMPTS) delay(100L * (1L shl (attempt - 1)))
            }
            if (lastError != null) {
                logger.error("Gave up persisting arena '${arena.name}' after $MAX_PERSIST_ATTEMPTS attempts; edit lives only in cache", lastError)
            }
        }
    }

    suspend fun removeArena(arena: Arena) {
        cache.remove(arena.name.lowercase())
        arenaRepo.delete(arena.name)
        logger.info("Removed arena '${arena.name}'")
    }

    suspend fun createArena(name: String, center: Location, radius: Int): Arena {
        val arena = Arena(
            name = name,
            displayName = name,
            worldName = center.world?.name ?: "world",
            minPlayers = config.arena.defaultMinPlayers,
            maxPlayers = config.arena.defaultMaxPlayers,
            spawnPoints = emptyList(),
            center = SerializableLocation.fromBukkit(center),
            radius = radius.toDouble()
        )
        save(arena)
        logger.info("Created arena '$name' at ${center.blockX}, ${center.blockY}, ${center.blockZ}")
        return arena
    }

    fun getSelectedArena(player: Player): Arena? =
        selectedArenas[player.uniqueId]?.let { getArena(it) }

    fun setSelectedArena(player: Player, arena: Arena) {
        selectedArenas[player.uniqueId] = arena.name
    }

    fun clearSelectedArena(player: Player) {
        selectedArenas.remove(player.uniqueId)
    }
}
