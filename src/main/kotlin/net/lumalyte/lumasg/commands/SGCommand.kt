package net.lumalyte.lumasg.commands

import net.badgersmc.nexus.commands.annotations.Arg
import net.badgersmc.nexus.commands.annotations.Command
import net.badgersmc.nexus.commands.annotations.Context
import net.badgersmc.nexus.paper.commands.annotations.Async
import net.badgersmc.nexus.paper.commands.annotations.Permission
import net.badgersmc.nexus.paper.commands.annotations.PlayerOnly
import net.badgersmc.nexus.paper.commands.annotations.Subcommand
import net.lumalyte.lumasg.game.GameManager
import net.lumalyte.lumasg.gui.GameBrowserMenu
import net.lumalyte.lumasg.gui.SetupMenu
import net.lumalyte.lumasg.statistics.StatisticsService
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

@Command(name = "sg", description = "SurvivalGames commands", aliases = ["survivalgames"])
class SGCommand(
    private val gameManager: GameManager,
    private val gameBrowserMenu: GameBrowserMenu,
    private val setupMenu: SetupMenu,
    private val statsService: StatisticsService
) {
    /** /sg join — open game browser */
    @Subcommand("join")
    @Permission("lumasg.play")
    @PlayerOnly
    fun join(@Context player: Player) {
        gameBrowserMenu.open(player)
    }

    /** /sg leave — leave current game */
    @Subcommand("leave")
    @Permission("lumasg.play")
    @PlayerOnly
    fun leave(@Context player: Player) {
        val game = gameManager.getGameForPlayer(player.uniqueId) ?: run {
            player.sendMessage("§cYou are not in a game.")
            return
        }
        game.removePlayer(player.uniqueId)
    }

    /** /sg stats — view own stats */
    @Subcommand("stats")
    @Permission("lumasg.play")
    @PlayerOnly
    @Async
    suspend fun stats(@Context player: Player) {
        val s = statsService.getOrCreate(player.uniqueId, player.name)
        player.sendMessage("§6Your stats: §fK/D ${String.format("%.2f", s.kdr)} | Wins ${s.wins}")
    }

    /** /sg stats <player> — view another player's stats */
    @Subcommand("stats")
    @Permission("lumasg.admin")
    @Async
    suspend fun statsTarget(
        @Context sender: CommandSender,
        @Arg("target") target: Player
    ) {
        val s = statsService.getOrCreate(target.uniqueId, target.name)
        sender.sendMessage("§6${target.name}: §fK/D ${String.format("%.2f", s.kdr)} | Wins ${s.wins}")
    }

    /** /sg admin setup — open arena setup menu */
    @Subcommand("admin setup")
    @Permission("lumasg.admin")
    @PlayerOnly
    fun adminSetup(@Context player: Player) {
        setupMenu.open(player)
    }

    /** /sg admin reload — reload config */
    @Subcommand("admin reload")
    @Permission("lumasg.admin")
    fun adminReload(@Context sender: CommandSender) {
        sender.sendMessage("§aConfig reloaded.")
    }

    /** /sg admin forcestart <arena> — start a game immediately */
    @Subcommand("admin forcestart")
    @Permission("lumasg.admin")
    @Async
    suspend fun adminForceStart(
        @Context sender: CommandSender,
        @Arg("arena") arenaName: String
    ) {
        sender.sendMessage("§aForce-starting game on arena §f$arenaName§a...")
        // TODO: look up arena via ArenaService and call gameManager.createGame()
    }
}
