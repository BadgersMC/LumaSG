package net.lumalyte.gui.menus;

import net.lumalyte.LumaSG;
import net.lumalyte.gui.MenuUtils;
import net.lumalyte.util.ItemUtils;
import net.lumalyte.util.MiniMessageUtils;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Main menu GUI for the LumaSG plugin.
 * Provides navigation to different game features and options.
 */
public class MainMenu {
    private final LumaSG plugin;

    public MainMenu(LumaSG plugin) {
        this.plugin = plugin;
    }

    /**
     * Opens the main menu for a player.
     */
    public void openMenu(Player player) {
        String title = plugin.getConfig().getString("gui.main-menu.title", "LumaSG Main Menu");
        int size = plugin.getConfig().getInt("gui.main-menu.size", 27);
        
        Inventory menu = plugin.getServer().createInventory(null, size, 
            MiniMessageUtils.deserializeLegacy(title));

        // Game Browser
        ItemStack gameBrowser = ItemUtils.createItem(
            Material.COMPASS,
            plugin.getConfig().getString("gui.main-menu.items.game-browser.name", "Game Browser"),
            plugin.getConfig().getStringList("gui.main-menu.items.game-browser.lore")
        );
        menu.setItem(plugin.getConfig().getInt("gui.main-menu.items.game-browser.slot", 11), gameBrowser);

        // Create Game
        ItemStack createGame = ItemUtils.createItem(
            Material.EMERALD,
            plugin.getConfig().getString("gui.main-menu.items.create-game.name", "Create Game"),
            plugin.getConfig().getStringList("gui.main-menu.items.create-game.lore")
        );
        menu.setItem(plugin.getConfig().getInt("gui.main-menu.items.create-game.slot", 13), createGame);

        // Leaderboard
        ItemStack leaderboard = ItemUtils.createItem(
            Material.GOLDEN_SWORD,
            plugin.getConfig().getString("gui.main-menu.items.leaderboard.name", "Leaderboard"),
            plugin.getConfig().getStringList("gui.main-menu.items.leaderboard.lore")
        );
        menu.setItem(plugin.getConfig().getInt("gui.main-menu.items.leaderboard.slot", 15), leaderboard);

        // Settings (if player has permission)
        if (player.hasPermission("lumasg.admin")) {
            ItemStack settings = ItemUtils.createItem(
                Material.REDSTONE,
                plugin.getConfig().getString("gui.main-menu.items.settings.name", "Settings"),
                plugin.getConfig().getStringList("gui.main-menu.items.settings.lore")
            );
            menu.setItem(plugin.getConfig().getInt("gui.main-menu.items.settings.slot", 22), settings);
        }

        // Fill empty slots with glass panes
        MenuUtils.fillEmptySlots(menu, Material.GRAY_STAINED_GLASS_PANE, " ");

        player.openInventory(menu);
    }
} 