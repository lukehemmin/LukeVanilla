package com.lukehemmin.lukeVanilla.System.WarningSystem

import com.lukehemmin.lukeVanilla.System.Database.Database
import net.dv8tion.jda.api.JDA
import org.bukkit.entity.Player
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.logging.Level
import java.util.logging.Logger

/**
 * 경고 시스템 비즈니스 로직을 처리하는 서비스 클래스
 */
/**
 * 최근 경고 정보를 담는 데이터 클래스
 */
data class RecentWarningInfo(
    val username: String,
    val warningCount: Int,
    val lastWarningDate: LocalDateTime
)

/**
 * 경고 시스템 분석 결과를 담는 데이터 클래스
 */
data class WarningAnalysisResult(
    val recentWarnings: List<RecentWarningInfo>,
    val repeatedOffenders: List<RepeatedOffenderInfo>,
    val riskAssessment: String,
    val recommendedActions: List<String>
)

/**
 * 반복 위반자 정보를 담는 데이터 클래스
 */
data class RepeatedOffenderInfo(
    val username: String,
    val totalWarnings: Int,
    val recentWarnings: Int,  // 최근 7일 내 경고 수
    val riskLevel: RiskLevel  // 위험도 수준
)

/**
 * 위험도 수준을 나타내는 열거형
 */
enum class RiskLevel {
    LOW,     // 낮음
    MEDIUM,  // 중간
    HIGH     // 높음
}

class WarningService(private val database: Database, jda: JDA) {
    private val logger = Logger.getLogger(WarningService::class.java.name)
    private val warningRepository = WarningRepository(database)
    private val banManager = BanManager(database, jda)
    
    companion object {
        // 자동 차단 임계값 (5회 이상 경고 시 자동 차단)
        const val AUTO_BAN_THRESHOLD = 5
    }
    
    /**
     * 플레이어에게 경고 부여
     * 
     * @param targetPlayer 경고 대상 플레이어
     * @param adminUuid 경고를 부여한 관리자 UUID
     * @param adminName 경고를 부여한 관리자 이름
     * @param reason 경고 사유
     * @return 경고 부여 성공 여부와 현재 경고 횟수
     */
    /**
     * 플레이어에게 경고 부여
     * 
     * @param targetPlayer 경고 대상 플레이어
     * @param adminUuid 경고를 부여한 관리자 UUID
     * @param adminName 경고를 부여한 관리자 이름
     * @param reason 경고 사유
     * @return 경고 부여 성공 여부, 현재 경고 횟수, 자동 차단 여부(3번째 값)
     */
    fun addWarning(
        targetPlayer: Player,
        adminUuid: UUID,
        adminName: String,
        reason: String
    ): Triple<Boolean, Int, Boolean> {
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
            
            val warningCount = playerWarning.activeWarningsCount
            
            // 경고 횟수가 임계값 이상이면 자동 차단 처리
            var autoBanned = false
            if (warningCount >= AUTO_BAN_THRESHOLD) {
                val banReason = "경고 누적 ${warningCount}회 (자동 차단)"
                autoBanned = banManager.banPlayer(
                    uuid = targetPlayer.uniqueId,
                    reason = banReason,
                    source = "경고 시스템 자동 차단"
                )
                
                if (autoBanned) {
                    logger.info("${targetPlayer.name}님이 경고 누적으로 자동 차단되었습니다. 현재 경고 수: $warningCount")
                } else {
                    logger.warning("${targetPlayer.name}님의 자동 차단 처리 중 오류가 발생했습니다. 현재 경고 수: $warningCount")
                }
            }
            
            return Triple(true, warningCount, autoBanned)
        }
        
        return Triple(false, 0, false)
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
     * 플레이어 경고 정보 조회
     * 
     * @param playerUuid 조회할 플레이어 UUID
     * @param playerName 플레이어 이름 (플레이어 정보가 없을 경우 생성에 사용)
     * @return 플레이어 경고 정보
     */
    fun getPlayerWarning(playerUuid: UUID, playerName: String): PlayerWarning {
        return warningRepository.getOrCreatePlayerWarning(
            uuid = playerUuid,
            username = playerName
        )
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
    
    /**
     * 최근 경고 내역 조회 (최근 경고일 기준 정렬)
     * 
     * @param limit 조회할 최대 개수
     * @return 최근 경고 내역 목록
     */
    fun getRecentWarnings(limit: Int = 10): List<RecentWarningInfo> {
        val recentWarnings = mutableListOf<RecentWarningInfo>()
        
        try {
            database.getConnection().use { connection ->
                val query = """
                    SELECT p.username, COUNT(r.warning_id) as warning_count, MAX(r.created_at) as last_warning_date 
                    FROM warnings_players p 
                    JOIN warnings_records r ON p.player_id = r.player_id 
                    WHERE r.is_active = 1 
                    GROUP BY p.username 
                    ORDER BY last_warning_date DESC 
                    LIMIT ?
                """.trimIndent()
                
                connection.prepareStatement(query).use { statement ->
                    statement.setInt(1, limit)
                    statement.executeQuery().use { resultSet ->
                        while (resultSet.next()) {
                            val username = resultSet.getString("username")
                            val warningCount = resultSet.getInt("warning_count")
                            val lastWarningDate = resultSet.getTimestamp("last_warning_date").toLocalDateTime()
                            
                            recentWarnings.add(RecentWarningInfo(username, warningCount, lastWarningDate))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logger.log(Level.SEVERE, "최근 경고 내역 조회 중 오류 발생: ${e.message}", e)
        }
        
        return recentWarnings
    }
    
    /**
     * 경고 내역 분석 수행
     * 
     * @param sampleSize 분석할 경고 샘플 수
     * @return 경고 분석 결과
     */
    fun analyzeWarnings(sampleSize: Int = 20): WarningAnalysisResult {
        // 더 많은 데이터로 분석 수행
        val recentWarnings = getRecentWarnings(sampleSize)
        
        // 반복 위반자 식별 (최근 7일 내 2회 이상 경고 받은 사용자)
        val repeatedOffenders = findRepeatedOffenders(recentWarnings)
        
        // 위험도 평가
        val riskAssessment = assessRisk(recentWarnings, repeatedOffenders)
        
        // 추천 조치 생성
        val recommendedActions = generateRecommendations(recentWarnings, repeatedOffenders)
        
        return WarningAnalysisResult(
            recentWarnings = recentWarnings.take(10),  // 결과에는 최대 10명만 포함
            repeatedOffenders = repeatedOffenders,
            riskAssessment = riskAssessment,
            recommendedActions = recommendedActions
        )
    }
    
    /**
     * 반복 위반자 식별
     */
    private fun findRepeatedOffenders(recentWarnings: List<RecentWarningInfo>): List<RepeatedOffenderInfo> {
        val repeatedOffenders = mutableListOf<RepeatedOffenderInfo>()
        val now = LocalDateTime.now()
        
        // 최근 경고 받은 사용자 중 위험도 평가
        for (warning in recentWarnings) {
            val daysSinceLastWarning = ChronoUnit.DAYS.between(warning.lastWarningDate, now)
            
            // 일주일 이내 경고 받은 사용자 중 2회 이상 경고 받은 사용자를 중점 관찰 대상으로 설정
            if (daysSinceLastWarning <= 7 && warning.warningCount >= 2) {
                // 위험도 계산
                val riskLevel = when {
                    warning.warningCount >= 4 -> RiskLevel.HIGH
                    warning.warningCount >= 3 -> RiskLevel.MEDIUM
                    else -> RiskLevel.LOW
                }
                
                repeatedOffenders.add(RepeatedOffenderInfo(
                    username = warning.username,
                    totalWarnings = warning.warningCount,
                    recentWarnings = warning.warningCount,  // 정확한 계산을 위해서는 추가 쿼리 필요
                    riskLevel = riskLevel
                ))
            }
        }
        
        // 위험도 순으로 정렬
        return repeatedOffenders.sortedByDescending { it.riskLevel }
    }
    
    /**
     * 경고 시스템 현황 평가
     */
    private fun assessRisk(recentWarnings: List<RecentWarningInfo>, repeatedOffenders: List<RepeatedOffenderInfo>): String {
        val now = LocalDateTime.now()
        
        // 최근 24시간 내 경고 횟수
        val warningsLast24Hours = recentWarnings.count { 
            ChronoUnit.HOURS.between(it.lastWarningDate, now) <= 24 
        }
        
        // 위험 수준 플레이어 수
        val highRiskPlayers = repeatedOffenders.count { it.riskLevel == RiskLevel.HIGH }
        
        // 시스템 평가 메시지 구성
        val assessment = StringBuilder()
        
        if (warningsLast24Hours > 5) {
            assessment.append("⚠️ 최근 24시간 내 경고가 ${warningsLast24Hours}회로 평소보다 많습니다.\n")
        } else {
            assessment.append("✅ 최근 24시간 내 경고는 ${warningsLast24Hours}회로 정상 범위 내입니다.\n")
        }
        
        if (highRiskPlayers > 0) {
            assessment.append("⚠️ 주의가 필요한 고위험 플레이어가 ${highRiskPlayers}명 있습니다.\n")
        } else if (repeatedOffenders.isNotEmpty()) {
            assessment.append("ℹ️ ${repeatedOffenders.size}명의 플레이어가 반복적으로 경고를 받고 있어 관찰이 필요합니다.\n")
        } else {
            assessment.append("✅ 현재 반복 위반자가 없습니다.\n")
        }
        
        return assessment.toString().trim()
    }
    
    /**
     * 추천 조치 생성
     */
    private fun generateRecommendations(recentWarnings: List<RecentWarningInfo>, repeatedOffenders: List<RepeatedOffenderInfo>): List<String> {
        val recommendations = mutableListOf<String>()
        
        // 고위험 플레이어에 대한 권장 사항
        val highRiskPlayers = repeatedOffenders.filter { it.riskLevel == RiskLevel.HIGH }
        if (highRiskPlayers.isNotEmpty()) {
            val playerNames = highRiskPlayers.joinToString(", ") { it.username }
            recommendations.add("⚠️ 다음 플레이어들을 중점 모니터링하세요: $playerNames")
        }
        
        // 차단 임계값에 가까운 플레이어에 대한 권장 사항
        val nearThresholdPlayers = recentWarnings.filter { it.warningCount >= AUTO_BAN_THRESHOLD - 1 }
        if (nearThresholdPlayers.isNotEmpty()) {
            val playerNames = nearThresholdPlayers.joinToString(", ") { "${it.username}(${it.warningCount}회)" }
            recommendations.add("⚠️ 다음 플레이어들은 자동 차단 임계값에 근접했습니다: $playerNames")
        }
        
        // 일반적인 권장 사항
        if (recentWarnings.isNotEmpty()) {
            val mostRecentWarning = recentWarnings.first()
            val daysSinceLastWarning = ChronoUnit.DAYS.between(mostRecentWarning.lastWarningDate, LocalDateTime.now())
            
            if (daysSinceLastWarning <= 1) {
                recommendations.add("ℹ️ 최근 활발한 경고 활동이 있으므로 서버 채팅을 모니터링하는 것이 좋습니다.")
            }
        }
        
        return recommendations
    }
    
    /**
     * BanManager 인스턴스 반환
     */
    fun getBanManager(): BanManager {
        return banManager
    }
}
