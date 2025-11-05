package com.lukehemmin.lukeVanilla.System.AdvancedLandClaiming.Service

import com.lukehemmin.lukeVanilla.System.AdvancedLandClaiming.Models.*
import com.lukehemmin.lukeVanilla.System.AdvancedLandClaiming.Cache.AdvancedLandCache
import com.lukehemmin.lukeVanilla.System.Database.Database
import com.lukehemmin.lukeVanilla.System.Debug.DebugManager
import com.lukehemmin.lukeVanilla.System.AdvancedLandClaiming.ClaimResult
import org.bukkit.Chunk
import org.bukkit.entity.Player
import java.sql.Connection
import java.sql.SQLException
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock

/**
 * Race Condition을 방지하는 원자적 클레이밍 서비스
 *
 * 핵심 개선사항:
 * - Database-level 원자적 연산으로 중복 클레이밍 방지
 * - SELECT FOR UPDATE를 통한 비관적 잠금
 * - Chunk-level 락으로 동시성 제어
 * - 트랜잭션 롤백으로 일관성 보장
 */
class AtomicClaimService(
    private val database: Database,
    private val cache: AdvancedLandCache,
    private val debugManager: DebugManager
) {

    // 청크별 락 (메모리 효율적인 락 관리)
    private val chunkLocks = ConcurrentHashMap<String, ReentrantLock>()

    companion object {
        private const val TABLE_NAME = "myland_claims"
    }

    /**
     * 🚀 핵심 개선: Race Condition 없는 원자적 클레이밍
     *
     * 기존 문제:
     * 1. isChunkClaimed() 체크
     * 2. (다른 스레드가 여기서 클레이밍 가능)
     * 3. 캐시/DB 업데이트
     *
     * 해결:
     * - Database-level SELECT FOR UPDATE
     * - 체크와 삽입을 하나의 트랜잭션으로 처리
     */
    fun atomicClaimChunk(
        player: Player,
        chunk: Chunk,
        claimCost: ClaimCost?,
        claimType: ClaimType = ClaimType.PERSONAL,
        villageId: Int? = null
    ): ClaimResult {
        val worldName = chunk.world.name
        val chunkKey = "$worldName:${chunk.x}:${chunk.z}"

        // 청크별 락 획득 (메모리 효율적)
        val chunkLock = chunkLocks.computeIfAbsent(chunkKey) { ReentrantLock() }

        return chunkLock.lock().let {
            try {
                debugManager.log("AtomicClaimService", "[ATOMIC_CLAIM] 시작: $chunkKey by ${player.name}")

                // 원자적 데이터베이스 연산
                atomicClaimInDatabase(player, chunk, claimCost, claimType, villageId)
            } catch (e: Exception) {
                debugManager.log("AtomicClaimService", "[ATOMIC_CLAIM_ERROR] $chunkKey: ${e.message}")
                ClaimResult(false, "클레이밍 중 오류가 발생했습니다: ${e.message}")
            } finally {
                chunkLock.unlock()
                // 사용하지 않는 락 정리 (메모리 최적화)
                if (!chunkLock.hasQueuedThreads()) {
                    chunkLocks.remove(chunkKey, chunkLock)
                }
            }
        }
    }

    /**
     * 데이터베이스 레벨에서의 원자적 클레이밍
     * SELECT FOR UPDATE를 사용한 비관적 잠금
     */
    private fun atomicClaimInDatabase(
        player: Player,
        chunk: Chunk,
        claimCost: ClaimCost?,
        claimType: ClaimType,
        villageId: Int?
    ): ClaimResult {
        val worldName = chunk.world.name
        val chunkX = chunk.x
        val chunkZ = chunk.z

        return database.getConnection().use { connection ->
            connection.autoCommit = false

            try {
                // 1단계: 기존 클레이밍 확인 (SELECT FOR UPDATE로 락 획득)
                val existingClaim = checkExistingClaimWithLock(connection, worldName, chunkX, chunkZ)

                if (existingClaim != null) {
                    connection.rollback()
                    return ClaimResult(
                        false,
                        "이 청크는 이미 ${existingClaim.ownerName}가 소유하고 있습니다.",
                        existingClaim
                    )
                }

                // 2단계: 새로운 클레이밍 삽입
                val claimInfo = AdvancedClaimInfo(
                    chunkX = chunkX,
                    chunkZ = chunkZ,
                    worldName = worldName,
                    ownerUuid = player.uniqueId,
                    ownerName = player.name,
                    claimType = claimType,
                    createdAt = System.currentTimeMillis(),
                    lastUpdated = System.currentTimeMillis(),
                    villageId = villageId,
                    claimCost = claimCost
                )

                val inserted = insertNewClaim(connection, claimInfo)

                if (!inserted) {
                    connection.rollback()
                    return ClaimResult(false, "데이터베이스 삽입에 실패했습니다.")
                }

                // 3단계: 트랜잭션 커밋
                connection.commit()

                // 4단계: 캐시 업데이트 (트랜잭션 성공 후)
                cache.addClaim(claimInfo)

                debugManager.log("AtomicClaimService", "[ATOMIC_CLAIM_SUCCESS] $worldName($chunkX,$chunkZ) by ${player.name}")

                return ClaimResult(true, "청크를 성공적으로 클레이밍했습니다!", claimInfo)

            } catch (e: SQLException) {
                connection.rollback()
                debugManager.log("AtomicClaimService", "[ATOMIC_CLAIM_DB_ERROR] ${e.message}")
                throw e
            } catch (e: Exception) {
                connection.rollback()
                debugManager.log("AtomicClaimService", "[ATOMIC_CLAIM_GENERAL_ERROR] ${e.message}")
                throw e
            }
        }
    }

    /**
     * SELECT FOR UPDATE를 사용한 기존 클레이밍 확인
     * 해당 행에 배타적 락을 걸어 다른 트랜잭션의 접근 차단
     */
    private fun checkExistingClaimWithLock(
        connection: Connection,
        worldName: String,
        chunkX: Int,
        chunkZ: Int
    ): AdvancedClaimInfo? {
        val query = """
            SELECT world, chunk_x, chunk_z, owner_uuid, owner_name, claim_type,
                   resource_type, resource_amount, used_free_slots, village_id,
                   UNIX_TIMESTAMP(claimed_at) as claimed_at,
                   UNIX_TIMESTAMP(last_updated) as last_updated
            FROM $TABLE_NAME
            WHERE world = ? AND chunk_x = ? AND chunk_z = ?
            FOR UPDATE
        """.trimIndent()

        return connection.prepareStatement(query).use { statement ->
            statement.setString(1, worldName)
            statement.setInt(2, chunkX)
            statement.setInt(3, chunkZ)

            statement.executeQuery().use { resultSet ->
                if (resultSet.next()) {
                    val ownerUuid = UUID.fromString(resultSet.getString("owner_uuid"))
                    val ownerName = resultSet.getString("owner_name")
                    val claimTypeStr = resultSet.getString("claim_type")
                    val resourceTypeStr = resultSet.getString("resource_type")
                    val resourceAmount = resultSet.getInt("resource_amount")
                    val usedFreeSlots = resultSet.getInt("used_free_slots")
                    val villageId = resultSet.getObject("village_id") as? Int
                    val createdAt = resultSet.getLong("claimed_at") * 1000
                    val lastUpdated = resultSet.getLong("last_updated") * 1000

                    val claimType = ClaimType.valueOf(claimTypeStr)
                    val resourceType = if (resourceTypeStr != null) {
                        ClaimResourceType.valueOf(resourceTypeStr)
                    } else ClaimResourceType.FREE

                    val claimCost = if (resourceType != ClaimResourceType.FREE) {
                        ClaimCost(resourceType, resourceAmount, usedFreeSlots)
                    } else null

                    AdvancedClaimInfo(
                        chunkX, chunkZ, worldName, ownerUuid, ownerName,
                        claimType, createdAt, lastUpdated, villageId, claimCost
                    )
                } else null
            }
        }
    }

    /**
     * 새로운 클레이밍 정보를 데이터베이스에 삽입
     */
    private fun insertNewClaim(connection: Connection, claimInfo: AdvancedClaimInfo): Boolean {
        val query = """
            INSERT INTO $TABLE_NAME
            (world, chunk_x, chunk_z, owner_uuid, owner_name, claim_type,
             resource_type, resource_amount, used_free_slots, village_id, playtime_days)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
        """.trimIndent()

        return connection.prepareStatement(query).use { statement ->
            val resourceType = claimInfo.claimCost?.resourceType ?: ClaimResourceType.FREE
            val resourceAmount = claimInfo.claimCost?.amount ?: 0
            val usedFreeSlots = claimInfo.claimCost?.usedFreeSlots ?: 0

            statement.setString(1, claimInfo.worldName)
            statement.setInt(2, claimInfo.chunkX)
            statement.setInt(3, claimInfo.chunkZ)
            statement.setString(4, claimInfo.ownerUuid.toString())
            statement.setString(5, claimInfo.ownerName)
            statement.setString(6, claimInfo.claimType.name)
            statement.setString(7, resourceType.name)
            statement.setInt(8, resourceAmount)
            statement.setInt(9, usedFreeSlots)
            statement.setObject(10, claimInfo.villageId)

            statement.executeUpdate() > 0
        }
    }

    /**
     * 🚀 원자적 클레이밍 해제 (Race Condition 방지)
     */
    fun atomicUnclaimChunk(
        actor: Player?,
        chunk: Chunk,
        reason: String
    ): ClaimResult {
        val worldName = chunk.world.name
        val chunkKey = "$worldName:${chunk.x}:${chunk.z}"
        val chunkLock = chunkLocks.computeIfAbsent(chunkKey) { ReentrantLock() }

        return chunkLock.lock().let {
            try {
                database.getConnection().use { connection ->
                    connection.autoCommit = false

                    try {
                        // 1. 기존 클레이밍 확인 및 락
                        val existingClaim = checkExistingClaimWithLock(connection, worldName, chunk.x, chunk.z)

                        if (existingClaim == null) {
                            connection.rollback()
                            return ClaimResult(false, "이 청크는 클레이밍되지 않았습니다.")
                        }

                        // 2. 권한 확인
                        if (actor != null && existingClaim.ownerUuid != actor.uniqueId && !actor.hasPermission("advancedland.admin.unclaim")) {
                            connection.rollback()
                            return ClaimResult(false, "본인의 청크만 포기할 수 있습니다.")
                        }

                        // 3. 히스토리 기록
                        val historyQuery = """
                            INSERT INTO myland_claim_history
                            (world, chunk_x, chunk_z, previous_owner_uuid, actor_uuid, reason)
                            VALUES (?, ?, ?, ?, ?, ?)
                        """.trimIndent()

                        connection.prepareStatement(historyQuery).use { statement ->
                            statement.setString(1, worldName)
                            statement.setInt(2, chunk.x)
                            statement.setInt(3, chunk.z)
                            statement.setString(4, existingClaim.ownerUuid.toString())
                            statement.setString(5, actor?.uniqueId?.toString())
                            statement.setString(6, reason)
                            statement.executeUpdate()
                        }

                        // 4. 클레이밍 삭제
                        val deleteQuery = "DELETE FROM $TABLE_NAME WHERE world = ? AND chunk_x = ? AND chunk_z = ?"
                        connection.prepareStatement(deleteQuery).use { statement ->
                            statement.setString(1, worldName)
                            statement.setInt(2, chunk.x)
                            statement.setInt(3, chunk.z)
                            statement.executeUpdate()
                        }

                        // 5. 커밋
                        connection.commit()

                        // 6. 캐시에서 제거
                        cache.removeClaim(worldName, chunk.x, chunk.z)

                        debugManager.log("AtomicClaimService", "[ATOMIC_UNCLAIM_SUCCESS] $worldName(${chunk.x},${chunk.z}) by ${actor?.name ?: "System"}")

                        ClaimResult(true, "청크 클레이밍을 성공적으로 포기했습니다.", existingClaim)

                    } catch (e: Exception) {
                        connection.rollback()
                        throw e
                    }
                }
            } finally {
                chunkLock.unlock()
                if (!chunkLock.hasQueuedThreads()) {
                    chunkLocks.remove(chunkKey, chunkLock)
                }
            }
        }
    }

    /**
     * 캐시와 데이터베이스 동기화 확인
     */
    fun validateCacheConsistency(worldName: String, chunkX: Int, chunkZ: Int): Boolean {
        try {
            val cacheData = cache.getClaimOwner(worldName, chunkX, chunkZ)

            val dbData = database.getConnection().use { connection ->
                checkExistingClaimWithLock(connection, worldName, chunkX, chunkZ)
            }

            return (cacheData == null && dbData == null) ||
                   (cacheData != null && dbData != null && cacheData.ownerUuid == dbData.ownerUuid)
        } catch (e: Exception) {
            debugManager.log("AtomicClaimService", "[CONSISTENCY_CHECK_ERROR] $worldName($chunkX,$chunkZ): ${e.message}")
            return false
        }
    }

    /**
     * 락 통계 정보
     */
    fun getLockStats(): Map<String, Any> {
        return mapOf(
            "activeLocks" to chunkLocks.size,
            "lockList" to chunkLocks.keys.toList()
        )
    }
}

