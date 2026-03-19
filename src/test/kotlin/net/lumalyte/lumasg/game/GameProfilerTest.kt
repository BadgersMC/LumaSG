package net.lumalyte.lumasg.game

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GameProfilerTest {

    @Test
    fun `startPhase and endPhase records duration`() {
        val profiler = GameProfiler()
        profiler.startPhase("countdown")
        Thread.sleep(10)
        profiler.endPhase("countdown")
        val duration = profiler.getPhaseDuration("countdown")
        assertNotNull(duration)
        assertTrue(duration.toMillis() >= 5)
    }

    @Test
    fun `endPhase without start is no-op`() {
        val profiler = GameProfiler()
        profiler.endPhase("never_started")
        assertNull(profiler.getPhaseDuration("never_started"))
    }

    @Test
    fun `incrementCounter tracks counts`() {
        val profiler = GameProfiler()
        assertEquals(0, profiler.getCount("kills"))
        profiler.incrementCounter("kills")
        profiler.incrementCounter("kills")
        profiler.incrementCounter("kills")
        assertEquals(3, profiler.getCount("kills"))
    }

    @Test
    fun `getCount returns 0 for unknown counter`() {
        val profiler = GameProfiler()
        assertEquals(0, profiler.getCount("nonexistent"))
    }

    @Test
    fun `totalElapsed increases over time`() {
        val profiler = GameProfiler()
        Thread.sleep(10)
        assertTrue(profiler.totalElapsed().toMillis() >= 5)
    }

    @Test
    fun `getSummary includes phases and counters`() {
        val profiler = GameProfiler()
        profiler.startPhase("grace")
        profiler.endPhase("grace")
        profiler.incrementCounter("chests")
        val summary = profiler.getSummary()
        assertTrue(summary.containsKey("total_elapsed"))
        assertTrue(summary.containsKey("phase_grace"))
        assertTrue(summary.containsKey("count_chests"))
        assertEquals("1", summary["count_chests"])
    }

    @Test
    fun `reset clears all data`() {
        val profiler = GameProfiler()
        profiler.startPhase("test")
        profiler.endPhase("test")
        profiler.incrementCounter("ops")
        profiler.reset()
        assertNull(profiler.getPhaseDuration("test"))
        assertEquals(0, profiler.getCount("ops"))
    }
}
