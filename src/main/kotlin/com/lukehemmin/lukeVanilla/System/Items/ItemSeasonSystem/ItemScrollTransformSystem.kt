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
        // Halloween scrolls
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
        "h_spear_scroll" to Pair(Material.NETHERITE_SWORD, "halloween_spear"),
        
        // Christmas scrolls
        "c_sword_scroll" to Pair(Material.NETHERITE_SWORD, "merry_christmas_sword"),
        "c_pickaxe_scroll" to Pair(Material.NETHERITE_PICKAXE, "merry_christmas_pickaxe"),
        "c_axe_scroll" to Pair(Material.NETHERITE_AXE, "merry_christmas_axe"),
        "c_shovel_scroll" to Pair(Material.NETHERITE_SHOVEL, "merry_christmas_shovel"),
        "c_hoe_scroll" to Pair(Material.NETHERITE_HOE, "merry_christmas_hoe"),
        "c_bow_scroll" to Pair(Material.BOW, "merry_christmas_bow"),
        "c_crossbow_scroll" to Pair(Material.CROSSBOW, "merry_christmas_crossbow"),
        "c_fishing_rod_scroll" to Pair(Material.FISHING_ROD, "merry_christmas_fishing_rod"),
        "c_hammer_scroll" to Pair(Material.MACE, "merry_christmas_hammer"),
        "c_shield_scroll" to Pair(Material.SHIELD, "merry_christmas_shield"),
        "c_head_scroll" to Pair(Material.LEATHER_HELMET, "merry_christmas_head"),
        "c_helmet_scroll" to Pair(Material.NETHERITE_HELMET, "merrychristmas_helmet"),
        "c_chestplate_scroll" to Pair(Material.NETHERITE_CHESTPLATE, "merrychristmas_chestplate"),
        "c_leggings_scroll" to Pair(Material.NETHERITE_LEGGINGS, "merrychristmas_leggings"),
        "c_boots_scroll" to Pair(Material.NETHERITE_BOOTS, "merrychristmas_boots"),
        
        // Valentine scrolls
        "v_sword_scroll" to Pair(Material.NETHERITE_SWORD, "valentine_sword"),
        "v_pickaxe_scroll" to Pair(Material.NETHERITE_PICKAXE, "valentine_pickaxe"),
        "v_axe_scroll" to Pair(Material.NETHERITE_AXE, "valentine_axe"),
        "v_shovel_scroll" to Pair(Material.NETHERITE_SHOVEL, "valentine_shovel"),
        "v_hoe_scroll" to Pair(Material.NETHERITE_HOE, "valentine_hoe"),
        "v_bow_scroll" to Pair(Material.BOW, "valentine_bow"),
        "v_crossbow_scroll" to Pair(Material.CROSSBOW, "valentine_crossbow"),
        "v_fishing_rod_scroll" to Pair(Material.FISHING_ROD, "valentine_fishing_rod"),
        "v_hammer_scroll" to Pair(Material.MACE, "valentine_hammer"),
        "v_helmet_scroll" to Pair(Material.NETHERITE_HELMET, "valentine_helmet"),
        "v_chestplate_scroll" to Pair(Material.NETHERITE_CHESTPLATE, "valentine_chestplate"),
        "v_leggings_scroll" to Pair(Material.NETHERITE_LEGGINGS, "valentine_leggings"),
        "v_boots_scroll" to Pair(Material.NETHERITE_BOOTS, "valentine_boots"),
        "v_head_scroll" to Pair(Material.LEATHER_HELMET, "valentine_head"),
        "v_shield_scroll" to Pair(Material.SHIELD, "valentine_shield")
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
            event.setCursor(null)
        } else {
            val newCursorItem = cursorItem.clone()
            newCursorItem.amount = newCursorAmount
            event.setCursor(newCursorItem)
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