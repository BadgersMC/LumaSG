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

import java.util.*;

/**
 * Handles all interactions with the SG Admin Wand.
 */
public class AdminWandListener implements Listener {
    
    private final AdminWand adminWand;
    private final ArenaManager arenaManager;
    
    /** Debug logger instance for admin wand listener */
    private final @NotNull DebugLogger.ContextualLogger logger;
    
    private final Map<UUID, Arena> selectedArenas;
    private final Set<UUID> playersHoldingWand;
    
    public AdminWandListener(@NotNull LumaSG plugin) {
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
        
        if (!isValidWandInteraction(item, event.getClickedBlock())) {
            return;
        }
        
        // Cancel the event to prevent normal item usage
        event.setCancelled(true);
        
        Arena selectedArena = validateArenaSelection(player);
        if (selectedArena == null) {
            return;
        }

        // Handle the interaction based on click type
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            handleSpawnPointAddition(player, selectedArena, Objects.requireNonNull(event.getClickedBlock()));
        } else if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            handleSpawnPointRemoval(player, selectedArena, Objects.requireNonNull(event.getClickedBlock()).getLocation());
        }
    }
    
    /**
     * Validates if the interaction is a valid wand interaction.
     * @param item The item being used
     * @param clickedBlock The block that was clicked
     * @return true if the interaction is valid, false otherwise
     */
    private boolean isValidWandInteraction(@Nullable ItemStack item, @Nullable Block clickedBlock) {
        return item != null && adminWand.isWand(item) && clickedBlock != null;
    }
    
    /**
     * Validates and retrieves the selected arena for a player.
     * @param player The player to check
     * @return The selected arena, or null if no arena is selected
     */
    private @Nullable Arena validateArenaSelection(@NotNull Player player) {
        Arena selectedArena = selectedArenas.get(player.getUniqueId());
        
        if (selectedArena == null) {
            logger.debug("No arena selected for player: " + player.getName() + ", selectedArenas map size: " + selectedArenas.size());
            logArenaSelectionState();
            player.sendMessage(Component.text("Please select an arena first using /sg arena select <n>")
                .color(NamedTextColor.RED));
            return null;
        }

        logger.debug("Player " + player.getName() + " interacting with wand, selected arena: " + selectedArena.getName());
        return selectedArena;
    }
    
    /**
     * Logs the current state of arena selections for debugging.
     */
    private void logArenaSelectionState() {
        for (Map.Entry<UUID, Arena> entry : selectedArenas.entrySet()) {
            Player mapPlayer = Bukkit.getPlayer(entry.getKey());
            String playerName = mapPlayer != null ? mapPlayer.getName() : "Unknown";
            logger.debug("Map entry: " + playerName + " -> " + entry.getValue().getName());
        }
    }
    
    /**
     * Handles adding a spawn point at the clicked location.
     * @param player The player adding the spawn point
     * @param arena The arena to add the spawn point to
     * @param clickedBlock The block that was clicked
     */
    private void handleSpawnPointAddition(@NotNull Player player, @NotNull Arena arena, @NotNull Block clickedBlock) {
        Location spawnLoc = clickedBlock.getLocation().add(0.5, 1, 0.5);
        arena.addSpawnPoint(spawnLoc);
        
        // Schedule a debounced save to prevent excessive disk I/O
        arenaManager.saveArenas();
        
        sendSpawnPointMessage(player, "Added spawn point at ", spawnLoc);
        refreshSpawnPointVisualization(arena);
    }
    
    /**
     * Handles removing a spawn point near the clicked location.
     * @param player The player removing the spawn point
     * @param arena The arena to remove the spawn point from
     * @param clickedLoc The location that was clicked
     */
    private void handleSpawnPointRemoval(@NotNull Player player, @NotNull Arena arena, @NotNull Location clickedLoc) {
        Location nearestSpawn = findNearestSpawnPoint(arena, clickedLoc);
        
        if (nearestSpawn != null) {
            int index = arena.getSpawnPoints().indexOf(nearestSpawn);
            arena.removeSpawnPoint(index);
            refreshSpawnPointVisualization(arena);
            sendSpawnPointMessage(player, "Removed spawn point at ", nearestSpawn);
        } else {
            player.sendMessage(Component.text("No spawn point found near this location")
                .color(NamedTextColor.RED));
        }
    }
    
    /**
     * Finds the nearest spawn point within 1 block of the clicked location.
     * @param arena The arena to search in
     * @param clickedLoc The location to search around
     * @return The nearest spawn point, or null if none found
     */
    private @Nullable Location findNearestSpawnPoint(@NotNull Arena arena, @NotNull Location clickedLoc) {
        return arena.getSpawnPoints().stream()
            .filter(spawnPoint -> isLocationNearby(spawnPoint, clickedLoc))
            .findFirst()
            .orElse(null);
    }
    
    /**
     * Checks if two locations are within 1 block of each other.
     * @param loc1 The first location
     * @param loc2 The second location
     * @return true if the locations are within 1 block, false otherwise
     */
    private boolean isLocationNearby(@NotNull Location loc1, @NotNull Location loc2) {
        return loc1.getWorld() == loc2.getWorld() &&
            Math.abs(loc1.getBlockX() - loc2.getBlockX()) <= 1 &&
            Math.abs(loc1.getBlockY() - loc2.getBlockY()) <= 1 &&
            Math.abs(loc1.getBlockZ() - loc2.getBlockZ()) <= 1;
    }
    
    /**
     * Sends a spawn point action message to a player.
     * @param player The player to send the message to
     * @param prefix The message prefix
     * @param location The location to include in the message
     */
    private void sendSpawnPointMessage(@NotNull Player player, @NotNull String prefix, @NotNull Location location) {
        player.sendMessage(Component.text(prefix)
            .color(NamedTextColor.GREEN)
            .append(Component.text(String.format("(%.1f, %.1f, %.1f)", 
                location.getX(), location.getY(), location.getZ()))
                .color(NamedTextColor.YELLOW)));
    }
    
    /**
     * Refreshes the spawn point visualization for an arena.
     * @param arena The arena to refresh
     */
    private void refreshSpawnPointVisualization(@NotNull Arena arena) {
        arena.hideSpawnPoints();
        arena.showSpawnPoints();
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
        if (!(event.getWhoClicked() instanceof Player player)) return;

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
        if (adminWand.isWand(mainHandItem) || adminWand.isWand(offHandItem)) {
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