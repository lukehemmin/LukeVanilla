package com.lukehemmin.lukeVanilla.System.Database

import com.lukehemmin.lukeVanilla.Main
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.entity.Player
import java.util.*

/**
 * 비동기 DB 시스템 사용 예시
 * AdvancedLandManager나 다른 매니저에서 이런 식으로 사용하면 됩니다
 */
class DatabaseIntegrationExample(
    private val plugin: Main,
    private val database: Database
) {

    private val asyncLandData: AsyncAdvancedLandData by lazy {
        val asyncManager = database.getAsyncManager()
            ?: throw IllegalStateException("AsyncDatabaseManager가 초기화되지 않았습니다")
        AsyncAdvancedLandData(plugin, asyncManager)
    }

    /**
     * 마을 해체 명령어 처리 예시 (기존 동기 버전을 비동기로 변환)
     */
    fun handleVillageDisbandAsync(player: Player, villageId: Int) {
        player.sendMessage(Component.text("마을 해체를 시작합니다...", NamedTextColor.YELLOW))

        // 1단계: 마을 정보 확인
        asyncLandData.getVillageInfoAsync(
            villageId = villageId,
            onSuccess = { villageInfo ->
                if (villageInfo == null) {
                    player.sendMessage(Component.text("마을을 찾을 수 없습니다.", NamedTextColor.RED))
                    return@getVillageInfoAsync
                }

                // 2단계: 마을 땅을 개인 토지로 변환
                convertVillageToPersonalLands(player, villageInfo, villageId)
            },
            onFailure = { exception ->
                player.sendMessage(Component.text("마을 정보 조회 중 오류가 발생했습니다.", NamedTextColor.RED))
                plugin.logger.severe("마을 정보 조회 실패: ${exception.message}")
            }
        )
    }

    private fun convertVillageToPersonalLands(
        player: Player,
        villageInfo: VillageInfo,
        villageId: Int
    ) {
        var lastProgress = 0

        asyncLandData.convertVillageToPersonalLandsAsync(
            villageId = villageId,
            newOwnerUuid = villageInfo.mayorUuid,
            newOwnerName = villageInfo.mayorName,
            onProgress = { completed, total ->
                // 10% 단위로만 진행상황 알림
                val progress = (completed * 100) / total
                if (progress >= lastProgress + 10) {
                    lastProgress = progress
                    player.sendMessage(
                        Component.text("땅 변환 진행률: $progress% ($completed/$total)", NamedTextColor.AQUA)
                    )
                }
            },
            onComplete = { success ->
                if (success) {
                    // 3단계: 마을 비활성화
                    deactivateVillage(player, villageInfo, villageId)
                } else {
                    player.sendMessage(Component.text("마을 땅 변환에 실패했습니다.", NamedTextColor.RED))
                }
            },
            onError = { exception ->
                player.sendMessage(Component.text("마을 땅 변환 중 오류가 발생했습니다.", NamedTextColor.RED))
                plugin.logger.severe("마을 땅 변환 실패: ${exception.message}")
            }
        )
    }

    private fun deactivateVillage(
        player: Player,
        villageInfo: VillageInfo,
        villageId: Int
    ) {
        asyncLandData.deactivateVillageAsync(
            villageId = villageId,
            onSuccess = { success ->
                if (success) {
                    player.sendMessage(
                        Component.text("마을 '${villageInfo.villageName}'이 성공적으로 해체되었습니다!", NamedTextColor.GREEN)
                    )
                    player.sendMessage(
                        Component.text("모든 땅이 이장 ${villageInfo.mayorName}에게 이전되었습니다.", NamedTextColor.GRAY)
                    )

                    // 통계 정보 로깅
                    val stats = asyncLandData.getStats()
                    plugin.logger.info("[마을해체] 완료 - 마을ID: $villageId, DB통계: $stats")
                } else {
                    player.sendMessage(Component.text("마을 비활성화에 실패했습니다.", NamedTextColor.RED))
                }
            },
            onFailure = { exception ->
                player.sendMessage(Component.text("마을 비활성화 중 오류가 발생했습니다.", NamedTextColor.RED))
                plugin.logger.severe("마을 비활성화 실패: ${exception.message}")
            }
        )
    }

    /**
     * 플레이어 클레이밍 수 조회 예시
     */
    fun checkPlayerClaimCount(player: Player, targetUuid: UUID) {
        asyncLandData.getPlayerClaimCountAsync(
            playerUuid = targetUuid,
            onSuccess = { count ->
                player.sendMessage(
                    Component.text("플레이어의 클레이밍 수: $count", NamedTextColor.AQUA)
                )
            },
            onFailure = { exception ->
                player.sendMessage(
                    Component.text("클레이밍 수 조회 중 오류가 발생했습니다.", NamedTextColor.RED)
                )
                plugin.logger.warning("클레이밍 수 조회 실패: ${exception.message}")
            }
        )
    }

    /**
     * 관리자용 DB 통계 확인
     */
    fun showDatabaseStats(player: Player) {
        val asyncManager = database.getAsyncManager()
        if (asyncManager == null) {
            player.sendMessage(Component.text("AsyncDatabaseManager가 초기화되지 않았습니다.", NamedTextColor.RED))
            return
        }

        val stats = asyncManager.getStats()
        player.sendMessage(Component.text("=== 데이터베이스 통계 ===", NamedTextColor.GOLD))
        player.sendMessage(Component.text("대기 중인 작업: ${stats["pendingOperations"]}", NamedTextColor.GRAY))
        player.sendMessage(Component.text("총 작업 수: ${stats["totalOperations"]}", NamedTextColor.GRAY))
        player.sendMessage(Component.text("실패한 작업: ${stats["failedOperations"]}", NamedTextColor.GRAY))
        player.sendMessage(Component.text("Circuit Breaker: ${stats["circuitBreakerOpen"]}", NamedTextColor.GRAY))
        player.sendMessage(Component.text("캐시 크기: ${stats["cacheSize"]}", NamedTextColor.GRAY))
        player.sendMessage(Component.text("활성 스레드: ${stats["activeThreads"]}", NamedTextColor.GRAY))
        player.sendMessage(Component.text("큐 크기: ${stats["queueSize"]}", NamedTextColor.GRAY))
    }
}

/**
 * VillageInfo 데이터 클래스 (예시)
 */
data class VillageInfo(
    val villageId: Int,
    val villageName: String,
    val mayorUuid: UUID,
    val mayorName: String,
    val createdAt: Long,
    val lastUpdated: Long,
    val isActive: Boolean
)