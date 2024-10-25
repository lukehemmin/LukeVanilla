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
}