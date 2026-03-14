package net.lumalyte.lumasg.gui

import net.badgersmc.nexus.annotations.Service
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
class SetupMenu(private val arenaService: ArenaService) {

    fun open(player: Player) {
        val arenaItems = arenaService.getAvailableArenas().map { arena ->
            SimpleItem(
                ItemBuilder(Material.MAP)
                    .setDisplayName("§b${arena.displayName}")
                    .addLoreLines(
                        "§7Players: §f${arena.minPlayers}-${arena.maxPlayers}",
                        "§7World: §f${arena.worldName}",
                        "",
                        "§eClick to edit"
                    )
            ) { _: Click ->
                player.sendMessage("§aEditing arena: §f${arena.displayName}")
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
            .setContent(arenaItems)
            .build()

        Window.single()
            .setViewer(player)
            .setTitle("§bArena Setup")
            .setGui(gui)
            .build()
            .open()
    }
}
