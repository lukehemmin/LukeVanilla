package com.lukehemmin.lukeVanilla.System.FarmVillage

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoField
import java.time.temporal.WeekFields
import java.util.*

/**
 * 주차별 스크롤 로테이션 데이터 클래스
 */
data class ScrollRotationWeek(
    val seasonName: String,
    val displayName: String,
    val scrollIds: List<String>,
    val themeColor: String
)

/**
 * 주간 스크롤 로테이션 관리 시스템
 * 매주 월요일마다 다른 시즌의 스크롤로 교환 가능
 */
class WeeklyScrollRotationSystem {
    
    companion object {
        private val KST_ZONE = ZoneId.of("Asia/Seoul")
    }
    
    private var farmVillageData: FarmVillageData? = null
    
    fun setFarmVillageData(data: FarmVillageData) {
        this.farmVillageData = data
    }
    
    // 확장 가능한 로테이션 설정 (순서대로 로테이션)
    private val scrollRotations = listOf(
        ScrollRotationWeek(
            seasonName = "halloween",
            displayName = "할로윈",
            scrollIds = listOf(
                "h_sword_scroll", "h_pickaxe_scroll", "h_axe_scroll", "h_shovel_scroll", "h_hoe_scroll",
                "h_bow_scroll", "h_rod_scroll", "h_hammer_scroll", "h_hat_scroll", "h_scythe_scroll", "h_spear_scroll"
            ),
            themeColor = "ORANGE" // 할로윈 - 주황색
        ),
        ScrollRotationWeek(
            seasonName = "christmas",
            displayName = "크리스마스", 
            scrollIds = listOf(
                "c_sword_scroll", "c_pickaxe_scroll", "c_axe_scroll", "c_shovel_scroll", "c_hoe_scroll",
                "c_bow_scroll", "c_crossbow_scroll", "c_fishing_rod_scroll", "c_hammer_scroll",
                "c_shield_scroll", "c_head_scroll", "c_helmet_scroll", "c_chestplate_scroll",
                "c_leggings_scroll", "c_boots_scroll"
            ),
            themeColor = "GREEN" // 크리스마스 - 초록색
        ),
        ScrollRotationWeek(
            seasonName = "valentine",
            displayName = "발렌타인",
            scrollIds = listOf(
                "v_sword_scroll", "v_pickaxe_scroll", "v_axe_scroll", "v_shovel_scroll", "v_hoe_scroll",
                "v_bow_scroll", "v_crossbow_scroll", "v_fishing_rod_scroll", "v_hammer_scroll",
                "v_helmet_scroll", "v_chestplate_scroll", "v_leggings_scroll", "v_boots_scroll",
                "v_head_scroll", "v_shield_scroll"
            ),
            themeColor = "PINK" // 발렌타인 - 핑크색
        )
        // 추후 확장: 새 시즌 등 여기에 추가
    )
    
    /**
     * 현재 주차 문자열 반환 (KST 기준, 강제 설정 고려)
     * 예: "2025-W03"
     * ISO 8601 표준 주차 시스템 사용 (월요일 주 시작, 첫 번째 목요일이 포함된 주가 1주차)
     */
    fun getCurrentWeekString(): String {
        // 강제 설정된 주차가 있는지 확인
        farmVillageData?.let { data ->
            val (overrideWeek, enabled) = data.getWeekOverride()
            if (enabled && overrideWeek != null) {
                return overrideWeek
            }
        }
        
        // ISO 8601 표준 주차 계산 (KST 기준)
        val now = LocalDate.now(KST_ZONE)
        val weekFields = WeekFields.ISO
        val year = now.get(weekFields.weekBasedYear())
        val week = now.get(weekFields.weekOfWeekBasedYear())
        return String.format("%04d-W%02d", year, week)
    }
    
    /**
     * 현재 로테이션 인덱스 계산
     */
    fun getCurrentRotationIndex(): Int {
        val weekString = getCurrentWeekString()
        val weekNumber = extractWeekNumber(weekString)
        return weekNumber % scrollRotations.size
    }
    
    /**
     * 현재 주의 로테이션 정보 반환
     */
    fun getCurrentRotation(): ScrollRotationWeek {
        val index = getCurrentRotationIndex()
        return scrollRotations[index]
    }
    
    /**
     * 다음 주의 로테이션 정보 반환 (미리보기용)
     */
    fun getNextRotation(): ScrollRotationWeek {
        val nextIndex = (getCurrentRotationIndex() + 1) % scrollRotations.size
        return scrollRotations[nextIndex]
    }
    
    /**
     * 전체 로테이션 목록 반환 (관리용)
     */
    fun getAllRotations(): List<ScrollRotationWeek> {
        return scrollRotations.toList()
    }
    
    /**
     * 특정 주차의 로테이션 인덱스 계산
     */
    fun getRotationIndexForWeek(weekString: String): Int {
        val weekNumber = extractWeekNumber(weekString)
        return weekNumber % scrollRotations.size
    }
    
    /**
     * 특정 주차의 로테이션 정보 반환
     */
    fun getRotationForWeek(weekString: String): ScrollRotationWeek {
        val index = getRotationIndexForWeek(weekString)
        return scrollRotations[index]
    }
    
    /**
     * 스크롤 ID로 시즌 이름 찾기
     */
    fun getSeasonNameByScrollId(scrollId: String): String? {
        for (rotation in scrollRotations) {
            if (scrollId in rotation.scrollIds) {
                return rotation.seasonName
            }
        }
        return null
    }
    
    /**
     * 주차 문자열에서 주차 번호 추출
     * 예: "2025-W03" -> 3
     */
    private fun extractWeekNumber(weekString: String): Int {
        return try {
            weekString.substringAfter("-W").toInt()
        } catch (e: NumberFormatException) {
            1 // 기본값
        }
    }
    
    /**
     * 다음 월요일까지 남은 시간 계산 (디버그/표시용)
     */
    fun getTimeUntilNextRotation(): String {
        val now = LocalDate.now(KST_ZONE)
        val nextMonday = if (now.dayOfWeek == DayOfWeek.MONDAY) {
            // 오늘이 월요일이면 다음 주 월요일
            now.plusWeeks(1)
        } else {
            // 이번 주 또는 다음 주 월요일 찾기
            now.with(DayOfWeek.MONDAY).let { thisMonday ->
                if (thisMonday.isAfter(now)) thisMonday else thisMonday.plusWeeks(1)
            }
        }
        val daysRemaining = java.time.temporal.ChronoUnit.DAYS.between(now, nextMonday)
        return when (daysRemaining.toInt()) {
            0 -> "오늘 자정"
            1 -> "내일"
            else -> "${daysRemaining}일 후"
        }
    }
    
    /**
     * 로테이션 설정 유효성 검증
     */
    fun validateRotations(): List<String> {
        val errors = mutableListOf<String>()
        
        if (scrollRotations.isEmpty()) {
            errors.add("로테이션이 설정되지 않았습니다.")
            return errors
        }
        
        for ((index, rotation) in scrollRotations.withIndex()) {
            if (rotation.scrollIds.isEmpty()) {
                errors.add("로테이션 #${index + 1} (${rotation.seasonName})에 스크롤이 없습니다.")
            }
            
            if (rotation.seasonName.isBlank()) {
                errors.add("로테이션 #${index + 1}의 시즌 이름이 비어있습니다.")
            }
        }
        
        return errors
    }
    
    /**
     * 강제로 주차 설정 (관리자용)
     */
    fun forceSetWeek(weekString: String): Boolean {
        return farmVillageData?.setWeekOverride(weekString, true) ?: false
    }
    
    /**
     * 다음주로 강제 이동
     */
    fun forceNextWeek(): String? {
        val currentWeek = getCurrentWeekString()
        val nextWeek = getNextWeekString(currentWeek)
        return if (farmVillageData?.setWeekOverride(nextWeek, true) == true) {
            nextWeek
        } else {
            null
        }
    }
    
    /**
     * 이전주로 강제 이동
     */
    fun forcePreviousWeek(): String? {
        val currentWeek = getCurrentWeekString()
        val prevWeek = getPreviousWeekString(currentWeek)
        return if (farmVillageData?.setWeekOverride(prevWeek, true) == true) {
            prevWeek
        } else {
            null
        }
    }
    
    /**
     * 강제 설정 해제 (자동 주차 계산으로 복귀)
     */
    fun disableForceMode(): Boolean {
        return farmVillageData?.disableWeekOverride() ?: false
    }
    
    /**
     * 강제 설정 상태 확인
     */
    fun getForceStatus(): Pair<String?, Boolean> {
        return farmVillageData?.getWeekOverride() ?: Pair(null, false)
    }
    
    private fun getNextWeekString(currentWeek: String): String {
        try {
            val parts = currentWeek.split("-W")
            val year = parts[0].toInt()
            val week = parts[1].toInt()
            
            // ISO 8601에서 해당 연도의 마지막 주차 계산
            val lastWeekOfYear = getLastWeekOfYear(year)
            
            return if (week >= lastWeekOfYear) {
                String.format("%04d-W%02d", year + 1, 1)
            } else {
                String.format("%04d-W%02d", year, week + 1)
            }
        } catch (e: Exception) {
            return getCurrentWeekString()
        }
    }
    
    private fun getPreviousWeekString(currentWeek: String): String {
        try {
            val parts = currentWeek.split("-W")
            val year = parts[0].toInt()
            val week = parts[1].toInt()
            
            return if (week <= 1) {
                val previousYear = year - 1
                val lastWeekOfPreviousYear = getLastWeekOfYear(previousYear)
                String.format("%04d-W%02d", previousYear, lastWeekOfPreviousYear)
            } else {
                String.format("%04d-W%02d", year, week - 1)
            }
        } catch (e: Exception) {
            return getCurrentWeekString()
        }
    }
    
    /**
     * ISO 8601 기준으로 해당 연도의 마지막 주차 계산
     */
    private fun getLastWeekOfYear(year: Int): Int {
        val lastDayOfYear = LocalDate.of(year, 12, 31)
        val weekFields = WeekFields.ISO
        return lastDayOfYear.get(weekFields.weekOfWeekBasedYear())
    }
    
    /**
     * 디버깅용 주차 정보 반환
     */
    fun getDebugInfo(): Map<String, Any> {
        val now = LocalDate.now(KST_ZONE)
        val weekFields = WeekFields.ISO
        
        return mapOf(
            "currentDate" to now.toString(),
            "dayOfWeek" to now.dayOfWeek.toString(),
            "currentWeekString" to getCurrentWeekString(),
            "weekBasedYear" to now.get(weekFields.weekBasedYear()),
            "weekOfWeekBasedYear" to now.get(weekFields.weekOfWeekBasedYear()),
            "currentRotation" to getCurrentRotation().seasonName,
            "currentRotationDisplay" to getCurrentRotation().displayName,
            "nextRotation" to getNextRotation().seasonName,
            "forceStatus" to getForceStatus(),
            "timeUntilNext" to getTimeUntilNextRotation()
        )
    }
}
