package net.lumalyte.gui.menus;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.jetbrains.annotations.NotNull;

import net.lumalyte.LumaSG;
import net.lumalyte.gui.GameBrowserMenu;
import net.lumalyte.gui.GameSetupMenu;
import net.lumalyte.gui.LeaderboardMenu;
import net.lumalyte.gui.SetupMenu;
import net.lumalyte.util.MiniMessageUtils;
import xyz.xenondevs.invui.gui.Gui;
import xyz.xenondevs.invui.item.Item;
import xyz.xenondevs.invui.item.ItemProvider;
import xyz.xenondevs.invui.item.builder.ItemBuilder;
import xyz.xenondevs.invui.item.impl.AbstractItem;
import xyz.xenondevs.invui.item.impl.SimpleItem;
import xyz.xenondevs.invui.window.Window;

/**
 * Main menu GUI for LumaSG using InvUI
 */
public class MainMenu {
    private final @NotNull LumaSG plugin;

    public MainMenu(@NotNull LumaSG plugin) {
        this.plugin = plugin;
    }

    /**
     * Opens the main menu for a player
     */
    public void openMenu(@NotNull Player player) {
        // Create border item
        Item borderItem = new SimpleItem(new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).setDisplayName(""));

        // Create main menu items
        Item gameBrowserItem = createGameBrowserItem(player);
        Item createGameItem = createCreateGameItem(player);
        Item leaderboardItem = createLeaderboardItem(player);
        Item settingsItem = createSettingsItem(player);

        // Create the GUI structure
        Gui gui = Gui.normal()
            .setStructure(
                "# # # # # # # # #",
                "# . . . . . . . #",
                "# . g . c . l . #",
                "# . . . s . . . #",
                "# # # # # # # # #"
            )
            .addIngredient('#', borderItem)
            .addIngredient('g', gameBrowserItem)
            .addIngredient('c', createGameItem)
            .addIngredient('l', leaderboardItem)
            .addIngredient('s', settingsItem)
            .build();

        // Create window title
        String title = plugin.getConfig().getString("gui.main-menu.title", "§6§lLumaSG Main Menu");

        // Create and open window
        Window window = Window.single()
            .setViewer(player)
            .setTitle(title)
            .setGui(gui)
            .build();

        window.open();
    }

    /**
     * Creates the Game Browser menu item
     */
    private @NotNull Item createGameBrowserItem(@NotNull Player player) {
        String displayName = plugin.getConfig().getString("gui.main-menu.items.game-browser.name", 
            "§a§lGame Browser");
        
        return new AbstractItem() {
            @Override
            public ItemProvider getItemProvider() {
                return new ItemBuilder(Material.COMPASS)
                    .setDisplayName(displayName)
                    .addLoreLines("§7Browse and join active games", "", "§aClick to open");
            }

            @Override
            public void handleClick(@NotNull ClickType clickType, @NotNull Player player, @NotNull org.bukkit.event.inventory.InventoryClickEvent event) {
                if (clickType.isLeftClick()) {
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
                    player.closeInventory();
                    
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        try {
                            new GameBrowserMenu(plugin).openMenu(player);
                        } catch (Exception e) {
                            plugin.getDebugLogger().error("Failed to open Game Browser menu for " + player.getName(), e);
                            MiniMessageUtils.sendMessage(player, "<red>Failed to open Game Browser. Please try again.</red>");
                        }
                    });
                }
            }
        };
    }

    /**
     * Creates the Create Game menu item
     */
    private @NotNull Item createCreateGameItem(@NotNull Player player) {
        String displayName = plugin.getConfig().getString("gui.main-menu.items.create-game.name", 
            "§6§lCreate Game");
            
        return new AbstractItem() {
            @Override
            public ItemProvider getItemProvider() {
                return new ItemBuilder(Material.EMERALD)
                    .setDisplayName(displayName)
                    .addLoreLines("§7Set up a new game", "", "§aClick to create");
            }

            @Override
            public void handleClick(@NotNull ClickType clickType, @NotNull Player player, @NotNull org.bukkit.event.inventory.InventoryClickEvent event) {
                if (clickType.isLeftClick()) {
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
                    player.closeInventory();
                    
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        try {
                            new GameSetupMenu(plugin).openMenu(player);
                        } catch (Exception e) {
                            plugin.getDebugLogger().error("Failed to open Game Setup menu for " + player.getName(), e);
                            MiniMessageUtils.sendMessage(player, "<red>Failed to open Game Setup. Please try again.</red>");
                        }
                    });
                }
            }
        };
    }

    /**
     * Creates the Leaderboard menu item
     */
    private @NotNull Item createLeaderboardItem(@NotNull Player player) {
        String displayName = plugin.getConfig().getString("gui.main-menu.items.leaderboard.name", 
            "§e§lLeaderboard");
            
        return new AbstractItem() {
            @Override
            public ItemProvider getItemProvider() {
                return new ItemBuilder(Material.GOLDEN_SWORD)
                    .setDisplayName(displayName)
                    .addLoreLines("§7View top players and statistics", "", "§aClick to view");
            }

            @Override
            public void handleClick(@NotNull ClickType clickType, @NotNull Player player, @NotNull org.bukkit.event.inventory.InventoryClickEvent event) {
                if (clickType.isLeftClick()) {
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
                    player.closeInventory();
                    
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        try {
                            new LeaderboardMenu(plugin).openMenu(player);
                        } catch (Exception e) {
                            plugin.getDebugLogger().error("Failed to open Leaderboard menu for " + player.getName(), e);
                            MiniMessageUtils.sendMessage(player, "<red>Failed to open Leaderboard. Please try again.</red>");
                        }
                    });
                }
            }
        };
    }

    /**
     * Creates the Settings menu item (admin only)
     */
    private @NotNull Item createSettingsItem(@NotNull Player player) {
        String displayName = plugin.getConfig().getString("gui.main-menu.items.settings.name", 
            "§c§lSettings");
        
        boolean hasPermission = player.hasPermission("lumasg.admin") || player.hasPermission("lumasg.setup");
        
        return new AbstractItem() {
            @Override
            public ItemProvider getItemProvider() {
                ItemBuilder builder = new ItemBuilder(hasPermission ? Material.REDSTONE : Material.BARRIER)
                    .setDisplayName(displayName);
                    
                if (hasPermission) {
                    builder.addLoreLines("§7Configure plugin settings", "", "§aClick to open");
                } else {
                    builder.addLoreLines("§7Configure plugin settings", "", "§cRequires admin permission");
                }
                
                return builder;
            }

            @Override
            public void handleClick(@NotNull ClickType clickType, @NotNull Player player, @NotNull org.bukkit.event.inventory.InventoryClickEvent event) {
                if (clickType.isLeftClick()) {
                    if (hasPermission) {
                        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
                        player.closeInventory();
                        
                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                            try {
                                new SetupMenu(plugin).openMenu(player);
                            } catch (Exception e) {
                                plugin.getDebugLogger().error("Failed to open Setup menu for " + player.getName(), e);
                                MiniMessageUtils.sendMessage(player, "<red>Failed to open Settings. Please try again.</red>");
                            }
                        });
                    } else {
                        player.playSound(player.getLocation(), Sound.UI_LOOM_TAKE_RESULT, 0.5f, 0.5f);
                        MiniMessageUtils.sendMessage(player, "<red>You don't have permission to access settings!</red>");
                    }
                }
            }
        };
    }
} 