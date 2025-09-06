// Database.kt
package com.lukehemmin.lukeVanilla.System.Database

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.lukehemmin.lukeVanilla.Main
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
    
    // MultiServer 시스템 데이터 클래스들
    data class ServerHeartbeat(
        val serverName: String,
        val tps: Double,
        val mspt: Double,
        val onlinePlayers: Int,
        val maxPlayers: Int, 
        val serverStatus: String,
        val lastUpdate: Long
    )
    
    data class OnlinePlayerInfo(
        val serverName: String,
        val playerUuid: String,
        val playerName: String,
        val playerDisplayName: String?,
        val locationWorld: String?,
        val locationX: Double,
        val locationY: Double,
        val locationZ: Double,
        val joinTime: Long,
        val lastUpdate: Long
    )
    
    data class CrossServerCommand(
        val id: Int,
        val commandType: String,
        val targetPlayerUuid: String,
        val targetPlayerName: String,
        val sourceServer: String,
        val targetServer: String,
        val commandData: String,
        val commandReason: String?,
        val issuedBy: String,
        val status: String,
        val createdAt: Long,
        val executedAt: Long?,
        val errorMessage: String?
    )

    init {
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

    fun getPlayerDataByNickname(nickname: String): PlayerData? {
        val query = "SELECT UUID, NickName, DiscordID FROM Player_Data WHERE NickName = ?"
        getConnection().use { connection ->
            connection.prepareStatement(query).use { statement ->
                statement.setString(1, nickname)
                statement.executeQuery().use { resultSet ->
                    return if (resultSet.next()) {
                        PlayerData(
                            nickname = resultSet.getString("NickName"),
                            uuid = resultSet.getString("UUID"),
                            discordId = resultSet.getString("DiscordID")
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

    fun setVoiceChannelMessageEnabled(uuid: String, enabled: Boolean) {
        val query = """
            INSERT INTO Voice_Channel_Message_Setting (UUID, IsEnabled) 
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

    fun isVoiceChannelMessageEnabled(uuid: String): Boolean {
        val query = "SELECT IsEnabled FROM Voice_Channel_Message_Setting WHERE UUID = ?"
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

    fun addDynamicVoiceChannel(channelId: String, creatorId: String) {
        val query = "INSERT INTO Dynamic_Voice_Channel (channel_id, creator_id) VALUES (?, ?)"
        getConnection().use { connection ->
            connection.prepareStatement(query).use { statement ->
                statement.setString(1, channelId)
                statement.setString(2, creatorId)
                statement.executeUpdate()
            }
        }
    }

    fun removeDynamicVoiceChannel(channelId: String) {
        val query = "DELETE FROM Dynamic_Voice_Channel WHERE channel_id = ?"
        getConnection().use { connection ->
            connection.prepareStatement(query).use { statement ->
                statement.setString(1, channelId)
                statement.executeUpdate()
            }
        }
    }

    fun getDynamicVoiceChannels(): Set<String> {
        val channels = mutableSetOf<String>()
        val query = "SELECT channel_id FROM Dynamic_Voice_Channel"
        getConnection().use { connection ->
            connection.prepareStatement(query).use { statement ->
                statement.executeQuery().use { resultSet ->
                    while (resultSet.next()) {
                        channels.add(resultSet.getString("channel_id"))
                    }
                }
            }
        }
        return channels
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

    // 기존 블록 잠금 시스템 코드는 새로운 NBT 기반 시스템으로 대체되었습니다.
    // 블록 보호 데이터는 이제 블록의 NBT에 직접 저장됩니다.
    
    // ===== MultiServer 동기화 시스템 함수들 =====
    
    /**
     * 서버 상태 정보를 업데이트합니다 (30초마다 호출)
     */
    fun updateServerHeartbeat(serverName: String, tps: Double, mspt: Double, onlinePlayers: Int, maxPlayers: Int) {
        val query = """
            INSERT INTO server_heartbeat (server_name, tps, mspt, online_players, max_players, server_status, last_update)
            VALUES (?, ?, ?, ?, ?, 'online', NOW())
            ON DUPLICATE KEY UPDATE
                tps = VALUES(tps),
                mspt = VALUES(mspt),
                online_players = VALUES(online_players),
                max_players = VALUES(max_players),
                server_status = 'online',
                last_update = NOW()
        """.trimIndent()
        
        getConnection().use { connection ->
            connection.prepareStatement(query).use { statement ->
                statement.setString(1, serverName)
                statement.setDouble(2, tps)
                statement.setDouble(3, mspt)
                statement.setInt(4, onlinePlayers)
                statement.setInt(5, maxPlayers)
                statement.executeUpdate()
            }
        }
    }
    
    /**
     * 서버 상태 정보를 조회합니다
     */
    fun getServerHeartbeat(serverName: String): ServerHeartbeat? {
        val query = """
            SELECT server_name, tps, mspt, online_players, max_players, server_status, 
                   UNIX_TIMESTAMP(last_update) as last_update_unix
            FROM server_heartbeat 
            WHERE server_name = ? AND last_update > NOW() - INTERVAL 2 MINUTE
        """.trimIndent()
        
        getConnection().use { connection ->
            connection.prepareStatement(query).use { statement ->
                statement.setString(1, serverName)
                statement.executeQuery().use { resultSet ->
                    if (resultSet.next()) {
                        return ServerHeartbeat(
                            serverName = resultSet.getString("server_name"),
                            tps = resultSet.getDouble("tps"),
                            mspt = resultSet.getDouble("mspt"),
                            onlinePlayers = resultSet.getInt("online_players"),
                            maxPlayers = resultSet.getInt("max_players"),
                            serverStatus = resultSet.getString("server_status"),
                            lastUpdate = resultSet.getLong("last_update_unix") * 1000L
                        )
                    }
                }
            }
        }
        return null
    }
    
    /**
     * 온라인 플레이어 정보를 업데이트합니다 (접속/이동 시 호출)
     */
    fun updateOnlinePlayer(
        serverName: String, playerUuid: String, playerName: String, playerDisplayName: String?,
        locationWorld: String?, locationX: Double, locationY: Double, locationZ: Double
    ) {
        val query = """
            INSERT INTO server_online_players 
            (server_name, player_uuid, player_name, player_displayname, location_world, location_x, location_y, location_z, join_time, last_update)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
            ON DUPLICATE KEY UPDATE
                player_name = VALUES(player_name),
                player_displayname = VALUES(player_displayname),
                location_world = VALUES(location_world),
                location_x = VALUES(location_x),
                location_y = VALUES(location_y),
                location_z = VALUES(location_z),
                last_update = NOW()
        """.trimIndent()
        
        getConnection().use { connection ->
            connection.prepareStatement(query).use { statement ->
                statement.setString(1, serverName)
                statement.setString(2, playerUuid)
                statement.setString(3, playerName)
                statement.setString(4, playerDisplayName)
                statement.setString(5, locationWorld)
                statement.setDouble(6, locationX)
                statement.setDouble(7, locationY)
                statement.setDouble(8, locationZ)
                statement.executeUpdate()
            }
        }
    }
    
    /**
     * 플레이어 로그아웃 시 온라인 목록에서 제거합니다
     */
    fun removeOnlinePlayer(serverName: String, playerUuid: String) {
        val query = "DELETE FROM server_online_players WHERE server_name = ? AND player_uuid = ?"
        getConnection().use { connection ->
            connection.prepareStatement(query).use { statement ->
                statement.setString(1, serverName)
                statement.setString(2, playerUuid)
                statement.executeUpdate()
            }
        }
    }
    
    /**
     * 특정 서버의 온라인 플레이어 목록을 조회합니다
     */
    fun getOnlinePlayersFromServer(serverName: String): List<OnlinePlayerInfo> {
        val query = """
            SELECT server_name, player_uuid, player_name, player_displayname, location_world,
                   location_x, location_y, location_z, 
                   UNIX_TIMESTAMP(join_time) as join_time_unix,
                   UNIX_TIMESTAMP(last_update) as last_update_unix
            FROM server_online_players 
            WHERE server_name = ? AND last_update > NOW() - INTERVAL 5 MINUTE
            ORDER BY join_time DESC
        """.trimIndent()
        
        val players = mutableListOf<OnlinePlayerInfo>()
        getConnection().use { connection ->
            connection.prepareStatement(query).use { statement ->
                statement.setString(1, serverName)
                statement.executeQuery().use { resultSet ->
                    while (resultSet.next()) {
                        players.add(OnlinePlayerInfo(
                            serverName = resultSet.getString("server_name"),
                            playerUuid = resultSet.getString("player_uuid"),
                            playerName = resultSet.getString("player_name"),
                            playerDisplayName = resultSet.getString("player_displayname"),
                            locationWorld = resultSet.getString("location_world"),
                            locationX = resultSet.getDouble("location_x"),
                            locationY = resultSet.getDouble("location_y"),
                            locationZ = resultSet.getDouble("location_z"),
                            joinTime = resultSet.getLong("join_time_unix") * 1000L,
                            lastUpdate = resultSet.getLong("last_update_unix") * 1000L
                        ))
                    }
                }
            }
        }
        return players
    }
    
    /**
     * 플레이어가 어느 서버든 접속중인지 확인합니다
     */
    fun isPlayerOnlineInAnyServer(playerUuid: String): Boolean {
        val query = """
            SELECT COUNT(*) as count FROM server_online_players 
            WHERE player_uuid = ? AND last_update > NOW() - INTERVAL 5 MINUTE
        """.trimIndent()
        
        getConnection().use { connection ->
            connection.prepareStatement(query).use { statement ->
                statement.setString(1, playerUuid)
                statement.executeQuery().use { resultSet ->
                    if (resultSet.next()) {
                        return resultSet.getInt("count") > 0
                    }
                }
            }
        }
        return false
    }
    
    /**
     * 플레이어가 현재 접속중인 서버를 조회합니다
     */
    fun getPlayerCurrentServer(playerUuid: String): String? {
        val query = """
            SELECT server_name FROM server_online_players 
            WHERE player_uuid = ? AND last_update > NOW() - INTERVAL 5 MINUTE
            LIMIT 1
        """.trimIndent()
        
        getConnection().use { connection ->
            connection.prepareStatement(query).use { statement ->
                statement.setString(1, playerUuid)
                statement.executeQuery().use { resultSet ->
                    if (resultSet.next()) {
                        return resultSet.getString("server_name")
                    }
                }
            }
        }
        return null
    }
    
    /**
     * 교차 서버 명령어를 추가합니다 (예: 밴, 킥 등)
     */
    fun addCrossServerCommand(
        commandType: String, targetPlayerUuid: String, targetPlayerName: String,
        sourceServer: String, targetServer: String, commandData: Map<String, Any>,
        commandReason: String?, issuedBy: String
    ): Int {
        val query = """
            INSERT INTO cross_server_commands 
            (command_type, target_player_uuid, target_player_name, source_server, target_server, 
             command_data, command_reason, issued_by, status, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'pending', NOW())
        """.trimIndent()
        
        getConnection().use { connection ->
            connection.prepareStatement(query, java.sql.Statement.RETURN_GENERATED_KEYS).use { statement ->
                statement.setString(1, commandType)
                statement.setString(2, targetPlayerUuid)
                statement.setString(3, targetPlayerName)
                statement.setString(4, sourceServer)
                statement.setString(5, targetServer)
                statement.setString(6, gson.toJson(commandData))
                statement.setString(7, commandReason)
                statement.setString(8, issuedBy)
                statement.executeUpdate()
                
                statement.generatedKeys.use { keys ->
                    if (keys.next()) {
                        return keys.getInt(1)
                    }
                }
            }
        }
        return -1
    }
    
    /**
     * 특정 서버의 대기중인 교차 서버 명령어들을 조회합니다
     */
    fun getPendingCrossServerCommands(targetServer: String): List<CrossServerCommand> {
        val query = """
            SELECT id, command_type, target_player_uuid, target_player_name, source_server, target_server,
                   command_data, command_reason, issued_by, status,
                   UNIX_TIMESTAMP(created_at) as created_at_unix,
                   UNIX_TIMESTAMP(executed_at) as executed_at_unix,
                   error_message
            FROM cross_server_commands 
            WHERE target_server = ? AND status = 'pending'
            ORDER BY created_at ASC
        """.trimIndent()
        
        val commands = mutableListOf<CrossServerCommand>()
        getConnection().use { connection ->
            connection.prepareStatement(query).use { statement ->
                statement.setString(1, targetServer)
                statement.executeQuery().use { resultSet ->
                    while (resultSet.next()) {
                        commands.add(CrossServerCommand(
                            id = resultSet.getInt("id"),
                            commandType = resultSet.getString("command_type"),
                            targetPlayerUuid = resultSet.getString("target_player_uuid"),
                            targetPlayerName = resultSet.getString("target_player_name"),
                            sourceServer = resultSet.getString("source_server"),
                            targetServer = resultSet.getString("target_server"),
                            commandData = resultSet.getString("command_data"),
                            commandReason = resultSet.getString("command_reason"),
                            issuedBy = resultSet.getString("issued_by"),
                            status = resultSet.getString("status"),
                            createdAt = resultSet.getLong("created_at_unix") * 1000L,
                            executedAt = resultSet.getLong("executed_at_unix").let { if (it > 0L) it * 1000L else null },
                            errorMessage = resultSet.getString("error_message")
                        ))
                    }
                }
            }
        }
        return commands
    }
    
    /**
     * 교차 서버 명령어의 실행 완료를 표시합니다
     */
    fun markCrossServerCommandExecuted(commandId: Int, success: Boolean, errorMessage: String? = null) {
        val status = if (success) "executed" else "failed"
        val query = """
            UPDATE cross_server_commands 
            SET status = ?, executed_at = NOW(), error_message = ?
            WHERE id = ?
        """.trimIndent()
        
        getConnection().use { connection ->
            connection.prepareStatement(query).use { statement ->
                statement.setString(1, status)
                statement.setString(2, errorMessage)
                statement.setInt(3, commandId)
                statement.executeUpdate()
            }
        }
    }
}
