package net.lumalyte.statistics;

/**
 * Enumeration of different statistic types that can be tracked and used for leaderboards.
 * 
 * @author LumaLyte
 * @version 1.0
 * @since 1.0
 */
public enum StatType {
    
    /** Total number of games won */
    WINS,
    
    /** Total number of kills */
    KILLS,
    
    /** Total number of games played */
    GAMES_PLAYED,
    
    /** Kill/Death ratio (calculated) */
    KILL_DEATH_RATIO,
    
    /** Win rate percentage (calculated) */
    WIN_RATE,
    
    /** Total time played in seconds */
    TIME_PLAYED,
    
    /** Best placement achieved (lower is better) */
    BEST_PLACEMENT,
    
    /** Best win streak achieved */
    WIN_STREAK,
    
    /** Number of top 3 finishes */
    TOP3_FINISHES,
    
    /** Total damage dealt to other players */
    DAMAGE_DEALT,
    
    /** Number of chests opened */
    CHESTS_OPENED
} 