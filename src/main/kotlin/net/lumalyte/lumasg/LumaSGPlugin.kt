package net.lumalyte.lumasg

import net.badgersmc.nexus.core.NexusContext
import net.badgersmc.nexus.paper.BukkitDispatcher
import net.badgersmc.nexus.paper.registerPaperCommands
import net.lumalyte.lumasg.util.SplashScreen
import org.bukkit.plugin.java.JavaPlugin
import xyz.xenondevs.invui.InvUI

class LumaSGPlugin : JavaPlugin() {

    lateinit var nexus: NexusContext
        private set

    lateinit var bukkitDispatcher: BukkitDispatcher
        private set

    override fun onEnable() {
        InvUI.getInstance().setPlugin(this)
        bukkitDispatcher = BukkitDispatcher(this)

        nexus = NexusContext.create(
            basePackage = "net.lumalyte.lumasg",
            classLoader = this::class.java.classLoader,
            configDirectory = dataFolder.toPath(),
            contextName = "lumasg",
            externalBeans = mapOf(
                "plugin" to this,
                "bukkitDispatcher" to bukkitDispatcher
            )
        )

        nexus.registerPaperCommands(
            basePackage = "net.lumalyte.lumasg",
            classLoader = this::class.java.classLoader,
            plugin = this
        )

        SplashScreen.print(this)
    }

    override fun onDisable() {
        if (::nexus.isInitialized) {
            nexus.close()
        }
        logger.info("LumaSG disabled.")
    }
}
