package net.lumalyte.lumasg.config

import net.badgersmc.nexus.config.Comment
import net.badgersmc.nexus.config.ConfigFile
import net.lumalyte.lumasg.domain.LootMode

@ConfigFile("config")
data class LumaSGConfig(
    @Comment("Lobby Settings")
    var lobby: LobbyConfig = LobbyConfig(),
    @Comment("Game Settings")
    var game: GameConfig = GameConfig(),
    @Comment("Arena Settings")
    var arena: ArenaConfig = ArenaConfig(),
    @Comment("World Border Settings")
    var worldBorder: WorldBorderConfig = WorldBorderConfig(),
    @Comment("Scoreboard Settings")
    var scoreboard: ScoreboardConfig = ScoreboardConfig(),
    @Comment("Chest Settings")
    var chest: ChestConfig = ChestConfig(),
    @Comment("Spectator Settings")
    var spectator: SpectatorConfig = SpectatorConfig(),
    @Comment("Reward Settings")
    var rewards: RewardsConfig = RewardsConfig(),
    @Comment("Messages (MiniMessage format)")
    var messages: MessagesSection = MessagesSection(),
    @Comment("Statistics Settings")
    var statistics: StatisticsConfig = StatisticsConfig(),
    @Comment("Database Settings")
    var database: DatabaseConfig = DatabaseConfig(),
    @Comment("Discord Integration Settings")
    var discord: DiscordConfig = DiscordConfig(),
    @Comment("Queue system settings")
    var queue: QueueConfig = QueueConfig(),
    @Comment("Debug Settings — enable for verbose logging")
    var debug: DebugConfig = DebugConfig(),
    @Comment("Airstrike Designator Settings")
    var airstrike: AirstrikeConfig = AirstrikeConfig(),
    @Comment("Smoke Grenade Settings")
    var smokeGrenade: SmokeGrenadeConfig = SmokeGrenadeConfig(),
    @Comment("Glider Feather Settings")
    var glider: GliderConfig = GliderConfig(),
    @Comment("Loot Mode Settings — Classic, Modern, OP")
    var modes: ModesSection = ModesSection()
) {

    data class LobbyConfig(
        @Comment("World name for the lobby")
        var world: String = "world",
        @Comment("Lobby spawn X coordinate")
        var x: Double = 0.0,
        @Comment("Lobby spawn Y coordinate")
        var y: Double = 64.0,
        @Comment("Lobby spawn Z coordinate")
        var z: Double = 0.0,
        var yaw: Float = 0f,
        var pitch: Float = 0f,
        @Comment("Teleport player to lobby when they leave a game")
        var teleportOnLeave: Boolean = true,
        @Comment("Teleport player to lobby when a game ends")
        var teleportOnEnd: Boolean = true
    )

    data class GameConfig(
        @Comment("Minimum players required to start a game")
        var minPlayers: Int = 2,
        @Comment("Maximum players per game instance")
        var maxPlayers: Int = 24,
        @Comment("Countdown duration before game starts (seconds)")
        var countdownSeconds: Int = 30,
        @Comment("Grace period with PvP disabled (seconds)")
        var gracePeriodSeconds: Int = 60,
        @Comment("Active game duration before deathmatch (minutes)")
        var gameTimeMinutes: Int = 10,
        @Comment("Deathmatch phase duration (minutes)")
        var deathmatchTimeMinutes: Int = 3,
        @Comment("Delay before teleporting players (seconds)")
        var teleportDelay: Int = 1,
        @Comment("Allow eliminated players to spectate")
        var allowSpectating: Boolean = true,
        @Comment("Clear player inventory on game join")
        var clearInventory: Boolean = true,
        @Comment("Restore player inventory after game ends")
        var restoreInventory: Boolean = true,
        @Comment("Save and restore player location")
        var saveLocation: Boolean = true,
        @Comment("Default game mode: SOLO, DUOS, TRIOS, SQUADS")
        var defaultMode: String = "SOLO",
        @Comment("Time for arena setup before game starts (seconds)")
        var setupPeriodSeconds: Int = 120,
        @Comment("Allow players to reconnect during an active game")
        var allowReconnect: Boolean = false,
        @Comment("Team mode settings")
        var teams: TeamsConfig = TeamsConfig()
    )

    data class TeamsConfig(
        @Comment("Teammates glow green for visibility")
        var glowEffects: Boolean = true,
        @Comment("Auto-balance teams if uneven")
        var autoBalance: Boolean = true,
        @Comment("Allow friendly fire between teammates")
        var friendlyFire: Boolean = false,
        @Comment("Team invitation timeout (seconds)")
        var invitationTimeout: Int = 60,
        @Comment("Maximum team size (overridden by game mode)")
        var maxTeamSize: Int = 3
    )

    data class ArenaConfig(
        @Comment("Default arena radius (blocks)")
        var defaultRadius: Int = 200,
        var defaultMaxPlayers: Int = 24,
        var defaultMinPlayers: Int = 1,
        @Comment("Auto-save arena changes")
        var autoSave: Boolean = true,
        @Comment("Delay before saving arena changes to prevent excessive saves (seconds)")
        var saveDelaySeconds: Int = 3
    )

    data class WorldBorderConfig(
        @Comment("Initial border size when the game starts")
        var initialSize: Double = 500.0,
        @Comment("Deathmatch border settings")
        var deathmatch: DeathmatchBorderConfig = DeathmatchBorderConfig()
    )

    data class DeathmatchBorderConfig(
        @Comment("Enable gradual border shrinking during deathmatch")
        var enableShrinking: Boolean = true,
        @Comment("Starting border size when deathmatch begins")
        var startSize: Double = 75.0,
        @Comment("Final border size the border shrinks to")
        var endSize: Double = 10.0,
        @Comment("How long it takes to shrink from start to end (seconds)")
        var shrinkDurationSeconds: Long = 120L,
        @Comment("Show warning messages about the shrinking border")
        var showWarnings: Boolean = true
    )

    data class ScoreboardConfig(
        @Comment("Enable the in-game scoreboard")
        var enabled: Boolean = true,
        @Comment("Scoreboard title (MiniMessage format)")
        var title: String = "<gold><bold>Survival Games</bold></gold>",
        @Comment("Update interval in ticks (20 ticks = 1 second)")
        var updateInterval: Int = 40,
        @Comment("Scoreboard lines. Placeholders: <alive>, <total>, <time>")
        var lines: List<String> = listOf(
            "<gray><st>--------------------</st></gray>",
            "<gold>Players: <white><alive></white><gray>/</gray><white><total></white></gold>",
            "<gold>Time: <white><time></white></gold>",
            "<gray><st>--------------------</st></gray>"
        ),
        @Comment("Additional lines shown during deathmatch phase")
        var deathmatchLines: List<String> = listOf(
            "<red><bold>DEATHMATCH</bold></red>"
        )
    )

    data class ChestConfig(
        @Comment("Minimum items per chest")
        var minItems: Int = 3,
        @Comment("Maximum items per chest")
        var maxItems: Int = 8,
        var refillChests: Boolean = true,
        @Comment("Seconds between chest refills")
        var refillTime: Int = 300,
        @Comment("Whether chests refill after a delay")
        var refillEnabled: Boolean = true,
        @Comment("Seconds after game start before first chest refill")
        var refillTimeSeconds: Int = 300,
        @Comment("Better loot near center, worse loot at edges")
        var distanceBasedLoot: Boolean = true
    )

    data class SpectatorConfig(
        @Comment("Allow spectating after elimination")
        var enabled: Boolean = true,
        @Comment("Teleport spectators to lobby when game ends")
        var teleportToLobbyAfterGame: Boolean = true
    )

    data class RewardsConfig(
        @Comment("Enable win/kill rewards")
        var enabled: Boolean = true,
        @Comment("Mob coins awarded to the winner")
        var mobCoins: Int = 1000,
        @Comment("Command executed when a player wins. Placeholder: <player>")
        var winCommand: String = "",
        @Comment("Command executed on each kill. Placeholders: <player>, <kills>")
        var killCommand: String = "",
        @Comment("Winner announcement settings")
        var winnerAnnouncement: WinnerAnnouncementConfig = WinnerAnnouncementConfig()
    )

    data class WinnerAnnouncementConfig(
        var enabled: Boolean = true,
        @Comment("Show pixel art of winner's skin")
        var usePixelArt: Boolean = true,
        @Comment("Title text (MiniMessage). Placeholder: <player>")
        var title: String = "<gradient:gold:yellow><bold>WINNER!</bold></gradient>",
        @Comment("Subtitle text (MiniMessage). Placeholder: <player>")
        var subtitle: String = "<bold><player></bold>",
        @Comment("Chat message. Placeholder: <player>")
        var message: String = "<green>The game has ended! <player> is the winner!",
        @Comment("Team victory message. Placeholders: <members>, <teamname>, <teamsize>, <kills>")
        var teamMessage: String = "<green>Team <yellow><members> <green>is victorious!",
        @Comment("Launch fireworks on win")
        var fireworks: Boolean = true,
        @Comment("Number of fireworks to launch")
        var fireworkCount: Int = 20,
        @Comment("Winner pixel art settings")
        var pixelArt: PixelArtConfig = PixelArtConfig()
    )

    data class PixelArtConfig(
        var enabled: Boolean = true,
        @Comment("Crafatar API URL for player skin. Placeholder: <uuid>")
        var apiUrl: String = "https://crafatar.com/avatars/<uuid>?size=8&overlay",
        @Comment("Pixel art grid size")
        var size: Int = 8,
        @Comment("Character used for each pixel")
        var character: String = "\u2B1B",
        @Comment("Cache downloaded skins")
        var cacheEnabled: Boolean = true,
        @Comment("How long to cache skins (minutes)")
        var cacheDurationMinutes: Int = 30,
        @Comment("Pre-cache skins when 3 players remain")
        var preCacheEnabled: Boolean = true
    )

    data class MessagesSection(
        @Comment("Message prefix for plugin messages")
        var prefix: String = "<dark_gray>[<gold>LumaSG</gold>] <reset>",
        @Comment("Broadcast game events to all participants")
        var broadcastEvents: Boolean = true,
        var gameStart: String = "<green>The game has started! Good luck!",
        @Comment("Placeholder: <time>")
        var gracePeriodStart: String = "<yellow>Grace period has started! PvP is disabled for <time> seconds.",
        var gracePeriodEnd: String = "<red>Grace period has ended! PvP is now enabled!",
        @Comment("Placeholders: <player>, <current>, <max>")
        var playerJoin: String = "<gray><player> <yellow>has joined! <gray>(<current>/<max>)",
        var playerLeave: String = "<gray><player> <yellow>has left. <gray>(<current>/<max>)",
        @Comment("Placeholder: <player>")
        var playerDeath: String = "<gray><player> <red>has been eliminated!",
        @Comment("Placeholders: <player>, <killer>")
        var playerKill: String = "<gray><player> <red>was eliminated by <gray><killer><red>!",
        @Comment("Placeholder: <player>")
        var gameEnd: String = "<green>The game has ended! <player> wins!",
        @Comment("Placeholder: <time>")
        var countdown: String = "<yellow>Game starting in <gray><time> <yellow>seconds!",
        @Comment("Kill-feed death message. Placeholders: <victim>, <killer>, <weapon>")
        var deathByPlayer: String = "<red><victim> <gray>was killed by <red><killer> <gray>with <white><weapon>",
        var deathNatural: String = "<red><victim> died",
        @Comment("Kill notification sent to killer. Placeholders: <victim>, <kills>")
        var killNotification: String = "<green>You killed <white><victim><green>! (<gold><kills> kills<green>)",
        @Comment("Configurable death message system")
        var deathMessages: DeathMessagesConfig = DeathMessagesConfig(),
        @Comment("Deathmatch reminder warnings")
        var deathmatchReminders: DeathmatchReminderConfig = DeathmatchReminderConfig()
    )

    data class DeathMessagesConfig(
        var enabled: Boolean = true,
        @Comment("Placeholders: <victim>, <killer>, <action>, <remaining>")
        var format: String = "<dark_red>\u2620 <red><victim> <gray>was <action> <gray>by <killer>! <yellow><remaining> players remain!",
        @Comment("Shown when only 2 players remain. Placeholders: <victim>, <killer>")
        var finalTwoFormat: String = "<dark_red>\u2694 FINAL BATTLE \u2694\n<victim> vs <killer>",
        @Comment("Shown when a player wins. Placeholder: <winner>")
        var winnerFormat: String = "<gold>\u2694 VICTORY \u2694\n<winner> is victorious!"
    )

    data class DeathmatchReminderConfig(
        var enabled: Boolean = true,
        @Comment("Seconds before deathmatch to show reminders (e.g. 300 = 5 min)")
        var reminderTimes: List<Int> = listOf(300, 180, 120, 60, 30, 10),
        @Comment("Play sounds with reminders")
        var playSounds: Boolean = true
    )

    data class StatisticsConfig(
        @Comment("Enable player statistics tracking")
        var enabled: Boolean = true,
        @Comment("How often to save pending statistics (seconds)")
        var saveIntervalSeconds: Int = 300,
        @Comment("Preload player statistics when they join the server")
        var preloadOnJoin: Boolean = true,
        @Comment("Track damage dealt and taken")
        var trackDamage: Boolean = true,
        @Comment("Track chests opened")
        var trackChests: Boolean = true
    )

    data class DatabaseConfig(
        @Comment("Database type: SQLITE, MYSQL, or POSTGRESQL")
        var type: String = "SQLITE",
        @Comment("SQLite file path (relative to plugin data folder)")
        var sqliteFile: String = "lumasg.db",
        @Comment("Database host (for MySQL/PostgreSQL)")
        var host: String = "localhost",
        @Comment("Database port (3306 for MySQL, 5432 for PostgreSQL)")
        var port: Int = 3306,
        @Comment("Database name")
        var database: String = "lumasg",
        var username: String = "root",
        var password: String = "password",
        @Comment("HikariCP connection pool settings")
        var pool: PoolConfig = PoolConfig(),
        @Comment("Enable SSL connections")
        var useSsl: Boolean = false,
        @Comment("Automatically run database migrations on startup")
        var autoMigrate: Boolean = true
    )

    data class PoolConfig(
        @Comment("Minimum idle connections in pool")
        var minimumIdle: Int = 2,
        @Comment("Maximum connections in pool")
        var maximumPoolSize: Int = 8,
        @Comment("Connection timeout (milliseconds)")
        var connectionTimeout: Long = 30000L,
        @Comment("Idle timeout (milliseconds)")
        var idleTimeout: Long = 600000L,
        @Comment("Maximum connection lifetime (milliseconds)")
        var maxLifetime: Long = 1800000L
    )

    data class DiscordConfig(
        @Comment("Enable Discord integration")
        var enabled: Boolean = false,
        @Comment("Discord bot token (keep secure, never share)")
        var botToken: String = "",
        @Comment("Discord server (guild) ID")
        var guildId: String = "",
        @Comment("Channel ID for game announcements")
        var announcementsChannelId: String = "",
        @Comment("Channel ID for statistics commands")
        var statsChannelId: String = ""
    )

    data class QueueConfig(
        @Comment("Broadcast queue status to non-playing players")
        var broadcastsEnabled: Boolean = true,
        @Comment("Broadcast update interval (seconds)")
        var broadcastInterval: Int = 30,
        @Comment("Allow players to mute queue broadcasts")
        var allowMute: Boolean = true
    )

    data class DebugConfig(
        @Comment("Enable debug logging (verbose, spams console)")
        var enabled: Boolean = false,
        var logLevel: String = "INFO"
    )

    data class AirstrikeConfig(
        @Comment("Enable the Airstrike Designator item")
        var enabled: Boolean = true,
        @Comment("Charge-up time while holding spyglass (ticks, 70 = 3.5s)")
        var chargeTimeTicks: Int = 70,
        @Comment("Maximum targeting range (blocks)")
        var maxRange: Int = 80,
        @Comment("Cancel charge if the player takes damage")
        var cancelOnDamage: Boolean = false,
        @Comment("Blast zone radius (blocks)")
        var blastRadius: Int = 18,
        @Comment("Number of meteors in normal phases")
        var meteorCount: Int = 5,
        @Comment("Number of meteors during deathmatch (reduced for fairness)")
        var deathmatchMeteorCount: Int = 1,
        @Comment("Delay between meteor impacts (ticks)")
        var meteorDelayTicks: Int = 30,
        @Comment("Warning duration before first impact (ticks)")
        var warningDurationTicks: Int = 60,
        @Comment("Lock-on animation duration (ticks)")
        var lockOnDurationTicks: Int = 10,
        @Comment("Per-meteor explosion radius (blocks)")
        var explosionRadius: Int = 4,
        @Comment("Base damage at explosion epicenter")
        var explosionDamage: Double = 6.0,
        @Comment("Debris per radius unit (radius * this = total FallingBlocks)")
        var debrisCountMultiplier: Int = 5,
        @Comment("Broadcast message when airstrike is called (MiniMessage). Placeholder: <player>")
        var broadcastMessage: String = "<red><bold>\u26A0</bold></red> <yellow><player> called in an airstrike!</yellow> <red><bold>\u26A0</bold></red>",
        @Comment("Action bar shown to players in the blast zone")
        var warningActionbar: String = "<red>\u26A0 AIRSTRIKE INCOMING \u26A0</red>",
        @Comment("Action bar shown when target is locked")
        var lockActionbar: String = "<dark_red><bold>\u26A0 TARGET LOCKED \u26A0</bold></dark_red>",
        @Comment("Message shown to caller when no target is in range")
        var noTargetMessage: String = "<gray>No target in range</gray>"
    )

    data class SmokeGrenadeConfig(
        @Comment("Enable the Smoke Grenade item")
        var enabled: Boolean = true,
        @Comment("Smoke cloud radius (blocks)")
        var radius: Int = 16,
        @Comment("Total smoke duration (ticks, 200 = 10s)")
        var durationTicks: Int = 200,
        @Comment("Fade-out duration at the end (ticks)")
        var fadeDurationTicks: Int = 40,
        @Comment("Ticks between particle spawns")
        var particleTickRate: Int = 2,
        @Comment("Ticks between nametag visibility checks")
        var visibilityCheckRate: Int = 4,
        @Comment("Particle points per tick")
        var particleDensity: Int = 80,
        @Comment("Ticks between ambient crackle sounds")
        var ambientSoundInterval: Int = 20
    )

    data class GliderConfig(
        @Comment("Enable the Glider Feather item")
        var enabled: Boolean = true,
        @Comment("Maximum glide duration (ticks, 300 = 15s)")
        var maxDurationTicks: Int = 300,
        @Comment("Must be airborne this long before glide activates (ticks)")
        var minAirtimeTicks: Int = 10,
        @Comment("Cancel fall damage on glide landing")
        var landingFallDamage: Boolean = false,
        @Comment("Prevent firework rockets from boosting glider")
        var blockFireworkBoost: Boolean = true
    )

    data class ModeConfig(
        @Comment("Display name (MiniMessage format)")
        var displayName: String = "",
        @Comment("Mode description")
        var description: String = "",
        @Comment("Optional timing overrides — keys: grace-period, game-duration, deathmatch-duration, countdown-time, chest-refill-delay")
        var timingOverrides: Map<String, Int> = emptyMap()
    )

    data class ModesSection(
        @Comment("Default loot mode when /sg start omits the mode argument")
        var defaultMode: String = "modern",
        @Comment("Classic mode — modernized 1.8-era loot")
        var classic: ModeConfig = ModeConfig(
            displayName = "<gold>Classic",
            description = "Modernized 1.8-era survival games"
        ),
        @Comment("Modern mode — balanced middle ground")
        var modern: ModeConfig = ModeConfig(
            displayName = "<green>Modern",
            description = "Balanced gameplay with all features"
        ),
        @Comment("OP mode — overpowered loot, chaotic battles")
        var op: ModeConfig = ModeConfig(
            displayName = "<red>OP",
            description = "Overpowered loot, chaotic battles"
        )
    )
}

/** Resolve [LumaSGConfig.ModeConfig] for a given [LootMode]. */
fun LumaSGConfig.ModesSection.forMode(mode: LootMode): LumaSGConfig.ModeConfig = when (mode) {
    LootMode.CLASSIC -> classic
    LootMode.MODERN -> modern
    LootMode.OP -> op
}
