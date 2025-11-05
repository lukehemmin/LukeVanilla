package com.lukehemmin.lukeVanilla.System.AdvancedLandClaiming.Cache

import com.lukehemmin.lukeVanilla.System.AdvancedLandClaiming.Models.*
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * Thread-Safe한 고급 토지 클레이밍 캐시 시스템
 *
 * 주요 개선사항:
 * - ConcurrentHashMap으로 Thread-Safety 보장
 * - 마을별 청크 인덱스로 O(1) 조회 성능
 * - Read-Write Lock으로 읽기 성능 최적화
 * - 메모리 사용량 최적화
 */
class AdvancedLandCache {

    // === Thread-Safe 캐시 구조 ===

    /**
     * 메인 청크 캐시: 월드별 -> 청크 좌표별 -> 클레이밍 정보
     * ConcurrentHashMap으로 Thread-Safety 보장
     */
    private val claimedChunks = ConcurrentHashMap<String, ConcurrentHashMap<Pair<Int, Int>, AdvancedClaimInfo>>()

    /**
     * 플레이어별 클레이밍 캐시: 플레이어 UUID -> 소유 청크 목록
     * Set 사용으로 중복 제거 및 빠른 조회
     */
    private val playerClaims = ConcurrentHashMap<UUID, MutableSet<ChunkCoordinate>>()

    /**
     * 마을별 청크 인덱스: 마을 ID -> 마을 소유 청크 목록
     * 🚀 핵심 개선: 전체 캐시 순회 없이 O(1) 조회
     */
    private val villageChunkIndex = ConcurrentHashMap<Int, MutableSet<ChunkCoordinate>>()

    /**
     * 청크별 마을 매핑: 청크 좌표 -> 마을 ID
     * 역방향 조회를 위한 인덱스
     */
    private val chunkToVillageMapping = ConcurrentHashMap<ChunkCoordinate, Int>()

    // Read-Write Lock for 읽기 성능 최적화
    private val cacheLock = ReentrantReadWriteLock()

    // === 청크 클레이밍 캐시 관리 ===

    /**
     * 청크가 클레이밍되었는지 확인 (Thread-Safe)
     */
    fun isChunkClaimed(worldName: String, chunkX: Int, chunkZ: Int): Boolean {
        return cacheLock.read {
            claimedChunks[worldName]?.containsKey(chunkX to chunkZ) ?: false
        }
    }

    /**
     * 청크의 소유자 정보 조회 (Thread-Safe)
     */
    fun getClaimOwner(worldName: String, chunkX: Int, chunkZ: Int): AdvancedClaimInfo? {
        return cacheLock.read {
            claimedChunks[worldName]?.get(chunkX to chunkZ)
        }
    }

    /**
     * 새로운 클레이밍 추가 (Thread-Safe)
     * 모든 관련 인덱스를 원자적으로 업데이트
     */
    fun addClaim(claimInfo: AdvancedClaimInfo) {
        cacheLock.write {
            val chunkCoord = Pair(claimInfo.chunkX, claimInfo.chunkZ)
            val chunkCoordinate = ChunkCoordinate(claimInfo.chunkX, claimInfo.chunkZ, claimInfo.worldName)

            // 1. 메인 캐시 업데이트
            claimedChunks.computeIfAbsent(claimInfo.worldName) { ConcurrentHashMap() }[chunkCoord] = claimInfo

            // 2. 플레이어 캐시 업데이트
            playerClaims.computeIfAbsent(claimInfo.ownerUuid) { ConcurrentHashMap.newKeySet() }.add(chunkCoordinate)

            // 3. 마을 인덱스 업데이트 (마을 토지인 경우)
            claimInfo.villageId?.let { villageId ->
                villageChunkIndex.computeIfAbsent(villageId) { ConcurrentHashMap.newKeySet() }.add(chunkCoordinate)
                chunkToVillageMapping[chunkCoordinate] = villageId
            }
        }
    }

    /**
     * 클레이밍 제거 (Thread-Safe)
     * 모든 관련 인덱스에서 원자적으로 제거
     */
    fun removeClaim(worldName: String, chunkX: Int, chunkZ: Int): AdvancedClaimInfo? {
        return cacheLock.write {
            val chunkCoord = Pair(chunkX, chunkZ)
            val chunkCoordinate = ChunkCoordinate(chunkX, chunkZ, worldName)

            // 1. 기존 정보 조회
            val removedClaim = claimedChunks[worldName]?.remove(chunkCoord) ?: return@write null

            // 2. 플레이어 캐시에서 제거
            playerClaims[removedClaim.ownerUuid]?.remove(chunkCoordinate)

            // 3. 마을 인덱스에서 제거 (마을 토지인 경우)
            removedClaim.villageId?.let { villageId ->
                villageChunkIndex[villageId]?.remove(chunkCoordinate)
                chunkToVillageMapping.remove(chunkCoordinate)
            }

            removedClaim
        }
    }

    // === 마을 관련 최적화된 메서드 ===

    /**
     * 🚀 핵심 개선: 마을의 모든 청크를 O(1)으로 조회
     * 기존: O(전체청크수) -> 개선: O(마을청크수)
     */
    fun getVillageChunks(villageId: Int): Set<ChunkCoordinate> {
        return cacheLock.read {
            villageChunkIndex[villageId]?.toSet() ?: emptySet()
        }
    }

    /**
     * 마을 청크 개수 조회 (O(1) 성능)
     */
    fun getVillageChunkCount(villageId: Int): Int {
        return cacheLock.read {
            villageChunkIndex[villageId]?.size ?: 0
        }
    }

    /**
     * 청크가 특정 마을에 속하는지 확인 (O(1) 성능)
     */
    fun isChunkInVillage(chunkCoordinate: ChunkCoordinate, villageId: Int): Boolean {
        return cacheLock.read {
            chunkToVillageMapping[chunkCoordinate] == villageId
        }
    }

    /**
     * 마을 토지를 개인 토지로 변환 (원자적 연산)
     */
    fun convertVillageToPersonalLands(
        villageId: Int,
        newOwnerUuid: UUID,
        newOwnerName: String
    ): List<AdvancedClaimInfo> {
        return cacheLock.write {
            val villageChunks = villageChunkIndex[villageId] ?: return@write emptyList()
            val convertedClaims = mutableListOf<AdvancedClaimInfo>()

            villageChunks.forEach { chunkCoord ->
                val currentClaim = claimedChunks[chunkCoord.worldName]?.get(chunkCoord.x to chunkCoord.z)
                if (currentClaim != null) {
                    // 개인 토지로 변환
                    val convertedClaim = currentClaim.copy(
                        claimType = ClaimType.PERSONAL,
                        villageId = null,
                        ownerUuid = newOwnerUuid,
                        ownerName = newOwnerName,
                        lastUpdated = System.currentTimeMillis()
                    )

                    // 캐시 업데이트
                    claimedChunks[chunkCoord.worldName]?.set(chunkCoord.x to chunkCoord.z, convertedClaim)

                    // 플레이어 캐시 업데이트
                    playerClaims.computeIfAbsent(newOwnerUuid) { ConcurrentHashMap.newKeySet() }.add(chunkCoord)

                    convertedClaims.add(convertedClaim)
                }
            }

            // 마을 인덱스 정리
            villageChunkIndex.remove(villageId)
            villageChunks.forEach { chunkCoord ->
                chunkToVillageMapping.remove(chunkCoord)
            }

            convertedClaims
        }
    }

    // === 플레이어 관련 메서드 ===

    /**
     * 플레이어의 클레이밍 수 조회 (O(1) 성능)
     */
    fun getPlayerClaimCount(playerUuid: UUID): Int {
        return cacheLock.read {
            playerClaims[playerUuid]?.size ?: 0
        }
    }

    /**
     * 플레이어의 모든 클레이밍 조회
     */
    fun getPlayerClaims(playerUuid: UUID): Set<ChunkCoordinate> {
        return cacheLock.read {
            playerClaims[playerUuid]?.toSet() ?: emptySet()
        }
    }

    // === 캐시 관리 ===

    /**
     * 전체 캐시 로드 (서버 시작 시)
     */
    fun loadAllClaims(claims: Map<String, Map<Pair<Int, Int>, AdvancedClaimInfo>>) {
        cacheLock.write {
            // 기존 캐시 정리
            claimedChunks.clear()
            playerClaims.clear()
            villageChunkIndex.clear()
            chunkToVillageMapping.clear()

            // 새 데이터 로드
            claims.forEach { (worldName, worldClaims) ->
                val worldCache = ConcurrentHashMap<Pair<Int, Int>, AdvancedClaimInfo>()
                claimedChunks[worldName] = worldCache

                worldClaims.forEach { (chunkCoord, claimInfo) ->
                    worldCache[chunkCoord] = claimInfo

                    val chunkCoordinate = ChunkCoordinate(chunkCoord.first, chunkCoord.second, worldName)

                    // 플레이어 캐시 구성
                    playerClaims.computeIfAbsent(claimInfo.ownerUuid) { ConcurrentHashMap.newKeySet() }
                        .add(chunkCoordinate)

                    // 마을 인덱스 구성
                    claimInfo.villageId?.let { villageId ->
                        villageChunkIndex.computeIfAbsent(villageId) { ConcurrentHashMap.newKeySet() }
                            .add(chunkCoordinate)
                        chunkToVillageMapping[chunkCoordinate] = villageId
                    }
                }
            }
        }
    }

    /**
     * 캐시 통계 정보
     */
    fun getCacheStats(): Map<String, Any> {
        return cacheLock.read {
            mapOf(
                "totalChunks" to claimedChunks.values.sumOf { it.size },
                "totalPlayers" to playerClaims.size,
                "totalVillages" to villageChunkIndex.size,
                "worldCount" to claimedChunks.size,
                "villageChunks" to villageChunkIndex.values.sumOf { it.size }
            )
        }
    }

    /**
     * 캐시 정리 (메모리 최적화)
     */
    fun cleanup() {
        cacheLock.write {
            // 빈 맵들 정리
            claimedChunks.values.removeIf { it.isEmpty() }
            playerClaims.values.removeIf { it.isEmpty() }
            villageChunkIndex.values.removeIf { it.isEmpty() }
        }
    }
}

/**
 * 청크 좌표를 나타내는 데이터 클래스
 * hashCode와 equals가 자동 구현되어 해시맵 키로 사용 가능
 */
data class ChunkCoordinate(
    val x: Int,
    val z: Int,
    val worldName: String
) {
    override fun toString(): String = "$worldName($x,$z)"
}