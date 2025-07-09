package com.lukehemmin.lukeVanilla.System.FarmVillage

import com.lukehemmin.lukeVanilla.Main
import com.nexomc.nexo.api.NexoItems
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.ItemStack

class SeedMerchantGUI(private val plugin: Main) : Listener {

    private val inventoryTitle = "씨앗 상인"

    fun open(player: Player) {
        val inventory = Bukkit.createInventory(player, 45, Component.text(inventoryTitle)) // 5 rows

        // 1. Fill with black glass panes
        val blackGlass = ItemStack(Material.BLACK_STAINED_GLASS_PANE).apply {
            itemMeta = itemMeta.apply { displayName(Component.text(" ")) }
        }
        for (i in 0 until inventory.size) {
            inventory.setItem(i, blackGlass)
        }

        // 2. Place seed items
        inventory.setItem(11, NexoItems.itemFromId("cabbage_seeds")?.build())
        inventory.setItem(13, NexoItems.itemFromId("chinese_cabbage_seeds")?.build())
        inventory.setItem(15, NexoItems.itemFromId("garlic_seeds")?.build())
        inventory.setItem(29, NexoItems.itemFromId("corn_seeds")?.build())
        inventory.setItem(31, NexoItems.itemFromId("pineapple_seeds")?.build())
        inventory.setItem(33, NexoItems.itemFromId("eggplant_seeds")?.build())

        player.openInventory(inventory)
    }

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        if (event.view.title() != Component.text(inventoryTitle)) return

        // Prevent players from taking items from the GUI
        event.isCancelled = true

        // TODO: Add logic for when a player clicks a seed item (e.g., purchase)
    }
} 