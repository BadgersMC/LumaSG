package net.lumalyte.lumasg.domain

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DomainModelTest {

    // ── PlayerStats ──────────────────────────────────────────────────────

    @Nested
    inner class PlayerStatsTest {
        @Test
        fun `kdr with zero deaths returns kills as double`() {
            val stats = PlayerStats(uuid = UUID.randomUUID(), playerName = "Test", kills = 10, deaths = 0)
            assertEquals(10.0, stats.kdr)
        }

        @Test
        fun `kdr computes correctly`() {
            val stats = PlayerStats(uuid = UUID.randomUUID(), playerName = "Test", kills = 10, deaths = 5)
            assertEquals(2.0, stats.kdr)
        }

        @Test
        fun `winRate with zero games returns 0`() {
            val stats = PlayerStats(uuid = UUID.randomUUID(), playerName = "Test", gamesPlayed = 0, wins = 0)
            assertEquals(0.0, stats.winRate)
        }

        @Test
        fun `winRate computes correctly`() {
            val stats = PlayerStats(uuid = UUID.randomUUID(), playerName = "Test", wins = 3, gamesPlayed = 10)
            assertEquals(0.3, stats.winRate)
        }

        @Test
        fun `top3Rate computes correctly`() {
            val stats = PlayerStats(uuid = UUID.randomUUID(), playerName = "Test", top3Finishes = 5, gamesPlayed = 10)
            assertEquals(0.5, stats.top3Rate)
        }

        @Test
        fun `averageKillsPerGame computes correctly`() {
            val stats = PlayerStats(uuid = UUID.randomUUID(), playerName = "Test", kills = 20, gamesPlayed = 4)
            assertEquals(5.0, stats.averageKillsPerGame)
        }

        @Test
        fun `averageGameTime with zero games returns 0`() {
            val stats = PlayerStats(uuid = UUID.randomUUID(), playerName = "Test", gamesPlayed = 0)
            assertEquals(0.0, stats.averageGameTime)
        }

        @Test
        fun `defaults are all zeroed`() {
            val stats = PlayerStats(uuid = UUID.randomUUID(), playerName = "Test")
            assertEquals(0, stats.kills)
            assertEquals(0, stats.deaths)
            assertEquals(0, stats.wins)
            assertEquals(0, stats.losses)
            assertEquals(0, stats.gamesPlayed)
            assertEquals(0.0, stats.damageDealt)
            assertEquals(0.0, stats.damageTaken)
            assertEquals(0L, stats.totalTimePlayed)
            assertEquals(0, stats.bestPlacement)
            assertEquals(0, stats.currentWinStreak)
            assertEquals(0, stats.bestWinStreak)
            assertEquals(0, stats.top3Finishes)
            assertEquals(0, stats.chestsOpened)
        }
    }

    // ── GameMode ─────────────────────────────────────────────────────────

    @Nested
    inner class GameModeTest {
        @Test
        fun `Solo is not team mode`() {
            assertEquals(false, GameMode.Solo.isTeamMode)
            assertEquals(1, GameMode.Solo.teamSize)
        }

        @Test
        fun `Duos is team mode`() {
            assertTrue(GameMode.Duos.isTeamMode)
            assertEquals(2, GameMode.Duos.teamSize)
        }

        @Test
        fun `Trios is team mode`() {
            assertTrue(GameMode.Trios.isTeamMode)
            assertEquals(3, GameMode.Trios.teamSize)
        }

        @Test
        fun `entries contains all modes`() {
            assertEquals(3, GameMode.entries.size)
            assertTrue(GameMode.entries.contains(GameMode.Solo))
            assertTrue(GameMode.entries.contains(GameMode.Duos))
            assertTrue(GameMode.entries.contains(GameMode.Trios))
        }

        @Test
        fun `fromDisplayName finds existing mode`() {
            assertEquals(GameMode.Solo, GameMode.fromDisplayName("Solo"))
            assertEquals(GameMode.Duos, GameMode.fromDisplayName("duos"))
            assertEquals(GameMode.Trios, GameMode.fromDisplayName("TRIOS"))
        }

        @Test
        fun `fromDisplayName returns null for unknown`() {
            assertNull(GameMode.fromDisplayName("Quads"))
        }

        @Test
        fun `getMaxTeams computes correctly`() {
            assertEquals(12, GameMode.Duos.getMaxTeams(24))
            assertEquals(8, GameMode.Trios.getMaxTeams(24))
            assertEquals(24, GameMode.Solo.getMaxTeams(24))
        }

        @Test
        fun `getIdealPlayerCount rounds down to team multiple`() {
            assertEquals(24, GameMode.Duos.getIdealPlayerCount(25))
            assertEquals(24, GameMode.Trios.getIdealPlayerCount(26))
            assertEquals(25, GameMode.Solo.getIdealPlayerCount(25))
        }

        @Test
        fun `each mode has a description`() {
            GameMode.entries.forEach { mode ->
                assertTrue(mode.description.isNotBlank(), "${mode.displayName} has no description")
            }
        }
    }

    // ── GamePhase ────────────────────────────────────────────────────────

    @Nested
    inner class GamePhaseTest {
        @Test
        fun `Countdown stores seconds`() {
            val phase = GamePhase.Countdown(10)
            assertEquals(10, phase.secondsLeft)
        }

        @Test
        fun `Grace stores seconds`() {
            val phase = GamePhase.Grace(30)
            assertEquals(30, phase.secondsRemaining)
        }

        @Test
        fun `Ended stores winner UUID`() {
            val winner = UUID.randomUUID()
            val phase = GamePhase.Ended(winner)
            assertEquals(winner, phase.winner)
        }

        @Test
        fun `Ended with no winner stores null`() {
            val phase = GamePhase.Ended(null)
            assertNull(phase.winner)
        }
    }

    // ── StatType ─────────────────────────────────────────────────────────

    @Nested
    inner class StatTypeTest {
        @Test
        fun `all stat types have display names`() {
            StatType.entries.forEach { stat ->
                assertTrue(stat.displayName.isNotBlank(), "${stat.name} has no display name")
            }
        }

        @Test
        fun `all stat types have column names`() {
            StatType.entries.forEach { stat ->
                assertTrue(stat.columnName.isNotBlank(), "${stat.name} has no column name")
            }
        }

        @Test
        fun `expected stat types exist`() {
            assertNotNull(StatType.valueOf("WINS"))
            assertNotNull(StatType.valueOf("KILLS"))
            assertNotNull(StatType.valueOf("KILL_DEATH_RATIO"))
            assertNotNull(StatType.valueOf("WIN_RATE"))
            assertNotNull(StatType.valueOf("CHESTS_OPENED"))
        }
    }

    // ── Exceptions ───────────────────────────────────────────────────────

    @Nested
    inner class ExceptionTest {
        @Test
        fun `all exceptions extend LumaSGException`() {
            assertIs<LumaSGException>(GameException("test"))
            assertIs<LumaSGException>(ArenaException("test"))
            assertIs<LumaSGException>(TeamException("test"))
            assertIs<LumaSGException>(PlayerStateException("test"))
            assertIs<LumaSGException>(ConfigurationException("test"))
        }

        @Test
        fun `LumaSGException extends RuntimeException`() {
            assertIs<RuntimeException>(LumaSGException("test"))
        }

        @Test
        fun `exceptions preserve message and cause`() {
            val cause = IllegalStateException("root cause")
            val ex = GameException("game failed", cause)
            assertEquals("game failed", ex.message)
            assertEquals(cause, ex.cause)
        }

        @Test
        fun `exceptions work without cause`() {
            val ex = ArenaException("not found")
            assertEquals("not found", ex.message)
            assertNull(ex.cause)
        }
    }
}
