package net.lumalyte.lumasg.persistence.tables

import org.jetbrains.exposed.sql.Table

object ArenaTable : Table("arenas") {
    val name        = varchar("name", 64)
    val displayName = varchar("display_name", 128)
    val worldName   = varchar("world_name", 64)
    val minPlayers  = integer("min_players").default(2)
    val maxPlayers  = integer("max_players").default(24)
    val spawnPoints = text("spawn_points_json") // JSON array of SerializableLocation
    val centerX     = double("center_x")
    val centerY     = double("center_y")
    val centerZ     = double("center_z")
    val enabled     = bool("enabled").default(true)

    override val primaryKey = PrimaryKey(name)
}
