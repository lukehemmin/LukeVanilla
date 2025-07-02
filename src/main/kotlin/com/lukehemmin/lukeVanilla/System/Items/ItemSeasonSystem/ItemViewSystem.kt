package com.lukehemmin.lukeVanilla.System.Items.ItemSeasonSystem

import com.lukehemmin.lukeVanilla.Main
import com.lukehemmin.lukeVanilla.System.Database.Database
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.sql.ResultSet
import java.util.*

class ItemViewSystem {
    private lateinit var plugin: Main
    private lateinit var database: Database
    
    // 이벤트 타입 목록
    private val eventTypes = listOf("할로윈", "크리스마스", "발렌타인", "봄")
    
    // 할로윈 아이템 목록
    private val halloweenItems = mapOf(
        "sword" to "호박의 빛 검",
        "pickaxe" to "호박 곡괭이",
        "axe" to "호박 도끼",
        "shovel" to "호박 삽",
        "hoe" to "호박 괭이",
        "bow" to "호박의 마법 활",
        "fishing_rod" to "호박의 낚시대",
        "hammer" to "호박의 철퇴",
        "hat" to "호박의 마법 모자",
        "scythe" to "호박의 낫",
        "spear" to "호박의 창"
    )
    
    // 크리스마스 아이템 목록
    private val christmasItems = mapOf(
        "sword" to "크리스마스 검",
        "pickaxe" to "크리스마스 곡괭이",
        "axe" to "크리스마스 도끼",
        "shovel" to "크리스마스 삽",
        "hoe" to "크리스마스 괭이",
        "bow" to "크리스마스 활",
        "crossbow" to "크리스마스 석궁",
        "fishing_rod" to "크리스마스 낚시대",
        "hammer" to "크리스마스 철퇴",
        "shield" to "크리스마스 방패",
        "head" to "크리스마스 모자",
        "helmet" to "크리스마스 투구",
        "chestplate" to "크리스마스 흉갑",
        "leggings" to "크리스마스 레깅스",
        "boots" to "크리스마스 부츠"
    )
    
    // 발렌타인 아이템 목록
    private val valentineItems = mapOf(
        "sword" to "발렌타인 러브 블레이드",
        "pickaxe" to "발렌타인 러브 마이너",
        "axe" to "발렌타인 하트 크래셔",
        "shovel" to "발렌타인 러브 디거",
        "hoe" to "발렌타인 가드너의 도구",
        "fishing_rod" to "발렌타인 큐피드 낚싯대",
        "bow" to "발렌타인 큐피드의 활",
        "crossbow" to "발렌타인 하트 석궁",
        "hammer" to "발렌타인 핑크 크래셔",
        "helmet" to "발렌타인 하트 가디언 헬멧",
        "chestplate" to "발렌타인 체스트 오브 러브",
        "leggings" to "발렌타인 로맨틱 레깅스",
        "boots" to "발렌타인 러브 트레커",
        "head" to "발렌타인 러버스 캡",
        "shield" to "발렌타인 방패"
    )
    
    // 봄 아이템 목록
    private val springItems = mapOf(
        "helmet" to "봄의 시작 헬멧",
        "chestplate" to "봄의 숨결 흉갑",
        "leggings" to "봄의 활력 레깅스",
        "boots" to "봄의 발걸음 부츠",
        "sword" to "봄의 수호자 검",
        "pickaxe" to "봄의 광부 곡괭이",
        "axe" to "봄의 가지치기 도끼",
        "hoe" to "봄의 경작 괭이",
        "shovel" to "봄의 정원사 삽",
        "bow" to "봄의 바람 활",
        "crossbow" to "봄의 연꽃 석궁",
        "shield" to "봄의 보호막 방패",
        "hammer" to "봄의 대장장이 망치"
    )
    
    init {
        val pluginManager = Bukkit.getPluginManager()
        val pluginInstance = pluginManager.getPlugin("LukeVanilla")
        if (pluginInstance is Main) {
            plugin = pluginInstance
            database = plugin.database
        }
    }
    
    fun viewItems(player: Player, eventTypeArg: String? = null): Boolean {
        // 권한 체크
        if (!player.hasPermission("lukevanilla.item.view")) {
            player.sendMessage("§c이 명령어를 사용할 권한이 없습니다.")
            return true
        }
        
        // 비동기로 처리
        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
            try {
                if (eventTypeArg == null) {
                    // 이벤트 타입이 지정되지 않은 경우 모든 이벤트 아이템 조회
                    viewAllEventItems(player)
                } else {
                    // 특정 이벤트 아이템 조회
                    viewSpecificEventItems(player, eventTypeArg)
                }
            } catch (e: Exception) {
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    player.sendMessage("§c아이템 조회 중 오류가 발생했습니다: ${e.message}")
                })
                plugin.logger.warning("아이템 조회 중 오류: ${e.message}")
                e.printStackTrace()
            }
        })
        
        return true
    }
    
    private fun viewSpecificEventItems(player: Player, eventTypeArg: String) {
        if (!eventTypes.contains(eventTypeArg)) {
            Bukkit.getScheduler().runTask(plugin, Runnable {
                player.sendMessage("§c유효하지 않은 이벤트 타입입니다. 사용 가능한 이벤트: ${eventTypes.joinToString(", ")}")
            })
            return
        }
        
        // DB에서 해당 이벤트 아이템 소유 정보 가져오기
        val playerItems = getPlayerEventItems(player, eventTypeArg)
        
        // 아이템 소유 정보 표시
        displayEventItems(player, eventTypeArg, playerItems)
    }
    
    private fun getPlayerEventItems(player: Player, eventType: String): Map<String, Boolean> {
        val playerItems = mutableMapOf<String, Boolean>()
        val tableName = getTableName(eventType)
        val itemList = getItemList(eventType)
        
        // DB에서 플레이어 아이템 정보 가져오기
        val uuid = player.uniqueId.toString()
        
        database.getConnection().use { connection ->
            val selectStmt = connection.prepareStatement("SELECT * FROM $tableName WHERE UUID = ?")
            selectStmt.setString(1, uuid)
            val resultSet = selectStmt.executeQuery()
            
            // 초기화: 모든 아이템은 기본적으로 미소유 상태
            itemList.forEach { playerItems[it] = false }
            
            if (resultSet.next()) {
                // 플레이어가 테이블에 존재하는 경우, 각 아이템 소유 상태 확인
                for (item in itemList) {
                    val value = resultSet.getInt(item)
                    playerItems[item] = (value == 1)
                }
            }
        }
        
        return playerItems
    }
    
    private fun viewAllEventItems(player: Player) {
        // 모든 이벤트 아이템 조회
        val eventToItems = mutableMapOf<String, Map<String, Boolean>>()
        
        // 각 이벤트 종류별로 아이템 소유 정보 가져오기
        for (eventType in eventTypes) {
            eventToItems[eventType] = getPlayerEventItems(player, eventType)
        }
        
        // 결과 표시
        Bukkit.getScheduler().runTask(plugin, Runnable {
            player.sendMessage("§e===== 시즌 아이템 소유 정보 =====")
            player.sendMessage("")
            
            // 각 이벤트별로 표시
            for (eventType in eventTypes) {
                val itemsData = eventToItems[eventType] ?: continue
                val ownedCount = itemsData.values.count { it }
                val totalCount = itemsData.size
                
                player.sendMessage("§b[$eventType] §f아이템 소유 정보: §a$ownedCount§f/§e$totalCount")
                
                // 각 이벤트 아이템 상세 정보 표시
                displayEventItems(player, eventType, itemsData, false)
                player.sendMessage("")
            }
            
            player.sendMessage("§e=================================")
        })
    }
    
    private fun getTableName(eventType: String): String {
        return when (eventType) {
            "할로윈" -> "Halloween_Item_Owner"
            "크리스마스" -> "Christmas_Item_Owner"
            "발렌타인" -> "Valentine_Item_Owner"
            "봄" -> "Spring_Item_Owner"
            else -> throw IllegalArgumentException("유효하지 않은 이벤트 타입: $eventType")
        }
    }
    
    private fun getItemList(eventType: String): List<String> {
        return when (eventType) {
            "할로윈" -> halloweenItems.keys.toList()
            "크리스마스" -> christmasItems.keys.toList()
            "발렌타인" -> valentineItems.keys.toList()
            "봄" -> springItems.keys.toList()
            else -> emptyList()
        }
    }
    
    private fun getItemDisplayName(eventType: String, itemKey: String): String {
        return when (eventType) {
            "할로윈" -> halloweenItems[itemKey] ?: itemKey
            "크리스마스" -> christmasItems[itemKey] ?: itemKey
            "발렌타인" -> valentineItems[itemKey] ?: itemKey
            "봄" -> springItems[itemKey] ?: itemKey
            else -> itemKey
        }
    }
    
    private fun displayEventItems(player: Player, eventType: String, playerItems: Map<String, Boolean>, showHeader: Boolean = true) {
        if (showHeader) {
            player.sendMessage("§e===== $eventType 이벤트 아이템 소유 정보 =====")
        }
        
        if (playerItems.isEmpty()) {
            player.sendMessage("§c이벤트 아이템이 없습니다.")
            return
        }
        
        // 소유한 아이템과 미소유 아이템 분류
        val ownedItems = playerItems.filter { it.value }.keys
        val unownedItems = playerItems.filter { !it.value }.keys
        
        // 소유한 아이템 표시
        if (ownedItems.isNotEmpty()) {
            player.sendMessage("§a● 소유한 아이템:")
            for (itemKey in ownedItems) {
                val displayName = getItemDisplayName(eventType, itemKey)
                player.sendMessage("  §a✔ §f$displayName")
            }
        }
        
        // 미소유 아이템 표시
        if (unownedItems.isNotEmpty()) {
            player.sendMessage("§c● 미소유 아이템:")
            for (itemKey in unownedItems) {
                val displayName = getItemDisplayName(eventType, itemKey)
                player.sendMessage("  §c✖ §f$displayName")
            }
        }
        
        if (showHeader) {
            player.sendMessage("§e=================================")
            player.sendMessage("§f총 §a${ownedItems.size}§f개 아이템 소유 / §e${playerItems.size}§f개 전체 아이템 중")
        }
    }
}
