package net.lumalyte.lumasg.util.database;

import org.jetbrains.annotations.NotNull;

/**
 * Database configuration container for connection settings.
 * Supports both PostgreSQL and MySQL with sensible defaults for production use.
 */
public class DatabaseConfig {
    
    public enum DatabaseType {
        POSTGRESQL("postgresql", "org.postgresql.Driver", 5432),
        MYSQL("mysql", "com.mysql.cj.jdbc.Driver", 3306),
        SQLITE("sqlite", "org.sqlite.JDBC", 0); // Fallback only
        
        private final String protocol;
        private final String driverClass;
        private final int defaultPort;
        
        DatabaseType(String protocol, String driverClass, int defaultPort) {
            this.protocol = protocol;
            this.driverClass = driverClass;
            this.defaultPort = defaultPort;
        }
        
        public String getProtocol() { return protocol; }
        public String getDriverClass() { return driverClass; }
        public int getDefaultPort() { return defaultPort; }
    }
    
    private final DatabaseType type;
    private final String host;
    private final int port;
    private final String database;
    private final String username;
    private final String password;
    
    // Connection pool settings
    private final int minimumIdle;
    private final int maximumPoolSize;
    private final long connectionTimeout;
    private final long idleTimeout;
    private final long maxLifetime;
    
    // Additional settings
    private final boolean useSSL;
    private final String additionalProperties;
    
    private DatabaseConfig(Builder builder) {
        this.type = builder.type;
        this.host = builder.host;
        this.port = builder.port;
        this.database = builder.database;
        this.username = builder.username;
        this.password = builder.password;
        this.minimumIdle = builder.minimumIdle;
        this.maximumPoolSize = builder.maximumPoolSize;
        this.connectionTimeout = builder.connectionTimeout;
        this.idleTimeout = builder.idleTimeout;
        this.maxLifetime = builder.maxLifetime;
        this.useSSL = builder.useSSL;
        this.additionalProperties = builder.additionalProperties;
    }
    
    /**
     * Builds the JDBC URL for this configuration
     */
    @NotNull
    public String buildJdbcUrl() {
        StringBuilder url = new StringBuilder();
        url.append("jdbc:").append(type.getProtocol()).append("://");
        url.append(host).append(":").append(port);
        url.append("/").append(database);
        
        // Add SSL and additional properties
        StringBuilder params = new StringBuilder();
        
        if (type == DatabaseType.POSTGRESQL) {
            params.append("?sslmode=").append(useSSL ? "require" : "disable");
            params.append("&prepareThreshold=0"); // Disable prepared statement caching for better performance
        } else if (type == DatabaseType.MYSQL) {
            params.append("?useSSL=").append(useSSL);
            params.append("&serverTimezone=UTC");
            params.append("&cachePrepStmts=true");
            params.append("&useServerPrepStmts=true");
            params.append("&rewriteBatchedStatements=true");
        }
        
        if (additionalProperties != null && !additionalProperties.isEmpty()) {
            if (params.length() == 0) {
                params.append("?");
            } else {
                params.append("&");
            }
            params.append(additionalProperties);
        }
        
        url.append(params);
        return url.toString();
    }
    
    // Getters
    public DatabaseType getType() { return type; }
    public String getHost() { return host; }
    public int getPort() { return port; }
    public String getDatabase() { return database; }
    public String getUsername() { return username; }
    public String getPassword() { return password; }
    public int getMinimumIdle() { return minimumIdle; }
    public int getMaximumPoolSize() { return maximumPoolSize; }
    public long getConnectionTimeout() { return connectionTimeout; }
    public long getIdleTimeout() { return idleTimeout; }
    public long getMaxLifetime() { return maxLifetime; }
    public boolean isUseSSL() { return useSSL; }
    public String getAdditionalProperties() { return additionalProperties; }
    
    /**
     * Builder for DatabaseConfig with sensible defaults
     */
    public static class Builder {
        private DatabaseType type = DatabaseType.POSTGRESQL;
        private String host = "localhost";
        private int port = 5432;
        private String database = "lumasg";
        private String username = "lumasg";
        private String password = ""; // Default empty - should be configured in config.yml
        
        // HikariCP optimal defaults for game servers
        private int minimumIdle = 2;
        private int maximumPoolSize = 8;
        private long connectionTimeout = 30000; // 30 seconds
        private long idleTimeout = 600000; // 10 minutes
        private long maxLifetime = 1800000; // 30 minutes
        
        private boolean useSSL = false;
        private String additionalProperties = "";
        
        public Builder type(DatabaseType type) {
            this.type = type;
            if (port == 5432 && type == DatabaseType.MYSQL) {
                this.port = 3306; // Auto-adjust port
            } else if (port == 3306 && type == DatabaseType.POSTGRESQL) {
                this.port = 5432; // Auto-adjust port
            }
            return this;
        }
        
        public Builder host(String host) {
            this.host = host;
            return this;
        }
        
        public Builder port(int port) {
            this.port = port;
            return this;
        }
        
        public Builder database(String database) {
            this.database = database;
            return this;
        }
        
        public Builder username(String username) {
            this.username = username;
            return this;
        }
        
        public Builder password(String password) {
            this.password = password;
            return this;
        }
        
        public Builder minimumIdle(int minimumIdle) {
            this.minimumIdle = minimumIdle;
            return this;
        }
        
        public Builder maximumPoolSize(int maximumPoolSize) {
            this.maximumPoolSize = maximumPoolSize;
            return this;
        }
        
        public Builder connectionTimeout(long connectionTimeout) {
            this.connectionTimeout = connectionTimeout;
            return this;
        }
        
        public Builder idleTimeout(long idleTimeout) {
            this.idleTimeout = idleTimeout;
            return this;
        }
        
        public Builder maxLifetime(long maxLifetime) {
            this.maxLifetime = maxLifetime;
            return this;
        }
        
        public Builder useSSL(boolean useSSL) {
            this.useSSL = useSSL;
            return this;
        }
        
        public Builder additionalProperties(String additionalProperties) {
            this.additionalProperties = additionalProperties;
            return this;
        }
        
        public DatabaseConfig build() {
            return new DatabaseConfig(this);
        }
    }
}
