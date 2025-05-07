package com.lukehemmin.lukeVanilla.System.Items.ItemSeasonSystem

import com.nexomc.nexo.api.NexoItems
import org.bukkit.Material
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import java.io.File
import java.io.IOException
import java.util.*

class ItemRegisterSystem {
    
    private val configFolder = File("plugins/LukeVanilla/season_items")
    private val eventTypes = mapOf(
        "할로윈" to "halloween",
        "크리스마스" to "christmas",
        "발렌타인" to "valentine",
        "봄" to "spring"
    )
    
    init {
        if (!configFolder.exists()) {
            configFolder.mkdirs()
        }
        
        // 각 이벤트 타입별 파일 초기화
        for (eventType in eventTypes.values) {
            val file = File(configFolder, "$eventType.yml")
            if (!file.exists()) {
                try {
                    file.createNewFile()
                    val config = YamlConfiguration.loadConfiguration(file)
                    config.set("items", ArrayList<String>())
                    config.save(file)
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }
    }
    
    fun registerItem(player: Player, args: Array<out String>): Boolean {
        if (!player.hasPermission("lukevanilla.item.register")) {
            player.sendMessage("§c이 명령어를 사용할 권한이 없습니다.")
            return true
        }
        
        val item = player.inventory.itemInMainHand
        
        if (item.type == Material.AIR) {
            player.sendMessage("§c등록할 아이템을 손에 들고 있어야 합니다.")
            return true
        }
        
        // Nexo 아이템 ID 확인
        val nexoId = NexoItems.idFromItem(item)
        
        if (nexoId.isNullOrEmpty()) {
            player.sendMessage("§c등록 가능한 커스텀 아이템이 아닙니다.")
            return true
        }
        
        // 이벤트 타입 선택 GUI 열기
        openEventTypeSelectionGui(player, nexoId)
        
        return true
    }
    
    private fun openEventTypeSelectionGui(player: Player, itemId: String) {
        // 여기에 GUI 코드를 구현하거나 간단한 채팅 인터페이스로 대체할 수 있습니다.
        // 예시로 채팅 인터페이스 구현:
        player.sendMessage("§e===== 이벤트 타입 선택 =====")
        player.sendMessage("§7다음 중 아이템을 등록할 이벤트 타입을 선택하세요:")
        
        var index = 1
        for (eventType in eventTypes.keys) {
            player.sendMessage("§f$index. §a$eventType")
            index++
        }
        
        player.sendMessage("§7채팅창에 번호나 이벤트 이름을 입력하세요.")
        
        // 실제 구현에서는 채팅 이벤트를 리스닝하여 선택을 처리해야 합니다.
        // 이 예제에서는 선택 처리 코드는 생략합니다.
    }
    
    // 아이템을 특정 이벤트 타입에 등록하는 메서드
    fun registerItemToEventType(player: Player, itemId: String, eventType: String) {
        val englishEventType = eventTypes[eventType] ?: return
        
        val file = File(configFolder, "$englishEventType.yml")
        val config = YamlConfiguration.loadConfiguration(file)
        
        val items = config.getStringList("items")
        
        if (items.contains(itemId)) {
            player.sendMessage("§c이미 '$eventType' 이벤트에 등록된 아이템입니다.")
            return
        }
        
        items.add(itemId)
        config.set("items", items)
        
        try {
            config.save(file)
            player.sendMessage("§a아이템 '$itemId'를 '$eventType' 이벤트에 성공적으로 등록했습니다.")
        } catch (e: IOException) {
            player.sendMessage("§c아이템 등록 중 오류가 발생했습니다.")
            e.printStackTrace()
        }
    }
}
