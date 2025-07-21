package net.lumalyte.lumasg.util.core;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.lumalyte.lumasg.LumaSG;
import net.lumalyte.lumasg.arena.Arena;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

/**
 * Represents the admin wand used for managing spawn points in arenas.
 * The wand shows spawn point locations and allows adding new spawn points.
 */
public class AdminWand {
    
    private static final String WAND_KEY = "lumasg_admin_wand";
    private final LumaSG plugin;
    private final NamespacedKey wandKey;
    
    /** The debug logger instance for this admin wand */
    private final DebugLogger.ContextualLogger logger;
    
    public AdminWand(@NotNull LumaSG plugin) {
        this.plugin = plugin;
        this.wandKey = new NamespacedKey(plugin, WAND_KEY);
        this.logger = plugin.getDebugLogger().forContext("AdminWand");
    }
    
    /**
     * Creates a new admin wand item.
     * 
     * @return The admin wand item
     */
    public @NotNull ItemStack createWand() {
        ItemStack wand = new ItemStack(Material.BLAZE_ROD);
        ItemMeta meta = wand.getItemMeta();
        
        // Set display name and lore
        meta.displayName(Component.text("SG Admin Wand")
            .color(NamedTextColor.GOLD)
            .decoration(TextDecoration.ITALIC, false));
        
        meta.lore(java.util.List.of(
            Component.text("Right-click to add spawn points")
                .color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false),
            Component.text("Left-click to remove spawn points")
                .color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false),
            Component.text("Hold to view existing spawn points")
                .color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false)
        ));
        
        // Add identifier to the item
        meta.getPersistentDataContainer().set(wandKey, PersistentDataType.BYTE, (byte) 1);
        
        wand.setItemMeta(meta);
        return wand;
    }
    
    /**
     * Checks if an item is an admin wand.
     * 
     * @param item The item to check
     * @return true if the item is an admin wand
     */
    public boolean isWand(@NotNull ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        return item.getType() == Material.BLAZE_ROD && 
               meta != null && 
               meta.getPersistentDataContainer().has(wandKey, PersistentDataType.BYTE);
    }
    
    /**
     * Gives an admin wand to a player.
     * 
     * @param player The player to give the wand to
     */
    public void giveWand(@NotNull Player player) {
        if (!player.hasPermission("lumasg.admin")) {
            player.sendMessage(Component.text("You don't have permission to use the admin wand!")
                .color(NamedTextColor.RED));
            return;
        }
        
        ItemStack wand = createWand();
        player.getInventory().addItem(wand);
        
        // Check if the player is holding the wand in their main hand after giving it
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        if (isWand(mainHand)) {
            logger.debug("Player " + player.getName() + " is holding wand after receiving it");
            // Show spawn points if an arena is selected
            Arena selectedArena = plugin.getArenaManager().getSelectedArena(player);
            if (selectedArena != null) {
                selectedArena.showSpawnPoints();
                // Add to holding set in the listener
                plugin.getAdminWandListener().onWandGiven(player);
            }
        }
        
        player.sendMessage(Component.text("You have been given an SG Admin Wand!")
            .color(NamedTextColor.GREEN));
    }
} 
