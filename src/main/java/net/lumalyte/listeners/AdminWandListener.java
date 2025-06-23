package net.lumalyte.listeners;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.lumalyte.LumaSG;
import net.lumalyte.arena.Arena;
import net.lumalyte.arena.ArenaManager;
import net.lumalyte.util.AdminWand;
import net.lumalyte.util.DebugLogger;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.bukkit.Bukkit;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.HashSet;
import java.util.Set;

/**
 * Handles all interactions with the SG Admin Wand.
 */
public class AdminWandListener implements Listener {
    
    private final LumaSG plugin;
    private final AdminWand adminWand;
    private final ArenaManager arenaManager;
    
    /** Debug logger instance for admin wand listener */
    private final @NotNull DebugLogger.ContextualLogger logger;
    
    private final Map<UUID, Arena> selectedArenas;
    private final Set<UUID> playersHoldingWand;
    
    public AdminWandListener(@NotNull LumaSG plugin) {
        this.plugin = plugin;
        this.adminWand = new AdminWand(plugin);
        this.arenaManager = plugin.getArenaManager();
        this.logger = plugin.getDebugLogger().forContext("AdminWandListener");
        this.selectedArenas = new HashMap<>();
        this.playersHoldingWand = new HashSet<>();
    }
    
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerInteract(@NotNull PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        
        if (item == null || !adminWand.isWand(item)) return;
        
        // Cancel the event to prevent normal item usage
        event.setCancelled(true);
        
        // Get the selected arena directly from the map for consistency
        Arena selectedArena = selectedArenas.get(player.getUniqueId());
        logger.debug("Player " + player.getName() + " interacting with wand, selected arena: " + 
            (selectedArena != null ? selectedArena.getName() : "null"));
            
        if (selectedArena == null) {
            logger.debug("No arena selected for player: " + player.getName() + ", selectedArenas map size: " + selectedArenas.size());
            // Log admin wand interactions for audit purposes
            for (Map.Entry<UUID, Arena> entry : selectedArenas.entrySet()) {
                Player mapPlayer = Bukkit.getPlayer(entry.getKey());
                String playerName = mapPlayer != null ? mapPlayer.getName() : "Unknown";
                logger.debug("Map entry: " + playerName + " -> " + entry.getValue().getName());
            }
            
            player.sendMessage(Component.text("Please select an arena first using /sg arena select <n>")
                .color(NamedTextColor.RED));
            return;
        }

        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null) return;
        
        // Handle right clicks to add spawn points
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            // Add spawn point at clicked location
            Location spawnLoc = clickedBlock.getLocation().add(0.5, 1, 0.5);
            selectedArena.addSpawnPoint(spawnLoc);
            
            // Schedule a debounced save to prevent excessive disk I/O
            arenaManager.saveArenas();
            
            player.sendMessage(Component.text("Added spawn point at ")
                .color(NamedTextColor.GREEN)
                .append(Component.text(String.format("(%.1f, %.1f, %.1f)", 
                    spawnLoc.getX(), spawnLoc.getY(), spawnLoc.getZ()))
                    .color(NamedTextColor.YELLOW)));
                    
            // Refresh spawn point visualization
            selectedArena.hideSpawnPoints();
            selectedArena.showSpawnPoints();
        }
        // Handle left clicks to remove spawn points
        else if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            Location clickedLoc = clickedBlock.getLocation();
            List<Location> spawnPoints = selectedArena.getSpawnPoints();
            
            // Find the closest spawn point within 1 block
            Location removedSpawn = null;
            int removedIndex = -1;
            for (int i = 0; i < spawnPoints.size(); i++) {
                Location spawnPoint = spawnPoints.get(i);
                if (spawnPoint.getWorld() == clickedLoc.getWorld() &&
                    Math.abs(spawnPoint.getBlockX() - clickedLoc.getBlockX()) <= 1 &&
                    Math.abs(spawnPoint.getBlockY() - clickedLoc.getBlockY()) <= 1 &&
                    Math.abs(spawnPoint.getBlockZ() - clickedLoc.getBlockZ()) <= 1) {
                    removedSpawn = spawnPoint;
                    removedIndex = i;
                    break;
                }
            }
            
            if (removedSpawn != null && removedIndex >= 0) {
                // Use the Arena's removeSpawnPoint method instead of directly modifying the list
                selectedArena.removeSpawnPoint(removedIndex);
                selectedArena.hideSpawnPoints();
                selectedArena.showSpawnPoints();
                
                player.sendMessage(Component.text("Removed spawn point at ")
                    .color(NamedTextColor.GREEN)
                    .append(Component.text(String.format("(%.1f, %.1f, %.1f)", 
                        removedSpawn.getX(), removedSpawn.getY(), removedSpawn.getZ()))
                        .color(NamedTextColor.YELLOW)));
            } else {
                player.sendMessage(Component.text("No spawn point found near this location")
                    .color(NamedTextColor.RED));
            }
        }
    }
    
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerItemHeld(@NotNull PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        
        // Check old slot for wand
        ItemStack oldItem = player.getInventory().getItem(event.getPreviousSlot());
        if (oldItem != null && adminWand.isWand(oldItem)) {
            logger.debug("Player " + player.getName() + " switched away from wand");
            // Hide beacons when switching away from wand
            Arena selectedArena = selectedArenas.get(playerId);
            if (selectedArena != null) {
                selectedArena.hideSpawnPoints();
                logger.debug("Hiding spawn points for " + player.getName() + " in arena " + selectedArena.getName());
            }
            playersHoldingWand.remove(playerId);
            // IMPORTANT: Do NOT remove the selected arena when switching away from the wand
            // selectedArenas.remove(playerId); - This was causing the issue
        }
        
        // Check new slot for wand
        ItemStack newItem = player.getInventory().getItem(event.getNewSlot());
        if (newItem != null && adminWand.isWand(newItem)) {
            logger.debug("Player " + player.getName() + " switched to wand");
            // Show beacons when switching to wand
            Arena selectedArena = selectedArenas.get(playerId);
            if (selectedArena != null) {
                selectedArena.showSpawnPoints();
                playersHoldingWand.add(playerId);
                logger.debug("Showing spawn points for " + player.getName() + " in arena " + selectedArena.getName() + 
                    ", points: " + selectedArena.getSpawnPoints().size());
            } else {
                player.sendMessage(Component.text("Please select an arena first using /sg arena select <n>")
                    .color(NamedTextColor.RED));
                logger.debug("Player " + player.getName() + " has no selected arena");
            }
        }
    }
    
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerDropItem(@NotNull PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        ItemStack droppedItem = event.getItemDrop().getItemStack();
        
        if (adminWand.isWand(droppedItem)) {
            // Hide beacons when dropping wand
            Arena selectedArena = selectedArenas.get(player.getUniqueId());
            if (selectedArena != null) {
                selectedArena.hideSpawnPoints();
                logger.debug("Hiding spawn points due to wand drop for " + player.getName());
            }
            playersHoldingWand.remove(player.getUniqueId());
            // Do NOT remove the selected arena when dropping the wand
        }
    }
    
    @EventHandler(priority = EventPriority.NORMAL)
    public void onInventoryClick(@NotNull InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        
        // Check if the clicked item is a wand
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem != null && adminWand.isWand(clickedItem)) {
            // Hide beacons when moving wand in inventory
            Arena selectedArena = selectedArenas.get(player.getUniqueId());
            if (selectedArena != null) {
                selectedArena.hideSpawnPoints();
                logger.debug("Hiding spawn points due to inventory click for " + player.getName());
            }
            playersHoldingWand.remove(player.getUniqueId());
            // Do NOT remove the selected arena when moving the wand in inventory
        }
    }
    
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerSwapHandItems(@NotNull PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        ItemStack mainHandItem = event.getMainHandItem();
        ItemStack offHandItem = event.getOffHandItem();
        
        // Check if either item is a wand
        if ((mainHandItem != null && adminWand.isWand(mainHandItem)) ||
            (offHandItem != null && adminWand.isWand(offHandItem))) {
            // Hide beacons when swapping wand
            Arena selectedArena = selectedArenas.get(player.getUniqueId());
            if (selectedArena != null) {
                selectedArena.hideSpawnPoints();
                logger.debug("Hiding spawn points due to hand swap for " + player.getName());
            }
            playersHoldingWand.remove(player.getUniqueId());
            // Do NOT remove the selected arena when swapping the wand
        }
    }
    
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerQuit(@NotNull PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        
        // Clean up when player leaves
        if (playersHoldingWand.contains(playerId)) {
            Arena selectedArena = selectedArenas.get(playerId);
            if (selectedArena != null) {
                selectedArena.hideSpawnPoints();
                logger.debug("Hiding spawn points due to player quit: " + player.getName());
            }
            playersHoldingWand.remove(playerId);
        }
        
        // Only remove from selectedArenas when the player actually quits the server
        selectedArenas.remove(playerId);
        logger.debug("Removed arena selection for player due to quit: " + player.getName());
    }
    
    /**
     * Sets the selected arena for a player.
     */
    public void setSelectedArena(@NotNull Player player, @NotNull Arena arena) {
        // Hide beacons of previously selected arena
        Arena previousArena = selectedArenas.get(player.getUniqueId());
        if (previousArena != null) {
            previousArena.hideSpawnPoints();
            logger.debug("Hiding spawn points for previous arena: " + previousArena.getName());
        }
        
        // Set new arena and show its beacons if holding wand
        selectedArenas.put(player.getUniqueId(), arena);
        logger.debug("Set selected arena for " + player.getName() + " to: " + arena.getName());
        
        ItemStack heldItem = player.getInventory().getItemInMainHand();
        if (adminWand.isWand(heldItem)) {
            arena.showSpawnPoints();
            playersHoldingWand.add(player.getUniqueId());
        } else {
            logger.debug("Player is not holding wand, spawn points not shown for: " + arena.getName());
        }
        
        player.sendMessage(Component.text("Selected arena: ")
            .color(NamedTextColor.GREEN)
            .append(Component.text(arena.getName())
                .color(NamedTextColor.YELLOW)));
    }
    
    /**
     * Gets the selected arena for a player.
     * 
     * @param player The player to get the selected arena for
     * @return The selected arena, or null if none is selected
     */
    public @Nullable Arena getSelectedArena(@NotNull Player player) {
        Arena arena = selectedArenas.get(player.getUniqueId());
        logger.debug("Getting selected arena for " + player.getName() + ": " + 
            (arena != null ? arena.getName() : "null"));
        return arena;
    }
    
    /**
     * Called when a wand is given to a player.
     * This ensures the player is properly tracked if they're holding the wand when it's given.
     */
    public void onWandGiven(@NotNull Player player) {
        logger.debug("Registering wand given to player: " + player.getName());
        playersHoldingWand.add(player.getUniqueId());
    }
    
    /**
     * Gets the admin wand instance.
     */
    public @NotNull AdminWand getAdminWand() {
        return adminWand;
    }
    
    /**
     * Gives the admin wand to a player.
     * 
     * @param player The player to give the wand to
     */
    public void giveWand(@NotNull Player player) {
        ItemStack wandItem = adminWand.createWand();
        player.getInventory().addItem(wandItem);
        onWandGiven(player);
    }
} 