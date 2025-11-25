package com.lukehemmin.lukeVanilla.System.VillageMerchant

import net.citizensnpcs.api.event.NPCRightClickEvent
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor

/**
 * 마을 상인 NPC 우클릭 이벤트 리스너
 * 농사마을 시스템에서 독립됨
 */
class VillageMerchantListener(private val manager: VillageMerchantManager) : Listener {

    @EventHandler(priority = org.bukkit.event.EventPriority.LOW)
    fun onNPCRightClick(event: NPCRightClickEvent) {
        if (event.isCancelled) return

        val npc = event.npc
        val player = event.clicker
        
        // NPC ID로 상인 타입 확인
        val shopId = manager.getShopIdByNPC(npc.id)
        
        if (shopId != null) {
            // 이벤트를 취소하여 다른 플러그인이나 리스너가 처리하지 못하도록 함
            event.isCancelled = true
            
            // 상인 GUI 열기
            when (shopId) {
                "seed_merchant" -> manager.openSeedMerchantGUI(player)
                "exchange_merchant" -> manager.openExchangeMerchantGUI(player)
                "equipment_merchant" -> manager.openEquipmentMerchantGUI(player)
                "soil_receive_merchant" -> manager.openSoilReceiveGUI(player)
                else -> {
                    // 다른 상인들에 대한 플레이스홀더
                    player.sendMessage(Component.text("이 상점은 아직 준비중입니다.", NamedTextColor.GRAY))
                }
            }
        }
    }
}
