package com.lukehemmin.lukeVanilla.System.Items.ItemSeasonSystem

import com.nexomc.nexo.api.NexoItems
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import java.io.File
import java.util.*

class ItemReceiveSystem {
    
    private val configFolder = File("plugins/LukeVanilla/season_items")
    private val eventTypes = mapOf(
        "할로윈" to "halloween",
        "크리스마스" to "christmas",
        "발렌타인" to "valentine",
        "봄" to "spring"
    )
    
    fun receiveItem(player: Player, eventTypeArg: String): Boolean {
        // 권한 체크
        if (!player.hasPermission("lukevanilla.item.receive")) {
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
        
        // GUI로 아이템 선택하게 하기
        openItemSelectionGui(player, eventTypeArg, items)
        
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
    
    private fun openItemSelectionGui(player: Player, eventTypeDisplay: String, items: List<String>) {
        // 여기에 GUI 코드를 구현하거나 간단한 채팅 인터페이스로 대체할 수 있습니다.
        
        // 예시로 모든 아이템을 한 번에 지급하는 간단한 구현:
        player.sendMessage("§a$eventTypeDisplay 이벤트의 아이템을 지급합니다.")
        
        var successCount = 0
        var failCount = 0
        
        for (itemId in items) {
            try {
                val item = NexoItems.getItem(itemId)
                if (item != null) {
                    // 인벤토리에 공간이 있는지 확인
                    if (player.inventory.firstEmpty() != -1) {
                        player.inventory.addItem(item)
                        successCount++
                    } else {
                        player.sendMessage("§c인벤토리가 가득 찼습니다.")
                        failCount = items.size - successCount
                        break
                    }
                } else {
                    failCount++
                }
            } catch (e: Exception) {
                failCount++
                e.printStackTrace()
            }
        }
        
        player.sendMessage("§a성공적으로 ${successCount}개의 아이템을 지급했습니다.")
        if (failCount > 0) {
            player.sendMessage("§c${failCount}개의 아이템을 지급하지 못했습니다.")
        }
    }
}
