package com.lukehemmin.lukeVanilla.System.FarmVillage

import net.citizensnpcs.api.event.NPCRightClickEvent
import net.citizensnpcs.api.npc.NPC
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor

class NPCMerchantListener(private val farmVillageManager: FarmVillageManager) : Listener {

    @EventHandler
    fun onNPCRightClick(event: NPCRightClickEvent) {
        val npc = event.npc
        val player = event.clicker
        
        // NPC ID로 상인 타입 확인
        val shopId = farmVillageManager.getShopIdByNPC(npc.id)
        
        if (shopId != null) {
            // 상인 GUI 열기
            when (shopId) {
                "seed_merchant" -> farmVillageManager.openSeedMerchantGUI(player)
                "exchange_merchant" -> farmVillageManager.openExchangeMerchantGUI(player)
                "equipment_merchant" -> farmVillageManager.openEquipmentMerchantGUI(player)
                "soil_receive_merchant" -> farmVillageManager.openSoilReceiveGUI(player)
                else -> {
                    // 다른 상인들에 대한 플레이스홀더
                    player.sendMessage(Component.text("이 상점은 아직 준비중입니다.", NamedTextColor.GRAY))
                }
            }
        }
    }
}
