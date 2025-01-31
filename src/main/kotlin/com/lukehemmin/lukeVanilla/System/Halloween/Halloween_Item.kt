package com.lukehemmin.lukeVanilla.System.Halloween

import com.nexomc.nexo.api.NexoItems
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockPlaceEvent

class Halloween_Item : Listener {
    @EventHandler
    fun onBlockPlace(event: BlockPlaceEvent) {
        val item = event.itemInHand

        // Nexo 아이템 ID 확인
        val nexoId = NexoItems.idFromItem(item)

        // halloween_lentern 아이템 체크
        if (nexoId == "halloween_lentern") {
            event.isCancelled = true
            event.player.sendMessage("§c이 아이템은 설치할 수 없습니다. 설명을 확인해주세요!")
        }
    }
//    @EventHandler
//    fun onBlockPlace(event: BlockPlaceEvent) {
//        val item = event.itemInHand
//
//        // Oraxen 아이템인지 확인
//        if (OraxenItems.exists(item)) {
//            // 아이템의 ID 가져오기
//            val oraxenId = OraxenItems.getIdByItem(item)
//
//            // halloween_lentern 아이템인지 확인
//            if (oraxenId == "halloween_lentern") {
//                event.isCancelled = true
//                event.player.sendMessage("§c이 아이템은 설치할 수 없습니다. 설명을 확인해주세요!")
//            }
//        }
//    }

}