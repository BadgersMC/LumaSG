package net.lumalyte.lumasg.listeners;

import java.util.HashSet;
import java.util.Set;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import net.lumalyte.lumasg.LumaSG;
import net.lumalyte.lumasg.chest.ChestManager;
import net.lumalyte.lumasg.game.Game;
import net.lumalyte.lumasg.game.GameManager;
import net.lumalyte.lumasg.game.GameState;
import net.lumalyte.lumasg.util.core.DebugLogger;

/**
 * Handles all chest-related events in Survival Games.
 * 
 * <p>This listener manages chest interactions, including opening, breaking,
 * and placing chests. It ensures that chests are properly filled with loot
 * when opened and prevents unauthorized chest manipulation during games.</p>
 * 
 * <p>The ChestListener works with the ChestManager to populate chests with
 * appropriate loot and enforces game rules regarding chest access and
 * modification.</p>
 * 
 * @author LumaLyte
 * @version 1.0
 * @since 1.0
 */
public class ChestListener implements Listener {
    
    /** The plugin instance for accessing managers and configuration */
    private final LumaSG plugin;
    
    /** The chest manager for handling loot generation and chest operations */
    // For future use
    @SuppressWarnings("unused")
    private final ChestManager chestManager;
    
    /** The game manager for checking game states and player participation */
    private final GameManager gameManager;
    
    /** The debug logger instance for this chest listener */
    private final @NotNull DebugLogger.ContextualLogger logger;
    
    /** The set of opened chests */
    private final Set<Block> openedChests;

	/**
     * Constructs a new ChestListener instance.
     * 
     * @param plugin The plugin instance
     */
    public ChestListener(@NotNull LumaSG plugin) {
        this.plugin = plugin;
        this.chestManager = plugin.getChestManager();
        this.gameManager = plugin.getGameManager();
        this.logger = plugin.getDebugLogger().forContext("ChestListener");
        this.openedChests = new HashSet<>();
    }
    
    /**
     * Handles chest opening events.
     * 
     * <p>When a player opens a chest, this method checks if the chest needs
     * to be filled with loot. Chests in Survival Games arenas are typically
     * filled with random loot when first opened by a player.</p>
     * 
     * @param event The inventory open event
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onChestOpen(@NotNull InventoryOpenEvent event) {
        if (!isPlayerEvent(event)) {
            return; // Only handle player interactions
        }
        
        Player player = (Player) event.getPlayer();
        Inventory inventory = event.getInventory();
        
        try {
            processChestOpenEvent(player, inventory);
        } catch (Exception e) {
            logger.warn("Error handling chest open for " + player.getName(), e);
        }
    }
    
    /**
     * Checks if the event involves a player.
     */
    private boolean isPlayerEvent(@NotNull InventoryOpenEvent event) {
        return event.getPlayer() instanceof Player;
    }
    
    /**
     * Processes the chest open event for the given player and inventory.
     */
    private void processChestOpenEvent(@NotNull Player player, @NotNull Inventory inventory) {
        // Check if this is a chest inventory
        if (!(inventory.getHolder() instanceof Chest chest)) {
            return;
        }

		Game game = gameManager.getGameByPlayer(player);
        
        if (isPlayerInActiveGame(game)) {
            // Player is in an active game, check if chest needs filling
            handleChestInGame(chest, player, game);
        } else {
            // Player is not in a game, check if chest is in an arena
            handleChestOutsideGame();
        }
    }
    
    /**
     * Checks if the player is in an active game (not waiting).
     */
    private boolean isPlayerInActiveGame(@Nullable Game game) {
        return game != null && game.getState() != GameState.WAITING;
    }
    
    /**
     * Handles chest breaking events.
     * 
     * <p>This method may prevent players from breaking chests during games
     * to maintain game balance and prevent griefing. Chest breaking rules
     * can be configured per game state.</p>
     * 
     * @param event The block break event
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onChestBreak(@NotNull BlockBreakEvent event) {
        Block block = event.getBlock();
        Player player = event.getPlayer();
        
        try {
            // Check if the broken block is a chest
            if (block.getType() == Material.CHEST) {
                // Check if player is in a game
                Game game = gameManager.getGameByPlayer(player);
                if (game != null && game.getState() != GameState.WAITING) {
                    // Player is in an active game, check chest breaking rules
                    boolean allowChestBreaking = plugin.getConfig().getBoolean("game.allow-chest-breaking", false);
                    if (!allowChestBreaking) {
                        event.setCancelled(true);
                        player.sendMessage("§cYou cannot break chests during the game!");
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Error handling chest break for " + player.getName(), e);
        }
    }
    
    /**
     * Handles chest placement events.
     * 
     * <p>This method may restrict chest placement during games to prevent
     * players from creating additional storage or hiding items. Chest
     * placement rules can be configured per game state.</p>
     * 
     * @param event The block place event
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onChestPlace(@NotNull BlockPlaceEvent event) {
        Block block = event.getBlock();
        Player player = event.getPlayer();
        
        try {
            // Check if the placed block is a chest
            if (block.getType() == Material.CHEST) {
                // Check if player is in a game
                Game game = gameManager.getGameByPlayer(player);
                if (game != null && game.getState() != GameState.WAITING) {
                    // Player is in an active game, check chest placement rules
                    boolean allowChestPlacement = plugin.getConfig().getBoolean("game.allow-chest-placement", false);
                    if (!allowChestPlacement) {
                        event.setCancelled(true);
                        player.sendMessage("§cYou cannot place chests during the game!");
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Error handling chest place for " + player.getName(), e);
        }
    }
    
    /**
     * Handles inventory click events in chests.
     * 
     * <p>This method may restrict certain inventory interactions during
     * games, such as preventing players from taking items from chests
     * that haven't been properly filled or restricting item movement.</p>
     * 
     * @param event The inventory click event
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(@NotNull InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return; // Only handle player interactions
        }

		Inventory inventory = event.getInventory();
        
        try {
            // Check if this is a chest inventory
            if (inventory.getHolder() instanceof Chest) {
                // Check if player is in a game
                Game game = gameManager.getGameByPlayer(player);
                if (game != null && game.getState() != GameState.WAITING) {
                    // Player is in an active game, handle chest interaction
                    handleChestInventoryInteraction();
                }
            }
        } catch (Exception e) {
            logger.warn("Error handling inventory click for " + player.getName(), e);
        }
    }
    
    /**
     * Handles chest interactions when a player is in an active game.
     * 
     * @param chest The chest being opened
     * @param player The player opening the chest
     * @param game The game the player is in
     */
    private void handleChestInGame(@NotNull Chest chest, @NotNull Player player, @NotNull Game game) {
        Block chestBlock = chest.getBlock();
        
        // Check if this chest is in the game arena and hasn't been opened yet
        if (isChestInArena(chestBlock.getLocation(), game) && !openedChests.contains(chestBlock)) {
            // Fill the chest with loot
            chestManager.fillChest(chestBlock.getLocation());
            openedChests.add(chestBlock);
            
            // Record chest opened statistic if enabled
            if (plugin.getConfig().getBoolean("statistics.enabled", true)) {
                plugin.getStatisticsManager().recordChestOpened(player.getUniqueId());
            }
            
            // Also record in the game instance for per-game tracking
            game.recordChestOpened(player.getUniqueId());
        }
    }
    
    /**
     * Handles chest interactions when a player is not in a game.
     */
    private void handleChestOutsideGame() {
        // For now, we don't do anything special for chests outside of games
        // This could be extended in the future for lobby chests or other features
    }

    
    /**
     * Handles inventory click events within chest inventories during games.
     */
    private void handleChestInventoryInteraction() {
        // For now, we allow all chest interactions during games
        // This could be extended to add restrictions or special behaviors
    }
    
    /**
     * Checks if a chest location is within a game arena.
     * 
     * @param location The location to check
     * @param game The game to check against
     * @return true if the chest is in the arena, false otherwise
     */
    private boolean isChestInArena(@NotNull org.bukkit.Location location, @NotNull Game game) {
        // This is a simplified check - in a real implementation, you'd check
        // against the arena's actual boundaries
        return location.getWorld().equals(game.getArena().getWorld());
    }
    
    /**
     * Clears the set of opened chests (typically called when a game ends).
     */
    public void clearOpenedChests() {
        openedChests.clear();
    }
} 
