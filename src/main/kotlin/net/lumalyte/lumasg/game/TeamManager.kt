package net.lumalyte.lumasg.game

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class TeamManager(private val game: Game) {
    private val teams = ConcurrentHashMap<Int, Team>()
    private val playerTeams = ConcurrentHashMap<UUID, Int>()
    private val pendingInvites = ConcurrentHashMap<UUID, UUID>() // invitee -> inviter
    private var nextTeamId = 1
    private var glowEnabled = true

    val teamSize: Int get() = game.mode.teamSize

    fun assignToTeam(uuid: UUID): Team {
        val team = teams.values.firstOrNull { !it.isFull && !it.inviteOnly }
            ?: createTeam()
        team.add(uuid)
        playerTeams[uuid] = team.id
        return team
    }

    fun createTeam(): Team {
        val team = Team(id = nextTeamId++, maxSize = teamSize)
        teams[team.id] = team
        return team
    }

    fun getTeamForPlayer(uuid: UUID): Team? = playerTeams[uuid]?.let { teams[it] }

    fun removeFromTeam(uuid: UUID) {
        val teamId = playerTeams.remove(uuid) ?: return
        teams[teamId]?.remove(uuid)
    }

    fun invite(inviter: UUID, invitee: UUID): Boolean {
        if (getTeamForPlayer(inviter)?.isFull != false) return false
        pendingInvites[invitee] = inviter
        return true
    }

    fun acceptInvite(invitee: UUID): Boolean {
        val inviter = pendingInvites.remove(invitee) ?: return false
        val team = getTeamForPlayer(inviter) ?: return false
        if (team.isFull) return false
        removeFromTeam(invitee)
        team.add(invitee)
        playerTeams[invitee] = team.id
        return true
    }

    fun declineInvite(invitee: UUID) { pendingInvites.remove(invitee) }

    /** Check whether two players are on the same team. */
    fun areTeammates(a: Player, b: Player): Boolean = areTeammates(a.uniqueId, b.uniqueId)

    fun areTeammates(a: UUID, b: UUID): Boolean {
        val teamA = playerTeams[a] ?: return false
        val teamB = playerTeams[b] ?: return false
        return teamA == teamB
    }

    /** Mark a team as eliminated. */
    fun eliminateTeam(team: Team) {
        team.eliminate()
    }

    /** Disband all teams and clear assignments. */
    fun disbandAllTeams() {
        teams.values.forEach { it.cleanup() }
        teams.clear()
        playerTeams.clear()
        pendingInvites.clear()
        nextTeamId = 1
    }

    /** Auto-balance teams by redistributing players evenly. */
    fun autoBalanceTeams() {
        val allPlayers = playerTeams.keys.toList()
        disbandAllTeams()
        for (uuid in allPlayers) {
            assignToTeam(uuid)
        }
    }

    fun applyGlowingToTeammates() {
        if (teamSize <= 1 || !glowEnabled) return
        for (team in teams.values) {
            for (member in team.members) {
                val player = Bukkit.getPlayer(member) ?: continue
                for (teammate in team.members) {
                    if (teammate == member) continue
                    val tp = Bukkit.getPlayer(teammate) ?: continue
                    tp.addPotionEffect(PotionEffect(PotionEffectType.GLOWING, Int.MAX_VALUE, 0, false, false))
                }
            }
        }
    }

    /** Remove glow effects from a specific player. */
    fun removePlayerTeamEffects(player: Player) {
        player.removePotionEffect(PotionEffectType.GLOWING)
    }

    /** Refresh glow effects for all teams. */
    fun refreshTeamEffects() {
        // Remove all glowing first
        for (team in teams.values) {
            for (member in team.members) {
                Bukkit.getPlayer(member)?.removePotionEffect(PotionEffectType.GLOWING)
            }
        }
        // Re-apply
        applyGlowingToTeammates()
    }

    /** Enable or disable glow effects. */
    fun setGlowEffectsEnabled(enabled: Boolean) {
        glowEnabled = enabled
        if (!enabled) {
            for (team in teams.values) {
                for (member in team.members) {
                    Bukkit.getPlayer(member)?.removePotionEffect(PotionEffectType.GLOWING)
                }
            }
        } else {
            applyGlowingToTeammates()
        }
    }

    fun getAliveTeams(): List<Team> = teams.values.filter { it.isAlive }

    fun getAllTeams(): Collection<Team> = teams.values

    fun getActiveTeamCount(): Int = getAliveTeams().size

    /** Clean up all team state. */
    fun cleanup() {
        disbandAllTeams()
    }
}
