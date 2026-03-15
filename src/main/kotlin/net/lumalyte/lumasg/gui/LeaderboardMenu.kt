package net.lumalyte.lumasg.gui

import net.badgersmc.nexus.annotations.Service
import net.lumalyte.lumasg.domain.StatType
import net.lumalyte.lumasg.statistics.StatisticsService
import org.bukkit.Material
import org.bukkit.entity.Player
import xyz.xenondevs.invui.gui.PagedGui
import xyz.xenondevs.invui.gui.structure.Markers
import xyz.xenondevs.invui.item.builder.ItemBuilder
import xyz.xenondevs.invui.item.impl.SimpleItem
import xyz.xenondevs.invui.item.impl.controlitem.PageItem
import xyz.xenondevs.invui.window.Window

@Service
class LeaderboardMenu(private val statsService: StatisticsService) {

    /** Open the default leaderboard (kills). */
    suspend fun open(player: Player) {
        openLeaderboardTab(player, StatType.KILLS)
    }

    /** Open a leaderboard filtered by a specific stat type. */
    suspend fun openLeaderboardTab(player: Player, statType: StatType) {
        val topPlayers = statsService.getLeaderboard(statType, 27)
        val items = topPlayers.mapIndexed { index, stats ->
            val statValue = when (statType) {
                StatType.WINS -> "§7Wins: §f${stats.wins}"
                StatType.KILLS -> "§7Kills: §f${stats.kills}"
                StatType.GAMES_PLAYED -> "§7Games: §f${stats.gamesPlayed}"
                StatType.KILL_DEATH_RATIO -> "§7K/D: §f${String.format("%.2f", stats.kdr)}"
                StatType.WIN_RATE -> "§7Win Rate: §f${String.format("%.1f%%", stats.winRate * 100)}"
                StatType.TIME_PLAYED -> "§7Time: §f${stats.totalTimePlayed / 3600}h"
                StatType.BEST_PLACEMENT -> "§7Best: §f#${stats.bestPlacement}"
                StatType.WIN_STREAK -> "§7Streak: §f${stats.bestWinStreak}"
                StatType.TOP3_FINISHES -> "§7Top 3: §f${stats.top3Finishes}"
                StatType.DAMAGE_DEALT -> "§7Damage: §f${String.format("%.0f", stats.damageDealt)}"
                StatType.CHESTS_OPENED -> "§7Chests: §f${stats.chestsOpened}"
            }
            SimpleItem(
                ItemBuilder(Material.PLAYER_HEAD)
                    .setDisplayName("§6#${index + 1} §f${stats.playerName}")
                    .addLoreLines(
                        statValue,
                        "§7Kills: §f${stats.kills}",
                        "§7Deaths: §f${stats.deaths}",
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
            .setTitle("§6Leaderboard — ${statType.displayName}")
            .setGui(gui)
            .build()
            .open()
    }
}
