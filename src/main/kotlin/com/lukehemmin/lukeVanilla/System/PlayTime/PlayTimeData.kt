package com.lukehemmin.lukeVanilla.System.PlayTime

import com.lukehemmin.lukeVanilla.System.Database.Database
import java.util.UUID

/**
 * 플레이타임 정보를 담는 데이터 클래스
 */
data class PlayTimeInfo(
    val playerUuid: UUID,
    val totalPlaytimeSeconds: Long,
    val sessionStartTime: Long?,
    val lastUpdated: Long,
    val createdAt: Long
)

/**
 * PlayTime 시스템의 데이터베이스 작업을 담당하는 클래스
 * DB 쿼리를 재사용 가능한 모듈로 구현
 */
class PlayTimeData(private val database: Database) {
    
    /**
     * 플레이어의 플레이타임 정보를 조회합니다.
     * @param playerUuid 플레이어 UUID
     * @return PlayTimeInfo 또는 null (없을 경우)
     */
    fun getPlayTimeInfo(playerUuid: UUID): PlayTimeInfo? {
        val query = """
            SELECT player_uuid, total_playtime_seconds, session_start_time, 
                   UNIX_TIMESTAMP(last_updated) as last_updated,
                   UNIX_TIMESTAMP(created_at) as created_at
            FROM playtime_data 
            WHERE player_uuid = ?
        """.trimIndent()
        
        database.getConnection().use { connection ->
            connection.prepareStatement(query).use { statement ->
                statement.setString(1, playerUuid.toString())
                statement.executeQuery().use { resultSet ->
                    if (resultSet.next()) {
                        return PlayTimeInfo(
                            playerUuid = UUID.fromString(resultSet.getString("player_uuid")),
                            totalPlaytimeSeconds = resultSet.getLong("total_playtime_seconds"),
                            sessionStartTime = resultSet.getLong("session_start_time").takeIf { !resultSet.wasNull() },
                            lastUpdated = resultSet.getLong("last_updated"),
                            createdAt = resultSet.getLong("created_at")
                        )
                    }
                }
            }
        }
        return null
    }
    
    /**
     * 플레이어의 총 플레이타임(초)을 조회합니다.
     * @param playerUuid 플레이어 UUID
     * @return 총 플레이타임(초), 없으면 0
     */
    fun getTotalPlayTime(playerUuid: UUID): Long {
        val query = "SELECT total_playtime_seconds FROM playtime_data WHERE player_uuid = ?"
        
        database.getConnection().use { connection ->
            connection.prepareStatement(query).use { statement ->
                statement.setString(1, playerUuid.toString())
                statement.executeQuery().use { resultSet ->
                    if (resultSet.next()) {
                        return resultSet.getLong("total_playtime_seconds")
                    }
                }
            }
        }
        return 0L
    }
    
    /**
     * 플레이어의 세션 시작 시간을 설정합니다.
     * @param playerUuid 플레이어 UUID
     * @param sessionStartTime 세션 시작 시간 (밀리초)
     * @return 성공 여부
     */
    fun setSessionStartTime(playerUuid: UUID, sessionStartTime: Long?): Boolean {
        val query = """
            INSERT INTO playtime_data (player_uuid, session_start_time) 
            VALUES (?, ?) 
            ON DUPLICATE KEY UPDATE session_start_time = VALUES(session_start_time)
        """.trimIndent()
        
        return try {
            database.getConnection().use { connection ->
                connection.prepareStatement(query).use { statement ->
                    statement.setString(1, playerUuid.toString())
                    if (sessionStartTime != null) {
                        statement.setLong(2, sessionStartTime)
                    } else {
                        statement.setNull(2, java.sql.Types.BIGINT)
                    }
                    statement.executeUpdate() > 0
                }
            }
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * 플레이어의 총 플레이타임을 업데이트합니다.
     * @param playerUuid 플레이어 UUID
     * @param totalPlaytimeSeconds 총 플레이타임(초)
     * @return 성공 여부
     */
    fun updateTotalPlayTime(playerUuid: UUID, totalPlaytimeSeconds: Long): Boolean {
        val query = """
            INSERT INTO playtime_data (player_uuid, total_playtime_seconds) 
            VALUES (?, ?) 
            ON DUPLICATE KEY UPDATE 
            total_playtime_seconds = VALUES(total_playtime_seconds),
            last_updated = CURRENT_TIMESTAMP
        """.trimIndent()
        
        return try {
            database.getConnection().use { connection ->
                connection.prepareStatement(query).use { statement ->
                    statement.setString(1, playerUuid.toString())
                    statement.setLong(2, totalPlaytimeSeconds)
                    statement.executeUpdate() > 0
                }
            }
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * 플레이어의 플레이타임 정보를 모두 업데이트합니다.
     * @param playerUuid 플레이어 UUID
     * @param totalPlaytimeSeconds 총 플레이타임(초)
     * @param sessionStartTime 세션 시작 시간 (밀리초, nullable)
     * @return 성공 여부
     */
    fun updatePlayTimeInfo(playerUuid: UUID, totalPlaytimeSeconds: Long, sessionStartTime: Long?): Boolean {
        val query = """
            INSERT INTO playtime_data (player_uuid, total_playtime_seconds, session_start_time) 
            VALUES (?, ?, ?) 
            ON DUPLICATE KEY UPDATE 
            total_playtime_seconds = VALUES(total_playtime_seconds),
            session_start_time = VALUES(session_start_time),
            last_updated = CURRENT_TIMESTAMP
        """.trimIndent()
        
        return try {
            database.getConnection().use { connection ->
                connection.prepareStatement(query).use { statement ->
                    statement.setString(1, playerUuid.toString())
                    statement.setLong(2, totalPlaytimeSeconds)
                    if (sessionStartTime != null) {
                        statement.setLong(3, sessionStartTime)
                    } else {
                        statement.setNull(3, java.sql.Types.BIGINT)
                    }
                    statement.executeUpdate() > 0
                }
            }
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * 플레이어의 플레이타임 데이터를 삭제합니다. (관리자용)
     * @param playerUuid 플레이어 UUID
     * @return 성공 여부
     */
    fun deletePlayTimeData(playerUuid: UUID): Boolean {
        val query = "DELETE FROM playtime_data WHERE player_uuid = ?"
        
        return try {
            database.getConnection().use { connection ->
                connection.prepareStatement(query).use { statement ->
                    statement.setString(1, playerUuid.toString())
                    statement.executeUpdate() > 0
                }
            }
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * 모든 플레이어의 플레이타임 정보를 조회합니다. (관리자용)
     * @return 플레이타임 정보 리스트
     */
    fun getAllPlayTimeInfo(): List<PlayTimeInfo> {
        val playTimeList = mutableListOf<PlayTimeInfo>()
        val query = """
            SELECT player_uuid, total_playtime_seconds, session_start_time,
                   UNIX_TIMESTAMP(last_updated) as last_updated,
                   UNIX_TIMESTAMP(created_at) as created_at
            FROM playtime_data 
            ORDER BY total_playtime_seconds DESC
        """.trimIndent()
        
        database.getConnection().use { connection ->
            connection.prepareStatement(query).use { statement ->
                statement.executeQuery().use { resultSet ->
                    while (resultSet.next()) {
                        playTimeList.add(
                            PlayTimeInfo(
                                playerUuid = UUID.fromString(resultSet.getString("player_uuid")),
                                totalPlaytimeSeconds = resultSet.getLong("total_playtime_seconds"),
                                sessionStartTime = resultSet.getLong("session_start_time").takeIf { !resultSet.wasNull() },
                                lastUpdated = resultSet.getLong("last_updated"),
                                createdAt = resultSet.getLong("created_at")
                            )
                        )
                    }
                }
            }
        }
        return playTimeList
    }
    
    /**
     * 상위 N명의 플레이타임 정보를 조회합니다. (최적화된 쿼리)
     * @param limit 조회할 상위 플레이어 수
     * @return 상위 플레이타임 정보 리스트
     */
    fun getTopPlayTimeInfo(limit: Int): List<PlayTimeInfo> {
        val playTimeList = mutableListOf<PlayTimeInfo>()
        val query = """
            SELECT player_uuid, total_playtime_seconds, session_start_time,
                   UNIX_TIMESTAMP(last_updated) as last_updated,
                   UNIX_TIMESTAMP(created_at) as created_at
            FROM playtime_data 
            ORDER BY total_playtime_seconds DESC
            LIMIT ?
        """.trimIndent()
        
        database.getConnection().use { connection ->
            connection.prepareStatement(query).use { statement ->
                statement.setInt(1, limit)
                statement.executeQuery().use { resultSet ->
                    while (resultSet.next()) {
                        playTimeList.add(
                            PlayTimeInfo(
                                playerUuid = UUID.fromString(resultSet.getString("player_uuid")),
                                totalPlaytimeSeconds = resultSet.getLong("total_playtime_seconds"),
                                sessionStartTime = resultSet.getLong("session_start_time").takeIf { !resultSet.wasNull() },
                                lastUpdated = resultSet.getLong("last_updated"),
                                createdAt = resultSet.getLong("created_at")
                            )
                        )
                    }
                }
            }
        }
        return playTimeList
    }
    
    /**
     * 특정 플레이타임 이상인 플레이어 수를 조회합니다.
     * @param minimumSeconds 최소 플레이타임(초)
     * @return 플레이어 수
     */
    fun getPlayerCountAbovePlayTime(minimumSeconds: Long): Int {
        val query = "SELECT COUNT(*) as count FROM playtime_data WHERE total_playtime_seconds >= ?"
        
        database.getConnection().use { connection ->
            connection.prepareStatement(query).use { statement ->
                statement.setLong(1, minimumSeconds)
                statement.executeQuery().use { resultSet ->
                    if (resultSet.next()) {
                        return resultSet.getInt("count")
                    }
                }
            }
        }
        return 0
    }
}