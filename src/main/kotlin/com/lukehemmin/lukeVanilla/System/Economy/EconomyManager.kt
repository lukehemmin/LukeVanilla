package com.lukehemmin.lukeVanilla.System.Economy

import com.lukehemmin.lukeVanilla.System.Database.Database
import org.bukkit.entity.Player

class EconomyManager(database: Database) {
    
    private val repository = EconomyRepository(database)
    val service = EconomyService(repository) // 외부에서 접근 가능하도록 public으로 열거나, 메서드로 위임

    // 기존 메서드 유지 (하위 호환성)
    fun getBalance(player: Player): Double {
        return service.getBalance(player)
    }

    fun removeBalance(player: Player, amount: Double): Boolean {
        // 기존 코드는 로그를 남기지 않았지만, 이제는 "UNKNOWN" 또는 "LEGACY" 타입으로 로그를 남김
        return service.withdraw(player, amount, TransactionType.UNKNOWN, "Legacy API Call")
    }

    fun addBalance(player: Player, amount: Double) {
        service.deposit(player, amount, TransactionType.UNKNOWN, "Legacy API Call")
    }
    
    // 새로운 API 메서드들
    fun deposit(player: Player, amount: Double, type: TransactionType, description: String) {
        service.deposit(player, amount, type, description)
    }

    fun withdraw(player: Player, amount: Double, type: TransactionType, description: String): Boolean {
        return service.withdraw(player, amount, type, description)
    }
}