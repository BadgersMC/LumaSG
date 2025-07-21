package net.lumalyte.lumasg.util.config;  import net.lumalyte.lumasg.util.core.DebugLogger;

import net.lumalyte.lumasg.LumaSG;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Manages configuration files with auto-update functionality.
 * 
 * <p>This class ensures that configuration files are kept up to date with new options
 * while preserving existing user configurations. When new configuration options are
 * added to the default configuration files, they will be automatically added to the
 * user's configuration files without overwriting existing settings.</p>
 */
public class ConfigurationManager {
    private final @NotNull LumaSG plugin;
    private final @NotNull DebugLogger.ContextualLogger logger;
    private final @NotNull Map<String, FileConfiguration> defaultConfigs;
    
    public ConfigurationManager(@NotNull LumaSG plugin) {
        this.plugin = plugin;
        this.logger = plugin.getDebugLogger().forContext("ConfigurationManager");
        this.defaultConfigs = new ConcurrentHashMap<>();
        
        // Load default configurations from resources
        loadDefaultConfigs("config.yml");
        loadDefaultConfigs("chest.yml");
        loadDefaultConfigs("custom-items.yml");
        loadDefaultConfigs("fishing.yml");
    }
    
    /**
     * Loads a default configuration from the plugin's resources.
     * 
     * @param filename The name of the configuration file
     */
    private void loadDefaultConfigs(@NotNull String filename) {
        try (InputStream inputStream = plugin.getResource(filename)) {
            if (inputStream != null) {
                YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(
                    new java.io.InputStreamReader(inputStream));
                defaultConfigs.put(filename, defaultConfig);
                logger.debug("Loaded default configuration for " + filename);
            }
        } catch (IOException e) {
            logger.warn("Failed to load default configuration for " + filename, e);
        }
    }
    
    /**
     * Updates a configuration file with any missing options from the default configuration.
     * 
     * @param file The configuration file to update
     * @param filename The name of the configuration file (for loading defaults)
     * @return true if the file was updated, false otherwise
     */
    public boolean updateConfig(@NotNull File file, @NotNull String filename) {
        if (!file.exists()) {
            plugin.saveResource(filename, false);
            return true;
        }
        
        FileConfiguration defaultConfig = defaultConfigs.get(filename);
        if (defaultConfig == null) {
            logger.warn("No default configuration found for " + filename);
            return false;
        }
        
        FileConfiguration currentConfig = YamlConfiguration.loadConfiguration(file);
        boolean updated = false;
        
        // Recursively update configuration sections
        updated = updateConfigSection(currentConfig, defaultConfig, "");
        
        // Save if changes were made
        if (updated) {
            try {
                currentConfig.save(file);
                logger.info("Updated configuration file: " + filename);
            } catch (IOException e) {
                logger.severe("Failed to save updated configuration: " + filename, e);
                return false;
            }
        }
        
        return updated;
    }
    
    /**
     * Recursively updates a configuration section with missing options.
     * 
     * @param current The current configuration section
     * @param defaults The default configuration section
     * @param path The current path in the configuration
     * @return true if any updates were made, false otherwise
     */
    private boolean updateConfigSection(@NotNull ConfigurationSection current,
                                      @NotNull ConfigurationSection defaults,
                                      @NotNull String path) {
        boolean updated = false;
        
        for (String key : defaults.getKeys(false)) {
            String fullPath = path.isEmpty() ? key : path + "." + key;
            
            if (!current.contains(key)) {
                // Add missing option
                current.set(key, defaults.get(key));
                logger.debug("Added missing configuration option: " + fullPath);
                updated = true;
                continue;
            }
            
            // Recursively check nested sections
            if (defaults.isConfigurationSection(key)) {
                ConfigurationSection defaultSection = defaults.getConfigurationSection(key);
                ConfigurationSection currentSection = current.getConfigurationSection(key);
                
                if (defaultSection != null && currentSection != null) {
                    if (updateConfigSection(currentSection, defaultSection, fullPath)) {
                        updated = true;
                    }
                }
            }
        }
        
        return updated;
    }
    
    /**
     * Updates all configuration files with any missing options.
     */
    public void updateAllConfigs() {
        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
        
        for (String filename : defaultConfigs.keySet()) {
            File configFile = new File(dataFolder, filename);
            if (updateConfig(configFile, filename)) {
                logger.info("Updated " + filename + " with new configuration options");
            }
        }
    }
} 
