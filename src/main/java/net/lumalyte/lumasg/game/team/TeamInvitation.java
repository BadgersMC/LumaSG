package net.lumalyte.lumasg.game.team;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.UUID;

import net.lumalyte.lumasg.game.core.Game;

/**
 * Represents a team invitation from one player to another.
 * 
 * <p>Team invitations allow players to form teams before joining games.
 * Invitations have an expiration time to prevent spam and ensure timely responses.</p>
 * 
 * @author LumaLyte
 * @version 1.0
 * @since 1.0
 */
public class TeamInvitation {
    
    /** The player who sent the invitation */
    private final @NotNull UUID inviter;
    
    /** The player who received the invitation */
    private final @NotNull UUID invitee;
    
    /** The team the invitee would join if they accept */
    private final @NotNull Team team;
    
    /** The game this invitation is for */
    private final @NotNull Game game;
    
    /** When this invitation was created */
    private final @NotNull Instant createdAt;
    
    /** When this invitation expires (default 60 seconds) */
    private final @NotNull Instant expiresAt;
    
    /** Whether this invitation has been responded to */
    private volatile boolean responded = false;
    
    /**
     * Creates a new team invitation.
     * 
     * @param inviter The player sending the invitation
     * @param invitee The player receiving the invitation
     * @param team The team to join
     * @param game The game this invitation is for
     */
    public TeamInvitation(@NotNull Player inviter, @NotNull Player invitee, @NotNull Team team, @NotNull Game game) {
        this.inviter = inviter.getUniqueId();
        this.invitee = invitee.getUniqueId();
        this.team = team;
        this.game = game;
        this.createdAt = Instant.now();
        this.expiresAt = createdAt.plusSeconds(60); // 60 second expiration
    }
    
    /**
     * Gets the UUID of the player who sent the invitation.
     * 
     * @return The inviter's UUID
     */
    public @NotNull UUID getInviter() {
        return inviter;
    }
    
    /**
     * Gets the UUID of the player who received the invitation.
     * 
     * @return The invitee's UUID
     */
    public @NotNull UUID getInvitee() {
        return invitee;
    }
    
    /**
     * Gets the team this invitation is for.
     * 
     * @return The team
     */
    public @NotNull Team getTeam() {
        return team;
    }
    
    /**
     * Gets the game this invitation is for.
     * 
     * @return The game
     */
    public @NotNull Game getGame() {
        return game;
    }
    
    /**
     * Gets when this invitation was created.
     * 
     * @return The creation time
     */
    public @NotNull Instant getCreatedAt() {
        return createdAt;
    }
    
    /**
     * Gets when this invitation expires.
     * 
     * @return The expiration time
     */
    public @NotNull Instant getExpiresAt() {
        return expiresAt;
    }
    
    /**
     * Checks if this invitation has expired.
     * 
     * @return true if expired, false otherwise
     */
    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }
    
    /**
     * Checks if this invitation has been responded to.
     * 
     * @return true if responded, false otherwise
     */
    public boolean isResponded() {
        return responded;
    }
    
    /**
     * Marks this invitation as responded to.
     */
    public void markResponded() {
        this.responded = true;
    }
    
    /**
     * Checks if this invitation is still valid (not expired and not responded to).
     * 
     * @return true if valid, false otherwise
     */
    public boolean isValid() {
        return !responded && !isExpired();
    }
} 
