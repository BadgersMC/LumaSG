package net.lumalyte.lumasg

import net.badgersmc.nexus.core.NexusContext
import net.badgersmc.nexus.paper.BukkitDispatcher
import net.badgersmc.nexus.paper.registerPaperCommands
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

        printStartupBanner()
    }

    override fun onDisable() {
        if (::nexus.isInitialized) {
            nexus.close()
        }
        logger.info("LumaSG disabled.")
    }

    @Suppress("LongMethod")
    private fun printStartupBanner() {
        val v = pluginMeta.version
        val jv = Runtime.version().toString()
        // §e = yellow (solid blocks), §6 = gold (box-drawing), §f = white, §c = red
        val E = "§e" // yellow — solid blocks
        val G = "§6" // gold   — corners / edges
        val W = "§f" // white  — label text
        val R = "§c" // red    — values
        val A = "§7" // gray   — author

        server.consoleSender.sendMessage(
            "\n" +
            "${E}██${G}╗     ${E}██${G}╗   ${E}██${G}╗${E}███${G}╗   ${E}███${G}╗ ${E}█████${G}╗ ${E}███████${G}╗ ${E}██████${G}╗ \n" +
            "${E}██${G}║     ${E}██${G}║   ${E}██${G}║${E}████${G}╗ ${E}████${G}║${E}██${G}╔══${E}██${G}╗${E}██${G}╔════╝${E}██${G}╔════╝ \n" +
            "${E}██${G}║     ${E}██${G}║   ${E}██${G}║${E}██${G}╔${E}████${G}╔${E}██${G}║${E}███████${G}║${E}███████${G}╗${E}██${G}║  ${E}███${G}╗${W}  Version: ${R}$v\n" +
            "${E}██${G}║     ${E}██${G}║   ${E}██${G}║${E}██${G}║╚${E}██${G}╔╝${E}██${G}║${E}██${G}╔══${E}██${G}║╚════${E}██${G}║${E}██${G}║   ${E}██${G}║${W}  By: ${R}B${G}a${R}d${G}g${R}e${G}r${R}s${G}M${R}C\n" +
            "${E}███████${G}╗╚${E}██████${G}╔╝${E}██${G}║ ${G}╚═╝ ${E}██${G}║${E}██${G}║  ${E}██${G}║${E}███████${G}║╚${E}██████${G}╔╝${W}  Java: ${R}$jv\n" +
            "${G}╚══════╝ ╚═════╝ ╚═╝     ╚═╝╚═╝  ╚═╝╚══════╝ ╚═════╝ \n"
        )
    }
}
