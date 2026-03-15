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
    val database: DatabaseConfig = DatabaseConfig(),
    val discord: DiscordConfig = DiscordConfig(),
    val lobby: LobbyConfig = LobbyConfig()
) {
    @Serializable
    data class ScoreboardConfig(
        val enabled: Boolean = true,
        val title: String = "<gold><bold>Survival Games</bold></gold>"
    )

    @Serializable
    data class RewardsConfig(
        val enabled: Boolean = true,
        @Comment("Command executed for the winner. Placeholders: <player>, <kills>")
        val winCommand: String = ""
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
