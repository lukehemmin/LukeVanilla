// ShopListener.kt
package com.lukehemmin.lukeVanilla.System.Shop

import dev.geco.gsit.api.GSitAPI
import net.citizensnpcs.api.CitizensAPI
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerInteractEntityEvent

class ShopListener(private val shopManager: ShopManager) : Listener {

    @EventHandler(priority = EventPriority.HIGH)
    fun onPlayerInteractEntity(event: PlayerInteractEntityEvent) {
        val player = event.player
        val clickedEntity = event.rightClicked

        if (clickedEntity.hasMetadata("NPC")) {  // Citizens NPC 확인
            val npc = CitizensAPI.getNPCRegistry().getNPC(clickedEntity)
            if (npc != null) {
                val shop = shopManager.getShopByNPCId(npc.id)
                if (shop != null) {
                    // GSit 이벤트 취소
                    event.isCancelled = true
                    
                    // 현재 앉아있다면 일어나도록 처리
                    if (GSitAPI.isSitting(player)) {
                        GSitAPI.removeSeat(player, dev.geco.gsit.objects.GetUpReason.PLUGIN)
                    }

                    // 약간의 딜레이를 주어 더블 클릭과 GSit 충돌 방지
                    shopManager.plugin.server.scheduler.runTaskLater(shopManager.plugin, Runnable {
                        // 상점 GUI 열기
                        ShopGUI(
                            plugin = shopManager.plugin,
                            shop = shop,
                            player = player
                        ).openPlayerShopGUI()

                        // 상점 열기 효과음
                        player.playSound(
                            player.location,
                            Sound.BLOCK_CHEST_OPEN,
                            1.0f,
                            1.0f
                        )
                    }, 1L)
                }
            }
        }
    }
}
