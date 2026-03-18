package net.lumalyte.lumasg.commands

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import net.badgersmc.nexus.commands.annotations.Arg
import net.badgersmc.nexus.commands.annotations.Command
import net.badgersmc.nexus.commands.annotations.Context
import net.badgersmc.nexus.paper.commands.annotations.Async
import net.badgersmc.nexus.paper.commands.annotations.Permission
import net.badgersmc.nexus.paper.commands.annotations.PlayerOnly
import net.badgersmc.nexus.paper.commands.annotations.Subcommand
import net.badgersmc.nexus.paper.commands.annotations.Suggests
import net.lumalyte.lumasg.domain.GameMode
import net.lumalyte.lumasg.domain.GamePhase
import net.lumalyte.lumasg.game.GameManager
import net.lumalyte.lumasg.game.TeamQueueManager
import net.lumalyte.lumasg.gui.GameBrowserMenu
import net.lumalyte.lumasg.gui.MainMenu
import net.lumalyte.lumasg.gui.SetupMenu
import net.lumalyte.lumasg.hooks.LumaGuildsHook
import net.lumalyte.lumasg.listeners.AdminWandListener
import net.lumalyte.lumasg.service.ArenaService
import net.lumalyte.lumasg.statistics.StatisticsService
import net.lumalyte.lumasg.debug.DummyManager
import net.lumalyte.lumasg.util.ConfigurationManager
import net.lumalyte.lumasg.util.cache.PlayerDataCache
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.command.ConsoleCommandSender
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin

@Command(name = "sg", description = "SurvivalGames commands", aliases = ["survivalgames"])
class SGCommand(
    private val plugin: JavaPlugin,
    private val gameManager: GameManager,
    private val arenaService: ArenaService,
    private val gameBrowserMenu: GameBrowserMenu,
    private val mainMenu: MainMenu,
    private val setupMenu: SetupMenu,
    private val statsService: StatisticsService,
    private val teamQueueManager: TeamQueueManager,
    private val lumaGuildsHook: LumaGuildsHook,
    private val adminWandListener: AdminWandListener,
    private val configurationManager: ConfigurationManager,
    private val playerDataCache: PlayerDataCache,
    private val dummyManager: DummyManager
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

    /** /sg stats <player> — view a player's stats */
    @Subcommand("stats")
    @Permission("lumasg.play")
    @Async
    suspend fun stats(
        @Context sender: CommandSender,
        @Arg("target") target: Player
    ) {
        val s = statsService.getOrCreate(target.uniqueId, target.name)
        val label = if (sender is Player && sender.uniqueId == target.uniqueId) "Your stats" else target.name
        sender.sendMessage("§6$label: §fK/D ${String.format("%.2f", s.kdr)} | Wins ${s.wins}")
    }

    /** /sg menu — open main menu */
    @Subcommand("menu")
    @Permission("lumasg.play")
    @PlayerOnly
    fun menu(@Context player: Player) {
        mainMenu.open(player)
    }

    /** /sg help — show help text */
    @Subcommand("help")
    fun help(@Context sender: CommandSender) {
        showHelpText(sender)
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

    /** /sg team leave — leave your current team */
    @Subcommand("team leave")
    @Permission("lumasg.play")
    @PlayerOnly
    fun teamLeave(@Context player: Player) {
        teamQueueManager.removeFromQueue(player.uniqueId)
        player.sendMessage("§aYou have left your team.")
    }

    /** /sg team list — list your team members */
    @Subcommand("team list")
    @Permission("lumasg.play")
    @PlayerOnly
    fun teamList(@Context player: Player) {
        val team = teamQueueManager.getPreGameTeam(player.uniqueId)
        if (team == null) {
            player.sendMessage("§cYou are not in a team.")
            return
        }
        player.sendMessage("§6=== ${team.displayName} ===")
        val onlineMembers = team.members.mapNotNull { Bukkit.getPlayer(it) }
        if (onlineMembers.isEmpty()) {
            player.sendMessage("§7No online members")
        } else {
            for (member in onlineMembers) {
                val suffix = if (member.uniqueId == player.uniqueId) " §e(You)" else ""
                player.sendMessage("§7• §a${member.name}$suffix")
            }
        }
        player.sendMessage("§7Team size: ${team.members.size} players")
    }

    // ── Game management commands ──────────────────────────────────────────

    /** /sg start <arena> — start a game in an arena */
    @Subcommand("start")
    @Permission("lumasg.admin")
    fun start(@Context sender: CommandSender, @Arg("arena") @Suggests("arenaNames") arenaName: String) {
        val arena = arenaService.getArena(arenaName) ?: run {
            sender.sendMessage("§cArena '$arenaName' not found.")
            return
        }
        val existing = gameManager.getGameByArena(arena)
        if (existing != null) {
            sender.sendMessage("§cA game is already running in arena '$arenaName' (${existing.phase::class.simpleName}).")
            return
        }
        val game = gameManager.createGame(arena, GameMode.Solo)
        sender.sendMessage("§aStarted game in arena '$arenaName' (ID: ${game.id}).")
    }

    /** /sg stop <arena> — stop a game in an arena */
    @Subcommand("stop")
    @Permission("lumasg.admin")
    fun stop(@Context sender: CommandSender, @Arg("arena") @Suggests("arenaNames") arenaName: String) {
        val game = gameManager.getGameByArena(arenaName) ?: run {
            sender.sendMessage("§cNo active game found in arena '$arenaName'.")
            return
        }
        if (game.phase is GamePhase.Waiting) {
            sender.sendMessage("§cGame in arena '$arenaName' has not started yet.")
            return
        }
        game.scope.cancel(CancellationException("Stopped by ${sender.name}"))
        sender.sendMessage("§aStopped game in arena '$arenaName'.")
    }

    /** /sg addplayer <player> <arena> — add a player to a game */
    @Subcommand("addplayer")
    @Permission("lumasg.admin")
    fun addPlayer(
        @Context sender: CommandSender,
        @Arg("player") targetName: String,
        @Arg("arena") @Suggests("arenaNames") arenaName: String
    ) {
        val target = Bukkit.getPlayer(targetName) ?: run {
            sender.sendMessage("§cPlayer '$targetName' not found or offline.")
            return
        }
        val arena = arenaService.getArena(arenaName) ?: run {
            sender.sendMessage("§cArena '$arenaName' not found.")
            return
        }
        if (gameManager.isPlayerInGame(target)) {
            val currentGame = gameManager.getGameForPlayer(target.uniqueId)
            sender.sendMessage("§cPlayer '$targetName' is already in a game in arena '${currentGame?.arena?.name}'.")
            return
        }
        val game = gameManager.getGameByArena(arena) ?: gameManager.createGame(arena, GameMode.Solo)
        if (game.phase !is GamePhase.Waiting) {
            sender.sendMessage("§cCannot add player to game in arena '$arenaName' — game is already in progress (${game.phase::class.simpleName}).")
            return
        }
        game.addPlayer(target)
        sender.sendMessage("§aAdded '$targetName' to game in arena '$arenaName'.")
        target.sendMessage("§aYou have been added to a Survival Games match in arena '$arenaName'!")
    }

    /** /sg removeplayer <player> — remove a player from their game */
    @Subcommand("removeplayer")
    @Permission("lumasg.admin")
    fun removePlayer(
        @Context sender: CommandSender,
        @Arg("player") targetName: String
    ) {
        val target = Bukkit.getPlayer(targetName) ?: run {
            sender.sendMessage("§cPlayer '$targetName' not found or offline.")
            return
        }
        val game = gameManager.getGameForPlayer(target.uniqueId) ?: run {
            sender.sendMessage("§cPlayer '$targetName' is not in any game.")
            return
        }
        val arenaName = game.arena.name
        game.removePlayer(target.uniqueId)
        sender.sendMessage("§aRemoved '$targetName' from game in arena '$arenaName'.")
        target.sendMessage("§eYou have been removed from the Survival Games match.")
    }

    /** /sg forcestart <arena> — force start a game regardless of player count */
    @Subcommand("forcestart")
    @Permission("lumasg.admin")
    fun forceStart(@Context sender: CommandSender, @Arg("arena") @Suggests("arenaNames") arenaName: String) {
        val arena = arenaService.getArena(arenaName) ?: run {
            sender.sendMessage("§cArena '$arenaName' not found.")
            return
        }
        val game = gameManager.getGameByArena(arena) ?: run {
            sender.sendMessage("§cNo game found in arena '$arenaName'.")
            return
        }
        if (game.phase !is GamePhase.Waiting) {
            sender.sendMessage("§cGame in arena '$arenaName' is not in waiting state (${game.phase::class.simpleName}).")
            return
        }
        if (game.players.isEmpty()) {
            sender.sendMessage("§cNo players in game for arena '$arenaName'.")
            return
        }
        // Game lifecycle is already launched — it waits for players.
        // Force-starting by creating the game already triggered launch().
        sender.sendMessage("§aForce-started game in arena '$arenaName' with ${game.players.size} players.")
    }

    /** /sg list — list all games and arenas */
    @Subcommand("list")
    fun list(@Context sender: CommandSender) {
        sender.sendMessage("§6=== Survival Games Status ===")
        val activeGames = gameManager.getAllActiveGames()
        if (activeGames.isEmpty()) {
            sender.sendMessage("§7No active games")
        } else {
            sender.sendMessage("§eActive Games:")
            for (game in activeGames) {
                val phaseName = game.phase::class.simpleName
                sender.sendMessage("  §f${game.arena.name}: §a$phaseName §7(${game.players.size} players)")
            }
        }
        sender.sendMessage("§eAvailable Arenas:")
        for (arena in arenaService.getAllArenas()) {
            val game = gameManager.getGameByArena(arena)
            if (game != null) {
                sender.sendMessage("  §f${arena.name}: §cIn Use §7(${game.phase::class.simpleName})")
            } else {
                sender.sendMessage("  §f${arena.name}: §aAvailable")
            }
        }
    }

    /** /sg info <arena> — show arena information */
    @Subcommand("info")
    fun info(@Context sender: CommandSender, @Arg("arena") @Suggests("arenaNames") arenaName: String) {
        val arena = arenaService.getArena(arenaName) ?: run {
            sender.sendMessage("§cArena '$arenaName' not found.")
            return
        }
        sender.sendMessage("§6=== Arena Info: ${arena.name} ===")
        val game = gameManager.getGameByArena(arena)
        if (game != null) {
            sender.sendMessage("§eStatus: §cIn Use")
            sender.sendMessage("§eGame State: §f${game.phase::class.simpleName}")
            sender.sendMessage("§ePlayers: §f${game.players.size}/${arena.maxPlayers}")
            if (game.players.isNotEmpty()) {
                sender.sendMessage("§ePlayer List:")
                for (uuid in game.players.keys) {
                    val p = Bukkit.getPlayer(uuid)
                    if (p != null) {
                        sender.sendMessage("  §7- §f${p.name}")
                    }
                }
            }
        } else {
            sender.sendMessage("§eStatus: §aAvailable")
        }
        sender.sendMessage("§eMax Players: §f${arena.maxPlayers}")
        sender.sendMessage("§eWorld: §f${arena.worldName}")
        sender.sendMessage("§eSpawn Points: §f${arena.spawnPoints.size}")
        sender.sendMessage("§eRadius: §f${arena.radius}")
    }

    /** /sg myinfo — show current game info for the player */
    @Subcommand("myinfo")
    @Permission("lumasg.play")
    @PlayerOnly
    fun myInfo(@Context player: Player) {
        val game = gameManager.getGameForPlayer(player.uniqueId) ?: run {
            player.sendMessage("§cYou are not in a game.")
            return
        }
        player.sendMessage("§6=== Current Game Info ===")
        player.sendMessage("§eArena: §f${game.arena.name}")
        player.sendMessage("§eState: §f${game.phase::class.simpleName}")
        player.sendMessage("§ePlayers: §f${game.players.size}/${game.arena.maxPlayers}")
        player.sendMessage("§ePvP Enabled: §f${if (game.isPvpEnabled()) "§aYes" else "§cNo"}")
    }

    // ── Admin commands ───────────────────────────────────────────────────

    /** /sg admin setup — open arena setup menu */
    @Subcommand("admin setup")
    @Permission("lumasg.admin")
    @PlayerOnly
    fun adminSetup(@Context player: Player) {
        setupMenu.open(player)
    }

    /** /sg admin reload — reload configuration */
    @Subcommand("admin reload")
    @Permission("lumasg.admin")
    fun adminReload(@Context sender: CommandSender) {
        plugin.reloadConfig()
        configurationManager.updateAllConfigs()
        sender.sendMessage("§aLumaSG configuration reloaded.")
    }

    /** /sg wand — give the admin wand */
    @Subcommand("wand")
    @Permission("lumasg.admin")
    @PlayerOnly
    fun wand(@Context player: Player) {
        adminWandListener.giveWand(player)
    }

    /** /sg arena select <name> — select an arena for wand editing */
    @Subcommand("arena select")
    @Permission("lumasg.admin")
    @PlayerOnly
    fun arenaSelect(@Context player: Player, @Arg("arena") @Suggests("arenaNames") arenaName: String) {
        val arena = arenaService.getArena(arenaName) ?: run {
            player.sendMessage("§cArena '$arenaName' not found.")
            return
        }
        adminWandListener.setSelectedArena(player, arena)
        player.sendMessage("§aArena '${arena.name}' selected! Use the admin wand to edit it.")
    }

    /** /sg create <name> <radius> — create a new arena at your location */
    @Subcommand("create")
    @Permission("lumasg.admin")
    @PlayerOnly
    @Async
    suspend fun create(
        @Context player: Player,
        @Arg("name") name: String,
        @Arg("radius") radius: Int
    ) {
        // Sanitize arena name
        val sanitized = name.replace(Regex("[^a-zA-Z0-9_-]"), "")
        if (sanitized != name) {
            player.sendMessage("§eArena name sanitized to: '$sanitized'")
        }
        if (sanitized.isBlank()) {
            player.sendMessage("§cInvalid arena name.")
            return
        }
        if (radius < 10 || radius > 1000) {
            player.sendMessage("§cRadius must be between 10 and 1000.")
            return
        }
        if (arenaService.getArena(sanitized) != null) {
            player.sendMessage("§cAn arena with that name already exists.")
            return
        }
        val arena = arenaService.createArena(sanitized, player.location, radius)
        adminWandListener.setSelectedArena(player, arena)
        player.sendMessage("§aArena '$sanitized' created with radius $radius!")
        player.sendMessage("§aArena selected! Use the admin wand to edit it.")
    }

    // ── Debug commands ───────────────────────────────────────────────────

    /** /sg debug skip-pvp <arena> — skip grace period on an arena */
    @Subcommand("debug skip-pvp")
    @Permission("lumasg.admin")
    fun debugSkipPvp(@Context sender: CommandSender, @Arg("arena") @Suggests("arenaNames") arenaName: String) {
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

    /** /sg debug dummies <arena> [count] — spawn test dummy villagers on spawn points */
    @Subcommand("debug dummies")
    @Permission("lumasg.admin")
    @PlayerOnly
    fun debugDummies(
        @Context player: Player,
        @Arg("arena") @Suggests("arenaNames") arenaName: String
    ) {
        val arena = arenaService.getArena(arenaName) ?: run {
            player.sendMessage("§cArena '$arenaName' not found.")
            return
        }
        val game = gameManager.getGameByArena(arena) ?: gameManager.createGame(arena, GameMode.Solo)
        if (game.phase !is GamePhase.Waiting) {
            player.sendMessage("§cCannot spawn dummies — game is already in progress.")
            return
        }
        // Add the player first if not already in the game
        if (!gameManager.isPlayerInGame(player)) {
            game.addPlayer(player)
        }
        val maxDummies = arena.spawnPoints.size - game.players.size
        val toSpawn = maxDummies
        if (toSpawn <= 0) {
            player.sendMessage("§cNo spawn points available for dummies.")
            return
        }
        val spawned = dummyManager.spawnDummies(game, toSpawn)
        player.sendMessage("§aSpawned $spawned dummy villagers in arena '$arenaName'.")
        player.sendMessage("§7Use §e/sg start $arenaName §7to begin the game.")
    }

    /** /sg debug remove-dummies — remove all dummy villagers */
    @Subcommand("debug remove-dummies")
    @Permission("lumasg.admin")
    fun debugRemoveDummies(@Context sender: CommandSender) {
        dummyManager.removeAll()
        sender.sendMessage("§aAll dummy villagers removed.")
    }

    /** /sg debug cache-stats — show cache performance statistics */
    @Subcommand("debug cache-stats")
    @Permission("lumasg.admin")
    fun debugCacheStats(@Context sender: CommandSender) {
        sender.sendMessage("§6=== LumaSG Cache Performance Stats ===")
        sender.sendMessage("§aPlayer Data: §f${playerDataCache.getCacheStats()}")

        val runtime = Runtime.getRuntime()
        val totalMemory = runtime.totalMemory() / 1024 / 1024
        val freeMemory = runtime.freeMemory() / 1024 / 1024
        val usedMemory = totalMemory - freeMemory
        sender.sendMessage("§eMemory: §f${usedMemory}MB used / ${totalMemory}MB total")
        sender.sendMessage("§6=====================================")
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private fun showHelpText(sender: CommandSender) {
        val isConsole = sender is ConsoleCommandSender
        sender.sendMessage("§6=== LumaSG Commands ===")

        if (!isConsole) {
            sender.sendMessage("§e/sg §7— Open the main menu")
            sender.sendMessage("§e/sg join §7— Browse and join games")
            sender.sendMessage("§e/sg leave §7— Leave current game")
            sender.sendMessage("§e/sg invite <player> §7— Invite to team")
            sender.sendMessage("§e/sg accept §7— Accept team invite")
            sender.sendMessage("§e/sg decline §7— Decline team invite")
            sender.sendMessage("§e/sg team leave §7— Leave your team")
            sender.sendMessage("§e/sg team list §7— List team members")
            sender.sendMessage("§e/sg mute §7— Toggle queue broadcasts")
            sender.sendMessage("§e/sg myinfo §7— Show current game info")
            sender.sendMessage("§e/sg stats <player> §7— View player stats")
        }

        sender.sendMessage("§e/sg list §7— List all games and arenas")
        sender.sendMessage("§e/sg info <arena> §7— Show arena information")
        sender.sendMessage("§e/sg help §7— Show this help text")

        if (sender.hasPermission("lumasg.admin")) {
            sender.sendMessage("§c=== Admin Commands ===")
            sender.sendMessage("§e/sg start <arena> §7— Start a game")
            sender.sendMessage("§e/sg stop <arena> §7— Stop a game")
            sender.sendMessage("§e/sg forcestart <arena> §7— Force start a game")
            sender.sendMessage("§e/sg addplayer <player> <arena> §7— Add player to game")
            sender.sendMessage("§e/sg removeplayer <player> §7— Remove player from game")
            sender.sendMessage("§e/sg create <name> <radius> §7— Create an arena")
            sender.sendMessage("§e/sg wand §7— Get the admin wand")
            sender.sendMessage("§e/sg arena select <name> §7— Select arena for editing")
            sender.sendMessage("§e/sg admin setup §7— Open setup menu")
            sender.sendMessage("§e/sg admin reload §7— Reload config")
        }
    }
}
