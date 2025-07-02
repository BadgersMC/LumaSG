package net.lumalyte.listeners;

import net.lumalyte.LumaSG;
import net.lumalyte.game.Game;
import net.lumalyte.game.GameManager;
import net.lumalyte.game.GameState;
import net.lumalyte.util.DebugLogger;
import net.lumalyte.util.MessageUtils;
import net.lumalyte.util.PlayerDataCache;
import net.lumalyte.util.SkinCache;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Handles all player-related events in Survival Games.
 * 
 * <p>This listener manages player interactions with the game, including
 * joining/leaving games, PvP combat, death handling, and various game
 * state restrictions. It ensures players follow the rules and mechanics
 * of Survival Games.</p>
 * 
 * <p>The PlayerListener works closely with the GameManager to track
 * player states and enforce game rules such as PvP restrictions during
 * grace periods and arena boundaries.</p>
 * 
 * @author LumaLyte
 * @version 1.0
 * @since 1.0
 */
public class PlayerListener implements Listener {
    
    /** The plugin instance for accessing managers and configuration */
    private final @NotNull LumaSG plugin;
    
    /** The game manager for tracking active games and players */
    private final GameManager gameManager;
    
    /** The debug logger instance for this player listener */
    private final @NotNull DebugLogger.ContextualLogger logger;
    
    /**
     * Constructs a new PlayerListener instance.
     * 
     * @param plugin The plugin instance
     */
    public PlayerListener(@NotNull LumaSG plugin) {
        this.plugin = plugin;
        this.gameManager = plugin.getGameManager();
        this.logger = plugin.getDebugLogger().forContext("PlayerListener");
    }
    
    /**
     * Handles player join events.
     * 
     * <p>When a player joins the server, this method checks if they were
     * previously in a game and handles their reconnection appropriately.
     * Disconnected players may be allowed to rejoin ongoing games depending
     * on server configuration.</p>
     * 
     * @param event The player join event
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerJoin(@NotNull PlayerJoinEvent event) {
        Player player = event.getPlayer();
        
        try {
            // Preload caching systems for better performance
            preloadPlayerCaches(player);
            
            // Preload player statistics if enabled
            if (plugin.getConfig().getBoolean("statistics.enabled", true) && 
                plugin.getConfig().getBoolean("statistics.preload-on-join", true)) {
                plugin.getStatisticsManager().preloadPlayerStats(player);
            }
            
            // Check if player was in a game when they disconnected and can rejoin
            Game game = gameManager.getGameByPlayer(player);
            if (game != null && game.getDisconnectedPlayers().contains(player.getUniqueId())) {
                // Player was disconnected during game, handle reconnection
                handlePlayerReconnection(player, game);
            }
        } catch (Exception e) {
            logger.warn("Error handling player join for " + player.getName(), e);
        }
    }
    
    /**
     * Preloads caching systems for a player to improve performance
     * 
     * @param player The player to preload caches for
     */
    private void preloadPlayerCaches(@NotNull Player player) {
        try {
            // Preload skin cache for better GUI performance
            SkinCache.preloadSkin(player);
            
            // Preload player data cache for better game performance
            PlayerDataCache.preloadPlayerData(player);
            
            logger.debug("Preloaded caches for player: " + player.getName());
        } catch (Exception e) {
            logger.warn("Failed to preload caches for player: " + player.getName(), e);
        }
    }
    
    /**
     * Handles player quit events.
     * 
     * <p>When a player leaves the server, this method removes them from
     * any active games and handles their inventory and state cleanup.
     * The player's game state is preserved for potential reconnection.</p>
     * 
     * @param event The player quit event
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerQuit(@NotNull PlayerQuitEvent event) {
        Player player = event.getPlayer();
        
        try {
            Game game = gameManager.getGameByPlayer(player);
            if (game != null) {
                // Remove player from game but mark them as disconnected
                game.removePlayer(player, true);
            }
            
            // Save and uncache player statistics if enabled
            if (plugin.getConfig().getBoolean("statistics.enabled", true)) {
                plugin.getStatisticsManager().uncachePlayer(player.getUniqueId());
            }
            
            // Safety check: always reset scoreboard to server default when player quits
            // This ensures scoreboards don't persist if there's an edge case
            player.setScoreboard(org.bukkit.Bukkit.getScoreboardManager().getMainScoreboard());
        } catch (Exception e) {
            logger.warn("Error handling player quit for " + player.getName(), e);
            
            // Even if there's an error, try to reset the scoreboard
            try {
                player.setScoreboard(org.bukkit.Bukkit.getScoreboardManager().getMainScoreboard());
            } catch (Exception ex) {
                logger.severe("Failed to reset scoreboard for " + player.getName(), ex);
            }
        }
    }
    
    /**
     * Handles player death events.
     * 
     * <p>When a player dies in a Survival Games match, this method handles
     * their elimination from the game, including inventory cleanup, death
     * messages, and game state updates. The death may trigger game end
     * conditions if only one player remains.</p>
     * 
     * @param event The player death event
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerDeath(@NotNull PlayerDeathEvent event) {
        Player player = event.getEntity();
        
        try {
            logger.debug("Player death event for " + player.getName());
            
            Game game = gameManager.getGameByPlayer(player);
            if (game != null) {
                logger.debug("Player " + player.getName() + " died in game " + game.getGameId() + 
                    " (state: " + game.getState() + ", players: " + game.getPlayers().size() + ")");
                
                if (game.getState() != GameState.WAITING) {
                    // Player died in an active game
                    event.setCancelled(true); // Prevent normal death handling
                    
                    // Determine killer
                    Player killer = null;
                    if (player.getKiller() != null) {
                        killer = player.getKiller();
                    }
                    
                    // Handle player death (includes death message and elimination)
                    game.getEliminationManager().handlePlayerDeath(player, killer);
                    
                    // Clear death drops
                    event.getDrops().clear();
                    
                    logger.debug("Player " + player.getName() + " eliminated from game " + game.getGameId() + 
                        " (remaining players: " + game.getPlayers().size() + ")");
                }
            } else {
                logger.debug("Player " + player.getName() + " died but was not in a game");
            }
        } catch (Exception e) {
            logger.warn("Error handling player death for " + player.getName(), e);
        }
    }
    
    /**
     * Handles player respawn events.
     * 
     * <p>This method prevents players from respawning normally in Survival
     * Games matches. Players who die in a game should not respawn until
     * the game ends or they are manually handled.</p>
     * 
     * @param event The player respawn event
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerRespawn(@NotNull PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        
        try {
            Game game = gameManager.getGameByPlayer(player);
            if (game != null && game.getState() != GameState.WAITING) {
                // Player is in an active game, prevent normal respawn
                // Note: PlayerRespawnEvent cannot be cancelled, so we handle it differently
                
                // Teleport to spectator spawn or lobby
                Location respawnLocation = game.getArena().getSpectatorSpawn();
                if (respawnLocation != null) {
                    event.setRespawnLocation(respawnLocation);
                }
                
                // Set to spectator mode after respawn
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    player.setGameMode(GameMode.SPECTATOR);
                }, 1L);
            }
        } catch (Exception e) {
            logger.warn("Error handling player respawn for " + player.getName(), e);
        }
    }
    
    /**
     * Handles entity damage events (PvP and environmental damage).
     * 
     * <p>This method enforces PvP rules during different game states.
     * PvP is disabled during grace periods and waiting states, and
     * environmental damage may be restricted in certain game phases.</p>
     * 
     * @param event The entity damage event
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDamage(@NotNull EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return; // Only handle player damage
        }

		try {
            // Check for firework damage first
            if (handleFireworkDamage(event, player)) {
                return; // Firework damage was handled
            }
            
            // Handle game-related damage rules
            handleGameDamageRules(event, player);
        } catch (Exception e) {
            logger.warn("Error handling entity damage for " + player.getName(), e);
        }
    }
    
    /**
     * Handles firework damage prevention.
     * 
     * @param event The damage event
     * @param player The player being damaged
     * @return true if firework damage was handled and prevented, false otherwise
     */
    private boolean handleFireworkDamage(@NotNull EntityDamageEvent event, @NotNull Player player) {
        if (event.getCause() != EntityDamageEvent.DamageCause.ENTITY_EXPLOSION) {
            return false;
        }
        
        if (!(event instanceof EntityDamageByEntityEvent entityEvent)) {
            return false;
        }

		if (!(entityEvent.getDamager() instanceof org.bukkit.entity.Firework firework)) {
            return false;
        }

		// Check if this is a celebration firework
        if (firework.hasMetadata("celebration_firework")) {
            event.setCancelled(true);
            logger.debug("Prevented celebration firework damage to player " + player.getName());
            return true;
        }
        
        // Check if player is in a game
        Game game = gameManager.getGameByPlayer(player);
        if (game != null) {
            event.setCancelled(true);
            logger.debug("Prevented firework damage to player " + player.getName() + " in game");
            return true;
        }
        
        return false;
    }
    
    /**
     * Handles damage rules based on game state.
     * 
     * @param event The damage event
     * @param player The player being damaged
     */
    private void handleGameDamageRules(@NotNull EntityDamageEvent event, @NotNull Player player) {
        Game game = gameManager.getGameByPlayer(player);
        if (game == null) {
            return; // Player not in a game
        }
        
        GameState gameState = game.getState();
        
        if (gameState == GameState.WAITING || gameState == GameState.COUNTDOWN) {
            // No damage allowed during waiting or countdown
            event.setCancelled(true);
        } else if (gameState == GameState.GRACE_PERIOD && event instanceof EntityDamageByEntityEvent) {
            // No PvP during grace period, only allow environmental damage
            event.setCancelled(true);
        }
    }
    
    /**
     * Handles entity damage by entity events (PvP combat).
     * 
     * <p>This method specifically handles PvP combat between players.
     * It enforces PvP rules based on game state and may handle special
     * combat mechanics or restrictions.</p>
     * 
     * @param event The entity damage by entity event
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDamageByEntity(@NotNull EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim) || !(event.getDamager() instanceof Player attacker)) {
            return; // Only handle player vs player damage
        }

		try {
            Game sharedGame = getSharedGame(victim, attacker);
            if (sharedGame != null) {
                // Both players are in the same game
                if (!sharedGame.isPvpEnabled()) {
                    // PvP is disabled in this game state
                    event.setCancelled(true);
                    
                    // Notify players
                    attacker.sendMessage(Component.text("PvP is currently disabled!", NamedTextColor.RED));
                } else {
                    // PvP is enabled, track damage statistics if enabled
                    if (isDamageTrackingEnabled()) {
                        recordPvpDamage(attacker, victim, event.getFinalDamage(), sharedGame);
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Error handling PvP damage between " + 
                attacker.getName() + " and " + victim.getName(), e);
        }
    }
    
    /**
     * Gets the shared game between two players, if they are in the same game.
     * 
     * @param player1 The first player
     * @param player2 The second player
     * @return The shared game if both players are in the same game, null otherwise
     */
    private @Nullable Game getSharedGame(@NotNull Player player1, @NotNull Player player2) {
        Game game1 = gameManager.getGameByPlayer(player1);
        Game game2 = gameManager.getGameByPlayer(player2);
        
        if (game1 != null && game2 != null && game1.equals(game2)) {
            return game1;
        }
        
        return null;
    }
    
    /**
     * Checks if damage tracking is enabled in the configuration.
     * 
     * @return true if damage tracking is enabled, false otherwise
     */
    private boolean isDamageTrackingEnabled() {
        return plugin.getConfig().getBoolean("statistics.enabled", true) && 
               plugin.getConfig().getBoolean("statistics.track-damage", true);
    }
    
    /**
     * Records PvP damage statistics for both players and the game.
     * 
     * @param attacker The attacking player
     * @param victim The victim player
     * @param damage The damage amount
     * @param game The game instance
     */
    private void recordPvpDamage(@NotNull Player attacker, @NotNull Player victim, double damage, @NotNull Game game) {
        plugin.getStatisticsManager().recordDamageDealt(attacker.getUniqueId(), damage);
        plugin.getStatisticsManager().recordDamageTaken(victim.getUniqueId(), damage);
        
        // Also record in the game instance for per-game tracking
        game.recordDamageDealt(attacker.getUniqueId(), damage);
        game.recordDamageTaken(victim.getUniqueId(), damage);
    }
    
    /**
     * Handles player teleport events.
     * 
     * <p>This method may restrict player teleportation during games to
     * prevent cheating or maintain game integrity. Players in active
     * games may be prevented from teleporting outside the arena.</p>
     * 
     * @param event The player teleport event
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerTeleport(@NotNull PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        
        try {
            Game game = gameManager.getGameByPlayer(player);
            Location to = event.getTo();
            
            if (shouldRestrictTeleport(game, to)) {
                // Player trying to teleport outside arena during active game
                event.setCancelled(true);
                player.sendMessage(Component.text("You cannot leave the arena during the game!", NamedTextColor.RED));
            }
        } catch (Exception e) {
            logger.warn("Error handling player teleport for " + player.getName(), e);
        }
    }
    
    /**
     * Determines if a teleport should be restricted based on game state and location.
     * 
     * @param game The player's current game (may be null)
     * @param destination The teleport destination
     * @return true if the teleport should be restricted, false otherwise
     */
    private boolean shouldRestrictTeleport(@Nullable Game game, @NotNull Location destination) {
        if (game == null) {
            return false;
        }
        
        // Allow teleports during waiting and finished states
        if (game.getState() == GameState.WAITING || game.getState() == GameState.FINISHED) {
            return false;
        }
        
        // Allow teleports during shutdown
        if (game.isShuttingDown()) {
            return false;
        }
        
        // Restrict teleports outside arena during active game
        return !isLocationInArena(destination, game);
    }
    
    /**
     * Handles player reconnection to an ongoing game.
     * 
     * <p>This method manages the process of a player rejoining a game
     * after disconnecting. It may restore their inventory, position,
     * and game state depending on server configuration.</p>
     * 
     * @param player The player reconnecting
     * @param game The game they are rejoining
     */
    private void handlePlayerReconnection(@NotNull Player player, @NotNull Game game) {
        // Check if reconnection is allowed based on game state and configuration
        if (game.getState() == GameState.WAITING || game.getState() == GameState.COUNTDOWN) {
            // Allow reconnection during waiting or countdown
            player.sendMessage(Component.text("Welcome back! You have rejoined the game.", NamedTextColor.GREEN));
        } else {
            // Game is in progress, handle based on configuration
            boolean allowReconnect = plugin.getConfig().getBoolean("game.allow-reconnect", false);
            if (allowReconnect) {
                player.sendMessage(Component.text("Welcome back! You have rejoined the game.", NamedTextColor.GREEN));
                // Restore player state if needed
            } else {
                player.sendMessage(Component.text("You cannot rejoin a game in progress.", NamedTextColor.RED));
                game.removePlayer(player, false);
            }
        }
    }
    
    /**
     * Checks if a location is within the bounds of a game arena.
     * 
     * @param location The location to check
     * @param game The game/arena to check against
     * @return true if the location is within the arena, false otherwise
     */
    private boolean isLocationInArena(@NotNull Location location, @NotNull Game game) {
        // This is a simplified check - in a real implementation, you'd check
        // against the arena's actual boundaries
        return location.getWorld().equals(game.getArena().getWorld());
    }
    
    // Movement restriction during countdown is now handled by barrier blocks
    // This provides a smoother experience with no client-server desync issues
    
    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockPlace(@NotNull BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Game game = gameManager.getGameByPlayer(player);
        
        if (game == null) return;
        
        // Only allow block placement during ACTIVE or GRACE_PERIOD states
        if (game.getState() != GameState.ACTIVE && game.getState() != GameState.GRACE_PERIOD) {
            event.setCancelled(true);
            return;
        }
        
        // Check if the block type is allowed
        if (!game.isBlockAllowed(event.getBlock().getType())) {
            event.setCancelled(true);
            MessageUtils.sendMessage(player, "<red>You cannot place this type of block!");
            return;
        }
        
        // Track the placed block
        game.trackPlacedBlock(event.getBlock().getLocation());
    }
    
    /**
     * Handles entity explosion events to prevent firework damage during celebrations.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityExplode(@NotNull EntityExplodeEvent event) {
        try {
            // Check if this is a firework explosion
            if (event.getEntity() instanceof org.bukkit.entity.Firework firework) {

				// Always prevent block damage from celebration fireworks
                if (firework.hasMetadata("celebration_firework")) {
                    event.blockList().clear();
                    logger.debug("Prevented celebration firework block damage");
                    return;
                }
                
                // Check if any players in the area are in a game
                boolean inGameArea = false;
                for (org.bukkit.entity.Entity nearby : firework.getNearbyEntities(10, 10, 10)) {
                    if (nearby instanceof Player player) {
						Game game = gameManager.getGameByPlayer(player);
                        if (game != null) {
                            inGameArea = true;
                            break;
                        }
                    }
                }
                
                // If in a game area, prevent block damage from fireworks
                if (inGameArea) {
                    event.blockList().clear();
                    logger.debug("Prevented firework block damage in game area");
                }
            }
        } catch (Exception e) {
            logger.warn("Error handling entity explosion", e);
        }
    }
} 
