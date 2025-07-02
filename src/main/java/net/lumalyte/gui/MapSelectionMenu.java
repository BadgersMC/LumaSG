package net.lumalyte.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.lumalyte.LumaSG;
import net.lumalyte.arena.Arena;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.jetbrains.annotations.NotNull;
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
 * Menu for selecting maps during game setup.
 */
public class MapSelectionMenu {
    private final LumaSG plugin;
    private final GameSetupMenu gameSetupMenu;
    
    /**
     * Constructs a new MapSelectionMenu.
     * 
     * @param plugin The LumaSG plugin instance
     * @param gameSetupMenu The game setup menu to return to
     */
    public MapSelectionMenu(LumaSG plugin, GameSetupMenu gameSetupMenu) {
        this.plugin = plugin;
        this.gameSetupMenu = gameSetupMenu;
    }
    
    /**
     * Opens the map selection menu for a player.
     * 
     * @param player The player to open the menu for
     * @param config The game setup configuration
     */
    public void openMenu(Player player, Object config) {
        // Create border item
        Item borderItem = new SimpleItem(new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).setDisplayName(""));
        
        // Create back button
        Item backButton = new AbstractItem() {
            @Override
            public ItemProvider getItemProvider() {
                return new ItemBuilder(Material.ARROW)
                    .setDisplayName("§c§lBack to Setup")
                    .addLoreLines("Click to return to game setup");
            }
            
            @Override
            public void handleClick(ClickType clickType, @NotNull Player player, org.bukkit.event.inventory.@NotNull InventoryClickEvent event) {
                if (clickType.isLeftClick()) {
                    player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
                    player.closeInventory();
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        gameSetupMenu.openMenu(player);
                    });
                }
            }
        };
        
        // Get all available arenas
        List<Item> arenaItems = new ArrayList<>();
        
        for (Arena arena : plugin.getArenaManager().getArenas()) {
            // Check if arena is available (no active game)
            boolean isAvailable = plugin.getGameManager().getGameByArena(arena) == null;
            
            final Arena finalArena = arena;
            
            Item arenaItem = new AbstractItem() {
                @Override
                public ItemProvider getItemProvider() {
                    Material material = isAvailable ? Material.GRASS_BLOCK : Material.REDSTONE_BLOCK;
                    String status = isAvailable ? "§aAvailable" : "§cIn Use";
                    
                    ItemBuilder builder = new ItemBuilder(material)
                        .setDisplayName("§b§l" + finalArena.getName())
                        .addLoreLines(
                            "§7Status: " + status,
                            "§7Max Players: §f" + finalArena.getMaxPlayers(),
                            "§7World: §f" + finalArena.getWorld().getName(),
                            ""
                        );
                    
                    if (isAvailable) {
                        builder.addLoreLines("§aClick to select this map");
                    } else {
                        builder.addLoreLines("§cThis map is currently in use");
                    }
                    
                    return builder;
                }
                
                @Override
                public void handleClick(ClickType clickType, @NotNull Player player, org.bukkit.event.inventory.@NotNull InventoryClickEvent event) {
                    if (clickType.isLeftClick() && isAvailable) {
                        player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
                        player.closeInventory();
                        
                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                            gameSetupMenu.onMapSelected(player, finalArena);
                        });
                    } else if (!isAvailable) {
                        player.sendMessage(Component.text("§cThis map is currently in use!", NamedTextColor.RED));
                    }
                }
            };
            
            arenaItems.add(arenaItem);
        }
        
        // Add "Random Map" option
        Item randomMapItem = new AbstractItem() {
            @Override
            public ItemProvider getItemProvider() {
                return new ItemBuilder(Material.COMPASS)
                    .setDisplayName("§d§lRandom Map")
                    .addLoreLines(
                        "§7Select a random available map",
                        "",
                        "§eClick to select random"
                    );
            }
            
            @Override
            public void handleClick(ClickType clickType, @NotNull Player player, org.bukkit.event.inventory.@NotNull InventoryClickEvent event) {
                if (clickType.isLeftClick()) {
                    player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
                    
                    // Find available arenas
                    List<Arena> availableArenas = new ArrayList<>();
                    for (Arena arena : plugin.getArenaManager().getArenas()) {
                        if (plugin.getGameManager().getGameByArena(arena) == null) {
                            availableArenas.add(arena);
                        }
                    }
                    
                    if (availableArenas.isEmpty()) {
                        player.sendMessage(Component.text("§cNo maps are currently available!", NamedTextColor.RED));
                        return;
                    }
                    
                    // Select random arena
                    Arena randomArena = availableArenas.get((int) (Math.random() * availableArenas.size()));
                    
                    player.closeInventory();
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        gameSetupMenu.onMapSelected(player, randomArena);
                    });
                }
            }
        };
        
        arenaItems.add(0, randomMapItem); // Add at the beginning
        
        // Create the paged GUI
        PagedGui.Builder<Item> builder = PagedGui.items()
            .setStructure(
                "# # # # # # # # #",
                "# x x x x x x x #",
                "# x x x x x x x #",
                "# x x x x x x x #",
                "# b # < p > # # #"
            )
            .addIngredient('#', borderItem)
            .addIngredient('x', Markers.CONTENT_LIST_SLOT_HORIZONTAL)
            .addIngredient('<', new SimpleItem(new ItemBuilder(Material.ARROW).setDisplayName("§a§lPrevious Page")))
            .addIngredient('>', new SimpleItem(new ItemBuilder(Material.ARROW).setDisplayName("§a§lNext Page")))
            .addIngredient('p', new SimpleItem(new ItemBuilder(Material.PAPER)
                .setDisplayName("§f§lPage {page}/{pages}")
                .addLoreLines("§7Select a map for your game")))
            .addIngredient('b', backButton)
            .setContent(arenaItems);
        
        Gui gui = builder.build();
        
        // Create and open the window
        Window window = Window.single()
            .setViewer(player)
            .setTitle("§8§lLumaSG §7- §fMap Selection")
            .setGui(gui)
            .build();
        
        window.open();
    }
} 