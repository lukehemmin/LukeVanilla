package com.lukehemmin.lukeVanilla.System.Economy

enum class TransactionType {
    SEND,       // 송금 (보냄)
    RECEIVE,    // 송금 (받음)
    SHOP_BUY,   // 상점 구매
    SHOP_SELL,  // 상점 판매
    ROULETTE,   // 룰렛
    ADMIN,      // 관리자 (기타)
    UNKNOWN     // 알 수 없음
}
