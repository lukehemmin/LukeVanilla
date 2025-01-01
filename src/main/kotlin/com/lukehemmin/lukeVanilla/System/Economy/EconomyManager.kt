package com.lukehemmin.lukeVanilla.System.Economy

import com.lukehemmin.lukeVanilla.System.Database.Database
import org.bukkit.entity.Player

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
        val uuid = player.uniqueId.toString()
        var balance = 0.0
        database.getConnection().use { connection ->
            connection.prepareStatement("SELECT balance FROM player_balance WHERE uuid = ?").use { stmt ->
                stmt.setString(1, uuid)
                stmt.executeQuery().use { rs ->
                    if (rs.next()) {
                        balance = rs.getDouble("balance")
                    } else {
                        connection.prepareStatement("INSERT INTO player_balance (uuid, balance) VALUES (?, ?)").use { insertStmt ->
                            insertStmt.setString(1, uuid)
                            insertStmt.setDouble(2, 0.0)
                            insertStmt.executeUpdate()
                        }
                    }
                }
            }
        }
        return balance
    }

    fun removeBalance(player: Player, amount: Double): Boolean {
        val currentBalance = getBalance(player)
        return if (currentBalance >= amount) {
            updateBalance(player, currentBalance - amount)
            true
        } else {
            false
        }
    }

    fun addBalance(player: Player, amount: Double) {
        val currentBalance = getBalance(player)
        updateBalance(player, currentBalance + amount)
    }

    private fun updateBalance(player: Player, newBalance: Double) {
        val uuid = player.uniqueId.toString()
        database.getConnection().use { connection ->
            val stmt = connection.prepareStatement("UPDATE player_balance SET balance = ? WHERE uuid = ?")
            stmt.setDouble(1, newBalance)
            stmt.setString(2, uuid)
            stmt.executeUpdate()
            stmt.close()
        }
    }
}