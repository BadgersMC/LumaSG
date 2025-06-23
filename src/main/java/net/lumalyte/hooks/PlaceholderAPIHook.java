package net.lumalyte.hooks;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.lumalyte.LumaSG;
import net.lumalyte.game.Game;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * This class handles the integration with PlaceholderAPI.
 * It registers custom placeholders that can be used in any plugin that supports PlaceholderAPI.
 */
public class PlaceholderAPIHook extends PlaceholderExpansion {
    private final LumaSG plugin;

    public PlaceholderAPIHook(@NotNull LumaSG plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "lumasg";
    }

    @Override
    public @NotNull String getAuthor() {
        return "LumaLyte";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true; // This is required or placeholders will stop working when the plugin is reloaded
    }

    @Override
    public @Nullable String onPlaceholderRequest(Player player, @NotNull String identifier) {
        // Player-specific placeholders
        if (player != null) {
            Game game = plugin.getGameManager().getGameByPlayer(player);
            
            // Game-specific placeholders
            if (game != null) {
                switch (identifier) {
                    case "game_state":
                        return game.getState().name();
                    case "game_time_colored":
                        // Get the remaining time in seconds
                        int timeRemaining = game.getTimeRemaining();
                        
                        // Get the total game time from config
                        int totalGameTime = plugin.getConfig().getInt("game.game-time-minutes", 20) * 60;
                        
                        // Calculate the color thresholds (divide total time into 4 segments)
                        int greenThreshold = totalGameTime * 3 / 4;  // 75% or more remaining = green
                        int yellowThreshold = totalGameTime / 2;     // 50% or more remaining = yellow
                        int orangeThreshold = totalGameTime / 4;     // 25% or more remaining = orange
                                                                     // Less than 25% = red
                        
                        // Format minutes and seconds
                        int minutes = timeRemaining / 60;
                        int seconds = timeRemaining % 60;
                        String formattedTime = String.format("%02d:%02d", minutes, seconds);
                        
                        // Apply color based on remaining time
                        if (timeRemaining >= greenThreshold) {
                            return "§a" + formattedTime; // Green
                        } else if (timeRemaining >= yellowThreshold) {
                            return "§e" + formattedTime; // Yellow
                        } else if (timeRemaining >= orangeThreshold) {
                            return "§6" + formattedTime; // Gold/Orange
                        } else {
                            return "§c" + formattedTime; // Red
                        }
                    case "game_time_stripped":
                        // Get the remaining time in seconds (without color)
                        int remainingTime = game.getTimeRemaining();
                        
                        // Format minutes and seconds
                        int mins = remainingTime / 60;
                        int secs = remainingTime % 60;
                        return String.format("%02d:%02d", mins, secs);
                    case "game_time": // Keep the original placeholder for backward compatibility
                        // Redirect to the colored version
                        return onPlaceholderRequest(player, "game_time_colored");
                    case "game_players":
                        return String.valueOf(game.getPlayerCount());
                    case "game_max_players":
                        return String.valueOf(game.getArena().getSpawnPoints().size());
                    case "game_arena":
                        return game.getArena().getName();
                    case "game_pvp_enabled":
                        return String.valueOf(game.isPvpEnabled());
                    case "game_is_grace_period":
                        return String.valueOf(game.isGracePeriod());
                    case "game_kills":
                        return String.valueOf(game.getPlayerKills(player.getUniqueId()));
                }
            }
            
            // General player placeholders
            switch (identifier) {
                case "in_game":
                    return String.valueOf(game != null);
            }
        }
        
        // Global placeholders (not player-specific)
        switch (identifier) {
            case "total_games":
                return String.valueOf(plugin.getGameManager().getActiveGameCount());
            case "total_arenas":
                return String.valueOf(plugin.getArenaManager().getArenas().size());
        }
        
        return null; // Placeholder not found
    }
} 