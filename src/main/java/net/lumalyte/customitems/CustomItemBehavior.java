package net.lumalyte.customitems;

/**
 * Enumeration of custom item behavior types.
 * 
 * <p>Each custom item can have a specific behavior that defines how it
 * interacts with players and the game world. This enum defines all
 * available behavior types.</p>
 * 
 * @author LumaLyte
 * @version 1.0
 * @since 1.0
 */
public enum CustomItemBehavior {
    
    /** No special behavior - item acts like a normal item */
    NONE,
    
    /** Player tracking behavior - compass that points to other players */
    PLAYER_TRACKER,
    
    /** Knockback stick behavior - enhanced knockback on hit */
    KNOCKBACK_STICK,
    
    /** Fire bomb behavior - throwable explosive that creates fire */
    FIRE_BOMB,
    
    /** Poison bomb behavior - throwable explosive that creates poison cloud */
    POISON_BOMB,
    
    /** Airdrop flare behavior - calls in supply drops */
    AIRDROP_FLARE;
    
    /**
     * Gets the behavior type from a string.
     * 
     * @param behaviorString The behavior string
     * @return The behavior type, or NONE if invalid
     */
    public static CustomItemBehavior fromString(String behaviorString) {
        if (behaviorString == null || behaviorString.isEmpty()) {
            return NONE;
        }
        
        try {
            return valueOf(behaviorString.toUpperCase());
        } catch (IllegalArgumentException e) {
            return NONE;
        }
    }
    
    /**
     * Checks if this behavior requires special handling.
     * 
     * @return True if this behavior requires special handling
     */
    public boolean requiresSpecialHandling() {
        return this != NONE;
    }
    
    /**
     * Checks if this behavior is a throwable item.
     * 
     * @return True if this behavior involves throwing items
     */
    public boolean isThrowable() {
        return this == FIRE_BOMB || this == POISON_BOMB || this == AIRDROP_FLARE;
    }
    
    /**
     * Checks if this behavior is a tracking item.
     * 
     * @return True if this behavior involves tracking
     */
    public boolean isTracker() {
        return this == PLAYER_TRACKER;
    }
    
    /**
     * Checks if this behavior is a combat item.
     * 
     * @return True if this behavior is primarily for combat
     */
    public boolean isCombat() {
        return this == KNOCKBACK_STICK || this == FIRE_BOMB || this == POISON_BOMB;
    }
    
    /**
     * Checks if this behavior is a utility item.
     * 
     * @return True if this behavior provides utility functionality
     */
    public boolean isUtility() {
        return this == PLAYER_TRACKER || this == AIRDROP_FLARE;
    }
} 