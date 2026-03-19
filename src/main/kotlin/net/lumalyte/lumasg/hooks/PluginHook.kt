package net.lumalyte.lumasg.hooks

interface PluginHook {
    val pluginName: String
    fun isAvailable(): Boolean = org.bukkit.Bukkit.getPluginManager().isPluginEnabled(pluginName)
}
