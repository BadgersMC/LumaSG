package net.lumalyte.lumasg.game

import io.mockk.every
import io.mockk.mockk
import net.lumalyte.lumasg.config.LumaSGConfig
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.PlayerInventory
import org.bukkit.plugin.java.JavaPlugin
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlayerStateManagerTest {

    @Test
    fun `saveLocation false does not save the arena spawn`() {
        val config = mockk<LumaSGConfig>(relaxed = true)
        every { config.game.saveLocation } returns false
        every { config.game.clearInventory } returns true

        val plugin = mockk<JavaPlugin>(relaxed = true)
        val manager = PlayerStateManager(config, plugin)

        val world = mockk<World>(relaxed = true)
        every { world.name } returns "world"

        val player = mockk<Player>(relaxed = true)
        val inventory = mockk<PlayerInventory>(relaxed = true)
        every { inventory.contents } returns arrayOfNulls<ItemStack?>(36)
        every { inventory.armorContents } returns arrayOfNulls<ItemStack?>(4)
        every { inventory.itemInOffHand } returns mockk(relaxed = true)
        every { player.uniqueId } returns UUID.randomUUID()
        every { player.location } returns Location(world, 0.0, 70.0, 0.0)
        every { player.inventory } returns inventory
        every { player.activePotionEffects } returns emptyList()

        val arenaSpawn = Location(world, 100.0, 64.0, 100.0)
        manager.saveAndPrepare(player, arenaSpawn)

        val savedLoc = manager.savedLocationOf(player.uniqueId)
        assertNull(savedLoc, "With saveLocation=false, saved location should be null")
    }

    @Test
    fun `prepareWithoutSaving does not overwrite saved state`() {
        val config = mockk<LumaSGConfig>(relaxed = true)
        every { config.game.saveLocation } returns true
        every { config.game.clearInventory } returns true
        every { config.game.restoreInventory } returns true

        val plugin = mockk<JavaPlugin>(relaxed = true)
        val manager = PlayerStateManager(config, plugin)

        val world = mockk<World>(relaxed = true)
        every { world.name } returns "world"

        val player = mockk<Player>(relaxed = true)
        val inventory = mockk<PlayerInventory>(relaxed = true)
        val marker = mockk<ItemStack>(relaxed = true)
        every { marker.type } returns Material.DIAMOND
        every { marker.clone() } returns marker
        every { inventory.contents } returns arrayOf<ItemStack?>(marker) + arrayOfNulls<ItemStack?>(35)
        every { inventory.armorContents } returns arrayOfNulls<ItemStack?>(4)
        every { inventory.itemInOffHand } returns mockk(relaxed = true)
        every { player.uniqueId } returns UUID.randomUUID()
        every { player.location } returns Location(world, 0.0, 70.0, 0.0)
        every { player.inventory } returns inventory
        every { player.activePotionEffects } returns emptyList()

        // saveAndPrepare: saves the marker item, clears inventory
        manager.saveAndPrepare(player, Location(world, 100.0, 64.0, 100.0))

        // Now verify saved state has the marker
        assertTrue(manager.hasSavedState(player.uniqueId))

        // prepareWithoutSaving should NOT touch saved state
        manager.prepareWithoutSaving(player, Location(world, 200.0, 64.0, 200.0))

        // Saved state should still exist (not overwritten)
        assertTrue(manager.hasSavedState(player.uniqueId),
            "prepareWithoutSaving must not overwrite or remove saved state")
    }
}
