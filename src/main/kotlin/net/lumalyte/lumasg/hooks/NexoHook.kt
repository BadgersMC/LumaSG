package net.lumalyte.lumasg.hooks

import net.badgersmc.nexus.annotations.PostConstruct
import net.badgersmc.nexus.annotations.Service
import org.bukkit.plugin.Plugin

@Service
class NexoHook(private val plugin: Plugin) : PluginHook {
    override val pluginName = "Nexo"

    @PostConstruct
    fun register() {
        if (!isAvailable()) {
            plugin.logger.info("Nexo not found — custom item compatibility disabled.")
            return
        }
        plugin.logger.info("Nexo integration enabled.")
        // TODO: register custom items with Nexo
    }
}
