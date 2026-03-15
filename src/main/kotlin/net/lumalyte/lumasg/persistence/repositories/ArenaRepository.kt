package net.lumalyte.lumasg.persistence.repositories

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import net.badgersmc.nexus.annotations.Repository
import net.lumalyte.lumasg.domain.Arena
import net.lumalyte.lumasg.domain.SerializableLocation
import net.lumalyte.lumasg.persistence.dbQuery
import net.lumalyte.lumasg.persistence.tables.ArenaTable
import org.bukkit.Material
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.upsert
import java.util.UUID

private val gson = Gson()
private val locationListType = object : TypeToken<List<SerializableLocation>>() {}.type
private val stringListType = object : TypeToken<List<String>>() {}.type

@Repository
class ArenaRepository {

    suspend fun findAll(): List<Arena> = dbQuery {
        ArenaTable.selectAll().map { row -> row.toArena() }
    }

    suspend fun findByName(name: String): Arena? = dbQuery {
        ArenaTable.selectAll().where { ArenaTable.name eq name }
            .singleOrNull()?.toArena()
    }

    suspend fun save(arena: Arena): Unit = dbQuery {
        ArenaTable.upsert {
            it[id] = arena.id.toString()
            it[name] = arena.name
            it[displayName] = arena.displayName
            it[worldName] = arena.worldName
            it[minPlayers] = arena.minPlayers
            it[maxPlayers] = arena.maxPlayers
            it[radius] = arena.radius
            it[centerX] = arena.center.x
            it[centerY] = arena.center.y
            it[centerZ] = arena.center.z
            it[enabled] = arena.enabled
            it[spawnPoints] = gson.toJson(arena.spawnPoints)
            it[chestLocations] = gson.toJson(arena.chestLocations)
            it[lobbySpawn] = arena.lobbySpawn?.let { loc -> gson.toJson(loc) }
            it[spectatorSpawn] = arena.spectatorSpawn?.let { loc -> gson.toJson(loc) }
            it[allowedBlocks] = gson.toJson(arena.allowedBlocks.map { m -> m.name })
        }
    }

    suspend fun delete(name: String): Unit = dbQuery {
        ArenaTable.deleteWhere { ArenaTable.name eq name }
    }

    private fun org.jetbrains.exposed.sql.ResultRow.toArena(): Arena {
        val worldName = this[ArenaTable.worldName]
        return Arena(
            id = runCatching { UUID.fromString(this[ArenaTable.id]) }.getOrDefault(UUID.randomUUID()),
            name = this[ArenaTable.name],
            displayName = this[ArenaTable.displayName],
            worldName = worldName,
            minPlayers = this[ArenaTable.minPlayers],
            maxPlayers = this[ArenaTable.maxPlayers],
            radius = this[ArenaTable.radius],
            spawnPoints = runCatching {
                gson.fromJson<List<SerializableLocation>>(this[ArenaTable.spawnPoints], locationListType)
            }.getOrDefault(emptyList()),
            center = SerializableLocation(
                world = worldName,
                x = this[ArenaTable.centerX],
                y = this[ArenaTable.centerY],
                z = this[ArenaTable.centerZ]
            ),
            chestLocations = runCatching {
                gson.fromJson<List<SerializableLocation>>(this[ArenaTable.chestLocations], locationListType)
            }.getOrDefault(emptyList()),
            lobbySpawn = this[ArenaTable.lobbySpawn]?.let { s ->
                runCatching { gson.fromJson(s, SerializableLocation::class.java) }.getOrNull()
            },
            spectatorSpawn = this[ArenaTable.spectatorSpawn]?.let { s ->
                runCatching { gson.fromJson(s, SerializableLocation::class.java) }.getOrNull()
            },
            allowedBlocks = runCatching {
                gson.fromJson<List<String>>(this[ArenaTable.allowedBlocks], stringListType)
                    .mapNotNull { runCatching { Material.valueOf(it) }.getOrNull() }
                    .toSet()
            }.getOrDefault(Arena.DEFAULT_ALLOWED_BLOCKS),
            enabled = this[ArenaTable.enabled]
        )
    }
}
