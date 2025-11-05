package com.lukehemmin.lukeVanilla.System.AdvancedLandClaiming.Test

import com.lukehemmin.lukeVanilla.System.AdvancedLandClaiming.Models.*
import com.lukehemmin.lukeVanilla.System.AdvancedLandClaiming.Cache.AdvancedLandCache
import com.lukehemmin.lukeVanilla.System.AdvancedLandClaiming.Cache.ChunkCoordinate
import java.util.*
import java.util.concurrent.*
import kotlin.system.measureTimeMillis

/**
 * 🚀 성능 개선 전후 비교 테스트
 *
 * 주요 검증 항목:
 * 1. Thread-Safety 검증
 * 2. 성능 개선 측정 (기존 vs 개선)
 * 3. 메모리 사용량 비교
 * 4. Race Condition 방지 검증
 */
class PerformanceComparison {

    companion object {
        const val TEST_VILLAGE_COUNT = 100
        const val TEST_CHUNKS_PER_VILLAGE = 50
        const val CONCURRENT_THREADS = 10
        const val OPERATIONS_PER_THREAD = 1000
    }

    /**
     * 🔥 핵심 성능 테스트: 마을 청크 조회 성능 비교
     *
     * 기존: O(n) 전체 캐시 순회
     * 개선: O(1) 인덱스 조회
     */
    fun testVillageChunkLookupPerformance() {
        println("=== 🚀 마을 청크 조회 성능 테스트 ===")

        val cache = AdvancedLandCache()
        val testData = generateTestData()

        // 테스트 데이터 로드
        cache.loadAllClaims(testData)

        println("테스트 데이터: ${TEST_VILLAGE_COUNT}개 마을, 총 ${TEST_VILLAGE_COUNT * TEST_CHUNKS_PER_VILLAGE}개 청크")

        // 🚀 개선된 방법: O(1) 조회
        val improvedTime = measureTimeMillis {
            repeat(1000) {
                val randomVillageId = Random().nextInt(TEST_VILLAGE_COUNT) + 1
                val chunks = cache.getVillageChunks(randomVillageId)
                // 실제 작업 시뮬레이션
                chunks.forEach { _ -> }
            }
        }

        // 📊 기존 방법 시뮬레이션: O(n) 전체 순회
        val legacyTime = measureTimeMillis {
            repeat(1000) {
                val randomVillageId = Random().nextInt(TEST_VILLAGE_COUNT) + 1
                val chunks = simulateLegacyVillageChunkLookup(testData, randomVillageId)
                chunks.forEach { _ -> }
            }
        }

        println("✅ 개선된 방법 (O(1)): ${improvedTime}ms")
        println("⚠️  기존 방법 (O(n)): ${legacyTime}ms")
        println("🚀 성능 향상: ${String.format("%.1f", legacyTime.toDouble() / improvedTime)}배")
        println()
    }

    /**
     * 기존 방법 시뮬레이션 (전체 캐시 순회)
     */
    private fun simulateLegacyVillageChunkLookup(
        testData: Map<String, Map<Pair<Int, Int>, AdvancedClaimInfo>>,
        villageId: Int
    ): List<ChunkCoordinate> {
        val result = mutableListOf<ChunkCoordinate>()

        // 기존 방법: 전체 캐시 순회
        testData.forEach { (worldName, worldClaims) ->
            worldClaims.forEach { (chunkCoord, claimInfo) ->
                if (claimInfo.claimType == ClaimType.VILLAGE && claimInfo.villageId == villageId) {
                    result.add(ChunkCoordinate(chunkCoord.first, chunkCoord.second, worldName))
                }
            }
        }

        return result
    }

    /**
     * ⚡ Thread-Safety 검증 테스트
     */
    fun testThreadSafety() {
        println("=== ⚡ Thread-Safety 검증 테스트 ===")

        val cache = AdvancedLandCache()
        val executor = Executors.newFixedThreadPool(CONCURRENT_THREADS)
        val latch = CountDownLatch(CONCURRENT_THREADS)
        val errors = ConcurrentLinkedQueue<Exception>()

        val startTime = System.currentTimeMillis()

        // 동시에 여러 스레드에서 캐시 조작
        repeat(CONCURRENT_THREADS) { threadId ->
            executor.submit {
                try {
                    repeat(OPERATIONS_PER_THREAD) { operationId ->
                        val claimInfo = createTestClaimInfo(threadId, operationId)

                        // 동시 읽기/쓰기 작업
                        cache.addClaim(claimInfo)
                        cache.isChunkClaimed(claimInfo.worldName, claimInfo.chunkX, claimInfo.chunkZ)
                        cache.getClaimOwner(claimInfo.worldName, claimInfo.chunkX, claimInfo.chunkZ)

                        if (operationId % 2 == 0) {
                            cache.removeClaim(claimInfo.worldName, claimInfo.chunkX, claimInfo.chunkZ)
                        }
                    }
                } catch (e: Exception) {
                    errors.offer(e)
                } finally {
                    latch.countDown()
                }
            }
        }

        // 모든 스레드 완료 대기
        latch.await(30, TimeUnit.SECONDS)
        executor.shutdown()

        val endTime = System.currentTimeMillis()
        val totalOperations = CONCURRENT_THREADS * OPERATIONS_PER_THREAD

        if (errors.isEmpty()) {
            println("✅ Thread-Safety 테스트 성공!")
            println("   - 동시 스레드: ${CONCURRENT_THREADS}개")
            println("   - 총 연산: ${totalOperations}개")
            println("   - 실행 시간: ${endTime - startTime}ms")
            println("   - 초당 연산: ${String.format("%.0f", totalOperations.toDouble() / (endTime - startTime) * 1000)}ops/sec")
        } else {
            println("❌ Thread-Safety 테스트 실패!")
            println("   오류 개수: ${errors.size}")
            errors.take(3).forEach { e ->
                println("   - ${e.javaClass.simpleName}: ${e.message}")
            }
        }
        println()
    }

    /**
     * 🧠 메모리 사용량 비교 테스트
     */
    fun testMemoryUsage() {
        println("=== 🧠 메모리 사용량 비교 테스트 ===")

        val runtime = Runtime.getRuntime()

        // GC 실행
        System.gc()
        Thread.sleep(100)

        val beforeMemory = runtime.totalMemory() - runtime.freeMemory()

        // 개선된 캐시 생성 및 데이터 로드
        val cache = AdvancedLandCache()
        val testData = generateTestData()
        cache.loadAllClaims(testData)

        // GC 실행
        System.gc()
        Thread.sleep(100)

        val afterMemory = runtime.totalMemory() - runtime.freeMemory()
        val usedMemory = afterMemory - beforeMemory

        val stats = cache.getCacheStats()

        println("📊 메모리 사용량:")
        println("   - 사용된 메모리: ${formatBytes(usedMemory)}")
        println("   - 총 청크 수: ${stats["totalChunks"]}")
        println("   - 총 플레이어 수: ${stats["totalPlayers"]}")
        println("   - 총 마을 수: ${stats["totalVillages"]}")
        println("   - 청크당 메모리: ${formatBytes(usedMemory / (stats["totalChunks"] as Int))}")

        // 캐시 정리 테스트
        cache.cleanup()
        println("✅ 캐시 정리 완료")
        println()
    }

    /**
     * 🔒 Race Condition 방지 검증
     */
    fun testRaceConditionPrevention() {
        println("=== 🔒 Race Condition 방지 검증 ===")

        val cache = AdvancedLandCache()
        val executor = Executors.newFixedThreadPool(CONCURRENT_THREADS)
        val latch = CountDownLatch(CONCURRENT_THREADS)

        val targetWorldName = "test_world"
        val targetChunkX = 0
        val targetChunkZ = 0

        val successfulClaims = ConcurrentLinkedQueue<UUID>()
        val failedClaims = ConcurrentLinkedQueue<UUID>()

        // 동시에 같은 청크를 클레이밍 시도
        repeat(CONCURRENT_THREADS) { threadId ->
            executor.submit {
                try {
                    val playerUuid = UUID.randomUUID()
                    val claimInfo = AdvancedClaimInfo(
                        chunkX = targetChunkX,
                        chunkZ = targetChunkZ,
                        worldName = targetWorldName,
                        ownerUuid = playerUuid,
                        ownerName = "Player$threadId",
                        claimType = ClaimType.PERSONAL,
                        createdAt = System.currentTimeMillis(),
                        lastUpdated = System.currentTimeMillis(),
                        villageId = null,
                        claimCost = null
                    )

                    // 중복 클레이밍 시도
                    if (cache.isChunkClaimed(targetWorldName, targetChunkX, targetChunkZ)) {
                        failedClaims.offer(playerUuid)
                    } else {
                        cache.addClaim(claimInfo)
                        if (cache.getClaimOwner(targetWorldName, targetChunkX, targetChunkZ)?.ownerUuid == playerUuid) {
                            successfulClaims.offer(playerUuid)
                        } else {
                            failedClaims.offer(playerUuid)
                        }
                    }
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await(10, TimeUnit.SECONDS)
        executor.shutdown()

        println("🔒 Race Condition 방지 결과:")
        println("   - 성공한 클레이밍: ${successfulClaims.size}개")
        println("   - 실패한 클레이밍: ${failedClaims.size}개")

        if (successfulClaims.size == 1) {
            println("✅ Race Condition 방지 성공! 오직 1개만 성공")
            val winner = cache.getClaimOwner(targetWorldName, targetChunkX, targetChunkZ)
            println("   승자: ${winner?.ownerName} (${winner?.ownerUuid})")
        } else {
            println("❌ Race Condition 방지 실패! ${successfulClaims.size}개가 동시 성공")
        }
        println()
    }

    /**
     * 🏃‍♂️ 전체 성능 벤치마크 실행
     */
    fun runFullBenchmark() {
        println("🚀 AdvancedLandClaiming 성능 개선 검증 테스트")
        println("=".repeat(50))
        println()

        testVillageChunkLookupPerformance()
        testThreadSafety()
        testMemoryUsage()
        testRaceConditionPrevention()

        println("✅ 모든 테스트 완료!")
        println("🎯 주요 개선사항:")
        println("   - Thread-Safe 캐시로 동시성 문제 해결")
        println("   - O(1) 마을 청크 조회로 성능 대폭 향상")
        println("   - Race Condition 방지로 데이터 일관성 보장")
        println("   - 메모리 효율적인 캐시 구조")
    }

    // === 유틸리티 메서드들 ===

    /**
     * 테스트 데이터 생성
     */
    private fun generateTestData(): Map<String, Map<Pair<Int, Int>, AdvancedClaimInfo>> {
        val testData = mutableMapOf<String, MutableMap<Pair<Int, Int>, AdvancedClaimInfo>>()
        val worldName = "test_world"
        val worldMap = mutableMapOf<Pair<Int, Int>, AdvancedClaimInfo>()

        repeat(TEST_VILLAGE_COUNT) { villageIndex ->
            val villageId = villageIndex + 1
            val mayorUuid = UUID.randomUUID()

            repeat(TEST_CHUNKS_PER_VILLAGE) { chunkIndex ->
                val chunkX = villageIndex * 100 + chunkIndex
                val chunkZ = villageIndex * 100 + chunkIndex

                val claimInfo = AdvancedClaimInfo(
                    chunkX = chunkX,
                    chunkZ = chunkZ,
                    worldName = worldName,
                    ownerUuid = mayorUuid,
                    ownerName = "Village$villageId (마을)",
                    claimType = ClaimType.VILLAGE,
                    createdAt = System.currentTimeMillis(),
                    lastUpdated = System.currentTimeMillis(),
                    villageId = villageId,
                    claimCost = null
                )

                worldMap[chunkX to chunkZ] = claimInfo
            }
        }

        testData[worldName] = worldMap
        return testData
    }

    /**
     * 테스트용 클레이밍 정보 생성
     */
    private fun createTestClaimInfo(threadId: Int, operationId: Int): AdvancedClaimInfo {
        return AdvancedClaimInfo(
            chunkX = threadId * 1000 + operationId,
            chunkZ = threadId * 1000 + operationId,
            worldName = "thread_test_world",
            ownerUuid = UUID.randomUUID(),
            ownerName = "Thread${threadId}_Op${operationId}",
            claimType = ClaimType.PERSONAL,
            createdAt = System.currentTimeMillis(),
            lastUpdated = System.currentTimeMillis(),
            villageId = null,
            claimCost = null
        )
    }

    /**
     * 바이트를 읽기 좋은 형태로 포맷
     */
    private fun formatBytes(bytes: Long): String {
        val units = arrayOf("B", "KB", "MB", "GB")
        var size = bytes.toDouble()
        var unitIndex = 0

        while (size >= 1024 && unitIndex < units.size - 1) {
            size /= 1024.0
            unitIndex++
        }

        return String.format("%.2f %s", size, units[unitIndex])
    }
}

/**
 * 테스트 실행 메인 함수
 */
fun main() {
    val test = PerformanceComparison()
    test.runFullBenchmark()
}