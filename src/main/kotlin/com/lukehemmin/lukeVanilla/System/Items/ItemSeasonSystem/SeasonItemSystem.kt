package com.lukehemmin.lukeVanilla.System.Items.ItemSeasonSystem

import com.nexomc.nexo.api.NexoItems
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockPlaceEvent

class SeasonItemSystem : Listener {
    
    // 시즌제 아이템 ID 리스트
    private val restrictedItems = listOf(
        "halloween_lentern"
        // 추가할 아이템 ID를 여기에 넣으세요
    )

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
