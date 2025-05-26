package com.lukehemmin.lukeVanilla.System.WarningSystem

import com.lukehemmin.lukeVanilla.System.Database.Database
import java.sql.Connection
import java.sql.ResultSet
import java.sql.Statement
import java.sql.Timestamp
import java.time.LocalDateTime
import java.util.UUID
import java.util.logging.Level
import java.util.logging.Logger

/**
 * 경고 시스템 데이터베이스 작업을 처리하는 클래스
 */
class WarningRepository(private val database: Database) {
    private val logger = Logger.getLogger(WarningRepository::class.java.name)

    /**
     * 플레이어 경고 정보 가져오기 또는 생성
     */
    fun getOrCreatePlayerWarning(uuid: UUID, username: String): PlayerWarning {
        var playerWarning: PlayerWarning? = null
        
        try {
            database.getConnection().use { connection ->
                // 기존 플레이어 정보 조회
                val selectQuery = "SELECT * FROM warnings_players WHERE uuid = ?"
                connection.prepareStatement(selectQuery).use { statement ->
                    statement.setString(1, uuid.toString())
                    statement.executeQuery().use { resultSet ->
                        if (resultSet.next()) {
                            playerWarning = resultSetToPlayerWarning(resultSet)
                        }
                    }
                }
                
                // 플레이어 정보가 없으면 새로 생성
                if (playerWarning == null) {
                    val insertQuery = """
                        INSERT INTO warnings_players (uuid, username, active_warnings_count)
                        VALUES (?, ?, 0)
                    """.trimIndent()
                    
                    connection.prepareStatement(insertQuery, Statement.RETURN_GENERATED_KEYS).use { statement ->
                        statement.setString(1, uuid.toString())
                        statement.setString(2, username)
                        statement.executeUpdate()
                        
                        statement.generatedKeys.use { generatedKeys ->
                            if (generatedKeys.next()) {
                                val playerId = generatedKeys.getInt(1)
                                playerWarning = PlayerWarning(
                                    playerId = playerId,
                                    uuid = uuid,
                                    username = username,
                                    activeWarningsCount = 0
                                )
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logger.log(Level.SEVERE, "플레이어 경고 정보 조회/생성 중 오류 발생", e)
        }
        
        return playerWarning ?: PlayerWarning(uuid = uuid, username = username)
    }

    /**
     * 경고 추가
     */
    fun addWarning(playerUuid: UUID, playerName: String, adminUuid: UUID, adminName: String, reason: String): Boolean {
        try {
            database.getConnection().use { connection ->
                connection.autoCommit = false
                
                try {
                    // 플레이어 정보 조회 또는 생성
                    val player = getOrCreatePlayerWarning(playerUuid, playerName)
                    val playerId = player.playerId ?: return false
                    
                    // 경고 기록 추가
                    val insertWarningQuery = """
                        INSERT INTO warnings_records 
                        (player_id, admin_uuid, admin_name, reason)
                        VALUES (?, ?, ?, ?)
                    """.trimIndent()
                    
                    connection.prepareStatement(insertWarningQuery).use { statement ->
                        statement.setInt(1, playerId)
                        statement.setString(2, adminUuid.toString())
                        statement.setString(3, adminName)
                        statement.setString(4, reason)
                        statement.executeUpdate()
                    }
                    
                    // 플레이어 경고 횟수 및 최종 경고 시간 업데이트
                    val updatePlayerQuery = """
                        UPDATE warnings_players 
                        SET active_warnings_count = active_warnings_count + 1,
                            last_warning_date = CURRENT_TIMESTAMP
                        WHERE player_id = ?
                    """.trimIndent()
                    
                    connection.prepareStatement(updatePlayerQuery).use { statement ->
                        statement.setInt(1, playerId)
                        statement.executeUpdate()
                    }
                    
                    connection.commit()
                    return true
                } catch (e: Exception) {
                    connection.rollback()
                    logger.log(Level.SEVERE, "경고 추가 중 오류 발생", e)
                    return false
                } finally {
                    connection.autoCommit = true
                }
            }
        } catch (e: Exception) {
            logger.log(Level.SEVERE, "경고 추가 중 데이터베이스 연결 오류", e)
            return false
        }
    }

    /**
     * 특정 경고 ID로 경고 차감
     */
    fun pardonWarningById(
        warningId: Int, 
        playerUuid: UUID, 
        adminUuid: UUID, 
        adminName: String, 
        reason: String
    ): Boolean {
        try {
            database.getConnection().use { connection ->
                connection.autoCommit = false
                
                try {
                    // 플레이어 정보 확인
                    val player = getPlayerWarningByUuid(playerUuid) ?: return false
                    val playerId = player.playerId ?: return false
                    
                    // 경고가 존재하고 활성 상태인지 확인
                    val checkWarningQuery = """
                        SELECT * FROM warnings_records 
                        WHERE warning_id = ? AND player_id = ? AND is_active = TRUE
                    """.trimIndent()
                    
                    var warningExists = false
                    
                    connection.prepareStatement(checkWarningQuery).use { statement ->
                        statement.setInt(1, warningId)
                        statement.setInt(2, playerId)
                        statement.executeQuery().use { resultSet ->
                            warningExists = resultSet.next()
                        }
                    }
                    
                    if (!warningExists) {
                        return false
                    }
                    
                    // 경고 차감 처리
                    val updateWarningQuery = """
                        UPDATE warnings_records 
                        SET is_active = FALSE,
                            pardoned_at = CURRENT_TIMESTAMP,
                            pardoned_by_uuid = ?,
                            pardoned_by_name = ?,
                            pardon_reason = ?
                        WHERE warning_id = ?
                    """.trimIndent()
                    
                    connection.prepareStatement(updateWarningQuery).use { statement ->
                        statement.setString(1, adminUuid.toString())
                        statement.setString(2, adminName)
                        statement.setString(3, reason)
                        statement.setInt(4, warningId)
                        statement.executeUpdate()
                    }
                    
                    // 차감 이력 추가
                    val insertPardonQuery = """
                        INSERT INTO warnings_pardons
                        (player_id, admin_uuid, admin_name, count, reason, is_id_based, warning_id)
                        VALUES (?, ?, ?, 1, ?, TRUE, ?)
                    """.trimIndent()
                    
                    connection.prepareStatement(insertPardonQuery).use { statement ->
                        statement.setInt(1, playerId)
                        statement.setString(2, adminUuid.toString())
                        statement.setString(3, adminName)
                        statement.setString(4, reason)
                        statement.setInt(5, warningId)
                        statement.executeUpdate()
                    }
                    
                    // 플레이어 활성 경고 수 감소
                    val updatePlayerQuery = """
                        UPDATE warnings_players
                        SET active_warnings_count = active_warnings_count - 1,
                            last_warning_date = CURRENT_TIMESTAMP
                        WHERE player_id = ?
                    """.trimIndent()
                    
                    connection.prepareStatement(updatePlayerQuery).use { statement ->
                        statement.setInt(1, playerId)
                        statement.executeUpdate()
                    }
                    
                    connection.commit()
                    return true
                } catch (e: Exception) {
                    connection.rollback()
                    logger.log(Level.SEVERE, "경고 ID 차감 중 오류 발생", e)
                    return false
                } finally {
                    connection.autoCommit = true
                }
            }
        } catch (e: Exception) {
            logger.log(Level.SEVERE, "경고 ID 차감 중 데이터베이스 연결 오류", e)
            return false
        }
    }

    /**
     * 횟수 기반 경고 차감
     */
    fun pardonWarningsByCount(
        playerUuid: UUID,
        count: Int,
        adminUuid: UUID,
        adminName: String,
        reason: String
    ): Boolean {
        if (count <= 0) return false
        
        try {
            database.getConnection().use { connection ->
                connection.autoCommit = false
                
                try {
                    // 플레이어 정보 확인
                    val player = getPlayerWarningByUuid(playerUuid) ?: return false
                    val playerId = player.playerId ?: return false
                    
                    // 차감 가능한 경고 수 확인
                    val activeWarningsCount = player.activeWarningsCount
                    val actualCount = minOf(count, activeWarningsCount)
                    
                    if (actualCount <= 0) {
                        return false
                    }
                    
                    // 가장 오래된 활성 경고부터 차감
                    val getOldestWarningsQuery = """
                        SELECT warning_id FROM warnings_records
                        WHERE player_id = ? AND is_active = TRUE
                        ORDER BY created_at ASC
                        LIMIT ?
                    """.trimIndent()
                    
                    val warningIds = mutableListOf<Int>()
                    
                    connection.prepareStatement(getOldestWarningsQuery).use { statement ->
                        statement.setInt(1, playerId)
                        statement.setInt(2, actualCount)
                        statement.executeQuery().use { resultSet ->
                            while (resultSet.next()) {
                                warningIds.add(resultSet.getInt("warning_id"))
                            }
                        }
                    }
                    
                    // 각 경고 차감 처리
                    val updateWarningsQuery = """
                        UPDATE warnings_records
                        SET is_active = FALSE,
                            pardoned_at = CURRENT_TIMESTAMP,
                            pardoned_by_uuid = ?,
                            pardoned_by_name = ?,
                            pardon_reason = ?
                        WHERE warning_id IN (${warningIds.joinToString(",")})
                    """.trimIndent()
                    
                    connection.prepareStatement(updateWarningsQuery).use { statement ->
                        statement.setString(1, adminUuid.toString())
                        statement.setString(2, adminName)
                        statement.setString(3, reason)
                        statement.executeUpdate()
                    }
                    
                    // 차감 이력 추가
                    val insertPardonQuery = """
                        INSERT INTO warnings_pardons
                        (player_id, admin_uuid, admin_name, count, reason, is_id_based)
                        VALUES (?, ?, ?, ?, ?, FALSE)
                    """.trimIndent()
                    
                    connection.prepareStatement(insertPardonQuery).use { statement ->
                        statement.setInt(1, playerId)
                        statement.setString(2, adminUuid.toString())
                        statement.setString(3, adminName)
                        statement.setInt(4, actualCount)
                        statement.setString(5, reason)
                        statement.executeUpdate()
                    }
                    
                    // 플레이어 활성 경고 수 갱신
                    val updatePlayerQuery = """
                        UPDATE warnings_players
                        SET active_warnings_count = active_warnings_count - ?,
                            last_warning_date = CURRENT_TIMESTAMP
                        WHERE player_id = ?
                    """.trimIndent()
                    
                    connection.prepareStatement(updatePlayerQuery).use { statement ->
                        statement.setInt(1, actualCount)
                        statement.setInt(2, playerId)
                        statement.executeUpdate()
                    }
                    
                    connection.commit()
                    return true
                } catch (e: Exception) {
                    connection.rollback()
                    logger.log(Level.SEVERE, "경고 횟수 차감 중 오류 발생", e)
                    return false
                } finally {
                    connection.autoCommit = true
                }
            }
        } catch (e: Exception) {
            logger.log(Level.SEVERE, "경고 횟수 차감 중 데이터베이스 연결 오류", e)
            return false
        }
    }

    /**
     * 플레이어의 모든 경고 내역 조회
     */
    fun getPlayerWarnings(playerUuid: UUID): List<WarningRecord> {
        val warnings = mutableListOf<WarningRecord>()
        
        try {
            database.getConnection().use { connection ->
                val player = getPlayerWarningByUuid(playerUuid) ?: return emptyList()
                val playerId = player.playerId ?: return emptyList()
                
                val query = """
                    SELECT * FROM warnings_records
                    WHERE player_id = ?
                    ORDER BY created_at DESC
                """.trimIndent()
                
                connection.prepareStatement(query).use { statement ->
                    statement.setInt(1, playerId)
                    statement.executeQuery().use { resultSet ->
                        while (resultSet.next()) {
                            warnings.add(resultSetToWarningRecord(resultSet))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logger.log(Level.SEVERE, "플레이어 경고 내역 조회 중 오류 발생", e)
        }
        
        return warnings
    }

    /**
     * 모든 경고를 받은 플레이어 목록 조회 (페이지네이션)
     */
    fun getWarnedPlayers(page: Int, playersPerPage: Int = 10): List<PlayerWarning> {
        val players = mutableListOf<PlayerWarning>()
        val offset = (page - 1) * playersPerPage
        
        try {
            database.getConnection().use { connection ->
                val query = """
                    SELECT * FROM warnings_players
                    ORDER BY last_warning_date DESC, active_warnings_count DESC
                    LIMIT ? OFFSET ?
                """.trimIndent()
                
                connection.prepareStatement(query).use { statement ->
                    statement.setInt(1, playersPerPage)
                    statement.setInt(2, offset)
                    statement.executeQuery().use { resultSet ->
                        while (resultSet.next()) {
                            players.add(resultSetToPlayerWarning(resultSet))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logger.log(Level.SEVERE, "경고 받은 플레이어 목록 조회 중 오류 발생", e)
        }
        
        return players
    }

    /**
     * 경고 받은 플레이어 총 수 조회
     */
    fun getWarnedPlayersCount(): Int {
        try {
            database.getConnection().use { connection ->
                val query = "SELECT COUNT(*) FROM warnings_players"
                
                connection.prepareStatement(query).use { statement ->
                    statement.executeQuery().use { resultSet ->
                        if (resultSet.next()) {
                            return resultSet.getInt(1)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logger.log(Level.SEVERE, "경고 받은 플레이어 수 조회 중 오류 발생", e)
        }
        
        return 0
    }

    /**
     * UUID로 플레이어 경고 정보 조회
     */
    private fun getPlayerWarningByUuid(uuid: UUID): PlayerWarning? {
        try {
            database.getConnection().use { connection ->
                val query = "SELECT * FROM warnings_players WHERE uuid = ?"
                
                connection.prepareStatement(query).use { statement ->
                    statement.setString(1, uuid.toString())
                    statement.executeQuery().use { resultSet ->
                        if (resultSet.next()) {
                            return resultSetToPlayerWarning(resultSet)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logger.log(Level.SEVERE, "플레이어 경고 정보 조회 중 오류 발생", e)
        }
        
        return null
    }

    /**
     * ResultSet을 PlayerWarning 객체로 변환
     */
    private fun resultSetToPlayerWarning(resultSet: ResultSet): PlayerWarning {
        val playerId = resultSet.getInt("player_id")
        val uuid = UUID.fromString(resultSet.getString("uuid"))
        val username = resultSet.getString("username")
        val lastWarningDateTimestamp = resultSet.getTimestamp("last_warning_date")
        val lastWarningDate = lastWarningDateTimestamp?.toLocalDateTime()
        val activeWarningsCount = resultSet.getInt("active_warnings_count")
        
        return PlayerWarning(
            playerId = playerId,
            uuid = uuid,
            username = username,
            lastWarningDate = lastWarningDate,
            activeWarningsCount = activeWarningsCount
        )
    }

    /**
     * ResultSet을 WarningRecord 객체로 변환
     */
    private fun resultSetToWarningRecord(resultSet: ResultSet): WarningRecord {
        val warningId = resultSet.getInt("warning_id")
        val playerId = resultSet.getInt("player_id")
        val adminUuid = UUID.fromString(resultSet.getString("admin_uuid"))
        val adminName = resultSet.getString("admin_name")
        val reason = resultSet.getString("reason")
        val createdAt = resultSet.getTimestamp("created_at").toLocalDateTime()
        val isActive = resultSet.getBoolean("is_active")
        
        val pardonedAtTimestamp = resultSet.getTimestamp("pardoned_at")
        val pardonedAt = pardonedAtTimestamp?.toLocalDateTime()
        
        val pardonedByUuidString = resultSet.getString("pardoned_by_uuid")
        val pardonedByUuid = if (pardonedByUuidString != null) UUID.fromString(pardonedByUuidString) else null
        
        val pardonedByName = resultSet.getString("pardoned_by_name")
        val pardonReason = resultSet.getString("pardon_reason")
        
        return WarningRecord(
            warningId = warningId,
            playerId = playerId,
            adminUuid = adminUuid,
            adminName = adminName,
            reason = reason,
            createdAt = createdAt,
            isActive = isActive,
            pardonedAt = pardonedAt,
            pardonedByUuid = pardonedByUuid,
            pardonedByName = pardonedByName,
            pardonReason = pardonReason
        )
    }
}
