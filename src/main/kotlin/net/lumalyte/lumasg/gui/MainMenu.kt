package net.lumalyte.lumasg.gui

import net.badgersmc.nexus.annotations.Service
import org.bukkit.Material
import org.bukkit.entity.Player
import xyz.xenondevs.invui.gui.Gui
import xyz.xenondevs.invui.item.builder.ItemBuilder
import xyz.xenondevs.invui.item.impl.SimpleItem
import xyz.xenondevs.invui.window.Window

@Service
class MainMenu(
    private val gameBrowserMenu: GameBrowserMenu,
    private val leaderboardMenu: LeaderboardMenu,
    private val setupMenu: SetupMenu
) {
    fun open(player: Player) {
        val gui = Gui.normal()
            .setStructure(
                "# # # # # # # # #",
                "# # b # l # s # #",
                "# # # # # # # # #"
            )
            .addIngredient('#', MenuUtils.createBorderItem())
            .addIngredient('b', SimpleItem(
                ItemBuilder(Material.IRON_SWORD)
                    .setDisplayName("§6Browse Games")
                    .addLoreLines("§7Find and join active games")
            ) { click ->
                click.player.closeInventory()
                gameBrowserMenu.open(click.player)
            })
            .addIngredient('l', SimpleItem(
                ItemBuilder(Material.BOOK)
                    .setDisplayName("§6Leaderboard")
                    .addLoreLines("§7View top players")
            ) { click ->
                click.player.closeInventory()
                // Leaderboard is suspend, launch in scope
            })
            .addIngredient('s', SimpleItem(
                ItemBuilder(Material.REDSTONE_TORCH)
                    .setDisplayName("§6Setup")
                    .addLoreLines("§7Configure arenas and games")
            ) { click ->
                click.player.closeInventory()
                setupMenu.open(click.player)
            })
            .build()

        Window.single()
            .setViewer(player)
            .setTitle("§6Survival Games")
            .setGui(gui)
            .build()
            .open()
    }
}
