package net.lumalyte.lumasg.game

import net.lumalyte.lumasg.domain.Arena
import org.bukkit.Location
import org.bukkit.Material
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages barrier blocks placed around spawn points during pre-game.
 * Barriers prevent players from leaving their spawn until the game starts.
 */
class GameBarrierManager(private val arena: Arena) {
    private val barriers = ConcurrentHashMap<Location, Material>()

    /** Place barrier boxes around all spawn points. Must be called on main thread. */
    fun placeAll() {
        arena.spawnPoints.forEach { spLoc ->
            spLoc.toBukkit()?.let { placeBarrier(it) }
        }
    }

    /** Remove all placed barriers, restoring original blocks. Must be called on main thread. */
    fun removeAll() {
        for ((loc, original) in barriers) {
            loc.block.type = original
        }
        barriers.clear()
    }

    /** Whether barriers are currently placed. */
    fun hasBarriers(): Boolean = barriers.isNotEmpty()

    /** Number of barrier blocks currently placed. */
    fun barrierCount(): Int = barriers.size

    /**
     * Places a 3x3x3 ring of BARRIER blocks around [center] (keeping center column open).
     * Stores original block type for restoration.
     */
    private fun placeBarrier(center: Location) {
        val world = center.world ?: return
        val x = center.blockX
        val y = center.blockY
        val z = center.blockZ

        for (dx in -1..1) {
            for (dy in 0..2) {
                for (dz in -1..1) {
                    if (dx == 0 && dz == 0) continue
                    val loc = Location(world, (x + dx).toDouble(), (y + dy).toDouble(), (z + dz).toDouble())
                    barriers[loc] = loc.block.type
                    loc.block.type = Material.BARRIER
                }
            }
        }
    }
}
