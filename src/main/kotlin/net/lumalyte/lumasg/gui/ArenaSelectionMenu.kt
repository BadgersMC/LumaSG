package net.lumalyte.lumasg.gui

import net.badgersmc.nexus.annotations.Service
import net.lumalyte.lumasg.game.GameManager
import net.lumalyte.lumasg.service.ArenaService
import org.bukkit.Material
import org.bukkit.entity.Player
import xyz.xenondevs.invui.gui.PagedGui
import xyz.xenondevs.invui.gui.structure.Markers
import xyz.xenondevs.invui.item.Click
import xyz.xenondevs.invui.item.builder.ItemBuilder
import xyz.xenondevs.invui.item.impl.SimpleItem
import xyz.xenondevs.invui.item.impl.controlitem.PageItem
import xyz.xenondevs.invui.window.Window

@Service
class ArenaSelectionMenu(
    private val arenaService: ArenaService,
    private val gameManager: GameManager
) {

    fun open(player: Player) {
        val items = arenaService.getAvailableArenas().map { arena ->
            val activeGame = gameManager.getAllActiveGames().firstOrNull { it.arena.name == arena.name }
            val playerCount = activeGame?.players?.size ?: 0
            SimpleItem(
                ItemBuilder(Material.GRASS_BLOCK)
                    .setDisplayName("§a${arena.displayName}")
                    .addLoreLines(
                        "§7Players: §f$playerCount/${arena.maxPlayers}",
                        "§7Status: §f${if (activeGame != null) "In Progress" else "Waiting"}",
                        "",
                        "§eClick to select!"
                    )
            ) { _: Click ->
                player.closeInventory()
                player.sendMessage("§aSelected arena: §f${arena.displayName}")
            }
        }

        val gui = PagedGui.items()
            .setStructure(
                "# # # # # # # # #",
                "# x x x x x x x #",
                "# x x x x x x x #",
                "# # # < # > # # #"
            )
            .addIngredient('x', Markers.CONTENT_LIST_SLOT_HORIZONTAL)
            .addIngredient('#', SimpleItem(ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).setDisplayName(" ")))
            .addIngredient('<', object : PageItem(false) {
                override fun getItemProvider(gui: PagedGui<*>) =
                    ItemBuilder(Material.ARROW).setDisplayName("§7Previous")
            })
            .addIngredient('>', object : PageItem(true) {
                override fun getItemProvider(gui: PagedGui<*>) =
                    ItemBuilder(Material.ARROW).setDisplayName("§7Next")
            })
            .setContent(items)
            .build()

        Window.single()
            .setViewer(player)
            .setTitle("§aSelect Arena")
            .setGui(gui)
            .build()
            .open()
    }
}
