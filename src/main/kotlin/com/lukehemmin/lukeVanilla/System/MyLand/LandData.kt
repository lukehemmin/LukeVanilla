package com.lukehemmin.lukeVanilla.System.MyLand

import com.lukehemmin.lukeVanilla.System.Database.Database
import java.sql.ResultSet
import java.sql.Timestamp
import java.util.UUID
import java.util.logging.Logger

data class ClaimInfo(val ownerUuid: UUID, val claimedAt: Timestamp, val claimType: String)
data class ClaimHistory(val previousOwnerUuid: UUID, val actorUuid: UUID?, val reason: String, val unclaimedAt: Timestamp)

class LandData(private val database: Database) {
    
    private val logger = Logger.getLogger(LandData::class.java.name)

    // ===== 공통 데이터베이스 유틸리티 메서드 =====
    
    /**
     * 단일 결과를 반환하는 쿼리 실행
     */
    private fun <T> executeQuerySingle(query: String, params: List<Any?> = emptyList(), mapper: (ResultSet) -> T): T? {
        return try {
            database.getConnection().use { connection ->
                connection.prepareStatement(query).use { statement ->
                    params.forEachIndexed { index, param ->
                        statement.setObject(index + 1, param)
                    }
                    statement.executeQuery().use { resultSet ->
                        if (resultSet.next()) mapper(resultSet) else null
                    }
                }
            }
        } catch (e: Exception) {
            logger.severe("[DB_ERROR] 쿼리 실행 실패: $query - ${e.message}")
            null
        }
    }
    
    /**
     * 업데이트/삭제 쿼리 실행
     */
    private fun executeUpdate(query: String, params: List<Any?> = emptyList()): Boolean {
        return try {
            database.getConnection().use { connection ->
                connection.prepareStatement(query).use { statement ->
                    params.forEachIndexed { index, param ->
                        statement.setObject(index + 1, param)
                    }
                    statement.executeUpdate() > 0
                }
            }
        } catch (e: Exception) {
            logger.severe("[DB_ERROR] 업데이트 쿼리 실행 실패: $query - ${e.message}")
            false
        }
    }
    
    /**
     * 여러 결과를 반환하는 쿼리 실행
     */
    private fun <T> executeQueryList(query: String, params: List<Any?> = emptyList(), mapper: (ResultSet) -> T): List<T> {
        val results = mutableListOf<T>()
        try {
            database.getConnection().use { connection ->
                connection.prepareStatement(query).use { statement ->
                    params.forEachIndexed { index, param ->
                        statement.setObject(index + 1, param)
                    }
                    statement.executeQuery().use { resultSet ->
                        while (resultSet.next()) {
                            results.add(mapper(resultSet))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logger.severe("[DB_ERROR] 리스트 쿼리 실행 실패: $query - ${e.message}")
        }
        return results
    }

    /**
     * 데이터베이스에서 기존 MyLand 시스템의 소유된 청크 정보만 불러옵니다.
     * AdvancedLandClaiming 시스템과 구분하기 위해 resource_type IS NULL 조건 사용
     * @return Map<월드 이름, Map<청크 좌표, 소유자 UUID>>
     */
    fun loadAllClaims(): MutableMap<String, MutableMap<Pair<Int, Int>, UUID>> {
        logger.info("[MYLAND] 모든 클레이밍 정보 로딩 시작")
        
        val claims = mutableMapOf<String, MutableMap<Pair<Int, Int>, UUID>>()
        val query = "SELECT world, chunk_x, chunk_z, owner_uuid FROM myland_claims WHERE resource_type IS NULL"
        
        val results = executeQueryList(query) { resultSet ->
            Triple(
                resultSet.getString("world"),
                Pair(resultSet.getInt("chunk_x"), resultSet.getInt("chunk_z")),
                UUID.fromString(resultSet.getString("owner_uuid"))
            )
        }
        
        results.forEach { (worldName, chunkCoord, ownerUUID) ->
            claims.computeIfAbsent(worldName) { mutableMapOf() }[chunkCoord] = ownerUUID
        }
        
        logger.info("[MYLAND] 클레이밍 정보 로딩 완료: ${results.size}개 청크")
        return claims
    }

    /**
     * 특정 청크의 소유권을 데이터베이스에 저장합니다.
     */
    fun saveClaim(worldName: String, chunkX: Int, chunkZ: Int, owner: UUID, claimType: String) {
        logger.info("[MYLAND] 청크 클레이밍 시도: $worldName($chunkX,$chunkZ) by ${owner}")
        
        val query = """
            INSERT INTO myland_claims (world, chunk_x, chunk_z, owner_uuid, claim_type) 
            VALUES (?, ?, ?, ?, ?) 
            ON DUPLICATE KEY UPDATE owner_uuid = ?, claim_type = ?, claimed_at = CURRENT_TIMESTAMP
        """.trimIndent()
        
        val success = executeUpdate(query, listOf(
            worldName, chunkX, chunkZ, owner.toString(), claimType,
            owner.toString(), claimType
        ))
        
        if (success) {
            logger.info("[MYLAND] 클레이밍 성공: $worldName($chunkX,$chunkZ)")
        } else {
            logger.severe("[MYLAND] 클레이밍 실패: $worldName($chunkX,$chunkZ)")
        }
    }

    /**
     * 특정 청크의 소유권을 데이터베이스에서 삭제합니다. (MyLand 시스템만)
     */
    fun deleteClaim(worldName: String, chunkX: Int, chunkZ: Int) {
        logger.info("[MYLAND] 청크 반환 시도: $worldName($chunkX,$chunkZ)")
        
        val query = "DELETE FROM myland_claims WHERE world = ? AND chunk_x = ? AND chunk_z = ? AND resource_type IS NULL"
        val success = executeUpdate(query, listOf(worldName, chunkX, chunkZ))
        
        if (success) {
            logger.info("[MYLAND] 청크 반환 성공: $worldName($chunkX,$chunkZ)")
        } else {
            logger.warning("[MYLAND] 청크 반환 실패 (존재하지 않음): $worldName($chunkX,$chunkZ)")
        }
    }

    /**
     * 특정 청크의 소유 정보를 불러옵니다. (MyLand 시스템만)
     */
    fun getClaimInfo(worldName: String, chunkX: Int, chunkZ: Int): ClaimInfo? {
        val query = "SELECT owner_uuid, claimed_at, claim_type FROM myland_claims WHERE world = ? AND chunk_x = ? AND chunk_z = ? AND resource_type IS NULL"
        
        return executeQuerySingle(query, listOf(worldName, chunkX, chunkZ)) { resultSet ->
            ClaimInfo(
                ownerUuid = UUID.fromString(resultSet.getString("owner_uuid")),
                claimedAt = resultSet.getTimestamp("claimed_at"),
                claimType = resultSet.getString("claim_type")
            )
        }
    }
    
    /**
     * 특정 청크의 멤버 목록을 조회합니다. (MyLand/AdvancedLandClaiming 공통 사용)
     */
    fun getMembers(worldName: String, chunkX: Int, chunkZ: Int): List<UUID> {
        val query = "SELECT member_uuid FROM myland_members WHERE world = ? AND chunk_x = ? AND chunk_z = ?"
        
        return executeQueryList(query, listOf(worldName, chunkX, chunkZ)) { resultSet ->
            UUID.fromString(resultSet.getString("member_uuid"))
        }
    }

    /**
     * 특정 청크에 멤버를 추가합니다. (MyLand/AdvancedLandClaiming 공통 사용)
     */
    fun addMember(worldName: String, chunkX: Int, chunkZ: Int, memberUuid: UUID): Boolean {
        logger.info("[MYLAND] 멤버 추가 시도: $worldName($chunkX,$chunkZ) - $memberUuid")
        
        val query = "INSERT IGNORE INTO myland_members (world, chunk_x, chunk_z, member_uuid) VALUES (?, ?, ?, ?)"
        val success = executeUpdate(query, listOf(worldName, chunkX, chunkZ, memberUuid.toString()))
        
        if (success) {
            logger.info("[MYLAND] 멤버 추가 성공: $memberUuid")
        } else {
            logger.warning("[MYLAND] 멤버 추가 실패 (이미 존재): $memberUuid")
        }
        
        return success
    }
    
    /**
     * 특정 청크에서 멤버를 제거합니다. (MyLand/AdvancedLandClaiming 공통 사용)
     */
    fun removeMember(worldName: String, chunkX: Int, chunkZ: Int, memberUuid: UUID): Boolean {
        logger.info("[MYLAND] 멤버 제거 시도: $worldName($chunkX,$chunkZ) - $memberUuid")
        
        val query = "DELETE FROM myland_members WHERE world = ? AND chunk_x = ? AND chunk_z = ? AND member_uuid = ?"
        val success = executeUpdate(query, listOf(worldName, chunkX, chunkZ, memberUuid.toString()))
        
        if (success) {
            logger.info("[MYLAND] 멤버 제거 성공: $memberUuid")
        } else {
            logger.warning("[MYLAND] 멤버 제거 실패 (존재하지 않음): $memberUuid")
        }
        
        return success
    }

    /**
     * 토지 소유권 해제 시 관련 멤버 정보도 모두 삭제합니다. (MyLand/AdvancedLandClaiming 공통 사용)
     */
    fun deleteAllMembers(worldName: String, chunkX: Int, chunkZ: Int) {
        logger.info("[MYLAND] 모든 멤버 삭제 시도: $worldName($chunkX,$chunkZ)")
        
        val query = "DELETE FROM myland_members WHERE world = ? AND chunk_x = ? AND chunk_z = ?"
        val success = executeUpdate(query, listOf(worldName, chunkX, chunkZ))
        
        if (success) {
            logger.info("[MYLAND] 모든 멤버 삭제 성공: $worldName($chunkX,$chunkZ)")
        }
    }

    /**
     * 토지 소유권 해제 이력을 기록합니다.
     */
    fun logClaimHistory(worldName: String, chunkX: Int, chunkZ: Int, previousOwner: UUID, actor: UUID?, reason: String) {
        logger.info("[MYLAND] 소유권 이력 기록: $worldName($chunkX,$chunkZ) - $reason")
        
        val query = "INSERT INTO myland_claim_history (world, chunk_x, chunk_z, previous_owner_uuid, actor_uuid, reason) VALUES (?, ?, ?, ?, ?, ?)"
        val success = executeUpdate(query, listOf(
            worldName, chunkX, chunkZ, 
            previousOwner.toString(), 
            actor?.toString(), 
            reason
        ))
        
        if (success) {
            logger.info("[MYLAND] 이력 기록 성공: $reason")
        } else {
            logger.severe("[MYLAND] 이력 기록 실패: $reason")
        }
    }

    /**
     * 특정 청크의 소유권 해제 이력 전체를 불러옵니다.
     */
    fun getClaimHistory(worldName: String, chunkX: Int, chunkZ: Int): List<ClaimHistory> {
        logger.info("[MYLAND] 소유권 해제 이력 조회: $worldName($chunkX,$chunkZ)")
        
        val query = "SELECT previous_owner_uuid, actor_uuid, reason, unclaimed_at FROM myland_claim_history WHERE world = ? AND chunk_x = ? AND chunk_z = ? ORDER BY unclaimed_at DESC"
        
        val historyList = executeQueryList(query, listOf(worldName, chunkX, chunkZ)) { resultSet ->
            ClaimHistory(
                previousOwnerUuid = UUID.fromString(resultSet.getString("previous_owner_uuid")),
                actorUuid = resultSet.getString("actor_uuid")?.let { UUID.fromString(it) },
                reason = resultSet.getString("reason"),
                unclaimedAt = resultSet.getTimestamp("unclaimed_at")
            )
        }
        
        logger.info("[MYLAND] 이력 조회 완료: ${historyList.size}개 이력")
        return historyList
    }
} 