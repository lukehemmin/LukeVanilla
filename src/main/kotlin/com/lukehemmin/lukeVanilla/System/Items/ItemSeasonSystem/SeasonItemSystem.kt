package com.lukehemmin.lukeVanilla.System.Items.ItemSeasonSystem

import com.lukehemmin.lukeVanilla.Main
import com.nexomc.nexo.api.NexoItems
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockPlaceEvent

/**
 * 시즌 아이템 시스템 - 할로윈, 크리스마스, 발렌타인 등 시즌별 아이템 관리 시스템
 */
class SeasonItemSystem(private val plugin: Main) : Listener {
    
    // 시즌제 아이템 ID 리스트 (설치 불가)
    private val restrictedItems = listOf(
        "halloween_lantern",
        "christmas_tree",
        "valentine_heart"
        // 추가할 아이템 ID를 여기에 넣으세요
    )
    
    // 이벤트 타입 목록
    val eventTypes = listOf("할로윈", "크리스마스", "발렌타인")
    
    // 각 시스템 관리
    private val itemRegisterSystem = ItemRegisterSystem()
    private val itemReceiveSystem = ItemReceiveSystem()
    private val itemViewSystem = ItemViewSystem()
    
    init {
        // 이벤트 리스너 등록
        Bukkit.getPluginManager().registerEvents(itemReceiveSystem, plugin)
        Bukkit.getPluginManager().registerEvents(this, plugin)
    }
    
    /**
     * 아이템 등록 처리
     */
    fun registerItem(player: Player, eventTypeArg: String?): Boolean {
        return itemRegisterSystem.registerItem(player, eventTypeArg)
    }
    
    /**
     * 아이템 수령 처리
     */
    fun receiveItem(player: Player, eventTypeArg: String): Boolean {
        return itemReceiveSystem.receiveItem(player, eventTypeArg)
    }
    
    /**
     * 아이템 조회 처리
     */
    fun viewItems(player: Player, eventTypeArg: String?): Boolean {
        return itemViewSystem.viewItems(player, eventTypeArg)
    }
    
    /**
     * 이벤트 타입 유효성 검증
     */
    fun isValidEventType(eventType: String): Boolean {
        return eventTypes.contains(eventType)
    }
    
    /**
     * 사용 가능한 이벤트 타입 목록 반환
     */
    fun getAvailableEventTypes(): List<String> {
        return eventTypes
    }
    
    /**
     * 시즌제 아이템 설치 제한
     */
    @EventHandler
    fun onBlockPlace(event: BlockPlaceEvent) {
        val item = event.itemInHand
        val nexoId = NexoItems.idFromItem(item)

        // 아이템이 제한 리스트에 있는지 확인
        if (nexoId in restrictedItems) {
            event.isCancelled = true
            event.player.sendMessage("§c이 아이템은 시즌제 아이템으로 현재 설치할 수 없습니다!")
        }
    }
}
