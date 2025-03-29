// Database.kt
package com.lukehemmin.lukeVanilla.System.Database

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.lukehemmin.lukeVanilla.Main
import com.lukehemmin.lukeVanilla.System.LockSystem.LockID
import com.lukehemmin.lukeVanilla.System.LockSystem.LockPermissions
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.bukkit.Bukkit
import org.bukkit.configuration.file.FileConfiguration
import java.sql.Connection
import java.util.*

class Database(private val plugin: Main, config: FileConfiguration) {
    private val hikariConfig: HikariConfig = HikariConfig()
    private val dataSource: HikariDataSource
    private val gson = Gson()

    // 데이터 클래스 추가
    data class AuthRecord(val uuid: String, val isAuth: Boolean)
    data class PlayerData(val nickname: String, val uuid: String, val discordId: String?) // discordId 추가
    
    private val useApi: Boolean
    
    init {
        useApi = plugin.config.getBoolean("api.enabled", false)
        val host = config.getString("database.host") ?: throw IllegalArgumentException("Database host not specified in config.")
        val port = config.getInt("database.port")
        val dbName = config.getString("database.name") ?: throw IllegalArgumentException("Database name not specified in config.")
        val user = config.getString("database.user") ?: throw IllegalArgumentException("Database user not specified in config.")
        val password = config.getString("database.password") ?: throw IllegalArgumentException("Database password not specified in config.")

        hikariConfig.jdbcUrl = "jdbc:mysql://$host:$port/$dbName?useSSL=false&serverTimezone=UTC"
        hikariConfig.username = user
        hikariConfig.password = password
        hikariConfig.addDataSourceProperty("cachePrepStmts", "true")
        hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250")
        hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048")
        hikariConfig.maximumPoolSize = 10
        hikariConfig.minimumIdle = 5
        hikariConfig.idleTimeout = 30000
        hikariConfig.maxLifetime = 1800000

        dataSource = HikariDataSource(hikariConfig)
    }

    fun getConnection(): Connection = dataSource.connection

    fun getJoinQuitMessage(serviceType: String, messageType: String): String? {
        val query = "SELECT message FROM Join_Quit_Message WHERE service_type = ? AND message_type = ?"
        getConnection().use { connection ->
            connection.prepareStatement(query).use { statement ->
                statement.setString(1, serviceType)
                statement.setString(2, messageType)
                statement.executeQuery().use { resultSet ->
                    return if (resultSet.next()) {
                        resultSet.getString("message")
                    } else {
                        null
                    }
                }
            }
        }
    }

    fun getSettingValue(settingType: String): String? {
        if (useApi && ::plugin.isInitialized && plugin::apiClient.isInitialized) {
            try {
                val response = plugin.apiClient.getSettingValue(settingType)
                if (response != null && response.has("value")) {
                    return response.get("value").asString
                }
            } catch (e: Exception) {
                plugin.logger.warning("API를 통한 설정 값 조회 실패, DB로 폴백: ${e.message}")
            }
        }
        
        // 기존 DB 접근 코드
        val query = "SELECT setting_value FROM Settings WHERE setting_type = ?"
        getConnection().use { connection ->
            connection.prepareStatement(query).use { statement ->
                statement.setString(1, settingType)
                statement.executeQuery().use { resultSet ->
                    return if (resultSet.next()) {
                        resultSet.getString("setting_value")
                    } else {
                        null
                    }
                }
            }
        }
    }

    fun getAuthRecord(authCode: String): AuthRecord? {
        // API 사용이 가능하고 API 클라이언트가 초기화되었는지 확인
        if (useApi && ::plugin.isInitialized && plugin::apiClient.isInitialized) {
            try {
                // API를 통해 인증 상태 확인을 시도
                val result = plugin.apiClient.getAuthRecord(authCode)
                if (result != null) {
                    return result
                }
                // API 호출 실패 시 DB로 폴백
            } catch (e: Exception) {
                plugin.logger.warning("API 호출 실패, DB로 폴백: ${e.message}")
                // DB로 폴백
            }
        }
        
        // 기존 DB 접근 코드
        val query = "SELECT UUID, IsAuth FROM Player_Auth WHERE AuthCode = ?"
        getConnection().use { connection ->
            connection.prepareStatement(query).use { statement ->
                statement.setString(1, authCode)
                statement.executeQuery().use { resultSet ->
                    return if (resultSet.next()) {
                        AuthRecord(
                            uuid = resultSet.getString("UUID"),
                            isAuth = resultSet.getBoolean("IsAuth")
                        )
                    } else {
                        null
                    }
                }
            }
        }
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
        // API 사용이 가능하고 API 클라이언트가 초기화되었는지 확인
        if (useApi && ::plugin.isInitialized && plugin::apiClient.isInitialized) {
            try {
                // API를 통해 플레이어 데이터 조회
                val playerDataDto = plugin.apiClient.getPlayerByUuid(uuid)
                if (playerDataDto != null) {
                    return PlayerData(
                        nickname = playerDataDto.get("nickname").asString,
                        uuid = playerDataDto.get("uuid").asString,
                        discordId = if (playerDataDto.has("discordId")) playerDataDto.get("discordId").asString else null
                    )
                }
                // API 호출 실패 시 DB로 폴백
            } catch (e: Exception) {
                plugin.logger.warning("API 호출 실패, DB로 폴백: ${e.message}")
                // DB로 폴백
            }
        }
        
        // 기존 DB 접근 코드
        val query = "SELECT nickname, uuid, DiscordID FROM Player_Data WHERE uuid = ?"
        getConnection().use { connection ->
            connection.prepareStatement(query).use { statement ->
                statement.setString(1, uuid)
                statement.executeQuery().use { resultSet ->
                    return if (resultSet.next()) {
                        PlayerData(
                            nickname = resultSet.getString("nickname"),
                            uuid = resultSet.getString("uuid"),
                            discordId = resultSet.getString("DiscordID")
                        )
                    } else {
                        null
                    }
                }
            }
        }
    }

    fun updateDiscordId(uuid: String, discordId: String) {
        if (useApi && ::plugin.isInitialized && plugin::apiClient.isInitialized) {
            try {
                plugin.apiClient.updatePlayerDiscordId(uuid, discordId)
                return
            } catch (e: Exception) {
                plugin.logger.warning("API를 통한 디스코드 ID 업데이트 실패, DB로 폴백: ${e.message}")
            }
        }
        
        // 기존 DB 접근 코드
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
        val selectQuery = "SELECT AuthCode FROM Player_Auth WHERE UUID = ?"
        val insertQuery = "INSERT INTO Player_Auth (UUID, IsAuth, AuthCode) VALUES (?, ?, ?)"
        getConnection().use { connection ->
            connection.prepareStatement(selectQuery).use { selectStmt ->
                selectStmt.setString(1, uuid)
                selectStmt.executeQuery().use { resultSet ->
                    if (resultSet.next()) {
                        return resultSet.getString("AuthCode")
                    }
                }
            }
            val authCode = generateAuthCode()
            connection.prepareStatement(insertQuery).use { insertStmt ->
                insertStmt.setString(1, uuid)
                insertStmt.setBoolean(2, false)
                insertStmt.setString(3, authCode)
                insertStmt.executeUpdate()
            }
            return authCode
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
                statement.executeQuery().use { resultSet ->
                    return if (resultSet.next()) {
                        PlayerData(
                            nickname = resultSet.getString("nickname"),
                            uuid = resultSet.getString("uuid"),
                            discordId = discordId
                        )
                    } else {
                        null
                    }
                }
            }
        }
    }

    // DiscordVoiceChannelListener 에서 사용할 함수 추가
    fun getPlayerUUIDByDiscordID(discordId: String): String? {
        val query = "SELECT UUID FROM Player_Data WHERE DiscordID = ?"
        getConnection().use { connection ->
            connection.prepareStatement(query).use { statement ->
                statement.setString(1, discordId)
                statement.executeQuery().use { resultSet ->
                    return if (resultSet.next()) {
                        resultSet.getString("UUID")
                    } else {
                        null
                    }
                }
            }
        }
    }

    /**
     * Player_NameTag 테이블에서 UUID로 태그를 조회합니다.
     */
    fun getPlayerNameTag(uuid: String): String? {
        val query = "SELECT Tag FROM Player_NameTag WHERE UUID = ?"
        getConnection().use { connection ->
            connection.prepareStatement(query).use { statement ->
                statement.setString(1, uuid)
                statement.executeQuery().use { resultSet ->
                    return if (resultSet.next()) {
                        resultSet.getString("Tag")
                    } else {
                        null
                    }
                }
            }
        }
    }

    fun setTitokerMessageEnabled(uuid: String, enabled: Boolean) {
        val query = """
            INSERT INTO Titoker_Message_Setting (UUID, IsEnabled) 
            VALUES (?, ?) 
            ON DUPLICATE KEY UPDATE IsEnabled = ?
        """
        getConnection().use { connection ->
            connection.prepareStatement(query).use { statement ->
                statement.setString(1, uuid)
                statement.setBoolean(2, enabled)
                statement.setBoolean(3, enabled)
                statement.executeUpdate()
            }
        }
    }

    fun isTitokerMessageEnabled(uuid: String): Boolean {
        val query = "SELECT IsEnabled FROM Titoker_Message_Setting WHERE UUID = ?"
        getConnection().use { connection ->
            connection.prepareStatement(query).use { statement ->
                statement.setString(1, uuid)
                statement.executeQuery().use { resultSet ->
                    return if (resultSet.next()) {
                        resultSet.getBoolean("IsEnabled")
                    } else {
                        false
                    }
                }
            }
        }
    }

    fun getDiscordIDByUUID(uuid: String): String? {
        val query = "SELECT DiscordID FROM Player_Data WHERE UUID = ?"
        getConnection().use { connection ->
            connection.prepareStatement(query).use { statement ->
                statement.setString(1, uuid)
                statement.executeQuery().use { resultSet ->
                    return if (resultSet.next()) {
                        resultSet.getString("DiscordID")
                    } else {
                        null
                    }
                }
            }
        }
    }

    fun setSetting(settingType: String, settingValue: String) {
        if (useApi && ::plugin.isInitialized && plugin::apiClient.isInitialized) {
            try {
                plugin.apiClient.updateSetting(settingType, settingValue)
                return
            } catch (e: Exception) {
                plugin.logger.warning("API를 통한 설정 업데이트 실패, DB로 폴백: ${e.message}")
            }
        }
        
        // 기존 DB 접근 코드
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
        val query = "SELECT SupportID, MessageLink FROM SupportChatLink WHERE UUID = ? AND CaseClose = 0"
        getConnection().use { connection ->
            connection.prepareStatement(query).use { statement ->
                statement.setString(1, uuid)
                statement.executeQuery().use { resultSet ->
                    while (resultSet.next()) {
                        val supportId = resultSet.getString("SupportID")
                        val messageLink = resultSet.getString("MessageLink")
                        openCases.add(SupportCase(supportId, messageLink))
                    }
                }
            }
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

    fun createLockTable() {
        val query = """
            CREATE TABLE IF NOT EXISTS block_locks (
                lock_id VARCHAR(36) PRIMARY KEY,
                owner_uuid VARCHAR(36) NOT NULL,
                access_list_json TEXT,
                is_locked BOOLEAN NOT NULL DEFAULT TRUE,
                allow_redstone BOOLEAN NOT NULL DEFAULT FALSE,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
        """
        getConnection().use { connection ->
            connection.prepareStatement(query).use { statement ->
                statement.executeUpdate()
            }
        }
    }

    fun getLockPermissions(lockId: LockID): LockPermissions? {
        val query = "SELECT owner_uuid, access_list_json, is_locked, allow_redstone FROM block_locks WHERE lock_id = ?"
        getConnection().use { connection ->
            connection.prepareStatement(query).use { statement ->
                statement.setString(1, lockId.id.toString())
                statement.executeQuery().use { resultSet ->
                    if (resultSet.next()) {
                        val ownerUuidStr = resultSet.getString("owner_uuid")
                        val ownerUuid = UUID.fromString(ownerUuidStr)
                        val accessListJson = resultSet.getString("access_list_json")
                        val isLocked = resultSet.getBoolean("is_locked")
                        val allowRedstone = resultSet.getBoolean("allow_redstone")
                        val allowedPlayers = parseAccessListJson(accessListJson) ?: mutableSetOf()

                        return LockPermissions(lockId, ownerUuid, allowedPlayers, isLocked, allowRedstone)
                    } else {
                        return null
                    }
                }
            }
        }
    }

    private fun parseAccessListJson(accessListJson: String?): MutableSet<UUID>? {
        return accessListJson?.let {
            val type = object : TypeToken<MutableSet<String>>() {}.type
            val uuidStrings = gson.fromJson<MutableSet<String>>(it, type) ?: emptySet<String>().toMutableSet() // null 처리 추가
            uuidStrings.map { UUID.fromString(it) }.toMutableSet()
        } ?: mutableSetOf() // null 처리 추가
    }

    fun saveLockPermissionsAsync(lockPermissions: LockPermissions) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
            saveLockPermissions(lockPermissions)
        })
    }

    fun saveLockPermissions(lockPermissions: LockPermissions) {
        val query = """
            INSERT INTO block_locks (lock_id, owner_uuid, access_list_json, is_locked, allow_redstone) 
            VALUES (?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE access_list_json = ?, is_locked = ?, allow_redstone = ?
        """
        getConnection().use { connection ->
            connection.prepareStatement(query).use { statement ->
                statement.setString(1, lockPermissions.lockId.id.toString())
                statement.setString(2, lockPermissions.owner.toString()) // Owner UUID
                statement.setString(3, gson.toJson(lockPermissions.allowedPlayers.map { it.toString() })) // Access list json
                statement.setBoolean(4, lockPermissions.isLocked) // isLocked 값 저장
                statement.setBoolean(5, lockPermissions.allowRedstone) // allowRedstone 값 저장
                statement.setString(6, gson.toJson(lockPermissions.allowedPlayers.map { it.toString() })) // Access list json (for update)
                statement.setBoolean(7, lockPermissions.isLocked) // isLocked 값 저장 (for update)
                statement.setBoolean(8, lockPermissions.allowRedstone) // allowRedstone 값 저장 (for update)
                statement.executeUpdate()
            }
        }
    }

    fun deleteLockPermissions(lockId: LockID) {
        val query = "DELETE FROM block_locks WHERE lock_id = ?"
        getConnection().use { connection ->
            connection.prepareStatement(query).use { statement ->
                statement.setString(1, lockId.id.toString())
                statement.executeUpdate()
            }
        }
    }
}
