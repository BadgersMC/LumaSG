package net.lumalyte.gui;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import net.kyori.adventure.text.Component;
import net.lumalyte.LumaSG;

/**
 * Utility class for creating and managing menus.
 */
public class MenuUtils {
    private static LumaSG plugin;
    
    /**
     * Initialize the MenuUtils with the plugin instance.
     * 
     * @param pluginInstance The LumaSG plugin instance
     */
    public static void initialize(LumaSG pluginInstance) {
        plugin = pluginInstance;
    }
    
    /**
     * Get the plugin instance.
     * 
     * @return The LumaSG plugin instance
     */
    public static LumaSG getPlugin() {
        return plugin;
    }
    
    /**
     * Create an item stack with the specified material, name, and lore.
     * 
     * @param material The material for the item
     * @param name The display name for the item
     * @param lore The lore for the item
     * @return The created item stack
     */
    public static ItemStack createItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        
        if (meta != null) {
            if (name != null) {
                meta.displayName(Component.text(name));
            }
            
            if (lore != null && !lore.isEmpty()) {
                List<Component> loreComponents = new ArrayList<>();
                for (String line : lore) {
                    loreComponents.add(Component.text(line));
                }
                meta.lore(loreComponents);
            }
            
            item.setItemMeta(meta);
        }
        
        return item;
    }
    
    /**
     * Create an item stack with the specified material and name.
     * 
     * @param material The material for the item
     * @param name The display name for the item
     * @return The created item stack
     */
    public static ItemStack createItem(Material material, String name) {
        return createItem(material, name, null);
    }
    
    /**
     * Create a border item (black glass pane).
     * 
     * @return The border item
     */
    public static ItemStack createBorderItem() {
        return createItem(Material.BLACK_STAINED_GLASS_PANE, " ");
    }
    
    /**
     * Create a back button item.
     * 
     * @return The back button item
     */
    public static ItemStack createBackButton() {
        List<String> lore = new ArrayList<>();
        lore.add("Click to go back");
        return createItem(Material.ARROW, "§c§lBack", lore);
    }
    
    /**
     * Create a next page button item.
     * 
     * @return The next page button item
     */
    public static ItemStack createNextPageButton() {
        List<String> lore = new ArrayList<>();
        lore.add("Click to go to the next page");
        return createItem(Material.ARROW, "§a§lNext Page", lore);
    }
    
    /**
     * Create a previous page button item.
     * 
     * @return The previous page button item
     */
    public static ItemStack createPrevPageButton() {
        List<String> lore = new ArrayList<>();
        lore.add("Click to go to the previous page");
        return createItem(Material.ARROW, "§a§lPrevious Page", lore);
    }

    /**
     * Fills empty slots in an inventory with the specified filler item.
     * 
     * @param inventory The inventory to fill
     * @param fillerItem The item to use as filler
     */
    public static void fillEmptySlots(org.bukkit.inventory.Inventory inventory, ItemStack fillerItem) {
        if (inventory == null || fillerItem == null) {
            return;
        }
        
        for (int i = 0; i < inventory.getSize(); i++) {
            if (inventory.getItem(i) == null || inventory.getItem(i).getType() == Material.AIR) {
                inventory.setItem(i, fillerItem);
            }
        }
    }

    /**
     * Fills empty slots in an inventory with black glass panes.
     * 
     * @param inventory The inventory to fill
     */
    public static void fillEmptySlots(org.bukkit.inventory.Inventory inventory) {
        fillEmptySlots(inventory, createBorderItem());
    }
} 