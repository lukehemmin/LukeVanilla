// ShopListener.kt
package com.lukehemmin.lukeVanilla.System.Shop

import net.citizensnpcs.api.CitizensAPI
import org.bukkit.Sound
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerInteractEntityEvent

class ShopListener(private val shopManager: ShopManager) : Listener {

    @EventHandler
    fun onPlayerInteractEntity(event: PlayerInteractEntityEvent) {
        val player = event.player
        val clickedEntity = event.rightClicked

        if (clickedEntity.hasMetadata("NPC")) {
            val npcRegistry = CitizensAPI.getNPCRegistry()
            val npc = npcRegistry.getNPC(clickedEntity)
            
            if (npc != null) {
                val shop = shopManager.getShopByNPCId(npc.id)
                if (shop != null) {
                    // 약간의 딜레이를 주어 더블 클릭 방지
                    shopManager.plugin.server.scheduler.runTaskLater(shopManager.plugin, Runnable {
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
