package net.lumalyte.permissions;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Manages rank-based permissions for LumaSG features.
 * 
 * <p>This class determines what actions players can perform based on their
 * rank or permission nodes. Higher ranked players have access to more
 * advanced features like game setup and configuration.</p>
 * 
 * @author LumaLyte
 * @version 1.0
 * @since 1.0
 */
public class RankPermissions {
    
    // Permission nodes for different actions
    public static final String SETUP_GAMES = "lumasg.setup.games";
    public static final String INVITE_PLAYERS = "lumasg.team.invite";
    public static final String JOIN_TEAMS = "lumasg.team.join";
    public static final String CREATE_TEAMS = "lumasg.team.create";
    public static final String CONFIGURE_TEAMS = "lumasg.team.configure";
    public static final String SELECT_MAPS = "lumasg.setup.maps";
    public static final String MANAGE_QUEUES = "lumasg.queue.manage";
    public static final String BYPASS_LIMITS = "lumasg.bypass.limits";
    public static final String FORCE_START = "lumasg.game.forcestart";
    public static final String ADMIN_COMMANDS = "lumasg.admin";
    
    /**
     * Checks if a player can set up games.
     * 
     * @param player The player to check
     * @return true if the player can set up games
     */
    public static boolean canSetupGames(@NotNull Player player) {
        return player.hasPermission(SETUP_GAMES) || 
               player.hasPermission(ADMIN_COMMANDS) ||
               player.isOp();
    }
    
    /**
     * Checks if a player can invite others to their team.
     * 
     * @param player The player to check
     * @return true if the player can send team invitations
     */
    public static boolean canInvitePlayers(@NotNull Player player) {
        return player.hasPermission(INVITE_PLAYERS) ||
               player.hasPermission(ADMIN_COMMANDS) ||
               player.isOp();
    }
    
    /**
     * Checks if a player can join teams.
     * 
     * @param player The player to check
     * @return true if the player can join teams
     */
    public static boolean canJoinTeams(@NotNull Player player) {
        return player.hasPermission(JOIN_TEAMS) ||
               player.hasPermission(ADMIN_COMMANDS) ||
               player.isOp();
    }
    
    /**
     * Checks if a player can create their own teams.
     * 
     * @param player The player to check
     * @return true if the player can create teams
     */
    public static boolean canCreateTeams(@NotNull Player player) {
        return player.hasPermission(CREATE_TEAMS) ||
               player.hasPermission(ADMIN_COMMANDS) ||
               player.isOp();
    }
    
    /**
     * Checks if a player can configure team settings (invite-only, auto-fill).
     * 
     * @param player The player to check
     * @return true if the player can configure teams
     */
    public static boolean canConfigureTeams(@NotNull Player player) {
        return player.hasPermission(CONFIGURE_TEAMS) ||
               player.hasPermission(ADMIN_COMMANDS) ||
               player.isOp();
    }
    
    /**
     * Checks if a player can select maps during game setup.
     * 
     * @param player The player to check
     * @return true if the player can select maps
     */
    public static boolean canSelectMaps(@NotNull Player player) {
        return player.hasPermission(SELECT_MAPS) ||
               player.hasPermission(ADMIN_COMMANDS) ||
               player.isOp();
    }
    
    /**
     * Checks if a player can manage game queues.
     * 
     * @param player The player to check
     * @return true if the player can manage queues
     */
    public static boolean canManageQueues(@NotNull Player player) {
        return player.hasPermission(MANAGE_QUEUES) ||
               player.hasPermission(ADMIN_COMMANDS) ||
               player.isOp();
    }
    
    /**
     * Checks if a player can bypass normal limits (team size, game limits, etc.).
     * 
     * @param player The player to check
     * @return true if the player can bypass limits
     */
    public static boolean canBypassLimits(@NotNull Player player) {
        return player.hasPermission(BYPASS_LIMITS) ||
               player.hasPermission(ADMIN_COMMANDS) ||
               player.isOp();
    }
    
    /**
     * Checks if a player can force start games.
     * 
     * @param player The player to check
     * @return true if the player can force start games
     */
    public static boolean canForceStart(@NotNull Player player) {
        return player.hasPermission(FORCE_START) ||
               player.hasPermission(ADMIN_COMMANDS) ||
               player.isOp();
    }
    
    /**
     * Checks if a player has administrative access.
     * 
     * @param player The player to check
     * @return true if the player has admin access
     */
    public static boolean hasAdminAccess(@NotNull Player player) {
        return player.hasPermission(ADMIN_COMMANDS) || player.isOp();
    }
    
    /**
     * Gets the permission level of a player for display purposes.
     * 
     * @param player The player to check
     * @return A string representing the player's permission level
     */
    public static @NotNull String getPermissionLevel(@NotNull Player player) {
        if (hasAdminAccess(player)) {
            return "Administrator";
        } else if (canSetupGames(player)) {
            return "Game Moderator";
        } else if (canCreateTeams(player)) {
            return "Team Leader";
        } else if (canJoinTeams(player)) {
            return "Player";
        } else {
            return "Guest";
        }
    }
} 