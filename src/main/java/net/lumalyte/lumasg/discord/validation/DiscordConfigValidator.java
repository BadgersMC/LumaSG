package net.lumalyte.lumasg.discord.validation;

import net.lumalyte.lumasg.discord.config.DiscordConfig;
import net.lumalyte.lumasg.util.core.DebugLogger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Comprehensive validation framework for Discord configuration.
 * Provides detailed validation of Discord bot tokens, channel IDs, webhook URLs,
 * and other Discord-specific configuration values.
 */
public class DiscordConfigValidator {
    
    private final @NotNull DebugLogger.ContextualLogger logger;
    
    // Discord validation patterns
    private static final Pattern DISCORD_TOKEN_PATTERN = Pattern.compile("^[A-Za-z0-9._-]{50,}$");
    private static final Pattern DISCORD_CHANNEL_ID_PATTERN = Pattern.compile("^\\d{17,19}$");
    private static final Pattern DISCORD_WEBHOOK_URL_PATTERN = Pattern.compile(
        "^https://discord(?:app)?\\.com/api/webhooks/\\d{17,19}/[A-Za-z0-9._-]+$"
    );
    private static final Pattern HEX_COLOR_PATTERN = Pattern.compile("^#[0-9A-Fa-f]{6}$");
    private static final Pattern URL_PATTERN = Pattern.compile(
        "^https?://[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}(?:/.*)?$"
    );
    
    /**
     * Creates a new Discord configuration validator.
     * 
     * @param logger The logger for validation messages
     */
    public DiscordConfigValidator(@NotNull DebugLogger.ContextualLogger logger) {
        this.logger = logger;
    }
    
    /**
     * Validates a complete Discord configuration.
     * 
     * @param config The Discord configuration to validate
     * @return A validation result containing any errors or warnings
     */
    public @NotNull ValidationResult validateConfiguration(@Nullable DiscordConfig config) {
        ValidationResult result = new ValidationResult();
        
        if (config == null) {
            result.addError("Discord configuration is null");
            return result;
        }
        
        // Skip validation if Discord is disabled
        if (!config.isEnabled()) {
            logger.debug("Discord integration is disabled - skipping detailed validation");
            return result;
        }
        
        // Validate bot token
        validateBotToken(config.getBotToken(), result);
        
        // Validate connection settings
        validateConnectionSettings(config, result);
        
        // Validate channel configuration
        validateChannelConfiguration(config.getChannelMappings(), result);
        
        // Validate webhook configuration
        validateWebhookConfiguration(config.getWebhooks(), result);
        
        // Validate announcement settings
        validateAnnouncementSettings(config, result);
        
        // Validate formatting settings
        validateFormattingSettings(config, result);
        
        // Validate permission settings
        validatePermissionSettings(config, result);
        
        return result;
    }
    
    /**
     * Validates the Discord bot token.
     * 
     * @param botToken The bot token to validate
     * @param result The validation result to add errors to
     */
    private void validateBotToken(@Nullable String botToken, @NotNull ValidationResult result) {
        if (botToken == null || botToken.isEmpty()) {
            result.addError("Discord bot token is not configured");
            return;
        }
        
        if (botToken.equals("YOUR_BOT_TOKEN_HERE")) {
            result.addError("Discord bot token is using default placeholder value");
            return;
        }
        
        if (botToken.length() < 50) {
            result.addError("Discord bot token appears to be too short (minimum 50 characters)");
            return;
        }
        
        if (!DISCORD_TOKEN_PATTERN.matcher(botToken).matches()) {
            result.addError("Discord bot token contains invalid characters");
            return;
        }
        
        // Additional token format validation
        String[] tokenParts = botToken.split("\\.");
        if (tokenParts.length != 3) {
            result.addWarning("Discord bot token format may be invalid (expected 3 parts separated by dots)");
        }
        
        logger.debug("Discord bot token validation passed");
    }
    
    /**
     * Validates connection settings.
     * 
     * @param config The Discord configuration
     * @param result The validation result to add errors to
     */
    private void validateConnectionSettings(@NotNull DiscordConfig config, @NotNull ValidationResult result) {
        // Validate reconnect attempts
        if (config.getReconnectAttempts() < 1) {
            result.addError("Discord reconnect attempts must be at least 1");
        } else if (config.getReconnectAttempts() > 10) {
            result.addWarning("Discord reconnect attempts is very high (" + config.getReconnectAttempts() + 
                            "), consider reducing to avoid excessive retry attempts");
        }
        
        // Validate reconnect delay
        if (config.getReconnectDelay() < 1000) {
            result.addError("Discord reconnect delay must be at least 1000ms (1 second)");
        } else if (config.getReconnectDelay() > 300000) {
            result.addWarning("Discord reconnect delay is very high (" + (config.getReconnectDelay() / 1000) + 
                            " seconds), consider reducing for faster recovery");
        }
        
        // Validate connection timeout
        if (config.getConnectionTimeout() < 5000) {
            result.addError("Discord connection timeout must be at least 5000ms (5 seconds)");
        } else if (config.getConnectionTimeout() > 60000) {
            result.addWarning("Discord connection timeout is very high (" + (config.getConnectionTimeout() / 1000) + 
                            " seconds), consider reducing");
        }
        
        logger.debug("Discord connection settings validation completed");
    }
    
    /**
     * Validates channel configuration.
     * 
     * @param channelMappings The channel mappings to validate
     * @param result The validation result to add errors to
     */
    private void validateChannelConfiguration(@NotNull Map<String, String> channelMappings, 
                                            @NotNull ValidationResult result) {
        if (channelMappings.isEmpty()) {
            result.addWarning("No Discord channels configured - announcements will not work");
            return;
        }
        
        for (Map.Entry<String, String> entry : channelMappings.entrySet()) {
            String channelName = entry.getKey();
            String channelId = entry.getValue();
            
            if (channelId == null || channelId.isEmpty()) {
                result.addWarning("Discord channel '" + channelName + "' has empty ID");
                continue;
            }
            
            if (!DISCORD_CHANNEL_ID_PATTERN.matcher(channelId).matches()) {
                result.addError("Discord channel '" + channelName + "' has invalid ID format: " + channelId);
            }
        }
        
        logger.debug("Discord channel configuration validation completed");
    }
    
    /**
     * Validates webhook configuration.
     * 
     * @param webhooks The webhook mappings to validate
     * @param result The validation result to add errors to
     */
    private void validateWebhookConfiguration(@NotNull Map<String, String> webhooks, 
                                            @NotNull ValidationResult result) {
        if (webhooks.isEmpty()) {
            result.addInfo("No Discord webhooks configured - using bot messaging only");
            return;
        }
        
        for (Map.Entry<String, String> entry : webhooks.entrySet()) {
            String webhookName = entry.getKey();
            String webhookUrl = entry.getValue();
            
            if (webhookUrl == null || webhookUrl.isEmpty()) {
                result.addWarning("Discord webhook '" + webhookName + "' has empty URL");
                continue;
            }
            
            if (!DISCORD_WEBHOOK_URL_PATTERN.matcher(webhookUrl).matches()) {
                result.addError("Discord webhook '" + webhookName + "' has invalid URL format: " + webhookUrl);
            }
        }
        
        logger.debug("Discord webhook configuration validation completed");
    }
    
    /**
     * Validates announcement settings.
     * 
     * @param config The Discord configuration
     * @param result The validation result to add errors to
     */
    private void validateAnnouncementSettings(@NotNull DiscordConfig config, @NotNull ValidationResult result) {
        // Validate milestone thresholds
        List<Integer> thresholds = config.getMilestoneThresholds();
        if (thresholds.isEmpty()) {
            result.addWarning("No milestone thresholds configured - milestone announcements will not work");
        } else {
            for (int threshold : thresholds) {
                if (threshold < 1) {
                    result.addError("Milestone threshold must be at least 1, found: " + threshold);
                } else if (threshold > 100) {
                    result.addWarning("Milestone threshold is very high (" + threshold + 
                                    "), consider reducing for more frequent announcements");
                }
            }
        }
        
        // Validate max players in embed
        if (config.getMaxPlayersInEmbed() < 1) {
            result.addError("Max players in embed must be at least 1");
        } else if (config.getMaxPlayersInEmbed() > 50) {
            result.addWarning("Max players in embed is very high (" + config.getMaxPlayersInEmbed() + 
                            "), Discord embeds have size limits");
        }
        
        logger.debug("Discord announcement settings validation completed");
    }
    
    /**
     * Validates formatting settings.
     * 
     * @param config The Discord configuration
     * @param result The validation result to add errors to
     */
    private void validateFormattingSettings(@NotNull DiscordConfig config, @NotNull ValidationResult result) {
        // Validate embed color
        String embedColor = config.getEmbedColor();
        if (embedColor != null && !embedColor.isEmpty()) {
            if (!HEX_COLOR_PATTERN.matcher(embedColor).matches()) {
                result.addError("Discord embed color has invalid format: " + embedColor + 
                              " (expected format: #RRGGBB)");
            }
        }
        
        // Validate server icon URL
        String serverIconUrl = config.getServerIconUrl();
        if (serverIconUrl != null && !serverIconUrl.isEmpty()) {
            if (!URL_PATTERN.matcher(serverIconUrl).matches()) {
                result.addError("Discord server icon URL has invalid format: " + serverIconUrl);
            }
        }
        
        // Validate timestamp format
        String timestampFormat = config.getTimestampFormat();
        if (timestampFormat == null || timestampFormat.isEmpty()) {
            result.addWarning("Discord timestamp format is empty - using default format");
        } else {
            try {
                // Test the format with a sample date
                java.time.format.DateTimeFormatter.ofPattern(timestampFormat);
            } catch (IllegalArgumentException e) {
                result.addError("Discord timestamp format is invalid: " + timestampFormat + 
                              " (" + e.getMessage() + ")");
            }
        }
        
        logger.debug("Discord formatting settings validation completed");
    }
    
    /**
     * Validates permission settings.
     * 
     * @param config The Discord configuration
     * @param result The validation result to add errors to
     */
    private void validatePermissionSettings(@NotNull DiscordConfig config, @NotNull ValidationResult result) {
        // Validate admin roles
        List<String> adminRoles = config.getAdminRoles();
        if (adminRoles.isEmpty()) {
            result.addWarning("No Discord admin roles configured - admin commands will not work");
        }
        
        // Validate moderator roles
        List<String> moderatorRoles = config.getModeratorRoles();
        if (moderatorRoles.isEmpty()) {
            result.addInfo("No Discord moderator roles configured");
        }
        
        // Validate role permissions mapping
        Map<String, String> rolePermissions = config.getRolePermissions();
        if (rolePermissions.isEmpty()) {
            result.addWarning("No Discord role permissions configured - role-based permissions will not work");
        } else {
            for (Map.Entry<String, String> entry : rolePermissions.entrySet()) {
                String roleName = entry.getKey();
                String permission = entry.getValue();
                
                if (permission == null || permission.isEmpty()) {
                    result.addWarning("Discord role '" + roleName + "' has empty permission mapping");
                } else if (!permission.startsWith("lumasg.")) {
                    result.addWarning("Discord role '" + roleName + "' permission '" + permission + 
                                    "' does not follow LumaSG permission format (lumasg.*)");
                }
            }
        }
        
        logger.debug("Discord permission settings validation completed");
    }
    
    /**
     * Represents the result of a Discord configuration validation.
     */
    public static class ValidationResult {
        private final List<String> errors = new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();
        private final List<String> info = new ArrayList<>();
        
        /**
         * Adds an error to the validation result.
         * 
         * @param error The error message
         */
        public void addError(@NotNull String error) {
            errors.add(error);
        }
        
        /**
         * Adds a warning to the validation result.
         * 
         * @param warning The warning message
         */
        public void addWarning(@NotNull String warning) {
            warnings.add(warning);
        }
        
        /**
         * Adds an info message to the validation result.
         * 
         * @param info The info message
         */
        public void addInfo(@NotNull String info) {
            this.info.add(info);
        }
        
        /**
         * Checks if the validation passed (no errors).
         * 
         * @return true if validation passed, false if there are errors
         */
        public boolean isValid() {
            return errors.isEmpty();
        }
        
        /**
         * Gets all validation errors.
         * 
         * @return List of error messages
         */
        public @NotNull List<String> getErrors() {
            return new ArrayList<>(errors);
        }
        
        /**
         * Gets all validation warnings.
         * 
         * @return List of warning messages
         */
        public @NotNull List<String> getWarnings() {
            return new ArrayList<>(warnings);
        }
        
        /**
         * Gets all validation info messages.
         * 
         * @return List of info messages
         */
        public @NotNull List<String> getInfo() {
            return new ArrayList<>(info);
        }
        
        /**
         * Gets a summary of the validation result.
         * 
         * @return A formatted summary string
         */
        public @NotNull String getSummary() {
            StringBuilder summary = new StringBuilder();
            
            if (!errors.isEmpty()) {
                summary.append("Errors (").append(errors.size()).append("):\n");
                for (String error : errors) {
                    summary.append("  - ").append(error).append("\n");
                }
            }
            
            if (!warnings.isEmpty()) {
                summary.append("Warnings (").append(warnings.size()).append("):\n");
                for (String warning : warnings) {
                    summary.append("  - ").append(warning).append("\n");
                }
            }
            
            if (!info.isEmpty()) {
                summary.append("Info (").append(info.size()).append("):\n");
                for (String infoMsg : info) {
                    summary.append("  - ").append(infoMsg).append("\n");
                }
            }
            
            if (summary.length() == 0) {
                summary.append("Validation passed with no issues");
            }
            
            return summary.toString().trim();
        }
    }
}