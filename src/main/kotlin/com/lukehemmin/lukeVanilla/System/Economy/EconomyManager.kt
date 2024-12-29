// EconomyManager.kt
package com.lukehemmin.lukeVanilla.System.Economy

import com.lukehemmin.lukeVanilla.System.Database.Database
import org.bukkit.entity.Player
import java.util.UUID

class EconomyManager(private val database: Database) {
    init {
        // 테이블 생성
        val connection = database.getConnection()
        connection.createStatement().use { statement ->
            statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS player_balance (
                    uuid VARCHAR(36) PRIMARY KEY,
                    balance DECIMAL(20, 2) DEFAULT 0
                )
            """)
        }
        connection.close()
    }

    fun getBalance(player: Player): Double {
        val connection = database.getConnection()
        connection.prepareStatement("SELECT balance FROM player_balance WHERE uuid = ?").use { statement ->
            statement.setString(1, player.uniqueId.toString())
            val resultSet = statement.executeQuery()
            if (resultSet.next()) {
                return resultSet.getDouble("balance")
            }
        }
        connection.close()
        // 계정이 없으면 생성
        setBalance(player, 0.0)
        return 0.0
    }

    fun setBalance(player: Player, amount: Double) {
        val connection = database.getConnection()
        connection.prepareStatement("""
            INSERT INTO player_balance (uuid, balance) 
            VALUES (?, ?) 
            ON DUPLICATE KEY UPDATE balance = ?
        """).use { statement ->
            statement.setString(1, player.uniqueId.toString())
            statement.setDouble(2, amount)
            statement.setDouble(3, amount)
            statement.executeUpdate()
        }
        connection.close()
    }

    fun addBalance(player: Player, amount: Double) {
        val currentBalance = getBalance(player)
        setBalance(player, currentBalance + amount)
    }

    fun removeBalance(player: Player, amount: Double): Boolean {
        val currentBalance = getBalance(player)
        if (currentBalance >= amount) {
            setBalance(player, currentBalance - amount)
            return true
        }
        return false
    }
}