package net.lumalyte.lumasg.gui

import net.badgersmc.nexus.annotations.Service
import net.lumalyte.lumasg.statistics.StatisticsService
import org.bukkit.Material
import org.bukkit.entity.Player
import xyz.xenondevs.invui.gui.PagedGui
import xyz.xenondevs.invui.gui.structure.Markers
import xyz.xenondevs.invui.item.builder.ItemBuilder
import xyz.xenondevs.invui.item.builder.SkullBuilder
import xyz.xenondevs.invui.item.impl.SimpleItem
import xyz.xenondevs.invui.item.impl.controlitem.PageItem
import xyz.xenondevs.invui.window.Window

@Service
class LeaderboardMenu(private val statsService: StatisticsService) {

    suspend fun open(player: Player) {
        val topPlayers = statsService.getLeaderboard(27)
        val items = topPlayers.mapIndexed { index, stats ->
            SimpleItem(
                ItemBuilder(Material.PLAYER_HEAD)
                    .setDisplayName("§6#${index + 1} §f${stats.playerName}")
                    .addLoreLines(
                        "§7Kills: §f${stats.kills}",
                        "§7Deaths: §f${stats.deaths}",
                        "§7K/D: §f${String.format("%.2f", stats.kdr)}",
                        "§7Wins: §f${stats.wins}",
                        "§7Games: §f${stats.gamesPlayed}"
                    )
            )
        }

        val gui = PagedGui.items()
            .setStructure(
                "# # # # # # # # #",
                "# x x x x x x x #",
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
            .setTitle("§6Leaderboard")
            .setGui(gui)
            .build()
            .open()
    }
}
