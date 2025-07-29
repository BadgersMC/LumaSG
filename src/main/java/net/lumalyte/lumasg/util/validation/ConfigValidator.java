package net.lumalyte.lumasg.util.validation;

import net.lumalyte.lumasg.LumaSG;
import net.lumalyte.lumasg.util.core.DebugLogger;
import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Comprehensive configuration validation system for LumaSG.
 * 
 * This validator ensures all configuration values are within safe bounds
 * and provides meaningful error messages for invalid configurations.
 * It prevents the plugin from starting with dangerous or invalid settings
 * that could cause performance issues or crashes.
 * 
 * @author LumaSG Team
 * @version 1.0
 * @since 1.0
 */
public class ConfigValidator {

    private final @NotNull LumaSG plugin;
    private final @NotNull DebugLogger.ContextualLogger logger;
    private final @NotNull List<String> validationErrors = new ArrayList<>();
    private final @NotNull List<String> validationWarnings = new ArrayList<>();

    public ConfigValidator(@NotNull LumaSG plugin) {
        this.plugin = plugin;
        this.logger = plugin.getDebugLogger().forContext("ConfigValidator");
    }

    /**
     * Validates the entire plugin configuration.
     * 
     * @return true if configuration is valid, false if critical errors exist
     */
    public boolean validateConfiguration() {
        validationErrors.clear();
        validationWarnings.clear();

        FileConfiguration config = plugin.getConfig();

        logger.info("Starting comprehensive configuration validation...");

        // Validate each configuration section
        validateGameSettings(config);
        validateArenaSettings(config);
        validateWorldBorderSettings(config);
        validateChestSettings(config);
        validateDatabaseSettings(config);
        validatePerformanceSettings(config);
        validateStatisticsSettings(config);
        validateRewardSettings(config);

        // Report results
        reportValidationResults();

        return validationErrors.isEmpty();
    }

    /**
     * Validates game-related settings.
     */
    private void validateGameSettings(@NotNull FileConfiguration config) {
        // Player count validation
        validateIntRange(config, "game.min-players", 1, 100, 2);
        validateIntRange(config, "game.max-players", 2, 100, 24);

        // Ensure max-players >= min-players
        int minPlayers = config.getInt("game.min-players", 2);
        int maxPlayers = config.getInt("game.max-players", 24);
        if (maxPlayers < minPlayers) {
            addError("game.max-players (" + maxPlayers + ") must be >= game.min-players (" + minPlayers + ")");
        }

        // Time validation (in seconds)
        validateIntRange(config, "game.countdown-seconds", 5, 300, 30);
        validateIntRange(config, "game.grace-period-seconds", 0, 600, 30);
        validateIntRange(config, "game.setup-period-seconds", 30, 600, 120);

        // Time validation (in minutes)
        validateIntRange(config, "game.game-time-minutes", 1, 120, 10);
        validateIntRange(config, "game.deathmatch-time-minutes", 1, 30, 3);

        // Teleport delay validation
        validateIntRange(config, "game.teleport-delay", 0, 10, 1);

        // Team settings validation
        validateIntRange(config, "game.teams.invitation-timeout", 10, 300, 60);
        validateIntRange(config, "game.teams.max-team-size", 2, 10, 3);

        // Game mode validation
        String gameMode = config.getString("game.default-mode", "SOLO");
        Set<String> validModes = Set.of("SOLO", "TEAMS", "DUOS");
        if (!validModes.contains(gameMode.toUpperCase())) {
            addError("game.default-mode must be one of: " + validModes + ", got: " + gameMode);
        }
    }

    /**
     * Validates arena-related settings.
     */
    private void validateArenaSettings(@NotNull FileConfiguration config) {
        validateIntRange(config, "arena.default-radius", 50, 2000, 200);
        validateIntRange(config, "arena.default-max-players", 2, 100, 24);
        validateIntRange(config, "arena.default-min-players", 1, 50, 2);
        validateIntRange(config, "arena.save-delay-seconds", 1, 60, 3);

        // Ensure arena max >= min players
        int arenaMin = config.getInt("arena.default-min-players", 2);
        int arenaMax = config.getInt("arena.default-max-players", 24);
        if (arenaMax < arenaMin) {
            addError("arena.default-max-players (" + arenaMax + ") must be >= arena.default-min-players (" + arenaMin
                    + ")");
        }
    }

    /**
     * Validates world border settings.
     */
    private void validateWorldBorderSettings(@NotNull FileConfiguration config) {
        validateDoubleRange(config, "world-border.initial-size", 50.0, 5000.0, 500.0);
        validateDoubleRange(config, "world-border.deathmatch.start-size", 10.0, 500.0, 75.0);
        validateDoubleRange(config, "world-border.deathmatch.end-size", 5.0, 100.0, 10.0);
        validateIntRange(config, "world-border.deathmatch.shrink-duration-seconds", 30, 600, 120);

        // Ensure border sizes make sense
        double startSize = config.getDouble("world-border.deathmatch.start-size", 75.0);
        double endSize = config.getDouble("world-border.deathmatch.end-size", 10.0);
        if (endSize >= startSize) {
            addError("world-border.deathmatch.end-size (" + endSize + ") must be < start-size (" + startSize + ")");
        }
    }

    /**
     * Validates chest-related settings.
     */
    private void validateChestSettings(@NotNull FileConfiguration config) {
        validateIntRange(config, "chest.min-items", 1, 27, 3);
        validateIntRange(config, "chest.max-items", 1, 27, 8);
        validateIntRange(config, "chest.refill-time", 60, 3600, 300);

        // Ensure max-items >= min-items
        int minItems = config.getInt("chest.min-items", 3);
        int maxItems = config.getInt("chest.max-items", 8);
        if (maxItems < minItems) {
            addError("chest.max-items (" + maxItems + ") must be >= chest.min-items (" + minItems + ")");
        }

        // Validate tier chances (should add up to 100 for each tier)
        validateTierChances(config, "chest.tier-chances.center");
        validateTierChances(config, "chest.tier-chances.middle");
        validateTierChances(config, "chest.tier-chances.outer");
    }

    /**
     * Validates database settings.
     */
    private void validateDatabaseSettings(@NotNull FileConfiguration config) {
        // Database type validation
        String dbType = config.getString("database.type", "POSTGRESQL");
        Set<String> validTypes = Set.of("POSTGRESQL", "MYSQL", "SQLITE");
        if (!validTypes.contains(dbType.toUpperCase())) {
            addError("database.type must be one of: " + validTypes + ", got: " + dbType);
        }

        // Port validation
        validateIntRange(config, "database.port", 1, 65535, 5432);

        // Connection pool validation
        validateIntRange(config, "database.pool.minimum-idle", 1, 50, 2);
        validateIntRange(config, "database.pool.maximum-pool-size", 2, 100, 8);
        validateIntRange(config, "database.pool.connection-timeout", 5000, 120000, 30000);
        validateIntRange(config, "database.pool.idle-timeout", 60000, 3600000, 600000);
        validateIntRange(config, "database.pool.max-lifetime", 300000, 7200000, 1800000);

        // Ensure pool settings make sense
        int minIdle = config.getInt("database.pool.minimum-idle", 2);
        int maxPool = config.getInt("database.pool.maximum-pool-size", 8);
        if (minIdle > maxPool) {
            addError("database.pool.minimum-idle (" + minIdle + ") must be <= maximum-pool-size (" + maxPool + ")");
        }

        // Required string fields
        validateNonEmptyString(config, "database.host", "localhost");
        validateNonEmptyString(config, "database.database", "lumasg");
        validateNonEmptyString(config, "database.username", "lumasg");

        // Warn about default password
        String password = config.getString("database.password", "");
        if (password.contains("changeme") || password.contains("DB_PASSWORD") || password.isEmpty()) {
            addWarning("database.password appears to be using default/empty value - ensure you set a secure password");
        }
    }

    /**
     * Validates performance settings.
     */
    private void validatePerformanceSettings(@NotNull FileConfiguration config) {
        validateIntRange(config, "performance.chest-filling.thread-pool-size", 0, 32, 0);
        validateIntRange(config, "performance.chest-filling.min-threads", 1, 16, 2);
        validateIntRange(config, "performance.chest-filling.max-threads", 2, 64, 16);
        validateDoubleRange(config, "performance.chest-filling.target-cpu-utilization", 0.1, 1.0, 0.75);
        validateDoubleRange(config, "performance.chest-filling.blocking-coefficient", 1.0, 10.0, 4.0);

        // Ensure thread limits make sense
        int minThreads = config.getInt("performance.chest-filling.min-threads", 2);
        int maxThreads = config.getInt("performance.chest-filling.max-threads", 16);
        if (minThreads > maxThreads) {
            addError("performance.chest-filling.min-threads (" + minThreads + ") must be <= max-threads (" + maxThreads
                    + ")");
        }
    }

    /**
     * Validates statistics settings.
     */
    private void validateStatisticsSettings(@NotNull FileConfiguration config) {
        validateIntRange(config, "statistics.save-interval-seconds", 60, 3600, 300);
    }

    /**
     * Validates reward settings.
     */
    private void validateRewardSettings(@NotNull FileConfiguration config) {
        validateIntRange(config, "rewards.mob-coins", 0, 1000000, 1000);
        validateIntRange(config, "rewards.firework-count", 1, 100, 20);
        validateIntRange(config, "rewards.pixel-art.size", 4, 16, 8);
        validateIntRange(config, "rewards.pixel-art.cache-duration-minutes", 1, 1440, 30);
    }

    // Helper validation methods

    private void validateIntRange(@NotNull FileConfiguration config, @NotNull String path,
            int min, int max, int defaultValue) {
        int value = config.getInt(path, defaultValue);
        if (value < min || value > max) {
            addError(path + " must be between " + min + " and " + max + ", got: " + value);
        }
    }

    private void validateDoubleRange(@NotNull FileConfiguration config, @NotNull String path,
            double min, double max, double defaultValue) {
        double value = config.getDouble(path, defaultValue);
        if (value < min || value > max) {
            addError(path + " must be between " + min + " and " + max + ", got: " + value);
        }
    }

    private void validateNonEmptyString(@NotNull FileConfiguration config, @NotNull String path,
            @NotNull String defaultValue) {
        String value = config.getString(path, defaultValue);
        if (value == null || value.trim().isEmpty()) {
            addError(path + " cannot be null or empty");
        }
    }

    private void validateTierChances(@NotNull FileConfiguration config, @NotNull String basePath) {
        int common = config.getInt(basePath + ".common", 0);
        int uncommon = config.getInt(basePath + ".uncommon", 0);
        int rare = config.getInt(basePath + ".rare", 0);

        validateIntRange(config, basePath + ".common", 0, 100, 0);
        validateIntRange(config, basePath + ".uncommon", 0, 100, 0);
        validateIntRange(config, basePath + ".rare", 0, 100, 0);

        int total = common + uncommon + rare;
        if (total != 100) {
            addWarning(basePath + " tier chances should add up to 100, got: " + total +
                    " (common: " + common + ", uncommon: " + uncommon + ", rare: " + rare + ")");
        }
    }

    private void addError(@NotNull String message) {
        validationErrors.add(message);
    }

    private void addWarning(@NotNull String message) {
        validationWarnings.add(message);
    }

    private void reportValidationResults() {
        if (!validationWarnings.isEmpty()) {
            logger.warn("Configuration validation found " + validationWarnings.size() + " warnings:");
            for (String warning : validationWarnings) {
                logger.warn("  ⚠ " + warning);
            }
        }

        if (!validationErrors.isEmpty()) {
            logger.severe("Configuration validation found " + validationErrors.size() + " critical errors:");
            for (String error : validationErrors) {
                logger.severe("  ✗ " + error);
            }
            logger.severe("Plugin cannot start with invalid configuration!");
        } else {
            logger.info("✓ Configuration validation passed successfully");
            if (!validationWarnings.isEmpty()) {
                logger.info("  (with " + validationWarnings.size() + " warnings)");
            }
        }
    }

    /**
     * Gets all validation errors.
     * 
     * @return List of validation error messages
     */
    public @NotNull List<String> getValidationErrors() {
        return new ArrayList<>(validationErrors);
    }

    /**
     * Gets all validation warnings.
     * 
     * @return List of validation warning messages
     */
    public @NotNull List<String> getValidationWarnings() {
        return new ArrayList<>(validationWarnings);
    }
}