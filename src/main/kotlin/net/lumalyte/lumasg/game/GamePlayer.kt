package net.lumalyte.lumasg.game

import org.bukkit.entity.Player
import java.util.UUID

data class GamePlayer(
    val uuid: UUID,
    val name: String,
    var isAlive: Boolean = true,
    var isSpectating: Boolean = false,
    var kills: Int = 0,
    var damageDealt: Double = 0.0,
    var damageTaken: Double = 0.0
)

fun Player.toGamePlayer() = GamePlayer(uniqueId, name)
