package net.lumalyte.lumasg.game

import net.badgersmc.nexus.annotations.Service
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffect
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Saves and restores player state around a game session.
 *
 * Call [saveAndPrepare] when a player joins a game (teleports to spawn,
 * clears inventory, sets survival mode).
 * Call [restore] when the game ends or the player leaves mid-game.
 */
@Service
class PlayerStateManager {

    private data class SavedState(
        val location: Location,
        val gameMode: GameMode,
        val inventory: Array<ItemStack?>,
        val armor: Array<ItemStack?>,
        val offhand: ItemStack?,
        val health: Double,
        val foodLevel: Int,
        val saturation: Float,
        val expLevel: Int,
        val expProgress: Float,
        val potionEffects: Collection<PotionEffect>
    )

    private val saved = ConcurrentHashMap<UUID, SavedState>()

    /**
     * Saves player state, teleports to [spawnLocation], clears inventory,
     * and puts player in SURVIVAL with full health/food.
     * Must be called on the main thread.
     */
    fun saveAndPrepare(player: Player, spawnLocation: Location) {
        saved[player.uniqueId] = SavedState(
            location = player.location.clone(),
            gameMode = player.gameMode,
            inventory = player.inventory.contents.map { it?.clone() }.toTypedArray(),
            armor = player.inventory.armorContents.map { it?.clone() }.toTypedArray(),
            offhand = player.inventory.itemInOffHand.clone(),
            health = player.health,
            foodLevel = player.foodLevel,
            saturation = player.saturation,
            expLevel = player.level,
            expProgress = player.exp,
            potionEffects = player.activePotionEffects.map { it }
        )

        player.activePotionEffects.forEach { player.removePotionEffect(it.type) }
        player.inventory.clear()
        player.inventory.setArmorContents(arrayOfNulls(4))
        player.setItemOnCursor(null)
        player.gameMode = GameMode.SURVIVAL
        player.health = player.maxHealth
        player.foodLevel = 20
        player.saturation = 20f
        player.exp = 0f
        player.level = 0
        player.teleport(spawnLocation)
    }

    /**
     * Restores the player's pre-game state and removes them from the saved map.
     * Must be called on the main thread.
     * No-op if no state is saved for this player.
     */
    fun restore(player: Player) {
        val state = saved.remove(player.uniqueId) ?: return

        player.activePotionEffects.forEach { player.removePotionEffect(it.type) }
        player.inventory.clear()
        player.inventory.contents = state.inventory
        player.inventory.armorContents = state.armor
        player.inventory.setItemInOffHand(state.offhand)
        player.health = minOf(state.health, player.maxHealth)
        player.foodLevel = state.foodLevel
        player.saturation = state.saturation
        player.level = state.expLevel
        player.exp = state.expProgress
        state.potionEffects.forEach { player.addPotionEffect(it) }
        player.gameMode = state.gameMode
        player.teleport(state.location)
    }

    /** Set a player to spectator mode (used when eliminated mid-game). */
    fun makeSpectator(player: Player) {
        player.gameMode = GameMode.SPECTATOR
    }

    /** Returns true if we have saved state for this player. */
    fun hasSavedState(uuid: UUID) = saved.containsKey(uuid)
}
