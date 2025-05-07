package com.lukehemmin.lukeVanilla.System.Items.ItemSeasonSystem

import com.lukehemmin.lukeVanilla.Main
import com.lukehemmin.lukeVanilla.System.Database.Database
import com.nexomc.nexo.api.NexoItems
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.persistence.PersistentDataType
import java.util.*

class ItemRegisterSystem {
    private lateinit var plugin: Main
    private lateinit var database: Database
    private lateinit var ownerKey: NamespacedKey
    
    // 이벤트 타입 목록
    private val eventTypes = listOf("할로윈", "크리스마스", "발렌타인")
    
    // 할로윈 아이템 목록
    private val halloweenItems = mapOf(
        "halloween_sword" to "호박의 빛 검",
        "halloween_pickaxe" to "호박 곡괭이",
        "halloween_axe" to "호박 도끼",
        "halloween_shovel" to "호박 삽",
        "halloween_hoe" to "호박 괭이",
        "halloween_bow" to "호박의 마법 활",
        "halloween_fishing_rod" to "호박의 낚시대",
        "halloween_hammer" to "호박의 철퇴",
        "halloween_hat" to "호박의 마법 모자",
        "halloween_scythe" to "호박의 낫",
        "halloween_spear" to "호박의 창"
    )
    
    // 크리스마스 아이템 목록
    private val christmasItems = mapOf(
        "merry_christmas_sword" to "크리스마스 검",
        "merry_christmas_pickaxe" to "크리스마스 곡괭이",
        "merry_christmas_axe" to "크리스마스 도끼",
        "merry_christmas_shovel" to "크리스마스 삽",
        "merry_christmas_hoe" to "크리스마스 괭이",
        "merry_christmas_bow" to "크리스마스 활",
        "merry_christmas_crossbow" to "크리스마스 석궁",
        "merry_christmas_fishing_rod" to "크리스마스 낚시대",
        "merry_christmas_hammer" to "크리스마스 철퇴",
        "merry_christmas_shield" to "크리스마스 방패",
        "merry_christmas_head" to "크리스마스 모자",
        "merry_christmas_helmet" to "크리스마스 투구",
        "merry_christmas_chestplate" to "크리스마스 흉갑",
        "merry_christmas_leggings" to "크리스마스 레깅스",
        "merry_christmas_boots" to "크리스마스 부츠"
    )
    
    // 발렌타인 아이템 목록
    private val valentineItems = mapOf(
        "valentine_sword" to "발렌타인 러브 블레이드",
        "valentine_pickaxe" to "발렌타인 러브 마이너",
        "valentine_axe" to "발렌타인 하트 크래셔",
        "valentine_shovel" to "발렌타인 러브 디거",
        "valentine_hoe" to "발렌타인 가드너의 도구",
        "valentine_fishing_rod" to "발렌타인 큐피드 낚싯대",
        "valentine_bow" to "발렌타인 큐피드의 활",
        "valentine_crossbow" to "발렌타인 하트 석궁",
        "valentine_hammer" to "발렌타인 핑크 크래셔",
        "valentine_helmet" to "발렌타인 하트 가디언 헬멧",
        "valentine_chestplate" to "발렌타인 체스트 오브 러브",
        "valentine_leggings" to "발렌타인 로맨틱 레깅스",
        "valentine_boots" to "발렌타인 러브 트레커",
        "valentine_head" to "발렌타인 러버스 캡",
        "valentine_shield" to "발렌타인 방패"
    )
    
    init {
        val pluginManager = Bukkit.getPluginManager()
        val pluginInstance = pluginManager.getPlugin("LukeVanilla")
        if (pluginInstance is Main) {
            plugin = pluginInstance
            database = plugin.database
            ownerKey = NamespacedKey(plugin, "owner")
        }
    }
    
    // 아이템 코드에서 DB 컬럼 이름 추출
    private fun getColumnName(itemId: String): String {
        return when {
            itemId.startsWith("halloween_") -> itemId.removePrefix("halloween_")
            itemId.startsWith("merry_christmas_") -> itemId.removePrefix("merry_christmas_")
            itemId.startsWith("valentine_") -> itemId.removePrefix("valentine_")
            else -> ""
        }
    }
    
    // 아이템 코드에서 이벤트 타입 추출
    private fun getEventType(itemId: String): String {
        return when {
            itemId.startsWith("halloween_") -> "할로윈"
            itemId.startsWith("merry_christmas_") -> "크리스마스"
            itemId.startsWith("valentine_") -> "발렌타인"
            else -> ""
        }
    }
    
    // 아이템 표시 이름 가져오기
    private fun getItemDisplayName(itemId: String): String {
        return when {
            halloweenItems.containsKey(itemId) -> halloweenItems[itemId] ?: "알 수 없는 할로윈 아이템"
            christmasItems.containsKey(itemId) -> christmasItems[itemId] ?: "알 수 없는 크리스마스 아이템"
            valentineItems.containsKey(itemId) -> valentineItems[itemId] ?: "알 수 없는 발렌타인 아이템"
            else -> "알 수 없는 아이템"
        }
    }
    
    // 이벤트 타입 테이블 이름 가져오기
    private fun getTableName(eventType: String): String {
        return when (eventType) {
            "할로윈" -> "Halloween_Item_Owner"
            "크리스마스" -> "Christmas_Item_Owner"
            "발렌타인" -> "Valentine_Item_Owner"
            else -> ""
        }
    }
    
    // 아이템을 데이터베이스에 등록
    private fun registerItemToDatabase(player: Player, itemId: String): Boolean {
        return try {
            val eventType = getEventType(itemId)
            val tableName = getTableName(eventType)
            val columnName = getColumnName(itemId)
            
            database.getConnection().use { connection ->
                val uuid = player.uniqueId.toString()
                
                val selectStmt = connection.prepareStatement("SELECT * FROM $tableName WHERE UUID = ?")
                selectStmt.setString(1, uuid)
                val resultSet = selectStmt.executeQuery()
                
                if (resultSet.next()) {
                    val updateStmt = connection.prepareStatement("UPDATE $tableName SET $columnName = 1 WHERE UUID = ?")
                    updateStmt.setString(1, uuid)
                    updateStmt.executeUpdate()
                } else {
                    val insertStmt = connection.prepareStatement("INSERT INTO $tableName (UUID, $columnName) VALUES (?, 1)")
                    insertStmt.setString(1, uuid)
                    insertStmt.executeUpdate()
                }
            }
            true
        } catch (e: Exception) {
            plugin.logger.warning("데이터베이스 작업 중 오류 발생: ${e.message}")
            false
        }
    }
    
    // 아이템 등록 메인 메서드
    fun registerItem(player: Player, args: Array<out String>): Boolean {
        if (!player.hasPermission("lukevanilla.item.register")) {
            player.sendMessage("§c이 명령어를 사용할 권한이 없습니다.")
            return true
        }
        
        // 비동기로 처리
        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
            try {
                val registeredItems = mutableListOf<String>() // 새로 등록된 아이템
                val alreadyOwnItems = mutableListOf<String>() // 이미 자신이 등록한 아이템
                val alreadyRegisteredItems = mutableListOf<Pair<String, String>>() // 다른 사람이 등록한 아이템과 소유자
                
                // 인벤토리의 모든 아이템 확인
                val itemsToCheck = ArrayList<Pair<ItemStack, ItemMeta>>()
                
                // 메인 인벤토리 확인
                for (item in player.inventory.contents) {
                    if (item != null && item.type != Material.AIR) {
                        val meta = item.itemMeta
                        if (meta != null) {
                            itemsToCheck.add(Pair(item, meta))
                        }
                    }
                }
                
                // 갑옷 슬롯 확인
                for (item in player.inventory.armorContents) {
                    if (item != null && item.type != Material.AIR) {
                        val meta = item.itemMeta
                        if (meta != null) {
                            itemsToCheck.add(Pair(item, meta))
                        }
                    }
                }
                
                // 왼손 확인
                val offhandItem = player.inventory.itemInOffHand
                if (offhandItem.type != Material.AIR) {
                    val meta = offhandItem.itemMeta
                    if (meta != null) {
                        itemsToCheck.add(Pair(offhandItem, meta))
                    }
                }
                
                // 모든 아이템 처리
                for ((item, meta) in itemsToCheck) {
                    val nexoId = NexoItems.idFromItem(item)
                    
                    // Nexo 아이템이고 시즌 아이템인 경우만 처리
                    if (!nexoId.isNullOrEmpty() && (
                            halloweenItems.containsKey(nexoId) || 
                            christmasItems.containsKey(nexoId) || 
                            valentineItems.containsKey(nexoId))) {
                        
                        // NBT 데이터에서 소유자 확인
                        if (meta.persistentDataContainer.has(ownerKey, PersistentDataType.STRING)) {
                            val ownerUuid = meta.persistentDataContainer.get(ownerKey, PersistentDataType.STRING)
                            
                            // 자신이 등록한 아이템인지 확인
                            if (ownerUuid == player.uniqueId.toString()) {
                                alreadyOwnItems.add(nexoId)
                            } else {
                                // 다른 사람이 등록한 아이템인 경우 소유자 이름 조회
                                database.getConnection().use { connection ->
                                    val selectStmt = connection.prepareStatement("SELECT NickName FROM Player_Data WHERE UUID = ?")
                                    selectStmt.setString(1, ownerUuid)
                                    val resultSet = selectStmt.executeQuery()
                                    val ownerName = if (resultSet.next()) resultSet.getString("NickName") else "알 수 없는 플레이어"
                                    alreadyRegisteredItems.add(Pair(nexoId, ownerName))
                                }
                            }
                        } else {
                            // 소유자가 없는 새로운 아이템 등록
                            if (registerItemToDatabase(player, nexoId)) {
                                Bukkit.getScheduler().runTask(plugin, Runnable {
                                    meta.persistentDataContainer.set(
                                        ownerKey,
                                        PersistentDataType.STRING,
                                        player.uniqueId.toString()
                                    )
                                    item.itemMeta = meta
                                })
                                registeredItems.add(nexoId)
                            } else {
                                plugin.logger.warning("아이템 등록 중 오류 발생: $nexoId")
                            }
                        }
                    }
                }
                
                // 모든 처리가 완료된 후 결과 메시지 출력
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    // 새로 등록된 아이템 메시지 출력
                    if (registeredItems.isNotEmpty()) {
                        val displayNames = registeredItems.map { getItemDisplayName(it) }
                        player.sendMessage("§a다음 아이템들이 소유자로 등록되었습니다: §f${displayNames.joinToString(", ")}")
                    }
                    
                    // 이미 등록된 내 아이템 메시지 출력
                    if (alreadyOwnItems.isNotEmpty()) {
                        alreadyOwnItems.forEach { itemId ->
                            player.sendMessage("§e아이템 §f${getItemDisplayName(itemId)} §e은(는) 이미 등록되어있습니다.")
                        }
                    }
                    
                    // 다른 사람의 아이템 메시지 출력
                    if (alreadyRegisteredItems.isNotEmpty()) {
                        alreadyRegisteredItems.forEach { (itemId, ownerName) ->
                            player.sendMessage("§c아이템 §f${getItemDisplayName(itemId)} §c은(는) §f$ownerName §c님이 이미 등록하였습니다.")
                        }
                    }
                    
                    // 등록된 아이템이 없는 경우 메시지 출력
                    if (registeredItems.isEmpty() && alreadyOwnItems.isEmpty() && alreadyRegisteredItems.isEmpty()) {
                        player.sendMessage("§c등록 가능한 시즌 아이템이 없습니다.")
                    }
                })
            } catch (e: Exception) {
                plugin.logger.warning("처리 중 오류 발생: ${e.message}")
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    player.sendMessage("§c처리 중 오류가 발생했습니다.")
                })
            }
        })
        
        return true
    }
}
