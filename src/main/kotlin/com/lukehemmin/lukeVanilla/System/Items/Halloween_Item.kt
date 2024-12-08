package com.lukehemmin.lukeVanilla.System.Items

import com.nexomc.nexo.api.NexoItems
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.inventory.ItemStack

class Halloween_Item : Listener {
    @EventHandler
    fun onBlockPlace(event: BlockPlaceEvent) {
        val item = event.itemInHand

        // Nexo 아이템인지 확인
        if (NexoItems.exists(item)) {
            // 아이템의 ID 가져오기
            val NexoId = NexoItems.idFromItem(item)

            // halloween_lentern 아이템인지 확인
            if (NexoId == "halloween_lentern") {
                event.isCancelled = true
                event.player.sendMessage("§c이 아이템은 설치할 수 없습니다. 설명을 확인해주세요!")
            }
        }
    }
}