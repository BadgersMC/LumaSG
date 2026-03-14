package net.lumalyte.lumasg.persistence.repositories

import net.badgersmc.nexus.annotations.Repository
import net.lumalyte.lumasg.domain.Arena
import net.lumalyte.lumasg.domain.SerializableLocation
import net.lumalyte.lumasg.persistence.dbQuery
import net.lumalyte.lumasg.persistence.tables.ArenaTable
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.upsert

@Repository
class ArenaRepository {

    suspend fun findAll(): List<Arena> = dbQuery {
        ArenaTable.selectAll().map { row ->
            Arena(
                name = row[ArenaTable.name],
                displayName = row[ArenaTable.displayName],
                worldName = row[ArenaTable.worldName],
                minPlayers = row[ArenaTable.minPlayers],
                maxPlayers = row[ArenaTable.maxPlayers],
                spawnPoints = emptyList(), // JSON parsing done in service layer
                center = SerializableLocation(
                    world = row[ArenaTable.worldName],
                    x = row[ArenaTable.centerX],
                    y = row[ArenaTable.centerY],
                    z = row[ArenaTable.centerZ]
                ),
                enabled = row[ArenaTable.enabled]
            )
        }
    }

    suspend fun findByName(name: String): Arena? = dbQuery {
        ArenaTable.selectAll().where { ArenaTable.name eq name }
            .singleOrNull()?.let { row ->
                Arena(
                    name = row[ArenaTable.name],
                    displayName = row[ArenaTable.displayName],
                    worldName = row[ArenaTable.worldName],
                    minPlayers = row[ArenaTable.minPlayers],
                    maxPlayers = row[ArenaTable.maxPlayers],
                    spawnPoints = emptyList(), // JSON parsing done in service layer
                    center = SerializableLocation(
                        world = row[ArenaTable.worldName],
                        x = row[ArenaTable.centerX],
                        y = row[ArenaTable.centerY],
                        z = row[ArenaTable.centerZ]
                    ),
                    enabled = row[ArenaTable.enabled]
                )
            }
    }

    suspend fun save(arena: Arena): Unit = dbQuery {
        ArenaTable.upsert {
            it[name] = arena.name
            it[displayName] = arena.displayName
            it[worldName] = arena.worldName
            it[minPlayers] = arena.minPlayers
            it[maxPlayers] = arena.maxPlayers
            it[centerX] = arena.center.x
            it[centerY] = arena.center.y
            it[centerZ] = arena.center.z
            it[enabled] = arena.enabled
            it[spawnPoints] = "[]" // serialized as JSON in future
        }
    }
}
