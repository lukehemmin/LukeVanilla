package com.lukehemmin.lukeVanilla.System.FarmVillage

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerInteractEvent

class ShopInteractListener(private val farmVillageManager: FarmVillageManager) : Listener {

    @EventHandler
    fun onPlayerInteract(event: PlayerInteractEvent) {
        val player = event.player
        val clickedBlock = event.clickedBlock ?: return

        if (event.action.isRightClick && farmVillageManager.isShopLocation(clickedBlock.location)) {
            event.isCancelled = true
            
            // Open the shop GUI for the player
            val shopInventory = Bukkit.createInventory(player, 54, "농사 상점") // 6 rows
            // TODO: Populate the GUI with items
            
            player.openInventory(shopInventory)
        }
    }
} 