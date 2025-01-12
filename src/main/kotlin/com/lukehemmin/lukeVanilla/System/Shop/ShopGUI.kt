// ShopGUI.kt
package com.lukehemmin.lukeVanilla.System.Shop

import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.plugin.Plugin

class ShopGUI(private val plugin: Plugin, private val shop: Shop, private val player: Player) {

    // 아이템 설정 GUI 열기
    fun openItemSettingGUI() {
        val inventory: Inventory = Bukkit.createInventory(null, 9 * shop.rows, "${ChatColor.GREEN}${shop.name} 아이템 설정")

        for ((slot, shopItem) in shop.items) {
            inventory.setItem(slot, shopItem.item)
        }

        // 기존의 유리판 배치 코드 제거
        // 플레이어가 아이템을 자유롭게 배치할 수 있도록 함

        player.openInventory(inventory)
    }

    // 가격 설정 GUI 열기
    fun openPriceSettingGUI() {
        val inventory: Inventory = Bukkit.createInventory(null, 9 * shop.rows, "${ChatColor.YELLOW}${shop.name} 가격 설정")

        for ((slot, shopItem) in shop.items) {
            val item = shopItem.item.clone()
            var meta = item.itemMeta
            val lore = mutableListOf<String>()
            if (shopItem.buyPrice != null) {
                lore.add("${ChatColor.GREEN}구매 가격: §e${shopItem.buyPrice}원")
            }
            if (shopItem.sellPrice != null) {
                lore.add("${ChatColor.RED}판매 가격: §e${shopItem.sellPrice}원")
            }
            if (lore.isNotEmpty()) {
                meta?.lore = lore
            }
            item.itemMeta = meta
            inventory.setItem(slot, item)
        }

        for (i in 0 until (9 * shop.rows)) {
            if (inventory.getItem(i) == null) {
                val placeholder = ItemStack(Material.GRAY_STAINED_GLASS_PANE)
                var meta = placeholder.itemMeta
                meta?.setDisplayName(" ")
                placeholder.itemMeta = meta
                inventory.setItem(i, placeholder)
            }
        }

        player.openInventory(inventory)
    }

    // 플레이어용 상점 GUI 열기
    fun openPlayerShopGUI() {
        val inventory: Inventory = Bukkit.createInventory(null, 9 * shop.rows, "${ChatColor.GOLD}${shop.name} 상점")

        for ((slot, shopItem) in shop.items) {
            val item = shopItem.item.clone()
            var meta = item.itemMeta
            val lore = mutableListOf<String>()
            if (shopItem.buyPrice != null) {
                lore.add("${ChatColor.GREEN}구매 가격: §e${shopItem.buyPrice}원")
            }
            if (shopItem.sellPrice != null) {
                lore.add("${ChatColor.RED}판매 가격: §e${shopItem.sellPrice}원")
            }
            if (lore.isNotEmpty()) {
                meta?.lore = lore
            }
            item.itemMeta = meta
            inventory.setItem(slot, item)
        }

        for (i in 0 until (9 * shop.rows)) {
            if (inventory.getItem(i) == null) {
                val placeholder = ItemStack(Material.GRAY_STAINED_GLASS_PANE)
                var meta = placeholder.itemMeta
                meta?.setDisplayName(" ")
                placeholder.itemMeta = meta
                inventory.setItem(i, placeholder)
            }
        }

        player.openInventory(inventory)
    }

    private fun createItemLore(shopItem: ShopItem): List<String> {
        val lore = mutableListOf<String>()
        shopItem.buyPrice?.let { price ->
            lore.add("${ChatColor.GREEN}구매 가격: §e${String.format("%,d", price.toLong())}원")
        }
        shopItem.sellPrice?.let { price ->
            lore.add("${ChatColor.RED}판매 가격: §e${String.format("%,d", price.toLong())}원")
        }
        if (lore.isEmpty()) {
            lore.add("${ChatColor.GRAY}거래 불가 아이템")
        }
        return lore
    }
}
