package net.lumalyte.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
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
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.concurrent.CompletableFuture;

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
            .executes(this::openMenu) // Default behavior: open menu when no subcommand is provided
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
                .executes(this::startGame))
            .then(Commands.literal("stop")
                .requires(source -> source.getSender().hasPermission("lumasg.command.sg.stop"))
                .executes(this::stopGame))
            .then(Commands.literal("menu")
                .executes(this::openMenu))
            .then(Commands.literal("debug")
                .requires(source -> source.getSender().hasPermission("lumasg.command.sg.debug"))
                .then(Commands.literal("skippvp")
                    .executes(this::debugSkipPvP))
                .then(Commands.literal("kingdomsx")
                    .executes(this::debugKingdomsX)));
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
} 
