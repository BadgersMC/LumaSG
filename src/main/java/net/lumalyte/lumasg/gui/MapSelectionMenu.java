package net.lumalyte.lumasg.gui;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.jetbrains.annotations.NotNull;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.lumalyte.lumasg.LumaSG;
import net.lumalyte.lumasg.arena.Arena;
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
        Item borderItem = createBorderItem();
        Item backButton = createBackButton();
        List<Item> arenaItems = createArenaItems();
        
        PagedGui.Builder<Item> builder = createPagedGuiBuilder(borderItem, backButton, arenaItems);
        Gui gui = builder.build();
        
        Window window = Window.single()
            .setViewer(player)
            .setTitle("§8§lLumaSG §7- §fMap Selection")
            .setGui(gui)
            .build();
        
        window.open();
    }
    
    /**
     * Creates a border item for the GUI.
     */
    private Item createBorderItem() {
        return new SimpleItem(new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).setDisplayName(""));
    }
    
    /**
     * Creates the back button item.
     */
    private Item createBackButton() {
        return new AbstractItem() {
            @Override
            public ItemProvider getItemProvider() {
                return new ItemBuilder(Material.ARROW)
                    .setDisplayName("§c§lBack to Setup")
                    .addLoreLines("Click to return to game setup");
            }
            
            @Override
            public void handleClick(ClickType clickType, @NotNull Player player, org.bukkit.event.inventory.@NotNull InventoryClickEvent event) {
                if (clickType.isLeftClick()) {
                    handleBackButtonClick(player);
                }
            }
        };
    }
    
    /**
     * Handles the back button click logic.
     */
    private void handleBackButtonClick(@NotNull Player player) {
        player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
        player.closeInventory();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            gameSetupMenu.openMenu(player);
        });
    }
    
    /**
     * Creates all arena items including the random map option.
     */
    private List<Item> createArenaItems() {
        List<Item> arenaItems = new ArrayList<>();
        
        // Add random map option first
        arenaItems.add(createRandomMapItem());
        
        // Add arena items
        for (Arena arena : plugin.getArenaManager().getArenas()) {
            arenaItems.add(createArenaItem(arena));
        }
        
        return arenaItems;
    }
    
    /**
     * Creates a random map selection item.
     */
    private Item createRandomMapItem() {
        return new AbstractItem() {
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
                    handleRandomMapClick(player);
                }
            }
        };
    }
    
    /**
     * Handles the random map selection click.
     */
    private void handleRandomMapClick(@NotNull Player player) {
        player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
        
        List<Arena> availableArenas = getAvailableArenas();
        
        if (availableArenas.isEmpty()) {
            player.sendMessage(Component.text("§cNo maps are currently available!", NamedTextColor.RED));
            return;
        }
        
        Arena randomArena = availableArenas.get((int) (Math.random() * availableArenas.size()));
        
        player.closeInventory();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            gameSetupMenu.onMapSelected(player, randomArena);
        });
    }
    
    /**
     * Gets a list of available arenas (not in use).
     */
    private List<Arena> getAvailableArenas() {
        List<Arena> availableArenas = new ArrayList<>();
        for (Arena arena : plugin.getArenaManager().getArenas()) {
            if (plugin.getGameManager().getGameByArena(arena) == null) {
                availableArenas.add(arena);
            }
        }
        return availableArenas;
    }
    
    /**
     * Creates an item for a specific arena.
     */
    private Item createArenaItem(@NotNull Arena arena) {
        boolean isAvailable = plugin.getGameManager().getGameByArena(arena) == null;
        
        return new AbstractItem() {
            @Override
            public ItemProvider getItemProvider() {
                return createArenaItemProvider(arena, isAvailable);
            }
            
            @Override
            public void handleClick(ClickType clickType, @NotNull Player player, org.bukkit.event.inventory.@NotNull InventoryClickEvent event) {
                handleArenaItemClick(clickType, player, arena, isAvailable);
            }
        };
    }
    
    /**
     * Creates the item provider for an arena item.
     */
    private ItemProvider createArenaItemProvider(@NotNull Arena arena, boolean isAvailable) {
        Material material = isAvailable ? Material.GRASS_BLOCK : Material.REDSTONE_BLOCK;
        String status = isAvailable ? "§aAvailable" : "§cIn Use";
        
        ItemBuilder builder = new ItemBuilder(material)
            .setDisplayName("§b§l" + arena.getName())
            .addLoreLines(
                "§7Status: " + status,
                "§7Max Players: §f" + arena.getMaxPlayers(),
                "§7World: §f" + arena.getWorld().getName(),
                ""
            );
        
        if (isAvailable) {
            builder.addLoreLines("§aClick to select this map");
        } else {
            builder.addLoreLines("§cThis map is currently in use");
        }
        
        return builder;
    }
    
    /**
     * Handles arena item click logic.
     */
    private void handleArenaItemClick(@NotNull ClickType clickType, @NotNull Player player, @NotNull Arena arena, boolean isAvailable) {
        if (clickType.isLeftClick() && isAvailable) {
            player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
            player.closeInventory();
            
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                gameSetupMenu.onMapSelected(player, arena);
            });
        } else if (!isAvailable) {
            player.sendMessage(Component.text("§cThis map is currently in use!", NamedTextColor.RED));
        }
    }
    
    /**
     * Creates the paged GUI builder with all necessary components.
     */
    private PagedGui.Builder<Item> createPagedGuiBuilder(@NotNull Item borderItem, @NotNull Item backButton, @NotNull List<Item> arenaItems) {
        return PagedGui.items()
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
    }
} 
