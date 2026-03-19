package net.lumalyte.lumasg.hooks

import com.nexomc.nexo.api.NexoItems
import net.badgersmc.nexus.annotations.PostConstruct
import net.badgersmc.nexus.annotations.Service
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin

@Service
class NexoHook(private val plugin: JavaPlugin) : PluginHook {
    override val pluginName = "Nexo"

    private var nexoAvailable = false

    @PostConstruct
    fun register() {
        if (!isAvailable()) {
            plugin.logger.info("Nexo not found — custom item compatibility disabled.")
            return
        }
        nexoAvailable = true
        plugin.logger.info("Nexo integration enabled.")
    }

    /**
     * Get a Nexo item by its ID.
     * Returns null if Nexo is not available or the item doesn't exist.
     */
    fun getNexoItem(id: String): ItemStack? {
        if (!nexoAvailable) return null
        return try {
            NexoItems.itemFromId(id)?.build()
        } catch (_: Exception) {
            null
        }
    }

    /** Check whether a Nexo item ID exists. */
    fun exists(id: String): Boolean {
        if (!nexoAvailable) return false
        return try {
            NexoItems.exists(id)
        } catch (_: Exception) {
            false
        }
    }
}
