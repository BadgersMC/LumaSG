package net.lumalyte.lumasg.persistence.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp

object PlayerStatsTable : Table("player_stats") {
    val uuid        = varchar("uuid", 36)
    val playerName  = varchar("player_name", 64)
    val kills       = integer("kills").default(0)
    val deaths      = integer("deaths").default(0)
    val wins        = integer("wins").default(0)
    val gamesPlayed = integer("games_played").default(0)
    val damageDealt = double("damage_dealt").default(0.0)
    val damageTaken = double("damage_taken").default(0.0)
    val lastSeen    = timestamp("last_seen")
    val createdAt   = timestamp("created_at")

    override val primaryKey = PrimaryKey(uuid)
}
