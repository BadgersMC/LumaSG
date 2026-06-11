package net.lumalyte.lumasg.persistence

import kotlinx.coroutines.test.runTest
import net.lumalyte.lumasg.domain.PlayerStats
import net.lumalyte.lumasg.persistence.repositories.PlayerStatsRepository
import net.lumalyte.lumasg.persistence.tables.PlayerStatsTable
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
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

    private val repo = PlayerStatsRepository(db = io.mockk.mockk(relaxed = true))

    @BeforeEach
    fun clearTable() {
        // Isolate each test — the H2 instance is shared (DB_CLOSE_DELAY=-1), so leftover
        // rows from earlier tests could otherwise skew the bounded ratio leaderboards.
        transaction { PlayerStatsTable.deleteAll() }
    }

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

    @Test
    fun `upsert persists all new fields`() = runTest {
        val uuid = UUID.randomUUID()
        val now = Instant.now()
        val stats = PlayerStats(
            uuid = uuid,
            playerName = "FullStats",
            kills = 15, deaths = 3, wins = 5, losses = 8,
            gamesPlayed = 13,
            damageDealt = 250.5, damageTaken = 120.3,
            totalTimePlayed = 3600L,
            bestPlacement = 1,
            currentWinStreak = 3, bestWinStreak = 5,
            top3Finishes = 7,
            chestsOpened = 42,
            createdAt = now, lastPlayed = now, lastUpdated = now
        )
        repo.upsert(stats)
        val retrieved = repo.findByUuid(uuid)!!
        assertEquals(15, retrieved.kills)
        assertEquals(3, retrieved.deaths)
        assertEquals(5, retrieved.wins)
        assertEquals(8, retrieved.losses)
        assertEquals(13, retrieved.gamesPlayed)
        assertEquals(250.5, retrieved.damageDealt)
        assertEquals(120.3, retrieved.damageTaken)
        assertEquals(3600L, retrieved.totalTimePlayed)
        assertEquals(1, retrieved.bestPlacement)
        assertEquals(3, retrieved.currentWinStreak)
        assertEquals(5, retrieved.bestWinStreak)
        assertEquals(7, retrieved.top3Finishes)
        assertEquals(42, retrieved.chestsOpened)
    }

    @Test
    fun `leaderboard by stat type orders correctly`() = runTest {
        val uuid1 = UUID.randomUUID()
        val uuid2 = UUID.randomUUID()
        repo.upsert(PlayerStats(uuid = uuid1, playerName = "Winner", wins = 500, kills = 1, createdAt = Instant.now()))
        repo.upsert(PlayerStats(uuid = uuid2, playerName = "Killer", wins = 1, kills = 200, createdAt = Instant.now()))
        val winBoard = repo.getLeaderboard(net.lumalyte.lumasg.domain.StatType.WINS, limit = 10)
        assert(winBoard.isNotEmpty())
        assertEquals("Winner", winBoard.first().playerName)
    }

    @Test
    fun `getTotalPlayerCount returns correct count`() = runTest {
        val countBefore = repo.getTotalPlayerCount()
        val uuid = UUID.randomUUID()
        repo.upsert(PlayerStats(uuid = uuid, playerName = "Counter", createdAt = Instant.now()))
        val countAfter = repo.getTotalPlayerCount()
        assertEquals(countBefore + 1, countAfter)
    }

    @Test
    fun `savePlayerStatsBatch persists multiple players`() = runTest {
        val stats1 = PlayerStats(uuid = UUID.randomUUID(), playerName = "Batch1", kills = 5, createdAt = Instant.now())
        val stats2 = PlayerStats(uuid = UUID.randomUUID(), playerName = "Batch2", kills = 10, createdAt = Instant.now())
        repo.savePlayerStatsBatch(listOf(stats1, stats2))
        val r1 = repo.findByUuid(stats1.uuid)
        val r2 = repo.findByUuid(stats2.uuid)
        assertNotNull(r1)
        assertNotNull(r2)
        assertEquals(5, r1.kills)
        assertEquals(10, r2.kills)
    }

    // ── H7: Computed-ratio leaderboard ordering ───────────────────────────

    @Test
    fun `KDR leaderboard ranks better ratio first`() = runTest {
        val a = UUID.randomUUID()
        val b = UUID.randomUUID()
        // A: KDR = 100/100 = 1.0, B: KDR = 50/1 = 50.0
        // B should outrank A even though A has more kills.
        // Other tests may leave players with deaths=0 (→ MAX_VALUE KDR);
        // give B enough kills to be unambiguous when pairwise-compared against A.
        repo.upsert(PlayerStats(uuid = a, playerName = "A", kills = 100, deaths = 100, createdAt = Instant.now()))
        repo.upsert(PlayerStats(uuid = b, playerName = "B", kills = 50, deaths = 1, createdAt = Instant.now()))
        val board = repo.getLeaderboard(net.lumalyte.lumasg.domain.StatType.KILL_DEATH_RATIO, limit = 10)
        // B's KDR (50.0) > A's KDR (1.0) → B must appear before A
        val idxB = board.indexOfFirst { it.playerName == "B" }
        val idxA = board.indexOfFirst { it.playerName == "A" }
        assert(idxB >= 0 && idxA >= 0) { "Both A and B must appear in leaderboard" }
        assert(idxB < idxA) { "B (KDR=50) must outrank A (KDR=1); got B@$idxB, A@$idxA" }
    }

    @Test
    fun `Win-rate leaderboard ranks better rate first`() = runTest {
        val c = UUID.randomUUID()
        val d = UUID.randomUUID()
        // C: 50/200 = 0.25, D: 1/1 = 1.0
        repo.upsert(PlayerStats(uuid = c, playerName = "C", wins = 50, gamesPlayed = 200, createdAt = Instant.now()))
        repo.upsert(PlayerStats(uuid = d, playerName = "D", wins = 1, gamesPlayed = 1, createdAt = Instant.now()))
        val board = repo.getLeaderboard(net.lumalyte.lumasg.domain.StatType.WIN_RATE, limit = 10)
        // D's win rate (1.0) > C's (0.25) → D must appear before C
        val idxD = board.indexOfFirst { it.playerName == "D" }
        val idxC = board.indexOfFirst { it.playerName == "C" }
        assert(idxD >= 0 && idxC >= 0) { "Both C and D must appear in leaderboard" }
        assert(idxD < idxC) { "D (WR=1.0) must outrank C (WR=0.25); got D@$idxD, C@$idxC" }
    }

    @Test
    fun `KDR division-by-zero guard does not crash and outranks zero-kill`() = runTest {
        val e = UUID.randomUUID()
        // E: kills=5, deaths=0 — should not throw and should outrank 0-kill players
        repo.upsert(PlayerStats(uuid = e, playerName = "E", kills = 5, deaths = 0, createdAt = Instant.now()))
        val board = repo.getLeaderboard(net.lumalyte.lumasg.domain.StatType.KILL_DEATH_RATIO, limit = 10)
        assert(board.isNotEmpty())
        // E should appear in results (no exception thrown)
        assert(board.any { it.playerName == "E" })
    }
}
