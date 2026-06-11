package net.lumalyte.lumasg.game

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.time.Instant
import java.util.UUID

data class Team(
    val id: Int,
    val members: MutableList<UUID> = mutableListOf(),
    val maxSize: Int = 1,
    var inviteOnly: Boolean = false,
    val createdAt: Instant = Instant.now()
) {
    @Volatile
    var isEliminated: Boolean = false
        private set

    val isAlive: Boolean get() = !isEliminated && members.isNotEmpty()

    val isFull: Boolean get() = members.size >= maxSize

    val displayName: String get() = "Team $id"

    fun add(uuid: UUID): Boolean {
        if (isFull) return false
        members.add(uuid)
        return true
    }

    fun remove(uuid: UUID) { members.remove(uuid) }

    fun contains(uuid: UUID) = uuid in members

    val leader: UUID? get() = members.firstOrNull()

    /** Mark this team as eliminated. */
    fun eliminate() {
        isEliminated = true
    }

    /** Get online members as Player objects. */
    fun getOnlineMembers(): List<Player> =
        members.mapNotNull { Bukkit.getPlayer(it) }

    /** Whether any team members are currently online. */
    fun hasOnlineMembers(): Boolean =
        members.any { Bukkit.getPlayer(it) != null }

    /** Get display names of all members. */
    fun getMemberNames(): List<String> =
        members.map { Bukkit.getOfflinePlayer(it).name ?: "Unknown" }

    /** Find a member UUID by player name. */
    fun getMemberByName(name: String): UUID? =
        members.firstOrNull { Bukkit.getOfflinePlayer(it).name.equals(name, ignoreCase = true) }

    /** Clean up team state. */
    fun cleanup() {
        members.clear()
        isEliminated = false
    }
}
