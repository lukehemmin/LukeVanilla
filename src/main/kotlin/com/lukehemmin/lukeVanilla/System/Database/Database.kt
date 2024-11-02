package com.lukehemmin.lukeVanilla.System.Database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.bukkit.configuration.file.FileConfiguration
import java.sql.Connection

class Database(config: FileConfiguration) {
    private val hikariConfig: HikariConfig = HikariConfig()
    private val dataSource: HikariDataSource

    init {
        val host = config.getString("database.host")
        val port = config.getInt("database.port")
        val dbName = config.getString("database.name")
        val user = config.getString("database.user")
        val password = config.getString("database.password")

        hikariConfig.jdbcUrl = "jdbc:mysql://$host:$port/$dbName"
        hikariConfig.username = user
        hikariConfig.password = password
        hikariConfig.addDataSourceProperty("cachePrepStmts", "true")
        hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250")
        hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048")

        dataSource = HikariDataSource(hikariConfig)
    }

    fun getConnection(): Connection = dataSource.connection

    fun getJoinQuitMessage(serviceType: String, messageType: String): String? {
        this.getConnection().use { connection ->
            val statement = connection.prepareStatement(
                "SELECT message FROM Join_Quit_Message WHERE service_type = ? AND message_type = ?"
            )
            statement.setString(1, serviceType)
            statement.setString(2, messageType)
            val resultSet = statement.executeQuery()
            return if (resultSet.next()) {
                resultSet.getString("message")
            } else {
                null
            }
        }
    }
}