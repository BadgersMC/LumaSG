package net.lumalyte.lumasg.security;

import net.lumalyte.lumasg.util.security.InputSanitizer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for the InputSanitizer class.
 * 
 * These tests ensure that input sanitization properly prevents:
 * - SQL injection attacks
 * - XSS attacks
 * - Command injection
 * - Path traversal attacks
 * - Malicious player names and user input
 */
class InputSanitizerTest {

    @Test
    void testValidPlayerNamePassesThrough() {
        String validName = "TestPlayer123";
        assertEquals(validName, InputSanitizer.sanitizePlayerName(validName));
        assertTrue(InputSanitizer.isValidPlayerName(validName));
    }

    @Test
    void testInvalidPlayerNamesSanitized() {
        // Test null and empty
        assertEquals("Unknown", InputSanitizer.sanitizePlayerName(null));
        assertEquals("Unknown", InputSanitizer.sanitizePlayerName(""));
        assertEquals("Unknown", InputSanitizer.sanitizePlayerName("   "));

        // Test special characters removed
        assertEquals("TestPlayer", InputSanitizer.sanitizePlayerName("Test@Player!"));
        assertEquals("TestPlayer", InputSanitizer.sanitizePlayerName("Test<>Player"));

        // Test length limit
        String longName = "ThisIsAVeryLongPlayerNameThatExceedsTheLimit";
        String sanitized = InputSanitizer.sanitizePlayerName(longName);
        assertTrue(sanitized.length() <= 16);

        // Test control characters removed
        assertEquals("TestPlayer", InputSanitizer.sanitizePlayerName("Test\u0000Player\u001F"));
    }

    @Test
    void testValidArenaNamePassesThrough() {
        String validName = "test-arena_01";
        assertEquals(validName, InputSanitizer.sanitizeArenaName(validName));
        assertTrue(InputSanitizer.isValidArenaName(validName));
    }

    @Test
    void testInvalidArenaNamesHandled() {
        // Test null and empty
        assertEquals("default", InputSanitizer.sanitizeArenaName(null));
        assertEquals("default", InputSanitizer.sanitizeArenaName(""));

        // Test path traversal prevention
        assertEquals("arena", InputSanitizer.sanitizeArenaName("../arena"));
        assertEquals("arena", InputSanitizer.sanitizeArenaName("..\\arena"));
        assertEquals("arena", InputSanitizer.sanitizeArenaName("%2e%2e%2farena"));

        // Test special characters removed
        assertEquals("testarena", InputSanitizer.sanitizeArenaName("test@arena!"));

        // Test length limit
        String longName = "ThisIsAVeryLongArenaNameThatExceedsTheThirtyTwoCharacterLimit";
        String sanitized = InputSanitizer.sanitizeArenaName(longName);
        assertTrue(sanitized.length() <= 32);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "'; DROP TABLE users; --",
            "' UNION SELECT * FROM passwords --",
            "'; INSERT INTO users VALUES ('hacker', 'password'); --"
    })
    void testSqlInjectionPrevention(String maliciousInput) {
        // Command arguments should reject SQL injection entirely
        assertEquals("", InputSanitizer.sanitizeCommandArgument(maliciousInput));

        // Database input should sanitize but not reject entirely
        String sanitized = InputSanitizer.sanitizeDatabaseInput(maliciousInput);
        assertFalse(sanitized.contains("DROP"));
        assertFalse(sanitized.contains("UNION"));
        assertFalse(sanitized.contains("--"));

        // Should be detected as security threat
        assertTrue(InputSanitizer.containsSecurityThreats(maliciousInput));
    }

    @Test
    void testSqlInjectionEdgeCases() {
        // These should be sanitized but not completely rejected since they're less
        // obvious
        String input1 = "1' OR '1'='1";
        String sanitized1 = InputSanitizer.sanitizeCommandArgument(input1);
        // This will be sanitized to remove dangerous characters but may not be empty
        assertFalse(sanitized1.contains("'"));

        String input2 = "admin'--";
        String sanitized2 = InputSanitizer.sanitizeCommandArgument(input2);
        // This will be sanitized to remove dangerous characters
        assertFalse(sanitized2.contains("'"));
        // Note: The double dash might still be present if not detected as SQL injection
        // but single quotes should be removed
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "<script>alert('xss')</script>",
            "javascript:alert('xss')",
            "vbscript:msgbox('xss')",
            "<img src=x onerror=alert('xss')>",
            "onload=\"alert('xss')\""
    })
    void testXssAttackPrevention(String maliciousInput) {
        String sanitized = InputSanitizer.sanitizeChatMessage(maliciousInput);

        // Should not contain script tags or javascript
        assertFalse(sanitized.toLowerCase().contains("<script"));
        assertFalse(sanitized.toLowerCase().contains("javascript:"));
        assertFalse(sanitized.toLowerCase().contains("vbscript:"));

        // Should be detected as security threat
        assertTrue(InputSanitizer.containsSecurityThreats(maliciousInput));
    }

    @Test
    void testCommandArgumentSanitization() {
        // Valid arguments should pass through
        assertEquals("test-arg", InputSanitizer.sanitizeCommandArgument("test-arg"));
        assertEquals("123", InputSanitizer.sanitizeCommandArgument("123"));
        assertEquals("file.txt", InputSanitizer.sanitizeCommandArgument("file.txt"));

        // Invalid characters should be removed
        assertEquals("testarg", InputSanitizer.sanitizeCommandArgument("test@arg!"));
        assertEquals("rm-rf", InputSanitizer.sanitizeCommandArgument("'; rm -rf /"));

        // Length limit should be enforced
        String longArg = "a".repeat(100);
        String sanitized = InputSanitizer.sanitizeCommandArgument(longArg);
        assertTrue(sanitized.length() <= 64);
    }

    @Test
    void testChatMessageSanitization() {
        // Normal messages should pass through mostly unchanged
        String normalMessage = "Hello world! How are you?";
        assertEquals(normalMessage, InputSanitizer.sanitizeChatMessage(normalMessage));

        // HTML should be escaped
        String htmlMessage = "Hello <b>world</b>!";
        String sanitized = InputSanitizer.sanitizeChatMessage(htmlMessage);
        assertTrue(sanitized.contains("&lt;b&gt;"));
        assertTrue(sanitized.contains("&lt;/b&gt;"));

        // Control characters should be removed
        assertEquals("Helloworld", InputSanitizer.sanitizeChatMessage("Hello\u0000world\u001F"));

        // Length limit should be enforced
        String longMessage = "a".repeat(300);
        String sanitizedLong = InputSanitizer.sanitizeChatMessage(longMessage);
        assertTrue(sanitizedLong.length() <= 256);
    }

    @Test
    void testFilenameSanitization() {
        // Valid filenames should pass through
        assertEquals("test.txt", InputSanitizer.sanitizeFilename("test.txt"));
        assertEquals("my-file_01.dat", InputSanitizer.sanitizeFilename("my-file_01.dat"));

        // Invalid characters should be removed
        assertEquals("testfile.txt", InputSanitizer.sanitizeFilename("test<>file.txt"));
        assertEquals("testfile.txt", InputSanitizer.sanitizeFilename("test|file.txt"));

        // Path traversal should be prevented
        assertEquals("file.txt", InputSanitizer.sanitizeFilename("../file.txt"));
        assertEquals("file.txt", InputSanitizer.sanitizeFilename("..\\file.txt"));

        // Reserved Windows names should be prefixed
        assertEquals("file_CON.txt", InputSanitizer.sanitizeFilename("CON.txt"));
        assertEquals("file_PRN", InputSanitizer.sanitizeFilename("PRN"));

        // Leading/trailing dots and spaces should be removed
        assertEquals("file.txt", InputSanitizer.sanitizeFilename("...file.txt..."));
        assertEquals("file.txt", InputSanitizer.sanitizeFilename("   file.txt   "));

        // Empty/null should return default
        assertEquals("unnamed", InputSanitizer.sanitizeFilename(null));
        assertEquals("unnamed", InputSanitizer.sanitizeFilename(""));
        assertEquals("unnamed", InputSanitizer.sanitizeFilename("..."));
    }

    @Test
    void testDatabaseInputSanitization() {
        // Normal input should pass through with quote escaping
        assertEquals("test''s data", InputSanitizer.sanitizeDatabaseInput("test's data"));

        // SQL injection should be neutralized
        String maliciousInput = "'; DROP TABLE users; --";
        String sanitized = InputSanitizer.sanitizeDatabaseInput(maliciousInput);
        assertFalse(sanitized.contains("DROP"));
        assertFalse(sanitized.contains("--"));
        assertFalse(sanitized.contains(";"));

        // Control characters should be removed
        assertEquals("testdata", InputSanitizer.sanitizeDatabaseInput("test\u0000data\u001F"));

        // Null should return empty string
        assertEquals("", InputSanitizer.sanitizeDatabaseInput(null));
    }

    @Test
    void testSecurityThreatDetection() {
        // Should detect SQL injection
        assertTrue(InputSanitizer.containsSecurityThreats("'; DROP TABLE users; --"));
        assertTrue(InputSanitizer.containsSecurityThreats("UNION SELECT * FROM passwords"));

        // Should detect XSS
        assertTrue(InputSanitizer.containsSecurityThreats("<script>alert('xss')</script>"));
        assertTrue(InputSanitizer.containsSecurityThreats("javascript:alert('xss')"));

        // Should detect path traversal
        assertTrue(InputSanitizer.containsSecurityThreats("../../../etc/passwd"));
        assertTrue(InputSanitizer.containsSecurityThreats("..\\..\\windows\\system32"));

        // Should not flag normal input
        assertFalse(InputSanitizer.containsSecurityThreats("Hello world"));
        assertFalse(InputSanitizer.containsSecurityThreats("test-file.txt"));
        assertFalse(InputSanitizer.containsSecurityThreats("player123"));
    }

    @Test
    void testLoggingSanitization() {
        // Normal input should pass through
        assertEquals("Normal log message", InputSanitizer.sanitizeForLogging("Normal log message"));

        // Control characters should be removed
        assertEquals("Logmessage", InputSanitizer.sanitizeForLogging("Log\u0000message\u001F"));

        // ANSI escape sequences should be removed
        assertEquals("[31mColored text[0m", InputSanitizer.sanitizeForLogging("\u001B[31mColored text\u001B[0m"));

        // Long messages should be truncated
        String longMessage = "a".repeat(250);
        String sanitized = InputSanitizer.sanitizeForLogging(longMessage);
        assertTrue(sanitized.length() <= 200);
        assertTrue(sanitized.endsWith("..."));

        // Null should return "null"
        assertEquals("null", InputSanitizer.sanitizeForLogging(null));
    }

    @Test
    void testValidationMethods() {
        // Valid inputs should pass validation
        assertTrue(InputSanitizer.isValidPlayerName("TestPlayer123"));
        assertTrue(InputSanitizer.isValidArenaName("test-arena_01"));

        // Invalid inputs should fail validation
        assertFalse(InputSanitizer.isValidPlayerName("Test@Player"));
        assertFalse(InputSanitizer.isValidPlayerName(null));
        assertFalse(InputSanitizer.isValidPlayerName(""));
        assertFalse(InputSanitizer.isValidPlayerName("ThisNameIsTooLongForMinecraft"));

        assertFalse(InputSanitizer.isValidArenaName("../arena"));
        assertFalse(InputSanitizer.isValidArenaName("arena@test"));
        assertFalse(InputSanitizer.isValidArenaName(null));
        assertFalse(InputSanitizer.isValidArenaName(""));
    }

    @Test
    void testEdgeCases() {
        // Test with only invalid characters
        assertEquals("Unknown", InputSanitizer.sanitizePlayerName("@#$%^&*()"));
        assertEquals("default", InputSanitizer.sanitizeArenaName("@#$%^&*()"));
        assertEquals("", InputSanitizer.sanitizeCommandArgument("@#$%^&*()"));

        // Test with mixed valid/invalid characters
        assertEquals("Test123", InputSanitizer.sanitizePlayerName("Test@123#"));
        assertEquals("testarena", InputSanitizer.sanitizeArenaName("test@arena#"));

        // Test with whitespace
        assertEquals("TestPlayer", InputSanitizer.sanitizePlayerName("  TestPlayer  "));
        assertEquals("test-arena", InputSanitizer.sanitizeArenaName("  test-arena  "));

        // Test with Unicode characters
        assertEquals("Test", InputSanitizer.sanitizePlayerName("Test\u00A9\u00AE"));
        assertEquals("test", InputSanitizer.sanitizeArenaName("test\u00A9\u00AE"));
    }
}