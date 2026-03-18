package net.lumalyte.lumasg.config

import net.badgersmc.nexus.config.Comment
import net.badgersmc.nexus.config.ConfigFile

@ConfigFile("config")
data class LumaSGConfig(
    var lobby: LobbyConfig = LobbyConfig(),
    var game: GameConfig = GameConfig(),
    var arena: ArenaConfig = ArenaConfig(),
    var worldBorder: WorldBorderConfig = WorldBorderConfig(),
    var scoreboard: ScoreboardConfig = ScoreboardConfig(),
    var chest: ChestConfig = ChestConfig(),
    var spectator: SpectatorConfig = SpectatorConfig(),
    var rewards: RewardsConfig = RewardsConfig(),
    var messages: MessagesSection = MessagesSection(),
    var statistics: StatisticsConfig = StatisticsConfig(),
    var database: DatabaseConfig = DatabaseConfig(),
    var discord: DiscordConfig = DiscordConfig(),
    var queue: QueueConfig = QueueConfig(),
    var debug: DebugConfig = DebugConfig(),
    var airstrike: AirstrikeConfig = AirstrikeConfig(),
    var smokeGrenade: SmokeGrenadeConfig = SmokeGrenadeConfig(),
    var glider: GliderConfig = GliderConfig()
) {

    data class LobbyConfig(
        var world: String = "world",
        var x: Double = 0.0,
        var y: Double = 64.0,
        var z: Double = 0.0,
        var yaw: Float = 0f,
        var pitch: Float = 0f,
        var teleportOnLeave: Boolean = true,
        var teleportOnEnd: Boolean = true
    )

    data class GameConfig(
        @Comment("Minimum players required to start a game")
        var minPlayers: Int = 2,
        @Comment("Maximum players per game instance")
        var maxPlayers: Int = 24,
        var countdownSeconds: Int = 30,
        var gracePeriodSeconds: Int = 60,
        var gameTimeMinutes: Int = 10,
        var deathmatchTimeMinutes: Int = 3,
        var teleportDelay: Int = 1,
        var allowSpectating: Boolean = true,
        var clearInventory: Boolean = true,
        var restoreInventory: Boolean = true,
        var saveLocation: Boolean = true,
        var defaultMode: String = "SOLO",
        var setupPeriodSeconds: Int = 120,
        @Comment("Allow players to reconnect during active game")
        var allowReconnect: Boolean = false,
        var teams: TeamsConfig = TeamsConfig()
    )

    data class TeamsConfig(
        var glowEffects: Boolean = true,
        var autoBalance: Boolean = true,
        var friendlyFire: Boolean = false,
        var invitationTimeout: Int = 60,
        var maxTeamSize: Int = 3
    )

    data class ArenaConfig(
        var defaultRadius: Int = 200,
        var defaultMaxPlayers: Int = 24,
        var defaultMinPlayers: Int = 1,
        var autoSave: Boolean = true,
        var saveDelaySeconds: Int = 3
    )

    data class WorldBorderConfig(
        var initialSize: Double = 500.0,
        var deathmatch: DeathmatchBorderConfig = DeathmatchBorderConfig()
    )

    data class DeathmatchBorderConfig(
        var enableShrinking: Boolean = true,
        var startSize: Double = 75.0,
        var endSize: Double = 10.0,
        var shrinkDurationSeconds: Long = 120L,
        var showWarnings: Boolean = true
    )

    data class ScoreboardConfig(
        var enabled: Boolean = true,
        var title: String = "<gold><bold>Survival Games</bold></gold>",
        var updateInterval: Int = 40,
        var lines: List<String> = listOf(
            "<gray><st>--------------------</st></gray>",
            "<gold>Players: <white><alive></white><gray>/</gray><white><total></white></gold>",
            "<gold>Time: <white><time></white></gold>",
            "<gray><st>--------------------</st></gray>"
        ),
        var deathmatchLines: List<String> = listOf(
            "<red><bold>DEATHMATCH</bold></red>"
        )
    )

    data class ChestConfig(
        var minItems: Int = 3,
        var maxItems: Int = 8,
        var refillChests: Boolean = true,
        var refillTime: Int = 300,
        @Comment("Whether chests refill after a delay")
        var refillEnabled: Boolean = true,
        @Comment("Seconds after game start before chests refill")
        var refillTimeSeconds: Int = 300,
        var distanceBasedLoot: Boolean = true
    )

    data class SpectatorConfig(
        var enabled: Boolean = true,
        var teleportToLobbyAfterGame: Boolean = true
    )

    data class RewardsConfig(
        var enabled: Boolean = true,
        var mobCoins: Int = 1000,
        var winCommand: String = "",
        var killCommand: String = "",
        var winnerAnnouncement: WinnerAnnouncementConfig = WinnerAnnouncementConfig()
    )

    data class WinnerAnnouncementConfig(
        var enabled: Boolean = true,
        var usePixelArt: Boolean = true,
        var title: String = "<gradient:gold:yellow><bold>WINNER!</bold></gradient>",
        var subtitle: String = "<bold><player></bold>",
        var message: String = "<green>The game has ended! <player> is the winner!",
        var teamMessage: String = "<green>Team <yellow><members> <green>is victorious!",
        var fireworks: Boolean = true,
        var fireworkCount: Int = 20,
        var pixelArt: PixelArtConfig = PixelArtConfig()
    )

    data class PixelArtConfig(
        var enabled: Boolean = true,
        var apiUrl: String = "https://crafatar.com/avatars/<uuid>?size=8&overlay",
        var size: Int = 8,
        var character: String = "\u2B1B",
        var cacheEnabled: Boolean = true,
        var cacheDurationMinutes: Int = 30,
        var preCacheEnabled: Boolean = true
    )

    data class MessagesSection(
        var prefix: String = "<dark_gray>[<gold>LumaSG</gold>] <reset>",
        var broadcastEvents: Boolean = true,
        var gameStart: String = "<green>The game has started! Good luck!",
        var gracePeriodStart: String = "<yellow>Grace period has started! PvP is disabled for <time> seconds.",
        var gracePeriodEnd: String = "<red>Grace period has ended! PvP is now enabled!",
        var playerJoin: String = "<gray><player> <yellow>has joined! <gray>(<current>/<max>)",
        var playerLeave: String = "<gray><player> <yellow>has left. <gray>(<current>/<max>)",
        var playerDeath: String = "<gray><player> <red>has been eliminated!",
        var playerKill: String = "<gray><player> <red>was eliminated by <gray><killer><red>!",
        var gameEnd: String = "<green>The game has ended! <player> wins!",
        var countdown: String = "<yellow>Game starting in <gray><time> <yellow>seconds!",
        @Comment("Death message templates. Placeholders: <victim>, <killer>, <weapon>")
        var deathByPlayer: String = "<red><victim> <gray>was killed by <red><killer> <gray>with <white><weapon>",
        var deathNatural: String = "<red><victim> died",
        @Comment("Kill notification sent to the killer. Placeholders: <victim>, <kills>")
        var killNotification: String = "<green>You killed <white><victim><green>! (<gold><kills> kills<green>)",
        var deathMessages: DeathMessagesConfig = DeathMessagesConfig(),
        var deathmatchReminders: DeathmatchReminderConfig = DeathmatchReminderConfig()
    )

    data class DeathMessagesConfig(
        var enabled: Boolean = true,
        var format: String = "<dark_red>\u2620 <red><victim> <gray>was <action> <gray>by <killer>! <yellow><remaining> players remain!",
        var finalTwoFormat: String = "<dark_red>\u2694 FINAL BATTLE \u2694\n<victim> vs <killer>",
        var winnerFormat: String = "<gold>\u2694 VICTORY \u2694\n<winner> is victorious!"
    )

    data class DeathmatchReminderConfig(
        var enabled: Boolean = true,
        @Comment("Seconds before deathmatch to show reminders")
        var reminderTimes: List<Int> = listOf(300, 180, 120, 60, 30, 10),
        var playSounds: Boolean = true
    )

    data class StatisticsConfig(
        var enabled: Boolean = true,
        var saveIntervalSeconds: Int = 300,
        var preloadOnJoin: Boolean = true,
        var trackDamage: Boolean = true,
        var trackChests: Boolean = true
    )

    data class DatabaseConfig(
        var type: String = "SQLITE",
        var sqliteFile: String = "lumasg.db",
        var host: String = "localhost",
        var port: Int = 3306,
        var database: String = "lumasg",
        var username: String = "root",
        var password: String = "password",
        var pool: PoolConfig = PoolConfig(),
        var useSsl: Boolean = false,
        var autoMigrate: Boolean = true
    )

    data class PoolConfig(
        var minimumIdle: Int = 2,
        var maximumPoolSize: Int = 8,
        var connectionTimeout: Long = 30000L,
        var idleTimeout: Long = 600000L,
        var maxLifetime: Long = 1800000L
    )

    data class DiscordConfig(
        var enabled: Boolean = false,
        var botToken: String = "",
        var guildId: String = "",
        var announcementsChannelId: String = "",
        var statsChannelId: String = ""
    )

    data class QueueConfig(
        @Comment("Whether to broadcast queue status to non-playing players")
        var broadcastsEnabled: Boolean = true,
        var broadcastInterval: Int = 30,
        var allowMute: Boolean = true
    )

    data class DebugConfig(
        var enabled: Boolean = false,
        var logLevel: String = "INFO"
    )

    data class AirstrikeConfig(
        var enabled: Boolean = true,
        var chargeTimeTicks: Int = 70,
        var maxRange: Int = 80,
        var cancelOnDamage: Boolean = false,
        var blastRadius: Int = 18,
        var meteorCount: Int = 5,
        var deathmatchMeteorCount: Int = 1,
        var meteorDelayTicks: Int = 30,
        var warningDurationTicks: Int = 60,
        var lockOnDurationTicks: Int = 10,
        var explosionRadius: Int = 4,
        var explosionDamage: Double = 6.0,
        var debrisCountMultiplier: Int = 5,
        var broadcastMessage: String = "<red><bold>⚠</bold></red> <yellow><player> called in an airstrike!</yellow> <red><bold>⚠</bold></red>",
        var warningActionbar: String = "<red>⚠ AIRSTRIKE INCOMING ⚠</red>",
        var lockActionbar: String = "<dark_red><bold>⚠ TARGET LOCKED ⚠</bold></dark_red>",
        var noTargetMessage: String = "<gray>No target in range</gray>"
    )

    data class SmokeGrenadeConfig(
        var enabled: Boolean = true,
        var radius: Int = 16,
        var durationTicks: Int = 200,
        var fadeDurationTicks: Int = 40,
        var particleTickRate: Int = 2,
        var visibilityCheckRate: Int = 4,
        var particleDensity: Int = 80,
        var ambientSoundInterval: Int = 20
    )

    data class GliderConfig(
        var enabled: Boolean = true,
        var maxDurationTicks: Int = 300,
        var minAirtimeTicks: Int = 10,
        var landingFallDamage: Boolean = false,
        var blockFireworkBoost: Boolean = true
    )
}
