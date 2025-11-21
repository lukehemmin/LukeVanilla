package com.lukehemmin.lukeVanilla.System.FleaMarket

import com.lukehemmin.lukeVanilla.System.Database.Database
import java.sql.SQLException
import java.util.UUID
import java.util.concurrent.CompletableFuture

/**
 * 플리마켓 데이터베이스 Repository
 */
class FleaMarketRepository(private val database: Database) {

    init {
        initializeTables()
    }

    /**
     * 데이터베이스 테이블 초기화
     */
    private fun initializeTables() {
        CompletableFuture.runAsync {
            try {
                database.getConnection().use { connection ->
                    connection.createStatement().use { statement ->
                        // 1. 마켓 아이템 테이블
                        statement.executeUpdate("""
                            CREATE TABLE IF NOT EXISTS flea_market (
                                id INT AUTO_INCREMENT PRIMARY KEY,
                                seller_uuid VARCHAR(36) NOT NULL,
                                seller_name VARCHAR(16) NOT NULL,
                                item_data TEXT NOT NULL,
                                price DOUBLE NOT NULL,
                                registered_at BIGINT NOT NULL,
                                INDEX idx_seller_uuid (seller_uuid)
                            )
                        """)

                        // 2. 마켓 거래 로그 테이블
                        statement.executeUpdate("""
                            CREATE TABLE IF NOT EXISTS market_logs (
                                id INT AUTO_INCREMENT PRIMARY KEY,
                                player_uuid VARCHAR(36) NOT NULL,
                                player_name VARCHAR(16) NOT NULL,
                                transaction_type VARCHAR(20) NOT NULL,
                                item_name VARCHAR(255) NOT NULL,
                                item_data TEXT,
                                price DOUBLE NOT NULL,
                                counterpart_uuid VARCHAR(36),
                                counterpart_name VARCHAR(16),
                                transaction_at BIGINT NOT NULL,
                                is_notified TINYINT(1) DEFAULT 0,
                                INDEX idx_player_uuid (player_uuid),
                                INDEX idx_transaction_at (transaction_at),
                                INDEX idx_is_notified (is_notified)
                            )
                        """)
                    }
                }
            } catch (e: SQLException) {
                e.printStackTrace()
            }
        }
    }

    // ===== 아이템 관련 =====

    /**
     * 아이템 등록
     */
    fun insertItem(item: MarketItem): CompletableFuture<Int> {
        return CompletableFuture.supplyAsync {
            var generatedId = -1
            try {
                database.getConnection().use { connection ->
                    val query = """
                        INSERT INTO flea_market (seller_uuid, seller_name, item_data, price, registered_at)
                        VALUES (?, ?, ?, ?, ?)
                    """
                    connection.prepareStatement(query, java.sql.Statement.RETURN_GENERATED_KEYS).use { stmt ->
                        stmt.setString(1, item.sellerUuid.toString())
                        stmt.setString(2, item.sellerName)
                        stmt.setString(3, item.itemData)
                        stmt.setDouble(4, item.price)
                        stmt.setLong(5, item.registeredAt)
                        stmt.executeUpdate()

                        val keys = stmt.generatedKeys
                        if (keys.next()) {
                            generatedId = keys.getInt(1)
                        }
                    }
                }
            } catch (e: SQLException) {
                e.printStackTrace()
            }
            generatedId
        }
    }

    /**
     * 아이템 삭제 (구매 또는 회수)
     */
    fun deleteItem(itemId: Int): CompletableFuture<Boolean> {
        return CompletableFuture.supplyAsync {
            var success = false
            try {
                database.getConnection().use { connection ->
                    val query = "DELETE FROM flea_market WHERE id = ?"
                    connection.prepareStatement(query).use { stmt ->
                        stmt.setInt(1, itemId)
                        success = stmt.executeUpdate() > 0
                    }
                }
            } catch (e: SQLException) {
                e.printStackTrace()
            }
            success
        }
    }

    /**
     * 모든 아이템 조회
     */
    fun getAllItemsAsync(): CompletableFuture<List<MarketItem>> {
        return CompletableFuture.supplyAsync {
            val items = mutableListOf<MarketItem>()
            try {
                database.getConnection().use { connection ->
                    val query = "SELECT * FROM flea_market ORDER BY registered_at DESC"
                    connection.prepareStatement(query).use { stmt ->
                        stmt.executeQuery().use { rs ->
                            while (rs.next()) {
                                items.add(MarketItem(
                                    id = rs.getInt("id"),
                                    sellerUuid = UUID.fromString(rs.getString("seller_uuid")),
                                    sellerName = rs.getString("seller_name"),
                                    itemData = rs.getString("item_data"),
                                    price = rs.getDouble("price"),
                                    registeredAt = rs.getLong("registered_at")
                                ))
                            }
                        }
                    }
                }
            } catch (e: SQLException) {
                e.printStackTrace()
            }
            items
        }
    }

    /**
     * 판매자별 아이템 조회
     */
    fun getItemsBySellerAsync(uuid: UUID): CompletableFuture<List<MarketItem>> {
        return CompletableFuture.supplyAsync {
            val items = mutableListOf<MarketItem>()
            try {
                database.getConnection().use { connection ->
                    val query = "SELECT * FROM flea_market WHERE seller_uuid = ? ORDER BY registered_at DESC"
                    connection.prepareStatement(query).use { stmt ->
                        stmt.setString(1, uuid.toString())
                        stmt.executeQuery().use { rs ->
                            while (rs.next()) {
                                items.add(MarketItem(
                                    id = rs.getInt("id"),
                                    sellerUuid = UUID.fromString(rs.getString("seller_uuid")),
                                    sellerName = rs.getString("seller_name"),
                                    itemData = rs.getString("item_data"),
                                    price = rs.getDouble("price"),
                                    registeredAt = rs.getLong("registered_at")
                                ))
                            }
                        }
                    }
                }
            } catch (e: SQLException) {
                e.printStackTrace()
            }
            items
        }
    }

    /**
     * 특정 아이템 조회
     */
    fun getItemByIdAsync(itemId: Int): CompletableFuture<MarketItem?> {
        return CompletableFuture.supplyAsync {
            var item: MarketItem? = null
            try {
                database.getConnection().use { connection ->
                    val query = "SELECT * FROM flea_market WHERE id = ?"
                    connection.prepareStatement(query).use { stmt ->
                        stmt.setInt(1, itemId)
                        stmt.executeQuery().use { rs ->
                            if (rs.next()) {
                                item = MarketItem(
                                    id = rs.getInt("id"),
                                    sellerUuid = UUID.fromString(rs.getString("seller_uuid")),
                                    sellerName = rs.getString("seller_name"),
                                    itemData = rs.getString("item_data"),
                                    price = rs.getDouble("price"),
                                    registeredAt = rs.getLong("registered_at")
                                )
                            }
                        }
                    }
                }
            } catch (e: SQLException) {
                e.printStackTrace()
            }
            item
        }
    }

    // ===== 거래 로그 관련 =====

    /**
     * 거래 기록 삽입
     */
    fun insertLog(log: MarketLog): CompletableFuture<Void> {
        return CompletableFuture.runAsync {
            try {
                database.getConnection().use { connection ->
                    val query = """
                        INSERT INTO market_logs 
                        (player_uuid, player_name, transaction_type, item_name, item_data, price, 
                         counterpart_uuid, counterpart_name, transaction_at, is_notified)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """
                    connection.prepareStatement(query).use { stmt ->
                        stmt.setString(1, log.playerUuid.toString())
                        stmt.setString(2, log.playerName)
                        stmt.setString(3, log.transactionType.name)
                        stmt.setString(4, log.itemName)
                        stmt.setString(5, log.itemData)
                        stmt.setDouble(6, log.price)
                        stmt.setString(7, log.counterpartUuid?.toString())
                        stmt.setString(8, log.counterpartName)
                        stmt.setLong(9, log.transactionAt)
                        stmt.setBoolean(10, log.isNotified)
                        stmt.executeUpdate()
                    }
                }
            } catch (e: SQLException) {
                e.printStackTrace()
            }
        }
    }

    /**
     * 플레이어의 거래 내역 조회
     */
    fun getPlayerLogsAsync(uuid: UUID, limit: Int): CompletableFuture<List<MarketLog>> {
        return CompletableFuture.supplyAsync {
            val logs = mutableListOf<MarketLog>()
            try {
                database.getConnection().use { connection ->
                    val query = """
                        SELECT * FROM market_logs 
                        WHERE player_uuid = ? 
                        ORDER BY transaction_at DESC 
                        LIMIT ?
                    """
                    connection.prepareStatement(query).use { stmt ->
                        stmt.setString(1, uuid.toString())
                        stmt.setInt(2, limit)
                        stmt.executeQuery().use { rs ->
                            while (rs.next()) {
                                logs.add(parseMarketLog(rs))
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

    /**
     * 유형별 거래 내역 조회
     */
    fun getPlayerLogsByTypeAsync(uuid: UUID, type: MarketTransactionType, limit: Int): CompletableFuture<List<MarketLog>> {
        return CompletableFuture.supplyAsync {
            val logs = mutableListOf<MarketLog>()
            try {
                database.getConnection().use { connection ->
                    val query = """
                        SELECT * FROM market_logs 
                        WHERE player_uuid = ? AND transaction_type = ? 
                        ORDER BY transaction_at DESC 
                        LIMIT ?
                    """
                    connection.prepareStatement(query).use { stmt ->
                        stmt.setString(1, uuid.toString())
                        stmt.setString(2, type.name)
                        stmt.setInt(3, limit)
                        stmt.executeQuery().use { rs ->
                            while (rs.next()) {
                                logs.add(parseMarketLog(rs))
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

    /**
     * 미확인 판매 내역 조회 (SELL 타입, is_notified = 0)
     */
    fun getUnnotifiedSalesAsync(uuid: UUID): CompletableFuture<List<MarketLog>> {
        return CompletableFuture.supplyAsync {
            val logs = mutableListOf<MarketLog>()
            try {
                database.getConnection().use { connection ->
                    val query = """
                        SELECT * FROM market_logs 
                        WHERE player_uuid = ? AND transaction_type = 'SELL' AND is_notified = 0 
                        ORDER BY transaction_at DESC
                    """
                    connection.prepareStatement(query).use { stmt ->
                        stmt.setString(1, uuid.toString())
                        stmt.executeQuery().use { rs ->
                            while (rs.next()) {
                                logs.add(parseMarketLog(rs))
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

    /**
     * 특정 플레이어의 모든 미확인 판매 로그를 is_notified = 1로 업데이트
     */
    fun markSalesAsNotifiedAsync(uuid: UUID): CompletableFuture<Void> {
        return CompletableFuture.runAsync {
            try {
                database.getConnection().use { connection ->
                    val query = """
                        UPDATE market_logs 
                        SET is_notified = 1 
                        WHERE player_uuid = ? AND transaction_type = 'SELL' AND is_notified = 0
                    """
                    connection.prepareStatement(query).use { stmt ->
                        stmt.setString(1, uuid.toString())
                        stmt.executeUpdate()
                    }
                }
            } catch (e: SQLException) {
                e.printStackTrace()
            }
        }
    }

    /**
     * ResultSet에서 MarketLog 파싱
     */
    private fun parseMarketLog(rs: java.sql.ResultSet): MarketLog {
        return MarketLog(
            id = rs.getInt("id"),
            playerUuid = UUID.fromString(rs.getString("player_uuid")),
            playerName = rs.getString("player_name"),
            transactionType = MarketTransactionType.valueOf(rs.getString("transaction_type")),
            itemName = rs.getString("item_name"),
            itemData = rs.getString("item_data"),
            price = rs.getDouble("price"),
            counterpartUuid = rs.getString("counterpart_uuid")?.let { UUID.fromString(it) },
            counterpartName = rs.getString("counterpart_name"),
            transactionAt = rs.getLong("transaction_at"),
            isNotified = rs.getBoolean("is_notified")
        )
    }
}
