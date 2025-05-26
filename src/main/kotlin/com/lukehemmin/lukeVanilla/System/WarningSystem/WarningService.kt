package com.lukehemmin.lukeVanilla.System.WarningSystem

import com.lukehemmin.lukeVanilla.System.Database.Database
import org.bukkit.entity.Player
import java.util.UUID
import java.util.logging.Level
import java.util.logging.Logger

/**
 * 경고 시스템 비즈니스 로직을 처리하는 서비스 클래스
 */
class WarningService(database: Database) {
    private val logger = Logger.getLogger(WarningService::class.java.name)
    private val warningRepository = WarningRepository(database)
    
    /**
     * 플레이어에게 경고 부여
     * 
     * @param targetPlayer 경고 대상 플레이어
     * @param adminUuid 경고를 부여한 관리자 UUID
     * @param adminName 경고를 부여한 관리자 이름
     * @param reason 경고 사유
     * @return 경고 부여 성공 여부와 현재 경고 횟수
     */
    fun addWarning(
        targetPlayer: Player,
        adminUuid: UUID,
        adminName: String,
        reason: String
    ): Pair<Boolean, Int> {
        val success = warningRepository.addWarning(
            playerUuid = targetPlayer.uniqueId,
            playerName = targetPlayer.name,
            adminUuid = adminUuid,
            adminName = adminName,
            reason = reason
        )
        
        if (success) {
            val playerWarning = warningRepository.getOrCreatePlayerWarning(
                uuid = targetPlayer.uniqueId,
                username = targetPlayer.name
            )
            return Pair(true, playerWarning.activeWarningsCount)
        }
        
        return Pair(false, 0)
    }
    
    /**
     * 경고 ID로 경고 차감
     * 
     * @param targetPlayerUuid 경고 대상 플레이어 UUID
     * @param warningId 차감할 경고 ID
     * @param adminUuid 차감을 실행한 관리자 UUID
     * @param adminName 차감을 실행한 관리자 이름
     * @param reason 차감 사유
     * @return 차감 성공 여부
     */
    fun pardonWarningById(
        targetPlayerUuid: UUID,
        warningId: Int,
        adminUuid: UUID,
        adminName: String,
        reason: String
    ): Boolean {
        return warningRepository.pardonWarningById(
            warningId = warningId,
            playerUuid = targetPlayerUuid,
            adminUuid = adminUuid,
            adminName = adminName,
            reason = reason
        )
    }
    
    /**
     * 경고 횟수 차감
     * 
     * @param targetPlayerUuid 경고 대상 플레이어 UUID
     * @param count 차감할 경고 횟수
     * @param adminUuid 차감을 실행한 관리자 UUID
     * @param adminName 차감을 실행한 관리자 이름
     * @param reason 차감 사유
     * @return 차감 성공 여부와 실제 차감된 횟수
     */
    fun pardonWarningsByCount(
        targetPlayerUuid: UUID,
        count: Int,
        adminUuid: UUID,
        adminName: String,
        reason: String
    ): Pair<Boolean, Int> {
        val playerBefore = warningRepository.getOrCreatePlayerWarning(
            uuid = targetPlayerUuid,
            username = "" // 조회만 하기 때문에 임시 값 사용
        )
        
        val beforeCount = playerBefore.activeWarningsCount
        val success = warningRepository.pardonWarningsByCount(
            playerUuid = targetPlayerUuid,
            count = count,
            adminUuid = adminUuid,
            adminName = adminName,
            reason = reason
        )
        
        if (success) {
            val playerAfter = warningRepository.getOrCreatePlayerWarning(
                uuid = targetPlayerUuid,
                username = "" // 조회만 하기 때문에 임시 값 사용
            )
            val actualPardoned = beforeCount - playerAfter.activeWarningsCount
            return Pair(true, actualPardoned)
        }
        
        return Pair(false, 0)
    }
    
    /**
     * 플레이어의 경고 내역 조회
     * 
     * @param playerUuid 조회할 플레이어 UUID
     * @return 플레이어의 경고 내역 목록
     */
    fun getPlayerWarnings(playerUuid: UUID): List<WarningRecord> {
        return warningRepository.getPlayerWarnings(playerUuid)
    }
    
    /**
     * 플레이어의 현재 유효 경고 횟수 조회
     * 
     * @param playerUuid 조회할 플레이어 UUID
     * @param playerName 플레이어 이름 (플레이어 정보가 없을 경우 생성에 사용)
     * @return 플레이어의 현재 유효 경고 횟수
     */
    fun getActiveWarningsCount(playerUuid: UUID, playerName: String): Int {
        val playerWarning = warningRepository.getOrCreatePlayerWarning(
            uuid = playerUuid,
            username = playerName
        )
        return playerWarning.activeWarningsCount
    }
    
    /**
     * 경고 받은 플레이어 목록 조회 (페이지네이션)
     * 
     * @param page 페이지 번호 (1부터 시작)
     * @param playersPerPage 페이지당 플레이어 수
     * @return 경고 받은 플레이어 목록
     */
    fun getWarnedPlayers(page: Int, playersPerPage: Int = 10): List<PlayerWarning> {
        return warningRepository.getWarnedPlayers(page, playersPerPage)
    }
    
    /**
     * 경고 받은 플레이어 총 수 조회
     * 
     * @return 경고 받은 플레이어 총 수
     */
    fun getWarnedPlayersCount(): Int {
        return warningRepository.getWarnedPlayersCount()
    }
}
