package com.lukehemmin.lukeVanilla.System.Items.ItemSeasonSystem

import com.nexomc.nexo.api.NexoItems
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin

class ItemScrollTransformSystem(private val plugin: JavaPlugin) : Listener {

    private val transformMappings = mapOf(
        "h_sword_scroll" to Pair(Material.NETHERITE_SWORD, "halloween_sword"),
        "h_pickaxe_scroll" to Pair(Material.NETHERITE_PICKAXE, "halloween_pickaxe"),
        "h_axe_scroll" to Pair(Material.NETHERITE_AXE, "halloween_axe"),
        "h_shovel_scroll" to Pair(Material.NETHERITE_SHOVEL, "halloween_shovel"),
        "h_hoe_scroll" to Pair(Material.NETHERITE_HOE, "halloween_hoe"),
        "h_bow_scroll" to Pair(Material.BOW, "halloween_bow"),
        "h_rod_scroll" to Pair(Material.FISHING_ROD, "halloween_fishing_rod"),
        "h_hammer_scroll" to Pair(Material.MACE, "halloween_hammer"),
        "h_hat_scroll" to Pair(Material.LEATHER_HELMET, "halloween_hat"),
        "h_scythe_scroll" to Pair(Material.NETHERITE_SWORD, "halloween_scythe"),
        "h_spear_scroll" to Pair(Material.NETHERITE_SWORD, "halloween_spear")
    )

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val cursorItem = event.cursor ?: return
        val clickedItem = event.currentItem ?: return
        
        if (cursorItem.type == Material.AIR || clickedItem.type == Material.AIR) return

        val cursorNexoId = NexoItems.idFromItem(cursorItem) ?: return
        
        val mapping = transformMappings[cursorNexoId] ?: return
        val (requiredMaterial, resultNexoId) = mapping
        
        if (clickedItem.type != requiredMaterial) return

        event.isCancelled = true
        
        val resultItemBuilder = NexoItems.itemFromId(resultNexoId) ?: return
        val resultItem = resultItemBuilder.build()
        
        copyEnchantments(clickedItem, resultItem)
        
        event.currentItem = resultItem
        
        val newCursorAmount = cursorItem.amount - 1
        if (newCursorAmount <= 0) {
            event.cursor = null
        } else {
            val newCursorItem = cursorItem.clone()
            newCursorItem.amount = newCursorAmount
            event.cursor = newCursorItem
        }
        
        player.updateInventory()
    }

    private fun copyEnchantments(source: ItemStack, target: ItemStack) {
        if (source.hasItemMeta() && source.itemMeta!!.hasEnchants()) {
            val targetMeta = target.itemMeta ?: return
            source.itemMeta!!.enchants.forEach { (enchantment, level) ->
                targetMeta.addEnchant(enchantment, level, true)
            }
            target.itemMeta = targetMeta
        }
    }
}