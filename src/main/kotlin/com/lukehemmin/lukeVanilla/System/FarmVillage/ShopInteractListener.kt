package com.lukehemmin.lukeVanilla.System.FarmVillage

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.EquipmentSlot
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor

class ShopInteractListener(private val farmVillageManager: FarmVillageManager) : Listener {

    @EventHandler
    fun onPlayerInteract(event: PlayerInteractEvent) {
        // Check if the event is for the main hand to avoid double-firing
        if (event.hand != EquipmentSlot.HAND) {
            return
        }
        
        val player = event.player
        val clickedBlock = event.clickedBlock ?: return

        if (event.action.isRightClick) {
            val shopId = farmVillageManager.getShopIdAtLocation(clickedBlock.location)
            if (shopId != null) {
            event.isCancelled = true
            
                when (shopId) {
                    "seed_merchant" -> farmVillageManager.openSeedMerchantGUI(player)
                    "exchange_merchant" -> farmVillageManager.openExchangeMerchantGUI(player)
                    "equipment_merchant" -> farmVillageManager.openEquipmentMerchantGUI(player)
                    "soil_receive_merchant" -> farmVillageManager.openSoilReceiveGUI(player)
                    else -> {
                        // For other merchants, you can open a placeholder or send a message
                        player.sendMessage(Component.text("이 상점은 아직 준비중입니다.", NamedTextColor.GRAY))
                    }
                }
            }
        }
    }
} 