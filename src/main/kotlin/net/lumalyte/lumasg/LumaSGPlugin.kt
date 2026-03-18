package net.lumalyte.lumasg

import com.mojang.brigadier.suggestion.SuggestionProvider
import io.papermc.paper.command.brigadier.CommandSourceStack
import net.badgersmc.nexus.core.NexusContext
import net.badgersmc.nexus.paper.BukkitDispatcher
import net.badgersmc.nexus.paper.registerPaperCommands
import net.lumalyte.lumasg.domain.LootMode
import net.lumalyte.lumasg.service.ArenaService
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

        val arenaService = nexus.getBean<ArenaService>()
        nexus.registerPaperCommands(
            basePackage = "net.lumalyte.lumasg",
            classLoader = this::class.java.classLoader,
            plugin = this,
            suggestionProviders = mapOf(
                "arenaNames" to SuggestionProvider<CommandSourceStack> { _, builder ->
                    arenaService.getAllArenas().forEach { builder.suggest(it.name) }
                    builder.buildFuture()
                },
                "lootModeNames" to SuggestionProvider<CommandSourceStack> { _, builder ->
                    LootMode.entries.forEach { builder.suggest(it.name.lowercase()) }
                    builder.buildFuture()
                },
                "dummyCounts" to SuggestionProvider<CommandSourceStack> { _, builder ->
                    listOf("1", "2", "4", "8", "16").forEach { builder.suggest(it) }
                    builder.buildFuture()
                }
            )
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
