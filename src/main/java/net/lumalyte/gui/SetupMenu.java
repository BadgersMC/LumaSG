package net.lumalyte.gui;

import net.lumalyte.LumaSG;
import net.lumalyte.arena.Arena;
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
 * Menu for administrators to manage arenas and plugin settings.
 */
public class SetupMenu {
    private final LumaSG plugin;
    
    /**
     * Constructs a new SetupMenu.
     * 
     * @param plugin The LumaSG plugin instance
     */
    public SetupMenu(LumaSG plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Opens the setup menu for a player.
     * 
     * @param player The player to open the menu for
     */
    public void openMenu(Player player) {
        // Check permission
        if (!player.hasPermission("lumasg.admin")) {
            player.sendMessage(Component.text("§cYou don't have permission to access this menu!"));
            return;
        }
        
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
        
        // Create arena management button
        Item arenaButton = new AbstractItem() {
            @Override
            public ItemProvider getItemProvider() {
                return new ItemBuilder(Material.GRASS_BLOCK)
                    .setDisplayName("§a§lArena Management")
                    .addLoreLines(
                        "§7Click to manage arenas",
                        "§7Current arenas: §f" + plugin.getArenaManager().getArenas().size()
                    );
            }
            
            @Override
            public void handleClick(ClickType clickType, Player player, org.bukkit.event.inventory.InventoryClickEvent event) {
                if (clickType.isLeftClick()) {
                    player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
                    player.closeInventory();
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        openArenaManagementMenu(player);
                    });
                }
            }
        };
        
        // Create chest loot button
        Item chestLootButton = new AbstractItem() {
            @Override
            public ItemProvider getItemProvider() {
                return new ItemBuilder(Material.CHEST)
                    .setDisplayName("§6§lChest Loot")
                    .addLoreLines(
                        "§7Click to manage chest loot",
                        "§7Configured tiers: §f" + plugin.getChestManager().getChestItems().size()
                    );
            }
            
            @Override
            public void handleClick(ClickType clickType, Player player, org.bukkit.event.inventory.InventoryClickEvent event) {
                if (clickType.isLeftClick()) {
                    player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
                    player.closeInventory();
                    player.sendMessage(Component.text("§eChest loot management coming soon!"));
                }
            }
        };
        
        // Create settings button
        Item settingsButton = new AbstractItem() {
            @Override
            public ItemProvider getItemProvider() {
                return new ItemBuilder(Material.COMPARATOR)
                    .setDisplayName("§b§lPlugin Settings")
                    .addLoreLines(
                        "§7Click to manage plugin settings"
                    );
            }
            
            @Override
            public void handleClick(ClickType clickType, Player player, org.bukkit.event.inventory.InventoryClickEvent event) {
                if (clickType.isLeftClick()) {
                    player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
                    player.closeInventory();
                    player.sendMessage(Component.text("§ePlugin settings management coming soon!"));
                }
            }
        };
        
        // Create reload button
        Item reloadButton = new AbstractItem() {
            @Override
            public ItemProvider getItemProvider() {
                return new ItemBuilder(Material.REDSTONE)
                    .setDisplayName("§c§lReload Plugin")
                    .addLoreLines(
                        "§7Click to reload the plugin",
                        "§cWarning: This may cause issues with active games!"
                    );
            }
            
            @Override
            public void handleClick(ClickType clickType, Player player, org.bukkit.event.inventory.InventoryClickEvent event) {
                if (clickType.isLeftClick()) {
                    player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
                    player.closeInventory();
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        player.sendMessage(Component.text("§eReloading LumaSG..."));
                        plugin.reload();
                        player.sendMessage(Component.text("§aLumaSG reloaded successfully!"));
                    });
                }
            }
        };
        
        // Create the GUI
        Gui gui = Gui.normal()
            .setStructure(
                "# # # # # # # # #",
                "# . . . . . . . #",
                "# . 1 . 2 . 3 . #",
                "# . . . 4 . . . #",
                "# # # # b # # # #"
            )
            .addIngredient('#', borderItem)
            .addIngredient('1', arenaButton)
            .addIngredient('2', chestLootButton)
            .addIngredient('3', settingsButton)
            .addIngredient('4', reloadButton)
            .addIngredient('b', backButton)
            .build();
        
        // Create and open the window
        Window window = Window.single()
            .setViewer(player)
            .setTitle("§8§lLumaSG §7- §fSetup Menu")
            .setGui(gui)
            .build();
        
        window.open();
    }
    
    /**
     * Opens the arena management menu for a player.
     * 
     * @param player The player to open the menu for
     */
    private void openArenaManagementMenu(Player player) {
        // Create border item
        Item borderItem = new SimpleItem(new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).setDisplayName(""));
        
        // Create back button
        Item backButton = new AbstractItem() {
            @Override
            public ItemProvider getItemProvider() {
                return new ItemBuilder(Material.ARROW)
                    .setDisplayName("§c§lBack to Setup Menu")
                    .addLoreLines("Click to return to the setup menu");
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
        
        // Create new arena button
        Item newArenaButton = new AbstractItem() {
            @Override
            public ItemProvider getItemProvider() {
                return new ItemBuilder(Material.EMERALD_BLOCK)
                    .setDisplayName("§a§lCreate New Arena")
                    .addLoreLines(
                        "§7Click to create a new arena",
                        "§7You will be prompted to enter a name"
                    );
            }
            
            @Override
            public void handleClick(ClickType clickType, Player player, org.bukkit.event.inventory.InventoryClickEvent event) {
                if (clickType.isLeftClick()) {
                    player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
                    player.closeInventory();
                    
                    // Prompt player to enter arena name
                    player.sendMessage(Component.text("§ePlease enter a name for the new arena in chat:"));
                    
                    // In a real implementation, you would register a chat listener
                    // to capture the player's input and create the arena
                }
            }
        };
        
        // Get all arenas
        List<Item> arenaItems = new ArrayList<>();
        
        // Add items for each arena
        for (Arena arena : plugin.getArenaManager().getArenas()) {
            ItemBuilder builder = new ItemBuilder(Material.GRASS_BLOCK)
                .setDisplayName("§b§l" + arena.getName())
                .addLoreLines(
                    "§7Spawn Points: §f" + arena.getSpawnPoints().size(),
                    "§7Chest Locations: §f" + arena.getChestLocations().size(),
                    "",
                    "§eLeft-Click: §7Edit arena",
                    "§cRight-Click: §7Delete arena"
                );
            
            Item arenaItem = new AbstractItem() {
                @Override
                public ItemProvider getItemProvider() {
                    return builder;
                }
                
                @Override
                public void handleClick(ClickType clickType, Player player, org.bukkit.event.inventory.InventoryClickEvent event) {
                    player.closeInventory();
                    
                    if (clickType.isLeftClick()) {
                        // Edit arena
                        player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                            player.sendMessage(Component.text("§eEditing arena: §f" + arena.getName()));
                            plugin.getAdminWandListener().setSelectedArena(player, arena);
                            player.sendMessage(Component.text("§aArena selected! Use the admin wand to edit it."));
                        });
                    } else if (clickType.isRightClick()) {
                        // Delete arena
                        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_ITEM_BREAK, 0.5f, 1.0f);
                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                            player.sendMessage(Component.text("§cAre you sure you want to delete arena §f" + arena.getName() + "§c?"));
                            player.sendMessage(Component.text("§cType §f/sg arena delete " + arena.getName() + " §cto confirm."));
                        });
                    }
                }
            };
            
            arenaItems.add(arenaItem);
        }
        
        // Create the paged GUI
        PagedGui.Builder<Item> builder = PagedGui.items()
            .setStructure(
                "# # # # # # # # #",
                "# x x x x x x x #",
                "# x x x x x x x #",
                "# x x x x x x x #",
                "# b # < p > # n #"
            )
            .addIngredient('#', borderItem)
            .addIngredient('x', Markers.CONTENT_LIST_SLOT_HORIZONTAL)
            .addIngredient('<', new SimpleItem(new ItemBuilder(Material.ARROW).setDisplayName("§a§lPrevious Page")))
            .addIngredient('>', new SimpleItem(new ItemBuilder(Material.ARROW).setDisplayName("§a§lNext Page")))
            .addIngredient('p', new SimpleItem(new ItemBuilder(Material.PAPER)
                .setDisplayName("§f§lPage {page}/{pages}")
                .addLoreLines("§7Managing arenas")))
            .addIngredient('b', backButton)
            .addIngredient('n', newArenaButton)
            .setContent(arenaItems);
        
        Gui gui = builder.build();
        
        // Create and open the window
        Window window = Window.single()
            .setViewer(player)
            .setTitle("§8§lLumaSG §7- §fArena Management")
            .setGui(gui)
            .build();
        
        window.open();
    }
} 