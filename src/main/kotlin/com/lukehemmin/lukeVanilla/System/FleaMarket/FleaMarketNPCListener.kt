package com.lukehemmin.lukeVanilla.System.FleaMarket

import net.citizensnpcs.api.event.NPCRightClickEvent
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener

/**
 * 플리마켓 NPC 상호작용 리스너
 */
class FleaMarketNPCListener(
    private val manager: FleaMarketManager
) : Listener {

    @EventHandler(ignoreCancelled = true)
    fun onNPCRightClick(event: NPCRightClickEvent) {
        val npc = event.npc
        
        // 등록된 플리마켓 NPC인지 확인
        if (manager.isMarketNPC(npc.id)) {
            event.isCancelled = true
            manager.gui.openMarket(event.clicker)
        }
    }
}
