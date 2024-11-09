package com.lukehemmin.lukeVanilla.System.Database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.bukkit.configuration.file.FileConfiguration
import java.sql.Connection

class Database(config: FileConfiguration) {
    private val hikariConfig: HikariConfig = HikariConfig()
    private val dataSource: HikariDataSource

    // 데이터 클래스 추가
    data class AuthRecord(val uuid: String, val isAuth: Boolean)
    data class PlayerData(val nickname: String, val uuid: String)

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

    fun getSettingValue(settingType: String): String? {
        val query = "SELECT setting_value FROM Settings WHERE setting_type = ?"
        getConnection().use { connection ->
            connection.prepareStatement(query).use { statement ->
                statement.setString(1, settingType)
                val result = statement.executeQuery()
                return if (result.next()) result.getString("setting_value") else null
            }
        }
    }

    fun getAuthRecord(authCode: String): AuthRecord? {
        val query = "SELECT UUID, IsAuth FROM Player_Auth WHERE AuthCode = ?"
        getConnection().use { connection ->
            connection.prepareStatement(query).use { statement ->
                statement.setString(1, authCode)
                val result = statement.executeQuery()
                if (result.next()) {
                    return AuthRecord(
                        uuid = result.getString("UUID"),
                        isAuth = result.getBoolean("IsAuth")
                    )
                }
            }
        }
        return null
    }

    fun updateAuthStatus(authCode: String, isAuth: Boolean) {
        val query = "UPDATE Player_Auth SET IsAuth = ? WHERE AuthCode = ?"
        getConnection().use { connection ->
            connection.prepareStatement(query).use { statement ->
                statement.setBoolean(1, isAuth)
                statement.setString(2, authCode)
                statement.executeUpdate()
            }
        }
    }

    fun getPlayerDataByUuid(uuid: String): PlayerData? {
        val query = "SELECT nickname, uuid FROM Player_Data WHERE uuid = ?"
        getConnection().use { connection ->
            connection.prepareStatement(query).use { statement ->
                statement.setString(1, uuid)
                val result = statement.executeQuery()
                if (result.next()) {
                    return PlayerData(
                        nickname = result.getString("nickname"),
                        uuid = result.getString("uuid")
                    )
                }
            }
        }
        return null
    }

    fun updateDiscordId(uuid: String, discordId: String) {
        val query = "UPDATE Player_Data SET DiscordID = ? WHERE uuid = ?"
        getConnection().use { connection ->
            connection.prepareStatement(query).use { statement ->
                statement.setString(1, discordId)
                statement.setString(2, uuid)
                statement.executeUpdate()
            }
        }
    }

    fun ensurePlayerAuth(uuid: String): String {
        getConnection().use { connection ->
            // Player_Auth 테이블에서 UUID로 행을 찾음
            connection.prepareStatement("SELECT AuthCode FROM Player_Auth WHERE UUID = ?").use { checkAuth ->
                checkAuth.setString(1, uuid)
                checkAuth.executeQuery().use { authResult ->
                    if (authResult.next()) {
                        // 기존 AuthCode 반환
                        return authResult.getString("AuthCode")
                    } else {
                        // 행이 없으면 새로운 행 생성
                        val authCode = generateAuthCode()
                        connection.prepareStatement("INSERT INTO Player_Auth (UUID, IsAuth, AuthCode) VALUES (?, ?, ?)").use { insertAuth ->
                            insertAuth.setString(1, uuid)
                            insertAuth.setInt(2, 0)
                            insertAuth.setString(3, authCode)
                            insertAuth.executeUpdate()
                        }
                        return authCode
                    }
                }
            }
        }
    }

    private fun generateAuthCode(): String {
        val chars = ('A'..'Z') + ('0'..'9')
        return (1..6)
            .map { chars.random() }
            .joinToString("")
    }
}