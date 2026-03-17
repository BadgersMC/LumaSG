package net.lumalyte.lumasg.util

import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.RemovalCause
import net.badgersmc.nexus.annotations.Service
import net.lumalyte.lumasg.game.TeamInvitation
import org.bukkit.Bukkit
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Team invitation management system using Caffeine cache with automatic expiration.
 *
 * Manages the lifecycle of [TeamInvitation]s — creation, acceptance, decline,
 * removal, and expiry — with a 30-second TTL.
 */
@Service
class InvitationManager {

    private val logger = LoggerFactory.getLogger(InvitationManager::class.java)

    private val invitationsCache = Caffeine.newBuilder()
        .maximumSize(1000)
        .expireAfterWrite(Duration.ofSeconds(30))
        .recordStats()
        .removalListener<String, TeamInvitation> { key, invitation, cause ->
            if (cause.wasEvicted() && invitation != null) {
                val inviterName = getPlayerName(invitation.inviter)
                val inviteeName = getPlayerName(invitation.invitee)
                logger.debug("Invitation expired: {} from {} to {}", key, inviterName, inviteeName)
            }
        }
        .build<String, TeamInvitation>()

    private val playerActiveInvitations = ConcurrentHashMap<UUID, String>()
    private val invitationCounter = AtomicLong(0)

    /**
     * Creates a new team invitation. Replaces any existing invitation for the same invitee.
     *
     * @return the invitation key for tracking
     */
    fun createInvitation(invitation: TeamInvitation): String {
        val invitationKey = generateInvitationKey(invitation)

        // Remove any existing invitation for this invitee
        playerActiveInvitations[invitation.invitee]?.let { existingKey ->
            invitationsCache.invalidate(existingKey)
        }

        // Store the new invitation
        invitationsCache.put(invitationKey, invitation)
        playerActiveInvitations[invitation.invitee] = invitationKey

        val inviterName = getPlayerName(invitation.inviter)
        val inviteeName = getPlayerName(invitation.invitee)
        logger.debug("Created invitation: {} from {} to {}", invitationKey, inviterName, inviteeName)

        return invitationKey
    }

    /**
     * Gets an invitation by key, or `null` if not found / expired.
     */
    fun getInvitation(key: String): TeamInvitation? =
        invitationsCache.getIfPresent(key)

    /**
     * Gets the active invitation where [playerId] is the invitee, or `null` if none exists.
     */
    fun getPlayerInvitation(playerId: UUID): TeamInvitation? {
        val invitationKey = playerActiveInvitations[playerId] ?: return null
        val invitation = invitationsCache.getIfPresent(invitationKey)
        if (invitation == null) {
            // Invitation expired — clean up stale mapping
            playerActiveInvitations.remove(playerId)
        }
        return invitation
    }

    /**
     * Accepts an invitation, removing it from the cache.
     *
     * @return the accepted [TeamInvitation], or `null` if not found / expired
     */
    fun acceptInvitation(key: String): TeamInvitation? {
        val invitation = invitationsCache.getIfPresent(key)
        if (invitation != null) {
            removeInvitation(key)
            logger.debug("Accepted invitation: {}", key)
        }
        return invitation
    }

    /**
     * Declines an invitation, removing it from the cache.
     *
     * @return `true` if the invitation was found and declined
     */
    fun declineInvitation(key: String): Boolean {
        val invitation = invitationsCache.getIfPresent(key)
        if (invitation != null) {
            removeInvitation(key)
            logger.debug("Declined invitation: {}", key)
            return true
        }
        return false
    }

    /**
     * Removes an invitation by key.
     */
    fun removeInvitation(key: String) {
        val invitation = invitationsCache.getIfPresent(key)
        if (invitation != null) {
            invitationsCache.invalidate(key)
            playerActiveInvitations.remove(invitation.invitee)
        }
    }

    /**
     * Removes all invitations for a player, both as inviter and invitee.
     */
    fun removePlayerInvitations(playerId: UUID) {
        // Remove as invitee
        playerActiveInvitations.remove(playerId)?.let { key ->
            invitationsCache.invalidate(key)
        }

        // Remove as inviter (scan through all invitations)
        invitationsCache.asMap().entries.removeIf { (_, invitation) ->
            if (invitation.inviter == playerId) {
                playerActiveInvitations.remove(invitation.invitee)
                true
            } else {
                false
            }
        }
    }

    /**
     * Checks if a player has an active invitation as invitee.
     */
    fun hasActiveInvitation(playerId: UUID): Boolean =
        getPlayerInvitation(playerId) != null

    /**
     * Returns statistics about the invitation system.
     */
    fun getStats(): String {
        val stats = invitationsCache.stats()
        return "Invitations Cache - Size: ${invitationsCache.estimatedSize()}, " +
            "Hit Rate: ${"%.2f".format(stats.hitRate() * 100)}%, " +
            "Active Players: ${playerActiveInvitations.size}"
    }

    /**
     * Clears all invitations and active-player mappings.
     */
    fun clearAll() {
        invitationsCache.invalidateAll()
        playerActiveInvitations.clear()
    }

    // ── Private helpers ─────────────────────────────────────────────────

    private fun generateInvitationKey(invitation: TeamInvitation): String =
        "${invitation.inviter}:${invitation.invitee}:${invitationCounter.incrementAndGet()}"

    private fun getPlayerName(playerId: UUID): String = try {
        Bukkit.getPlayer(playerId)?.name ?: "Unknown Player"
    } catch (_: Exception) {
        "Unknown Player"
    }
}
