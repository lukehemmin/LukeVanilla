package com.lukehemmin.lukeVanilla.System.Items.ItemSeasonSystem

import com.nexomc.nexo.api.NexoItems
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import java.io.File
import java.util.*

class ItemViewSystem {
    
    private val configFolder = File("plugins/LukeVanilla/season_items")
    private val eventTypes = mapOf(
        "할로윈" to "halloween",
        "크리스마스" to "christmas",
        "발렌타인" to "valentine",
        "봄" to "spring"
    )
    
    fun viewItems(player: Player, eventTypeArg: String): Boolean {
        // 권한 체크
        if (!player.hasPermission("lukevanilla.item.view")) {
            player.sendMessage("§c이 명령어를 사용할 권한이 없습니다.")
            return true
        }
        
        // 이벤트 타입 확인
        val eventType = getEventType(eventTypeArg)
        if (eventType == null) {
            player.sendMessage("§c유효하지 않은 이벤트 타입입니다. 사용 가능한 이벤트: ${eventTypes.keys.joinToString(", ")}")
            return true
        }
        
        // 이벤트 아이템 목록 불러오기
        val items = getEventItems(eventType)
        if (items.isEmpty()) {
            player.sendMessage("§c해당 이벤트에 등록된 아이템이 없습니다.")
            return true
        }
        
        // 아이템 목록 표시
        displayItems(player, eventTypeArg, items)
        
        return true
    }
    
    private fun getEventType(input: String): String? {
        // 한글 이벤트 타입 직접 입력한 경우
        if (eventTypes.containsKey(input)) {
            return eventTypes[input]
        }
        
        // 영문 이벤트 타입 입력한 경우
        if (eventTypes.containsValue(input.lowercase(Locale.getDefault()))) {
            return input.lowercase(Locale.getDefault())
        }
        
        return null
    }
    
    private fun getEventItems(eventType: String): List<String> {
        val file = File(configFolder, "$eventType.yml")
        if (!file.exists()) {
            return emptyList()
        }
        
        val config = YamlConfiguration.loadConfiguration(file)
        return config.getStringList("items")
    }
    
    private fun displayItems(player: Player, eventTypeDisplay: String, items: List<String>) {
        player.sendMessage("§e===== $eventTypeDisplay 이벤트 아이템 목록 =====")
        
        if (items.isEmpty()) {
            player.sendMessage("§c등록된 아이템이 없습니다.")
            return
        }
        
        // 아이템 정보 표시
        for ((index, itemId) in items.withIndex()) {
            try {
                val item = NexoItems.getItem(itemId)
                val displayName = if (item?.itemMeta?.hasDisplayName() == true) {
                    item.itemMeta?.displayName ?: itemId
                } else {
                    itemId
                }
                
                player.sendMessage("§f${index + 1}. §a$displayName §7(ID: $itemId)")
            } catch (e: Exception) {
                player.sendMessage("§f${index + 1}. §c$itemId §7(오류: 아이템을 찾을 수 없음)")
            }
        }
        
        player.sendMessage("§e=================================")
        player.sendMessage("§7총 ${items.size}개의 아이템이 등록되어 있습니다.")
    }
}
