package com.lukehemmin.lukeVanilla.System.VillageMerchant

import com.lukehemmin.lukeVanilla.System.Database.Database
import org.bukkit.plugin.java.JavaPlugin
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

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
            // 통합 아이템 테이블 생성 (없으면)
            connection.createStatement().execute("""
                CREATE TABLE IF NOT EXISTS villagemerchant_items (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    shop_type VARCHAR(50) NOT NULL,
                    item_id VARCHAR(255) NOT NULL,
                    price DOUBLE NOT NULL,
                    INDEX idx_shop_type (shop_type)
                )
            """)

            val statement = connection.prepareStatement(
                "SELECT * FROM villagemerchant_items WHERE shop_type = ? ORDER BY id ASC"
            )
            statement.setString(1, shopType)
            val resultSet = statement.executeQuery()
            
            val items = mutableListOf<MerchantItem>()
            while (resultSet.next()) {
                items.add(
                    MerchantItem(
                        id = resultSet.getInt("id"),
                        itemId = resultSet.getString("item_id"),
                        price = resultSet.getDouble("price")
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
            SeedItem(it.id, it.itemId, it.price) 
        }
    }
}

/**
 * 상점 아이템 데이터 클래스 (공용)
 */
data class MerchantItem(
    val id: Int,
    val itemId: String,
    val price: Double
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
