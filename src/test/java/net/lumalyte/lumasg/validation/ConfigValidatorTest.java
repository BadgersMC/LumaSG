package net.lumalyte.lumasg.validation;

import net.lumalyte.lumasg.LumaSG;
import net.lumalyte.lumasg.util.validation.ConfigValidator;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for the ConfigValidator class.
 * 
 * These tests ensure that configuration validation catches invalid values
 * and prevents the plugin from starting with dangerous settings.
 */
class ConfigValidatorTest {

    @Mock
    private LumaSG plugin;

    private ConfigValidator validator;
    private YamlConfiguration testConfig;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        // Create a valid test configuration
        testConfig = new YamlConfiguration();
        setupValidConfiguration();

        // Mock the debug logger system
        var debugLogger = mock(net.lumalyte.lumasg.util.core.DebugLogger.class);
        var contextualLogger = mock(net.lumalyte.lumasg.util.core.DebugLogger.ContextualLogger.class);
        when(plugin.getDebugLogger()).thenReturn(debugLogger);
        when(debugLogger.forContext(anyString())).thenReturn(contextualLogger);

        // Mock the plugin to return our test configuration
        when(plugin.getConfig()).thenReturn(testConfig);

        validator = new ConfigValidator(plugin);
    }

    /**
     * Sets up a valid configuration for testing.
     */
    private void setupValidConfiguration() {
        // Game settings
        testConfig.set("game.min-players", 2);
        testConfig.set("game.max-players", 24);
        testConfig.set("game.countdown-seconds", 30);
        testConfig.set("game.grace-period-seconds", 30);
        testConfig.set("game.game-time-minutes", 10);
        testConfig.set("game.deathmatch-time-minutes", 3);
        testConfig.set("game.teleport-delay", 1);
        testConfig.set("game.default-mode", "SOLO");
        testConfig.set("game.setup-period-seconds", 120);
        testConfig.set("game.teams.invitation-timeout", 60);
        testConfig.set("game.teams.max-team-size", 3);

        // Arena settings
        testConfig.set("arena.default-radius", 200);
        testConfig.set("arena.default-max-players", 24);
        testConfig.set("arena.default-min-players", 2);
        testConfig.set("arena.save-delay-seconds", 3);

        // World border settings
        testConfig.set("world-border.initial-size", 500.0);
        testConfig.set("world-border.deathmatch.start-size", 75.0);
        testConfig.set("world-border.deathmatch.end-size", 10.0);
        testConfig.set("world-border.deathmatch.shrink-duration-seconds", 120);

        // Chest settings
        testConfig.set("chest.min-items", 3);
        testConfig.set("chest.max-items", 8);
        testConfig.set("chest.refill-time", 300);
        testConfig.set("chest.tier-chances.center.common", 20);
        testConfig.set("chest.tier-chances.center.uncommon", 50);
        testConfig.set("chest.tier-chances.center.rare", 30);

        // Database settings
        testConfig.set("database.type", "POSTGRESQL");
        testConfig.set("database.port", 5432);
        testConfig.set("database.host", "localhost");
        testConfig.set("database.database", "lumasg");
        testConfig.set("database.username", "lumasg");
        testConfig.set("database.password", "secure_password");
        testConfig.set("database.pool.minimum-idle", 2);
        testConfig.set("database.pool.maximum-pool-size", 8);
        testConfig.set("database.pool.connection-timeout", 30000);
        testConfig.set("database.pool.idle-timeout", 600000);
        testConfig.set("database.pool.max-lifetime", 1800000);

        // Performance settings
        testConfig.set("performance.chest-filling.thread-pool-size", 0);
        testConfig.set("performance.chest-filling.min-threads", 2);
        testConfig.set("performance.chest-filling.max-threads", 16);
        testConfig.set("performance.chest-filling.target-cpu-utilization", 0.75);
        testConfig.set("performance.chest-filling.blocking-coefficient", 4.0);

        // Statistics settings
        testConfig.set("statistics.save-interval-seconds", 300);

        // Reward settings
        testConfig.set("rewards.mob-coins", 1000);
        testConfig.set("rewards.firework-count", 20);
        testConfig.set("rewards.pixel-art.size", 8);
        testConfig.set("rewards.pixel-art.cache-duration-minutes", 30);
    }

    @Test
    void testValidConfigurationPasses() {
        boolean result = validator.validateConfiguration();

        assertTrue(result, "Valid configuration should pass validation");
        assertTrue(validator.getValidationErrors().isEmpty(), "Should have no validation errors");
    }

    @Test
    void testInvalidPlayerCountsFail() {
        // Set invalid player counts
        testConfig.set("game.min-players", 0); // Too low
        testConfig.set("game.max-players", 101); // Too high

        boolean result = validator.validateConfiguration();

        assertFalse(result, "Invalid player counts should fail validation");
        assertFalse(validator.getValidationErrors().isEmpty(), "Should have validation errors");

        // Check that specific errors are present
        assertTrue(validator.getValidationErrors().stream()
                .anyMatch(error -> error.contains("game.min-players")),
                "Should have min-players error");

        assertTrue(validator.getValidationErrors().stream()
                .anyMatch(error -> error.contains("game.max-players")),
                "Should have max-players error");
    }

    @Test
    void testInvalidDatabaseTypeFails() {
        // Set invalid database type
        testConfig.set("database.type", "INVALID_DB");

        boolean result = validator.validateConfiguration();

        assertFalse(result, "Invalid database type should fail validation");
        assertTrue(validator.getValidationErrors().stream()
                .anyMatch(error -> error.contains("database.type") && error.contains("INVALID_DB")),
                "Should have database type error");
    }

    @Test
    void testInvalidWorldBorderSizesFail() {
        // Set invalid border sizes (end size >= start size)
        testConfig.set("world-border.deathmatch.start-size", 50.0);
        testConfig.set("world-border.deathmatch.end-size", 75.0); // Larger than start

        boolean result = validator.validateConfiguration();

        assertFalse(result, "Invalid border sizes should fail validation");
        assertTrue(validator.getValidationErrors().stream()
                .anyMatch(error -> error.contains("end-size") && error.contains("must be < start-size")),
                "Should have border size consistency error");
    }

    @Test
    void testDefaultPasswordWarning() {
        // Set default password
        testConfig.set("database.password", "your_password_here");

        boolean result = validator.validateConfiguration();

        assertTrue(result, "Default password should not fail validation, only warn");
        assertTrue(validator.getValidationWarnings().stream()
                .anyMatch(warning -> warning.contains("database.password") && warning.contains("default")),
                "Should have password warning");
    }

    @Test
    void testTierChancesWarning() {
        // Set tier chances that don't add up to 100
        testConfig.set("chest.tier-chances.center.common", 30);
        testConfig.set("chest.tier-chances.center.uncommon", 40);
        testConfig.set("chest.tier-chances.center.rare", 20); // Total = 90, not 100

        boolean result = validator.validateConfiguration();

        assertTrue(result, "Tier chances not adding to 100 should only warn, not fail");
        assertTrue(validator.getValidationWarnings().stream()
                .anyMatch(warning -> warning.contains("tier chances should add up to 100")),
                "Should have tier chances warning");
    }
}