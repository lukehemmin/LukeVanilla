package com.lukehemmin.lukeVanilla.System.Database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.bukkit.configuration.file.FileConfiguration
import java.sql.Connection
import java.util.*

class Database(config: FileConfiguration) {
    private val hikariConfig: HikariConfig = HikariConfig()
    private val dataSource: HikariDataSource

    // 데이터 클래스 추가
    data class AuthRecord(val uuid: String, val isAuth: Boolean)
    data class PlayerData(val nickname: String, val uuid: String, val discordId: String?) // discordId 추가

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
        val query = "SELECT nickname, uuid, DiscordID FROM Player_Data WHERE uuid = ?"
        getConnection().use { connection ->
            connection.prepareStatement(query).use { statement ->
                statement.setString(1, uuid)
                val result = statement.executeQuery()
                if (result.next()) {
                    return PlayerData(
                        nickname = result.getString("nickname"),
                        uuid = result.getString("uuid"),
                        discordId = result.getString("DiscordID") // DiscordID 포함
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

    fun getPlayerDataByDiscordId(discordId: String): PlayerData? {
        val query = "SELECT nickname, uuid FROM Player_Data WHERE DiscordID = ?"
        getConnection().use { connection ->
            connection.prepareStatement(query).use { statement ->
                statement.setString(1, discordId)
                val result = statement.executeQuery()
                if (result.next()) {
                    return PlayerData(
                        nickname = result.getString("nickname"),
                        uuid = result.getString("uuid"),
                        discordId = discordId // DiscordID 포함
                    )
                }
            }
        }
        return null
    }

    // DiscordVoiceChannelListener 에서 사용할 함수 추가
    fun getPlayerUUIDByDiscordID(discordId: String): String? {
        val query = "SELECT UUID FROM Player_Data WHERE DiscordID = ?"
        getConnection().use { connection ->
            connection.prepareStatement(query).use { statement ->
                statement.setString(1, discordId)
                val result = statement.executeQuery()
                if (result.next()) {
                    return result.getString("UUID")
                }
            }
        }
        return null
    }

    /**
     * Player_NameTag 테이블에서 UUID로 태그를 조회합니다.
     */
    fun getPlayerNameTag(uuid: String): String? {
        val query = "SELECT Tag FROM Player_NameTag WHERE UUID = ?"
        getConnection().use { connection ->
            connection.prepareStatement(query).use { statement ->
                statement.setString(1, uuid)
                val result = statement.executeQuery()
                return if (result.next()) result.getString("Tag") else null
            }
        }
    }

    fun setTitokerMessageEnabled(uuid: String, enabled: Boolean) {
        getConnection().use { connection ->
            connection.prepareStatement(
                """
            INSERT INTO Titoker_Message_Setting (UUID, IsEnabled) 
            VALUES (?, ?) 
            ON DUPLICATE KEY UPDATE IsEnabled = ?
            """
            ).use { statement ->
                statement.setString(1, uuid)
                statement.setBoolean(2, enabled)
                statement.setBoolean(3, enabled)
                statement.executeUpdate()
            }
        }
    }

    fun isTitokerMessageEnabled(uuid: String): Boolean {
        getConnection().use { connection ->
            connection.prepareStatement(
                "SELECT IsEnabled FROM Titoker_Message_Setting WHERE UUID = ?"
            ).use { statement ->
                statement.setString(1, uuid)
                val result = statement.executeQuery()
                return if (result.next()) result.getBoolean("IsEnabled") else false
            }
        }
    }

    fun getDiscordIDByUUID(uuid: String): String? {
        getConnection().use { connection ->
            connection.prepareStatement(
                "SELECT DiscordID FROM Player_Data WHERE UUID = ?"
            ).use { statement ->
                statement.setString(1, uuid)
                val result = statement.executeQuery()
                return if (result.next()) result.getString("DiscordID") else null
            }
        }
    }

    fun setSetting(settingType: String, settingValue: String) {
        val query = """
        INSERT INTO Settings (setting_type, setting_value) 
        VALUES (?, ?) 
        ON DUPLICATE KEY UPDATE setting_value = ?
        """
        getConnection().use { connection ->
            connection.prepareStatement(query).use { statement ->
                statement.setString(1, settingType)
                statement.setString(2, settingValue)
                statement.setString(3, settingValue)
                statement.executeUpdate()
            }
        }
    }

    data class SupportCase(
        val supportId: String,
        val messageLink: String
    )

    fun getOpenSupportCases(uuid: String): List<SupportCase> {
        val openCases = mutableListOf<SupportCase>()

        getConnection().use { connection ->
            val statement = connection.prepareStatement(
                "SELECT SupportID, MessageLink FROM SupportChatLink WHERE UUID = ? AND CaseClose = 0"
            )
            statement.setString(1, uuid)
            val resultSet = statement.executeQuery()
            while (resultSet.next()) {
                val supportId = resultSet.getString("SupportID")
                val messageLink = resultSet.getString("MessageLink")
                openCases.add(SupportCase(supportId, messageLink))
            }
            resultSet.close()
            statement.close()
        }

        return openCases
    }
    
    /**
     * 데이터베이스를 닫는 메서드
     */
    fun close() {
        if (!dataSource.isClosed) {
            dataSource.close()
        }
    }

    fun isDataSourceClosed(): Boolean {
        return dataSource.isClosed
    }
}