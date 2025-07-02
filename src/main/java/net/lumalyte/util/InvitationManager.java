package net.lumalyte.util;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import net.lumalyte.LumaSG;
import net.lumalyte.game.TeamInvitation;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * High-performance invitation management system using Caffeine
 * Handles team invitations with automatic expiration and memory management
 */
public class InvitationManager {
    
    private static DebugLogger.ContextualLogger logger;
    
    private static final Cache<String, TeamInvitation> INVITATIONS_CACHE = Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(Duration.ofSeconds(30))
            .recordStats()
            .removalListener((String key, TeamInvitation invitation, com.github.benmanes.caffeine.cache.RemovalCause cause) -> {
                if (cause.wasEvicted() && invitation != null && logger != null) {
                    String inviterName = getPlayerName(invitation.getInviter());
                    String inviteeName = getPlayerName(invitation.getInvitee());
                    logger.debug("Invitation expired: " + key + " from " + inviterName + " to " + inviteeName);
                }
            })
            .build();
    
    private static final ConcurrentHashMap<UUID, String> PLAYER_ACTIVE_INVITATIONS = new ConcurrentHashMap<>();
    private static final AtomicLong INVITATION_COUNTER = new AtomicLong(0);
    
    /**
     * Initializes the invitation manager with plugin instance
     * 
     * @param pluginInstance The plugin instance
     */
    public static void initialize(LumaSG pluginInstance) {
        logger = pluginInstance.getDebugLogger().forContext("InvitationManager");
    }
    
    /**
     * Creates a new team invitation
     * 
     * @param invitation The team invitation to create
     * @return The invitation key for tracking
     */
    public static @NotNull String createInvitation(@NotNull TeamInvitation invitation) {
        String invitationKey = generateInvitationKey(invitation);
        
        // Remove any existing invitation for this player
        String existingKey = PLAYER_ACTIVE_INVITATIONS.get(invitation.getInvitee());
        if (existingKey != null) {
            INVITATIONS_CACHE.invalidate(existingKey);
        }
        
        // Store the new invitation
        INVITATIONS_CACHE.put(invitationKey, invitation);
        PLAYER_ACTIVE_INVITATIONS.put(invitation.getInvitee(), invitationKey);
        
        if (logger != null) {
            String inviterName = getPlayerName(invitation.getInviter());
            String inviteeName = getPlayerName(invitation.getInvitee());
            logger.debug("Created invitation: " + invitationKey + " from " + inviterName + " to " + inviteeName);
        }
        
        return invitationKey;
    }
    
    /**
     * Gets an invitation by key
     * 
     * @param invitationKey The invitation key
     * @return The invitation or null if not found/expired
     */
    public static @Nullable TeamInvitation getInvitation(@NotNull String invitationKey) {
        return INVITATIONS_CACHE.getIfPresent(invitationKey);
    }
    
    /**
     * Gets the active invitation for a player
     * 
     * @param playerId The player's UUID
     * @return The active invitation or null if none exists
     */
    public static @Nullable TeamInvitation getPlayerInvitation(@NotNull UUID playerId) {
        String invitationKey = PLAYER_ACTIVE_INVITATIONS.get(playerId);
        if (invitationKey == null) {
            return null;
        }
        
        TeamInvitation invitation = INVITATIONS_CACHE.getIfPresent(invitationKey);
        if (invitation == null) {
            // Invitation expired, clean up
            PLAYER_ACTIVE_INVITATIONS.remove(playerId);
        }
        
        return invitation;
    }
    
    /**
     * Accepts an invitation
     * 
     * @param invitationKey The invitation key
     * @return The accepted invitation or null if not found/expired
     */
    public static @Nullable TeamInvitation acceptInvitation(@NotNull String invitationKey) {
        TeamInvitation invitation = INVITATIONS_CACHE.getIfPresent(invitationKey);
        if (invitation != null) {
            removeInvitation(invitationKey);
            if (logger != null) {
                logger.debug("Accepted invitation: " + invitationKey);
            }
        }
        return invitation;
    }
    
    /**
     * Declines an invitation
     * 
     * @param invitationKey The invitation key
     * @return true if the invitation was found and declined
     */
    public static boolean declineInvitation(@NotNull String invitationKey) {
        TeamInvitation invitation = INVITATIONS_CACHE.getIfPresent(invitationKey);
        if (invitation != null) {
            removeInvitation(invitationKey);
            if (logger != null) {
                logger.debug("Declined invitation: " + invitationKey);
            }
            return true;
        }
        return false;
    }
    
    /**
     * Removes an invitation
     * 
     * @param invitationKey The invitation key
     */
    public static void removeInvitation(@NotNull String invitationKey) {
        TeamInvitation invitation = INVITATIONS_CACHE.getIfPresent(invitationKey);
        if (invitation != null) {
            INVITATIONS_CACHE.invalidate(invitationKey);
            PLAYER_ACTIVE_INVITATIONS.remove(invitation.getInvitee());
        }
    }
    
    /**
     * Removes all invitations for a player (as inviter or invitee)
     * 
     * @param playerId The player's UUID
     */
    public static void removePlayerInvitations(@NotNull UUID playerId) {
        // Remove as invitee
        String invitationKey = PLAYER_ACTIVE_INVITATIONS.remove(playerId);
        if (invitationKey != null) {
            INVITATIONS_CACHE.invalidate(invitationKey);
        }
        
        // Remove as inviter (scan through all invitations)
        INVITATIONS_CACHE.asMap().entrySet().removeIf(entry -> {
            TeamInvitation invitation = entry.getValue();
            if (invitation.getInviter().equals(playerId)) {
                PLAYER_ACTIVE_INVITATIONS.remove(invitation.getInvitee());
                return true;
            }
            return false;
        });
    }
    
    /**
     * Checks if a player has an active invitation
     * 
     * @param playerId The player's UUID
     * @return true if the player has an active invitation
     */
    public static boolean hasActiveInvitation(@NotNull UUID playerId) {
        return getPlayerInvitation(playerId) != null;
    }
    
    /**
     * Gets statistics about the invitation system
     * 
     * @return String containing invitation statistics
     */
    public static String getStats() {
        return String.format("Invitations Cache - Size: %d, Hit Rate: %.2f%%, Active Players: %d",
                INVITATIONS_CACHE.estimatedSize(),
                INVITATIONS_CACHE.stats().hitRate() * 100,
                PLAYER_ACTIVE_INVITATIONS.size());
    }
    
    /**
     * Clears all invitations
     */
    public static void clearAll() {
        INVITATIONS_CACHE.invalidateAll();
        PLAYER_ACTIVE_INVITATIONS.clear();
    }
    
    /**
     * Generates a unique invitation key
     * 
     * @param invitation The team invitation
     * @return A unique invitation key
     */
    private static @NotNull String generateInvitationKey(@NotNull TeamInvitation invitation) {
        return invitation.getInviter() + ":" + invitation.getInvitee() + ":" + INVITATION_COUNTER.incrementAndGet();
    }
    
    /**
     * Helper method to get player name from UUID
     * 
     * @param playerId The player's UUID
     * @return The player's name or "Unknown Player" if not found
     */
    private static @NotNull String getPlayerName(@NotNull UUID playerId) {
        try {
            var player = Bukkit.getPlayer(playerId);
            return player != null ? player.getName() : "Unknown Player";
        } catch (Exception e) {
            return "Unknown Player";
        }
    }
} 