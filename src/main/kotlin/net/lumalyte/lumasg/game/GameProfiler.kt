package net.lumalyte.lumasg.game

import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Lightweight performance profiler for game instances.
 * Tracks timing of game phases and operations for debugging and optimization.
 */
class GameProfiler {
    private val phaseTimings = ConcurrentHashMap<String, Instant>()
    private val phaseDurations = ConcurrentHashMap<String, Duration>()
    private val operationCounts = ConcurrentHashMap<String, AtomicLong>()
    private val gameStartTime = Instant.now()

    /** Mark the start of a phase or operation. */
    fun startPhase(name: String) {
        phaseTimings[name] = Instant.now()
    }

    /** Mark the end of a phase, recording its duration. */
    fun endPhase(name: String) {
        val start = phaseTimings.remove(name) ?: return
        phaseDurations[name] = Duration.between(start, Instant.now())
    }

    /** Increment a named operation counter. */
    fun incrementCounter(name: String) {
        operationCounts.computeIfAbsent(name) { AtomicLong(0) }.incrementAndGet()
    }

    /** Get the duration of a completed phase. */
    fun getPhaseDuration(name: String): Duration? = phaseDurations[name]

    /** Get a counter's current value. */
    fun getCount(name: String): Long = operationCounts[name]?.get() ?: 0

    /** Total elapsed time since the profiler was created. */
    fun totalElapsed(): Duration = Duration.between(gameStartTime, Instant.now())

    /** Get a summary of all recorded timings and counters. */
    fun getSummary(): Map<String, String> {
        val result = linkedMapOf<String, String>()
        result["total_elapsed"] = formatDuration(totalElapsed())
        for ((name, duration) in phaseDurations) {
            result["phase_$name"] = formatDuration(duration)
        }
        for ((name, count) in operationCounts) {
            result["count_$name"] = count.get().toString()
        }
        return result
    }

    /** Reset all profiler data. */
    fun reset() {
        phaseTimings.clear()
        phaseDurations.clear()
        operationCounts.clear()
    }

    private fun formatDuration(d: Duration): String {
        val totalMs = d.toMillis()
        return if (totalMs < 1000) "${totalMs}ms"
        else "${totalMs / 1000}.${(totalMs % 1000) / 100}s"
    }
}
