package net.lumalyte.lumasg.game;

/**
 * Represents the different game modes available for Survival Games.
 * 
 * <p>Each game mode determines how many players can be on a team and
 * affects various game mechanics like spawning, team effects, and victory
 * conditions.</p>
 * 
 * @author LumaLyte
 * @version 1.0
 * @since 1.0
 */
public enum GameMode {
    
    /**
     * Solo mode - every player for themselves.
     * 
     * <p>In this mode, each player is their own team. There are no team
     * mechanics, glowing effects, or shared victory conditions.</p>
     * 
     * <p>Characteristics:
     * <ul>
     *   <li>Team size: 1 player</li>
     *   <li>No team glowing effects</li>
     *   <li>Individual spawning</li>
     *   <li>Single winner</li>
     * </ul></p>
     */
    SOLO(1, "Solo", "Every player for themselves"),
    
    /**
     * Duos mode - teams of 2 players.
     * 
     * <p>In this mode, players are paired into teams of 2. Teammates can see
     * each other with green glowing effects and spawn near each other.</p>
     * 
     * <p>Characteristics:
     * <ul>
     *   <li>Team size: 2 players</li>
     *   <li>Green glowing effects for teammates</li>
     *   <li>Team-based spawning</li>
     *   <li>Team victory (both players win if one team remains)</li>
     * </ul></p>
     */
    DUOS(2, "Duos", "Teams of 2 players"),
    
    /**
     * Trios mode - teams of 3 players.
     * 
     * <p>In this mode, players are grouped into teams of 3. Teammates can see
     * each other with green glowing effects and spawn near each other.</p>
     * 
     * <p>Characteristics:
     * <ul>
     *   <li>Team size: 3 players</li>
     *   <li>Green glowing effects for teammates</li>
     *   <li>Team-based spawning</li>
     *   <li>Team victory (all remaining players win if one team remains)</li>
     * </ul></p>
     */
    TRIOS(3, "Trios", "Teams of 3 players");
    
    /** The maximum number of players allowed on a team in this mode */
    private final int teamSize;
    
    /** The display name for this game mode */
    private final String displayName;
    
    /** A description of this game mode */
    private final String description;
    
    /**
     * Constructs a GameMode with the specified properties.
     * 
     * @param teamSize The maximum team size for this mode
     * @param displayName The display name for this mode
     * @param description A description of this mode
     */
    GameMode(int teamSize, String displayName, String description) {
        this.teamSize = teamSize;
        this.displayName = displayName;
        this.description = description;
    }
    
    /**
     * Gets the maximum team size for this game mode.
     * 
     * @return The maximum number of players allowed on a team
     */
    public int getTeamSize() {
        return teamSize;
    }
    
    /**
     * Gets the display name for this game mode.
     * 
     * @return The display name
     */
    public String getDisplayName() {
        return displayName;
    }
    
    /**
     * Gets the description for this game mode.
     * 
     * @return The description
     */
    public String getDescription() {
        return description;
    }
    
    /**
     * Checks if this game mode uses teams (team size > 1).
     * 
     * @return true if this mode uses teams, false for solo
     */
    public boolean isTeamMode() {
        return teamSize > 1;
    }
    
    /**
     * Calculates the maximum number of teams possible with the given player count.
     * 
     * @param playerCount The number of players
     * @return The maximum number of complete teams
     */
    public int getMaxTeams(int playerCount) {
        return playerCount / teamSize;
    }
    
    /**
     * Calculates the ideal number of players for this game mode.
     * This returns the largest multiple of team size that doesn't exceed the max players.
     * 
     * @param maxPlayers The maximum players supported by the arena
     * @return The ideal number of players for even teams
     */
    public int getIdealPlayerCount(int maxPlayers) {
        return (maxPlayers / teamSize) * teamSize;
    }
    
    /**
     * Gets a GameMode by its display name (case-insensitive).
     * 
     * @param name The display name to search for
     * @return The matching GameMode, or null if not found
     */
    public static GameMode fromDisplayName(String name) {
        if (name == null) return null;
        
        for (GameMode mode : values()) {
            if (mode.displayName.equalsIgnoreCase(name)) {
                return mode;
            }
        }
        return null;
    }
} 
