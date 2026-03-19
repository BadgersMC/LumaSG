package net.lumalyte.lumasg.gui

import net.badgersmc.nexus.annotations.Service
import net.lumalyte.lumasg.game.Team
import org.bukkit.Material
import org.bukkit.entity.Player
import xyz.xenondevs.invui.gui.Gui
import xyz.xenondevs.invui.item.Click
import xyz.xenondevs.invui.item.builder.ItemBuilder
import xyz.xenondevs.invui.item.impl.SimpleItem
import xyz.xenondevs.invui.window.Window

@Service
class TeamSelectionMenu {

    fun open(player: Player, teams: List<Team>) {
        val gui = Gui.normal()
            .setStructure("# # # # # # # # #")
            .addIngredient('#', SimpleItem(ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).setDisplayName(" ")))
            .build()

        teams.forEachIndexed { index, team ->
            if (index < 9) {
                gui.setItem(index, SimpleItem(
                    ItemBuilder(Material.WHITE_BANNER)
                        .setDisplayName("§eTeam ${team.id}")
                        .addLoreLines(
                            "§7Members: §f${team.members.size}",
                            "",
                            "§eClick to join!"
                        )
                ) { _: Click ->
                    player.closeInventory()
                    team.add(player.uniqueId)
                    player.sendMessage("§aJoined Team ${team.id}!")
                })
            }
        }

        Window.single()
            .setViewer(player)
            .setTitle("§eSelect Team")
            .setGui(gui)
            .build()
            .open()
    }
}
