package net.lumalyte.gui;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.jetbrains.annotations.NotNull;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.lumalyte.LumaSG;
import net.lumalyte.arena.Arena;
import net.lumalyte.game.Game;
import net.lumalyte.game.GameState;
import xyz.xenondevs.invui.gui.Gui;
import xyz.xenondevs.invui.gui.PagedGui;
import xyz.xenondevs.invui.gui.structure.Markers;
import xyz.xenondevs.invui.item.Item;
import xyz.xenondevs.invui.item.ItemProvider;
import xyz.xenondevs.invui.item.builder.ItemBuilder;
import xyz.xenondevs.invui.item.impl.AbstractItem;
import xyz.xenondevs.invui.item.impl.SimpleItem;
import xyz.xenondevs.invui.window.Window;

/**
 * Menu for browsing and joining available games.
 */
public class GameBrowserMenu {
    private final LumaSG plugin;
    
    /**
     * Constructs a new GameBrowserMenu.
     * 
     * @param plugin The LumaSG plugin instance
     */
    public GameBrowserMenu(LumaSG plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Opens the game browser menu for a player.
     * 
     * @param player The player to open the menu for
     */
    public void openMenu(Player player) {
        // Create border item
        Item borderItem = new SimpleItem(new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).setDisplayName(""));
        
        // Create back button
        Item backButton = new AbstractItem() {
            @Override
            public ItemProvider getItemProvider() {
                return new ItemBuilder(Material.ARROW)
                    .setDisplayName("§c§lBack to Main Menu")
                    .addLoreLines("Click to return to the main menu");
            }
            
            @Override
            public void handleClick(ClickType clickType, @NotNull Player player, org.bukkit.event.inventory.@NotNull InventoryClickEvent event) {
                if (clickType.isLeftClick()) {
                    player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
                    player.closeInventory();
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        new net.lumalyte.gui.menus.MainMenu(plugin).openMenu(player);
                    });
                }
            }
        };
        
        // Create refresh button
        Item refreshButton = new AbstractItem() {
            @Override
            public ItemProvider getItemProvider() {
                return new ItemBuilder(Material.CLOCK)
                    .setDisplayName("§a§lRefresh")
                    .addLoreLines("Click to refresh the game list");
            }
            
            @Override
            public void handleClick(ClickType clickType, @NotNull Player player, org.bukkit.event.inventory.@NotNull InventoryClickEvent event) {
                if (clickType.isLeftClick()) {
                    player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
                    player.closeInventory();
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        openMenu(player);
                    });
                }
            }
        };
        
        // Get all available games
        List<Item> gameItems = new ArrayList<>();
        
        // Add items for each arena/game
        for (Arena arena : plugin.getArenaManager().getArenas()) {
            Game game = plugin.getGameManager().getGameByArena(arena);
            boolean gameExists = (game != null);
            
            // Check if player has setup permissions
            boolean canSetupGames = player.hasPermission("lumasg.setup.game") || 
                                   player.hasPermission("lumasg.admin");
            
            // For regular players: only show games that have been properly set up
            if (!canSetupGames && gameExists && !game.isSetupComplete()) {
                // Skip games that haven't been set up yet for regular players
                continue;
            }
            
            // For regular players: don't show arenas with no games at all
            if (!canSetupGames && !gameExists) {
                // Skip arenas with no games for regular players
                continue;
            }
            
            // Filter out INACTIVE games for regular players (only show to admins/ranked players)
            if (gameExists && game.getState() == GameState.INACTIVE) {
                if (!player.hasPermission("lumasg.admin")) {
                    continue; // Skip INACTIVE games for regular players
                }
            }
            
            final Arena finalArena = arena;
            final Game finalGame = game;
            
            // Create item based on game status
            ItemBuilder builder = new ItemBuilder(getArenaIcon(finalGame));
            
            // Set display name
            builder.setDisplayName("§b§l" + finalArena.getName());
            
            // Add lore based on game status
            List<String> lore = new ArrayList<>();
            
            // Show current players vs available spawn points (more accurate than max players)
            int currentPlayers = gameExists ? finalGame.getPlayerCount() : 0;
            int maxPlayers = Math.min(finalArena.getMaxPlayers(), finalArena.getSpawnPoints().size());
            lore.add("§7Players: §f" + currentPlayers + "/" + maxPlayers);
            lore.add("§7Status: " + getGameStatusText(finalGame));
            
            // Add spawn points info for admins
            if (player.hasPermission("lumasg.admin")) {
                lore.add("§7Spawn Points: §f" + finalArena.getSpawnPoints().size());
            }
            
            lore.add("");
            
            if (gameExists && finalGame.getState() != GameState.FINISHED) {
                if (finalGame.getState() == GameState.INACTIVE) {
                    lore.add("§6Click to configure this game");
                } else if (finalGame.getState() == GameState.WAITING) {
                    if (currentPlayers < maxPlayers) {
                        lore.add("§aClick to join this game");
                    } else {
                        lore.add("§cGame is full");
                    }
                } else {
                    lore.add("§cGame in progress");
                }
            } else {
                // Check if player can create games
                if (player.hasPermission("lumasg.setup.game") || player.hasPermission("lumasg.admin")) {
                    lore.add("§aClick to create a new game");
                } else {
                    lore.add("§7No games available");
                }
            }
            
            // Convert List<String> to varargs String...
            builder.addLoreLines(lore.toArray(new String[0]));
            
            // Create the item with click handler
            Item gameItem = new AbstractItem() {
                @Override
                public ItemProvider getItemProvider() {
                    return builder;
                }
                
                @Override
                public void handleClick(ClickType clickType, @NotNull Player player, org.bukkit.event.inventory.@NotNull InventoryClickEvent event) {
                    if (clickType.isLeftClick()) {
                        player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
                        player.closeInventory();
                        
                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                            if (finalGame != null && finalGame.getState() != GameState.FINISHED) {
                                // Handle existing games based on their state
                                switch (finalGame.getState()) {
                                    case INACTIVE:
                                        // Game exists but is inactive - check if player has setup permission
                                        if (player.hasPermission("lumasg.setup.game") || 
                                            player.hasPermission("lumasg.admin")) {
                                            // Ranked player - open game setup menu
                                            new GameSetupMenu(plugin).openMenu(player);
                                        } else {
                                            // Regular player - shouldn't see this but handle gracefully
                                            player.sendMessage(Component.text("§cThis game is being set up by a ranked player. Please wait for it to become available!", NamedTextColor.RED));
                                        }
                                        break;
                                        
                                    case WAITING:
                                        // Check if game has space
                                        int currentPlayers = finalGame.getPlayerCount();
                                        int maxPlayers = Math.min(finalArena.getMaxPlayers(), finalArena.getSpawnPoints().size());
                                        
                                        if (currentPlayers < maxPlayers) {
                                            // Join the game
                                            finalGame.addPlayer(player);
                                            player.sendMessage(Component.text("§aYou joined the game in arena " + finalArena.getName() + "!", NamedTextColor.GREEN));
                                        } else {
                                            player.sendMessage(Component.text("§cThis game is full! (" + currentPlayers + "/" + maxPlayers + ")", NamedTextColor.RED));
                                        }
                                        break;
                                        
                                    case COUNTDOWN:
                                        player.sendMessage(Component.text("§cThis game is starting soon and cannot accept new players!", NamedTextColor.RED));
                                        break;
                                        
                                    case GRACE_PERIOD:
                                    case ACTIVE:
                                    case DEATHMATCH:
                                        // Offer to spectate
                                        finalGame.addSpectator(player);
                                        player.sendMessage(Component.text("§6You are now spectating the game in arena " + finalArena.getName() + "!", NamedTextColor.GOLD));
                                        break;
                                        
                                    default:
                                        player.sendMessage(Component.text("§cThis game is not available for joining!", NamedTextColor.RED));
                                        break;
                                }
                            } else {
                                // No game exists or game is finished - check if player has setup permission
                                if (player.hasPermission("lumasg.setup.game") || 
                                    player.hasPermission("lumasg.admin")) {
                                    // Ranked player - open game setup menu
                                    new GameSetupMenu(plugin).openMenu(player);
                                } else {
                                    // Regular player - can't create games
                                    player.sendMessage(Component.text("§cOnly ranked players can create new games!", NamedTextColor.RED));
                                }
                            }
                        });
                    }
                }
            };
            
            gameItems.add(gameItem);
        }
        
        // Create the paged GUI
        PagedGui.Builder<Item> builder = PagedGui.items()
            .setStructure(
                "# # # # # # # # #",
                "# x x x x x x x #",
                "# x x x x x x x #",
                "# x x x x x x x #",
                "# b # < p > # r #"
            )
            .addIngredient('#', borderItem)
            .addIngredient('x', Markers.CONTENT_LIST_SLOT_HORIZONTAL)
            .addIngredient('<', new SimpleItem(new ItemBuilder(Material.ARROW).setDisplayName("§a§lPrevious Page")))
            .addIngredient('>', new SimpleItem(new ItemBuilder(Material.ARROW).setDisplayName("§a§lNext Page")))
            .addIngredient('p', new SimpleItem(new ItemBuilder(Material.PAPER)
                .setDisplayName("§f§lPage {page}/{pages}")
                .addLoreLines("§7Browsing available games")))
            .addIngredient('b', backButton)
            .addIngredient('r', refreshButton)
            .setContent(gameItems);
        
        Gui gui = builder.build();
        
        // Create and open the window
        Window window = Window.single()
            .setViewer(player)
            .setTitle("§8§lLumaSG §7- §fGame Browser")
            .setGui(gui)
            .build();
        
        window.open();
    }
    
    /**
     * Gets the appropriate icon material for an arena based on its game status.
     * 
     * @param game The game in the arena, or null if no game exists
     * @return The material to use for the icon
     */
    private Material getArenaIcon(Game game) {
        if (game == null) {
            return Material.EMERALD_BLOCK; // No game, can create
        }

		return switch (game.getState()) {
			case INACTIVE -> Material.PURPLE_WOOL; // Game being set up
			case WAITING -> Material.LIME_WOOL; // Waiting for players
			case COUNTDOWN -> Material.YELLOW_WOOL; // Countdown in progress
			case GRACE_PERIOD, ACTIVE -> Material.ORANGE_WOOL; // Game in progress
			case DEATHMATCH -> Material.RED_WOOL; // Deathmatch in progress
			case FINISHED -> Material.EMERALD_BLOCK; // Game finished, can create new
		};
    }
    
    /**
     * Gets a colored text representation of a game's status.
     * 
     * @param game The game, or null if no game exists
     * @return The colored status text
     */
    private String getGameStatusText(Game game) {
        if (game == null) {
            return "§aAvailable";
        }

		return switch (game.getState()) {
			case INACTIVE -> "§5Being set up";
			case WAITING -> "§aWaiting for players";
			case COUNTDOWN -> "§eStarting soon";
			case GRACE_PERIOD -> "§6Grace period";
			case ACTIVE -> "§cIn progress";
			case DEATHMATCH -> "§4Deathmatch";
			case FINISHED -> "§aFinished";
		};
    }
} 