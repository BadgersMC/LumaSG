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
        // Handle global placeholders first (not player-specific)
        String globalValue = handleGlobalPlaceholder(identifier);
        if (globalValue != null) return globalValue;
        
        // Handle player-specific placeholders
        if (player != null) {
            // Handle general player placeholders
            String playerValue = handlePlayerPlaceholder(player, identifier);
            if (playerValue != null) return playerValue;
            
            // Handle game-specific placeholders if player is in a game
            Game game = plugin.getGameManager().getGameByPlayer(player);
            if (game != null) {
                String gameValue = handleGamePlaceholder(game, player, identifier);
                if (gameValue != null) return gameValue;
            }
        }
        
        return null; // Placeholder not found
    }
    
    /**
     * Handles global placeholders that don't require a player context
     */
    private @Nullable String handleGlobalPlaceholder(@NotNull String identifier) {
        return switch (identifier) {
            case "total_games" -> String.valueOf(plugin.getGameManager().getActiveGameCount());
            case "total_arenas" -> String.valueOf(plugin.getArenaManager().getArenas().size());
            default -> null;
        };
    }
    
    /**
     * Handles player-specific placeholders that don't require a game context
     */
    private @Nullable String handlePlayerPlaceholder(@NotNull Player player, @NotNull String identifier) {
        Game game = plugin.getGameManager().getGameByPlayer(player);
        return switch (identifier) {
            case "in_game" -> String.valueOf(game != null);
            default -> null;
        };
    }
    
    /**
     * Handles game-specific placeholders
     */
    private @Nullable String handleGamePlaceholder(@NotNull Game game, @NotNull Player player, @NotNull String identifier) {
        return switch (identifier) {
            case "game_state" -> game.getState().name();
            case "game_time_colored" -> formatGameTimeColored(game);
            case "game_time_stripped" -> formatGameTime(game);
            case "game_time" -> formatGameTimeColored(game); // Backward compatibility
            case "game_players" -> String.valueOf(game.getPlayerCount());
            case "game_max_players" -> String.valueOf(game.getArena().getSpawnPoints().size());
            case "game_arena" -> game.getArena().getName();
            case "game_pvp_enabled" -> String.valueOf(game.isPvpEnabled());
            case "game_is_grace_period" -> String.valueOf(game.isGracePeriod());
            case "game_kills" -> String.valueOf(game.getPlayerKills(player.getUniqueId()));
            default -> null;
        };
    }
    
    /**
     * Formats the game time with color based on remaining time
     */
    private @NotNull String formatGameTimeColored(@NotNull Game game) {
        int timeRemaining = game.getTimeRemaining();
        String formattedTime = formatGameTime(game);
        
        // Calculate color thresholds (divide total time into 4 segments)
        int totalGameTime = plugin.getConfig().getInt("game.game-time-minutes", 20) * 60;
        int greenThreshold = totalGameTime * 3 / 4;   // 75% or more remaining = green
        int yellowThreshold = totalGameTime / 2;      // 50% or more remaining = yellow
        int orangeThreshold = totalGameTime / 4;      // 25% or more remaining = orange
                                                     // Less than 25% = red
        
        // Apply color based on remaining time
        if (timeRemaining >= greenThreshold) return "§a" + formattedTime;      // Green
        if (timeRemaining >= yellowThreshold) return "§e" + formattedTime;     // Yellow
        if (timeRemaining >= orangeThreshold) return "§6" + formattedTime;     // Gold/Orange
        return "§c" + formattedTime;                                           // Red
    }
    
    /**
     * Formats the game time without color
     */
    private @NotNull String formatGameTime(@NotNull Game game) {
        int timeRemaining = game.getTimeRemaining();
        int minutes = timeRemaining / 60;
        int seconds = timeRemaining % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }
} 