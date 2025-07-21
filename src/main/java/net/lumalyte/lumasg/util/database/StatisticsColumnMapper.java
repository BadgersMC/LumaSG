package net.lumalyte.lumasg.util.database;

import net.lumalyte.lumasg.statistics.StatType;
import org.jetbrains.annotations.NotNull;

import java.util.EnumMap;
import java.util.Map;

/**
 * Utility class for mapping statistic types to database column names.
 * This centralizes the column mapping logic and improves maintainability.
 */
public final class StatisticsColumnMapper {
    
    /**
     * Pre-computed mapping of statistic types to database column names.
     * Using EnumMap for optimal performance with enum keys.
     */
    private static final Map<StatType, String> COLUMN_MAPPING = new EnumMap<>(StatType.class);
    
    static {
        // Initialize the mapping once to avoid repeated switch evaluations
        COLUMN_MAPPING.put(StatType.WINS, "wins");
        COLUMN_MAPPING.put(StatType.KILLS, "kills");
        COLUMN_MAPPING.put(StatType.GAMES_PLAYED, "games_played");
        COLUMN_MAPPING.put(StatType.KILL_DEATH_RATIO, "kills"); // Calculated in application logic
        COLUMN_MAPPING.put(StatType.WIN_RATE, "wins"); // Calculated in application logic
        COLUMN_MAPPING.put(StatType.TIME_PLAYED, "total_time_played");
        COLUMN_MAPPING.put(StatType.BEST_PLACEMENT, "best_placement");
        COLUMN_MAPPING.put(StatType.WIN_STREAK, "best_win_streak");
        COLUMN_MAPPING.put(StatType.TOP3_FINISHES, "top3_finishes");
        COLUMN_MAPPING.put(StatType.DAMAGE_DEALT, "total_damage_dealt");
        COLUMN_MAPPING.put(StatType.CHESTS_OPENED, "chests_opened");
    }
    
    private StatisticsColumnMapper() {
        // Utility class - prevent instantiation
    }
    
    /**
     * Gets the database column name for a statistic type.
     * 
     * @param statType The statistic type
     * @return The corresponding database column name
     * @throws IllegalArgumentException if the statistic type is not supported
     */
    public static @NotNull String getColumnName(@NotNull StatType statType) {
        String columnName = COLUMN_MAPPING.get(statType);
        if (columnName == null) {
            throw new IllegalArgumentException("Unsupported statistic type: " + statType);
        }
        return columnName;
    }
    
    /**
     * Checks if a statistic type requires calculation in application logic.
     * 
     * @param statType The statistic type to check
     * @return true if the statistic is calculated (KDR, win rate), false if it's stored directly
     */
    public static boolean isCalculatedStat(@NotNull StatType statType) {
        return statType == StatType.KILL_DEATH_RATIO || statType == StatType.WIN_RATE;
    }
    
    /**
     * Gets all supported statistic types.
     * 
     * @return An array of all supported statistic types
     */
    public static StatType[] getSupportedStatTypes() {
        return COLUMN_MAPPING.keySet().toArray(new StatType[0]);
    }
    
    /**
     * Gets all database column names used for statistics.
     * 
     * @return An array of all database column names
     */
    public static String[] getAllColumnNames() {
        return COLUMN_MAPPING.values().toArray(new String[0]);
    }
} 
