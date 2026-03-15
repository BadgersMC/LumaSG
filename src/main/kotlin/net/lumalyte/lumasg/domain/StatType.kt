package net.lumalyte.lumasg.domain

/** Types of statistics that can be used for leaderboard filtering. */
enum class StatType(val displayName: String, val columnName: String) {
    WINS("Wins", "wins"),
    KILLS("Kills", "kills"),
    GAMES_PLAYED("Games Played", "games_played"),
    KILL_DEATH_RATIO("K/D Ratio", "kills"), // computed from kills/deaths
    WIN_RATE("Win Rate", "wins"), // computed from wins/gamesPlayed
    TIME_PLAYED("Time Played", "total_time_played"),
    BEST_PLACEMENT("Best Placement", "best_placement"),
    WIN_STREAK("Win Streak", "best_win_streak"),
    TOP3_FINISHES("Top 3 Finishes", "top3_finishes"),
    DAMAGE_DEALT("Damage Dealt", "damage_dealt"),
    CHESTS_OPENED("Chests Opened", "chests_opened");
}
