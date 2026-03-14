package net.lumalyte.lumasg.discord

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import net.badgersmc.nexus.annotations.PostConstruct
import net.badgersmc.nexus.annotations.PreDestroy
import net.badgersmc.nexus.annotations.Service
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.requests.GatewayIntent
import net.lumalyte.lumasg.config.LumaSGConfig
import org.slf4j.LoggerFactory

@Service
class DiscordService(
    private val config: LumaSGConfig,
    private val nexusScope: CoroutineScope
) {
    private val logger = LoggerFactory.getLogger(DiscordService::class.java)
    private var jda: JDA? = null

    @PostConstruct
    fun init() {
        if (!config.discord.enabled) {
            logger.info("Discord integration disabled.")
            return
        }
        try {
            jda = JDABuilder.createLight(config.discord.botToken, GatewayIntent.GUILD_MESSAGES)
                .build()
                .awaitReady()
            logger.info("Discord bot connected.")
        } catch (e: Exception) {
            logger.warn("Failed to connect Discord bot: ${e.message}")
        }
    }

    @PreDestroy
    fun shutdown() {
        jda?.shutdown()
    }

    /** Announce a game event to the configured announcements channel. Fire-and-forget. */
    fun announce(embed: MessageEmbed) {
        val channelId = config.discord.announcementsChannelId
        if (channelId.isBlank()) return
        val channel = jda?.getTextChannelById(channelId) ?: return
        nexusScope.launch {
            try {
                channel.sendMessageEmbeds(embed).queue()
            } catch (e: Exception) {
                logger.warn("Failed to send Discord announcement: ${e.message}")
            }
        }
    }

    /** Post to the stats channel. Fire-and-forget. */
    fun postStats(embed: MessageEmbed) {
        val channelId = config.discord.statsChannelId
        if (channelId.isBlank()) return
        val channel = jda?.getTextChannelById(channelId) ?: return
        nexusScope.launch {
            try {
                channel.sendMessageEmbeds(embed).queue()
            } catch (e: Exception) {
                logger.warn("Failed to send Discord stats message: ${e.message}")
            }
        }
    }
}
