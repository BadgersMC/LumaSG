package net.lumalyte.lumasg.game

import kotlinx.coroutines.*
import kotlin.coroutines.coroutineContext
import net.badgersmc.nexus.paper.BukkitDispatcher
import org.bukkit.Bukkit
import org.bukkit.FluidCollisionMode
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class NameplateManager(
    private val plugin: Plugin,
    private val bukkitDispatcher: BukkitDispatcher,
    private val scope: CoroutineScope,
    private val maxDistance: Double = 50.0,
    private val updateIntervalTicks: Long = 10L
) {
    private val visibility = ConcurrentHashMap<UUID, ConcurrentHashMap<UUID, Boolean>>()
    private var job: Job? = null
    @Volatile
    private var hidingEnabled = true

    fun start(players: Set<UUID>) {
        for (p in players) visibility[p] = ConcurrentHashMap()
        job = scope.launch { updateLoop() }
    }

    fun stop() {
        job?.cancel()
        scope.launch {
            withContext(bukkitDispatcher) { restoreAll() }
        }
    }

    fun addPlayer(uuid: UUID) { visibility[uuid] = ConcurrentHashMap() }
    fun removePlayer(uuid: UUID) {
        visibility.remove(uuid)
        visibility.values.forEach { it.remove(uuid) }
    }

    /** Disable nameplate hiding (show all players). Used during celebration. */
    fun disableNameplateHiding() {
        hidingEnabled = false
        scope.launch {
            withContext(bukkitDispatcher) { restoreAll() }
        }
    }

    /** Re-enable nameplate hiding. */
    fun enableNameplateHiding() {
        hidingEnabled = true
    }

    private suspend fun updateLoop() {
        while (coroutineContext.isActive) {
            withContext(bukkitDispatcher) { updateVisibility() }
            delay(updateIntervalTicks * 50L)
        }
    }

    private fun updateVisibility() {
        if (!hidingEnabled) return
        val uuids = visibility.keys.toList()
        for (viewerUuid in uuids) {
            val viewer = Bukkit.getPlayer(viewerUuid) ?: continue
            val map = visibility[viewerUuid] ?: continue
            for (targetUuid in uuids) {
                if (targetUuid == viewerUuid) continue
                val target = Bukkit.getPlayer(targetUuid) ?: continue
                val visible = isVisible(viewer, target)
                val was = map.put(targetUuid, visible)
                if (was != visible) {
                    if (visible) viewer.showEntity(plugin, target)
                    else viewer.hideEntity(plugin, target)
                }
            }
        }
    }

    private fun isVisible(viewer: Player, target: Player): Boolean {
        val dist = viewer.location.distance(target.location)
        if (dist > maxDistance) return false
        val eyeLoc = viewer.eyeLocation
        val direction = target.eyeLocation.subtract(eyeLoc).toVector()
        val result = viewer.world.rayTraceBlocks(
            eyeLoc, direction, dist, FluidCollisionMode.NEVER, true
        )
        return result == null // no blocks in the way
    }

    private fun restoreAll() {
        for (viewerUuid in visibility.keys) {
            val viewer = Bukkit.getPlayer(viewerUuid) ?: continue
            for (targetUuid in visibility.keys) {
                if (targetUuid == viewerUuid) continue
                val target = Bukkit.getPlayer(targetUuid) ?: continue
                viewer.showEntity(plugin, target)
            }
        }
        visibility.clear()
    }
}
