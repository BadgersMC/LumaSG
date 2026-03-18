package net.lumalyte.lumasg.debug

import net.badgersmc.nexus.annotations.PostConstruct
import net.badgersmc.nexus.annotations.PreDestroy
import net.badgersmc.nexus.annotations.Service
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.lumalyte.lumasg.game.Game
import net.lumalyte.lumasg.game.GameManager
import org.bukkit.Bukkit
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import org.bukkit.entity.Villager
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.metadata.FixedMetadataValue
import org.bukkit.plugin.java.JavaPlugin
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Spawns brainless villager dummies on arena spawn points for solo testing.
 * Dummies are registered as GamePlayers so the game lifecycle (countdown,
 * elimination, win condition) works normally.
 */
@Service
class DummyManager(
    private val plugin: JavaPlugin,
    private val gameManager: GameManager
) : Listener {

    private val mm = MiniMessage.miniMessage()

    /** villager entity UUID → game UUID */
    private val dummies = ConcurrentHashMap<UUID, UUID>()

    @PostConstruct
    fun register() {
        plugin.server.pluginManager.registerEvents(this, plugin)
    }

    @PreDestroy
    fun shutdown() {
        removeAll()
    }

    /**
     * Spawn [count] dummy villagers on the arena spawn points and register
     * them as players in the given game. Returns the number actually spawned.
     */
    fun spawnDummies(game: Game, count: Int): Int {
        val spawns = game.arena.spawnPoints
        // Skip index 0 so the real player keeps their spawn
        val available = spawns.drop(game.players.size).take(count)
        var spawned = 0

        for ((i, sp) in available.withIndex()) {
            val loc = sp.toBukkit() ?: continue
            val villager = loc.world.spawn(loc, Villager::class.java) { v ->
                v.setAI(false)
                v.isSilent = true
                v.isInvulnerable = false
                v.customName(mm.deserialize("<gray>Dummy_${i + 1}"))
                v.isCustomNameVisible = true
                v.setMetadata("lumasg_dummy", FixedMetadataValue(plugin, game.id.toString()))
            }

            game.addDummy(villager.uniqueId, "Dummy_${i + 1}")
            dummies[villager.uniqueId] = game.id
            spawned++
        }
        return spawned
    }

    @EventHandler
    fun onDummyDeath(event: EntityDeathEvent) {
        val entity = event.entity
        if (!entity.hasMetadata("lumasg_dummy")) return

        val gameId = dummies.remove(entity.uniqueId) ?: return
        val game = gameManager.getAllActiveGames().firstOrNull { it.id == gameId } ?: return
        val dummyName = game.players[entity.uniqueId]?.name ?: "Dummy"

        // Credit the killer
        val killer = entity.killer
        if (killer != null) {
            game.players[killer.uniqueId]?.let { it.kills++ }
        }

        game.eliminate(entity.uniqueId)

        // Broadcast death message using the dummy's name
        val remaining = game.alivePlayers.size
        val msg = if (killer != null) {
            mm.deserialize(
                "<red><victim> <gray>was eliminated by <red><killer><gray>! <yellow><remaining> players remain!",
                Placeholder.unparsed("victim", dummyName),
                Placeholder.unparsed("killer", killer.name),
                Placeholder.unparsed("remaining", remaining.toString())
            )
        } else {
            mm.deserialize(
                "<red><victim> <gray>was eliminated! <yellow><remaining> players remain!",
                Placeholder.unparsed("victim", dummyName),
                Placeholder.unparsed("remaining", remaining.toString())
            )
        }
        game.broadcastMessage(msg)

        // Clean up drops
        event.drops.clear()
        event.droppedExp = 0
    }

    /** Remove all dummy entities from the world. */
    fun removeAll() {
        for (uuid in dummies.keys.toList()) {
            Bukkit.getEntity(uuid)?.remove()
        }
        dummies.clear()
    }

    /** Remove dummies for a specific game. */
    fun removeForGame(gameId: UUID) {
        val toRemove = dummies.entries.filter { it.value == gameId }.map { it.key }
        for (uuid in toRemove) {
            dummies.remove(uuid)
            Bukkit.getEntity(uuid)?.remove()
        }
    }
}
