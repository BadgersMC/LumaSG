package net.lumalyte.lumasg.persistence

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import net.badgersmc.nexus.annotations.PostConstruct
import net.badgersmc.nexus.annotations.PreDestroy
import net.badgersmc.nexus.annotations.Service
import net.lumalyte.lumasg.config.LumaSGConfig
import net.lumalyte.lumasg.persistence.tables.ArenaTable
import net.lumalyte.lumasg.persistence.tables.GameHistoryTable
import net.lumalyte.lumasg.persistence.tables.PlayerStatsTable
import org.bukkit.plugin.java.JavaPlugin
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory

@Service
class DatabaseService(
    private val config: LumaSGConfig,
    private val plugin: JavaPlugin
) {

    private val logger = LoggerFactory.getLogger(DatabaseService::class.java)
    private lateinit var dataSource: HikariDataSource

    @PostConstruct
    fun init() {
        val db = config.database
        val hikariConfig = HikariConfig().apply {
            poolName = "LumaSG-DB"
            maximumPoolSize = db.pool.maximumPoolSize
            minimumIdle = db.pool.minimumIdle
            connectionTimeout = db.pool.connectionTimeout
            idleTimeout = db.pool.idleTimeout
            maxLifetime = db.pool.maxLifetime

            when (db.type.uppercase()) {
                "SQLITE" -> {
                    val dbFile = plugin.dataFolder.resolve(db.sqliteFile)
                    jdbcUrl = "jdbc:sqlite:${dbFile.absolutePath}"
                    driverClassName = "org.sqlite.JDBC"
                    maximumPoolSize = 1 // SQLite only supports one writer
                }
                "MYSQL", "MARIADB" -> {
                    val sslParam = if (!db.useSsl) "&useSSL=false" else ""
                    jdbcUrl = "jdbc:mariadb://${db.host}:${db.port}/${db.database}?$sslParam"
                    username = db.username
                    password = db.password
                    driverClassName = "org.mariadb.jdbc.Driver"
                }
                "POSTGRESQL" -> {
                    val sslParam = if (!db.useSsl) "&sslmode=disable" else ""
                    jdbcUrl = "jdbc:postgresql://${db.host}:${db.port}/${db.database}?$sslParam"
                    username = db.username
                    password = db.password
                    driverClassName = "org.postgresql.Driver"
                }
                else -> error("Unsupported database type: ${db.type}")
            }
        }
        dataSource = HikariDataSource(hikariConfig)
        Database.connect(dataSource)

        transaction {
            SchemaUtils.createMissingTablesAndColumns(
                PlayerStatsTable,
                ArenaTable,
                GameHistoryTable
            )
        }
        logger.info("Database connected (${db.type}) and schema verified.")
    }

    @PreDestroy
    fun shutdown() {
        if (::dataSource.isInitialized) {
            dataSource.close()
            logger.info("Database connection pool closed.")
        }
    }
}
