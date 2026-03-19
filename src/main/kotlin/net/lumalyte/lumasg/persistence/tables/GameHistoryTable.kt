package net.lumalyte.lumasg.persistence.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp

object GameHistoryTable : Table("game_history") {
    val id             = uuid("id").autoGenerate()
    val arenaName      = varchar("arena_name", 64) references ArenaTable.name
    val gameMode       = varchar("game_mode", 16)
    val winnerUuid     = varchar("winner_uuid", 36).nullable()
    val playerCount    = integer("player_count")
    val durationSeconds = long("duration_seconds")
    val startedAt      = timestamp("started_at")
    val endedAt        = timestamp("ended_at")

    override val primaryKey = PrimaryKey(id)
}
