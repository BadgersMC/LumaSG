package net.lumalyte.lumasg.permissions

import org.bukkit.entity.Player

/** Permission checks for LumaSG features. */
object RankPermissions {

    const val SETUP_GAMES = "lumasg.setup.games"
    const val INVITE_PLAYERS = "lumasg.team.invite"
    const val JOIN_TEAMS = "lumasg.team.join"
    const val CREATE_TEAMS = "lumasg.team.create"
    const val CONFIGURE_TEAMS = "lumasg.team.configure"
    const val SELECT_MAPS = "lumasg.setup.maps"
    const val MANAGE_QUEUES = "lumasg.queue.manage"
    const val BYPASS_LIMITS = "lumasg.bypass.limits"
    const val FORCE_START = "lumasg.game.forcestart"
    const val ADMIN_COMMANDS = "lumasg.admin"

    fun canSetupGames(player: Player): Boolean =
        player.hasPermission(SETUP_GAMES) || hasAdminAccess(player)

    fun canInvitePlayers(player: Player): Boolean =
        player.hasPermission(INVITE_PLAYERS) || hasAdminAccess(player)

    fun canJoinTeams(player: Player): Boolean =
        player.hasPermission(JOIN_TEAMS) || hasAdminAccess(player)

    fun canCreateTeams(player: Player): Boolean =
        player.hasPermission(CREATE_TEAMS) || hasAdminAccess(player)

    fun canConfigureTeams(player: Player): Boolean =
        player.hasPermission(CONFIGURE_TEAMS) || hasAdminAccess(player)

    fun canSelectMaps(player: Player): Boolean =
        player.hasPermission(SELECT_MAPS) || hasAdminAccess(player)

    fun canManageQueues(player: Player): Boolean =
        player.hasPermission(MANAGE_QUEUES) || hasAdminAccess(player)

    fun canBypassLimits(player: Player): Boolean =
        player.hasPermission(BYPASS_LIMITS) || hasAdminAccess(player)

    fun canForceStart(player: Player): Boolean =
        player.hasPermission(FORCE_START) || hasAdminAccess(player)

    fun hasAdminAccess(player: Player): Boolean =
        player.hasPermission(ADMIN_COMMANDS) || player.isOp

    fun getPermissionLevel(player: Player): String = when {
        hasAdminAccess(player) -> "Administrator"
        canSetupGames(player) -> "Game Moderator"
        canCreateTeams(player) -> "Team Leader"
        canJoinTeams(player) -> "Player"
        else -> "Guest"
    }
}
