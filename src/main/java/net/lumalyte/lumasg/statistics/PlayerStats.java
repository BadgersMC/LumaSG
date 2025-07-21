package net.lumalyte.lumasg.statistics;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Represents player statistics for Survival Games.
 * 
 * <p>This class holds all statistical data for a player including wins, losses,
 * kills, deaths, games played, and various time-based metrics.</p>
 * 
 * @author LumaLyte
 * @version 1.0
 * @since 1.0
 */
public class PlayerStats {
    
    /** The player's unique identifier */
    private final @NotNull UUID playerId;
    
    /** The player's name (for display purposes) */
    private @NotNull String playerName;
    
    /** Total number of games won */
    private int wins;
    
    /** Total number of games lost */
    private int losses;
    
    /** Total number of kills */
    private int kills;
    
    /** Total number of deaths */
    private int deaths;
    
    /** Total number of games played */
    private int gamesPlayed;
    
    /** Total time played in seconds */
    private long totalTimePlayed;
    
    /** Best placement in a game (1 = winner, 2 = second place, etc.) */
    private int bestPlacement;
    
    /** Current win streak */
    private int currentWinStreak;
    
    /** Best win streak ever achieved */
    private int bestWinStreak;
    
    /** Number of times placed in top 3 */
    private int top3Finishes;
    
    /** Total damage dealt to other players */
    private double totalDamageDealt;
    
    /** Total damage taken from other players */
    private double totalDamageTaken;
    
    /** Number of chests opened */
    private int chestsOpened;
    
    /** Date and time when the player first joined */
    private final @Nullable LocalDateTime firstJoined;
    
    /** Date and time of the player's last game */
    private @Nullable LocalDateTime lastPlayed;
    
    /** Date and time when stats were last updated */
    private @NotNull LocalDateTime lastUpdated;
    
    /**
     * Creates a new PlayerStats instance with default values.
     * 
     * @param playerId The player's unique identifier
     * @param playerName The player's name
     */
    public PlayerStats(@NotNull UUID playerId, @NotNull String playerName) {
        this.playerId = playerId;
        this.playerName = playerName;
        this.wins = 0;
        this.losses = 0;
        this.kills = 0;
        this.deaths = 0;
        this.gamesPlayed = 0;
        this.totalTimePlayed = 0;
        this.bestPlacement = Integer.MAX_VALUE; // Worst possible placement initially
        this.currentWinStreak = 0;
        this.bestWinStreak = 0;
        this.top3Finishes = 0;
        this.totalDamageDealt = 0.0;
        this.totalDamageTaken = 0.0;
        this.chestsOpened = 0;
        this.firstJoined = LocalDateTime.now();
        this.lastPlayed = null;
        this.lastUpdated = LocalDateTime.now();
    }
    
    /**
     * Creates a PlayerStats instance with all values specified.
     * Used for loading from database.
     */
    public PlayerStats(@NotNull UUID playerId, @NotNull String playerName, int wins, int losses,
                      int kills, int deaths, int gamesPlayed, long totalTimePlayed,
                      int bestPlacement, int currentWinStreak, int bestWinStreak,
                      int top3Finishes, double totalDamageDealt, double totalDamageTaken,
                      int chestsOpened, @Nullable LocalDateTime firstJoined,
                      @Nullable LocalDateTime lastPlayed, @NotNull LocalDateTime lastUpdated) {
        this.playerId = playerId;
        this.playerName = playerName;
        this.wins = wins;
        this.losses = losses;
        this.kills = kills;
        this.deaths = deaths;
        this.gamesPlayed = gamesPlayed;
        this.totalTimePlayed = totalTimePlayed;
        this.bestPlacement = bestPlacement;
        this.currentWinStreak = currentWinStreak;
        this.bestWinStreak = bestWinStreak;
        this.top3Finishes = top3Finishes;
        this.totalDamageDealt = totalDamageDealt;
        this.totalDamageTaken = totalDamageTaken;
        this.chestsOpened = chestsOpened;
        this.firstJoined = firstJoined;
        this.lastPlayed = lastPlayed;
        this.lastUpdated = lastUpdated;
    }
    
    // Getters
    
    public @NotNull UUID getPlayerId() {
        return playerId;
    }
    
    public @NotNull String getPlayerName() {
        return playerName;
    }
    
    public int getWins() {
        return wins;
    }
    
    public int getLosses() {
        return losses;
    }
    
    public int getKills() {
        return kills;
    }
    
    public int getDeaths() {
        return deaths;
    }
    
    public int getGamesPlayed() {
        return gamesPlayed;
    }
    
    public long getTotalTimePlayed() {
        return totalTimePlayed;
    }
    
    public int getBestPlacement() {
        return bestPlacement == Integer.MAX_VALUE ? 0 : bestPlacement;
    }
    
    public int getCurrentWinStreak() {
        return currentWinStreak;
    }
    
    public int getBestWinStreak() {
        return bestWinStreak;
    }
    
    public int getTop3Finishes() {
        return top3Finishes;
    }
    
    public double getTotalDamageDealt() {
        return totalDamageDealt;
    }
    
    public double getTotalDamageTaken() {
        return totalDamageTaken;
    }
    
    public int getChestsOpened() {
        return chestsOpened;
    }
    
    public @Nullable LocalDateTime getFirstJoined() {
        return firstJoined;
    }
    
    public @Nullable LocalDateTime getLastPlayed() {
        return lastPlayed;
    }
    
    public @NotNull LocalDateTime getLastUpdated() {
        return lastUpdated;
    }
    
    // Calculated properties
    
    /**
     * Calculates the player's kill/death ratio.
     * 
     * @return The K/D ratio, or kills if deaths is 0
     */
    public double getKillDeathRatio() {
        if (deaths == 0) {
            return kills; // Avoid division by zero
        }
        return (double) kills / deaths;
    }
    
    /**
     * Calculates the player's win rate as a percentage.
     * 
     * @return Win rate between 0.0 and 100.0
     */
    public double getWinRate() {
        if (gamesPlayed == 0) {
            return 0.0;
        }
        return ((double) wins / gamesPlayed) * 100.0;
    }
    
    /**
     * Calculates the player's top 3 finish rate as a percentage.
     * 
     * @return Top 3 rate between 0.0 and 100.0
     */
    public double getTop3Rate() {
        if (gamesPlayed == 0) {
            return 0.0;
        }
        return ((double) top3Finishes / gamesPlayed) * 100.0;
    }
    
    /**
     * Calculates average kills per game.
     * 
     * @return Average kills per game
     */
    public double getAverageKillsPerGame() {
        if (gamesPlayed == 0) {
            return 0.0;
        }
        return (double) kills / gamesPlayed;
    }
    
    /**
     * Calculates average game time in minutes.
     * 
     * @return Average game time in minutes
     */
    public double getAverageGameTime() {
        if (gamesPlayed == 0) {
            return 0.0;
        }
        return (double) totalTimePlayed / gamesPlayed / 60.0;
    }
    
    // Setters (for updates)
    
    public void setPlayerName(@NotNull String playerName) {
        this.playerName = playerName;
        this.lastUpdated = LocalDateTime.now();
    }
    
    public void setWins(int wins) {
        this.wins = wins;
        this.lastUpdated = LocalDateTime.now();
    }
    
    public void setLosses(int losses) {
        this.losses = losses;
        this.lastUpdated = LocalDateTime.now();
    }
    
    public void setKills(int kills) {
        this.kills = kills;
        this.lastUpdated = LocalDateTime.now();
    }
    
    public void setDeaths(int deaths) {
        this.deaths = deaths;
        this.lastUpdated = LocalDateTime.now();
    }
    
    public void setGamesPlayed(int gamesPlayed) {
        this.gamesPlayed = gamesPlayed;
        this.lastUpdated = LocalDateTime.now();
    }
    
    public void setTotalTimePlayed(long totalTimePlayed) {
        this.totalTimePlayed = totalTimePlayed;
        this.lastUpdated = LocalDateTime.now();
    }
    
    public void setBestPlacement(int bestPlacement) {
        if (bestPlacement < this.bestPlacement) {
            this.bestPlacement = bestPlacement;
            this.lastUpdated = LocalDateTime.now();
        }
    }
    
    public void setCurrentWinStreak(int currentWinStreak) {
        this.currentWinStreak = currentWinStreak;
        if (currentWinStreak > bestWinStreak) {
            this.bestWinStreak = currentWinStreak;
        }
        this.lastUpdated = LocalDateTime.now();
    }
    
    public void setBestWinStreak(int bestWinStreak) {
        this.bestWinStreak = bestWinStreak;
        this.lastUpdated = LocalDateTime.now();
    }
    
    public void setTop3Finishes(int top3Finishes) {
        this.top3Finishes = top3Finishes;
        this.lastUpdated = LocalDateTime.now();
    }
    
    public void setTotalDamageDealt(double totalDamageDealt) {
        this.totalDamageDealt = totalDamageDealt;
        this.lastUpdated = LocalDateTime.now();
    }
    
    public void setTotalDamageTaken(double totalDamageTaken) {
        this.totalDamageTaken = totalDamageTaken;
        this.lastUpdated = LocalDateTime.now();
    }
    
    public void setChestsOpened(int chestsOpened) {
        this.chestsOpened = chestsOpened;
        this.lastUpdated = LocalDateTime.now();
    }
    
    public void setLastPlayed(@Nullable LocalDateTime lastPlayed) {
        this.lastPlayed = lastPlayed;
        this.lastUpdated = LocalDateTime.now();
    }
    
    // Increment methods for easier updates
    
    public void incrementWins() {
        this.wins++;
        this.currentWinStreak++;
        if (this.currentWinStreak > this.bestWinStreak) {
            this.bestWinStreak = this.currentWinStreak;
        }
        this.lastUpdated = LocalDateTime.now();
    }
    
    public void incrementLosses() {
        this.losses++;
        this.currentWinStreak = 0; // Reset win streak on loss
        this.lastUpdated = LocalDateTime.now();
    }
    
    public void incrementKills() {
        this.kills++;
        this.lastUpdated = LocalDateTime.now();
    }
    
    public void incrementDeaths() {
        this.deaths++;
        this.lastUpdated = LocalDateTime.now();
    }
    
    public void incrementGamesPlayed() {
        this.gamesPlayed++;
        this.lastPlayed = LocalDateTime.now();
        this.lastUpdated = LocalDateTime.now();
    }
    
    public void addTimePlayed(long seconds) {
        this.totalTimePlayed += seconds;
        this.lastUpdated = LocalDateTime.now();
    }
    
    public void incrementChestsOpened() {
        this.chestsOpened++;
        this.lastUpdated = LocalDateTime.now();
    }
    
    public void addDamageDealt(double damage) {
        this.totalDamageDealt += damage;
        this.lastUpdated = LocalDateTime.now();
    }
    
    public void addDamageTaken(double damage) {
        this.totalDamageTaken += damage;
        this.lastUpdated = LocalDateTime.now();
    }
    
    /**
     * Updates placement and related statistics.
     * 
     * @param placement The player's placement in the game (1 = winner, 2 = second, etc.)
     */
    public void updatePlacement(int placement) {
        if (placement < this.bestPlacement) {
            this.bestPlacement = placement;
        }
        
        if (placement <= 3) {
            this.top3Finishes++;
        }
        
        this.lastUpdated = LocalDateTime.now();
    }
    
    @Override
    public String toString() {
        return String.format("PlayerStats{playerId=%s, name='%s', wins=%d, losses=%d, kills=%d, deaths=%d, games=%d}",
                playerId, playerName, wins, losses, kills, deaths, gamesPlayed);
    }
} 
