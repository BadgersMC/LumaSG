package net.lumalyte.game;

import net.kyori.adventure.text.Component;
import net.lumalyte.LumaSG;
import net.lumalyte.util.DebugLogger;
import net.lumalyte.util.MiniMessageUtils;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Handles configuration and formatting for deathmatch reminders.
 */
public class DeathmatchReminderConfig {
    private final @NotNull LumaSG plugin;
    private final @NotNull DebugLogger.ContextualLogger logger;
    
    private final boolean enabled;
    @NotNull List<Integer> reminderTimes;
    private final String messageTemplate;
    private final boolean playSounds;
    private final String urgentSound;
    private final String warningSound;
    
    public DeathmatchReminderConfig(@NotNull LumaSG plugin) {
        this.plugin = plugin;
        this.logger = plugin.getDebugLogger().forContext("DeathmatchReminderConfig");
        
        ConfigurationSection config = plugin.getConfig().getConfigurationSection("messages.deathmatch-reminders");
        if (config == null) {
            this.enabled = true;
            this.reminderTimes = List.of(300, 180, 120, 60, 30, 10);
            this.messageTemplate = "⚔ <red><bold>DEATHMATCH</bold></red> <gray>starting in <time_color><bold><time></bold></time_color><gray>! Prepare for battle!";
            this.playSounds = true;
            this.urgentSound = "BLOCK_NOTE_BLOCK_PLING";
            this.warningSound = "BLOCK_NOTE_BLOCK_BELL";
            logger.debug("Using default deathmatch reminder configuration");
            return;
        }
        
        this.enabled = config.getBoolean("enabled", true);
        this.reminderTimes = config.getIntegerList("reminder-times");
        if (this.reminderTimes.isEmpty()) {
            this.reminderTimes = List.of(300, 180, 120, 60, 30, 10);
            logger.debug("Using default reminder times");
        }
        
        this.messageTemplate = config.getString("message", 
            "⚔ <red><bold>DEATHMATCH</bold></red> <gray>starting in <time_color><bold><time></bold></time_color><gray>! Prepare for battle!");
        this.playSounds = config.getBoolean("play-sounds", true);
        this.urgentSound = config.getString("urgent-sound", "BLOCK_NOTE_BLOCK_PLING");
        this.warningSound = config.getString("warning-sound", "BLOCK_NOTE_BLOCK_BELL");
    }
    
    /**
     * Formats the time remaining until deathmatch with appropriate color.
     * @return A pair containing the formatted time text and color tag
     */
    public @NotNull Map.Entry<String, String> formatDeathmatchTime(int secondsUntilDeathmatch) {
        String timeText;
        String timeColorTag;
        
        if (secondsUntilDeathmatch >= 60) {
            int minutes = secondsUntilDeathmatch / 60;
            timeText = minutes + " minute" + (minutes > 1 ? "s" : "");
            timeColorTag = secondsUntilDeathmatch >= 180 ? "<yellow>" : "<gold>";
        } else {
            timeText = secondsUntilDeathmatch + " second" + (secondsUntilDeathmatch > 1 ? "s" : "");
            timeColorTag = secondsUntilDeathmatch >= 30 ? "<gold>" : "<red>";
        }
        
        return Map.entry(timeText, timeColorTag);
    }
    
    /**
     * Creates the deathmatch reminder message component with proper formatting.
     */
    public @NotNull Component createDeathmatchMessage(String timeText, String timeColorTag) {
        try {
            String processedMessage = messageTemplate.replace("<time_color>", timeColorTag)
                .replace("</time_color>", "</" + timeColorTag.substring(1));
            
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("time", timeText);
            placeholders.put("time_color", timeColorTag);
            
            return MiniMessageUtils.parseMessage(processedMessage, placeholders);
        } catch (Exception e) {
            logger.warn("Failed to parse deathmatch reminder message, using fallback", e);
            return createFallbackMessage(timeText);
        }
    }
    
    /**
     * Creates a fallback message when the configured message template fails to parse.
     */
    private @NotNull Component createFallbackMessage(String timeText) {
        return Component.text()
            .append(Component.text("⚔ DEATHMATCH", net.kyori.adventure.text.format.NamedTextColor.RED, net.kyori.adventure.text.format.TextDecoration.BOLD))
            .append(Component.text(" starting in ", net.kyori.adventure.text.format.NamedTextColor.GRAY))
            .append(Component.text(timeText, net.kyori.adventure.text.format.NamedTextColor.GOLD, net.kyori.adventure.text.format.TextDecoration.BOLD))
            .append(Component.text("! Prepare for battle!", net.kyori.adventure.text.format.NamedTextColor.GRAY))
            .build();
    }
    
    /**
     * Plays the appropriate deathmatch reminder sound for a player.
     */
    public void playDeathmatchSound(@NotNull Player player, int secondsUntilDeathmatch) {
        if (!playSounds || secondsUntilDeathmatch > 60) {
            return;
        }
        
        String soundName = secondsUntilDeathmatch <= 10 ? urgentSound : warningSound;
        
        try {
            // Parse namespaced key (supports both "minecraft:sound" and "lumalyte:whistle" formats)
            NamespacedKey soundKey = soundName.contains(":") ? 
                NamespacedKey.fromString(soundName.toLowerCase()) :
                NamespacedKey.minecraft(soundName.toLowerCase());
            
            Sound sound = Registry.SOUNDS.get(soundKey);
            if (sound != null) {
                player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
            } else {
                throw new IllegalArgumentException("Sound not found: " + soundName);
            }
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid sound name in config: " + soundName + ", using default");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 1.0f);
        }
    }
    
    // Getters
    public boolean isEnabled() {
        return enabled;
    }
    
    public List<Integer> getReminderTimes() {
        return reminderTimes;
    }
} 