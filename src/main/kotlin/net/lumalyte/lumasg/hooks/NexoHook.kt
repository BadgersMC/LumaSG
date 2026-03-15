package net.lumalyte.lumasg.hooks

import net.badgersmc.nexus.annotations.PostConstruct
import net.badgersmc.nexus.annotations.Service
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.Plugin

@Service
class NexoHook(private val plugin: Plugin) : PluginHook {
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
            // Use reflection to access Nexo API without compile-time dependency
            val nexoItemsClass = Class.forName("com.nexomc.nexo.api.NexoItems")
            val itemByIdMethod = nexoItemsClass.getMethod("itemFromId", String::class.java)
            val builder = itemByIdMethod.invoke(null, id) ?: return null
            val buildMethod = builder.javaClass.getMethod("build")
            buildMethod.invoke(builder) as? ItemStack
        } catch (_: Exception) {
            null
        }
    }
}
