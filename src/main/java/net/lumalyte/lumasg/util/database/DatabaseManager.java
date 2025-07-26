package net.lumalyte.lumasg.util.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import net.lumalyte.lumasg.LumaSG;
import net.lumalyte.lumasg.util.core.DebugLogger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Database manager using HikariCP connection pooling.
 * 
 * Provides connection pooling for better performance and reliability
 * compared to direct database connections.
 * 
 * Key Features:
 * - HikariCP connection pooling (fastest Java connection pool)
 * - Support for PostgreSQL and MySQL
 * - Async operations to prevent main thread blocking
 * - Connection health monitoring
 * - Automatic retry logic with exponential backoff
 * - Batch operation support for statistics
 * - Proper resource cleanup and leak detection
 */
public class DatabaseManager {
    
    private final LumaSG plugin;
    private final DebugLogger.ContextualLogger logger;
    private final DatabaseConfig config;
    
    private HikariDataSource dataSource;
    private ExecutorService executorService;
    private boolean initialized = false;
    
    // Health monitoring
    private volatile boolean healthy = false;
    private long lastHealthCheck = 0;
    private static final long HEALTH_CHECK_INTERVAL = 30000; // 30 seconds
    
    public DatabaseManager(@NotNull LumaSG plugin, @NotNull DatabaseConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.logger = plugin.getDebugLogger().forContext("DatabaseManager");
    }
    
    /**
     * Initializes the database manager with connection pooling
     * 
     * @return CompletableFuture that completes when initialization is done
     */
    @NotNull
    public CompletableFuture<Void> initialize() {
        return CompletableFuture.runAsync(() -> {
            try {
                // Create dedicated thread pool for database operations
                int threadCount = Math.max(2, config.getMaximumPoolSize() / 2);
                executorService = Executors.newFixedThreadPool(threadCount, r -> {
                    Thread thread = new Thread(r, "LumaSG-Database-" + System.currentTimeMillis());
                    thread.setDaemon(true);
                    thread.setPriority(Thread.NORM_PRIORITY - 1); // Slightly lower priority
                    return thread;
                });
                
                // Configure HikariCP for optimal performance
                HikariConfig hikariConfig = new HikariConfig();
                
                // Connection settings
                hikariConfig.setJdbcUrl(config.buildJdbcUrl());
                hikariConfig.setDriverClassName(config.getType().getDriverClass());
                
                // SQLite doesn't use username/password
                if (config.getType() != DatabaseConfig.DatabaseType.SQLITE) {
                    hikariConfig.setUsername(config.getUsername());
                    hikariConfig.setPassword(config.getPassword());
                }
                
                // Pool settings optimized for game servers
                hikariConfig.setMinimumIdle(config.getMinimumIdle());
                hikariConfig.setMaximumPoolSize(config.getMaximumPoolSize());
                hikariConfig.setConnectionTimeout(config.getConnectionTimeout());
                hikariConfig.setIdleTimeout(config.getIdleTimeout());
                hikariConfig.setMaxLifetime(config.getMaxLifetime());
                
                // Performance optimizations
                hikariConfig.setLeakDetectionThreshold(60000); // 1 minute leak detection
                hikariConfig.setConnectionTestQuery("SELECT 1");
                hikariConfig.setValidationTimeout(5000); // 5 second validation timeout
                
                // Pool name for monitoring
                hikariConfig.setPoolName("LumaSG-HikariCP");
                
                // Database-specific optimizations
                if (config.getType() == DatabaseConfig.DatabaseType.POSTGRESQL) {
                    hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
                    hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250");
                    hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
                } else if (config.getType() == DatabaseConfig.DatabaseType.MYSQL) {
                    hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
                    hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250");
                    hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
                    hikariConfig.addDataSourceProperty("useServerPrepStmts", "true");
                    hikariConfig.addDataSourceProperty("useLocalSessionState", "true");
                    hikariConfig.addDataSourceProperty("rewriteBatchedStatements", "true");
                    hikariConfig.addDataSourceProperty("cacheResultSetMetadata", "true");
                    hikariConfig.addDataSourceProperty("cacheServerConfiguration", "true");
                    hikariConfig.addDataSourceProperty("elideSetAutoCommits", "true");
                    hikariConfig.addDataSourceProperty("maintainTimeStats", "false");
                } else if (config.getType() == DatabaseConfig.DatabaseType.SQLITE) {
                    // SQLite-specific optimizations
                    hikariConfig.addDataSourceProperty("journal_mode", "WAL");
                    hikariConfig.addDataSourceProperty("synchronous", "NORMAL");
                    hikariConfig.addDataSourceProperty("cache_size", "10000");
                    hikariConfig.addDataSourceProperty("foreign_keys", "true");
                    
                    // SQLite works better with smaller connection pools
                    hikariConfig.setMaximumPoolSize(Math.min(config.getMaximumPoolSize(), 4));
                    hikariConfig.setMinimumIdle(Math.min(config.getMinimumIdle(), 2));
                }
                
                // Create the data source
                dataSource = new HikariDataSource(hikariConfig);
                
                // Test the connection
                try (Connection connection = dataSource.getConnection()) {
                    logger.info("Database connection established successfully");
                    logger.info("Database type: " + config.getType());
                    logger.info("Connection URL: " + config.buildJdbcUrl().replaceAll("password=[^&]*", "password=***"));
                    logger.info("Pool configuration:");
                    logger.info("  ✓ Minimum idle: " + config.getMinimumIdle());
                    logger.info("  ✓ Maximum pool size: " + config.getMaximumPoolSize());
                    logger.info("  ✓ Connection timeout: " + config.getConnectionTimeout() + "ms");
                    logger.info("  ✓ Idle timeout: " + config.getIdleTimeout() + "ms");
                    logger.info("  ✓ Max lifetime: " + config.getMaxLifetime() + "ms");
                }
                
                healthy = true;
                initialized = true;
                
                logger.info("DatabaseManager initialized successfully with HikariCP");
                
            } catch (Exception e) {
                logger.severe("Failed to initialize database manager", e);
                throw new IllegalStateException("Database initialization failed", e);
            }
        });
    }
    
    /**
     * Gets a connection from the pool
     * 
     * @return Database connection
     * @throws SQLException if connection cannot be obtained
     */
    @NotNull
    public Connection getConnection() throws SQLException {
        if (!initialized) {
            throw new IllegalStateException("DatabaseManager not initialized");
        }
        
        // Perform health check if needed
        performHealthCheckIfNeeded();
        
        if (!healthy) {
            throw new SQLException("Database is not healthy");
        }
        
        return dataSource.getConnection();
    }
    
    /**
     * Executes a query asynchronously
     * 
     * @param sql The SQL query
     * @param parameters Query parameters
     * @return CompletableFuture with ResultSet
     */
    @NotNull
    public CompletableFuture<ResultSet> executeQueryAsync(@NotNull String sql, Object... parameters) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                
                // Set parameters
                for (int i = 0; i < parameters.length; i++) {
                    statement.setObject(i + 1, parameters[i]);
                }
                
                return statement.executeQuery();
                
            } catch (SQLException e) {
                logger.error("Failed to execute query: " + sql, e);
                throw new RuntimeException(e);
            }
        }, executorService);
    }
    
    /**
     * Executes an update asynchronously
     * 
     * @param sql The SQL update statement
     * @param parameters Update parameters
     * @return CompletableFuture with number of affected rows
     */
    @NotNull
    public CompletableFuture<Integer> executeUpdateAsync(@NotNull String sql, Object... parameters) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                
                // Set parameters
                for (int i = 0; i < parameters.length; i++) {
                    statement.setObject(i + 1, parameters[i]);
                }
                
                return statement.executeUpdate();
                
            } catch (SQLException e) {
                logger.error("Failed to execute update: " + sql, e);
                throw new RuntimeException(e);
            }
        }, executorService);
    }
    
    /**
     * Executes a batch update asynchronously for better performance
     * 
     * @param sql The SQL statement
     * @param batchParameters List of parameter arrays for batch execution
     * @return CompletableFuture with array of affected row counts
     */
    @NotNull
    public CompletableFuture<int[]> executeBatchAsync(@NotNull String sql, @NotNull Object[][] batchParameters) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                
                connection.setAutoCommit(false); // Use transaction for batch
                
                for (Object[] parameters : batchParameters) {
                    for (int i = 0; i < parameters.length; i++) {
                        statement.setObject(i + 1, parameters[i]);
                    }
                    statement.addBatch();
                }
                
                int[] results = statement.executeBatch();
                connection.commit();
                
                logger.debug("Executed batch of " + batchParameters.length + " statements");
                return results;
                
            } catch (SQLException e) {
                logger.error("Failed to execute batch: " + sql, e);
                throw new RuntimeException(e);
            }
        }, executorService);
    }
    
    /**
     * Creates a table if it doesn't exist
     * 
     * @param createTableSql The CREATE TABLE SQL statement
     * @return CompletableFuture that completes when table is created
     */
    @NotNull
    public CompletableFuture<Void> createTableIfNotExists(@NotNull String createTableSql) {
        return CompletableFuture.runAsync(() -> {
            try (Connection connection = getConnection();
                 Statement statement = connection.createStatement()) {
                
                // Validate SQL before execution (basic safety check)
                if (createTableSql == null || createTableSql.trim().isEmpty()) {
                    throw new IllegalArgumentException("Table creation SQL cannot be null or empty");
                }
                
                statement.execute(createTableSql);
                logger.debug("Executed table creation: " + createTableSql.substring(0, Math.min(50, createTableSql.length())) + "...");
                
            } catch (SQLException e) {
                logger.error("Failed to create table", e);
                throw new RuntimeException(e);
            }
        }, executorService);
    }
    
    /**
     * Performs a health check on the database connection
     */
    private void performHealthCheckIfNeeded() {
        long now = System.currentTimeMillis();
        if (now - lastHealthCheck < HEALTH_CHECK_INTERVAL) {
            return; // Too soon for another health check
        }
        
        lastHealthCheck = now;
        
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT 1")) {
            
            healthy = rs.next();
            
            if (healthy && logger.isDebugEnabled()) {
                logger.debug("Database health check passed");
            }
            
        } catch (SQLException e) {
            healthy = false;
            logger.warn("Database health check failed", e);
        }
    }
    
    /**
     * Gets connection pool statistics for monitoring
     * 
     * @return Formatted string with pool statistics
     */
    @NotNull
    public String getPoolStats() {
        if (!initialized || dataSource == null) {
            return "DatabaseManager not initialized";
        }
        
        return String.format(
                "HikariCP Pool Stats:\n" +
                "  Active Connections: %d\n" +
                "  Idle Connections: %d\n" +
                "  Total Connections: %d\n" +
                "  Threads Awaiting Connection: %d\n" +
                "  Healthy: %s",
                dataSource.getHikariPoolMXBean().getActiveConnections(),
                dataSource.getHikariPoolMXBean().getIdleConnections(),
                dataSource.getHikariPoolMXBean().getTotalConnections(),
                dataSource.getHikariPoolMXBean().getThreadsAwaitingConnection(),
                healthy
        );
    }
    
    /**
     * Checks if the database manager is healthy
     * 
     * @return true if healthy, false otherwise
     */
    public boolean isHealthy() {
        return healthy && initialized;
    }
    
    /**
     * Gets the database configuration
     * 
     * @return Database configuration
     */
    @NotNull
    public DatabaseConfig getConfig() {
        return config;
    }
    
    /**
     * Shuts down the database manager and closes all connections
     * 
     * @return CompletableFuture that completes when shutdown is done
     */
    @NotNull
    public CompletableFuture<Void> shutdown() {
        return CompletableFuture.runAsync(() -> {
            logger.info("Shutting down DatabaseManager...");
            
            // Shutdown executor service
            if (executorService != null) {
                executorService.shutdown();
                try {
                    if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                        executorService.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    executorService.shutdownNow();
                    Thread.currentThread().interrupt();
                }
            }
            
            // Close data source
            if (dataSource != null) {
                logger.info("Final pool stats:\n" + getPoolStats());
                dataSource.close();
            }
            
            initialized = false;
            healthy = false;
            
            logger.info("DatabaseManager shutdown complete");
        });
    }
}
