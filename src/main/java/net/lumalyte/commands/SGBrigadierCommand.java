package net.lumalyte.commands;

import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.lumalyte.LumaSG;
import net.lumalyte.arena.Arena;
import net.lumalyte.arena.ArenaManager;
import net.lumalyte.game.Game;
import net.lumalyte.game.GameCelebrationManager;
import net.lumalyte.game.GameManager;
import net.lumalyte.game.GamePlayerManager;
import net.lumalyte.game.GameState;
import net.lumalyte.gui.MainMenu;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.lumalyte.util.DebugLogger;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * Modern command handler for Survival Games plugin commands using Paper's Brigadier command system.
 * 
 * <p>This class provides a comprehensive command interface for the Survival Games
 * plugin, featuring modern error handling, rich text formatting, and extensive
 * tab completion support.</p>
 * 
 * @author LumaLyte
 * @version 1.0
 * @since 1.0
 */
public class SGBrigadierCommand extends Command {
    
    private final LumaSG plugin;
    private final GameManager gameManager;
    private final ArenaManager arenaManager;
    
    /** The debug logger instance for this command handler */
    private final DebugLogger.ContextualLogger logger;
    
    /**
     * Creates a new SG command handler.
     * 
     * @param plugin The plugin instance
     */
    public SGBrigadierCommand(LumaSG plugin) {
        super("sg");
        this.plugin = plugin;
        this.gameManager = plugin.getGameManager();
        this.arenaManager = plugin.getArenaManager();
        this.logger = plugin.getDebugLogger().forContext("SGBrigadierCommand");
    }
    
    /**
     * Registers the command with the server's command dispatcher.
     */
    public void register() {
        // Register the command with Paper's command system
        plugin.getServer().getCommandMap().register("lumasg", new Command("sg") {
            @Override
            public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {
                return handleCommand(sender, args);
            }
            
            @Override
            public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, @NotNull String[] args) {
                return getTabComplete(sender, args);
            }
        });
    }
    
    /**
     * Handles command execution.
     */
    private boolean handleCommand(CommandSender sender, String[] args) {
        if (args.length == 0) {
            return handleMenuCommand(createContext(sender)) == 1;
        }
        
        String subcommand = args[0].toLowerCase();
        CommandContext<CommandSender> context = createContext(sender, args);
        
        int result = executeSubcommand(subcommand, context, args);
        return result == 1;
    }
    
    /**
     * Executes the appropriate subcommand based on the command input.
     */
    private int executeSubcommand(String subcommand, CommandContext<CommandSender> context, String[] args) {
        return switch (subcommand) {
            case "join" -> handleJoinCommand(context);
            case "leave" -> handleLeaveCommand(context);
            case "start" -> handleStartCommand(context);
            case "stop" -> handleStopCommand(context);
            case "list" -> handleListCommand(context);
            case "info" -> handleInfoCommand(context);
            case "reload" -> handleReloadCommand(context);
            case "wand" -> handleWandCommand(context);
            case "create" -> handleCreateCommand(context);
            case "arena" -> handleArenaSubcommand(context, args);
            case "scan" -> handleScanSubcommand(context, args);
            case "menu" -> handleMenuCommand(context);
            case "test" -> handleTestCommand(context);
            case "help" -> handleHelpCommand(context);
            default -> handleHelpCommand(context);
        };
    }
    
    /**
     * Handles arena-related subcommands.
     */
    private int handleArenaSubcommand(CommandContext<CommandSender> context, String[] args) {
        if (args.length > 1) {
            if (args[1].equalsIgnoreCase("select")) {
                // Handle /sg arena select <arena>
                return handleArenaSelectCommand(context);
            } else {
                // Handle /sg arena <arena> (direct selection)
                return handleArenaDirectSelectCommand(context);
            }
        }
        return handleHelpCommand(context);
    }
    
    /**
     * Handles scan-related subcommands.
     */
    private int handleScanSubcommand(CommandContext<CommandSender> context, String[] args) {
        if (args.length > 1 && args[1].equalsIgnoreCase("chests")) {
            return handleScanChestsCommand(context);
        }
        return handleHelpCommand(context);
    }
    
    /**
     * Provides tab completion for commands.
     */
    private List<String> getTabComplete(CommandSender sender, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            return getFirstArgumentCompletions(sender, args[0]);
        } else if (args.length == 2) {
            return getSecondArgumentCompletions(args[0], args[1]);
        } else if (args.length == 3) {
            return getThirdArgumentCompletions(args[0], args[1], args[2]);
        }
        
        return completions;
    }
    
    /**
     * Gets tab completions for the first argument (main commands).
     */
    private List<String> getFirstArgumentCompletions(CommandSender sender, String partial) {
        List<String> completions = new ArrayList<>();
        String partialLower = partial.toLowerCase();
        
        List<String> commands = Arrays.asList("join", "leave", "start", "stop", "list", "info", "menu", "help");
        
        if (sender.hasPermission("lumasg.admin")) {
            commands = new ArrayList<>(commands);
            commands.addAll(Arrays.asList("reload", "wand", "create", "arena", "scan", "test"));
        }
        
        for (String command : commands) {
            if (command.startsWith(partialLower)) {
                completions.add(command);
            }
        }
        
        return completions;
    }
    
    /**
     * Gets tab completions for the second argument.
     */
    private List<String> getSecondArgumentCompletions(String subcommand, String partial) {
        List<String> completions = new ArrayList<>();
        String subcommandLower = subcommand.toLowerCase();
        String partialLower = partial.toLowerCase();
        
        if (subcommandLower.equals("join") || subcommandLower.equals("start")) {
            return getArenaNameCompletions(partialLower);
        } else if (subcommandLower.equals("arena")) {
            return getArenaSubcommandCompletions(partialLower);
        } else if (subcommandLower.equals("scan")) {
            return getScanSubcommandCompletions(partialLower);
        } else if (subcommandLower.equals("test")) {
            return getTestSubcommandCompletions(partialLower);
        }
        
        return completions;
    }
    
    /**
     * Gets tab completions for the third argument.
     */
    private List<String> getThirdArgumentCompletions(String subcommand, String action, String partial) {
        List<String> completions = new ArrayList<>();
        String subcommandLower = subcommand.toLowerCase();
        String actionLower = action.toLowerCase();
        String partialLower = partial.toLowerCase();
        
        if ((subcommandLower.equals("arena") && actionLower.equals("select")) ||
            (subcommandLower.equals("scan") && actionLower.equals("chests"))) {
            return getArenaNameCompletions(partialLower);
        }
        
        return completions;
    }
    
    /**
     * Gets arena name completions that match the partial input.
     */
    private List<String> getArenaNameCompletions(String partial) {
        List<String> completions = new ArrayList<>();
        for (Arena arena : arenaManager.getArenas()) {
            if (arena.getName().toLowerCase().startsWith(partial)) {
                completions.add(arena.getName());
            }
        }
        return completions;
    }
    
    /**
     * Gets arena subcommand completions.
     */
    private List<String> getArenaSubcommandCompletions(String partial) {
        List<String> completions = new ArrayList<>();
        
        // Tab complete "select" for arena command, and also arena names for direct selection
        if ("select".startsWith(partial)) {
            completions.add("select");
        }
        
        // Also allow direct arena selection: /sg arena <arena_name>
        completions.addAll(getArenaNameCompletions(partial));
        
        return completions;
    }
    
    /**
     * Gets scan subcommand completions.
     */
    private List<String> getScanSubcommandCompletions(String partial) {
        List<String> completions = new ArrayList<>();
        if ("chests".startsWith(partial)) {
            completions.add("chests");
        }
        return completions;
    }
    
    /**
     * Gets test subcommand completions.
     */
    private List<String> getTestSubcommandCompletions(String partial) {
        List<String> completions = new ArrayList<>();
        if ("celebration".startsWith(partial)) {
            completions.add("celebration");
        }
        return completions;
    }
    
    /**
     * Creates a command context for handling commands.
     */
    private CommandContext<CommandSender> createContext(CommandSender sender, String... args) {
        return new CommandContext<>(sender, args);
    }
    
    /**
     * Helper class to create a command context.
     */
    private static class CommandContext<T> {
        private final T source;
        private final java.util.Map<String, String> arguments;
        
        public CommandContext(T source, String... args) {
            this.source = source;
            this.arguments = new HashMap<>();
            
            // Parse arguments into a map for easy access
            if (args.length > 1) {
                String subcommand = args[0].toLowerCase();
                
                if (subcommand.equals("arena")) {
                    if (args.length > 2 && args[1].equalsIgnoreCase("select")) {
                        // /sg arena select <arena>
                        this.arguments.put("arena", args[2]);
                    } else if (args.length > 1) {
                        // /sg arena <arena> (direct selection)
                        this.arguments.put("direct_arena", args[1]);
                    }
                } else if (subcommand.equals("create") && args.length > 2) {
                    // /sg create <name> <radius>
                    this.arguments.put("name", args[1]);
                    this.arguments.put("radius", args[2]);
                } else if (subcommand.equals("scan") && args.length > 2 && args[1].equalsIgnoreCase("chests")) {
                    // /sg scan chests <arena>
                    this.arguments.put("arena", args[2]);
                } else {
                    // Default: /sg <subcommand> <arena>
                    this.arguments.put("arena", args[1]);
                }
            }
        }
        
        public T getSource() {
            return source;
        }
        
        public String getArgument(String name) {
            return arguments.get(name);
        }
    }
    
    /**
     * Gets a string argument from the context.
     */
    private static String getString(CommandContext<?> context, String name) {
        return context.getArgument(name);
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
        String arenaName = getString(context, "arena");
        
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
        String arenaName = getString(context, "arena");
        
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
        sender.sendMessage(Component.text("Available Arenas (" + arenaManager.getArenas().size() + "):")
            .color(NamedTextColor.GOLD));
        
        for (Arena arena : arenaManager.getArenas()) {
            sender.sendMessage(Component.text(" - " + arena.getName() + " (Spawn Points: " + arena.getSpawnPoints().size() + ")")
                .color(NamedTextColor.YELLOW));
        }
        
        // List active games
        sender.sendMessage(Component.text("Active Games (" + gameManager.getActiveGames().size() + "):")
            .color(NamedTextColor.GOLD));
        
        for (Game game : gameManager.getActiveGames()) {
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
            logger.severe("Error reloading configuration", e);
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
            logger.severe("Error giving admin wand", e);
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
        String name = getString(context, "name");
        String radiusStr = getString(context, "radius");
        
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
                player.sendMessage(Component.text("Found " + arena.getChestLocations().size() + " chests in the arena.")
                    .color(NamedTextColor.GREEN));
                return 1;
            } else {
                player.sendMessage(Component.text("Failed to create arena: " + name)
                    .color(NamedTextColor.RED));
                return 0;
            }
        } catch (Exception e) {
            logger.severe("Error creating arena", e);
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
        String arenaName = getString(context, "arena");
        
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
        return 1;
    }
    
    /**
     * Handles the direct arena selection command (/sg arena <arena>).
     */
    private int handleArenaDirectSelectCommand(CommandContext<CommandSender> context) {
        CommandSender sender = context.getSource();
        String arenaName = getString(context, "direct_arena");
        
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
        return 1;
    }
    
    /**
     * Handles the scan chests command.
     */
    private int handleScanChestsCommand(CommandContext<CommandSender> context) {
        CommandSender sender = context.getSource();
        String arenaName = getString(context, "arena");
        
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
     * Handles the test command for testing various features.
     */
    private int handleTestCommand(CommandContext<CommandSender> context) {
        CommandSender sender = context.getSource();
        String testType = getString(context, "arena"); // Reuse arena argument parsing for test type
        
        if (!sender.hasPermission("lumasg.admin")) {
            sender.sendMessage(Component.text("You don't have permission to use test commands.")
                .color(NamedTextColor.RED));
            return 0;
        }
        
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("This command can only be used by players.")
                .color(NamedTextColor.RED));
            return 0;
        }
        
        if (testType == null || testType.isEmpty()) {
            player.sendMessage(Component.text("Usage: /sg test <celebration>")
                .color(NamedTextColor.YELLOW));
            player.sendMessage(Component.text("Available tests:")
                .color(NamedTextColor.GRAY));
            player.sendMessage(Component.text("  celebration - Test the winner celebration pixel art")
                .color(NamedTextColor.GRAY));
            return 0;
        }
        
        switch (testType.toLowerCase()) {
            case "celebration" -> {
                // Test the celebration manager
                player.sendMessage(Component.text("Testing winner celebration with your player...")
                    .color(NamedTextColor.YELLOW));
                
                // Create a minimal test celebration manager that doesn't depend on full game infrastructure
                testWinnerCelebration(player);
                
                player.sendMessage(Component.text("Winner celebration test triggered! Check chat for pixel art.")
                    .color(NamedTextColor.GREEN));
                
                return 1;
            }
            default -> {
                player.sendMessage(Component.text("Unknown test type: " + testType)
                    .color(NamedTextColor.RED));
                player.sendMessage(Component.text("Available tests: celebration")
                    .color(NamedTextColor.GRAY));
                return 0;
            }
        }
    }
    
    /**
     * Test the winner celebration functionality without needing a full game.
     */
    private void testWinnerCelebration(Player player) {
        // Create a simple test celebration that bypasses the complex GamePlayerManager
        GameCelebrationManager celebrationManager = new GameCelebrationManager(plugin, new TestGamePlayerManager(player));
        celebrationManager.celebrateWinner(player);
    }
    
    /**
     * Simplified test implementation of GamePlayerManager for testing celebrations.
     */
    private class TestGamePlayerManager extends GamePlayerManager {
        private final Player testPlayer;
        private final Arena testArena;
        
        public TestGamePlayerManager(Player testPlayer) {
            super(plugin, createTestArena(testPlayer), UUID.randomUUID());
            this.testPlayer = testPlayer;
            this.testArena = createTestArena(testPlayer);
        }
        
        @Override
        public Set<UUID> getPlayers() {
            return Set.of(testPlayer.getUniqueId());
        }
        
        @Override
        public Set<UUID> getSpectators() {
            return Set.of(); // No spectators for test
        }
        
        @Override
        public Player getCachedPlayer(UUID playerId) {
            if (playerId.equals(testPlayer.getUniqueId())) {
                return testPlayer;
            }
            return null;
        }
        
        @Override
        public int getPlayerKills(UUID playerId) {
            return 5; // Mock kill count for testing
        }
        
        /**
         * Creates a minimal test arena for celebration testing.
         */
        private static Arena createTestArena(Player testPlayer) {
            try {
                // Get the plugin instance properly
                LumaSG plugin = (LumaSG) testPlayer.getServer().getPluginManager().getPlugin("LumaSG");
                if (plugin == null) {
                    throw new IllegalStateException("LumaSG plugin not found");
                }
                
                // Create a minimal arena with just the player's current location
                Location playerLoc = testPlayer.getLocation();
                Arena arena = new Arena("test-arena", plugin, 1, 1);
                arena.addSpawnPoint(playerLoc);
                return arena;
            } catch (Exception e) {
                // Fallback to a basic arena
                LumaSG plugin = (LumaSG) testPlayer.getServer().getPluginManager().getPlugin("LumaSG");
                return new Arena("test-arena", plugin);
            }
        }
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
    private void showHelp(@NotNull CommandSender sender) {
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
            sender.sendMessage(Component.text(" /sg scan chests <arena> - Scan for chests in an arena")
                .color(NamedTextColor.YELLOW));
            sender.sendMessage(Component.text(" /sg test <type> - Test various features (celebration)")
                .color(NamedTextColor.YELLOW));
        }
    }
    
    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {
        return handleCommand(sender, args);
    }
    
    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, @NotNull String[] args) throws IllegalArgumentException {
        return getTabComplete(sender, args);
    }
    
    /**
     * Gets the plugin instance.
     * 
     * @return The plugin instance
     */
    public LumaSG getPlugin() {
        return plugin;
    }
} 