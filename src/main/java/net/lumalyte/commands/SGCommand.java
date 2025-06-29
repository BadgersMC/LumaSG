package net.lumalyte.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.lumalyte.LumaSG;
import net.lumalyte.arena.Arena;
import net.lumalyte.arena.ArenaManager;
import net.lumalyte.game.Game;
import net.lumalyte.game.GameManager;
import net.lumalyte.game.GameState;
import net.lumalyte.gui.MainMenu;
import net.lumalyte.util.DebugLogger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.concurrent.CompletableFuture;
import java.util.Collection;
import java.util.UUID;
import net.lumalyte.customitems.behaviors.AirdropBehavior;
import net.lumalyte.customitems.behaviors.AirdropData;
import net.lumalyte.customitems.CustomItemsManager;
import net.lumalyte.listeners.CustomItemListener;
import net.lumalyte.util.MiniMessageUtils;

/**
 * Modern command handler for Survival Games plugin commands using Paper's Brigadier command system.
 * 
 * <p>This class provides a comprehensive command interface for the Survival Games
 * plugin, featuring modern error handling, rich text formatting, extensive
 * tab completion support, and full console support for game management.</p>
 * 
 * <p>The command supports various subcommands for different operations:
 * <ul>
 *   <li>join - Join a game in a specific arena (player only)</li>
 *   <li>leave - Leave the current game (player only)</li>
 *   <li>start - Start a game in an arena (admin only, supports console)</li>
 *   <li>stop - Stop a game in an arena (admin only, supports console)</li>
 *   <li>addplayer - Add a player to a game (admin only, supports console)</li>
 *   <li>removeplayer - Remove a player from a game (admin only, supports console)</li>
 *   <li>forcestart - Force start a game regardless of player count (admin only, supports console)</li>
 *   <li>list - List available arenas and games (supports console)</li>
 *   <li>info - Show information about current game or specific arena (supports console)</li>
 *   <li>spectate - Spectate a game (spectator permission, player only)</li>
 *   <li>reload - Reload plugin configuration (admin only, supports console)</li>
 *   <li>help - Show help information (supports console)</li>
 * </ul></p>
 * 
 * <p>Console commands are designed for external integrations and automated systems.
 * They provide detailed feedback and support arena-specific operations.</p>
 * 
 * @author LumaLyte
 * @version 1.0
 * @since 1.0
 */
public class SGCommand {
    
    private LumaSG plugin;
    private GameManager gameManager;
    private ArenaManager arenaManager;
    
    /** The debug logger instance for this command handler */
    private DebugLogger.ContextualLogger logger;
    
    /**
     * Creates a new SG command handler.
     * 
     * @param plugin The plugin instance (can be null during bootstrap)
     */
    public SGCommand(LumaSG plugin) {
        this.plugin = plugin;
        // We'll initialize these later when needed
        this.gameManager = null;
        this.arenaManager = null;
        this.logger = null;
    }
    
    /**
     * Gets the plugin instance, initializing managers if needed.
     * This is called lazily when needed rather than during construction.
     */
    private LumaSG getPlugin() {
        if (plugin == null) {
            plugin = JavaPlugin.getPlugin(LumaSG.class);
            initializeManagers();
        }
        return plugin;
    }
    
    /**
     * Initialize managers after we have a valid plugin instance.
     */
    private void initializeManagers() {
        if (gameManager == null) {
            gameManager = getPlugin().getGameManager();
        }
        if (arenaManager == null) {
            arenaManager = getPlugin().getArenaManager();
        }
        if (logger == null) {
            logger = getPlugin().getDebugLogger().forContext("SGCommand");
        }
    }
    
    /**
     * Creates the command node for registration.
     * This follows Paper's recommended pattern of building the command tree.
     * 
     * @return The built command node ready for registration
     */
    public LiteralCommandNode<CommandSourceStack> createCommandNode() {
        return createCommandBuilder().build();
    }
    
    /**
     * Creates the command builder for registration with dispatcher.
     * 
     * @return The command builder ready for registration
     */
    public LiteralArgumentBuilder<CommandSourceStack> createCommandBuilder() {
        return Commands.literal("sg")
            .executes(this::openMenuOrHelp) // Default behavior: open menu for players, help for console
            .then(Commands.literal("join")
                .requires(source -> source.getSender().hasPermission("lumasg.command.sg.join"))
                .then(Commands.argument("arena", StringArgumentType.word())
                    .suggests(this::suggestArenas)
                    .executes(this::joinGame)))
            .then(Commands.literal("leave")
                .requires(source -> source.getSender().hasPermission("lumasg.command.sg.leave"))
                .executes(this::leaveGame))
            .then(Commands.literal("start")
                .requires(source -> source.getSender().hasPermission("lumasg.command.sg.start"))
                .then(Commands.argument("arena", StringArgumentType.word())
                    .suggests(this::suggestArenas)
                    .executes(this::startGameInArena))
                .executes(this::startGame))
            .then(Commands.literal("stop")
                .requires(source -> source.getSender().hasPermission("lumasg.command.sg.stop"))
                .then(Commands.argument("arena", StringArgumentType.word())
                    .suggests(this::suggestActiveArenas)
                    .executes(this::stopGameInArena))
                .executes(this::stopGame))
            .then(Commands.literal("addplayer")
                .requires(source -> source.getSender().hasPermission("lumasg.command.sg.admin"))
                .then(Commands.argument("player", StringArgumentType.word())
                    .suggests(this::suggestOnlinePlayers)
                    .then(Commands.argument("arena", StringArgumentType.word())
                        .suggests(this::suggestArenas)
                        .executes(this::addPlayerToGame))))
            .then(Commands.literal("removeplayer")
                .requires(source -> source.getSender().hasPermission("lumasg.command.sg.admin"))
                .then(Commands.argument("player", StringArgumentType.word())
                    .suggests(this::suggestPlayersInGame)
                    .executes(this::removePlayerFromGame)))
            .then(Commands.literal("forcestart")
                .requires(source -> source.getSender().hasPermission("lumasg.command.sg.admin"))
                .then(Commands.argument("arena", StringArgumentType.word())
                    .suggests(this::suggestWaitingArenas)
                    .executes(this::forceStartGame)))
            .then(Commands.literal("list")
                .executes(this::listGames))
            .then(Commands.literal("info")
                .then(Commands.argument("arena", StringArgumentType.word())
                    .suggests(this::suggestArenas)
                    .executes(this::showArenaInfo))
                .executes(this::showCurrentGameInfo))
            .then(Commands.literal("reload")
                .requires(source -> source.getSender().hasPermission("lumasg.command.sg.reload"))
                .executes(this::reloadPlugin))
            .then(Commands.literal("menu")
                .executes(this::openMenu))
            .then(Commands.literal("wand")
                .requires(source -> source.getSender().hasPermission("lumasg.admin"))
                .executes(this::giveWand))
            .then(Commands.literal("help")
                .executes(this::showHelp))
            .then(Commands.literal("debug")
                .requires(source -> source.getSender().hasPermission("lumasg.command.sg.debug"))
                .then(Commands.literal("skippvp")
                    .executes(this::debugSkipPvP))
                .then(Commands.literal("kingdomsx")
                    .executes(this::debugKingdomsX))
                .then(Commands.literal("meteor")
                    .executes(this::debugSpawnMeteor)))
            .then(Commands.literal("create")
                .requires(source -> source.getSender().hasPermission("lumasg.command.sg.admin"))
                .then(Commands.argument("name", StringArgumentType.word())
                    .then(Commands.argument("radius", IntegerArgumentType.integer(10, 1000))
                        .executes(this::createArena))))
            .then(Commands.literal("arena")
                .requires(source -> source.getSender().hasPermission("lumasg.command.sg.admin"))
                .then(Commands.literal("select")
                    .then(Commands.argument("arena", StringArgumentType.word())
                        .suggests(this::suggestArenas)
                        .executes(this::selectArena))));
    }
    
    /**
     * Registers the command with the server's command dispatcher.
     * 
     * @param dispatcher The command dispatcher
     * @deprecated Use createCommandNode() instead for proper Paper integration
     */
    @Deprecated
    public void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(createCommandBuilder());
    }
    
    /**
     * Suggests arena names for tab completion.
     */
    private CompletableFuture<Suggestions> suggestArenas(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        // Initialize managers if needed
        initializeManagers();
        
        String input = builder.getRemaining().toLowerCase();
        for (Arena arena : arenaManager.getArenas()) {
            String name = arena.getName().toLowerCase();
            if (name.startsWith(input)) {
                builder.suggest(name);
            }
        }
        return builder.buildFuture();
    }
    
    /**
     * Suggests active arena names for tab completion.
     */
    private CompletableFuture<Suggestions> suggestActiveArenas(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        // Initialize managers if needed
        initializeManagers();
        
        String input = builder.getRemaining().toLowerCase();
        for (Game game : gameManager.getActiveGames()) {
            String name = game.getArena().getName().toLowerCase();
            if (name.startsWith(input)) {
                builder.suggest(name);
            }
        }
        return builder.buildFuture();
    }
    
    /**
     * Suggests arena names that have games in waiting state for tab completion.
     */
    private CompletableFuture<Suggestions> suggestWaitingArenas(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        // Initialize managers if needed
        initializeManagers();
        
        String input = builder.getRemaining().toLowerCase();
        for (Game game : gameManager.getActiveGames()) {
            if (game.getState() == GameState.WAITING) {
                String name = game.getArena().getName().toLowerCase();
                if (name.startsWith(input)) {
                    builder.suggest(name);
                }
            }
        }
        return builder.buildFuture();
    }
    
    /**
     * Suggests online player names for tab completion.
     */
    private CompletableFuture<Suggestions> suggestOnlinePlayers(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        String input = builder.getRemaining().toLowerCase();
        for (Player player : Bukkit.getOnlinePlayers()) {
            String name = player.getName().toLowerCase();
            if (name.startsWith(input)) {
                builder.suggest(name);
            }
        }
        return builder.buildFuture();
    }
    
    /**
     * Suggests players currently in games for tab completion.
     */
    private CompletableFuture<Suggestions> suggestPlayersInGame(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        // Initialize managers if needed
        initializeManagers();
        
        String input = builder.getRemaining().toLowerCase();
        for (Game game : gameManager.getActiveGames()) {
            for (UUID playerId : game.getPlayers()) {
                Player player = Bukkit.getPlayer(playerId);
                if (player != null) {
                    String name = player.getName().toLowerCase();
                    if (name.startsWith(input)) {
                        builder.suggest(name);
                    }
                }
            }
        }
        return builder.buildFuture();
    }
    
    /**
     * Handles the join command.
     */
    private int joinGame(CommandContext<CommandSourceStack> context) {
        // Initialize managers if needed
        initializeManagers();
        
        CommandSender sender = context.getSource().getSender();
        if (!(sender instanceof Player player)) {
            context.getSource().getSender().sendMessage(Component.text("Only players can use this command!", NamedTextColor.RED));
            return 0;
        }

        String arenaName = StringArgumentType.getString(context, "arena");
        Arena arena = arenaManager.getArena(arenaName);

        if (arena == null) {
            player.sendMessage(Component.text("Arena not found!", NamedTextColor.RED));
            return 0;
        }

        Game game = gameManager.getGameByArena(arena);
        if (game == null) {
            game = gameManager.createGame(arena);
        }

        if (game == null) {
            player.sendMessage(Component.text("Failed to create game!", NamedTextColor.RED));
            return 0;
        }

        if (game.getState() != GameState.WAITING) {
            player.sendMessage(Component.text("Game is already in progress!", NamedTextColor.RED));
            return 0;
        }

        if (gameManager.isPlayerInGame(player)) {
            player.sendMessage(Component.text("You are already in a game!", NamedTextColor.RED));
            return 0;
        }

        game.addPlayer(player);
        player.sendMessage(Component.text("You joined the game!", NamedTextColor.GREEN));
        return 1;
    }
    
    /**
     * Handles the leave command.
     */
    private int leaveGame(CommandContext<CommandSourceStack> context) {
        // Initialize managers if needed
        initializeManagers();
        
        CommandSender sender = context.getSource().getSender();
        if (!(sender instanceof Player player)) {
            context.getSource().getSender().sendMessage(Component.text("Only players can use this command!", NamedTextColor.RED));
            return 0;
        }

        Game game = gameManager.getGameByPlayer(player);
        if (game == null) {
            player.sendMessage(Component.text("You are not in a game!", NamedTextColor.RED));
            return 0;
        }

        game.removePlayer(player, false);
        player.sendMessage(Component.text("You left the game!", NamedTextColor.GREEN));
        return 1;
    }
    
    /**
     * Handles the start command.
     */
    private int startGame(CommandContext<CommandSourceStack> context) {
        // Initialize managers if needed
        initializeManagers();
        
        CommandSender sender = context.getSource().getSender();
        if (!(sender instanceof Player player)) {
            context.getSource().getSender().sendMessage(Component.text("Only players can use this command!", NamedTextColor.RED));
            return 0;
        }

        Game game = gameManager.getGameByPlayer(player);
        if (game == null) {
            player.sendMessage(Component.text("You are not in a game!", NamedTextColor.RED));
            return 0;
        }

        if (game.getState() != GameState.WAITING) {
            player.sendMessage(Component.text("Game is already in progress!", NamedTextColor.RED));
            return 0;
        }

        game.startCountdown();
        return 1;
    }
    
    /**
     * Handles the stop command.
     */
    private int stopGame(CommandContext<CommandSourceStack> context) {
        // Initialize managers if needed
        initializeManagers();
        
        CommandSender sender = context.getSource().getSender();
        if (!(sender instanceof Player player)) {
            context.getSource().getSender().sendMessage(Component.text("Only players can use this command!", NamedTextColor.RED));
            return 0;
        }

        Game game = gameManager.getGameByPlayer(player);
        if (game == null) {
            player.sendMessage(Component.text("You are not in a game!", NamedTextColor.RED));
            return 0;
        }

        if (game.getState() == GameState.WAITING) {
            player.sendMessage(Component.text("Game has not started yet!", NamedTextColor.RED));
            return 0;
        }

        game.endGame(null);
        return 1;
    }
    
    /**
     * Handles the menu command.
     */
    private int openMenu(CommandContext<CommandSourceStack> context) {
        // Initialize managers if needed
        initializeManagers();
        
        CommandSender sender = context.getSource().getSender();
        if (!(sender instanceof Player player)) {
            context.getSource().getSender().sendMessage(Component.text("Only players can use this command!", NamedTextColor.RED));
            return 0;
        }

        new MainMenu(getPlugin()).show(player);
        return 1;
    }

    /**
     * Handles the debug skip PvP command.
     */
    private int debugSkipPvP(CommandContext<CommandSourceStack> context) {
        // Initialize managers if needed
        initializeManagers();
        
        CommandSender sender = context.getSource().getSender();
        if (!(sender instanceof Player player)) {
            context.getSource().getSender().sendMessage(Component.text("Only players can use this command!", NamedTextColor.RED));
            return 0;
        }

        Game game = gameManager.getGameByPlayer(player);
        if (game == null) {
            player.sendMessage(Component.text("You are not in a game!", NamedTextColor.RED));
            return 0;
        }

        // Skip grace period and enable PvP immediately
        game.skipGracePeriod();
        player.sendMessage(Component.text("Grace period skipped! PvP is now enabled.", NamedTextColor.GREEN));
        return 1;
    }
    
    /**
     * Handles the debug KingdomsX command - tests KingdomsX integration.
     */
    private int debugKingdomsX(CommandContext<CommandSourceStack> context) {
        // Initialize managers if needed
        initializeManagers();
        
        CommandSender sender = context.getSource().getSender();
        if (!(sender instanceof Player player)) {
            context.getSource().getSender().sendMessage(Component.text("Only players can use this command!", NamedTextColor.RED));
            return 0;
        }

        // Test KingdomsX hook integration
        boolean kingdomsXAvailable = getPlugin().getHookManager().isHookAvailable("KingdomsX");
        
        player.sendMessage(Component.text("=== KingdomsX Debug Information ===", NamedTextColor.GOLD));
        player.sendMessage(Component.text("KingdomsX Plugin: " + (isKingdomsXInstalled() ? "✅ Installed" : "❌ Not Found"), 
            isKingdomsXInstalled() ? NamedTextColor.GREEN : NamedTextColor.RED));
        player.sendMessage(Component.text("KingdomsX Hook: " + (kingdomsXAvailable ? "✅ Active" : "❌ Inactive"), 
            kingdomsXAvailable ? NamedTextColor.GREEN : NamedTextColor.RED));
        
        if (kingdomsXAvailable) {
            // Test KingdomsX API access
            testKingdomsXAPI(player);
        } else {
            player.sendMessage(Component.text("Install KingdomsX to test integration!", NamedTextColor.YELLOW));
        }
        
        // Test current game status
        Game game = gameManager.getGameByPlayer(player);
        if (game != null) {
            player.sendMessage(Component.text("Current Game: ✅ In game (" + game.getArena().getName() + ")", NamedTextColor.GREEN));
            player.sendMessage(Component.text("Game State: " + game.getState(), NamedTextColor.YELLOW));
            player.sendMessage(Component.text("PvP Enabled: " + (game.isPvpEnabled() ? "✅ Yes" : "❌ No"), 
                game.isPvpEnabled() ? NamedTextColor.GREEN : NamedTextColor.RED));
        } else {
            player.sendMessage(Component.text("Current Game: ❌ Not in game", NamedTextColor.RED));
            player.sendMessage(Component.text("Join a game with /sg join <arena> to test PvP integration!", NamedTextColor.YELLOW));
        }
        
        return 1;
    }
    
    /**
     * Checks if KingdomsX plugin is installed.
     */
    private boolean isKingdomsXInstalled() {
        return getPlugin().getServer().getPluginManager().getPlugin("KingdomsX") != null;
    }
    
    /**
     * Tests KingdomsX API access using reflection (same as our hook).
     */
    private void testKingdomsXAPI(Player player) {
        try {
            // Test the same reflection calls our hook uses
            Class<?> kingdomPlayerClass = Class.forName("org.kingdoms.constants.player.KingdomPlayer");
            Class<?> kingdomClass = Class.forName("org.kingdoms.constants.group.Kingdom");
            
            // Test getting KingdomPlayer
            java.lang.reflect.Method getKingdomPlayerMethod = kingdomPlayerClass.getMethod("getKingdomPlayer", java.util.UUID.class);
            Object kingdomPlayer = getKingdomPlayerMethod.invoke(null, player.getUniqueId());
            
            player.sendMessage(Component.text("API Test Results:", NamedTextColor.AQUA));
            
            if (kingdomPlayer != null) {
                // Test PvP status
                java.lang.reflect.Method isPvpMethod = kingdomPlayerClass.getMethod("isPvp");
                boolean playerPvp = (Boolean) isPvpMethod.invoke(kingdomPlayer);
                
                // Test kingdom membership
                java.lang.reflect.Method getKingdomMethod = kingdomPlayerClass.getMethod("getKingdom");
                Object kingdom = getKingdomMethod.invoke(kingdomPlayer);
                
                player.sendMessage(Component.text("  Player PvP: " + (playerPvp ? "✅ Enabled" : "❌ Disabled"), 
                    playerPvp ? NamedTextColor.GREEN : NamedTextColor.RED));
                
                if (kingdom != null) {
                    // Test pacifist status
                    java.lang.reflect.Method isPacifistMethod = kingdomClass.getMethod("isPacifist");
                    boolean kingdomPacifist = (Boolean) isPacifistMethod.invoke(kingdom);
                    
                    player.sendMessage(Component.text("  Has Kingdom: ✅ Yes", NamedTextColor.GREEN));
                    player.sendMessage(Component.text("  Kingdom Pacifist: " + (kingdomPacifist ? "❌ Yes (blocks PvP)" : "✅ No"), 
                        kingdomPacifist ? NamedTextColor.RED : NamedTextColor.GREEN));
                } else {
                    player.sendMessage(Component.text("  Has Kingdom: ❌ No", NamedTextColor.YELLOW));
                }
                
                player.sendMessage(Component.text("  API Access: ✅ Working", NamedTextColor.GREEN));
                
            } else {
                player.sendMessage(Component.text("  KingdomPlayer: ❌ Not found", NamedTextColor.YELLOW));
                player.sendMessage(Component.text("  This is normal if you haven't used KingdomsX yet", NamedTextColor.GRAY));
            }
            
        } catch (Exception e) {
            player.sendMessage(Component.text("  API Access: ❌ Failed", NamedTextColor.RED));
            player.sendMessage(Component.text("  Error: " + e.getMessage(), NamedTextColor.GRAY));
            logger.warn("KingdomsX API test failed", e);
        }
    }

    /**
     * Handles the default command behavior - open menu for players, show help for console.
     */
    private int openMenuOrHelp(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        if (sender instanceof Player) {
            return openMenu(context);
        } else {
            return showHelp(context);
        }
    }

    /**
     * Handles starting a game in a specific arena (console-friendly).
     */
    private int startGameInArena(CommandContext<CommandSourceStack> context) {
        // Initialize managers if needed
        initializeManagers();
        
        CommandSender sender = context.getSource().getSender();
        String arenaName = StringArgumentType.getString(context, "arena");
        
        Arena arena = arenaManager.getArena(arenaName);
        if (arena == null) {
            sender.sendMessage(Component.text("Arena '" + arenaName + "' not found!", NamedTextColor.RED));
            return 0;
        }

        Game game = gameManager.getGameByArena(arena);
        if (game == null) {
            game = gameManager.createGame(arena);
            if (game == null) {
                sender.sendMessage(Component.text("Failed to create game in arena '" + arenaName + "'!", NamedTextColor.RED));
                return 0;
            }
        }

        if (game.getState() != GameState.WAITING) {
            sender.sendMessage(Component.text("Game in arena '" + arenaName + "' is already in progress! State: " + game.getState(), NamedTextColor.RED));
            return 0;
        }

        game.startCountdown();
        sender.sendMessage(Component.text("Started countdown for game in arena '" + arenaName + "'!", NamedTextColor.GREEN));
        logger.info("Game started in arena '" + arenaName + "' by " + sender.getName());
        return 1;
    }

    /**
     * Handles stopping a game in a specific arena (console-friendly).
     */
    private int stopGameInArena(CommandContext<CommandSourceStack> context) {
        // Initialize managers if needed
        initializeManagers();
        
        CommandSender sender = context.getSource().getSender();
        String arenaName = StringArgumentType.getString(context, "arena");
        
        Arena arena = arenaManager.getArena(arenaName);
        if (arena == null) {
            sender.sendMessage(Component.text("Arena '" + arenaName + "' not found!", NamedTextColor.RED));
            return 0;
        }

        Game game = gameManager.getGameByArena(arena);
        if (game == null) {
            sender.sendMessage(Component.text("No active game found in arena '" + arenaName + "'!", NamedTextColor.RED));
            return 0;
        }

        if (game.getState() == GameState.WAITING) {
            sender.sendMessage(Component.text("Game in arena '" + arenaName + "' has not started yet!", NamedTextColor.RED));
            return 0;
        }

        game.endGame(null);
        sender.sendMessage(Component.text("Stopped game in arena '" + arenaName + "'!", NamedTextColor.GREEN));
        logger.info("Game stopped in arena '" + arenaName + "' by " + sender.getName());
        return 1;
    }

    /**
     * Handles adding a player to a game (console-friendly).
     */
    private int addPlayerToGame(CommandContext<CommandSourceStack> context) {
        // Initialize managers if needed
        initializeManagers();
        
        CommandSender sender = context.getSource().getSender();
        String playerName = StringArgumentType.getString(context, "player");
        String arenaName = StringArgumentType.getString(context, "arena");
        
        Player player = Bukkit.getPlayer(playerName);
        if (player == null) {
            sender.sendMessage(Component.text("Player '" + playerName + "' not found or offline!", NamedTextColor.RED));
            return 0;
        }

        Arena arena = arenaManager.getArena(arenaName);
        if (arena == null) {
            sender.sendMessage(Component.text("Arena '" + arenaName + "' not found!", NamedTextColor.RED));
            return 0;
        }

        // Check if player is already in a game
        Game currentGame = gameManager.getGameByPlayer(player);
        if (currentGame != null) {
            sender.sendMessage(Component.text("Player '" + playerName + "' is already in a game in arena '" + 
                currentGame.getArena().getName() + "'!", NamedTextColor.RED));
            return 0;
        }

        Game game = gameManager.getGameByArena(arena);
        if (game == null) {
            game = gameManager.createGame(arena);
            if (game == null) {
                sender.sendMessage(Component.text("Failed to create game in arena '" + arenaName + "'!", NamedTextColor.RED));
                return 0;
            }
        }

        if (game.getState() != GameState.WAITING) {
            sender.sendMessage(Component.text("Cannot add player to game in arena '" + arenaName + 
                "' - game is already in progress! State: " + game.getState(), NamedTextColor.RED));
            return 0;
        }

        // The addPlayer method is void, so we need to handle this differently
        try {
            game.addPlayer(player);
            sender.sendMessage(Component.text("Added player '" + playerName + "' to game in arena '" + arenaName + "'!", NamedTextColor.GREEN));
            player.sendMessage(Component.text("You have been added to a Survival Games match in arena '" + arenaName + "'!", NamedTextColor.GREEN));
            logger.info("Player '" + playerName + "' added to game in arena '" + arenaName + "' by " + sender.getName());
            return 1;
        } catch (Exception e) {
            sender.sendMessage(Component.text("Failed to add player '" + playerName + "' to game in arena '" + arenaName + 
                "' - " + e.getMessage(), NamedTextColor.RED));
            logger.warn("Failed to add player to game", e);
            return 0;
        }
    }

    /**
     * Handles removing a player from their current game (console-friendly).
     */
    private int removePlayerFromGame(CommandContext<CommandSourceStack> context) {
        // Initialize managers if needed
        initializeManagers();
        
        CommandSender sender = context.getSource().getSender();
        String playerName = StringArgumentType.getString(context, "player");
        
        Player player = Bukkit.getPlayer(playerName);
        if (player == null) {
            sender.sendMessage(Component.text("Player '" + playerName + "' not found or offline!", NamedTextColor.RED));
            return 0;
        }

        Game game = gameManager.getGameByPlayer(player);
        if (game == null) {
            sender.sendMessage(Component.text("Player '" + playerName + "' is not in any game!", NamedTextColor.RED));
            return 0;
        }

        String arenaName = game.getArena().getName();
        game.removePlayer(player, false); // false = not a disconnect
        sender.sendMessage(Component.text("Removed player '" + playerName + "' from game in arena '" + arenaName + "'!", NamedTextColor.GREEN));
        player.sendMessage(Component.text("You have been removed from the Survival Games match!", NamedTextColor.YELLOW));
        logger.info("Player '" + playerName + "' removed from game in arena '" + arenaName + "' by " + sender.getName());
        return 1;
    }

    /**
     * Handles force starting a game regardless of player count (console-friendly).
     */
    private int forceStartGame(CommandContext<CommandSourceStack> context) {
        // Initialize managers if needed
        initializeManagers();
        
        CommandSender sender = context.getSource().getSender();
        String arenaName = StringArgumentType.getString(context, "arena");
        
        Arena arena = arenaManager.getArena(arenaName);
        if (arena == null) {
            sender.sendMessage(Component.text("Arena '" + arenaName + "' not found!", NamedTextColor.RED));
            return 0;
        }

        Game game = gameManager.getGameByArena(arena);
        if (game == null) {
            sender.sendMessage(Component.text("No game found in arena '" + arenaName + "'!", NamedTextColor.RED));
            return 0;
        }

        if (game.getState() != GameState.WAITING) {
            sender.sendMessage(Component.text("Cannot force start game in arena '" + arenaName + 
                "' - game is not in waiting state! Current state: " + game.getState(), NamedTextColor.RED));
            return 0;
        }

        if (game.getPlayers().isEmpty()) {
            sender.sendMessage(Component.text("Cannot force start game in arena '" + arenaName + 
                "' - no players in game!", NamedTextColor.RED));
            return 0;
        }

        // Force start by directly starting the countdown (equivalent to force start)
        game.startCountdown();
        sender.sendMessage(Component.text("Force started game in arena '" + arenaName + "' with " + 
            game.getPlayers().size() + " players!", NamedTextColor.GREEN));
        logger.info("Game force started in arena '" + arenaName + "' by " + sender.getName() + " with " + game.getPlayers().size() + " players");
        return 1;
    }

    /**
     * Handles listing all games and arenas (console-friendly).
     */
    private int listGames(CommandContext<CommandSourceStack> context) {
        // Initialize managers if needed
        initializeManagers();
        
        CommandSender sender = context.getSource().getSender();
        
        sender.sendMessage(Component.text("=== Survival Games Status ===", NamedTextColor.GOLD));
        
        Collection<Game> activeGames = gameManager.getActiveGames();
        if (activeGames.isEmpty()) {
            sender.sendMessage(Component.text("No active games", NamedTextColor.GRAY));
        } else {
            sender.sendMessage(Component.text("Active Games:", NamedTextColor.YELLOW));
            for (Game game : activeGames) {
                Arena arena = game.getArena();
                Component status = Component.text("  " + arena.getName() + ": ", NamedTextColor.WHITE)
                    .append(Component.text(game.getState().toString(), getStateColor(game.getState())))
                    .append(Component.text(" (" + game.getPlayers().size() + " players)", NamedTextColor.GRAY));
                sender.sendMessage(status);
            }
        }
        
        sender.sendMessage(Component.text("Available Arenas:", NamedTextColor.YELLOW));
        for (Arena arena : arenaManager.getArenas()) {
            Game game = gameManager.getGameByArena(arena);
            Component arenaInfo;
            if (game != null) {
                arenaInfo = Component.text("  " + arena.getName() + ": ", NamedTextColor.WHITE)
                    .append(Component.text("In Use", NamedTextColor.RED))
                    .append(Component.text(" (" + game.getState() + ")", NamedTextColor.GRAY));
            } else {
                arenaInfo = Component.text("  " + arena.getName() + ": ", NamedTextColor.WHITE)
                    .append(Component.text("Available", NamedTextColor.GREEN));
            }
            sender.sendMessage(arenaInfo);
        }
        
        return 1;
    }

    /**
     * Shows information about a specific arena (console-friendly).
     */
    private int showArenaInfo(CommandContext<CommandSourceStack> context) {
        // Initialize managers if needed
        initializeManagers();
        
        CommandSender sender = context.getSource().getSender();
        String arenaName = StringArgumentType.getString(context, "arena");
        
        Arena arena = arenaManager.getArena(arenaName);
        if (arena == null) {
            sender.sendMessage(Component.text("Arena '" + arenaName + "' not found!", NamedTextColor.RED));
            return 0;
        }

        sender.sendMessage(Component.text("=== Arena Info: " + arena.getName() + " ===", NamedTextColor.GOLD));
        
        Game game = gameManager.getGameByArena(arena);
        if (game != null) {
            sender.sendMessage(Component.text("Status: ", NamedTextColor.YELLOW)
                .append(Component.text("In Use", NamedTextColor.RED)));
            sender.sendMessage(Component.text("Game State: ", NamedTextColor.YELLOW)
                .append(Component.text(game.getState().toString(), getStateColor(game.getState()))));
            sender.sendMessage(Component.text("Players: ", NamedTextColor.YELLOW)
                .append(Component.text(game.getPlayers().size() + "/" + arena.getMaxPlayers(), NamedTextColor.WHITE)));
            
            if (!game.getPlayers().isEmpty()) {
                sender.sendMessage(Component.text("Player List:", NamedTextColor.YELLOW));
                for (UUID playerId : game.getPlayers()) {
                    Player player = Bukkit.getPlayer(playerId);
                    if (player != null) {
                        sender.sendMessage(Component.text("  - " + player.getName(), NamedTextColor.WHITE));
                    }
                }
            }
        } else {
            sender.sendMessage(Component.text("Status: ", NamedTextColor.YELLOW)
                .append(Component.text("Available", NamedTextColor.GREEN)));
        }
        
        sender.sendMessage(Component.text("Max Players: ", NamedTextColor.YELLOW)
            .append(Component.text(String.valueOf(arena.getMaxPlayers()), NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("World: ", NamedTextColor.YELLOW)
            .append(Component.text(arena.getWorld().getName(), NamedTextColor.WHITE)));
        
        return 1;
    }

    /**
     * Shows information about the sender's current game or general status (console shows all).
     */
    private int showCurrentGameInfo(CommandContext<CommandSourceStack> context) {
        // Initialize managers if needed
        initializeManagers();
        
        CommandSender sender = context.getSource().getSender();
        
        if (sender instanceof Player player) {
            Game game = gameManager.getGameByPlayer(player);
            if (game == null) {
                player.sendMessage(Component.text("You are not in a game!", NamedTextColor.RED));
                return 0;
            }
            
            Arena arena = game.getArena();
            player.sendMessage(Component.text("=== Current Game Info ===", NamedTextColor.GOLD));
            player.sendMessage(Component.text("Arena: ", NamedTextColor.YELLOW)
                .append(Component.text(arena.getName(), NamedTextColor.WHITE)));
            player.sendMessage(Component.text("State: ", NamedTextColor.YELLOW)
                .append(Component.text(game.getState().toString(), getStateColor(game.getState()))));
            player.sendMessage(Component.text("Players: ", NamedTextColor.YELLOW)
                .append(Component.text(game.getPlayers().size() + "/" + arena.getMaxPlayers(), NamedTextColor.WHITE)));
            player.sendMessage(Component.text("PvP Enabled: ", NamedTextColor.YELLOW)
                .append(Component.text(game.isPvpEnabled() ? "Yes" : "No", 
                    game.isPvpEnabled() ? NamedTextColor.GREEN : NamedTextColor.RED)));
            
            return 1;
        } else {
            // Console gets overview of all games
            return listGames(context);
        }
    }

    /**
     * Handles the reload command (console-friendly).
     */
    private int reloadPlugin(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        
        try {
            getPlugin().reloadConfig();
            sender.sendMessage(Component.text("LumaSG configuration reloaded successfully!", NamedTextColor.GREEN));
            logger.info("Configuration reloaded by " + sender.getName());
            return 1;
        } catch (Exception e) {
            sender.sendMessage(Component.text("Failed to reload configuration: " + e.getMessage(), NamedTextColor.RED));
            logger.error("Failed to reload configuration", e);
            return 0;
        }
    }

    /**
     * Shows help information (console-friendly).
     */
    private int showHelp(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        boolean isConsole = sender instanceof ConsoleCommandSender;
        
        sender.sendMessage(Component.text("=== LumaSG Commands ===", NamedTextColor.GOLD));
        
        if (!isConsole) {
            sender.sendMessage(Component.text("/sg", NamedTextColor.YELLOW)
                .append(Component.text(" - Open the main menu", NamedTextColor.GRAY)));
            sender.sendMessage(Component.text("/sg join <arena>", NamedTextColor.YELLOW)
                .append(Component.text(" - Join a game", NamedTextColor.GRAY)));
            sender.sendMessage(Component.text("/sg leave", NamedTextColor.YELLOW)
                .append(Component.text(" - Leave current game", NamedTextColor.GRAY)));
            sender.sendMessage(Component.text("/sg info", NamedTextColor.YELLOW)
                .append(Component.text(" - Show current game info", NamedTextColor.GRAY)));
        }
        
        sender.sendMessage(Component.text("/sg list", NamedTextColor.YELLOW)
            .append(Component.text(" - List all games and arenas", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/sg info <arena>", NamedTextColor.YELLOW)
            .append(Component.text(" - Show arena information", NamedTextColor.GRAY)));
        
        if (sender.hasPermission("lumasg.command.sg.start")) {
            sender.sendMessage(Component.text("/sg start [arena]", NamedTextColor.YELLOW)
                .append(Component.text(" - Start a game", NamedTextColor.GRAY)));
        }
        
        if (sender.hasPermission("lumasg.command.sg.stop")) {
            sender.sendMessage(Component.text("/sg stop [arena]", NamedTextColor.YELLOW)
                .append(Component.text(" - Stop a game", NamedTextColor.GRAY)));
        }
        
        if (sender.hasPermission("lumasg.command.sg.admin")) {
            sender.sendMessage(Component.text("=== Admin Commands ===", NamedTextColor.RED));
            sender.sendMessage(Component.text("/sg addplayer <player> <arena>", NamedTextColor.YELLOW)
                .append(Component.text(" - Add player to game", NamedTextColor.GRAY)));
            sender.sendMessage(Component.text("/sg removeplayer <player>", NamedTextColor.YELLOW)
                .append(Component.text(" - Remove player from game", NamedTextColor.GRAY)));
            sender.sendMessage(Component.text("/sg forcestart <arena>", NamedTextColor.YELLOW)
                .append(Component.text(" - Force start game", NamedTextColor.GRAY)));
        }
        
        if (sender.hasPermission("lumasg.command.sg.reload")) {
            sender.sendMessage(Component.text("/sg reload", NamedTextColor.YELLOW)
                .append(Component.text(" - Reload configuration", NamedTextColor.GRAY)));
        }
        
        return 1;
    }

    /**
     * Handles the wand command.
     */
    private int giveWand(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use this command!", NamedTextColor.RED));
            return 0;
        }

        getPlugin().getAdminWand().giveWand(player);
        return 1;
    }

    /**
     * Gets the appropriate color for a game state.
     */
    private NamedTextColor getStateColor(GameState state) {
        return switch (state) {
            case WAITING -> NamedTextColor.YELLOW;
            case COUNTDOWN -> NamedTextColor.GOLD;
            case GRACE_PERIOD -> NamedTextColor.AQUA;
            case ACTIVE -> NamedTextColor.GREEN;
            case DEATHMATCH -> NamedTextColor.RED;
            case FINISHED -> NamedTextColor.GRAY;
        };
    }

    /**
     * Creates a new arena at the player's location.
     */
    private int createArena(CommandContext<CommandSourceStack> context) {
        // Initialize managers if needed
        initializeManagers();
        
        CommandSender sender = context.getSource().getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use this command!", NamedTextColor.RED));
            return 0;
        }

        String name = StringArgumentType.getString(context, "name");
        int radius = IntegerArgumentType.getInteger(context, "radius");
        
        // Check if arena already exists
        if (arenaManager.getArena(name) != null) {
            player.sendMessage(Component.text("An arena with that name already exists!", NamedTextColor.RED));
            return 0;
        }

        // Create the arena at player's location
        Arena arena = arenaManager.createArena(name, player.getLocation(), radius);
        if (arena == null) {
            player.sendMessage(Component.text("Failed to create arena!", NamedTextColor.RED));
            return 0;
        }

        // Select the arena for editing
        getPlugin().getAdminWandListener().setSelectedArena(player, arena);
        
        player.sendMessage(Component.text("Arena '" + name + "' created with radius " + radius + "!", NamedTextColor.GREEN));
        player.sendMessage(Component.text("Arena selected! Use the admin wand to edit it.", NamedTextColor.GREEN));
        
        return 1;
    }

    /**
     * Selects an arena for editing.
     */
    private int selectArena(CommandContext<CommandSourceStack> context) {
        // Initialize managers if needed
        initializeManagers();
        
        CommandSender sender = context.getSource().getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use this command!", NamedTextColor.RED));
            return 0;
        }

        String arenaName = StringArgumentType.getString(context, "arena");
        Arena arena = arenaManager.getArena(arenaName);

        if (arena == null) {
            player.sendMessage(Component.text("Arena not found!", NamedTextColor.RED));
            return 0;
        }

        getPlugin().getAdminWandListener().setSelectedArena(player, arena);
        player.sendMessage(Component.text("Arena '" + arenaName + "' selected! Use the admin wand to edit it.", NamedTextColor.GREEN));
        
        return 1;
    }

    /**
     * Debug command to spawn a meteor for testing.
     */
    private int debugSpawnMeteor(CommandContext<CommandSourceStack> context) {
        // Initialize managers if needed
        initializeManagers();
        
        Player player = context.getSource().getExecutor() instanceof Player p ? p : null;
        if (player == null) {
            context.getSource().getSender().sendMessage(
                MiniMessageUtils.parseMessage("<red>This command can only be used by players!</red>")
            );
            return 0;
        }

        // Check if player has permission
        if (!player.hasPermission("lumasg.admin")) {
            MiniMessageUtils.sendMessage(player, "<red>You don't have permission to use this command!</red>");
            return 0;
        }

        try {
            // Get the custom items manager
            CustomItemsManager customItemsManager = plugin.getCustomItemsManager();

            // Calculate spawn location - 50 blocks away from player
            Location targetLocation = player.getLocation();
            double angle = Math.random() * 2 * Math.PI;
            double spawnDistance = 50.0;
            Location spawnLocation = targetLocation.clone().add(
                Math.cos(angle) * spawnDistance,
                150, // Reasonable height for meteor spawn
                Math.sin(angle) * spawnDistance
            );

            // Create new airdrop behavior instance
            AirdropBehavior airdropBehavior = new AirdropBehavior(plugin, targetLocation, spawnLocation, player);

            // Create test airdrop data
            UUID airdropId = UUID.randomUUID();
            AirdropData testAirdropData = new AirdropData(
                airdropId,
                player.getUniqueId(),
                targetLocation.clone(), // Use clone to ensure we have the exact location
                "tier4",
                System.currentTimeMillis()
            );

            // Execute the airdrop immediately
            airdropBehavior.executeAirdrop(testAirdropData);

            MiniMessageUtils.sendMessage(player, 
                "<green><bold>Debug meteor spawned!</bold></green> <yellow>Target: " + 
                (int)targetLocation.getX() + ", " + (int)targetLocation.getY() + ", " + (int)targetLocation.getZ() + 
                " - Check the sky for incoming meteor!</yellow>");

            logger.info("Debug meteor spawned by " + player.getName() + " at " + targetLocation);
            return 1;

        } catch (Exception e) {
            logger.error("Failed to spawn debug meteor", e);
            MiniMessageUtils.sendMessage(player, "<red>Failed to spawn meteor: " + e.getMessage() + "</red>");
            return 0;
        }
    }
} 
