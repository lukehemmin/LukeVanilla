package com.lukehemmin.lukeVanilla.System.MyLand

import com.lukehemmin.lukeVanilla.System.Database.Database
import com.lukehemmin.lukeVanilla.System.Debug.DebugManager
import org.bukkit.Chunk
import org.bukkit.entity.Player
import java.sql.Connection
import java.sql.SQLException
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock

/**
 * MyLand 시스템을 위한 원자적 클레임 서비스
 * Race Condition 방지를 위해 AtomicClaimService 패턴 적용
 */
class MyLandAtomicService(
    private val database: Database,
    private val debugManager: DebugManager
) {

    // 청크별 락 (메모리 효율적인 락 관리)
    private val chunkLocks = ConcurrentHashMap<String, ReentrantLock>()

    companion object {
        private const val TABLE_NAME = "myland_claims"
    }

    /**
     * Race Condition 없는 원자적 클레임
     */
    fun atomicClaimChunk(
        chunk: Chunk,
        player: Player,
        claimType: String,
        cache: MutableMap<String, MutableMap<Pair<Int, Int>, UUID>>,
        ownedChunks: MutableMap<UUID, MutableMap<String, MutableList<Pair<Int, Int>>>>
    ): ClaimResult {
        val worldName = chunk.world.name
        val chunkKey = "$worldName:${chunk.x}:${chunk.z}"
        val chunkLock = chunkLocks.computeIfAbsent(chunkKey) { ReentrantLock() }

        chunkLock.lock()
        return try {
            debugManager.log("MyLand", "[ATOMIC_CLAIM] 시작: $chunkKey by ${player.name}")

            // 원자적 데이터베이스 연산
            atomicClaimInDatabase(chunk, player, claimType, cache, ownedChunks)
        } catch (e: Exception) {
            debugManager.log("MyLand", "[ATOMIC_CLAIM_ERROR] $chunkKey: ${e.message}")
            ClaimResult.ALREADY_CLAIMED // 오류 발생 시 안전하게 실패 처리
        } finally {
            chunkLock.unlock()
            // 사용하지 않는 락 정리 (메모리 최적화)
            if (!chunkLock.hasQueuedThreads()) {
                chunkLocks.remove(chunkKey, chunkLock)
            }
        }
    }

    /**
     * 데이터베이스 레벨에서의 원자적 클레임
     * SELECT FOR UPDATE를 사용한 비관적 잠금
     */
    private fun atomicClaimInDatabase(
        chunk: Chunk,
        player: Player,
        claimType: String,
        cache: MutableMap<String, MutableMap<Pair<Int, Int>, UUID>>,
        ownedChunks: MutableMap<UUID, MutableMap<String, MutableList<Pair<Int, Int>>>>
    ): ClaimResult {
        val worldName = chunk.world.name
        val chunkX = chunk.x
        val chunkZ = chunk.z

        return database.getConnection().use { connection ->
            connection.autoCommit = false

            try {
                // 1단계: 기존 클레임 확인 (SELECT FOR UPDATE로 락 획득)
                val existingOwner = checkExistingClaimWithLock(connection, worldName, chunkX, chunkZ)

                if (existingOwner != null) {
                    connection.rollback()
                    debugManager.log("MyLand", "[ATOMIC_CLAIM] 이미 클레임됨: $worldName($chunkX,$chunkZ) by $existingOwner")
                    return ClaimResult.ALREADY_CLAIMED
                }

                // 2단계: 새로운 클레임 삽입
                val inserted = insertNewClaim(connection, worldName, chunkX, chunkZ, player.uniqueId, claimType)

                if (!inserted) {
                    connection.rollback()
                    return ClaimResult.ALREADY_CLAIMED
                }

                // 3단계: 트랜잭션 커밋
                connection.commit()

                // 4단계: 캐시 업데이트 (트랜잭션 성공 후)
                val chunkCoords = chunkX to chunkZ
                cache.computeIfAbsent(worldName) { mutableMapOf() }[chunkCoords] = player.uniqueId
                ownedChunks.computeIfAbsent(player.uniqueId) { mutableMapOf() }
                    .computeIfAbsent(worldName) { mutableListOf() }
                    .add(chunkCoords)

                debugManager.log("MyLand", "[ATOMIC_CLAIM_SUCCESS] $worldName($chunkX,$chunkZ) by ${player.name}")

                return ClaimResult.SUCCESS

            } catch (e: SQLException) {
                connection.rollback()
                debugManager.log("MyLand", "[ATOMIC_CLAIM_DB_ERROR] ${e.message}")
                throw e
            } catch (e: Exception) {
                connection.rollback()
                debugManager.log("MyLand", "[ATOMIC_CLAIM_GENERAL_ERROR] ${e.message}")
                throw e
            }
        }
    }

    /**
     * SELECT FOR UPDATE를 사용한 기존 클레임 확인
     * 해당 행에 배타적 락을 걸어 다른 트랜잭션의 접근 차단
     */
    private fun checkExistingClaimWithLock(
        connection: Connection,
        worldName: String,
        chunkX: Int,
        chunkZ: Int
    ): UUID? {
        val query = """
            SELECT owner_uuid
            FROM $TABLE_NAME
            WHERE world = ? AND chunk_x = ? AND chunk_z = ? AND resource_type IS NULL
            FOR UPDATE
        """.trimIndent()

        return connection.prepareStatement(query).use { statement ->
            statement.setString(1, worldName)
            statement.setInt(2, chunkX)
            statement.setInt(3, chunkZ)

            statement.executeQuery().use { resultSet ->
                if (resultSet.next()) {
                    UUID.fromString(resultSet.getString("owner_uuid"))
                } else null
            }
        }
    }

    /**
     * 새로운 클레임 정보를 데이터베이스에 삽입
     */
    private fun insertNewClaim(
        connection: Connection,
        worldName: String,
        chunkX: Int,
        chunkZ: Int,
        ownerUuid: UUID,
        claimType: String
    ): Boolean {
        val query = """
            INSERT INTO $TABLE_NAME
            (world, chunk_x, chunk_z, owner_uuid, claim_type)
            VALUES (?, ?, ?, ?, ?)
        """.trimIndent()

        return connection.prepareStatement(query).use { statement ->
            statement.setString(1, worldName)
            statement.setInt(2, chunkX)
            statement.setInt(3, chunkZ)
            statement.setString(4, ownerUuid.toString())
            statement.setString(5, claimType)

            statement.executeUpdate() > 0
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
