package net.lumalyte.lumasg.util.config;  import net.lumalyte.lumasg.util.core.DebugLogger;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import net.lumalyte.lumasg.LumaSG;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * High-performance configuration loader with advanced Caffeine caching
 * Reduces file I/O and improves configuration access performance
 */
public class OptimizedConfigLoader {
    
    // Cache for parsed configuration values
    private static final LoadingCache<String, Object> CONFIG_VALUE_CACHE = Caffeine.newBuilder()
            .maximumSize(2000)
            .expireAfterWrite(Duration.ofMinutes(30))
            .expireAfterAccess(Duration.ofMinutes(10))
            .recordStats()
            .build(OptimizedConfigLoader::loadConfigValue);
    
    // Cache for entire configuration sections
    private static final Cache<String, ConfigurationSection> SECTION_CACHE = Caffeine.newBuilder()
            .maximumSize(100)
            .expireAfterWrite(Duration.ofMinutes(15))
            .recordStats()
            .build();
    
    // Cache for file configurations to avoid re-parsing
    private static final Cache<String, FileConfiguration> FILE_CONFIG_CACHE = Caffeine.newBuilder()
            .maximumSize(50)
            .expireAfterWrite(Duration.ofMinutes(60))
            .recordStats()
            .build();
    
    // Cache for computed lists (arena names, game modes, etc.)
    private static final Cache<String, List<String>> LIST_CACHE = Caffeine.newBuilder()
            .maximumSize(200)
            .expireAfterWrite(Duration.ofMinutes(20))
            .recordStats()
            .build();
    
    // Thread-safe map for tracking configuration file modifications
    private static final ConcurrentHashMap<String, Long> FILE_MODIFICATION_TIMES = new ConcurrentHashMap<>();
    
    private static LumaSG plugin;
    private static DebugLogger.ContextualLogger logger;
    
    /**
     * Initializes the optimized configuration loader
     */
    public static void initialize(LumaSG pluginInstance) {
        plugin = pluginInstance;
        logger = plugin.getDebugLogger().forContext("OptimizedConfigLoader");
        logger.info("Optimized Configuration Loader initialized");
    }
    
    /**
     * Gets a cached configuration value with type safety
     */
    @SuppressWarnings("unchecked")
    public static <T> T getCachedConfigValue(String configFile, String path, T defaultValue, Class<T> type) {
        String cacheKey = configFile + ":" + path;
        
        try {
            Object value = CONFIG_VALUE_CACHE.get(cacheKey);
            
            if (value == null) {
                return defaultValue;
            }
            
            if (type.isInstance(value)) {
                return (T) value;
            } else {
                logger.warn("Type mismatch for config value " + path);
                return defaultValue;
            }
        } catch (Exception e) {
            logger.warn("Failed to get cached config value for " + cacheKey, e);
            return defaultValue;
        }
    }
    
    /**
     * Gets a cached configuration section
     * 
     * @param configFile The configuration file name
     * @param sectionPath The section path
     * @return The configuration section, or null if not found
     */
    public static ConfigurationSection getCachedConfigSection(String configFile, String sectionPath) {
        String cacheKey = configFile + ":section:" + sectionPath;
        
        try {
            // Check if file has been modified
            if (hasFileBeenModified(configFile)) {
                invalidateFileCache(configFile);
            }
            
            return SECTION_CACHE.get(cacheKey, key -> {
                FileConfiguration config = getFileConfiguration(configFile);
                return config != null ? config.getConfigurationSection(sectionPath) : null;
            });
        } catch (Exception e) {
            logger.warn("Failed to get cached config section for " + cacheKey, e);
            return null;
        }
    }
    
    /**
     * Gets a cached list from configuration
     * 
     * @param configFile The configuration file name
     * @param path The configuration path
     * @param defaultValue The default list if not found
     * @return The list from configuration
     */
    public static List<String> getCachedConfigList(String configFile, String path, List<String> defaultValue) {
        String cacheKey = configFile + ":list:" + path;
        
        try {
            // Check if file has been modified
            if (hasFileBeenModified(configFile)) {
                invalidateFileCache(configFile);
            }
            
            return LIST_CACHE.get(cacheKey, key -> {
                FileConfiguration config = getFileConfiguration(configFile);
                if (config != null) {
                    List<String> list = config.getStringList(path);
                    return list.isEmpty() ? defaultValue : list;
                }
                return defaultValue;
            });
        } catch (Exception e) {
            logger.warn("Failed to get cached config list for " + cacheKey, e);
            return defaultValue;
        }
    }
    
    /**
     * Gets all keys in a configuration section with caching
     * 
     * @param configFile The configuration file name
     * @param sectionPath The section path
     * @return Set of keys in the section
     */
    public static Set<String> getCachedSectionKeys(String configFile, String sectionPath) {
        String cacheKey = configFile + ":keys:" + sectionPath;
        
        try {
            // Check if file has been modified
            if (hasFileBeenModified(configFile)) {
                invalidateFileCache(configFile);
            }
            
            @SuppressWarnings("unchecked")
            Set<String> keys = (Set<String>) CONFIG_VALUE_CACHE.get(cacheKey + ":keys", key -> {
                ConfigurationSection section = getCachedConfigSection(configFile, sectionPath);
                return section != null ? section.getKeys(false) : Set.of();
            });
            
            return keys;
        } catch (Exception e) {
            logger.warn("Failed to get cached section keys for " + cacheKey, e);
            return Set.of();
        }
    }
    
    /**
     * Invalidates cache for a specific file
     */
    public static void invalidateFileCache(String configFile) {
        // Remove all cached values for this file
        CONFIG_VALUE_CACHE.asMap().keySet().removeIf(key -> key.startsWith(configFile + ":"));
        SECTION_CACHE.asMap().keySet().removeIf(key -> key.startsWith(configFile + ":"));
        FILE_CONFIG_CACHE.invalidate(configFile);
        LIST_CACHE.asMap().keySet().removeIf(key -> key.startsWith(configFile + ":"));
        FILE_MODIFICATION_TIMES.remove(configFile);
        
        logger.debug("Invalidated cache for file: " + configFile);
    }
    
    /**
     * Invalidates all caches
     */
    public static void invalidateAllCaches() {
        CONFIG_VALUE_CACHE.invalidateAll();
        SECTION_CACHE.invalidateAll();
        FILE_CONFIG_CACHE.invalidateAll();
        LIST_CACHE.invalidateAll();
        FILE_MODIFICATION_TIMES.clear();
        logger.info("All configuration caches invalidated");
    }
    
    /**
     * Gets comprehensive cache statistics
     */
    public static String getCacheStats() {
        return String.format(
            "Config Loader Stats:\n" +
            "Values - Size: %d, Hit Rate: %.2f%%\n" +
            "Sections - Size: %d, Hit Rate: %.2f%%",
            CONFIG_VALUE_CACHE.estimatedSize(), CONFIG_VALUE_CACHE.stats().hitRate() * 100,
            SECTION_CACHE.estimatedSize(), SECTION_CACHE.stats().hitRate() * 100
        );
    }
    
    // Private helper methods
    
    private static Object loadConfigValue(String cacheKey) {
        try {
            String[] parts = cacheKey.split(":", 2);
            if (parts.length != 2) {
                return null;
            }
            
            String configFile = parts[0];
            String path = parts[1];
            
            File file = new File(plugin.getDataFolder(), configFile);
            if (!file.exists()) {
                return null;
            }
            
            FileConfiguration config = YamlConfiguration.loadConfiguration(file);
            return config.get(path);
        } catch (Exception e) {
            logger.warn("Failed to load config value for key: " + cacheKey, e);
            return null;
        }
    }
    
    private static FileConfiguration getFileConfiguration(String configFile) {
        return FILE_CONFIG_CACHE.get(configFile, fileName -> {
            try {
                File file = new File(plugin.getDataFolder(), fileName);
                if (!file.exists()) {
                    logger.warn("Configuration file not found: " + fileName);
                    return null;
                }
                
                FileConfiguration config = YamlConfiguration.loadConfiguration(file);
                updateFileModificationTime(fileName);
                return config;
            } catch (Exception e) {
                logger.error("Failed to load configuration file: " + fileName, e);
                return null;
            }
        });
    }
    
    private static boolean hasFileBeenModified(String configFile) {
        try {
            File file = new File(plugin.getDataFolder(), configFile);
            if (!file.exists()) {
                return false;
            }
            
            long currentModTime = file.lastModified();
            Long cachedModTime = FILE_MODIFICATION_TIMES.get(configFile);
            
            return cachedModTime == null || currentModTime > cachedModTime;
        } catch (Exception e) {
            logger.warn("Failed to check file modification time for: " + configFile, e);
            return true; // Assume modified to be safe
        }
    }
    
    private static void updateFileModificationTime(String configFile) {
        try {
            File file = new File(plugin.getDataFolder(), configFile);
            if (file.exists()) {
                FILE_MODIFICATION_TIMES.put(configFile, file.lastModified());
            }
        } catch (Exception e) {
            logger.warn("Failed to update file modification time for: " + configFile, e);
        }
    }
    
    /**
     * Preloads commonly accessed configuration values to warm the cache
     * 
     * @param configFile The configuration file to preload
     * @param commonPaths Array of commonly accessed configuration paths
     */
    public static void preloadConfiguration(String configFile, String... commonPaths) {
        logger.debug("Preloading configuration cache for: " + configFile);
        
        for (String path : commonPaths) {
            try {
                String cacheKey = configFile + ":" + path;
                CONFIG_VALUE_CACHE.get(cacheKey);
            } catch (Exception e) {
                logger.warn("Failed to preload config value: " + path + " from " + configFile, e);
            }
        }
        
        logger.debug("Configuration preloading completed for: " + configFile);
    }
} 
