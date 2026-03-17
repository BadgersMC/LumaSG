package net.lumalyte.lumasg.game

import net.lumalyte.lumasg.config.LumaSGConfig
import net.lumalyte.lumasg.domain.Arena
import org.bukkit.Difficulty
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.entity.Item
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages world state for a single game instance.
 *
 * Lifecycle:
 * 1. [setup]            — called at start of Countdown phase
 * 2. [removeBarriers]   — called when grace period begins (gates open)
 * 3. [setupDeathmatch]  — called when deathmatch phase starts
 * 4. [cleanup]          — called when game ends; restores world to original state
 *
 * All methods must be called on the main thread (via withContext(bukkitDispatcher)).
 */
class WorldManager(private val arena: Arena, private val config: LumaSGConfig) {

    // Barrier tracking: location → original block type
    private val barriers = ConcurrentHashMap<Location, Material>()
    // Track player-placed blocks during the game for cleanup
    private val placedBlocks = ConcurrentHashMap.newKeySet<Location>()

    // Snapshot of world settings before game started
    private var originalDifficulty: Difficulty? = null
    private var originalTime: Long = 0L
    private var originalBorderSize: Double = 60_000_000.0
    private var originalBorderCenter: Location? = null

    // ── Setup ───────────────────────────────────────────────────────────────

    /**
     * Prepares the world for a new game:
     * - Stores original world settings
     * - Sets difficulty PEACEFUL and time to morning
     * - Sets world border to configured initial radius
     * - Places barrier boxes around every spawn point
     */
    fun setup() {
        val world = arenaWorld() ?: return
        val center = arenaCenter() ?: return

        // Snapshot
        originalDifficulty = world.difficulty
        originalTime = world.time
        val border = world.worldBorder
        originalBorderSize = border.size
        originalBorderCenter = border.center.clone()

        // Game settings
        world.difficulty = Difficulty.PEACEFUL
        world.setTime(1000)

        // World border
        border.center = center
        border.setSize(config.worldBorder.initialSize * 2) // WorldBorder size = diameter

        // Barriers at each spawn point
        arena.spawnPoints.forEach { spLoc ->
            spLoc.toBukkit()?.let { placeBarriers(it) }
        }
    }

    /**
     * Removes all spawn-point barriers (called when grace period begins).
     */
    fun removeBarriers() {
        for ((loc, original) in barriers) {
            loc.block.type = original
        }
        barriers.clear()
    }

    /**
     * Configures the world border for deathmatch and begins smooth shrinking.
     */
    fun setupDeathmatch() {
        val world = arenaWorld() ?: return
        val center = arenaCenter() ?: return
        val border = world.worldBorder
        val cfg = config.worldBorder

        border.center = center
        // 1.21.11: setSize(double, long) is deprecated for removal — use changeSize(double, ticks)
        border.changeSize(cfg.deathmatch.endSize * 2, cfg.deathmatch.shrinkDurationSeconds * 20L)
    }

    /**
     * Resets the world border to the initial safe size for celebration phase.
     * Prevents players from taking border damage during winner celebration.
     */
    fun resetBorderForCelebration() {
        val world = arenaWorld() ?: return
        val center = arenaCenter() ?: return
        val border = world.worldBorder
        border.center = center
        border.setSize(config.worldBorder.initialSize * 2)
    }

    // ── Cleanup ─────────────────────────────────────────────────────────────

    /**
     * Restores the world to its pre-game state:
     * - Removes any remaining barriers
     * - Removes player-placed blocks
     * - Clears item drops within 300 blocks of arena center
     * - Restores world border, difficulty, and time
     */
    fun cleanup() {
        removeBarriers()
        removePlacedBlocks()
        clearDrops()
        restoreWorldSettings()
    }

    // ── Block tracking ───────────────────────────────────────────────────────

    /** Call from BlockPlaceEvent handler to track blocks placed during game. */
    fun trackPlacedBlock(location: Location) {
        placedBlocks.add(location.clone())
    }

    /** Get the set of all player-placed block locations. */
    fun getPlacedBlocks(): Set<Location> = placedBlocks.toSet()

    /** Whether a block material is allowed to be broken during the game. */
    fun isBlockAllowed(material: Material): Boolean = arena.isBlockAllowed(material)

    /** Clears all item drops within the arena radius. Returns the number removed. */
    fun clearAllDrops(): Int {
        val world = arenaWorld() ?: return 0
        val center = arenaCenter() ?: return 0
        val radiusSq = arena.radius * arena.radius
        val items = world.entities
            .filterIsInstance<Item>()
            .filter { it.location.distanceSquared(center) <= radiusSq }
        val count = items.size
        items.forEach { it.remove() }
        return count
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    /**
     * Places a 3×2 ring of BARRIER blocks around [center] (skipping the
     * center column so the player can stand there).
     * Stores the original block type for restoration.
     */
    private fun placeBarriers(center: Location) {
        val world = center.world ?: return
        val x = center.blockX
        val y = center.blockY
        val z = center.blockZ

        for (dx in -1..1) {
            for (dy in 0..2) {
                for (dz in -1..1) {
                    if (dx == 0 && dz == 0) continue // keep center column open
                    val loc = Location(world, (x + dx).toDouble(), (y + dy).toDouble(), (z + dz).toDouble())
                    barriers[loc] = loc.block.type
                    loc.block.type = Material.BARRIER
                }
            }
        }
    }

    private fun removePlacedBlocks() {
        placedBlocks.forEach { it.block.type = Material.AIR }
        placedBlocks.clear()
    }

    private fun clearDrops() {
        val world = arenaWorld() ?: return
        val center = arenaCenter() ?: return
        world.entities
            .filterIsInstance<Item>()
            .filter { it.location.distanceSquared(center) <= 300.0 * 300.0 }
            .forEach { it.remove() }
    }

    private fun restoreWorldSettings() {
        val world = arenaWorld() ?: return
        val border = world.worldBorder
        originalBorderCenter?.let { border.center = it }
        border.setSize(originalBorderSize)
        originalDifficulty?.let { world.difficulty = it }
        world.setTime(originalTime)
    }

    private fun arenaWorld(): World? = arena.spawnPoints.firstOrNull()?.toBukkit()?.world

    private fun arenaCenter(): Location? =
        arena.center.toBukkit()
            ?: arena.spawnPoints.firstOrNull()?.toBukkit()
}
