package net.lumalyte.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.lumalyte.LumaSG;
import net.lumalyte.arena.Arena;
import net.lumalyte.arena.ArenaManager;
import net.lumalyte.game.Game;
import net.lumalyte.game.GameManager;
import net.lumalyte.game.GameState;
import net.lumalyte.gui.MainMenu;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * Modern command handler for Survival Games plugin commands using Paper's Brigadier command system.
 * 
 * <p>This class provides a comprehensive command interface for the Survival Games
 * plugin, featuring modern error handling, rich text formatting, and extensive
 * tab completion support.</p>
 * 
 * <p>The command supports various subcommands for different operations:
 * <ul>
 *   <li>join - Join a game in a specific arena</li>
 *   <li>leave - Leave the current game</li>
 *   <li>start - Start a game in an arena (admin only)</li>
 *   <li>stop - Stop the current game (admin only)</li>
 *   <li>list - List available arenas and games</li>
 *   <li>info - Show information about current game</li>
 *   <li>spectate - Spectate a game (spectator permission)</li>
 *   <li>reload - Reload plugin configuration (admin only)</li>
 *   <li>help - Show help information</li>
 * </ul></p>
 * 
 * @author LumaLyte
 * @version 1.0
 * @since 1.0
 */
public class SGCommand {
    
    private final LumaSG plugin;
    private final GameManager gameManager;
    private final ArenaManager arenaManager;
    
    /**
     * Creates a new SG command handler.
     * 
     * @param plugin The plugin instance
     */
    public SGCommand(LumaSG plugin) {
        this.plugin = plugin;
        this.gameManager = plugin.getGameManager();
        this.arenaManager = plugin.getArenaManager();
    }
    
    /**
     * Registers the command with the server's command dispatcher.
     * 
     * @param dispatcher The command dispatcher
     */
    public void register(CommandDispatcher<CommandSender> dispatcher) {
        // Main command
        LiteralArgumentBuilder<CommandSender> sgCommand = LiteralArgumentBuilder
            .<CommandSender>literal("sg")
            .requires(sender -> sender.hasPermission("lumasg.play"))
            .executes(this::handleMenuCommand);
        
        // Join command
        sgCommand.then(LiteralArgumentBuilder.<CommandSender>literal("join")
            .then(RequiredArgumentBuilder.<CommandSender, String>argument("arena", StringArgumentType.word())
                .suggests(this::suggestArenas)
                .executes(this::handleJoinCommand)));
        
        // Leave command
        sgCommand.then(LiteralArgumentBuilder.<CommandSender>literal("leave")
            .executes(this::handleLeaveCommand));
        
        // Start command
        sgCommand.then(LiteralArgumentBuilder.<CommandSender>literal("start")
            .requires(sender -> sender.hasPermission("lumasg.admin"))
            .then(RequiredArgumentBuilder.<CommandSender, String>argument("arena", StringArgumentType.word())
                .suggests(this::suggestArenas)
                .executes(this::handleStartCommand)));
        
        // Stop command
        sgCommand.then(LiteralArgumentBuilder.<CommandSender>literal("stop")
            .requires(sender -> sender.hasPermission("lumasg.admin"))
            .executes(this::handleStopCommand));
        
        // List command
        sgCommand.then(LiteralArgumentBuilder.<CommandSender>literal("list")
            .executes(this::handleListCommand));
        
        // Info command
        sgCommand.then(LiteralArgumentBuilder.<CommandSender>literal("info")
            .executes(this::handleInfoCommand));
        
        // Reload command
        sgCommand.then(LiteralArgumentBuilder.<CommandSender>literal("reload")
            .requires(sender -> sender.hasPermission("lumasg.admin"))
            .executes(this::handleReloadCommand));
        
        // Wand command
        sgCommand.then(LiteralArgumentBuilder.<CommandSender>literal("wand")
            .requires(sender -> sender.hasPermission("lumasg.admin"))
            .executes(this::handleWandCommand));
        
        // Create command
        sgCommand.then(LiteralArgumentBuilder.<CommandSender>literal("create")
            .requires(sender -> sender.hasPermission("lumasg.admin"))
            .then(RequiredArgumentBuilder.<CommandSender, String>argument("name", StringArgumentType.word())
                .then(RequiredArgumentBuilder.<CommandSender, String>argument("radius", StringArgumentType.word())
                    .executes(this::handleCreateCommand))));
        
        // Arena command
        sgCommand.then(LiteralArgumentBuilder.<CommandSender>literal("arena")
            .requires(sender -> sender.hasPermission("lumasg.admin"))
            .then(LiteralArgumentBuilder.<CommandSender>literal("select")
                .then(RequiredArgumentBuilder.<CommandSender, String>argument("arena", StringArgumentType.word())
                    .suggests(this::suggestArenas)
                    .executes(this::handleArenaSelectCommand))));
        
        // Scan chests command
        sgCommand.then(LiteralArgumentBuilder.<CommandSender>literal("scanchests")
            .requires(sender -> sender.hasPermission("lumasg.admin"))
            .then(RequiredArgumentBuilder.<CommandSender, String>argument("arena", StringArgumentType.word())
                .suggests(this::suggestArenas)
                .executes(this::handleScanChestsCommand)));
        
        // Menu command
        sgCommand.then(LiteralArgumentBuilder.<CommandSender>literal("menu")
            .executes(this::handleMenuCommand));
        
        // Help command
        sgCommand.then(LiteralArgumentBuilder.<CommandSender>literal("help")
            .executes(this::handleHelpCommand));
        
        // Register command
        dispatcher.register(sgCommand);
    }
    
    /**
     * Suggests arena names for tab completion.
     */
    private CompletableFuture<Suggestions> suggestArenas(CommandContext<CommandSender> context, SuggestionsBuilder builder) {
        String input = builder.getRemaining().toLowerCase();
        for (Arena arena : arenaManager.getArenas()) {
            if (arena.getName().toLowerCase().startsWith(input)) {
                builder.suggest(arena.getName());
            }
        }
        return builder.buildFuture();
    }
    
    /**
     * Handles the join command.
     */
    private int handleJoinCommand(CommandContext<CommandSender> context) {
        CommandSender sender = context.getSource();
        String arenaName = StringArgumentType.getString(context, "arena");
        
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("This command can only be used by players.")
                .color(NamedTextColor.RED));
            return 0;
        }
        
        // Check if player is already in a game
        if (gameManager.getGameByPlayer(player) != null) {
            player.sendMessage(Component.text("You are already in a game. Use /sg leave to leave first.")
                .color(NamedTextColor.RED));
            return 0;
        }
        
        // Find arena by name
        Arena arena = arenaManager.getArena(arenaName);
        if (arena == null) {
            player.sendMessage(Component.text("Arena not found: " + arenaName)
                .color(NamedTextColor.RED));
            return 0;
        }
        
        // Get or create game
        Game game = gameManager.getOrCreateGame(arena);
        if (game == null) {
            player.sendMessage(Component.text("Failed to create game for arena: " + arenaName)
                .color(NamedTextColor.RED));
            return 0;
        }
        
        // Add player to game
        game.addPlayer(player);
        return 1;
    }
    
    /**
     * Handles the leave command.
     */
    private int handleLeaveCommand(CommandContext<CommandSender> context) {
        CommandSender sender = context.getSource();
        
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("This command can only be used by players.")
                .color(NamedTextColor.RED));
            return 0;
        }
        
        // Check if player is in a game
        Game game = gameManager.getGameByPlayer(player);
        if (game == null) {
            player.sendMessage(Component.text("You are not in a game.")
                .color(NamedTextColor.RED));
            return 0;
        }
        
        // Remove player from game
        game.removePlayer(player, false);
        player.sendMessage(Component.text("You have left the game.")
            .color(NamedTextColor.GREEN));
        return 1;
    }
    
    /**
     * Handles the start command.
     */
    private int handleStartCommand(CommandContext<CommandSender> context) {
        CommandSender sender = context.getSource();
        String arenaName = StringArgumentType.getString(context, "arena");
        
        // Find arena by name
        Arena arena = arenaManager.getArena(arenaName);
        if (arena == null) {
            sender.sendMessage(Component.text("Arena not found: " + arenaName)
                .color(NamedTextColor.RED));
            return 0;
        }
        
        // Get or create game
        Game game = gameManager.getOrCreateGame(arena);
        if (game == null) {
            sender.sendMessage(Component.text("Failed to create game for arena: " + arenaName)
                .color(NamedTextColor.RED));
            return 0;
        }
        
        // Start countdown
        if (game.getState() == GameState.WAITING) {
            game.startCountdown();
            sender.sendMessage(Component.text("Game countdown started for arena: " + arenaName)
                .color(NamedTextColor.GREEN));
        } else {
            sender.sendMessage(Component.text("Game is already in progress.")
                .color(NamedTextColor.RED));
            return 0;
        }
        
        return 1;
    }
    
    /**
     * Handles the stop command.
     */
    private int handleStopCommand(CommandContext<CommandSender> context) {
        CommandSender sender = context.getSource();
        
        if (gameManager.getActiveGames().isEmpty()) {
            sender.sendMessage(Component.text("There are no active games to stop.")
                .color(NamedTextColor.RED));
            return 0;
        }
        
        // If sender is a player, check if they're in a game
        if (sender instanceof Player player) {
            Game playerGame = gameManager.getGameByPlayer(player);
            if (playerGame != null) {
                playerGame.endGame(null);
                sender.sendMessage(Component.text("Your game has been stopped.")
                    .color(NamedTextColor.GREEN));
                return 1;
            }
        }
        
        // If no specific game, stop all games
        for (Game game : new ArrayList<>(gameManager.getActiveGames())) {
            game.endGame(null);
        }
        
        sender.sendMessage(Component.text("All games have been stopped.")
            .color(NamedTextColor.GREEN));
        return 1;
    }
    
    /**
     * Handles the list command.
     */
    private int handleListCommand(CommandContext<CommandSender> context) {
        CommandSender sender = context.getSource();
        
        // List arenas
        List<Arena> arenas = arenaManager.getArenas();
        sender.sendMessage(Component.text("Available Arenas (" + arenas.size() + "):")
            .color(NamedTextColor.GOLD));
        
        for (Arena arena : arenas) {
            sender.sendMessage(Component.text(" - " + arena.getName() + " (Spawn Points: " + arena.getSpawnPoints().size() + ")")
                .color(NamedTextColor.YELLOW));
        }
        
        // List active games
        List<Game> games = gameManager.getActiveGames();
        sender.sendMessage(Component.text("Active Games (" + games.size() + "):")
            .color(NamedTextColor.GOLD));
        
        for (Game game : games) {
            sender.sendMessage(Component.text(" - " + game.getArena().getName() + " (" + game.getState() + ", Players: " + game.getPlayers().size() + ")")
                .color(NamedTextColor.YELLOW));
        }
        
        return 1;
    }
    
    /**
     * Handles the info command.
     */
    private int handleInfoCommand(CommandContext<CommandSender> context) {
        CommandSender sender = context.getSource();
        
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("This command can only be used by players.")
                .color(NamedTextColor.RED));
            return 0;
        }
        
        // Check if player is in a game
        Game game = gameManager.getGameByPlayer(player);
        if (game == null) {
            player.sendMessage(Component.text("You are not in a game.")
                .color(NamedTextColor.RED));
            return 0;
        }
        
        // Show game info
        player.sendMessage(Component.text("Game Information:")
            .color(NamedTextColor.GOLD));
        player.sendMessage(Component.text(" - Arena: " + game.getArena().getName())
            .color(NamedTextColor.YELLOW));
        player.sendMessage(Component.text(" - State: " + game.getState())
            .color(NamedTextColor.YELLOW));
        player.sendMessage(Component.text(" - Players: " + game.getPlayers().size())
            .color(NamedTextColor.YELLOW));
        player.sendMessage(Component.text(" - Time Remaining: " + formatTime(game.getTimeRemaining()))
            .color(NamedTextColor.YELLOW));
        
        return 1;
    }
    
    /**
     * Formats time in seconds to a readable string.
     */
    private String formatTime(int seconds) {
        int minutes = seconds / 60;
        int remainingSeconds = seconds % 60;
        return String.format("%d:%02d", minutes, remainingSeconds);
    }
    
    /**
     * Handles the reload command.
     */
    private int handleReloadCommand(CommandContext<CommandSender> context) {
        CommandSender sender = context.getSource();
        
        try {
            plugin.reload();
            sender.sendMessage(Component.text("LumaSG configuration reloaded successfully.")
                .color(NamedTextColor.GREEN));
            return 1;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error reloading configuration", e);
            sender.sendMessage(Component.text("Error reloading configuration: " + e.getMessage())
                .color(NamedTextColor.RED));
            return 0;
        }
    }
    
    /**
     * Handles the wand command.
     */
    private int handleWandCommand(CommandContext<CommandSender> context) {
        CommandSender sender = context.getSource();
        
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("This command can only be used by players.")
                .color(NamedTextColor.RED));
            return 0;
        }
        
        try {
            // Give admin wand to player
            plugin.getAdminWandListener().giveWand(player);
            player.sendMessage(Component.text("You have received the admin wand.")
                .color(NamedTextColor.GREEN));
            return 1;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error giving admin wand", e);
            player.sendMessage(Component.text("Error giving admin wand: " + e.getMessage())
                .color(NamedTextColor.RED));
            return 0;
        }
    }
    
    /**
     * Handles the create command.
     */
    private int handleCreateCommand(CommandContext<CommandSender> context) {
        CommandSender sender = context.getSource();
        String name = StringArgumentType.getString(context, "name");
        String radiusStr = StringArgumentType.getString(context, "radius");
        
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("This command can only be used by players.")
                .color(NamedTextColor.RED));
            return 0;
        }
        
        // Parse radius
        int radius;
        try {
            radius = Integer.parseInt(radiusStr);
        } catch (NumberFormatException e) {
            player.sendMessage(Component.text("Invalid radius: " + radiusStr)
                .color(NamedTextColor.RED));
            return 0;
        }
        
        // Create arena
        try {
            Arena arena = arenaManager.createArena(name, player.getLocation(), radius);
            if (arena != null) {
                player.sendMessage(Component.text("Arena created: " + name)
                    .color(NamedTextColor.GREEN));
                return 1;
            } else {
                player.sendMessage(Component.text("Failed to create arena: " + name)
                    .color(NamedTextColor.RED));
                return 0;
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error creating arena", e);
            player.sendMessage(Component.text("Error creating arena: " + e.getMessage())
                .color(NamedTextColor.RED));
            return 0;
        }
    }
    
    /**
     * Handles the arena select command.
     */
    private int handleArenaSelectCommand(CommandContext<CommandSender> context) {
        CommandSender sender = context.getSource();
        String arenaName = StringArgumentType.getString(context, "arena");
        
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("This command can only be used by players.")
                .color(NamedTextColor.RED));
            return 0;
        }
        
        // Find arena by name
        Arena arena = arenaManager.getArena(arenaName);
        if (arena == null) {
            player.sendMessage(Component.text("Arena not found: " + arenaName)
                .color(NamedTextColor.RED));
            return 0;
        }
        
        // Select arena
        plugin.getAdminWandListener().setSelectedArena(player, arena);
        player.sendMessage(Component.text("Selected arena: " + arenaName)
            .color(NamedTextColor.GREEN));
        return 1;
    }
    
    /**
     * Handles the scan chests command.
     */
    private int handleScanChestsCommand(CommandContext<CommandSender> context) {
        CommandSender sender = context.getSource();
        String arenaName = StringArgumentType.getString(context, "arena");
        
        // Find arena by name
        Arena arena = arenaManager.getArena(arenaName);
        if (arena == null) {
            sender.sendMessage(Component.text("Arena not found: " + arenaName)
                .color(NamedTextColor.RED));
            return 0;
        }
        
        // Scan for chests
        int chestCount = arena.scanForChests();
        sender.sendMessage(Component.text("Found " + chestCount + " chests in arena " + arenaName)
            .color(NamedTextColor.GREEN));
        
        // Save arena
        arenaManager.saveArenas();
        sender.sendMessage(Component.text("Saved chest locations for arena " + arenaName)
            .color(NamedTextColor.GREEN));
        
        return 1;
    }
    
    /**
     * Handles the menu command.
     */
    private int handleMenuCommand(CommandContext<CommandSender> context) {
        CommandSender sender = context.getSource();
        
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("This command can only be used by players.")
                .color(NamedTextColor.RED));
            return 0;
        }
        
        // Open main menu
        new MainMenu(plugin).show(player);
        return 1;
    }
    
    /**
     * Handles the help command.
     */
    private int handleHelpCommand(CommandContext<CommandSender> context) {
        CommandSender sender = context.getSource();
        showHelp(sender);
        return 1;
    }
    
    /**
     * Shows help information to the sender.
     */
    private void showHelp(CommandSender sender) {
        sender.sendMessage(Component.text("LumaSG Commands:")
            .color(NamedTextColor.GOLD));
        
        // Basic commands
        sender.sendMessage(Component.text(" /sg join <arena> - Join a game")
            .color(NamedTextColor.YELLOW));
        sender.sendMessage(Component.text(" /sg leave - Leave the current game")
            .color(NamedTextColor.YELLOW));
        sender.sendMessage(Component.text(" /sg list - List available arenas and games")
            .color(NamedTextColor.YELLOW));
        sender.sendMessage(Component.text(" /sg info - Show information about current game")
            .color(NamedTextColor.YELLOW));
        sender.sendMessage(Component.text(" /sg menu - Open the main menu")
            .color(NamedTextColor.YELLOW));
        
        // Admin commands
        if (sender.hasPermission("lumasg.admin")) {
            sender.sendMessage(Component.text("Admin Commands:")
                .color(NamedTextColor.GOLD));
            sender.sendMessage(Component.text(" /sg start <arena> - Start a game")
                .color(NamedTextColor.YELLOW));
            sender.sendMessage(Component.text(" /sg stop - Stop the current game")
                .color(NamedTextColor.YELLOW));
            sender.sendMessage(Component.text(" /sg reload - Reload plugin configuration")
                .color(NamedTextColor.YELLOW));
            sender.sendMessage(Component.text(" /sg wand - Get the admin wand")
                .color(NamedTextColor.YELLOW));
            sender.sendMessage(Component.text(" /sg create <name> <radius> - Create a new arena")
                .color(NamedTextColor.YELLOW));
            sender.sendMessage(Component.text(" /sg arena select <arena> - Select an arena for editing")
                .color(NamedTextColor.YELLOW));
            sender.sendMessage(Component.text(" /sg scanchests <arena> - Scan for chests in an arena")
                .color(NamedTextColor.YELLOW));
        }
    }
} 
