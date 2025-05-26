package com.lukehemmin.lukeVanilla.System.WarningSystem

import java.time.LocalDateTime
import java.util.UUID

/**
 * 플레이어 경고 정보 모델
 */
data class PlayerWarning(
    val playerId: Int? = null,
    val uuid: UUID,
    val username: String,
    val lastWarningDate: LocalDateTime? = null,
    val activeWarningsCount: Int = 0
)

/**
 * 경고 내역 모델
 */
data class WarningRecord(
    val warningId: Int? = null,
    val playerId: Int,
    val adminUuid: UUID,
    val adminName: String,
    val reason: String,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val isActive: Boolean = true,
    val pardonedAt: LocalDateTime? = null,
    val pardonedByUuid: UUID? = null,
    val pardonedByName: String? = null,
    val pardonReason: String? = null
) {
    // 경고 상세 정보를 UI용 문자열로 변환
    fun toDetailedString(): String {
        val activeText = if (isActive) "유효" else "차감됨"
        val baseInfo = "경고 ID: $warningId | 상태: $activeText | 관리자: $adminName | 시각: $createdAt\n사유: $reason"
        
        return if (!isActive && pardonedAt != null && pardonedByName != null) {
            "$baseInfo\n차감 정보: $pardonedByName이(가) $pardonedAt에 차감함\n차감 사유: $pardonReason"
        } else {
            baseInfo
        }
    }
}

/**
 * 경고 차감 이력 모델
 */
data class WarningPardon(
    val pardonId: Int? = null,
    val playerId: Int,
    val adminUuid: UUID,
    val adminName: String,
    val count: Int = 1,
    val reason: String,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val isIdBased: Boolean,
    val warningId: Int? = null
)
