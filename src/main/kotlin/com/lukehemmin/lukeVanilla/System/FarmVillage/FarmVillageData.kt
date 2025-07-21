package com.lukehemmin.lukeVanilla.System.FarmVillage

import com.google.gson.Gson
import com.lukehemmin.lukeVanilla.System.Database.Database
import org.bukkit.Location
import org.bukkit.entity.Player
import java.time.ZoneId
import java.util.UUID

data class PlotPartInfo(val plotNumber: Int, val plotPart: Int, val world: String, val chunkX: Int, val chunkZ: Int)
data class PackageItem(val slot: Int, val itemType: String, val identifier: String, val itemData: String?)
data class NPCMerchant(
    val shopId: String,
    val npcId: Int
)

class FarmVillageData(private val database: Database) {
    
    private lateinit var plugin: com.lukehemmin.lukeVanilla.Main

    private val gson = Gson()
    private val TABLE_PLOTS = "farmvillage_plots"
    private val TABLE_NPC_MERCHANTS = "farmvillage_npc_merchants"
    private val TABLE_PACKAGE_ITEMS = "farmvillage_package_items"
    private val TABLE_SEED_TRADES = "farmvillage_seed_trades"
    private val TABLE_PURCHASE_HISTORY = "farmvillage_purchase_history"
    private val TABLE_WEEKLY_SCROLL_PURCHASES = "farmvillage_weekly_scroll_purchases"
    private val TABLE_WEEKLY_SCROLL_CONFIG = "farmvillage_weekly_scroll_config"

    init {
        createTables()
    }
    
    fun setPlugin(plugin: com.lukehemmin.lukeVanilla.Main) {
        this.plugin = plugin
    }

    private fun createTables() {
        val sqlPlots = """
            CREATE TABLE IF NOT EXISTS $TABLE_PLOTS (
                plot_number INT NOT NULL,
                plot_part INT NOT NULL,
                world VARCHAR(255) NOT NULL,
                chunk_x INT NOT NULL,
                chunk_z INT NOT NULL,
                PRIMARY KEY (plot_number, plot_part)
            );
        """.trimIndent()

        
        val sqlPackageItems = """
            CREATE TABLE IF NOT EXISTS $TABLE_PACKAGE_ITEMS (
                slot INT NOT NULL,
                item_type VARCHAR(255) NOT NULL,
                item_identifier VARCHAR(255) NOT NULL,
                item_data TEXT,
                PRIMARY KEY (slot)
            );
        """.trimIndent()

        val sqlSeedTrades = """
            CREATE TABLE IF NOT EXISTS $TABLE_SEED_TRADES (
                player_uuid VARCHAR(36) NOT NULL,
                seed_id VARCHAR(255) NOT NULL,
                traded_amount INT NOT NULL DEFAULT 0,
                trade_date DATE NOT NULL,
                PRIMARY KEY (player_uuid, seed_id)
            );
        """.trimIndent()

        val sqlPurchaseHistory = """
            CREATE TABLE IF NOT EXISTS $TABLE_PURCHASE_HISTORY (
                player_uuid VARCHAR(36) NOT NULL,
                item_id VARCHAR(100) NOT NULL,
                purchase_date DATE NOT NULL,
                purchase_count INT NOT NULL DEFAULT 1,
                INDEX idx_player_date (player_uuid, purchase_date)
            );
        """.trimIndent()

        val sqlNPCMerchants = """
            CREATE TABLE IF NOT EXISTS $TABLE_NPC_MERCHANTS (
                shop_id VARCHAR(255) NOT NULL PRIMARY KEY,
                npc_id INT NOT NULL
            );
        """.trimIndent()

        val sqlWeeklyScrollPurchases = """
            CREATE TABLE IF NOT EXISTS $TABLE_WEEKLY_SCROLL_PURCHASES (
                player_uuid VARCHAR(36) NOT NULL,
                purchase_week VARCHAR(10) NOT NULL,
                scroll_id VARCHAR(100) NOT NULL,
                season_name VARCHAR(50) NOT NULL,
                purchase_date DATE NOT NULL,
                purchase_timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                PRIMARY KEY (player_uuid, purchase_week),
                INDEX idx_week (purchase_week),
                INDEX idx_season (season_name)
            );
        """.trimIndent()
        
        val sqlWeeklyScrollConfig = """
            CREATE TABLE IF NOT EXISTS $TABLE_WEEKLY_SCROLL_CONFIG (
                id INT PRIMARY KEY DEFAULT 1,
                current_week_override VARCHAR(10) NULL,
                override_enabled BOOLEAN DEFAULT FALSE,
                last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
            );
        """.trimIndent()

        database.getConnection().use { connection ->
            connection.createStatement().use { statement ->
                statement.executeUpdate(sqlPlots)
                statement.executeUpdate(sqlNPCMerchants)
                statement.executeUpdate(sqlPackageItems)
                statement.executeUpdate(sqlSeedTrades)
                statement.executeUpdate(sqlPurchaseHistory)
                statement.executeUpdate(sqlWeeklyScrollPurchases)
                statement.executeUpdate(sqlWeeklyScrollConfig)
            }
        }
    }
    
    fun setPlotLocation(plotNumber: Int, plotPart: Int, location: Location) {
        val query = """
            REPLACE INTO $TABLE_PLOTS (plot_number, plot_part, world, chunk_x, chunk_z) 
            VALUES (?, ?, ?, ?, ?)
        """.trimIndent()
        database.getConnection().use { connection ->
            connection.prepareStatement(query).use { statement ->
                statement.setInt(1, plotNumber)
                statement.setInt(2, plotPart)
                statement.setString(3, location.world.name)
                statement.setInt(4, location.chunk.x)
                statement.setInt(5, location.chunk.z)
                statement.executeUpdate()
            }
        }
    }



    fun savePackageItems(items: List<PackageItem>) {
        val deleteQuery = "DELETE FROM $TABLE_PACKAGE_ITEMS"
        val insertQuery = "INSERT INTO $TABLE_PACKAGE_ITEMS (slot, item_type, item_identifier, item_data) VALUES (?, ?, ?, ?)"
        
        database.getConnection().use { connection ->
            connection.autoCommit = false
            try {
                connection.prepareStatement(deleteQuery).use { it.executeUpdate() }
                connection.prepareStatement(insertQuery).use { statement ->
                    for (item in items) {
                        statement.setInt(1, item.slot)
                        statement.setString(2, item.itemType)
                        statement.setString(3, item.identifier)
                        statement.setString(4, item.itemData)
                        statement.addBatch()
                    }
                    statement.executeBatch()
                }
                connection.commit()
            } catch (e: Exception) {
                connection.rollback()
                throw e
            } finally {
                connection.autoCommit = true
            }
        }
    }

    fun getPackageItems(): List<PackageItem> {
        val items = mutableListOf<PackageItem>()
        val query = "SELECT slot, item_type, item_identifier, item_data FROM $TABLE_PACKAGE_ITEMS"
        database.getConnection().use { connection ->
            connection.prepareStatement(query).use { statement ->
                statement.executeQuery().use { rs ->
                    while (rs.next()) {
                        items.add(
                            PackageItem(
                                rs.getInt("slot"),
                                rs.getString("item_type"),
                                rs.getString("item_identifier"),
                                rs.getString("item_data")
                            )
                        )
                    }
                }
            }
        }
        return items
    }

    fun getPlotPart(plotNumber: Int, plotPart: Int): PlotPartInfo? {
        val query = "SELECT world, chunk_x, chunk_z FROM $TABLE_PLOTS WHERE plot_number = ? AND plot_part = ?"
        database.getConnection().use { connection ->
            connection.prepareStatement(query).use { statement ->
                statement.setInt(1, plotNumber)
                statement.setInt(2, plotPart)
                statement.executeQuery().use { rs ->
                    if (rs.next()) {
                        return PlotPartInfo(
                            plotNumber,
                            plotPart,
                            rs.getString("world"),
                            rs.getInt("chunk_x"),
                            rs.getInt("chunk_z")
                        )
                    }
                    }
                }
            }
        return null
    }

    fun getAllPlotParts(): List<PlotPartInfo> {
        val plots = mutableListOf<PlotPartInfo>()
        val query = "SELECT plot_number, plot_part, world, chunk_x, chunk_z FROM $TABLE_PLOTS ORDER BY plot_number ASC, plot_part ASC"
        database.getConnection().use { connection ->
            connection.prepareStatement(query).use { statement ->
                statement.executeQuery().use { rs ->
                    while (rs.next()) {
                        plots.add(
                            PlotPartInfo(
                                rs.getInt("plot_number"),
                                rs.getInt("plot_part"),
                                rs.getString("world"),
                                rs.getInt("chunk_x"),
                                rs.getInt("chunk_z")
                            )
                        )
                    }
                }
            }
        }
        return plots
    }

    fun getTodaysTradeAmount(playerUUID: UUID, seedId: String): Int {
        val query = "SELECT traded_amount, trade_date FROM $TABLE_SEED_TRADES WHERE player_uuid = ? AND seed_id = ?"
        database.getConnection().use { connection ->
            connection.prepareStatement(query).use { statement ->
                statement.setString(1, playerUUID.toString())
                statement.setString(2, seedId)
                statement.executeQuery().use { rs ->
                    if (rs.next()) {
                        val lastTradeDate = rs.getDate("trade_date").toLocalDate()
                        // Always use KST (Asia/Seoul) for today's date
                        val today = java.time.LocalDate.now(ZoneId.of("Asia/Seoul"))
                        // If the record is from today, return the amount. Otherwise, it's effectively 0 for today.
                        if (lastTradeDate.isEqual(today)) {
                            return rs.getInt("traded_amount")
                        }
                    }
                }
            }
        }
        return 0 // No record for today, so 0 traded
    }

    fun recordSeedTrade(playerUUID: UUID, seedId: String, amount: Int) {
        val query = """
            INSERT INTO $TABLE_SEED_TRADES (player_uuid, seed_id, traded_amount, trade_date)
            VALUES (?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE 
            traded_amount = IF(trade_date = VALUES(trade_date), traded_amount + VALUES(traded_amount), VALUES(traded_amount)),
            trade_date = VALUES(trade_date)
        """.trimIndent()
        // Always use KST for the trade date
        val kstDate = java.sql.Date.valueOf(java.time.LocalDate.now(ZoneId.of("Asia/Seoul")))
        database.getConnection().use { connection ->
            connection.prepareStatement(query).use { statement ->
                statement.setString(1, playerUUID.toString())
                statement.setString(2, seedId)
                statement.setInt(3, amount)
                statement.setDate(4, kstDate)
                statement.executeUpdate()
            }
        }
    }

    fun getLifetimePurchaseAmount(playerUUID: UUID, itemId: String): Int {
        val query = "SELECT total_purchased FROM $TABLE_PURCHASE_HISTORY WHERE player_uuid = ? AND item_id = ?"
        database.getConnection().use { connection ->
            connection.prepareStatement(query).use { statement ->
                statement.setString(1, playerUUID.toString())
                statement.setString(2, itemId)
                statement.executeQuery().use { rs ->
                    if (rs.next()) {
                        return rs.getInt("total_purchased")
                    }
                }
            }
        }
        return 0
    }
    
    fun recordPurchase(playerUUID: UUID, itemId: String, amount: Int) {
        val query = """
            INSERT INTO $TABLE_PURCHASE_HISTORY (player_uuid, item_id, total_purchased)
            VALUES (?, ?, ?)
            ON DUPLICATE KEY UPDATE total_purchased = total_purchased + VALUES(total_purchased)
        """.trimIndent()
        database.getConnection().use { connection ->
            connection.prepareStatement(query).use { statement ->
                statement.setString(1, playerUUID.toString())
                statement.setString(2, itemId)
                statement.setInt(3, amount)
                statement.executeUpdate()
            }
        }
    }
    
    fun updatePurchaseAmount(playerUUID: UUID, itemId: String, newAmount: Int) {
        val query = """
            INSERT INTO $TABLE_PURCHASE_HISTORY (player_uuid, item_id, total_purchased)
            VALUES (?, ?, ?)
            ON DUPLICATE KEY UPDATE total_purchased = VALUES(total_purchased)
        """.trimIndent()
        database.getConnection().use { connection ->
            connection.prepareStatement(query).use { statement ->
                statement.setString(1, playerUUID.toString())
                statement.setString(2, itemId)
                statement.setInt(3, newAmount)
                statement.executeUpdate()
            }
        }
    }

    // NPC 상인 관련 메서드들
    fun saveNPCMerchant(shopId: String, npcId: Int) {
        val sql = "REPLACE INTO $TABLE_NPC_MERCHANTS (shop_id, npc_id) VALUES (?, ?)"
        database.getConnection().use { connection ->
            connection.prepareStatement(sql).use { statement ->
                statement.setString(1, shopId)
                statement.setInt(2, npcId)
                statement.executeUpdate()
            }
        }
    }

    fun getAllNPCMerchants(): List<NPCMerchant> {
        val merchants = mutableListOf<NPCMerchant>()
        val sql = "SELECT * FROM $TABLE_NPC_MERCHANTS"
        database.getConnection().use { connection ->
            connection.prepareStatement(sql).use { statement ->
                statement.executeQuery().use { rs ->
                    while (rs.next()) {
                        merchants.add(NPCMerchant(
                            shopId = rs.getString("shop_id"),
                            npcId = rs.getInt("npc_id")
                        ))
                    }
                }
            }
        }
        return merchants
    }

    fun getNPCMerchantByShopId(shopId: String): NPCMerchant? {
        val sql = "SELECT * FROM $TABLE_NPC_MERCHANTS WHERE shop_id = ?"
        database.getConnection().use { connection ->
            connection.prepareStatement(sql).use { statement ->
                statement.setString(1, shopId)
                statement.executeQuery().use { rs ->
                    if (rs.next()) {
                        return NPCMerchant(
                            shopId = rs.getString("shop_id"),
                            npcId = rs.getInt("npc_id")
                        )
                    }
                }
            }
        }
        return null
    }

    fun getNPCMerchantByNPCId(npcId: Int): NPCMerchant? {
        val sql = "SELECT * FROM $TABLE_NPC_MERCHANTS WHERE npc_id = ?"
        database.getConnection().use { connection ->
            connection.prepareStatement(sql).use { statement ->
                statement.setInt(1, npcId)
                statement.executeQuery().use { rs ->
                    if (rs.next()) {
                        return NPCMerchant(
                            shopId = rs.getString("shop_id"),
                            npcId = rs.getInt("npc_id")
                        )
                    }
                }
            }
        }
        return null
    }

    fun removeNPCMerchant(shopId: String) {
        val sql = "DELETE FROM $TABLE_NPC_MERCHANTS WHERE shop_id = ?"
        database.getConnection().use { connection ->
            connection.prepareStatement(sql).use { statement ->
                statement.setString(1, shopId)
                statement.executeUpdate()
            }
        }
    }
    
    // ===== 주차별 스크롤 구매 관련 메서드들 =====
    
    /**
     * 플레이어가 해당 주차에 스크롤을 구매했는지 확인
     */
    fun hasPlayerPurchasedThisWeek(playerUUID: UUID, weekString: String): Boolean {
        val sql = "SELECT COUNT(*) FROM $TABLE_WEEKLY_SCROLL_PURCHASES WHERE player_uuid = ? AND purchase_week = ?"
        database.getConnection().use { connection ->
            connection.prepareStatement(sql).use { statement ->
                statement.setString(1, playerUUID.toString())
                statement.setString(2, weekString)
                statement.executeQuery().use { rs ->
                    if (rs.next()) {
                        return rs.getInt(1) > 0
                    }
                }
            }
        }
        return false
    }
    
    /**
     * 플레이어의 해당 주차 구매 기록 조회
     */
    fun getPlayerWeeklyPurchase(playerUUID: UUID, weekString: String): WeeklyScrollPurchase? {
        val sql = "SELECT * FROM $TABLE_WEEKLY_SCROLL_PURCHASES WHERE player_uuid = ? AND purchase_week = ?"
        database.getConnection().use { connection ->
            connection.prepareStatement(sql).use { statement ->
                statement.setString(1, playerUUID.toString())
                statement.setString(2, weekString)
                statement.executeQuery().use { rs ->
                    if (rs.next()) {
                        return WeeklyScrollPurchase(
                            playerUUID = UUID.fromString(rs.getString("player_uuid")),
                            purchaseWeek = rs.getString("purchase_week"),
                            scrollId = rs.getString("scroll_id"),
                            seasonName = rs.getString("season_name"),
                            purchaseDate = rs.getDate("purchase_date").toLocalDate(),
                            purchaseTimestamp = rs.getTimestamp("purchase_timestamp").toLocalDateTime()
                        )
                    }
                }
            }
        }
        return null
    }
    
    /**
     * 주차별 스크롤 구매 기록 저장
     */
    fun recordWeeklyScrollPurchase(
        playerUUID: UUID, 
        weekString: String, 
        scrollId: String, 
        seasonName: String
    ): Boolean {
        val sql = """
            INSERT INTO $TABLE_WEEKLY_SCROLL_PURCHASES 
            (player_uuid, purchase_week, scroll_id, season_name, purchase_date, purchase_timestamp)
            VALUES (?, ?, ?, ?, ?, ?)
        """.trimIndent()
        
        return try {
            database.getConnection().use { connection ->
                connection.prepareStatement(sql).use { statement ->
                    val kstZone = java.time.ZoneId.of("Asia/Seoul")
                    val now = java.time.LocalDateTime.now(kstZone)
                    
                    statement.setString(1, playerUUID.toString())
                    statement.setString(2, weekString)
                    statement.setString(3, scrollId)
                    statement.setString(4, seasonName)
                    statement.setDate(5, java.sql.Date.valueOf(now.toLocalDate()))
                    statement.setTimestamp(6, java.sql.Timestamp.valueOf(now))
                    
                    statement.executeUpdate() > 0
                }
            }
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * 특정 주차의 모든 구매 기록 조회 (관리용)
     */
    fun getAllPurchasesForWeek(weekString: String): List<WeeklyScrollPurchase> {
        val purchases = mutableListOf<WeeklyScrollPurchase>()
        val sql = "SELECT * FROM $TABLE_WEEKLY_SCROLL_PURCHASES WHERE purchase_week = ? ORDER BY purchase_timestamp DESC"
        
        database.getConnection().use { connection ->
            connection.prepareStatement(sql).use { statement ->
                statement.setString(1, weekString)
                statement.executeQuery().use { rs ->
                    while (rs.next()) {
                        purchases.add(
                            WeeklyScrollPurchase(
                                playerUUID = UUID.fromString(rs.getString("player_uuid")),
                                purchaseWeek = rs.getString("purchase_week"),
                                scrollId = rs.getString("scroll_id"),
                                seasonName = rs.getString("season_name"),
                                purchaseDate = rs.getDate("purchase_date").toLocalDate(),
                                purchaseTimestamp = rs.getTimestamp("purchase_timestamp").toLocalDateTime()
                            )
                        )
                    }
                }
            }
        }
        return purchases
    }
    
    /**
     * 플레이어의 모든 주차별 구매 이력 조회
     */
    fun getPlayerPurchaseHistory(playerUUID: UUID): List<WeeklyScrollPurchase> {
        val purchases = mutableListOf<WeeklyScrollPurchase>()
        val sql = "SELECT * FROM $TABLE_WEEKLY_SCROLL_PURCHASES WHERE player_uuid = ? ORDER BY purchase_timestamp DESC"
        
        database.getConnection().use { connection ->
            connection.prepareStatement(sql).use { statement ->
                statement.setString(1, playerUUID.toString())
                statement.executeQuery().use { rs ->
                    while (rs.next()) {
                        purchases.add(
                            WeeklyScrollPurchase(
                                playerUUID = UUID.fromString(rs.getString("player_uuid")),
                                purchaseWeek = rs.getString("purchase_week"),
                                scrollId = rs.getString("scroll_id"),
                                seasonName = rs.getString("season_name"),
                                purchaseDate = rs.getDate("purchase_date").toLocalDate(),
                                purchaseTimestamp = rs.getTimestamp("purchase_timestamp").toLocalDateTime()
                            )
                        )
                    }
                }
            }
        }
        return purchases
    }
    
    // ===== 주차 강제 설정 관련 메서드들 =====
    
    /**
     * 주차 강제 설정 활성화/비활성화
     */
    fun setWeekOverride(weekString: String?, enabled: Boolean): Boolean {
        val sql = """
            INSERT INTO $TABLE_WEEKLY_SCROLL_CONFIG (id, current_week_override, override_enabled)
            VALUES (1, ?, ?)
            ON DUPLICATE KEY UPDATE 
            current_week_override = VALUES(current_week_override),
            override_enabled = VALUES(override_enabled),
            last_updated = CURRENT_TIMESTAMP
        """.trimIndent()
        
        return try {
            database.getConnection().use { connection ->
                connection.prepareStatement(sql).use { statement ->
                    statement.setString(1, weekString)
                    statement.setBoolean(2, enabled)
                    statement.executeUpdate() > 0
                }
            }
        } catch (e: Exception) {
            plugin.logger.warning("주차 강제 설정 저장 실패: ${e.message}")
            false
        }
    }
    
    /**
     * 현재 주차 강제 설정 상태 조회
     */
    fun getWeekOverride(): Pair<String?, Boolean> {
        val sql = "SELECT current_week_override, override_enabled FROM $TABLE_WEEKLY_SCROLL_CONFIG WHERE id = 1"
        
        database.getConnection().use { connection ->
            connection.prepareStatement(sql).use { statement ->
                statement.executeQuery().use { rs ->
                    if (rs.next()) {
                        return Pair(
                            rs.getString("current_week_override"),
                            rs.getBoolean("override_enabled")
                        )
                    }
                }
            }
        }
        return Pair(null, false)
    }
    
    /**
     * 주차 강제 설정 비활성화 (자동 주차 계산으로 복귀)
     */
    fun disableWeekOverride(): Boolean {
        return setWeekOverride(null, false)
    }
}

/**
 * 주차별 스크롤 구매 기록 데이터 클래스
 */
data class WeeklyScrollPurchase(
    val playerUUID: UUID,
    val purchaseWeek: String,
    val scrollId: String, 
    val seasonName: String,
    val purchaseDate: java.time.LocalDate,
    val purchaseTimestamp: java.time.LocalDateTime
) 