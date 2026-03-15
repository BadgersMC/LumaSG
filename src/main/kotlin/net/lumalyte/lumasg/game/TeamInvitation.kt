package net.lumalyte.lumasg.game

import java.time.Instant
import java.util.UUID

/**
 * Represents a pending team invitation.
 */
data class TeamInvitation(
    val inviter: UUID,
    val invitee: UUID,
    val team: Team,
    val game: Game? = null,
    val createdAt: Instant = Instant.now(),
    val expiresAt: Instant = createdAt.plusSeconds(60)
) {
    @Volatile
    var responded: Boolean = false
        private set

    fun isExpired(): Boolean = Instant.now().isAfter(expiresAt)

    fun isValid(): Boolean = !isExpired() && !responded && !team.isFull

    fun markResponded() {
        responded = true
    }
}
