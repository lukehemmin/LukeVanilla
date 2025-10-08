package com.lukehemmin.lukeVanilla.System.FishMerchant

import net.citizensnpcs.api.event.NPCRightClickEvent
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener

class FishMerchantListener(private val fishMerchantManager: FishMerchantManager) : Listener {

    @EventHandler
    fun onNPCRightClick(event: NPCRightClickEvent) {
        val npc = event.npc
        val player = event.clicker

        // 낚시 상인 NPC인지 확인
        if (fishMerchantManager.isFishMerchant(npc.id)) {
            // 낚시 상인 GUI 열기
            fishMerchantManager.openFishMerchantGUI(player)
        }
    }
}
