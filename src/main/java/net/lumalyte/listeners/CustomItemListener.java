package net.lumalyte.listeners;

import net.lumalyte.LumaSG;
import net.lumalyte.customitems.CustomItem;
import net.lumalyte.customitems.CustomItemBehavior;
import net.lumalyte.customitems.CustomItemsManager;
import net.lumalyte.customitems.behaviors.AirdropBehavior;
import net.lumalyte.customitems.behaviors.PlayerTrackerBehavior;
import net.lumalyte.customitems.behaviors.ExplosiveBehavior;
import net.lumalyte.util.DebugLogger;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import java.util.Map;
import java.util.UUID;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Listener for custom item events and interactions.
 * 
 * <p>This listener handles all interactions with custom items including
 * right-click events, item switching, and cleanup when players leave.
 * It coordinates with the various behavior handlers to provide custom
 * functionality for each item type.</p>
 * 
 * @author LumaLyte
 * @version 1.0
 * @since 1.0
 */
public class CustomItemListener implements Listener {
    
    private final LumaSG plugin;
    private final DebugLogger.ContextualLogger logger;
    private final CustomItemsManager customItemsManager;
    private final PlayerTrackerBehavior playerTrackerBehavior;
    private final ExplosiveBehavior explosiveBehavior;
    private final Map<UUID, AirdropBehavior> activeAirdrops;
    
    /**
     * Creates a new custom item listener.
     * 
     * @param plugin The plugin instance
     */
    public CustomItemListener(@NotNull LumaSG plugin) {
        this.plugin = plugin;
        this.logger = plugin.getDebugLogger().forContext("CustomItemListener");
        this.customItemsManager = plugin.getCustomItemsManager();
        this.playerTrackerBehavior = new PlayerTrackerBehavior(plugin);
        this.explosiveBehavior = new ExplosiveBehavior(plugin);
        this.activeAirdrops = new ConcurrentHashMap<>();
        
        // Initialize behavior handlers
        this.playerTrackerBehavior.initialize();
        
        // Register as listener
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }
    
    /**
     * Handles player interactions with custom items.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(@NotNull PlayerInteractEvent event) {
        if (!event.hasItem()) {
            return;
        }
        
        ItemStack item = event.getItem();
        if (!customItemsManager.isCustomItem(item)) {
            return;
        }
        
        Player player = event.getPlayer();
        CustomItem customItem = customItemsManager.getCustomItemFromStack(item);
        if (customItem == null) {
            return;
        }
        
        // Handle different custom item behaviors
        switch (customItem.getBehaviorType()) {
            case PLAYER_TRACKER -> handlePlayerTracker(player, customItem, event);
            case FIRE_BOMB -> handleFireBomb(player, customItem, event);
            case POISON_BOMB -> handlePoisonBomb(player, customItem, event);
            case AIRDROP_FLARE -> handleAirdropFlare(player, customItem, event);
            case KNOCKBACK_STICK -> handleKnockbackStick(player, customItem, event);
            default -> logger.debug("No special interaction handling for custom item: " + customItem.getId());
        }
    }
    
    /**
     * Handles player tracker interactions.
     */
    private void handlePlayerTracker(@NotNull Player player, @NotNull CustomItem customItem, @NotNull PlayerInteractEvent event) {
        // Player tracker doesn't need special right-click handling
        // It's automatically updated by the PlayerTrackerBehavior
        logger.debug("Player " + player.getName() + " interacted with player tracker");
    }
    
    /**
     * Handles fire bomb interactions.
     */
    private void handleFireBomb(@NotNull Player player, @NotNull CustomItem customItem, @NotNull PlayerInteractEvent event) {
        explosiveBehavior.throwFireBomb(player, customItem, event);
        event.setCancelled(true); // Prevent normal TNT behavior
    }
    
    /**
     * Handles poison bomb interactions.
     */
    private void handlePoisonBomb(@NotNull Player player, @NotNull CustomItem customItem, @NotNull PlayerInteractEvent event) {
        explosiveBehavior.throwPoisonBomb(player, customItem, event);
        event.setCancelled(true); // Prevent normal TNT behavior
    }
    
    /**
     * Handles airdrop flare interactions.
     */
    private void handleAirdropFlare(@NotNull Player player, @NotNull CustomItem customItem, @NotNull PlayerInteractEvent event) {
        // Get the exact location where the flare was activated
        Location dropLocation;
        if (event.getClickedBlock() != null) {
            // If they clicked a block, use that block's location
            dropLocation = event.getClickedBlock().getLocation().add(0.5, 1, 0.5);
        } else {
            // If they clicked air, use their exact standing location
            dropLocation = player.getLocation().clone();
        }
        
        // Calculate spawn location - 50 blocks away
        double angle = Math.random() * 2 * Math.PI;
        double spawnDistance = 50.0;
        Location spawnLocation = dropLocation.clone().add(
            Math.cos(angle) * spawnDistance,
            150, // Reasonable height for meteor spawn
            Math.sin(angle) * spawnDistance
        );
        
        // Create new airdrop behavior instance for this activation
        AirdropBehavior airdropBehavior = new AirdropBehavior(plugin, dropLocation, spawnLocation, player);
        UUID airdropId = UUID.randomUUID();
        activeAirdrops.put(airdropId, airdropBehavior);
        
        // Activate the flare
        airdropBehavior.activateAirdropFlare(player, customItem, event);
        event.setCancelled(true); // Prevent normal torch behavior
    }
    
    /**
     * Handles knockback stick interactions.
     */
    private void handleKnockbackStick(@NotNull Player player, @NotNull CustomItem customItem, @NotNull PlayerInteractEvent event) {
        // Knockback stick doesn't need special right-click handling
        // The knockback is handled by the enchantment
        logger.debug("Player " + player.getName() + " interacted with knockback stick");
    }
    
    /**
     * Handles when a player switches to holding a custom item.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerItemHeld(@NotNull PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        ItemStack newItem = player.getInventory().getItem(event.getNewSlot());
        ItemStack oldItem = player.getInventory().getItem(event.getPreviousSlot());
        
        // Handle switching away from a custom item
        if (oldItem != null && customItemsManager.isCustomItem(oldItem)) {
            handleItemSwitchedAway(player, oldItem);
        }
        
        // Handle switching to a custom item
        if (newItem != null && customItemsManager.isCustomItem(newItem)) {
            handleItemSwitchedTo(player, newItem);
        }
    }
    
    /**
     * Handles when a player switches away from a custom item.
     */
    private void handleItemSwitchedAway(@NotNull Player player, @NotNull ItemStack item) {
        CustomItem customItem = customItemsManager.getCustomItemFromStack(item);
        if (customItem == null) {
            return;
        }
        
        // Handle behavior-specific cleanup
        if (customItem.getBehaviorType() == CustomItemBehavior.PLAYER_TRACKER) {
            playerTrackerBehavior.unregisterTracker(player);
        }
    }
    
    /**
     * Handles when a player switches to a custom item.
     */
    private void handleItemSwitchedTo(@NotNull Player player, @NotNull ItemStack item) {
        CustomItem customItem = customItemsManager.getCustomItemFromStack(item);
        if (customItem == null) {
            return;
        }
        
        // Handle behavior-specific setup
        if (customItem.getBehaviorType() == CustomItemBehavior.PLAYER_TRACKER) {
            playerTrackerBehavior.registerTracker(player, customItem);
        }
    }
    
    /**
     * Handles when a player drops a custom item.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDropItem(@NotNull PlayerDropItemEvent event) {
        ItemStack item = event.getItemDrop().getItemStack();
        if (!customItemsManager.isCustomItem(item)) {
            return;
        }
        
        Player player = event.getPlayer();
        CustomItem customItem = customItemsManager.getCustomItemFromStack(item);
        if (customItem == null) {
            return;
        }
        
        // Handle behavior-specific cleanup when item is dropped
        if (customItem.getBehaviorType() == CustomItemBehavior.PLAYER_TRACKER) {
            playerTrackerBehavior.unregisterTracker(player);
        }
        
        logger.debug("Player " + player.getName() + " dropped custom item: " + customItem.getId());
    }
    
    /**
     * Handles when a player quits the server.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(@NotNull PlayerQuitEvent event) {
        Player player = event.getPlayer();
        
        // Clean up any active custom item behaviors for this player
        playerTrackerBehavior.unregisterTracker(player);
        
        logger.debug("Cleaned up custom item behaviors for player: " + player.getName());
    }
    
    /**
     * Shuts down the custom item listener and all behavior handlers.
     */
    public void shutdown() {
        playerTrackerBehavior.shutdown();
        explosiveBehavior.shutdown();
        // Shutdown all active airdrops
        for (AirdropBehavior airdropBehavior : activeAirdrops.values()) {
            airdropBehavior.shutdown();
        }
        activeAirdrops.clear();
        logger.info("Custom item listener shut down");
    }
    
    /**
     * Gets the locations of all active airdrops.
     * 
     * @return Map of airdrop IDs to their locations
     */
    public Map<UUID, Location> getActiveAirdropLocations() {
        Map<UUID, Location> locations = new HashMap<>();
        for (Map.Entry<UUID, AirdropBehavior> entry : activeAirdrops.entrySet()) {
            locations.putAll(entry.getValue().getActiveAirdropLocations());
        }
        return locations;
    }
} 