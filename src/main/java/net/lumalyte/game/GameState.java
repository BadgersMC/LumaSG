package net.lumalyte.game;

/**
 * Represents the different states a Survival Games game can be in.
 * 
 * <p>Each state represents a specific phase of the game with different rules,
 * behaviors, and allowed actions. The game transitions through these states
 * in a specific order as the match progresses.</p>
 * 
 * <p>The typical state flow is: WAITING → COUNTDOWN → GRACE_PERIOD → ACTIVE → 
 * DEATHMATCH → FINISHED. Each state has different implications for player
 * actions, PvP rules, and game mechanics.</p>
 * 
 * @author LumaLyte
 * @version 1.0
 * @since 1.0
 */
public enum GameState {
    
    /**
     * The game is waiting for players to join.
     * 
     * <p>In this state, players can join the game and the countdown has not
     * started yet. The game remains in this state until enough players have
     * joined and the countdown is manually started.</p>
     * 
     * <p>Characteristics:
     * <ul>
     *   <li>Players can join freely</li>
     *   <li>No PvP is allowed</li>
     *   <li>No countdown is running</li>
     *   <li>Game can be cancelled or reset</li>
     * </ul></p>
     */
    WAITING,
    
    /**
     * The game is counting down to start.
     * 
     * <p>In this state, the game has enough players and is counting down
     * before the actual gameplay begins. Players see title messages showing
     * the remaining time until the game starts.</p>
     * 
     * <p>Characteristics:
     * <ul>
     *   <li>Players cannot join (game is locked)</li>
     *   <li>No PvP is allowed</li>
     *   <li>Countdown timer is running</li>
     *   <li>Players see countdown titles</li>
     *   <li>Countdown can be cancelled to return to WAITING</li>
     * </ul></p>
     */
    COUNTDOWN,
    
    /**
     * The game has started but PvP is disabled (grace period).
     * 
     * <p>In this state, the game has officially started and players are
     * teleported to their spawn points, but PvP is disabled to give players
     * time to prepare and gather resources before combat begins.</p>
     * 
     * <p>Characteristics:
     * <ul>
     *   <li>Players are at spawn points</li>
     *   <li>PvP is disabled</li>
     *   <li>Players can gather resources</li>
     *   <li>Grace period timer is running</li>
     *   <li>Players cannot leave the arena</li>
     * </ul></p>
     */
    GRACE_PERIOD,
    
    /**
     * The game is fully active with PvP enabled.
     * 
     * <p>In this state, the main gameplay occurs. PvP is enabled, players
     * can fight each other, and the game continues until there is only one
     * player remaining or the time limit is reached.</p>
     * 
     * <p>Characteristics:
     * <ul>
     *   <li>PvP is fully enabled</li>
     *   <li>Players can fight and eliminate each other</li>
     *   <li>Game timer is running</li>
     *   <li>Players cannot leave the arena</li>
     *   <li>Game ends when one player remains or time expires</li>
     * </ul></p>
     */
    ACTIVE,
    
    /**
     * The game is in deathmatch mode.
     * 
     * <p>In this state, the game has reached its time limit but multiple
     * players are still alive. The arena shrinks to force remaining players
     * into combat to determine the winner.</p>
     * 
     * <p>Characteristics:
     * <ul>
     *   <li>PvP is enabled</li>
     *   <li>Arena boundaries are shrinking</li>
     *   <li>Players are forced into combat</li>
     *   <li>Deathmatch timer is running</li>
     *   <li>Game ends when one player remains</li>
     * </ul></p>
     */
    DEATHMATCH,
    
    /**
     * The game has finished.
     * 
     * <p>In this state, the game has ended and a winner has been determined.
     * Players are being cleaned up and the game instance is being prepared
     * for removal.</p>
     * 
     * <p>Characteristics:
     * <ul>
     *   <li>No further gameplay occurs</li>
     *   <li>Winner has been determined</li>
     *   <li>Players are being cleaned up</li>
     *   <li>Game instance is being removed</li>
     *   <li>No new actions are allowed</li>
     * </ul></p>
     */
    FINISHED
} 
