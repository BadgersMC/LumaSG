package net.lumalyte.lumasg.persistence

import kotlinx.coroutines.test.runTest
import net.lumalyte.lumasg.domain.PlayerStats
import net.lumalyte.lumasg.persistence.repositories.PlayerStatsRepository
import net.lumalyte.lumasg.persistence.tables.PlayerStatsTable
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class PlayerStatsRepositoryTest {

    companion object {
        @BeforeAll @JvmStatic
        fun setup() {
            Database.connect("jdbc:h2:mem:test_stats;DB_CLOSE_DELAY=-1", driver = "org.h2.Driver")
            transaction { SchemaUtils.create(PlayerStatsTable) }
        }

        @AfterAll @JvmStatic
        fun teardown() {
            transaction { SchemaUtils.drop(PlayerStatsTable) }
        }
    }

    private val repo = PlayerStatsRepository()

    @Test
    fun `upsert and retrieve player stats`() = runTest {
        val uuid = UUID.randomUUID()
        val stats = PlayerStats(
            uuid = uuid,
            playerName = "TestPlayer",
            kills = 5, deaths = 2, wins = 1,
            gamesPlayed = 3,
            damageDealt = 100.0, damageTaken = 50.0,
            createdAt = Instant.now()
        )
        repo.upsert(stats)
        val retrieved = repo.findByUuid(uuid)!!
        assertNotNull(retrieved)
        assertEquals(5, retrieved.kills)
        assertEquals("TestPlayer", retrieved.playerName)
        assertEquals(1, retrieved.wins)
    }

    @Test
    fun `returns null for unknown player`() = runTest {
        val result = repo.findByUuid(UUID.randomUUID())
        assertNull(result)
    }

    @Test
    fun `upsert updates existing record`() = runTest {
        val uuid = UUID.randomUUID()
        val stats = PlayerStats(uuid = uuid, playerName = "Player2", kills = 1, createdAt = Instant.now())
        repo.upsert(stats)
        repo.upsert(stats.copy(kills = 10))
        val retrieved = repo.findByUuid(uuid)!!
        assertNotNull(retrieved)
        assertEquals(10, retrieved.kills)
    }

    @Test
    fun `leaderboard returns players ordered by kills`() = runTest {
        val uuid1 = UUID.randomUUID()
        val uuid2 = UUID.randomUUID()
        repo.upsert(PlayerStats(uuid = uuid1, playerName = "TopKiller", kills = 100, createdAt = Instant.now()))
        repo.upsert(PlayerStats(uuid = uuid2, playerName = "LowKiller", kills = 1, createdAt = Instant.now()))
        val board = repo.getLeaderboard(limit = 10)
        assert(board.isNotEmpty())
        assertEquals(board.first().kills, board.maxOf { it.kills })
    }
}
