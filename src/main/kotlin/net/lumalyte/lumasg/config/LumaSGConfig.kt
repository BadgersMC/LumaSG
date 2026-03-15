package net.lumalyte.lumasg.config

import kotlinx.serialization.Serializable
import net.badgersmc.nexus.config.Comment
import net.badgersmc.nexus.config.ConfigFile

@ConfigFile("config.yml")
@Serializable
data class LumaSGConfig(
    @Comment("Minimum players required to start a game")
    val minPlayers: Int = 2,
    @Comment("Maximum players per game instance")
    val maxPlayers: Int = 24,
    @Comment("Countdown duration in seconds before game starts")
    val countdownSeconds: Int = 30,
    @Comment("Grace period duration in seconds (no PvP)")
    val gracePeriodSeconds: Int = 60,
    @Comment("Maximum game duration in minutes before deathmatch is forced")
    val maxGameMinutes: Int = 10,
    val worldBorder: WorldBorderConfig = WorldBorderConfig(),
    val scoreboard: ScoreboardConfig = ScoreboardConfig(),
    val rewards: RewardsConfig = RewardsConfig(),
    val deathmatchReminders: DeathmatchReminderConfig = DeathmatchReminderConfig(),
    val messages: MessagesConfig = MessagesConfig(),
    val database: DatabaseConfig = DatabaseConfig(),
    val discord: DiscordConfig = DiscordConfig(),
    val lobby: LobbyConfig = LobbyConfig()
) {
    @Serializable
    data class ScoreboardConfig(
        val enabled: Boolean = true,
        val title: String = "<gold><bold>Survival Games</bold></gold>",
        @Comment("Lines displayed on the sidebar. Placeholders: <arena>, <alive>, <total>, <time>, <phase>")
        val lines: List<String> = listOf(
            "<gray><st>--------------------</st></gray>",
            "<gold>Arena: <white><arena></white></gold>",
            "<gold>Players: <white><alive></white><gray>/</gray><white><total></white></gold>",
            "<time>",
            "<gray><st>--------------------</st></gray>"
        )
    )

    @Serializable
    data class RewardsConfig(
        val enabled: Boolean = true,
        @Comment("Command executed for the winner. Placeholders: <player>, <kills>")
        val winCommand: String = "",
        val pixelArt: PixelArtConfig = PixelArtConfig()
    )

    @Serializable
    data class PixelArtConfig(
        val enabled: Boolean = true,
        @Comment("API URL to fetch player head. <uuid> is replaced with the player's UUID")
        val apiUrl: String = "https://crafatar.com/avatars/<uuid>?size=8&overlay",
        val size: Int = 8,
        val character: String = "\u2B1B"
    )

    @Serializable
    data class DeathmatchReminderConfig(
        val enabled: Boolean = true,
        @Comment("Seconds before deathmatch to show reminders")
        val reminderTimes: List<Int> = listOf(300, 180, 120, 60, 30, 10),
        val playSounds: Boolean = true
    )

    @Serializable
    data class MessagesConfig(
        @Comment("Death message templates. Placeholders: <victim>, <killer>, <weapon>")
        val deathByPlayer: String = "<red><victim> <gray>was killed by <red><killer> <gray>with <white><weapon>",
        val deathNatural: String = "<red><victim> died",
        @Comment("Kill notification sent to the killer. Placeholders: <victim>, <kills>")
        val killNotification: String = "<green>You killed <white><victim><green>! (<gold><kills> kills<green>)"
    )

    @Serializable
    data class WorldBorderConfig(
        val initialRadius: Double = 500.0,
        val finalRadius: Double = 10.0,
        val shrinkDurationSeconds: Long = 120L
    )

    @Serializable
    data class DatabaseConfig(
        val host: String = "localhost",
        val port: Int = 3306,
        val database: String = "lumasg",
        val username: String = "root",
        val password: String = "password",
        val poolSize: Int = 10
    )

    @Serializable
    data class DiscordConfig(
        val enabled: Boolean = false,
        val botToken: String = "",
        val guildId: String = "",
        val announcementsChannelId: String = "",
        val statsChannelId: String = ""
    )

    @Serializable
    data class LobbyConfig(
        val world: String = "world",
        val x: Double = 0.0,
        val y: Double = 64.0,
        val z: Double = 0.0,
        val yaw: Float = 0f,
        val pitch: Float = 0f
    )
}
