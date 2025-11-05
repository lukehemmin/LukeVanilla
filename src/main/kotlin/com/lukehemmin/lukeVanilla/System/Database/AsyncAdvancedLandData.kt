package com.lukehemmin.lukeVanilla.System.Database

import com.lukehemmin.lukeVanilla.System.AdvancedLandClaiming.Models.*
import com.lukehemmin.lukeVanilla.Main
import java.util.*
import java.util.concurrent.CompletableFuture

/**
 * 비동기 고급 토지 데이터 관리 클래스
 * AsyncDatabaseManager를 사용하여 서버 멈춤 및 DB 과부하 방지
 */
class AsyncAdvancedLandData(
    private val plugin: Main,
    private val asyncDbManager: AsyncDatabaseManager
) {

    companion object {
        private const val TABLE_NAME = "myland_claims"
        private const val ADVANCED_FILTER = "resource_type IS NOT NULL"
        private const val PERSONAL_CLAIMS_FILTER = "$ADVANCED_FILTER AND claim_type = 'PERSONAL'"

        // 배치 처리 최적화 설정
        private const val DEFAULT_BATCH_SIZE = 50        // 기본 배치 크기
        private const val MIN_BATCH_SIZE = 10           // 최소 배치 크기
        private const val MAX_BATCH_SIZE = 100          // 최대 배치 크기
    }

    /**
     * 마을 해체 시 모든 땅을 개인 토지로 변환 (비동기 배치 처리)
     */
    fun convertVillageToPersonalLandsAsync(
        villageId: Int,
        newOwnerUuid: UUID,
        newOwnerName: String,
        onProgress: (Int, Int) -> Unit = { _, _ -> },
        onComplete: (Boolean) -> Unit = {},
        onError: (Exception) -> Unit = {}
    ): CompletableFuture<Boolean> {

        // 1단계: 마을 땅 목록 조회
        return getVillageLandsAsync(villageId)
            .thenCompose { villageLands ->
                if (villageLands.isEmpty()) {
                    onComplete(true)
                    return@thenCompose CompletableFuture.completedFuture(true)
                }

                plugin.logger.info("[AsyncAdvancedLandData] 마을 $villageId 의 ${villageLands.size}개 땅을 개인 토지로 변환 시작")

                // 2단계: 배치로 개인 토지로 변환
                val batchQueries = villageLands.map { land ->
                    val query = """
                        UPDATE $TABLE_NAME
                        SET claim_type = 'PERSONAL', village_id = NULL, owner_uuid = ?, owner_name = ?
                        WHERE world = ? AND chunk_x = ? AND chunk_z = ?
                    """.trimIndent()

                    val params = listOf(
                        newOwnerUuid.toString(),
                        newOwnerName,
                        land.worldName,
                        land.chunkX,
                        land.chunkZ
                    )

                    query to params
                }

                // 동적 배치 크기 계산 (총 아이템 수에 따라 최적화)
                val batchSize = calculateOptimalBatchSize(villageLands.size)
                plugin.logger.info("[AsyncAdvancedLandData] 최적화된 배치 크기: $batchSize (총 ${villageLands.size}개 항목)")
                val batches = batchQueries.chunked(batchSize)
                var completedBatches = 0

                // 순차적으로 배치 실행
                batches.fold(CompletableFuture.completedFuture(true)) { future, batch ->
                    future.thenCompose { success ->
                        if (!success) {
                            CompletableFuture.completedFuture(false)
                        } else {
                            asyncDbManager.executeBatchAsync(
                                batch,
                                onSuccess = { results ->
                                    completedBatches++
                                    onProgress(completedBatches * batchSize, villageLands.size)
                                    plugin.logger.info("[AsyncAdvancedLandData] 배치 $completedBatches/${batches.size} 완료")
                                },
                                onFailure = { e ->
                                    plugin.logger.severe("[AsyncAdvancedLandData] 배치 처리 실패: ${e.message}")
                                    onError(e)
                                }
                            ).thenApply { results -> results.all { it } }
                        }
                    }
                }
            }
            .whenComplete { success, throwable ->
                if (throwable != null) {
                    plugin.logger.severe("[AsyncAdvancedLandData] 마을 땅 변환 중 오류: ${throwable.message}")
                    onError(Exception(throwable))
                } else {
                    plugin.logger.info("[AsyncAdvancedLandData] 마을 $villageId 땅 변환 완료: $success")
                    onComplete(success ?: false)
                }
            }
    }

    /**
     * 마을의 모든 땅 조회
     */
    private fun getVillageLandsAsync(villageId: Int): CompletableFuture<List<AdvancedClaimInfo>> {
        val query = """
            SELECT world, chunk_x, chunk_z, owner_uuid, owner_name, claim_type,
                   resource_type, resource_amount, used_free_slots,
                   UNIX_TIMESTAMP(claimed_at) as claimed_at,
                   UNIX_TIMESTAMP(last_updated) as last_updated
            FROM $TABLE_NAME
            WHERE $ADVANCED_FILTER AND village_id = ?
        """.trimIndent()

        return asyncDbManager.executeQueryAsync(
            query = query,
            params = listOf(villageId),
            cacheKey = "village_lands_$villageId",
            mapper = { resultSet ->
                val lands = mutableListOf<AdvancedClaimInfo>()
                do {
                    val worldName = resultSet.getString("world")
                    val chunkX = resultSet.getInt("chunk_x")
                    val chunkZ = resultSet.getInt("chunk_z")
                    val ownerUuid = UUID.fromString(resultSet.getString("owner_uuid"))
                    val ownerName = resultSet.getString("owner_name")
                    val claimType = ClaimType.valueOf(resultSet.getString("claim_type"))
                    val resourceType = ClaimResourceType.valueOf(resultSet.getString("resource_type"))
                    val resourceAmount = resultSet.getInt("resource_amount")
                    val usedFreeSlots = resultSet.getInt("used_free_slots")
                    val createdAt = resultSet.getLong("claimed_at") * 1000
                    val lastUpdated = resultSet.getLong("last_updated") * 1000

                    val claimCost = if (resourceType != ClaimResourceType.FREE) {
                        ClaimCost(resourceType, resourceAmount, usedFreeSlots)
                    } else null

                    lands.add(AdvancedClaimInfo(
                        chunkX, chunkZ, worldName, ownerUuid, ownerName,
                        claimType, createdAt, lastUpdated, villageId, claimCost
                    ))
                } while (resultSet.next())
                lands
            }
        ).thenApply { it ?: emptyList() }
    }

    /**
     * 마을 정보 비동기 조회
     */
    fun getVillageInfoAsync(
        villageId: Int,
        onSuccess: (VillageInfo?) -> Unit = {},
        onFailure: (Exception) -> Unit = {}
    ): CompletableFuture<VillageInfo?> {

        val query = """
            SELECT village_id, village_name, mayor_uuid, mayor_name,
                   UNIX_TIMESTAMP(created_at) as created_at,
                   UNIX_TIMESTAMP(last_updated) as last_updated,
                   is_active
            FROM villages
            WHERE village_id = ? AND is_active = TRUE
        """.trimIndent()

        return asyncDbManager.executeQueryAsync(
            query = query,
            params = listOf(villageId),
            cacheKey = "village_info_$villageId",
            mapper = { resultSet ->
                VillageInfo(
                    resultSet.getInt("village_id"),
                    resultSet.getString("village_name"),
                    UUID.fromString(resultSet.getString("mayor_uuid")),
                    resultSet.getString("mayor_name"),
                    resultSet.getLong("created_at") * 1000,
                    resultSet.getLong("last_updated") * 1000,
                    resultSet.getBoolean("is_active")
                )
            },
            onSuccess = onSuccess,
            onFailure = onFailure
        )
    }

    /**
     * 마을 비활성화 (비동기)
     */
    fun deactivateVillageAsync(
        villageId: Int,
        onSuccess: (Boolean) -> Unit = {},
        onFailure: (Exception) -> Unit = {}
    ): CompletableFuture<Boolean> {

        val query = "UPDATE villages SET is_active = FALSE WHERE village_id = ?"

        return asyncDbManager.executeUpdateAsync(
            query = query,
            params = listOf(villageId),
            onSuccess = { success ->
                if (success) {
                    // 캐시 무효화
                    plugin.logger.info("[AsyncAdvancedLandData] 마을 $villageId 비활성화 완료")
                }
                onSuccess(success)
            },
            onFailure = { e ->
                plugin.logger.severe("[AsyncAdvancedLandData] 마을 $villageId 비활성화 실패: ${e.message}")
                onFailure(e)
            }
        )
    }

    /**
     * 플레이어의 클레이밍 수 조회 (비동기)
     */
    fun getPlayerClaimCountAsync(
        playerUuid: UUID,
        onSuccess: (Int) -> Unit = {},
        onFailure: (Exception) -> Unit = {}
    ): CompletableFuture<Int> {

        val query = "SELECT COUNT(*) as count FROM $TABLE_NAME WHERE $PERSONAL_CLAIMS_FILTER AND owner_uuid = ?"

        return asyncDbManager.executeQueryAsync(
            query = query,
            params = listOf(playerUuid.toString()),
            cacheKey = "player_claim_count_${playerUuid}",
            mapper = { resultSet -> resultSet.getInt("count") },
            onSuccess = { result -> onSuccess(result ?: 0) },
            onFailure = onFailure
        ).thenApply { it ?: 0 }
    }

    /**
     * 새로운 클레이밍 저장 (비동기)
     */
    fun saveClaimAsync(
        claimInfo: AdvancedClaimInfo,
        onSuccess: (Boolean) -> Unit = {},
        onFailure: (Exception) -> Unit = {}
    ): CompletableFuture<Boolean> {

        val query = """
            INSERT INTO myland_claims
            (world, chunk_x, chunk_z, owner_uuid, owner_name, claim_type,
             resource_type, resource_amount, used_free_slots, village_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
            owner_uuid = ?, owner_name = ?, claim_type = ?,
            resource_type = ?, resource_amount = ?, used_free_slots = ?, village_id = ?,
            last_updated = CURRENT_TIMESTAMP
        """.trimIndent()

        val resourceType = claimInfo.claimCost?.resourceType ?: ClaimResourceType.FREE
        val resourceAmount = claimInfo.claimCost?.amount ?: 0
        val usedFreeSlots = claimInfo.claimCost?.usedFreeSlots ?: 0

        val params = listOf(
            claimInfo.worldName, claimInfo.chunkX, claimInfo.chunkZ,
            claimInfo.ownerUuid.toString(), claimInfo.ownerName, claimInfo.claimType.name,
            resourceType.name, resourceAmount, usedFreeSlots, claimInfo.villageId ?: null,
            // ON DUPLICATE KEY UPDATE 부분
            claimInfo.ownerUuid.toString(), claimInfo.ownerName, claimInfo.claimType.name,
            resourceType.name, resourceAmount, usedFreeSlots, claimInfo.villageId ?: null
        )

        return asyncDbManager.executeUpdateAsync(
            query = query,
            params = params,
            onSuccess = { success ->
                if (success) {
                    // 관련 캐시 무효화
                    plugin.logger.fine("[AsyncAdvancedLandData] 클레이밍 저장 완료: ${claimInfo.worldName}(${claimInfo.chunkX}, ${claimInfo.chunkZ})")
                }
                onSuccess(success)
            },
            onFailure = { e ->
                plugin.logger.severe("[AsyncAdvancedLandData] 클레이밍 저장 실패: ${e.message}")
                onFailure(e)
            }
        )
    }

    /**
     * 동적 배치 크기 계산
     * @param totalItems 처리할 총 항목 수
     * @return 최적화된 배치 크기
     */
    private fun calculateOptimalBatchSize(totalItems: Int): Int {
        return when {
            totalItems <= 50 -> MIN_BATCH_SIZE                    // 적은 수: 작은 배치
            totalItems <= 200 -> DEFAULT_BATCH_SIZE               // 보통: 기본 배치
            totalItems <= 500 -> (DEFAULT_BATCH_SIZE * 1.5).toInt()  // 많음: 큰 배치
            else -> MAX_BATCH_SIZE                                // 대량: 최대 배치
        }
    }

    /**
     * 통계 정보 반환
     */
    fun getStats(): Map<String, Any> {
        return asyncDbManager.getStats()
    }
}