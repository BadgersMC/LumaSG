package net.lumalyte.lumasg.gui

import net.badgersmc.nexus.annotations.Service
import net.lumalyte.lumasg.game.Game
import net.lumalyte.lumasg.game.GameManager
import net.lumalyte.lumasg.util.cache.GuiComponentCache
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
class GameBrowserMenu(
    private val gameManager: GameManager,
    private val guiCache: GuiComponentCache
) {

    fun open(player: Player) {
        val items = gameManager.getAllActiveGames().map { game -> buildGameItem(game, player) }

        val gui = PagedGui.items()
            .setStructure(
                "# # # # # # # # #",
                "# x x x x x x x #",
                "# x x x x x x x #",
                "# # # < # > # # #"
            )
            .addIngredient('x', Markers.CONTENT_LIST_SLOT_HORIZONTAL)
            .addIngredient('#', guiCache.createBorderItem("gray"))
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
            .setTitle("§6Available Games")
            .setGui(gui)
            .build()
            .open()
    }

    private fun buildGameItem(game: Game, viewer: Player) = SimpleItem(
        ItemBuilder(Material.GRASS_BLOCK)
            .setDisplayName("§6${game.arena.displayName}")
            .addLoreLines(
                "§7Mode: §f${game.mode.displayName}",
                "§7Loot: §f${game.lootMode.name.lowercase().replaceFirstChar { it.uppercase() }}",
                "§7Players: §f${game.players.size}/${game.arena.maxPlayers}",
                "§7Phase: §f${game.phase::class.simpleName}",
                "",
                "§eClick to join!"
            )
    ) { _: Click ->
        viewer.closeInventory()
        if (gameManager.isPlayerInGame(viewer)) {
            viewer.sendMessage("§cYou are already in a game.")
            return@SimpleItem
        }
        if (game.players.size >= game.arena.maxPlayers) {
            viewer.sendMessage("§cThat game is full.")
            return@SimpleItem
        }
        game.addPlayer(viewer)
        viewer.sendMessage("§aYou joined §6${game.arena.displayName}§a!")
    }
}
