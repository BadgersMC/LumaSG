package net.lumalyte.statistics;

import net.lumalyte.LumaSG;
import net.lumalyte.util.DebugLogger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Manages SQLite database operations for player statistics.
 * 
 * <p>This class handles all database operations including creating tables,
 * inserting, updating, and querying player statistics. All operations are
 * performed asynchronously to avoid blocking the main server thread.</p>
 * 
 * @author LumaLyte
 * @version 1.0
 * @since 1.0
 */
public class StatisticsDatabase {

	private final @NotNull File databaseFile;
    private final @NotNull ExecutorService executorService;
    private final @NotNull DateTimeFormatter dateTimeFormatter;
    
    /** The debug logger instance for this statistics database */
    private final @NotNull DebugLogger.ContextualLogger logger;
    
    /** SQL for creating the player statistics table */
    private static final String CREATE_TABLE_SQL = """
        CREATE TABLE IF NOT EXISTS player_stats (
            player_id TEXT PRIMARY KEY,
            player_name TEXT NOT NULL,
            wins INTEGER DEFAULT 0,
            losses INTEGER DEFAULT 0,
            kills INTEGER DEFAULT 0,
            deaths INTEGER DEFAULT 0,
            games_played INTEGER DEFAULT 0,
            total_time_played INTEGER DEFAULT 0,
            best_placement INTEGER DEFAULT 999999,
            current_win_streak INTEGER DEFAULT 0,
            best_win_streak INTEGER DEFAULT 0,
            top3_finishes INTEGER DEFAULT 0,
            total_damage_dealt REAL DEFAULT 0.0,
            total_damage_taken REAL DEFAULT 0.0,
            chests_opened INTEGER DEFAULT 0,
            first_joined TEXT,
            last_played TEXT,
            last_updated TEXT NOT NULL
        )
        """;
    
    /** SQL for inserting or updating player statistics */
    private static final String UPSERT_STATS_SQL = """
        INSERT OR REPLACE INTO player_stats (
            player_id, player_name, wins, losses, kills, deaths, games_played,
            total_time_played, best_placement, current_win_streak, best_win_streak,
            top3_finishes, total_damage_dealt, total_damage_taken, chests_opened,
            first_joined, last_played, last_updated
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
    
    /** SQL for selecting player statistics by ID */
    private static final String SELECT_STATS_SQL = """
        SELECT * FROM player_stats WHERE player_id = ?
        """;
    
    /** SQL for selecting top players by a specific statistic */
    private static final String SELECT_LEADERBOARD_SQL = """
        SELECT * FROM player_stats ORDER BY %s DESC LIMIT ?
        """;
    
    /** SQL for getting total number of players */
    private static final String COUNT_PLAYERS_SQL = """
        SELECT COUNT(*) FROM player_stats
        """;
    
    /**
     * Creates a new StatisticsDatabase instance.
     * 
     * @param plugin The plugin instance
     */
    public StatisticsDatabase(@NotNull LumaSG plugin) {
		this.databaseFile = new File(plugin.getDataFolder(), "statistics.db");
        this.executorService = Executors.newFixedThreadPool(2, r -> {
            Thread thread = new Thread(r, "LumaSG-Statistics-" + System.currentTimeMillis());
            thread.setDaemon(true);
            return thread;
        });
        this.dateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
        this.logger = plugin.getDebugLogger().forContext("StatisticsDatabase");
        
        // Ensure the plugin data folder exists
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
    }
    
    /**
     * Initializes the database by creating necessary tables.
     * 
     * @return A CompletableFuture that completes when initialization is done
     */
    public @NotNull CompletableFuture<Void> initialize() {
        return CompletableFuture.runAsync(() -> {
            try (Connection connection = getConnection()) {
                createTables(connection);
                logger.info("Statistics database initialized successfully");
            } catch (SQLException e) {
                logger.severe("Failed to initialize statistics database", e);
                throw new RuntimeException("Database initialization failed", e);
            }
        }, executorService);
    }
    
    private void createTables(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(CREATE_TABLE_SQL);
        }
    }
    
    /**
     * Gets a connection to the SQLite database.
     * 
     * @return A database connection
     * @throws SQLException if connection fails
     */
    private @NotNull Connection getConnection() throws SQLException {
        String url = "jdbc:sqlite:" + databaseFile.getAbsolutePath();
        Connection connection = DriverManager.getConnection(url);
        connection.setAutoCommit(true);
        return connection;
    }
    
    /**
     * Saves player statistics to the database.
     * 
     * @param stats The player statistics to save
     * @return A CompletableFuture that completes when the save is done
     */
    public @NotNull CompletableFuture<Void> savePlayerStats(@NotNull PlayerStats stats) {
        return CompletableFuture.runAsync(() -> {
            try (Connection connection = getConnection();
                 PreparedStatement statement = connection.prepareStatement(UPSERT_STATS_SQL)) {
                
                statement.setString(1, stats.getPlayerId().toString());
                statement.setString(2, stats.getPlayerName());
                statement.setInt(3, stats.getWins());
                statement.setInt(4, stats.getLosses());
                statement.setInt(5, stats.getKills());
                statement.setInt(6, stats.getDeaths());
                statement.setInt(7, stats.getGamesPlayed());
                statement.setLong(8, stats.getTotalTimePlayed());
                statement.setInt(9, stats.getBestPlacement());
                statement.setInt(10, stats.getCurrentWinStreak());
                statement.setInt(11, stats.getBestWinStreak());
                statement.setInt(12, stats.getTop3Finishes());
                statement.setDouble(13, stats.getTotalDamageDealt());
                statement.setDouble(14, stats.getTotalDamageTaken());
                statement.setInt(15, stats.getChestsOpened());
                statement.setString(16, stats.getFirstJoined() != null ? stats.getFirstJoined().format(dateTimeFormatter) : null);
                statement.setString(17, stats.getLastPlayed() != null ? stats.getLastPlayed().format(dateTimeFormatter) : null);
                statement.setString(18, stats.getLastUpdated().format(dateTimeFormatter));
                
                statement.executeUpdate();
                
                logger.debug("Saved statistics for player: " + stats.getPlayerName());
            } catch (SQLException e) {
                logger.severe("Failed to save player statistics for " + stats.getPlayerName(), e);
                throw new RuntimeException("Failed to save player statistics", e);
            }
        }, executorService);
    }
    
    /**
     * Loads player statistics from the database.
     * 
     * @param playerId The player's unique identifier
     * @return A CompletableFuture containing the player statistics, or null if not found
     */
    public @NotNull CompletableFuture<@Nullable PlayerStats> loadPlayerStats(@NotNull UUID playerId) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = getConnection();
                 PreparedStatement statement = connection.prepareStatement(SELECT_STATS_SQL)) {
                
                statement.setString(1, playerId.toString());
                
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (resultSet.next()) {
                        return createPlayerStatsFromResultSet(resultSet);
                    }
                }
                
                return null; // Player not found
            } catch (SQLException e) {
                logger.severe("Failed to load player statistics for " + playerId, e);
                throw new RuntimeException("Failed to load player statistics", e);
            }
        }, executorService);
    }
    
    /**
     * Gets a leaderboard of top players by a specific statistic.
     * 
     * @param statType The type of statistic to sort by
     * @param limit The maximum number of players to return
     * @return A CompletableFuture containing the list of top players
     */
    public @NotNull CompletableFuture<@NotNull List<PlayerStats>> getLeaderboard(@NotNull StatType statType, int limit) {
        return CompletableFuture.supplyAsync(() -> {
            String columnName = getColumnNameForStatType(statType);
            String sql = String.format(SELECT_LEADERBOARD_SQL, columnName);
            
            List<PlayerStats> leaderboard = new ArrayList<>();
            
            try (Connection connection = getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                
                statement.setInt(1, limit);
                
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        PlayerStats stats = createPlayerStatsFromResultSet(resultSet);
                        if (stats != null) {
                            leaderboard.add(stats);
                        }
                    }
                }
            } catch (SQLException e) {
                logger.severe("Failed to load leaderboard for " + statType, e);
                throw new RuntimeException("Failed to load leaderboard", e);
            }
            
            return leaderboard;
        }, executorService);
    }
    
    /**
     * Gets the total number of players in the database.
     * 
     * @return A CompletableFuture containing the player count
     */
    public @NotNull CompletableFuture<Integer> getTotalPlayerCount() {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = getConnection();
                 Statement statement = connection.createStatement();
                 ResultSet resultSet = statement.executeQuery(COUNT_PLAYERS_SQL)) {
                
                if (resultSet.next()) {
                    return resultSet.getInt(1);
                }
                return 0;
            } catch (SQLException e) {
                logger.severe("Failed to get total player count", e);
                throw new RuntimeException("Failed to get total player count", e);
            }
        }, executorService);
    }
    
    /**
     * Creates a PlayerStats object from a database result set.
     * 
     * @param resultSet The result set from a database query
     * @return A PlayerStats object, or null if creation fails
     */
    private @Nullable PlayerStats createPlayerStatsFromResultSet(@NotNull ResultSet resultSet) {
        try {
            UUID playerId = UUID.fromString(resultSet.getString("player_id"));
            String playerName = resultSet.getString("player_name");
            int wins = resultSet.getInt("wins");
            int losses = resultSet.getInt("losses");
            int kills = resultSet.getInt("kills");
            int deaths = resultSet.getInt("deaths");
            int gamesPlayed = resultSet.getInt("games_played");
            long totalTimePlayed = resultSet.getLong("total_time_played");
            int bestPlacement = resultSet.getInt("best_placement");
            int currentWinStreak = resultSet.getInt("current_win_streak");
            int bestWinStreak = resultSet.getInt("best_win_streak");
            int top3Finishes = resultSet.getInt("top3_finishes");
            double totalDamageDealt = resultSet.getDouble("total_damage_dealt");
            double totalDamageTaken = resultSet.getDouble("total_damage_taken");
            int chestsOpened = resultSet.getInt("chests_opened");
            
            String firstJoinedStr = resultSet.getString("first_joined");
            LocalDateTime firstJoined = firstJoinedStr != null ? LocalDateTime.parse(firstJoinedStr, dateTimeFormatter) : null;
            
            String lastPlayedStr = resultSet.getString("last_played");
            LocalDateTime lastPlayed = lastPlayedStr != null ? LocalDateTime.parse(lastPlayedStr, dateTimeFormatter) : null;
            
            String lastUpdatedStr = resultSet.getString("last_updated");
            LocalDateTime lastUpdated = LocalDateTime.parse(lastUpdatedStr, dateTimeFormatter);
            
            return new PlayerStats(playerId, playerName, wins, losses, kills, deaths, gamesPlayed,
                    totalTimePlayed, bestPlacement, currentWinStreak, bestWinStreak, top3Finishes,
                    totalDamageDealt, totalDamageTaken, chestsOpened, firstJoined, lastPlayed, lastUpdated);
        } catch (Exception e) {
            logger.warn("Failed to create PlayerStats from result set", e);
            return null;
        }
    }
    
    /**
     * Gets the database column name for a statistic type.
     * 
     * @param statType The statistic type
     * @return The corresponding database column name
     */
    private @NotNull String getColumnNameForStatType(@NotNull StatType statType) {
        return switch (statType) {
            case WINS -> "wins";
            case KILLS -> "kills";
            case GAMES_PLAYED -> "games_played";
            case KILL_DEATH_RATIO -> "kills"; // We'll calculate KDR in application logic
            case WIN_RATE -> "wins"; // We'll calculate win rate in application logic
            case TIME_PLAYED -> "total_time_played";
            case BEST_PLACEMENT -> "best_placement";
            case WIN_STREAK -> "best_win_streak";
            case TOP3_FINISHES -> "top3_finishes";
            case DAMAGE_DEALT -> "total_damage_dealt";
            case CHESTS_OPENED -> "chests_opened";
        };
    }
    
    /**
     * Shuts down the database connection pool and cleans up resources.
     */
    public void shutdown() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
            logger.info("Statistics database shutdown completed");
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * Performs database maintenance operations like cleaning up old data or optimizing.
     * 
     * @return A CompletableFuture that completes when maintenance is done
     */
    public @NotNull CompletableFuture<Void> performMaintenance() {
        return CompletableFuture.runAsync(() -> {
            try (Connection connection = getConnection();
                 Statement statement = connection.createStatement()) {
                
                // Optimize the database
                statement.execute("VACUUM");
                statement.execute("ANALYZE");
                
                logger.info("Database maintenance completed");
            } catch (SQLException e) {
                logger.warn("Database maintenance failed", e);
            }
        }, executorService);
    }
} 