package com.lukehemmin.lukeVanilla.System.VillageMerchant

import com.lukehemmin.lukeVanilla.System.Database.Database
import org.bukkit.plugin.java.JavaPlugin
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * 마을 상인 NPC 데이터 관리 클래스
 * 농사마을에서 독립된 시스템으로 분리
 * 교환 제한 없이 돈으로만 거래하는 단순한 시스템
 * 
 * 모든 DB 호출은 비동기로 처리하여 서버 성능(TPS/MSPT)에 영향을 주지 않음
 */
class VillageMerchantData(
    private val plugin: JavaPlugin,
    private val database: Database
) {
    // 아이템 캐시 (상점 타입 -> 아이템 목록)
    private val itemCache = ConcurrentHashMap<String, List<MerchantItem>>()

    /**
     * 캐시 초기화
     * 리로드 명령어 실행 시 호출됨
     */
    fun clearCache() {
        itemCache.clear()
    }

    /**
     * NPC ID로 상점 타입 조회 (비동기)
     */
    fun getShopIdByNPCAsync(npcId: Int): CompletableFuture<String?> {
        return CompletableFuture.supplyAsync {
            getShopIdByNPC(npcId)
        }
    }

    /**
     * NPC ID로 상점 타입 조회 (동기 - 이벤트 리스너 등에서 즉시 필요한 경우)
     */
    fun getShopIdByNPC(npcId: Int): String? {
        return database.getConnection().use { connection ->
            val statement = connection.prepareStatement(
                "SELECT shop_id FROM villagemerchant_npcs WHERE npc_id = ?"
            )
            statement.setInt(1, npcId)
            val resultSet = statement.executeQuery()
            
            if (resultSet.next()) {
                resultSet.getString("shop_id")
            } else {
                null
            }
        }
    }

    /**
     * 상점 ID로 NPC ID 조회 (비동기)
     */
    fun getNPCIdByShopIdAsync(shopId: String): CompletableFuture<Int?> {
        return CompletableFuture.supplyAsync {
            getNPCIdByShopId(shopId)
        }
    }

    /**
     * 상점 ID로 NPC ID 조회 (동기)
     */
    private fun getNPCIdByShopId(shopId: String): Int? {
        return database.getConnection().use { connection ->
            val statement = connection.prepareStatement(
                "SELECT npc_id FROM villagemerchant_npcs WHERE shop_id = ?"
            )
            statement.setString(1, shopId)
            val resultSet = statement.executeQuery()
            
            if (resultSet.next()) {
                resultSet.getInt("npc_id")
            } else {
                null
            }
        }
    }

    /**
     * NPC 상인 저장 (비동기)
     */
    fun saveNPCMerchantAsync(shopId: String, npcId: Int): CompletableFuture<Boolean> {
        return CompletableFuture.supplyAsync {
            try {
                saveNPCMerchant(shopId, npcId)
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }

    /**
     * NPC 상인 저장 (동기)
     */
    fun saveNPCMerchant(shopId: String, npcId: Int) {
        database.getConnection().use { connection ->
            val statement = connection.prepareStatement(
                """
                INSERT INTO villagemerchant_npcs (shop_id, npc_id) 
                VALUES (?, ?)
                ON DUPLICATE KEY UPDATE npc_id = VALUES(npc_id)
                """
            )
            statement.setString(1, shopId)
            statement.setInt(2, npcId)
            statement.executeUpdate()
        }
    }

    /**
     * NPC 상인 삭제 (비동기)
     */
    fun removeNPCMerchantAsync(shopId: String): CompletableFuture<Boolean> {
        return CompletableFuture.supplyAsync {
            try {
                removeNPCMerchant(shopId)
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }

    /**
     * NPC 상인 삭제 (동기)
     */
    private fun removeNPCMerchant(shopId: String): Boolean {
        return database.getConnection().use { connection ->
            val statement = connection.prepareStatement(
                "DELETE FROM villagemerchant_npcs WHERE shop_id = ?"
            )
            statement.setString(1, shopId)
            val affectedRows = statement.executeUpdate()
            affectedRows > 0
        }
    }

    /**
     * 모든 NPC 상인 조회 (비동기)
     */
    fun getAllNPCMerchantsAsync(): CompletableFuture<List<NPCMerchant>> {
        return CompletableFuture.supplyAsync {
            getAllNPCMerchants()
        }
    }

    /**
     * 모든 NPC 상인 조회 (동기 - 초기 로드용)
     */
    fun getAllNPCMerchants(): List<NPCMerchant> {
        return database.getConnection().use { connection ->
            val statement = connection.createStatement()
            val resultSet = statement.executeQuery(
                "SELECT shop_id, npc_id FROM villagemerchant_npcs"
            )
            
            val merchants = mutableListOf<NPCMerchant>()
            while (resultSet.next()) {
                merchants.add(
                    NPCMerchant(
                        shopId = resultSet.getString("shop_id"),
                        npcId = resultSet.getInt("npc_id")
                    )
                )
            }
            merchants
        }
    }

    /**
     * 초기화: 테이블 생성 및 마이그레이션 (동기)
     */
    fun initialize() {
        database.getConnection().use { connection ->
            // 통합 아이템 테이블 생성 (없으면)
            // NPC 상인 테이블 생성
            connection.createStatement().execute("""
                CREATE TABLE IF NOT EXISTS villagemerchant_npcs (
                    shop_id VARCHAR(50) PRIMARY KEY,
                    npc_id INT NOT NULL
                )
            """)

            // 거래 기록 테이블 생성
            connection.createStatement().execute("""
                CREATE TABLE IF NOT EXISTS villagemerchant_history (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    uuid VARCHAR(36) NOT NULL,
                    player_name VARCHAR(16) NOT NULL,
                    shop_type VARCHAR(50) NOT NULL,
                    transaction_type ENUM('BUY', 'SELL') NOT NULL,
                    item_code VARCHAR(100) NOT NULL,
                    amount INT NOT NULL,
                    unit_price DOUBLE NOT NULL,
                    total_price DOUBLE NOT NULL,
                    timestamp DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    INDEX idx_uuid (uuid),
                    INDEX idx_timestamp (timestamp),
                    INDEX idx_shop_item (shop_type, item_code)
                )
            """)

            // 아이템 테이블 생성
            connection.createStatement().execute("""
                CREATE TABLE IF NOT EXISTS villagemerchant_items (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    shop_type VARCHAR(50) NOT NULL,
                    item_id VARCHAR(255) NOT NULL,
                    buy_price DOUBLE NOT NULL DEFAULT 0,
                    sell_price DOUBLE NOT NULL DEFAULT 0,
                    can_buy BOOLEAN NOT NULL DEFAULT true,
                    can_sell BOOLEAN NOT NULL DEFAULT false,
                    item_type VARCHAR(20) NOT NULL DEFAULT 'VANILLA',
                    INDEX idx_shop_type (shop_type)
                )
            """)

            // 테이블 마이그레이션
            try {
                val metaData = connection.metaData
                
                // 1. 기존 price 컬럼 마이그레이션
                val priceColumn = metaData.getColumns(null, null, "villagemerchant_items", "price")
                if (priceColumn.next()) {
                    connection.createStatement().execute("""
                        ALTER TABLE villagemerchant_items 
                        ADD COLUMN IF NOT EXISTS buy_price DOUBLE NOT NULL DEFAULT 0,
                        ADD COLUMN IF NOT EXISTS sell_price DOUBLE NOT NULL DEFAULT 0,
                        ADD COLUMN IF NOT EXISTS can_buy BOOLEAN NOT NULL DEFAULT true,
                        ADD COLUMN IF NOT EXISTS can_sell BOOLEAN NOT NULL DEFAULT false
                    """)
                    
                    connection.createStatement().execute("""
                        UPDATE villagemerchant_items 
                        SET buy_price = price, can_buy = true, can_sell = false 
                        WHERE buy_price = 0
                    """)
                    
                    connection.createStatement().execute("""
                        ALTER TABLE villagemerchant_items DROP COLUMN price
                    """)
                }

                // 2. item_type 컬럼 추가 및 데이터 보정
                val itemTypeColumn = metaData.getColumns(null, null, "villagemerchant_items", "item_type")
                if (!itemTypeColumn.next()) {
                    connection.createStatement().execute("""
                        ALTER TABLE villagemerchant_items 
                        ADD COLUMN item_type VARCHAR(20) NOT NULL DEFAULT 'VANILLA'
                    """)
                    
                    // customcrops_ 로 시작하는 아이템은 NEXO 타입으로 업데이트
                    connection.createStatement().execute("""
                        UPDATE villagemerchant_items 
                        SET item_type = 'NEXO' 
                        WHERE item_id LIKE 'customcrops_%'
                    """)
                }
            } catch (e: Exception) {
                // 마이그레이션 실패는 무시
                e.printStackTrace()
            }
        }
    }

    /**
     * 상점 아이템 목록 조회 (동기)
     * shopType에 따라 다른 아이템을 불러옵니다.
     * 모든 상점 아이템은 'villagemerchant_items' 통합 테이블에서 관리됩니다.
     * 성능 최적화를 위해 캐싱을 사용합니다.
     */
    fun getMerchantItems(shopType: String): List<MerchantItem> {
        // 캐시에 있으면 캐시된 값 반환
        if (itemCache.containsKey(shopType)) {
            return itemCache[shopType]!!
        }

        return database.getConnection().use { connection ->
            val statement = connection.prepareStatement(
                "SELECT * FROM villagemerchant_items WHERE shop_type = ? ORDER BY id ASC"
            )
            statement.setString(1, shopType)
            val resultSet = statement.executeQuery()
            
            val items = mutableListOf<MerchantItem>()
            while (resultSet.next()) {
                // item_type 컬럼이 없을 경우(매우 드문 경우) 대비
                val itemType = try {
                    resultSet.getString("item_type") ?: "VANILLA"
                } catch (e: Exception) {
                    "VANILLA"
                }

                items.add(
                    MerchantItem(
                        id = resultSet.getInt("id"),
                        itemId = resultSet.getString("item_id"),
                        buyPrice = resultSet.getDouble("buy_price"),
                        sellPrice = resultSet.getDouble("sell_price"),
                        canBuy = resultSet.getBoolean("can_buy"),
                        canSell = resultSet.getBoolean("can_sell"),
                        itemType = itemType
                    )
                )
            }
            
            // 캐시에 저장
            itemCache[shopType] = items
            items
        }
    }

    /**
     * 씨앗 상인 아이템 목록 조회 (동기) - 호환성 유지용
     */
    fun getSeedMerchantItems(): List<SeedItem> {
        // 이제 내부적으로 통합 메서드를 호출합니다.
        return getMerchantItems("seed_merchant").map { 
            SeedItem(it.id, it.itemId, it.buyPrice) 
        }
    }
}

/**
 * 상점 아이템 데이터 클래스 (공용)
 */
data class MerchantItem(
    val id: Int,
    val itemId: String,
    val buyPrice: Double,
    val sellPrice: Double,
    val canBuy: Boolean,
    val canSell: Boolean,
    val itemType: String = "VANILLA" // VANILLA, NEXO
)

/**
 * 씨앗 상인 아이템 데이터 클래스
 */
data class SeedItem(
    val id: Int,
    val itemId: String,
    val price: Double
)

/**
 * NPC 상인 데이터 클래스
 */
data class NPCMerchant(
    val shopId: String,
    val npcId: Int
)

/**
 * 거래 기록 데이터 클래스
 */
data class HistoryRecord(
    val uuid: UUID,
    val playerName: String,
    val shopType: String,
    val transactionType: String,  // "BUY" or "SELL"
    val itemCode: String,         // "VANILLA:WHEAT_SEEDS" or "NEXO:tomato_seeds"
    val amount: Int,
    val unitPrice: Double,
    val totalPrice: Double,
    val timestamp: LocalDateTime = LocalDateTime.now()
)

/**
 * 거래 기록 배치 처리 클래스
 * 성능 최적화를 위해 거래 기록을 모아서 일괄 저장
 * - 5초마다 또는 50건 이상 쌓이면 DB에 저장
 * - 서버 종료 시 남은 기록 플러시
 */
class TransactionHistoryBatcher(
    private val plugin: JavaPlugin,
    private val database: Database
) {
    private val queue = ConcurrentLinkedQueue<HistoryRecord>()
    private val scheduler = Executors.newSingleThreadScheduledExecutor()
    private val isRunning = AtomicBoolean(true)
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    
    companion object {
        private const val BATCH_SIZE = 50
        private const val FLUSH_INTERVAL_SECONDS = 5L
    }

    init {
        // 5초마다 배치 저장 스케줄러 시작
        scheduler.scheduleAtFixedRate(
            { flushIfNeeded() },
            FLUSH_INTERVAL_SECONDS,
            FLUSH_INTERVAL_SECONDS,
            TimeUnit.SECONDS
        )
    }

    /**
     * 거래 기록 추가
     * 메인 스레드에서 호출해도 안전 (논블로킹)
     */
    fun addRecord(record: HistoryRecord) {
        queue.add(record)
        
        // 50건 이상이면 즉시 플러시 트리거
        if (queue.size >= BATCH_SIZE) {
            CompletableFuture.runAsync { flush() }
        }
    }

    /**
     * 조건부 플러시 (스케줄러에서 호출)
     */
    private fun flushIfNeeded() {
        if (queue.isNotEmpty() && isRunning.get()) {
            flush()
        }
    }

    /**
     * 큐의 모든 기록을 DB에 저장
     */
    fun flush() {
        if (queue.isEmpty()) return
        
        val records = mutableListOf<HistoryRecord>()
        
        // 큐에서 모든 기록 꺼내기
        while (queue.isNotEmpty()) {
            queue.poll()?.let { records.add(it) }
        }
        
        if (records.isEmpty()) return
        
        try {
            database.getConnection().use { connection ->
                val sql = """
                    INSERT INTO villagemerchant_history 
                    (uuid, player_name, shop_type, transaction_type, item_code, amount, unit_price, total_price, timestamp)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """
                
                connection.prepareStatement(sql).use { stmt ->
                    for (record in records) {
                        stmt.setString(1, record.uuid.toString())
                        stmt.setString(2, record.playerName)
                        stmt.setString(3, record.shopType)
                        stmt.setString(4, record.transactionType)
                        stmt.setString(5, record.itemCode)
                        stmt.setInt(6, record.amount)
                        stmt.setDouble(7, record.unitPrice)
                        stmt.setDouble(8, record.totalPrice)
                        stmt.setString(9, record.timestamp.format(dateFormatter))
                        stmt.addBatch()
                    }
                    stmt.executeBatch()
                }
            }
            
            if (records.size > 0) {
                plugin.logger.info("[VillageMerchant] 거래 기록 ${records.size}건 저장 완료")
            }
        } catch (e: Exception) {
            plugin.logger.severe("[VillageMerchant] 거래 기록 저장 실패: ${e.message}")
            e.printStackTrace()
            
            // 실패한 기록은 다시 큐에 넣기 (재시도)
            records.forEach { queue.add(it) }
        }
    }

    /**
     * 시스템 종료 시 호출
     * 남은 모든 기록을 저장하고 스케줄러 종료
     */
    fun shutdown() {
        isRunning.set(false)
        scheduler.shutdown()
        
        // 남은 기록 저장
        if (queue.isNotEmpty()) {
            plugin.logger.info("[VillageMerchant] 종료 전 거래 기록 ${queue.size}건 저장 중...")
            flush()
        }
        
        try {
            scheduler.awaitTermination(5, TimeUnit.SECONDS)
        } catch (e: InterruptedException) {
            scheduler.shutdownNow()
        }
    }

    /**
     * 현재 대기 중인 기록 수
     */
    fun pendingCount(): Int = queue.size
}
