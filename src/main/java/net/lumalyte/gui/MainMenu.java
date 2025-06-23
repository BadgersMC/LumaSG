package net.lumalyte.gui;

import net.lumalyte.LumaSG;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import xyz.xenondevs.invui.gui.Gui;
import xyz.xenondevs.invui.gui.PagedGui;
import xyz.xenondevs.invui.gui.structure.Markers;
import xyz.xenondevs.invui.item.Item;
import xyz.xenondevs.invui.item.ItemProvider;
import xyz.xenondevs.invui.item.builder.ItemBuilder;
import xyz.xenondevs.invui.item.impl.AbstractItem;
import xyz.xenondevs.invui.item.impl.SimpleItem;
import xyz.xenondevs.invui.window.Window;

import java.util.function.Consumer;

/**
 * Main menu for LumaSG plugin.
 */
public class MainMenu {
    private final LumaSG plugin;
    
    /**
     * Constructs a new MainMenu.
     * 
     * @param plugin The LumaSG plugin instance
     */
    public MainMenu(LumaSG plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Opens the main menu for a player.
     * 
     * @param player The player to open the menu for
     */
    public void openMenu(Player player) {
        // Create border item
        Item borderItem = new SimpleItem(new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).setDisplayName(""));
        
        // Create menu items
        Item joinGameItem = createMenuItem(
            Material.DIAMOND_SWORD, 
            "§b§lJoin Game", 
            "Click to view and join available games",
            player1 -> new GameBrowserMenu(plugin).openMenu(player1)
        );
        
        Item leaderboardItem = createMenuItem(
            Material.GOLDEN_HELMET, 
            "§6§lLeaderboards", 
            "Click to view the leaderboards",
            player1 -> new LeaderboardMenu(plugin).openMenu(player1)
        );
        
        Item setupItem = createMenuItem(
            Material.REDSTONE_TORCH, 
            "§c§lSetup", 
            "Click to access the setup menu",
            player1 -> {
                if (player1.hasPermission("lumasg.admin")) {
                    new SetupMenu(plugin).openMenu(player1);
                } else {
                    player1.sendMessage(Component.text("§cYou don't have permission to access this menu!"));
                }
            }
        );
        
        // Create the GUI
        Gui gui = Gui.normal()
            .setStructure(
                "# # # # # # # # #",
                "# . . . . . . . #",
                "# . 1 . 2 . 3 . #",
                "# . . . . . . . #",
                "# # # # # # # # #"
            )
            .addIngredient('#', borderItem)
            .addIngredient('1', joinGameItem)
            .addIngredient('2', leaderboardItem)
            .addIngredient('3', setupItem)
            .build();
        
        // Create and open the window
        Window window = Window.single()
            .setViewer(player)
            .setTitle("§8§lLumaSG §7- §fMain Menu")
            .setGui(gui)
            .build();
        
        window.open();
    }
    
    /**
     * Shows the main menu to a player (alias for openMenu).
     * 
     * @param player The player to show the menu to
     */
    public void show(Player player) {
        openMenu(player);
    }
    
    /**
     * Creates a menu item with the specified properties.
     * 
     * @param material The material for the item
     * @param name The display name for the item
     * @param description The description/lore for the item
     * @param clickHandler The click handler for the item
     * @return The created menu item
     */
    private Item createMenuItem(Material material, String name, String description, Consumer<Player> clickHandler) {
        return new AbstractItem() {
            @Override
            public ItemProvider getItemProvider() {
                ItemBuilder builder = new ItemBuilder(material)
                    .setDisplayName(name)
                    .addLoreLines(description);
                return builder;
            }
            
            @Override
            public void handleClick(ClickType clickType, Player player, org.bukkit.event.inventory.InventoryClickEvent event) {
                if (clickType.isLeftClick()) {
                    player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
                    // Close the inventory and run the click handler in the next tick
                    player.closeInventory();
                    plugin.getServer().getScheduler().runTask(plugin, () -> clickHandler.accept(player));
                }
            }
        };
    }
} 