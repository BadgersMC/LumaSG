package net.lumalyte.game;

import net.lumalyte.LumaSG;
import net.lumalyte.util.DebugLogger;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Manages all team-related operations for a specific Survival Games match.
 * 
 * <p>This class handles team creation, player assignment, team effects (like glowing),
 * and team-based game mechanics such as victory conditions and elimination tracking.</p>
 * 
 * @author LumaLyte
 * @version 1.0
 * @since 1.0
 */
public class GameTeamManager {
    
    /** The game this team manager belongs to */
    private final @NotNull Game game;
    
    /** The plugin instance */
    private final @NotNull LumaSG plugin;
    
    /** The current game mode determining team structure */
    private @NotNull GameMode gameMode;
    
    /** Map of team numbers to Team objects */
    private final @NotNull Map<Integer, Team> teams;
    
    /** Map of player UUIDs to their team numbers for quick lookup */
    private final @NotNull Map<UUID, Integer> playerTeams;
    
    /** ReadWriteLock for thread-safe team operations */
    private final @NotNull ReadWriteLock lock = new ReentrantReadWriteLock();
    
    /** Debug logger for team operations */
    private final @NotNull DebugLogger.ContextualLogger logger;
    
    /** Whether team glow effects are enabled */
    private volatile boolean glowEffectsEnabled = true;
    
    /** Current team counter for assigning team numbers */
    private volatile int teamCounter = 1;
    
    /**
     * Constructs a new GameTeamManager for the specified game.
     * 
     * @param plugin The plugin instance
     * @param game The game this manager belongs to
     * @param gameMode The initial game mode
     */
    public GameTeamManager(@NotNull LumaSG plugin, @NotNull Game game, @NotNull GameMode gameMode) {
        this.plugin = plugin;
        this.game = game;
        this.gameMode = gameMode;
        this.teams = new ConcurrentHashMap<>();
        this.playerTeams = new ConcurrentHashMap<>();
        this.logger = new DebugLogger(plugin).forClass(GameTeamManager.class);
    }
    
    /**
     * Sets the game mode and reorganizes teams accordingly.
     * 
     * @param gameMode The new game mode
     */
    public void setGameMode(@NotNull GameMode gameMode) {
        this.gameMode = gameMode;
        logger.debug("Game mode changed to: " + gameMode.getDisplayName());
        
        if (gameMode == GameMode.SOLO) {
            disbandAllTeams();
        }
    }
    
    /**
     * Gets the current game mode.
     * 
     * @return The current game mode
     */
    public @NotNull GameMode getGameMode() {
        return gameMode;
    }
    
    /**
     * Assigns a player to a team based on the current game mode.
     * 
     * @param player The player to assign
     * @return The team the player was assigned to, or null if assignment failed
     */
    public @Nullable Team assignPlayerToTeam(@NotNull Player player) {
        if (gameMode == GameMode.SOLO) {
            return createSoloTeam(player);
        }
        
        Team availableTeam = findAvailableTeam();
        
        if (availableTeam != null) {
            if (availableTeam.addMember(player)) {
                playerTeams.put(player.getUniqueId(), availableTeam.getTeamNumber());
                logger.debug("Assigned player " + player.getName() + " to existing " + availableTeam.getDisplayName());
                
                if (gameMode.isTeamMode() && glowEffectsEnabled) {
                    applyTeamEffects(availableTeam);
                }
                
                return availableTeam;
            }
        }
        
        Team newTeam = createNewTeam();
        if (newTeam.addMember(player)) {
            teams.put(newTeam.getTeamNumber(), newTeam);
            playerTeams.put(player.getUniqueId(), newTeam.getTeamNumber());
            logger.debug("Created new " + newTeam.getDisplayName() + " for player " + player.getName());
            
            if (gameMode.isTeamMode() && glowEffectsEnabled) {
                applyTeamEffects(newTeam);
            }
            
            return newTeam;
        }
        
        return null;
    }
    
    /**
     * Creates a solo team for a player (team size = 1).
     * 
     * @param player The player to create a solo team for
     * @return The created team
     */
    private @NotNull Team createSoloTeam(@NotNull Player player) {
        Team soloTeam = createNewTeam();
        soloTeam.addMember(player);
        teams.put(soloTeam.getTeamNumber(), soloTeam);
        playerTeams.put(player.getUniqueId(), soloTeam.getTeamNumber());
        logger.debug("Created solo team for player " + player.getName());
        return soloTeam;
    }
    
    /**
     * Finds an available team with space for more players.
     * 
     * @return An available team, or null if none found
     */
    private @Nullable Team findAvailableTeam() {
        for (Team team : teams.values()) {
            if (!team.isFull(gameMode) && !team.isEliminated()) {
                return team;
            }
        }
        return null;
    }
    
    /**
     * Creates a new team with the next available team number.
     * 
     * @return The newly created team
     */
    private @NotNull Team createNewTeam() {
        Team team = new Team(teamCounter++);
        logger.debug("Created new team with number: " + team.getTeamNumber());
        return team;
    }
    
    /**
     * Removes a player from their current team.
     * 
     * @param player The player to remove
     * @return true if the player was removed from a team
     */
    public boolean removePlayerFromTeam(@NotNull Player player) {
        UUID playerId = player.getUniqueId();
        Integer teamNumber = playerTeams.remove(playerId);
        
        if (teamNumber != null) {
            Team team = teams.get(teamNumber);
            if (team != null) {
                team.removeMember(playerId);
                logger.debug("Removed player " + player.getName() + " from " + team.getDisplayName());
                
                if (gameMode.isTeamMode()) {
                    removePlayerTeamEffects(player);
                }
                
                if (team.isEmpty()) {
                    teams.remove(teamNumber);
                    logger.debug("Disbanded empty " + team.getDisplayName());
                } else {
                    if (gameMode.isTeamMode() && glowEffectsEnabled) {
                        applyTeamEffects(team);
                    }
                }
                
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Gets the team a player belongs to.
     * 
     * @param player The player to check
     * @return The player's team, or null if not on a team
     */
    public @Nullable Team getPlayerTeam(@NotNull Player player) {
        Integer teamNumber = playerTeams.get(player.getUniqueId());
        return teamNumber != null ? teams.get(teamNumber) : null;
    }
    
    /**
     * Checks if two players are on the same team.
     * 
     * @param player1 The first player
     * @param player2 The second player
     * @return true if both players are on the same team
     */
    public boolean areTeammates(@NotNull Player player1, @NotNull Player player2) {
        if (gameMode == GameMode.SOLO) {
            return false;
        }
        
        Integer team1 = playerTeams.get(player1.getUniqueId());
        Integer team2 = playerTeams.get(player2.getUniqueId());
        
        return team1 != null && team1.equals(team2);
    }
    
    /**
     * Gets all teams in the game.
     * 
     * @return An unmodifiable collection of all teams
     */
    public @NotNull Collection<Team> getAllTeams() {
        return Collections.unmodifiableCollection(new ArrayList<>(teams.values()));
    }
    
    /**
     * Gets all active (non-eliminated) teams.
     * 
     * @return A list of active teams
     */
    public @NotNull List<Team> getActiveTeams() {
        return teams.values().stream()
            .filter(team -> !team.isEliminated() && team.hasOnlineMembers())
            .toList();
    }
    
    /**
     * Gets the number of active teams remaining.
     * 
     * @return The count of active teams
     */
    public int getActiveTeamCount() {
        return getActiveTeams().size();
    }
    
    /**
     * Eliminates a team from the game.
     * 
     * @param team The team to eliminate
     */
    public void eliminateTeam(@NotNull Team team) {
        team.eliminate();
        logger.debug("Eliminated " + team.getDisplayName());
        
        if (gameMode.isTeamMode()) {
            for (Player member : team.getOnlineMembers()) {
                removePlayerTeamEffects(member);
            }
        }
    }
    
    /**
     * Applies team glow effects to all members of a team.
     * 
     * @param team The team to apply effects to
     */
    public void applyTeamEffects(@NotNull Team team) {
        if (!gameMode.isTeamMode() || !glowEffectsEnabled) {
            return;
        }
        
        List<Player> members = team.getOnlineMembers();
        if (members.size() <= 1) {
            return;
        }
        
        for (Player member : members) {
            for (Player teammate : members) {
                if (!member.equals(teammate)) {
                    teammate.setGlowing(true);
                    
                    member.addPotionEffect(new PotionEffect(
                        PotionEffectType.LUCK,
                        Integer.MAX_VALUE, 
                        0, 
                        true, 
                        false, 
                        false
                    ));
                }
            }
        }
        
        logger.debug("Applied team effects to " + team.getDisplayName() + " (" + members.size() + " members)");
    }
    
    /**
     * Removes team effects from a player.
     * 
     * @param player The player to remove effects from
     */
    public void removePlayerTeamEffects(@NotNull Player player) {
        player.setGlowing(false);
        player.removePotionEffect(PotionEffectType.LUCK);
    }
    
    /**
     * Refreshes team effects for all teams.
     * This should be called periodically to maintain proper team visibility.
     */
    public void refreshTeamEffects() {
        if (!gameMode.isTeamMode() || !glowEffectsEnabled) {
            return;
        }
        
        for (Team team : teams.values()) {
            if (!team.isEliminated()) {
                applyTeamEffects(team);
            }
        }
    }
    
    /**
     * Enables or disables team glow effects.
     * 
     * @param enabled Whether to enable glow effects
     */
    public void setGlowEffectsEnabled(boolean enabled) {
        this.glowEffectsEnabled = enabled;
        if (!enabled) {
            for (Team team : getAllTeams()) {
                for (Player member : team.getOnlineMembers()) {
                    removePlayerTeamEffects(member);
                }
            }
        } else {
            refreshTeamEffects();
        }
    }
    
    /**
     * Disbands all teams and removes all players.
     */
    public void disbandAllTeams() {
        for (Team team : teams.values()) {
            for (Player member : team.getOnlineMembers()) {
                removePlayerTeamEffects(member);
            }
            team.cleanup();
        }
        
        teams.clear();
        playerTeams.clear();
        teamCounter = 1;
        
        logger.debug("Disbanded all teams");
    }
    
    /**
     * Auto-balances teams by redistributing players as evenly as possible.
     * This is useful for ensuring fair team distribution.
     */
    public void autoBalanceTeams() {
        if (gameMode == GameMode.SOLO) {
            return; // No balancing needed for solo mode
        }
        
        lock.writeLock().lock();
        try {
            List<Player> allPlayers = new ArrayList<>();
            
            // Collect all players
            for (Team team : teams.values()) {
                allPlayers.addAll(team.getOnlineMembers());
            }
            
            // Clear existing teams
            disbandAllTeams();
            
            // Shuffle players for random distribution
            Collections.shuffle(allPlayers, ThreadLocalRandom.current());
            
            // Reassign players to balanced teams
            for (Player player : allPlayers) {
                assignPlayerToTeam(player);
            }
            
            logger.debug("Auto-balanced teams for " + allPlayers.size() + " players");
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Gets statistics about the current team setup.
     * 
     * @return A map containing team statistics
     */
    public @NotNull Map<String, Object> getTeamStatistics() {
        lock.readLock().lock();
        try {
            Map<String, Object> stats = new HashMap<>();
            
            stats.put("gameMode", gameMode.getDisplayName());
            stats.put("totalTeams", teams.size());
            stats.put("activeTeams", getActiveTeamCount());
            stats.put("totalPlayers", playerTeams.size());
            stats.put("glowEffectsEnabled", glowEffectsEnabled);
            
            // Team size distribution
            Map<Integer, Integer> sizeDistribution = new HashMap<>();
            for (Team team : teams.values()) {
                int size = team.getOnlineSize();
                sizeDistribution.put(size, sizeDistribution.getOrDefault(size, 0) + 1);
            }
            stats.put("teamSizeDistribution", sizeDistribution);
            
            return stats;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Cleans up all team data when the game ends.
     */
    public void cleanup() {
        disbandAllTeams();
        logger.debug("GameTeamManager cleanup completed");
    }
    
    /**
     * Gets all teams in this game.
     * 
     * @return An unmodifiable collection of all teams
     */
    public @NotNull Collection<Team> getTeams() {
        lock.readLock().lock();
        try {
            return Collections.unmodifiableCollection(teams.values());
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Creates a new team for this game.
     * 
     * @return The created team, or null if maximum teams reached
     */
    public @Nullable Team createTeam() {
        lock.writeLock().lock();
        try {
            // Create new team with the next available team number
            Team newTeam = new Team(teamCounter++);
            
            // Add to teams collection
            teams.put(newTeam.getTeamNumber(), newTeam);
            
            logger.debug("Created new team " + newTeam.getTeamNumber() + " (ID: " + newTeam.getTeamId() + ")");
            return newTeam;
            
        } finally {
            lock.writeLock().unlock();
        }
    }
} 