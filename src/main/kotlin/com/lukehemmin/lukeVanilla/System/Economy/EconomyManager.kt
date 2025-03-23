package com.lukehemmin.lukeVanilla.System.Economy

import com.lukehemmin.lukeVanilla.System.API.ApiClient
import com.lukehemmin.lukeVanilla.System.Database.Database
import java.math.BigDecimal
import java.util.UUID

class EconomyManager {
    private val database: Database
    private val apiClient: ApiClient?
    private val useApi: Boolean
    
    // API 클라이언트를 사용하지 않는 기본 생성자
    constructor(database: Database) {
        this.database = database
        this.apiClient = null
        this.useApi = false
    }
    
    // API 클라이언트를 사용하는 생성자
    constructor(database: Database, apiClient: ApiClient) {
        this.database = database
        this.apiClient = apiClient
        this.useApi = true
    }
    
    fun getBalance(uuid: String): BigDecimal {
        return if (useApi && apiClient != null) {
            // API를 통해 잔액 조회
            apiClient.getPlayerBalance(uuid) ?: BigDecimal.ZERO
        } else {
            // 데이터베이스에서 직접 조회
            val connection = database.getConnection()
            connection.use {
                val statement = it.prepareStatement("SELECT balance FROM player_balance WHERE uuid = ?")
                statement.setString(1, uuid)
                val resultSet = statement.executeQuery()
                
                if (resultSet.next()) {
                    resultSet.getBigDecimal("balance")
                } else {
                    // 계정이 없으면 0 반환
                    BigDecimal.ZERO
                }
            }
        }
    }
    
    fun addBalance(uuid: String, amount: BigDecimal): BigDecimal {
        if (amount < BigDecimal.ZERO) {
            throw IllegalArgumentException("Amount cannot be negative")
        }
        
        return if (useApi && apiClient != null) {
            // API를 통해 잔액 추가
            apiClient.addPlayerBalance(uuid, amount) ?: getBalance(uuid)
        } else {
            // 데이터베이스에서 직접 처리
            val connection = database.getConnection()
            connection.use {
                it.prepareStatement("INSERT INTO player_balance (uuid, balance) VALUES (?, ?) ON DUPLICATE KEY UPDATE balance = balance + ?").use { stmt ->
                    stmt.setString(1, uuid)
                    stmt.setBigDecimal(2, amount)
                    stmt.setBigDecimal(3, amount)
                    stmt.executeUpdate()
                }
            }
            
            // 업데이트된 잔액 반환
            getBalance(uuid)
        }
    }
    
    fun removeBalance(uuid: String, amount: BigDecimal): Boolean {
        if (amount < BigDecimal.ZERO) {
            throw IllegalArgumentException("Amount cannot be negative")
        }
        
        val currentBalance = getBalance(uuid)
        
        // 잔액이 충분한지 확인
        if (currentBalance < amount) {
            return false
        }
        
        return if (useApi && apiClient != null) {
            // API를 통해 잔액 차감
            apiClient.removePlayerBalance(uuid, amount)
        } else {
            // 데이터베이스에서 직접 처리
            val connection = database.getConnection()
            connection.use {
                val statement = it.prepareStatement("UPDATE player_balance SET balance = balance - ? WHERE uuid = ?")
                statement.setBigDecimal(1, amount)
                statement.setString(2, uuid)
                statement.executeUpdate() > 0
            }
        }
    }
    
    fun transferBalance(fromUuid: String, toUuid: String, amount: BigDecimal): Boolean {
        if (amount <= BigDecimal.ZERO) {
            throw IllegalArgumentException("Transfer amount must be positive")
        }
        
        // 동일한 계정 간 이체 방지
        if (fromUuid == toUuid) {
            return false
        }
        
        // 송금자의 잔액 확인
        val fromBalance = getBalance(fromUuid)
        if (fromBalance < amount) {
            return false
        }
        
        // API 또는 DB 트랜잭션으로 처리
        return if (useApi && apiClient != null) {
            val removed = apiClient.removePlayerBalance(fromUuid, amount)
            if (removed) {
                apiClient.addPlayerBalance(toUuid, amount) != null
            } else {
                false
            }
        } else {
            val connection = database.getConnection()
            try {
                connection.autoCommit = false
                
                // 송금자 잔액 차감
                val debitStmt = connection.prepareStatement("UPDATE player_balance SET balance = balance - ? WHERE uuid = ?")
                debitStmt.setBigDecimal(1, amount)
                debitStmt.setString(2, fromUuid)
                val debitResult = debitStmt.executeUpdate()
                
                // 수취인 잔액 증가
                val creditStmt = connection.prepareStatement("INSERT INTO player_balance (uuid, balance) VALUES (?, ?) ON DUPLICATE KEY UPDATE balance = balance + ?")
                creditStmt.setString(1, toUuid)
                creditStmt.setBigDecimal(2, amount)
                creditStmt.setBigDecimal(3, amount)
                val creditResult = creditStmt.executeUpdate()
                
                // 두 작업이 모두 성공했는지 확인
                if (debitResult > 0 && creditResult > 0) {
                    connection.commit()
                    true
                } else {
                    connection.rollback()
                    false
                }
            } catch (e: Exception) {
                connection.rollback()
                false
            } finally {
                connection.autoCommit = true
                connection.close()
            }
        }
    }
}