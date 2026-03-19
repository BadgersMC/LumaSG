package net.lumalyte.lumasg.gui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import net.badgersmc.nexus.annotations.Service
import net.badgersmc.nexus.paper.BukkitDispatcher
import net.lumalyte.lumasg.util.cache.GuiComponentCache
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
    private val setupMenu: SetupMenu,
    private val guiCache: GuiComponentCache,
    private val bukkitDispatcher: BukkitDispatcher
) {
    private val scope = CoroutineScope(bukkitDispatcher + SupervisorJob())
    fun open(player: Player) {
        val gui = Gui.normal()
            .setStructure(
                "# # # # # # # # #",
                "# # b # l # s # #",
                "# # # # # # # # #"
            )
            .addIngredient('#', guiCache.createBorderItem("gray"))
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
                scope.launch { leaderboardMenu.open(click.player) }
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
