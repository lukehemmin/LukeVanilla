// Shop.kt
package com.lukehemmin.lukeVanilla.System.Shop

import org.bukkit.inventory.ItemStack

data class Shop(
    val name: String,
    val npcId: Int,
    var rows: Int = 3,
    val items: MutableMap<Int, ShopItem> = mutableMapOf()
)

data class ShopItem(
    val slot: Int,
    var item: ItemStack,
    var buyPrice: Double? = null,
    var sellPrice: Double? = null
)
