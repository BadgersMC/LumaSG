package net.lumalyte.game;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.lumalyte.LumaSG;
import net.lumalyte.util.DebugLogger;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Manages the team queue system for Survival Games.
 * 
 * <p>This class handles team invitations, team formation, and the queue system
 * that allows players to form teams before joining games. It also manages
 * server-wide broadcasts for game setup and team status updates.</p>
 * 
 * @author LumaLyte
 * @version 1.0
 * @since 1.0
 */
public class TeamQueueManager {
    
    /** The plugin instance */
    private final @NotNull LumaSG plugin;
    
    /** Debug logger for queue operations */
    private final @NotNull DebugLogger.ContextualLogger logger;
    
    /** Map of pending invitations by invitee UUID */
    private final @NotNull Map<UUID, TeamInvitation> pendingInvitations;
    
    /** Map of players to their current teams in queue */
    private final @NotNull Map<UUID, Team> playerTeams;
    
    /** Map of game IDs to their current broadcast messages */
    private final @NotNull Map<UUID, Component> currentBroadcasts;
    
    /** Map of game IDs to their broadcast update tasks */
    private final @NotNull Map<UUID, BukkitTask> broadcastTasks;
    
    /** Set of players who have muted queue broadcasts */
    private final @NotNull Set<UUID> mutedPlayers;
    
    /**
     * Creates a new TeamQueueManager.
     * 
     * @param plugin The plugin instance
     */
    public TeamQueueManager(@NotNull LumaSG plugin) {
        this.plugin = plugin;
        this.logger = plugin.getDebugLogger().forContext("TeamQueueManager");
        this.pendingInvitations = new ConcurrentHashMap<>();
        this.playerTeams = new ConcurrentHashMap<>();
        this.currentBroadcasts = new ConcurrentHashMap<>();
        this.broadcastTasks = new ConcurrentHashMap<>();
        this.mutedPlayers = ConcurrentHashMap.newKeySet();
        
        // Start cleanup task for expired invitations
        startCleanupTask();
    }
    
    /**
     * Sends a team invitation from one player to another.
     * 
     * @param inviter The player sending the invitation
     * @param invitee The player receiving the invitation
     * @param team The team to join
     * @param game The game this invitation is for
     * @return true if invitation was sent, false if failed
     */
    public boolean sendInvitation(@NotNull Player inviter, @NotNull Player invitee, @NotNull Team team, @NotNull Game game) {
        // Check if invitee already has a pending invitation
        if (pendingInvitations.containsKey(invitee.getUniqueId())) {
            inviter.sendMessage(Component.text(invitee.getName() + " already has a pending team invitation!", NamedTextColor.RED));
            return false;
        }
        
        // Check if invitee is already in a team
        if (playerTeams.containsKey(invitee.getUniqueId())) {
            inviter.sendMessage(Component.text(invitee.getName() + " is already in a team!", NamedTextColor.RED));
            return false;
        }
        
        // Check if team has space
        if (team.isFull(game.getGameMode())) {
            inviter.sendMessage(Component.text("Your team is already full!", NamedTextColor.RED));
            return false;
        }
        
        // Create and store invitation
        TeamInvitation invitation = new TeamInvitation(inviter, invitee, team, game);
        pendingInvitations.put(invitee.getUniqueId(), invitation);
        
        // Send messages
        inviter.sendMessage(Component.text("Team invitation sent to " + invitee.getName() + "!", NamedTextColor.GREEN));
        
        Component inviteMessage = Component.text()
            .append(Component.text(inviter.getName(), NamedTextColor.YELLOW))
            .append(Component.text(" has invited you to join their team for ", NamedTextColor.WHITE))
            .append(Component.text(game.getGameMode().getDisplayName(), NamedTextColor.GOLD))
            .append(Component.text(" in arena ", NamedTextColor.WHITE))
            .append(Component.text(game.getArena().getName(), NamedTextColor.AQUA))
            .append(Component.text("!", NamedTextColor.WHITE))
            .build();
        
        Component acceptMessage = Component.text()
            .append(Component.text("Click here to accept", NamedTextColor.GREEN, net.kyori.adventure.text.format.TextDecoration.UNDERLINED))
            .clickEvent(ClickEvent.runCommand("/sg accept"))
            .build();
        
        Component declineMessage = Component.text()
            .append(Component.text(" or ", NamedTextColor.GRAY))
            .append(Component.text("click here to decline", NamedTextColor.RED, net.kyori.adventure.text.format.TextDecoration.UNDERLINED))
            .clickEvent(ClickEvent.runCommand("/sg decline"))
            .build();
        
        invitee.sendMessage(inviteMessage);
        invitee.sendMessage(Component.text().append(acceptMessage).append(declineMessage).build());
        invitee.sendMessage(Component.text("This invitation expires in 60 seconds.", NamedTextColor.GRAY));
        
        logger.debug("Team invitation sent from " + inviter.getName() + " to " + invitee.getName());
        return true;
    }
    
    /**
     * Accepts a team invitation for the specified player.
     * 
     * @param player The player accepting the invitation
     * @return true if invitation was accepted, false if no valid invitation
     */
    public boolean acceptInvitation(@NotNull Player player) {
        TeamInvitation invitation = pendingInvitations.remove(player.getUniqueId());
        
        if (invitation == null) {
            player.sendMessage(Component.text("You don't have any pending team invitations!", NamedTextColor.RED));
            return false;
        }
        
        if (!invitation.isValid()) {
            player.sendMessage(Component.text("That team invitation has expired!", NamedTextColor.RED));
            return false;
        }
        
        // Check if team still has space
        Team team = invitation.getTeam();
        if (team.isFull(invitation.getGame().getGameMode())) {
            player.sendMessage(Component.text("That team is now full!", NamedTextColor.RED));
            return false;
        }
        
        // Add player to team
        if (team.addMember(player)) {
            invitation.markResponded();
            playerTeams.put(player.getUniqueId(), team);
            
            // Notify all team members
            for (UUID memberId : team.getMembers()) {
                Player member = Bukkit.getPlayer(memberId);
                if (member != null) {
                    member.sendMessage(Component.text(player.getName() + " joined the team!", NamedTextColor.GREEN));
                }
            }
            
            // Notify inviter
            Player inviter = Bukkit.getPlayer(invitation.getInviter());
            if (inviter != null) {
                inviter.sendMessage(Component.text(player.getName() + " accepted your team invitation!", NamedTextColor.GREEN));
            }
            
            // Update game broadcast if team changed
            updateGameBroadcast(invitation.getGame());
            
            logger.debug("Player " + player.getName() + " accepted team invitation");
            return true;
        } else {
            player.sendMessage(Component.text("Failed to join team!", NamedTextColor.RED));
            return false;
        }
    }
    
    /**
     * Declines a team invitation for the specified player.
     * 
     * @param player The player declining the invitation
     * @return true if invitation was declined, false if no valid invitation
     */
    public boolean declineInvitation(@NotNull Player player) {
        TeamInvitation invitation = pendingInvitations.remove(player.getUniqueId());
        
        if (invitation == null) {
            player.sendMessage(Component.text("You don't have any pending team invitations!", NamedTextColor.RED));
            return false;
        }
        
        invitation.markResponded();
        
        // Notify inviter
        Player inviter = Bukkit.getPlayer(invitation.getInviter());
        if (inviter != null) {
            inviter.sendMessage(Component.text(player.getName() + " declined your team invitation.", NamedTextColor.YELLOW));
        }
        
        player.sendMessage(Component.text("Team invitation declined.", NamedTextColor.YELLOW));
        
        logger.debug("Player " + player.getName() + " declined team invitation");
        return true;
    }
    
    /**
     * Leaves a team for the specified player.
     * 
     * @param player The player leaving the team
     * @return true if player left team, false if not in a team
     */
    public boolean leaveTeam(@NotNull Player player) {
        Team team = playerTeams.remove(player.getUniqueId());
        
        if (team == null) {
            player.sendMessage(Component.text("You're not in a team!", NamedTextColor.RED));
            return false;
        }
        
        team.removeMember(player.getUniqueId());
        
        // Notify remaining team members
        for (UUID memberId : team.getMembers()) {
            Player member = Bukkit.getPlayer(memberId);
            if (member != null) {
                member.sendMessage(Component.text(player.getName() + " left the team.", NamedTextColor.YELLOW));
            }
        }
        
        player.sendMessage(Component.text("You left the team.", NamedTextColor.YELLOW));
        
        logger.debug("Player " + player.getName() + " left team");
        return true;
    }
    
    /**
     * Gets the team a player is currently in by UUID.
     * 
     * @param playerId The player's UUID
     * @return The player's team, or null if not in a team
     */
    public @Nullable Team getPlayerTeam(@NotNull UUID playerId) {
        return playerTeams.get(playerId);
    }
    
    /**
     * Gets the team a player is currently in.
     * 
     * @param player The player
     * @return The player's team, or null if not in a team
     */
    public @Nullable Team getPlayerTeam(@NotNull Player player) {
        return playerTeams.get(player.getUniqueId());
    }
    
    /**
     * Updates the server broadcast for a game.
     * 
     * @param game The game to update broadcast for
     */
    public void updateGameBroadcast(@NotNull Game game) {
        // Only broadcast for games in WAITING state
        if (game.getState() != GameState.WAITING) {
            stopGameBroadcast(game);
            return;
        }
        
        int playerCount = game.getPlayerCount();
        if (playerCount == 0) {
            stopGameBroadcast(game);
            return;
        }
        
        Component newBroadcast = Component.text()
            .append(Component.text("🎮 ", NamedTextColor.GOLD))
            .append(Component.text(game.getGameMode().getDisplayName(), NamedTextColor.YELLOW))
            .append(Component.text(" game in ", NamedTextColor.WHITE))
            .append(Component.text(game.getArena().getName(), NamedTextColor.AQUA))
            .append(Component.text(" - ", NamedTextColor.GRAY))
            .append(Component.text(playerCount + " players", NamedTextColor.GREEN))
            .append(Component.text(" joined! ", NamedTextColor.WHITE))
            .append(Component.text("[Click to Join]", NamedTextColor.GOLD, net.kyori.adventure.text.format.TextDecoration.UNDERLINED))
            .clickEvent(ClickEvent.runCommand("/sg join " + game.getArena().getName()))
            .build();
        
        Component oldBroadcast = currentBroadcasts.get(game.getGameId());
        
        // Only update if message changed
        if (oldBroadcast == null || !newBroadcast.equals(oldBroadcast)) {
            currentBroadcasts.put(game.getGameId(), newBroadcast);
            
            // Send to all non-muted players
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!mutedPlayers.contains(player.getUniqueId())) {
                    player.sendMessage(newBroadcast);
                }
            }
            
            logger.debug("Updated game broadcast for " + game.getArena().getName());
        }
    }
    
    /**
     * Stops broadcasting for a game.
     * 
     * @param game The game to stop broadcasting for
     */
    public void stopGameBroadcast(@NotNull Game game) {
        currentBroadcasts.remove(game.getGameId());
        
        BukkitTask task = broadcastTasks.remove(game.getGameId());
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
    }
    
    /**
     * Toggles queue broadcast muting for a player.
     * 
     * @param player The player
     * @return true if now muted, false if now unmuted
     */
    public boolean toggleMute(@NotNull Player player) {
        if (mutedPlayers.contains(player.getUniqueId())) {
            mutedPlayers.remove(player.getUniqueId());
            player.sendMessage(Component.text("Queue broadcasts unmuted!", NamedTextColor.GREEN));
            return false;
        } else {
            mutedPlayers.add(player.getUniqueId());
            player.sendMessage(Component.text("Queue broadcasts muted!", NamedTextColor.YELLOW));
            return true;
        }
    }
    
    /**
     * Starts the cleanup task for expired invitations.
     */
    private void startCleanupTask() {
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::cleanupExpiredInvitations, 20L * 30, 20L * 30); // Every 30 seconds
    }
    
    /**
     * Cleans up expired invitations.
     */
    private void cleanupExpiredInvitations() {
        Iterator<Map.Entry<UUID, TeamInvitation>> iterator = pendingInvitations.entrySet().iterator();
        
        while (iterator.hasNext()) {
            Map.Entry<UUID, TeamInvitation> entry = iterator.next();
            TeamInvitation invitation = entry.getValue();
            
            if (invitation.isExpired()) {
                iterator.remove();
                
                // Notify invitee on main thread
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Player invitee = Bukkit.getPlayer(entry.getKey());
                    if (invitee != null) {
                        invitee.sendMessage(Component.text("Your team invitation has expired.", NamedTextColor.GRAY));
                    }
                });
                
                logger.debug("Cleaned up expired invitation for " + entry.getKey());
            }
        }
    }
    
    /**
     * Cleans up all data for a player.
     * 
     * @param player The player to clean up
     */
    public void cleanupPlayer(@NotNull Player player) {
        pendingInvitations.remove(player.getUniqueId());
        playerTeams.remove(player.getUniqueId());
        mutedPlayers.remove(player.getUniqueId());
    }
    
    /**
     * Shuts down the queue manager and cleans up resources.
     */
    public void shutdown() {
        pendingInvitations.clear();
        playerTeams.clear();
        mutedPlayers.clear();
        
        // Cancel all broadcast tasks
        for (BukkitTask task : broadcastTasks.values()) {
            if (!task.isCancelled()) {
                task.cancel();
            }
        }
        broadcastTasks.clear();
        currentBroadcasts.clear();
    }
    
    /**
     * Creates a new team for a player in the specified game.
     * 
     * @param player The player creating the team
     * @param game The game the team is for
     * @param inviteOnly Whether the team is invite only
     * @param autoFill Whether the team should auto-fill empty slots
     * @return The created team, or null if failed
     */
    public @Nullable Team createTeam(@NotNull Player player, @NotNull Game game, boolean inviteOnly, boolean autoFill) {
        // Check if player is already in a team
        if (playerTeams.containsKey(player.getUniqueId())) {
            return null;
        }
        
        // Create team through the game's team manager
        Team team = game.getTeamManager().createTeam();
        if (team == null) {
            return null;
        }
        
        // Add player to the team
        if (team.addMember(player)) {
            playerTeams.put(player.getUniqueId(), team);
            
            // TODO: Store invite only and auto-fill settings when team privacy is implemented
            // team.setInviteOnly(inviteOnly);
            // team.setAutoFill(autoFill);
            
            // Update game broadcast
            updateGameBroadcast(game);
            
            logger.debug("Created team for player " + player.getName() + " in game " + game.getGameId());
            return team;
        }
        
        return null;
    }
    
    /**
     * Allows a player to join an existing team.
     * 
     * @param player The player joining the team
     * @param team The team to join
     * @return true if successfully joined, false otherwise
     */
    public boolean joinTeam(@NotNull Player player, @NotNull Team team) {
        // Check if player is already in a team
        if (playerTeams.containsKey(player.getUniqueId())) {
            return false;
        }
        
        // Add player to the team
        if (team.addMember(player)) {
            playerTeams.put(player.getUniqueId(), team);
            
            // Update game broadcast (find the game this team belongs to)
            // For now, we'll need to search for the game - in the future we could store game reference in team
            for (Game game : plugin.getGameManager().getActiveGames()) {
                if (game.getTeamManager().getTeams().contains(team)) {
                    updateGameBroadcast(game);
                    break;
                }
            }
            
            logger.debug("Player " + player.getName() + " joined team " + team.getDisplayNumber());
            return true;
        }
        
        return false;
    }
    
    /**
     * Checks if a player has an invitation to join a specific team.
     * 
     * @param playerId The player's UUID
     * @param team The team to check for invitation
     * @return true if the player has an invitation to this team
     */
    public boolean hasInvitation(@NotNull UUID playerId, @NotNull Team team) {
        TeamInvitation invitation = pendingInvitations.get(playerId);
        return invitation != null && invitation.getTeam().equals(team) && invitation.isValid();
    }
    
    /**
     * Removes a pending invitation for a player.
     * 
     * @param playerId The player's UUID
     */
    public void removeInvitation(@NotNull UUID playerId) {
        TeamInvitation removed = pendingInvitations.remove(playerId);
        if (removed != null) {
            logger.debug("Removed invitation for player " + playerId);
        }
    }
    
    /**
     * Starts the setup period for a game, allowing time for players to form teams.
     * 
     * @param game The game to start setup period for
     * @param setupTimeSeconds The setup time in seconds
     */
    public void startSetupPeriod(@NotNull Game game, int setupTimeSeconds) {
        logger.debug("Starting setup period for game " + game.getGameId() + " (" + setupTimeSeconds + " seconds)");
        
        // Start initial broadcast
        updateGameBroadcast(game);
        
        // Schedule transition to WAITING state after setup period
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (game.getState() == GameState.INACTIVE) {
                // Transition to WAITING state so players can start joining
                // Note: This might need adjustment based on how game state transitions work
                logger.debug("Setup period ended for game " + game.getGameId());
                
                // Update broadcast to reflect new state
                updateGameBroadcast(game);
            }
        }, setupTimeSeconds * 20L); // Convert seconds to ticks
    }
} 