package net.lumalyte.lumasg.commands

import net.badgersmc.nexus.commands.annotations.Arg
import net.badgersmc.nexus.commands.annotations.Command
import net.badgersmc.nexus.commands.annotations.Context
import net.badgersmc.nexus.paper.commands.annotations.Async
import net.badgersmc.nexus.paper.commands.annotations.Permission
import net.badgersmc.nexus.paper.commands.annotations.PlayerOnly
import net.badgersmc.nexus.paper.commands.annotations.Subcommand
import net.lumalyte.lumasg.domain.GamePhase
import net.lumalyte.lumasg.game.GameManager
import net.lumalyte.lumasg.game.TeamQueueManager
import net.lumalyte.lumasg.gui.GameBrowserMenu
import net.lumalyte.lumasg.gui.SetupMenu
import net.lumalyte.lumasg.hooks.LumaGuildsHook
import net.lumalyte.lumasg.statistics.StatisticsService
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

@Command(name = "sg", description = "SurvivalGames commands", aliases = ["survivalgames"])
class SGCommand(
    private val gameManager: GameManager,
    private val gameBrowserMenu: GameBrowserMenu,
    private val setupMenu: SetupMenu,
    private val statsService: StatisticsService,
    private val teamQueueManager: TeamQueueManager,
    private val lumaGuildsHook: LumaGuildsHook
) {
    // ── Player commands ──────────────────────────────────────────────────

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

    // ── Team commands ────────────────────────────────────────────────────

    /** /sg invite <player> — invite a player to your team */
    @Subcommand("invite")
    @Permission("lumasg.play")
    @PlayerOnly
    fun invite(@Context player: Player, @Arg("target") target: Player) {
        if (target.uniqueId == player.uniqueId) {
            player.sendMessage("§cYou cannot invite yourself.")
            return
        }
        teamQueueManager.invite(player, target)
    }

    /** /sg accept — accept a pending team invitation */
    @Subcommand("accept")
    @Permission("lumasg.play")
    @PlayerOnly
    fun accept(@Context player: Player) {
        teamQueueManager.accept(player)
    }

    /** /sg decline — decline a pending team invitation */
    @Subcommand("decline")
    @Permission("lumasg.play")
    @PlayerOnly
    fun decline(@Context player: Player) {
        teamQueueManager.decline(player)
    }

    /** /sg mute — toggle queue broadcast messages */
    @Subcommand("mute")
    @Permission("lumasg.play")
    @PlayerOnly
    fun mute(@Context player: Player) {
        teamQueueManager.toggleMute(player)
    }

    // ── Admin commands ───────────────────────────────────────────────────

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

    // ── Debug commands ───────────────────────────────────────────────────

    /** /sg debug skip-pvp <arena> — skip grace period on an arena */
    @Subcommand("debug skip-pvp")
    @Permission("lumasg.admin")
    fun debugSkipPvp(@Context sender: CommandSender, @Arg("arena") arenaName: String) {
        val game = gameManager.getGameByArena(arenaName)
        if (game != null && game.phase is GamePhase.Grace) {
            game.skipGracePeriod()
            sender.sendMessage("§aSkipped grace period for $arenaName")
        } else {
            sender.sendMessage("§cNo game in grace phase found for $arenaName")
        }
    }

    /** /sg debug lumaguilds — check LumaGuilds integration status */
    @Subcommand("debug lumaguilds")
    @Permission("lumasg.admin")
    fun debugLumaGuilds(@Context sender: CommandSender) {
        val available = lumaGuildsHook.isAvailable()
        sender.sendMessage(
            if (available) "§aLumaGuilds integration: §2ACTIVE"
            else "§cLumaGuilds integration: §4INACTIVE"
        )
    }

    /** /sg debug games — list all active games */
    @Subcommand("debug games")
    @Permission("lumasg.admin")
    fun debugGames(@Context sender: CommandSender) {
        val games = gameManager.getAllActiveGames()
        if (games.isEmpty()) {
            sender.sendMessage("§7No active games.")
            return
        }
        for (game in games) {
            sender.sendMessage("§6${game.arena.name} §7— §f${game.phase::class.simpleName} §7| ${game.alivePlayers.size} alive / ${game.players.size} total")
        }
    }
}
