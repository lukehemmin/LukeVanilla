package com.lukehemmin.lukeVanilla.System.FleaMarket

/**
 * 플리마켓 거래 유형
 */
enum class MarketTransactionType {
    REGISTER,   // 아이템 등록
    SELL,       // 판매 완료 (판매자 입장)
    BUY,        // 구매 완료 (구매자 입장)
    WITHDRAW    // 회수
}
