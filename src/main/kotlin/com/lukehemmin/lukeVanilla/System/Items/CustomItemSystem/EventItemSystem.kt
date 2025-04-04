package com.lukehemmin.lukeVanilla.System.Items.CustomItemSystem

import com.lukehemmin.lukeVanilla.Main
import com.nexomc.nexo.api.NexoItems
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.inventory.ItemStack
import java.sql.Connection
import java.util.*

class EventItemSystem(private val plugin: Main) : Listener {
    
    // 이벤트 유형별 아이템 매핑
    private val eventItemMappings = mapOf(
        // 할로윈 아이템
        EventType.HALLOWEEN to mapOf(
            "sword" to "halloween_sword",
            "pickaxe" to "halloween_pickaxe",
            "axe" to "halloween_axe",
            "shovel" to "halloween_shovel",
            "hoe" to "halloween_hoe",
            "bow" to "halloween_bow",
            "fishing_rod" to "halloween_fishing_rod",
            "hammer" to "halloween_hammer",
            "hat" to "halloween_hat",
            "scythe" to "halloween_scythe",
            "spear" to "halloween_spear"
        ),
        
        // 크리스마스 아이템
        EventType.CHRISTMAS to mapOf(
            "sword" to "merry_christmas_sword",
            "pickaxe" to "merry_christmas_pickaxe",
            "axe" to "merry_christmas_axe",
            "shovel" to "merry_christmas_shovel",
            "hoe" to "merry_christmas_hoe",
            "bow" to "merry_christmas_bow",
            "crossbow" to "merry_christmas_crossbow",
            "fishing_rod" to "merry_christmas_fishing_rod",
            "hammer" to "merry_christmas_hammer",
            "shield" to "merry_christmas_shield",
            "head" to "merry_christmas_head",
            "helmet" to "merrychristmas_helmet",
            "chestplate" to "merrychristmas_chestplate",
            "leggings" to "merrychristmas_leggings",
            "boots" to "merrychristmas_boots"
        ),
        
        // 발렌타인 아이템
        EventType.VALENTINE to mapOf(
            "sword" to "valentine_sword",
            "pickaxe" to "valentine_pickaxe",
            "axe" to "valentine_axe",
            "shovel" to "valentine_shovel",
            "hoe" to "valentine_hoe",
            "fishing_rod" to "valentine_fishing_rod",
            "bow" to "valentine_bow",
            "crossbow" to "valentine_crossbow",
            "hammer" to "valentine_hammer",
            "helmet" to "valentine_helmet",
            "chestplate" to "valentine_chestplate",
            "leggings" to "valentine_leggings",
            "boots" to "valentine_boots",
            "head" to "valentine_head",
            "shield" to "valentine_shield"
        )
    )
    
    // 이벤트 유형별 스크롤 아이템 매핑
    private val eventScrollMappings = mapOf(
        // 할로윈 스크롤
        EventType.HALLOWEEN to mapOf(
            "sword" to "h_sword_scroll",
            "pickaxe" to "h_pickaxe_scroll", 
            "axe" to "h_axe_scroll",
            "shovel" to "h_shovel_scroll",
            "hoe" to "h_hoe_scroll",
            "bow" to "h_bow_scroll", 
            "fishing_rod" to "h_rod_scroll",
            "hammer" to "h_hammer_scroll",
            "hat" to "h_hat_scroll",
            "scythe" to "h_scythe_scroll",
            "spear" to "h_spear_scroll"
        ),
        
        // 크리스마스 스크롤
        EventType.CHRISTMAS to mapOf(
            "sword" to "c_sword_scroll",
            "pickaxe" to "c_pickaxe_scroll",
            "axe" to "c_axe_scroll",
            "shovel" to "c_shovel_scroll",
            "hoe" to "c_hoe_scroll",
            "bow" to "c_bow_scroll",
            "crossbow" to "c_crossbow_scroll",
            "fishing_rod" to "c_rod_scroll",
            "hammer" to "c_hammer_scroll",
            "shield" to "c_shield_scroll",
            "head" to "c_head_scroll",
            "helmet" to "c_helmet_scroll",
            "chestplate" to "c_chestplate_scroll",
            "leggings" to "c_leggings_scroll",
            "boots" to "c_boots_scroll"
        ),
        
        // 발렌타인 스크롤
        EventType.VALENTINE to mapOf(
            "sword" to "v_sword_scroll",
            "pickaxe" to "v_pickaxe_scroll",
            "axe" to "v_axe_scroll",
            "shovel" to "v_shovel_scroll",
            "hoe" to "v_hoe_scroll",
            "fishing_rod" to "v_rod_scroll",
            "bow" to "v_bow_scroll",
            "crossbow" to "v_crossbow_scroll",
            "hammer" to "v_hammer_scroll",
            "helmet" to "v_helmet_scroll",
            "chestplate" to "v_chestplate_scroll",
            "leggings" to "v_leggings_scroll",
            "boots" to "v_boots_scroll",
            "head" to "v_head_scroll",
            "shield" to "v_shield_scroll"
        )
    )
    
    // 아이템 표시 이름 매핑
    private val itemDisplayNames = mapOf(
        // 기본 아이템 이름
        "sword" to "검",
        "pickaxe" to "곡괭이",
        "axe" to "도끼",
        "shovel" to "삽",
        "hoe" to "괭이",
        "bow" to "활",
        "crossbow" to "석궁",
        "fishing_rod" to "낚싯대",
        "hammer" to "망치",
        "head" to "모자",
        "shield" to "방패",
        "helmet" to "투구",
        "chestplate" to "흉갑",
        "leggings" to "레깅스",
        "boots" to "부츠"
    )
    
    // 이벤트별 아이템 이름 접두사
    private val eventPrefixes = mapOf(
        EventType.HALLOWEEN to "호박",
        EventType.CHRISTMAS to "크리스마스",
        EventType.VALENTINE to "발렌타인"
    )

    // 데이터베이스 초기화 (플러그인 시작 시 호출)
    fun initializeDatabase() {
        plugin.database.getConnection().use { connection ->
            // 각 이벤트 타입별로 필요한 테이블 생성
            for (eventType in EventType.values()) {
                createEventTables(connection, eventType)
            }
        }
    }
    
    // 이벤트별 테이블 생성
    private fun createEventTables(connection: Connection, eventType: EventType) {
        val itemColumns = getItemColumns(eventType)
        if (itemColumns.isEmpty()) return
        
        // 아이템 소유 테이블 생성
        val ownerTableName = "`${eventType.dbTablePrefix}_Owner`"
        val ownerTableSQL = buildString {
            append("CREATE TABLE IF NOT EXISTS $ownerTableName (")
            append("`UUID` VARCHAR(36) PRIMARY KEY, ")
            itemColumns.forEach { append("`$it` TINYINT(1) NOT NULL DEFAULT 0, ") }
            append("`registered_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
            append(")")
        }
        
        // 아이템 수령 테이블 생성
        val receiveTableName = "`${eventType.dbTablePrefix}_Receive`"
        val receiveTableSQL = buildString {
            append("CREATE TABLE IF NOT EXISTS $receiveTableName (")
            append("`UUID` VARCHAR(36) PRIMARY KEY, ")
            itemColumns.forEach { append("`$it` TINYINT(1) NOT NULL DEFAULT 0, ") }
            append("`last_received_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
            append(")")
        }
        
        connection.createStatement().use { stmt ->
            stmt.execute(ownerTableSQL)
            stmt.execute(receiveTableSQL)
        }
    }
    
    // 이벤트 타입별 아이템 컬럼 목록 가져오기
    fun getItemColumns(eventType: EventType): List<String> {
        return eventItemMappings[eventType]?.keys?.toList() ?: emptyList()
    }
    
    // 플레이어 인벤토리에서 이벤트 아이템 등록
    fun registerItemsFromInventory(player: Player): Map<EventType, List<String>> {
        val registeredItems = mutableMapOf<EventType, MutableList<String>>()
        
        // 플레이어의 인벤토리 확인
        for (item in player.inventory.contents) {
            item ?: continue
            
            // 넥소 아이템 ID 확인
            val nexoId = NexoItems.idFromItem(item) ?: continue
            
            // 각 이벤트 유형별로 아이템 확인
            for (eventType in EventType.values()) {
                val itemMapping = eventItemMappings[eventType] ?: continue
                
                // 아이템 타입 확인
                for ((columnName, itemId) in itemMapping) {
                    if (nexoId == itemId) {
                        // 아이템 발견, 등록 처리
                        registerItemForPlayer(player.uniqueId, eventType, columnName)
                        
                        // 결과 추가
                        if (!registeredItems.containsKey(eventType)) {
                            registeredItems[eventType] = mutableListOf()
                        }
                        registeredItems[eventType]!!.add(columnName)
                        
                        // 인벤토리에서 아이템 제거 (1개만)
                        item.amount--
                        break
                    }
                }
            }
        }
        
        return registeredItems
    }
    
    // 플레이어에게 특정 이벤트 아이템 등록
    private fun registerItemForPlayer(uuid: UUID, eventType: EventType, itemColumn: String) {
        plugin.database.getConnection().use { connection ->
            val tableName = "`${eventType.dbTablePrefix}_Owner`"
            val columnName = "`$itemColumn`"
            val query = "INSERT INTO $tableName (`UUID`, $columnName) VALUES (?, 1) ON DUPLICATE KEY UPDATE $columnName = 1"
            
            connection.prepareStatement(query).use { stmt ->
                stmt.setString(1, uuid.toString())
                stmt.executeUpdate()
            }
        }
    }
    
    // 플레이어가 특정 이벤트 아이템을 소유하고 있는지 확인
    fun getOwnedItems(uuid: UUID, eventType: EventType): List<String> {
        val ownedItems = mutableListOf<String>()
        
        plugin.database.getConnection().use { connection ->
            val tableName = "`${eventType.dbTablePrefix}_Owner`"
            val query = "SELECT * FROM $tableName WHERE `UUID` = ?"
            
            connection.prepareStatement(query).use { stmt ->
                stmt.setString(1, uuid.toString())
                val resultSet = stmt.executeQuery()
                
                if (resultSet.next()) {
                    for (column in getItemColumns(eventType)) {
                        try {
                            val hasItem = resultSet.getBoolean(column)
                            if (hasItem) {
                                ownedItems.add(column)
                            }
                        } catch (e: Exception) {
                            plugin.logger.warning("getOwnedItems: `$column` 컬럼 처리 중 오류 발생")
                        }
                    }
                }
            }
        }
        
        return ownedItems
    }
    
    // 플레이어가 수령한 이벤트 아이템 목록 확인
    fun getReceivedItems(uuid: UUID, eventType: EventType): List<String> {
        val receivedItems = mutableListOf<String>()
        
        plugin.database.getConnection().use { connection ->
            val tableName = "`${eventType.dbTablePrefix}_Receive`"
            val query = "SELECT * FROM $tableName WHERE `UUID` = ?"
            
            connection.prepareStatement(query).use { stmt ->
                stmt.setString(1, uuid.toString())
                val resultSet = stmt.executeQuery()
                
                if (resultSet.next()) {
                    for (column in getItemColumns(eventType)) {
                        try {
                            val hasReceived = resultSet.getBoolean(column)
                            if (hasReceived) {
                                receivedItems.add(column)
                            }
                        } catch (e: Exception) {
                            plugin.logger.warning("getReceivedItems: `$column` 컬럼 처리 중 오류 발생")
                        }
                    }
                }
            }
        }
        
        return receivedItems
    }
    
    // 특정 아이템 수령 처리
    fun markItemAsReceived(uuid: UUID, eventType: EventType, itemColumn: String) {
        plugin.database.getConnection().use { connection ->
            val tableName = "`${eventType.dbTablePrefix}_Receive`"
            val columnName = "`$itemColumn`"
            val query = "INSERT INTO $tableName (`UUID`, $columnName) VALUES (?, 1) ON DUPLICATE KEY UPDATE $columnName = 1, `last_received_at` = CURRENT_TIMESTAMP"
            
            connection.prepareStatement(query).use { stmt ->
                stmt.setString(1, uuid.toString())
                stmt.executeUpdate()
            }
        }
    }
    
    // 이벤트 타입과 아이템 타입에 해당하는 스크롤 아이템 ID 가져오기
    fun getScrollId(eventType: EventType, itemColumn: String): String? {
        return eventScrollMappings[eventType]?.get(itemColumn)
    }
    
    // 아이템 타입에 대한 표시 이름 가져오기
    fun getItemDisplayName(eventType: EventType, itemColumn: String): String {
        val prefix = eventPrefixes[eventType] ?: eventType.displayName
        val itemName = itemDisplayNames[itemColumn] ?: itemColumn
        return "$prefix $itemName"
    }
    
    // 스크롤 ID에서 아이템 컬럼 이름 가져오기
    fun getColumnNameFromScrollId(eventType: EventType, scrollId: String): String? {
        val scrollMap = eventScrollMappings[eventType] ?: return null
        return scrollMap.entries.find { it.value == scrollId }?.key
    }
    
    // 이벤트 아이템 설치 방지
    @EventHandler
    fun onBlockPlace(event: BlockPlaceEvent) {
        val item = event.itemInHand
        val nexoId = NexoItems.idFromItem(item)

        // halloween_lentern 아이템 체크
        if (nexoId == "halloween_lentern") {
            event.isCancelled = true
            event.player.sendMessage("§c이 아이템은 설치할 수 없습니다. 설명을 확인해주세요!")
        }
    }
} 