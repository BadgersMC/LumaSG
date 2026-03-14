package net.lumalyte.lumasg

import net.badgersmc.nexus.core.NexusContext
import net.badgersmc.nexus.paper.BukkitDispatcher
import net.badgersmc.nexus.paper.registerPaperCommands
import org.bukkit.plugin.java.JavaPlugin

class LumaSGPlugin : JavaPlugin() {

    lateinit var nexus: NexusContext
        private set

    lateinit var bukkitDispatcher: BukkitDispatcher
        private set

    override fun onEnable() {
        saveDefaultConfig()
        bukkitDispatcher = BukkitDispatcher(this)

        nexus = NexusContext.create(
            basePackage = "net.lumalyte.lumasg",
            classLoader = this::class.java.classLoader,
            contextName = "lumasg",
            externalBeans = mapOf("plugin" to this)
        )

        nexus.registerPaperCommands(
            basePackage = "net.lumalyte.lumasg",
            classLoader = this::class.java.classLoader,
            plugin = this
        )

        logger.info("LumaSG enabled — coroutine pipeline active.")
    }

    override fun onDisable() {
        nexus.close()
        logger.info("LumaSG disabled — all coroutines cancelled.")
    }
}
