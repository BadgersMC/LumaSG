package net.lumalyte.lumasg.hooks

import me.clip.placeholderapi.expansion.PlaceholderExpansion
import net.badgersmc.nexus.annotations.PostConstruct
import net.badgersmc.nexus.annotations.Service
import net.lumalyte.lumasg.statistics.StatisticsService
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin

@Service
class PlaceholderAPIHook(
    private val plugin: Plugin,
    private val statsService: StatisticsService
) : PluginHook {
    override val pluginName = "PlaceholderAPI"

    @PostConstruct
    fun register() {
        if (!isAvailable()) return
        // Cache metadata outside the anonymous object to avoid PlaceholderExpansion.getDescription() shadowing Plugin.getDescription()
        val authorName = plugin.pluginMeta.authors.firstOrNull() ?: "LumaSG"
        val pluginVersion = plugin.pluginMeta.version
        object : PlaceholderExpansion() {
            override fun getIdentifier() = "lumasg"
            override fun getAuthor() = authorName
            override fun getVersion() = pluginVersion
            override fun persist() = true

            override fun onPlaceholderRequest(player: Player?, identifier: String): String? {
                if (player == null) return null
                return when (identifier) {
                    "kills" -> null // TODO: async — use cached value from statsService
                    "wins" -> null  // TODO: async — use cached value from statsService
                    "kdr" -> null   // TODO: async — use cached value from statsService
                    else -> null
                }
            }
        }.register()
    }
}
