package net.lumalyte.lumasg.hooks

import net.badgersmc.nexus.annotations.PostConstruct
import net.badgersmc.nexus.annotations.Service
import org.bukkit.plugin.Plugin

@Service
class KingdomsXHook(private val plugin: Plugin) : PluginHook {
    override val pluginName = "Kingdoms"

    @PostConstruct
    fun register() {
        if (!isAvailable()) {
            plugin.logger.info("KingdomsX not found — kingdom integration disabled.")
            return
        }
        plugin.logger.info("KingdomsX integration enabled.")
        // TODO: disable PvP protections during active games
    }
}
