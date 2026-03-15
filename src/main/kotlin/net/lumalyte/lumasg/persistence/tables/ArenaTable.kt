package net.lumalyte.lumasg.persistence.tables

import org.jetbrains.exposed.sql.Table

object ArenaTable : Table("arenas") {
    val id          = varchar("id", 36)
    val name        = varchar("name", 64)
    val displayName = varchar("display_name", 128)
    val worldName   = varchar("world_name", 64)
    val minPlayers  = integer("min_players").default(2)
    val maxPlayers  = integer("max_players").default(24)
    val radius      = double("radius").default(500.0)
    val spawnPoints = text("spawn_points_json")
    val centerX     = double("center_x")
    val centerY     = double("center_y")
    val centerZ     = double("center_z")
    val chestLocations = text("chest_locations_json").default("[]")
    val lobbySpawn  = text("lobby_spawn_json").nullable()
    val spectatorSpawn = text("spectator_spawn_json").nullable()
    val allowedBlocks = text("allowed_blocks_json").default("[]")
    val enabled     = bool("enabled").default(true)

    override val primaryKey = PrimaryKey(name)
}
