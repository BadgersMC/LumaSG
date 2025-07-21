package net.lumalyte.lumasg.game;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Represents a team of players in a Survival Games match.
 * 
 * <p>A team consists of one or more players who work together towards victory.
 * Teams handle member management, team identification, and provide utilities
 * for team-based game mechanics.</p>
 * 
 * @author LumaLyte
 * @version 1.0
 * @since 1.0
 */
public class Team {
    
    /** Unique identifier for this team */
    private final @NotNull UUID teamId;
    
    /** The team number (1, 2, 3, etc.) for display purposes */
    private final int teamNumber;
    
    /** Set of player UUIDs that belong to this team */
    private final @NotNull Set<UUID> members;
    
    /** Cache of team member player objects for quick access */
    private final @NotNull Map<UUID, Player> memberCache;
    
    /** ReadWriteLock for thread-safe member operations */
    private final @NotNull ReadWriteLock lock = new ReentrantReadWriteLock();
    
    /** Whether this team has been eliminated from the game */
    private volatile boolean eliminated = false;
    
    /** Team creation timestamp */
    private final long createdAt;
    
    /**
     * Constructs a new Team with the specified team number.
     * 
     * @param teamNumber The team number for display purposes (1, 2, 3, etc.)
     */
    public Team(int teamNumber) {
        this.teamId = UUID.randomUUID();
        this.teamNumber = teamNumber;
        this.members = Collections.newSetFromMap(new ConcurrentHashMap<>());
        this.memberCache = new ConcurrentHashMap<>();
        this.createdAt = System.currentTimeMillis();
    }
    
    /**
     * Adds a player to this team.
     * 
     * @param player The player to add
     * @return true if the player was added, false if they were already on the team
     */
    public boolean addMember(@NotNull Player player) {
        lock.writeLock().lock();
        try {
            UUID playerId = player.getUniqueId();
            if (members.add(playerId)) {
                memberCache.put(playerId, player);
                return true;
            }
            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Removes a player from this team.
     * 
     * @param playerId The UUID of the player to remove
     * @return true if the player was removed, false if they weren't on the team
     */
    public boolean removeMember(@NotNull UUID playerId) {
        lock.writeLock().lock();
        try {
            boolean removed = members.remove(playerId);
            if (removed) {
                memberCache.remove(playerId);
            }
            return removed;
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Checks if a player is a member of this team.
     * 
     * @param playerId The UUID of the player to check
     * @return true if the player is on this team
     */
    public boolean isMember(@NotNull UUID playerId) {
        lock.readLock().lock();
        try {
            return members.contains(playerId);
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Gets all member UUIDs for this team.
     * 
     * @return An unmodifiable set of member UUIDs
     */
    public @NotNull Set<UUID> getMembers() {
        lock.readLock().lock();
        try {
            return Collections.unmodifiableSet(new HashSet<>(members));
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Gets all online team members.
     * 
     * @return A list of online team member Player objects
     */
    public @NotNull List<Player> getOnlineMembers() {
        lock.readLock().lock();
        try {
            List<Player> onlineMembers = new ArrayList<>();
            for (UUID memberId : members) {
                Player player = memberCache.get(memberId);
                if (player != null && player.isOnline()) {
                    onlineMembers.add(player);
                }
            }
            return onlineMembers;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Gets a cached player object for a team member.
     * 
     * @param playerId The UUID of the player
     * @return The Player object, or null if not found or offline
     */
    public @Nullable Player getCachedMember(@NotNull UUID playerId) {
        lock.readLock().lock();
        try {
            Player player = memberCache.get(playerId);
            return (player != null && player.isOnline()) ? player : null;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Gets the number of members on this team.
     * 
     * @return The team size
     */
    public int getSize() {
        lock.readLock().lock();
        try {
            return members.size();
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Gets the number of members on this team.
     * 
     * @return The team size (alias for getSize())
     */
    public int getMemberCount() {
        return getSize();
    }
    
    /**
     * Gets the number of online members on this team.
     * 
     * @return The number of online team members
     */
    public int getOnlineSize() {
        return getOnlineMembers().size();
    }
    
    /**
     * Checks if this team is invite only.
     * For now, teams are always open to join.
     * TODO: Implement invite-only functionality when team privacy settings are added.
     * 
     * @return false (teams are currently always open)
     */
    public boolean isInviteOnly() {
        // TODO: Add team privacy settings
        return false;
    }
    
    /**
     * Checks if this team is empty (no members).
     * 
     * @return true if the team has no members
     */
    public boolean isEmpty() {
        lock.readLock().lock();
        try {
            return members.isEmpty();
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Checks if this team is full based on the given game mode.
     * 
     * @param gameMode The game mode to check team size against
     * @return true if the team is at maximum capacity
     */
    public boolean isFull(@NotNull GameMode gameMode) {
        return getSize() >= gameMode.getTeamSize();
    }
    
    /**
     * Checks if this team has any online members.
     * 
     * @return true if at least one team member is online
     */
    public boolean hasOnlineMembers() {
        return getOnlineSize() > 0;
    }
    
    /**
     * Gets the team's unique identifier.
     * 
     * @return The team UUID
     */
    public @NotNull UUID getTeamId() {
        return teamId;
    }
    
    /**
     * Gets the team number for display purposes.
     * 
     * @return The team number (1, 2, 3, etc.)
     */
    public int getTeamNumber() {
        return teamNumber;
    }
    
    /**
     * Checks if this team has been eliminated.
     * 
     * @return true if the team is eliminated
     */
    public boolean isEliminated() {
        return eliminated;
    }
    
    /**
     * Marks this team as eliminated.
     */
    public void eliminate() {
        this.eliminated = true;
    }
    
    /**
     * Gets the time when this team was created.
     * 
     * @return The creation timestamp in milliseconds
     */
    public long getCreatedAt() {
        return createdAt;
    }
    
    /**
     * Gets a display name for this team.
     * 
     * @return A formatted team name (e.g., "Team 1", "Team 2")
     */
    public @NotNull String getDisplayName() {
        return "Team " + teamNumber;
    }
    
    /**
     * Gets the names of all team members.
     * 
     * @return A list of member names
     */
    public @NotNull List<String> getMemberNames() {
        lock.readLock().lock();
        try {
            List<String> names = new ArrayList<>();
            for (UUID memberId : members) {
                Player player = memberCache.get(memberId);
                if (player != null) {
                    names.add(player.getName());
                }
            }
            return names;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Gets a member UUID by their name.
     * 
     * @param name The player name
     * @return The UUID of the member, or null if not found
     */
    public @Nullable UUID getMemberByName(@NotNull String name) {
        lock.readLock().lock();
        try {
            for (UUID memberId : members) {
                Player player = memberCache.get(memberId);
                if (player != null && player.getName().equals(name)) {
                    return memberId;
                }
            }
            return null;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Gets the team leader (first member to join).
     * 
     * @return The UUID of the team leader, or null if team is empty
     */
    public @Nullable UUID getLeader() {
        lock.readLock().lock();
        try {
            // For now, we'll consider the first member in the set as the leader
            // In the future, we could track the actual leader separately
            return members.isEmpty() ? null : members.iterator().next();
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Gets the display number for this team.
     * 
     * @return The team number
     */
    public int getDisplayNumber() {
        return teamNumber;
    }
    
    /**
     * Updates the cached player objects for all team members.
     * This should be called periodically to ensure cache accuracy.
     */
    public void updateCache() {
        lock.writeLock().lock();
        try {
            // Remove offline players from cache
            memberCache.entrySet().removeIf(entry -> {
                Player player = entry.getValue();
                return player == null || !player.isOnline();
            });
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Clears all team data and caches.
     * This should be called when the team is no longer needed.
     */
    public void cleanup() {
        lock.writeLock().lock();
        try {
            members.clear();
            memberCache.clear();
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        Team team = (Team) obj;
        return teamId.equals(team.teamId);
    }
    
    @Override
    public int hashCode() {
        return teamId.hashCode();
    }
    
    @Override
    public String toString() {
        return String.format("Team{id=%s, number=%d, members=%d, eliminated=%b}", 
            teamId.toString().substring(0, 8), teamNumber, getSize(), eliminated);
    }
} 
