package net.lumalyte.lumasg.util

import net.badgersmc.nexus.annotations.PostConstruct
import net.badgersmc.nexus.annotations.Service
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages configuration files with auto-update functionality.
 *
 * Ensures that configuration files are kept up to date with new options
 * while preserving existing user configurations. When new configuration options are
 * added to the default configuration files, they will be automatically added to the
 * user's configuration files without overwriting existing settings.
 */
@Service
class ConfigurationManager(
    private val plugin: JavaPlugin
) {
    private val logger = LoggerFactory.getLogger(ConfigurationManager::class.java)
    private val defaultConfigs = ConcurrentHashMap<String, FileConfiguration>()

    @PostConstruct
    fun init() {
        // config.yml is NOT loaded here — Nexus manages it via @ConfigFile("config")
        // which creates config.yaml. Avoid duplicate config files.
        loadDefaultConfig("chest.yml")
        loadDefaultConfig("custom-items.yml")
        loadDefaultConfig("fishing.yml")

        // Auto-update all config files with any new options from defaults
        updateAllConfigs()
    }

    /**
     * Loads a default configuration from the plugin's resources.
     */
    private fun loadDefaultConfig(filename: String) {
        try {
            plugin.getResource(filename)?.use { inputStream ->
                val defaultConfig = YamlConfiguration.loadConfiguration(InputStreamReader(inputStream))
                defaultConfigs[filename] = defaultConfig
                logger.debug("Loaded default configuration for {}", filename)
            }
        } catch (e: IOException) {
            logger.warn("Failed to load default configuration for {}", filename, e)
        }
    }

    /**
     * Updates a configuration file with any missing options from the default configuration.
     *
     * @return true if the file was updated, false otherwise
     */
    fun updateConfig(file: File, filename: String): Boolean {
        if (!file.exists()) {
            plugin.saveResource(filename, false)
            return true
        }

        val defaultConfig = defaultConfigs[filename]
        if (defaultConfig == null) {
            logger.warn("No default configuration found for {}", filename)
            return false
        }

        val currentConfig = YamlConfiguration.loadConfiguration(file)
        val updated = updateConfigSection(currentConfig, defaultConfig, "")

        if (updated) {
            try {
                currentConfig.save(file)
                logger.info("Updated configuration file: {}", filename)
            } catch (e: IOException) {
                logger.error("Failed to save updated configuration: {}", filename, e)
                return false
            }
        }

        return updated
    }

    /**
     * Updates all registered configuration files with any missing options.
     */
    fun updateAllConfigs() {
        val dataFolder = plugin.dataFolder
        if (!dataFolder.exists()) {
            dataFolder.mkdirs()
        }

        for (filename in defaultConfigs.keys) {
            val configFile = File(dataFolder, filename)
            if (updateConfig(configFile, filename)) {
                logger.info("Updated {} with new configuration options", filename)
            }
        }
    }

    /**
     * Recursively updates a configuration section with missing options from defaults.
     *
     * @return true if any updates were made
     */
    private fun updateConfigSection(
        current: ConfigurationSection,
        defaults: ConfigurationSection,
        path: String
    ): Boolean {
        var updated = false

        for (key in defaults.getKeys(false)) {
            val fullPath = if (path.isEmpty()) key else "$path.$key"

            if (!current.contains(key)) {
                current.set(key, defaults.get(key))
                logger.debug("Added missing configuration option: {}", fullPath)
                updated = true
                continue
            }

            // Recursively check nested sections
            if (defaults.isConfigurationSection(key)) {
                val defaultSection = defaults.getConfigurationSection(key)
                val currentSection = current.getConfigurationSection(key)

                if (defaultSection != null && currentSection != null) {
                    if (updateConfigSection(currentSection, defaultSection, fullPath)) {
                        updated = true
                    }
                }
            }
        }

        return updated
    }
}
