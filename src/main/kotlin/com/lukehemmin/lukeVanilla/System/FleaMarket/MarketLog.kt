package com.lukehemmin.lukeVanilla.System.FleaMarket

import java.util.UUID

/**
 * 마켓 거래 기록 엔티티
 */
data class MarketLog(
    val id: Int,                            // 고유 ID (AUTO_INCREMENT)
    val playerUuid: UUID,                   // 거래한 플레이어 UUID
    val playerName: String,                 // 거래한 플레이어 이름
    val transactionType: MarketTransactionType, // 거래 유형 (판매/구매/회수)
    val itemName: String,                   // 아이템 이름
    val itemData: String?,                  // 아이템 직렬화 데이터 (선택적)
    val price: Double,                      // 거래 가격
    val counterpartUuid: UUID?,             // 거래 상대방 UUID (구매/판매 시)
    val counterpartName: String?,           // 거래 상대방 이름
    val transactionAt: Long,                // 거래 시간 (timestamp)
    val isNotified: Boolean = false         // 알림 확인 여부 (SELL 타입에만 사용)
)
