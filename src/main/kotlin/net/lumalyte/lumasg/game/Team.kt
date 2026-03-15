package net.lumalyte.lumasg.game

import org.bukkit.Bukkit
import java.util.UUID

data class Team(
    val id: Int,
    val members: MutableList<UUID> = mutableListOf(),
    val maxSize: Int = 1,
    var inviteOnly: Boolean = false
) {
    val isAlive: Boolean get() = members.any { uuid ->
        Bukkit.getPlayer(uuid)?.isOnline == true
    }

    val isFull: Boolean get() = members.size >= maxSize

    fun add(uuid: UUID): Boolean {
        if (isFull) return false
        members.add(uuid)
        return true
    }

    fun remove(uuid: UUID) { members.remove(uuid) }

    fun contains(uuid: UUID) = uuid in members

    val leader: UUID? get() = members.firstOrNull()
}
