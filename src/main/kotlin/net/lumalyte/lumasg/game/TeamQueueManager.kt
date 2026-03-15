package net.lumalyte.lumasg.game

import net.badgersmc.nexus.annotations.PostConstruct
import net.badgersmc.nexus.annotations.PreDestroy
import net.badgersmc.nexus.annotations.Service
import net.kyori.adventure.text.minimessage.MiniMessage
import net.lumalyte.lumasg.config.LumaSGConfig
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Service
class TeamQueueManager(
    private val plugin: Plugin,
    private val config: LumaSGConfig,
    private val gameManager: GameManager
) {
    private val miniMessage = MiniMessage.miniMessage()
    private val pendingInvitations = ConcurrentHashMap<UUID, TeamInvitation>()
    private val preGameTeams = ConcurrentHashMap<UUID, Team>()
    private val mutedPlayers = ConcurrentHashMap.newKeySet<UUID>()
    private var broadcastTask: Int = -1
    private var cleanupTask: Int = -1

    @PostConstruct
    fun start() {
        if (config.queue.broadcastsEnabled) {
            broadcastTask = Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
                broadcastQueueStatus()
            }, 0L, config.queue.broadcastIntervalTicks).taskId
        }
        cleanupTask = Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
            pendingInvitations.entries.removeIf { it.value.isExpired() }
        }, 200L, 200L).taskId
    }

    @PreDestroy
    fun stop() {
        if (broadcastTask != -1) Bukkit.getScheduler().cancelTask(broadcastTask)
        if (cleanupTask != -1) Bukkit.getScheduler().cancelTask(cleanupTask)
        pendingInvitations.clear()
        preGameTeams.clear()
    }

    fun invite(inviter: Player, invitee: Player): Boolean {
        if (pendingInvitations.containsKey(invitee.uniqueId)) {
            inviter.sendMessage(miniMessage.deserialize("<red>${invitee.name} already has a pending invitation."))
            return false
        }
        val team = preGameTeams.getOrPut(inviter.uniqueId) {
            Team(id = 0, members = mutableListOf(inviter.uniqueId))
        }
        val invitation = TeamInvitation(inviter.uniqueId, invitee.uniqueId, team)
        pendingInvitations[invitee.uniqueId] = invitation
        inviter.sendMessage(miniMessage.deserialize("<green>Invitation sent to ${invitee.name}."))
        invitee.sendMessage(miniMessage.deserialize(
            "<gold>${inviter.name} invited you to their team! <green><click:run_command:'/sg accept'>[Accept]</click> <red><click:run_command:'/sg decline'>[Decline]</click>"
        ))
        return true
    }

    fun accept(player: Player): Boolean {
        val invitation = pendingInvitations.remove(player.uniqueId)
        if (invitation == null || invitation.isExpired()) {
            player.sendMessage(miniMessage.deserialize("<red>No pending invitation found."))
            return false
        }
        invitation.team.add(player.uniqueId)
        preGameTeams[player.uniqueId] = invitation.team
        val inviterName = Bukkit.getOfflinePlayer(invitation.inviter).name ?: "Unknown"
        player.sendMessage(miniMessage.deserialize("<green>You joined $inviterName's team!"))
        Bukkit.getPlayer(invitation.inviter)?.sendMessage(
            miniMessage.deserialize("<green>${player.name} accepted your invitation!")
        )
        return true
    }

    fun decline(player: Player): Boolean {
        val invitation = pendingInvitations.remove(player.uniqueId)
        if (invitation == null) {
            player.sendMessage(miniMessage.deserialize("<red>No pending invitation found."))
            return false
        }
        player.sendMessage(miniMessage.deserialize("<yellow>Invitation declined."))
        Bukkit.getPlayer(invitation.inviter)?.sendMessage(
            miniMessage.deserialize("<red>${player.name} declined your invitation.")
        )
        return true
    }

    fun toggleMute(player: Player) {
        if (mutedPlayers.remove(player.uniqueId)) {
            player.sendMessage(miniMessage.deserialize("<green>Queue broadcasts unmuted."))
        } else {
            mutedPlayers.add(player.uniqueId)
            player.sendMessage(miniMessage.deserialize("<yellow>Queue broadcasts muted."))
        }
    }

    fun getPreGameTeam(playerId: UUID): Team? = preGameTeams[playerId]

    fun removeFromQueue(playerId: UUID) {
        pendingInvitations.remove(playerId)
        preGameTeams.remove(playerId)
    }

    private fun broadcastQueueStatus() {
        val waitingGames = gameManager.getWaitingGames()
        if (waitingGames.isEmpty()) return
        for (game in waitingGames) {
            val msg = miniMessage.deserialize(
                "<gold>[SG] <yellow>${game.arena.name} needs players! <white>(${game.players.size}/${game.arena.maxPlayers}) <green><click:run_command:'/sg join ${game.arena.name}'>[Join]</click>"
            )
            for (player in Bukkit.getOnlinePlayers()) {
                if (player.uniqueId !in mutedPlayers && gameManager.getGameForPlayer(player.uniqueId) == null) {
                    player.sendMessage(msg)
                }
            }
        }
    }
}
