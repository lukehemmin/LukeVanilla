// ShopPriceListener.kt
package com.lukehemmin.lukeVanilla.System.Shop

import org.bukkit.ChatColor
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.AsyncPlayerChatEvent

class ShopPriceListener(
    private val shopManager: ShopManager
) : Listener {

    private val priceSettings = mutableMapOf<Player, PriceSetting>()

    data class PriceSetting(
        val shop: Shop,
        val slot: Int,
        val isBuyPrice: Boolean
    )

    fun startPriceSetting(player: Player, shop: Shop, slot: Int, isBuyPrice: Boolean) {
        priceSettings[player] = PriceSetting(shop, slot, isBuyPrice)
    }

    @EventHandler
    fun onPlayerChat(event: AsyncPlayerChatEvent) {
        val player = event.player
        val priceSetting = priceSettings[player] ?: return

        if (event.message.equals("취소", ignoreCase = true)) {
            priceSettings.remove(player)
            player.sendMessage("${ChatColor.YELLOW}가격 설정이 취소되었습니다.")
            event.isCancelled = true
            return
        }

        val priceInput = event.message.toDoubleOrNull()
        if (priceInput == null || priceInput < 0) {
            player.sendMessage("${ChatColor.RED}올바른 가격을 입력해주세요. (0 이상의 숫자)")
            event.isCancelled = true
            return
        }

        if (priceInput > 1000000000) {
            player.sendMessage("${ChatColor.RED}가격은 10억원을 초과할 수 없습니다.")
            event.isCancelled = true
            return
        }
    }
}
