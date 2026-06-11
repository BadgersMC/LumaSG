package net.lumalyte.lumasg.domain

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.slf4j.LoggerFactory
import java.util.UUID

data class Arena(
    val id: UUID = UUID.randomUUID(),
    val name: String,
    val displayName: String,
    val worldName: String,
    val minPlayers: Int,
    val maxPlayers: Int,
    val spawnPoints: List<SerializableLocation>,
    val center: SerializableLocation,
    val radius: Double = 500.0,
    val enabled: Boolean = true,
    val chestLocations: List<SerializableLocation> = emptyList(),
    val lobbySpawn: SerializableLocation? = null,
    val spectatorSpawn: SerializableLocation? = null,
    val allowedBlocks: Set<Material> = DEFAULT_ALLOWED_BLOCKS
) {
    /** Whether this arena can support the given number of players. */
    fun canSupportPlayers(count: Int): Boolean = count in minPlayers..maxPlayers

    /** Check if a block material is allowed to be broken during a game. */
    fun isBlockAllowed(material: Material): Boolean = material in allowedBlocks

    /**
     * Scans the arena world for chest blocks within [radius] of center.
     * Returns the discovered chest locations. Must be called on main thread.
     *
     * The scan radius is capped at [MAX_CHEST_SCAN_RADIUS]: an uncapped cubic scan at the
     * default radius of 500 is ~10^9 `getBlockAt` calls and freezes the main thread for
     * minutes (C2). A radius beyond the cap is clamped and logged so the operator knows.
     */
    fun scanForChests(): List<SerializableLocation> {
        val world = Bukkit.getWorld(worldName) ?: return emptyList()
        val centerLoc = center.toBukkit() ?: return emptyList()
        if (!isChestScanRadiusSafe(radius)) {
            logger.warn(
                "Arena '{}' chest-scan radius {} exceeds the safe cap of {}; clamping to avoid a main-thread freeze.",
                name, radius.toInt(), MAX_CHEST_SCAN_RADIUS
            )
        }
        val r = radius.toInt().coerceIn(0, MAX_CHEST_SCAN_RADIUS)
        val cx = centerLoc.blockX
        val cy = centerLoc.blockY
        val cz = centerLoc.blockZ
        val found = mutableListOf<SerializableLocation>()
        for (x in (cx - r)..(cx + r)) {
            for (y in (cy - r).coerceAtLeast(world.minHeight)..(cy + r).coerceAtMost(world.maxHeight)) {
                for (z in (cz - r)..(cz + r)) {
                    val block = world.getBlockAt(x, y, z)
                    if (block.type == Material.CHEST || block.type == Material.TRAPPED_CHEST) {
                        found.add(SerializableLocation(worldName, x.toDouble(), y.toDouble(), z.toDouble()))
                    }
                }
            }
        }
        return found
    }

    /** Show particle markers at each spawn point. Must be called on main thread. */
    fun showSpawnPoints() {
        for (sp in spawnPoints) {
            val loc = sp.toBukkit() ?: continue
            loc.world?.spawnParticle(Particle.FLAME, loc.clone().add(0.0, 1.0, 0.0), 20, 0.2, 0.5, 0.2, 0.01)
        }
    }

    /** Remove spawn point markers (particles are ephemeral — no cleanup needed). */
    fun hideSpawnPoints() { /* no-op: particles are ephemeral */ }

    /** Restores arena to clean state. Called when arena is unloaded or removed. */
    fun cleanup() { /* no-op: DB-backed arena has no runtime state to clean */ }

    companion object {
        private val logger = LoggerFactory.getLogger(Arena::class.java)

        /** Hard cap on the [scanForChests] radius — keeps the cubic scan bounded (C2). */
        const val MAX_CHEST_SCAN_RADIUS: Int = 64

        /** Whether [radius] is within the safe cap for an on-main-thread chest scan. */
        fun isChestScanRadiusSafe(radius: Double): Boolean =
            radius >= 1.0 && radius <= MAX_CHEST_SCAN_RADIUS

        val DEFAULT_ALLOWED_BLOCKS: Set<Material> = setOf(
            Material.TALL_GRASS, Material.SHORT_GRASS, Material.FERN,
            Material.DEAD_BUSH, Material.VINE, Material.LILY_PAD,
            Material.SUGAR_CANE, Material.SWEET_BERRY_BUSH,
            Material.COBWEB, Material.FIRE, Material.SOUL_FIRE,
            Material.OAK_LEAVES, Material.SPRUCE_LEAVES, Material.BIRCH_LEAVES,
            Material.JUNGLE_LEAVES, Material.ACACIA_LEAVES, Material.DARK_OAK_LEAVES
        )
    }
}

/** Serializable wrapper for Location (Bukkit Location is not serializable). */
data class SerializableLocation(
    val world: String,
    val x: Double,
    val y: Double,
    val z: Double,
    val yaw: Float = 0f,
    val pitch: Float = 0f
) {
    fun toBukkit(): Location? {
        val w = Bukkit.getWorld(world) ?: return null
        return Location(w, x, y, z, yaw, pitch)
    }

    companion object {
        fun fromBukkit(loc: Location) = SerializableLocation(
            world = loc.world?.name ?: "world",
            x = loc.x, y = loc.y, z = loc.z,
            yaw = loc.yaw, pitch = loc.pitch
        )
    }
}
