package net.lumalyte.lumasg.util.security;


import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Pattern;

/**
 * Comprehensive input sanitization utility for LumaSG.
 * 
 * This class provides security-focused input sanitization to prevent:
 * - SQL injection attacks
 * - XSS attacks in web interfaces
 * - Command injection
 * - Path traversal attacks
 * - Malicious player names and user input
 * 
 * All methods are designed to be safe-by-default, returning sanitized
 * versions of input rather than throwing exceptions.
 * 
 * @author LumaSG Team
 * @version 1.0
 * @since 1.0
 */
public final class InputSanitizer {
    
    // Regex patterns for validation
    private static final Pattern VALID_PLAYER_NAME = Pattern.compile("^[a-zA-Z0-9_]{1,16}$");
    private static final Pattern VALID_ARENA_NAME = Pattern.compile("^[a-zA-Z0-9_\\-]{1,32}$");
    private static final Pattern VALID_COMMAND_ARG = Pattern.compile("^[a-zA-Z0-9_\\-\\.]{1,64}$");
    private static final Pattern SQL_INJECTION_PATTERNS = Pattern.compile(
        "(?i)(\\bunion\\s+select|\\bselect\\s+.*\\bfrom|\\binsert\\s+into|\\bupdate\\s+.*\\bset|\\bdelete\\s+from|\\bdrop\\s+table|\\bcreate\\s+table|\\balter\\s+table|\\bexec\\s*\\(|\\bexecute\\s*\\()",
        Pattern.CASE_INSENSITIVE
    );
    
    // Dangerous characters that should be removed or escaped
    private static final Pattern DANGEROUS_CHARS = Pattern.compile("[<>\"'&;\\\\]");
    private static final Pattern PATH_TRAVERSAL = Pattern.compile("(\\.\\./|\\.\\.\\\\|%2e%2e%2f|%2e%2e%5c)", Pattern.CASE_INSENSITIVE);
    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]");
    private static final Pattern CONTROL_CHARS_NO_SPACE = Pattern.compile("[\\x00-\\x1F\\x7F]");
    
    // Maximum lengths for different input types
    private static final int MAX_PLAYER_NAME_LENGTH = 16;
    private static final int MAX_ARENA_NAME_LENGTH = 32;
    private static final int MAX_COMMAND_ARG_LENGTH = 64;
    private static final int MAX_CHAT_MESSAGE_LENGTH = 256;
    private static final int MAX_FILENAME_LENGTH = 255;
    
    /**
     * Private constructor to prevent instantiation.
     */
    private InputSanitizer() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }
    
    /**
     * Sanitizes a player name to ensure it's safe for database storage and display.
     * 
     * Player names are restricted to alphanumeric characters and underscores,
     * with a maximum length of 16 characters (Minecraft standard).
     * 
     * @param playerName The raw player name input
     * @return A sanitized player name, or "Unknown" if input is invalid
     */
    public static @NotNull String sanitizePlayerName(@Nullable String playerName) {
        if (playerName == null || playerName.trim().isEmpty()) {
            return "Unknown";
        }
        
        // Trim and limit length
        String sanitized = playerName.trim();
        if (sanitized.length() > MAX_PLAYER_NAME_LENGTH) {
            sanitized = sanitized.substring(0, MAX_PLAYER_NAME_LENGTH);
        }
        
        // Remove control characters
        sanitized = CONTROL_CHARS.matcher(sanitized).replaceAll("");
        
        // Validate against allowed pattern
        if (!VALID_PLAYER_NAME.matcher(sanitized).matches()) {
            // Remove invalid characters, keeping only alphanumeric and underscore
            sanitized = sanitized.replaceAll("[^a-zA-Z0-9_]", "");
            
            // If nothing valid remains, return default
            if (sanitized.isEmpty()) {
                return "Unknown";
            }
        }
        
        return sanitized;
    }
    
    /**
     * Sanitizes an arena name to ensure it's safe for file system and database use.
     * 
     * Arena names allow alphanumeric characters, underscores, and hyphens,
     * with a maximum length of 32 characters.
     * 
     * @param arenaName The raw arena name input
     * @return A sanitized arena name, or "default" if input is invalid
     */
    public static @NotNull String sanitizeArenaName(@Nullable String arenaName) {
        if (arenaName == null || arenaName.trim().isEmpty()) {
            return "default";
        }
        
        // Trim and limit length
        String sanitized = arenaName.trim();
        if (sanitized.length() > MAX_ARENA_NAME_LENGTH) {
            sanitized = sanitized.substring(0, MAX_ARENA_NAME_LENGTH);
        }
        
        // Remove control characters
        sanitized = CONTROL_CHARS.matcher(sanitized).replaceAll("");
        
        // Remove path traversal attempts
        sanitized = PATH_TRAVERSAL.matcher(sanitized).replaceAll("");
        
        // Validate against allowed pattern
        if (!VALID_ARENA_NAME.matcher(sanitized).matches()) {
            // Remove invalid characters
            sanitized = sanitized.replaceAll("[^a-zA-Z0-9_\\-]", "");
            
            // If nothing valid remains, return default
            if (sanitized.isEmpty()) {
                return "default";
            }
        }
        
        return sanitized;
    }
    
    /**
     * Sanitizes a command argument to prevent command injection attacks.
     * 
     * @param argument The raw command argument
     * @return A sanitized command argument, or empty string if invalid
     */
    public static @NotNull String sanitizeCommandArgument(@Nullable String argument) {
        if (argument == null || argument.trim().isEmpty()) {
            return "";
        }
        
        // Trim and limit length
        String sanitized = argument.trim();
        if (sanitized.length() > MAX_COMMAND_ARG_LENGTH) {
            sanitized = sanitized.substring(0, MAX_COMMAND_ARG_LENGTH);
        }
        
        // Remove control characters
        sanitized = CONTROL_CHARS.matcher(sanitized).replaceAll("");
        
        // Remove dangerous characters
        sanitized = DANGEROUS_CHARS.matcher(sanitized).replaceAll("");
        
        // Check for SQL injection patterns
        if (SQL_INJECTION_PATTERNS.matcher(sanitized).find()) {
            return ""; // Reject entirely if SQL injection detected
        }
        
        // Validate against allowed pattern
        if (!VALID_COMMAND_ARG.matcher(sanitized).matches()) {
            // Remove invalid characters
            sanitized = sanitized.replaceAll("[^a-zA-Z0-9_\\-\\.]", "");
        }
        
        return sanitized;
    }
    
    /**
     * Sanitizes a chat message or user input for safe display and storage.
     * 
     * @param message The raw message input
     * @return A sanitized message with dangerous content removed/escaped
     */
    public static @NotNull String sanitizeChatMessage(@Nullable String message) {
        if (message == null || message.trim().isEmpty()) {
            return "";
        }
        
        // Trim and limit length
        String sanitized = message.trim();
        if (sanitized.length() > MAX_CHAT_MESSAGE_LENGTH) {
            sanitized = sanitized.substring(0, MAX_CHAT_MESSAGE_LENGTH);
        }
        
        // Remove control characters (except newlines, tabs, and spaces)
        sanitized = CONTROL_CHARS.matcher(sanitized).replaceAll("");
        
        // Escape HTML/XML characters for safety (manual implementation to avoid dependency issues)
        sanitized = sanitized.replace("&", "&amp;")
                            .replace("<", "&lt;")
                            .replace(">", "&gt;")
                            .replace("\"", "&quot;")
                            .replace("'", "&#x27;");
        
        // Remove potential script injection
        sanitized = sanitized.replaceAll("(?i)<script[^>]*>.*?</script>", "");
        sanitized = sanitized.replaceAll("(?i)javascript:", "");
        sanitized = sanitized.replaceAll("(?i)vbscript:", "");
        
        return sanitized;
    }
    
    /**
     * Sanitizes a filename to prevent path traversal and ensure file system safety.
     * 
     * @param filename The raw filename input
     * @return A sanitized filename safe for file system use
     */
    public static @NotNull String sanitizeFilename(@Nullable String filename) {
        if (filename == null || filename.trim().isEmpty()) {
            return "unnamed";
        }
        
        // Trim and limit length
        String sanitized = filename.trim();
        if (sanitized.length() > MAX_FILENAME_LENGTH) {
            sanitized = sanitized.substring(0, MAX_FILENAME_LENGTH);
        }
        
        // Remove control characters
        sanitized = CONTROL_CHARS.matcher(sanitized).replaceAll("");
        
        // Remove path traversal attempts
        sanitized = PATH_TRAVERSAL.matcher(sanitized).replaceAll("");
        
        // Remove dangerous filename characters
        sanitized = sanitized.replaceAll("[<>:\"/\\\\|?*]", "");
        
        // Remove leading/trailing dots and spaces (Windows compatibility)
        sanitized = sanitized.replaceAll("^[\\.\\s]+|[\\.\\s]+$", "");
        
        // Prevent reserved Windows filenames
        String[] reservedNames = {"CON", "PRN", "AUX", "NUL", "COM1", "COM2", "COM3", "COM4", 
                                 "COM5", "COM6", "COM7", "COM8", "COM9", "LPT1", "LPT2", 
                                 "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9"};
        
        String upperSanitized = sanitized.toUpperCase();
        for (String reserved : reservedNames) {
            if (upperSanitized.equals(reserved) || upperSanitized.startsWith(reserved + ".")) {
                sanitized = "file_" + sanitized;
                break;
            }
        }
        
        // If nothing valid remains, return default
        if (sanitized.isEmpty()) {
            return "unnamed";
        }
        
        return sanitized;
    }
    
    /**
     * Sanitizes database input to prevent SQL injection.
     * 
     * Note: This is a defense-in-depth measure. Primary protection should
     * come from prepared statements and parameterized queries.
     * 
     * @param input The raw database input
     * @return A sanitized string safe for database operations
     */
    public static @NotNull String sanitizeDatabaseInput(@Nullable String input) {
        if (input == null) {
            return "";
        }
        
        String sanitized = input.trim();
        
        // Remove control characters
        sanitized = CONTROL_CHARS.matcher(sanitized).replaceAll("");
        
        // Check for and reject SQL injection patterns
        if (SQL_INJECTION_PATTERNS.matcher(sanitized).find()) {
            return ""; // Reject entirely if SQL injection detected
        }
        
        // Escape single quotes (double them for SQL safety)
        sanitized = sanitized.replace("'", "''");
        
        // Remove or escape other dangerous SQL characters
        sanitized = sanitized.replace("--", ""); // Remove SQL comments
        sanitized = sanitized.replace("/*", ""); // Remove SQL block comments
        sanitized = sanitized.replace("*/", "");
        sanitized = sanitized.replace(";", ""); // Remove statement terminators
        
        return sanitized;
    }
    
    /**
     * Validates if a player name is safe without modification.
     * 
     * @param playerName The player name to validate
     * @return true if the player name is safe as-is, false if it needs sanitization
     */
    public static boolean isValidPlayerName(@Nullable String playerName) {
        if (playerName == null || playerName.trim().isEmpty()) {
            return false;
        }
        
        String trimmed = playerName.trim();
        return trimmed.length() <= MAX_PLAYER_NAME_LENGTH && 
               VALID_PLAYER_NAME.matcher(trimmed).matches() &&
               !CONTROL_CHARS.matcher(trimmed).find();
    }
    
    /**
     * Validates if an arena name is safe without modification.
     * 
     * @param arenaName The arena name to validate
     * @return true if the arena name is safe as-is, false if it needs sanitization
     */
    public static boolean isValidArenaName(@Nullable String arenaName) {
        if (arenaName == null || arenaName.trim().isEmpty()) {
            return false;
        }
        
        String trimmed = arenaName.trim();
        return trimmed.length() <= MAX_ARENA_NAME_LENGTH && 
               VALID_ARENA_NAME.matcher(trimmed).matches() &&
               !CONTROL_CHARS.matcher(trimmed).find() &&
               !PATH_TRAVERSAL.matcher(trimmed).find();
    }
    
    /**
     * Checks if input contains potential security threats.
     * 
     * @param input The input to check
     * @return true if the input appears to contain security threats
     */
    public static boolean containsSecurityThreats(@Nullable String input) {
        if (input == null) {
            return false;
        }
        
        String lowerInput = input.toLowerCase();
        
        return SQL_INJECTION_PATTERNS.matcher(input).find() ||
               PATH_TRAVERSAL.matcher(input).find() ||
               lowerInput.contains("javascript:") ||
               lowerInput.contains("vbscript:") ||
               lowerInput.contains("<script") ||
               lowerInput.contains("onload=") ||
               lowerInput.contains("onerror=") ||
               lowerInput.contains("onclick=") ||
               lowerInput.contains("onmouseover=");
    }
    
    /**
     * Sanitizes input for logging purposes to prevent log injection attacks.
     * 
     * @param logInput The input to be logged
     * @return A sanitized version safe for logging
     */
    public static @NotNull String sanitizeForLogging(@Nullable String logInput) {
        if (logInput == null) {
            return "null";
        }
        
        String sanitized = logInput;
        
        // Remove control characters that could break log format
        sanitized = CONTROL_CHARS.matcher(sanitized).replaceAll("");
        
        // Remove ANSI escape sequences that could manipulate terminal output
        sanitized = sanitized.replaceAll("\\x1B\\[[0-9;]*[a-zA-Z]", "");
        
        // Limit length to prevent log flooding
        if (sanitized.length() > 200) {
            sanitized = sanitized.substring(0, 197) + "...";
        }
        
        return sanitized;
    }
}