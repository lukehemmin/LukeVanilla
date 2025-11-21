package com.lukehemmin.lukeVanilla.System.FleaMarket

import java.util.UUID

/**
 * 마켓에 등록된 아이템 엔티티
 */
data class MarketItem(
    val id: Int,                    // 고유 ID (AUTO_INCREMENT)
    val sellerUuid: UUID,           // 판매자 UUID
    val sellerName: String,         // 판매자 이름 (표시용)
    val itemData: String,           // 아이템 직렬화 데이터 (Base64)
    val price: Double,              // 판매 가격
    val registeredAt: Long          // 등록 시간 (timestamp)
)
