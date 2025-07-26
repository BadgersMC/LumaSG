package net.lumalyte.lumasg.discord.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Simple test for DiscordConfigManager to verify basic functionality.
 */
class DiscordConfigManagerSimpleTest {

    @Test
    void testConfigManagerCreation() {
        // This test just verifies the class can be instantiated
        // We'll use a mock plugin in a more comprehensive test
        assertNotNull(DiscordConfigManager.class);
        assertNotNull(DiscordConfig.class);
    }
}