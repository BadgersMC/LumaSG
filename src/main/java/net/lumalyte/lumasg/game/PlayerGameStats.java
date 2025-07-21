package net.lumalyte.lumasg.game;

/**
 * Record to hold player game statistics.
 * 
 * @param kills Number of kills
 * @param damageDealt Total damage dealt
 * @param damageTaken Total damage taken
 * @param chestsOpened Number of chests opened
 */
public record PlayerGameStats(
    int kills,
    double damageDealt,
    double damageTaken,
    int chestsOpened
) {
    /**
     * Creates a new PlayerGameStats instance.
     */
    public PlayerGameStats {
        if (kills < 0) {
            throw new IllegalArgumentException("Kills cannot be negative");
        }
        if (damageDealt < 0) {
            throw new IllegalArgumentException("Damage dealt cannot be negative");
        }
        if (damageTaken < 0) {
            throw new IllegalArgumentException("Damage taken cannot be negative");
        }
        if (chestsOpened < 0) {
            throw new IllegalArgumentException("Chests opened cannot be negative");
        }
    }
    
    /**
     * Gets the number of kills.
     */
    public int getKills() {
        return kills;
    }
    
    /**
     * Gets the total damage dealt.
     */
    public double getDamageDealt() {
        return damageDealt;
    }
    
    /**
     * Gets the total damage taken.
     */
    public double getDamageTaken() {
        return damageTaken;
    }
    
    /**
     * Gets the number of chests opened.
     */
    public int getChestsOpened() {
        return chestsOpened;
    }
} 
