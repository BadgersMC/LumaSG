package net.lumalyte.gui;

import net.lumalyte.LumaSG;
import net.lumalyte.arena.Arena;
import net.lumalyte.game.Game;
import net.lumalyte.game.GameState;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import xyz.xenondevs.invui.gui.Gui;
import xyz.xenondevs.invui.gui.PagedGui;
import xyz.xenondevs.invui.gui.structure.Markers;
import xyz.xenondevs.invui.item.Item;
import xyz.xenondevs.invui.item.ItemProvider;
import xyz.xenondevs.invui.item.builder.ItemBuilder;
import xyz.xenondevs.invui.item.impl.AbstractItem;
import xyz.xenondevs.invui.item.impl.SimpleItem;
import xyz.xenondevs.invui.window.Window;

import java.util.ArrayList;
import java.util.List;

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
            public void handleClick(ClickType clickType, Player player, org.bukkit.event.inventory.InventoryClickEvent event) {
                if (clickType.isLeftClick()) {
                    player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
                    player.closeInventory();
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        new MainMenu(plugin).openMenu(player);
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
            public void handleClick(ClickType clickType, Player player, org.bukkit.event.inventory.InventoryClickEvent event) {
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
            
            final Arena finalArena = arena;
            final Game finalGame = game;
            
            // Create item based on game status
            ItemBuilder builder = new ItemBuilder(getArenaIcon(finalArena, finalGame));
            
            // Set display name
            builder.setDisplayName("§b§l" + finalArena.getName());
            
            // Add lore based on game status
            List<String> lore = new ArrayList<>();
            lore.add("§7Players: §f" + (gameExists && finalGame != null ? finalGame.getPlayerCount() : 0) + "/" + finalArena.getMaxPlayers());
            lore.add("§7Status: " + getGameStatusText(finalGame));
            lore.add("");
            
            if (gameExists && finalGame != null && finalGame.getState() != GameState.FINISHED) {
                lore.add("§aClick to join this game");
            } else {
                lore.add("§aClick to create a new game");
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
                public void handleClick(ClickType clickType, Player player, org.bukkit.event.inventory.InventoryClickEvent event) {
                    if (clickType.isLeftClick()) {
                        player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
                        player.closeInventory();
                        
                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                            // Join or create game
                            if (finalGame != null && finalGame.getState() != GameState.FINISHED) {
                                // Join existing game
                                finalGame.addPlayer(player);
                                player.sendMessage(Component.text("§aYou joined the game in arena " + finalArena.getName()));
                            } else {
                                // Create new game
                                Game newGame = plugin.getGameManager().createGame(finalArena);
                                if (newGame != null) {
                                    newGame.addPlayer(player);
                                    player.sendMessage(Component.text("§aCreated and joined a new game in arena " + finalArena.getName()));
                                } else {
                                    player.sendMessage(Component.text("§cFailed to create a new game!"));
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
     * @param arena The arena
     * @param game The game in the arena, or null if no game exists
     * @return The material to use for the icon
     */
    private Material getArenaIcon(Arena arena, Game game) {
        if (game == null) {
            return Material.EMERALD_BLOCK; // No game, can create
        }
        
        switch (game.getState()) {
            case WAITING:
                return Material.LIME_WOOL; // Waiting for players
            case COUNTDOWN:
                return Material.YELLOW_WOOL; // Countdown in progress
            case GRACE_PERIOD:
            case ACTIVE:
                return Material.ORANGE_WOOL; // Game in progress
            case DEATHMATCH:
                return Material.RED_WOOL; // Deathmatch in progress
            case FINISHED:
                return Material.EMERALD_BLOCK; // Game finished, can create new
            default:
                return Material.GRAY_WOOL; // Unknown state
        }
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
        
        switch (game.getState()) {
            case WAITING:
                return "§aWaiting for players";
            case COUNTDOWN:
                return "§eStarting soon";
            case GRACE_PERIOD:
                return "§6Grace period";
            case ACTIVE:
                return "§cIn progress";
            case DEATHMATCH:
                return "§4Deathmatch";
            case FINISHED:
                return "§aFinished";
            default:
                return "§7Unknown";
        }
    }
} 