package net.lumalyte.lumasg.service

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import net.lumalyte.lumasg.config.LumaSGConfig
import net.lumalyte.lumasg.domain.Arena
import net.lumalyte.lumasg.domain.SerializableLocation
import net.lumalyte.lumasg.persistence.repositories.ArenaRepository
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ArenaServiceTest {

    private fun arena(name: String) = Arena(
        name = name,
        displayName = name,
        worldName = "world",
        minPlayers = 2,
        maxPlayers = 24,
        spawnPoints = emptyList(),
        center = SerializableLocation("world", 0.0, 64.0, 0.0)
    )

    @Test
    fun `addToCache caches and persists the arena`() = runTest {
        val repo = mockk<ArenaRepository>(relaxed = true)
        val config = mockk<LumaSGConfig>(relaxed = true)
        // UnconfinedTestDispatcher runs the launched persist eagerly, so coVerify sees it.
        val service = ArenaService(repo, config, CoroutineScope(UnconfinedTestDispatcher(testScheduler)))

        val a = arena("alpha")
        service.addToCache(a)

        assertEquals(a, service.getArena("alpha"))
        coVerify(exactly = 1) { repo.save(a) }
    }

    @Test
    fun `addToCache keeps the arena cached even when every persist attempt fails`() = runTest {
        val repo = mockk<ArenaRepository>(relaxed = true)
        coEvery { repo.save(any()) } throws RuntimeException("DB unavailable")
        val config = mockk<LumaSGConfig>(relaxed = true)
        val service = ArenaService(repo, config, CoroutineScope(UnconfinedTestDispatcher(testScheduler)))

        val a = arena("bravo")
        service.addToCache(a)

        // Cache update is synchronous and must survive a failed async persist (H12 contract).
        assertEquals(a, service.getArena("bravo"))
        // All retry attempts were made before giving up.
        coVerify(exactly = 3) { repo.save(a) }
    }
}
