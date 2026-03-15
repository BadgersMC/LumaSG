package net.lumalyte.lumasg.domain

import org.bukkit.Bukkit
import org.bukkit.Location

data class Arena(
    val name: String,
    val displayName: String,
    val worldName: String,
    val minPlayers: Int,
    val maxPlayers: Int,
    val spawnPoints: List<SerializableLocation>,
    val center: SerializableLocation,
    val radius: Double = 500.0,
    val enabled: Boolean = true
)

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
}
