package net.lumalyte.lumasg.game

import java.util.UUID

data class Team(
    val id: Int,
    val members: MutableList<UUID> = mutableListOf()
) {
    val isAlive: Boolean get() = members.isNotEmpty()
    fun add(uuid: UUID) { members.add(uuid) }
    fun remove(uuid: UUID) { members.remove(uuid) }
}
