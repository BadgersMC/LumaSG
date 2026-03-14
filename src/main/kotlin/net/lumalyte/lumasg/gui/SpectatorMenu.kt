package net.lumalyte.lumasg.gui

import net.badgersmc.nexus.annotations.Service
import net.lumalyte.lumasg.game.Game
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import xyz.xenondevs.invui.gui.Gui
import xyz.xenondevs.invui.item.Click
import xyz.xenondevs.invui.item.builder.ItemBuilder
import xyz.xenondevs.invui.item.impl.SimpleItem
import xyz.xenondevs.invui.window.Window

@Service
class SpectatorMenu {

    fun open(spectator: Player, game: Game) {
        val gui = Gui.normal()
            .setStructure(
                "# # # # # # # # #",
                "# # # # # # # # #",
                "# # # # # # # # #"
            )
            .addIngredient('#', SimpleItem(ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).setDisplayName(" ")))
            .build()

        game.alivePlayers.forEachIndexed { index, gp ->
            if (index < 27) {
                val target = Bukkit.getPlayer(gp.uuid) ?: return@forEachIndexed
                gui.setItem(index, SimpleItem(
                    ItemBuilder(Material.PLAYER_HEAD)
                        .setDisplayName("§a${gp.name}")
                        .addLoreLines(
                            "§7Kills: §f${gp.kills}",
                            "",
                            "§eTeleport to player"
                        )
                ) { _: Click ->
                    spectator.closeInventory()
                    spectator.teleport(target.location)
                })
            }
        }

        Window.single()
            .setViewer(spectator)
            .setTitle("§aSpectate Player")
            .setGui(gui)
            .build()
            .open()
    }
}
