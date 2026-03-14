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
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory

@Service
class DatabaseService(private val config: LumaSGConfig) {

    private val logger = LoggerFactory.getLogger(DatabaseService::class.java)
    private lateinit var dataSource: HikariDataSource

    @PostConstruct
    fun init() {
        val db = config.database
        val hikariConfig = HikariConfig().apply {
            jdbcUrl = "jdbc:mariadb://${db.host}:${db.port}/${db.database}"
            username = db.username
            password = db.password
            maximumPoolSize = db.poolSize
            driverClassName = "org.mariadb.jdbc.Driver"
            poolName = "LumaSG-DB"
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
        logger.info("Database connected and schema verified.")
    }

    @PreDestroy
    fun shutdown() {
        if (::dataSource.isInitialized) {
            dataSource.close()
            logger.info("Database connection pool closed.")
        }
    }
}
