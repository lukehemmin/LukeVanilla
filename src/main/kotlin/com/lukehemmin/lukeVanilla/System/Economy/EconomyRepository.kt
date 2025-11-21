package com.lukehemmin.lukeVanilla.System.Economy

import com.lukehemmin.lukeVanilla.System.Database.Database
import java.sql.SQLException
import java.sql.Timestamp
import java.util.UUID
import java.util.concurrent.CompletableFuture

data class EconomyLog(
    val id: Long,
    val type: TransactionType,
    val amount: Double,
    val balanceAfter: Double,
    val relatedUuid: String?,
    val description: String?,
    val date: Timestamp
)

class EconomyRepository(private val database: Database) {

    init {
        initializeTables()
    }

    private fun initializeTables() {
        CompletableFuture.runAsync {
            try {
                database.getConnection().use { connection ->
                    connection.createStatement().use { statement ->
                        // 1. 플레이어 잔액 테이블 (기존 유지 및 보완)
                        statement.executeUpdate("""
                            CREATE TABLE IF NOT EXISTS player_balance (
                                uuid VARCHAR(36) PRIMARY KEY,
                                balance DECIMAL(20, 2) DEFAULT 0,
                                last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                            )
                        """)
                        
                        // 2. 거래 내역(로그) 테이블 (신규 생성)
                        statement.executeUpdate("""
                            CREATE TABLE IF NOT EXISTS economy_logs (
                                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                player_uuid VARCHAR(36) NOT NULL,
                                transaction_type VARCHAR(32) NOT NULL,
                                amount DECIMAL(20, 2) NOT NULL,
                                balance_after DECIMAL(20, 2) NOT NULL,
                                related_uuid VARCHAR(36),
                                description TEXT,
                                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                INDEX idx_player_uuid (player_uuid),
                                INDEX idx_created_at (created_at)
                            )
                        """)
                    }
                }
            } catch (e: SQLException) {
                e.printStackTrace()
            }
        }
    }

    // 비동기로 잔액 조회
    fun getBalanceAsync(uuid: UUID): CompletableFuture<Double> {
        return CompletableFuture.supplyAsync {
            var balance = 0.0
            try {
                database.getConnection().use { connection ->
                    val query = "SELECT balance FROM player_balance WHERE uuid = ?"
                    connection.prepareStatement(query).use { stmt ->
                        stmt.setString(1, uuid.toString())
                        stmt.executeQuery().use { rs ->
                            if (rs.next()) {
                                balance = rs.getDouble("balance")
                            } else {
                                // 데이터가 없으면 0원으로 초기화 (Insert)
                                createAccount(uuid)
                            }
                        }
                    }
                }
            } catch (e: SQLException) {
                e.printStackTrace()
            }
            balance
        }
    }

    // 계좌 생성 (내부용)
    private fun createAccount(uuid: UUID) {
        try {
            database.getConnection().use { connection ->
                val query = "INSERT IGNORE INTO player_balance (uuid, balance) VALUES (?, 0.0)"
                connection.prepareStatement(query).use { stmt ->
                    stmt.setString(1, uuid.toString())
                    stmt.executeUpdate()
                }
            }
        } catch (e: SQLException) {
            e.printStackTrace()
        }
    }

    // 잔액 업데이트 (비동기)
    fun updateBalance(uuid: UUID, newBalance: Double) {
        CompletableFuture.runAsync {
            try {
                database.getConnection().use { connection ->
                    val query = "UPDATE player_balance SET balance = ? WHERE uuid = ?"
                    connection.prepareStatement(query).use { stmt ->
                        stmt.setDouble(1, newBalance)
                        stmt.setString(2, uuid.toString())
                        stmt.executeUpdate()
                    }
                }
            } catch (e: SQLException) {
                e.printStackTrace()
            }
        }
    }

    // 로그 기록
    fun insertLog(uuid: UUID, type: TransactionType, amount: Double, balanceAfter: Double, relatedUuid: UUID?, description: String) {
        CompletableFuture.runAsync {
            try {
                database.getConnection().use { connection ->
                    val query = """
                        INSERT INTO economy_logs 
                        (player_uuid, transaction_type, amount, balance_after, related_uuid, description) 
                        VALUES (?, ?, ?, ?, ?, ?)
                    """
                    connection.prepareStatement(query).use { stmt ->
                        stmt.setString(1, uuid.toString())
                        stmt.setString(2, type.name)
                        stmt.setDouble(3, amount)
                        stmt.setDouble(4, balanceAfter)
                        stmt.setString(5, relatedUuid?.toString())
                        stmt.setString(6, description)
                        stmt.executeUpdate()
                    }
                }
            } catch (e: SQLException) {
                e.printStackTrace()
            }
        }
    }

    // 최근 로그 조회 (비동기)
    fun getRecentLogsAsync(uuid: UUID, limit: Int = 10): CompletableFuture<List<EconomyLog>> {
        return CompletableFuture.supplyAsync {
            val logs = mutableListOf<EconomyLog>()
            try {
                database.getConnection().use { connection ->
                    val query = """
                        SELECT * FROM economy_logs 
                        WHERE player_uuid = ? 
                        ORDER BY created_at DESC 
                        LIMIT ?
                    """
                    connection.prepareStatement(query).use { stmt ->
                        stmt.setString(1, uuid.toString())
                        stmt.setInt(2, limit)
                        stmt.executeQuery().use { rs ->
                            while (rs.next()) {
                                logs.add(EconomyLog(
                                    id = rs.getLong("id"),
                                    type = TransactionType.valueOf(rs.getString("transaction_type")),
                                    amount = rs.getDouble("amount"),
                                    balanceAfter = rs.getDouble("balance_after"),
                                    relatedUuid = rs.getString("related_uuid"),
                                    description = rs.getString("description"),
                                    date = rs.getTimestamp("created_at")
                                ))
                            }
                        }
                    }
                }
            } catch (e: SQLException) {
                e.printStackTrace()
            }
            logs
        }
    }
}
