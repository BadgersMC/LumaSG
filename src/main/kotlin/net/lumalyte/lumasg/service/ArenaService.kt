package net.lumalyte.lumasg.service

import net.badgersmc.nexus.annotations.PostConstruct
import net.badgersmc.nexus.annotations.Service
import net.lumalyte.lumasg.domain.Arena
import net.lumalyte.lumasg.persistence.repositories.ArenaRepository
import java.util.concurrent.ConcurrentHashMap

@Service
class ArenaService(private val arenaRepo: ArenaRepository) {
    private val cache = ConcurrentHashMap<String, Arena>()

    @PostConstruct
    suspend fun loadAll() {
        arenaRepo.findAll().forEach { cache[it.name] = it }
    }

    fun getAvailableArenas(): List<Arena> = cache.values.filter { it.enabled }
    fun getArena(name: String): Arena? = cache[name]

    suspend fun save(arena: Arena) {
        arenaRepo.save(arena)
        cache[arena.name] = arena
    }

    fun addToCache(arena: Arena) {
        cache[arena.name] = arena
    }
}
