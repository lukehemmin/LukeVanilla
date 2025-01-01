package com.lukehemmin.lukeVanilla.System.Shop

import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.AsyncPlayerChatEvent
import java.util.concurrent.ConcurrentHashMap

class PriceEditManager(private val shopManager: ShopManager) : Listener {
    private val editingPlayers = ConcurrentHashMap<Player, PriceEditInfo>()

    data class PriceEditInfo(
        val shopName: String,
        val slot: Int,
        val priceType: String // "buy" 또는 "sell"
    )

    fun startPriceEdit(player: Player, shopName: String, slot: Int, priceType: String) {
        // PriceEditInfo를 맵에 저장
        val info = PriceEditInfo(shopName, slot, priceType)
        editingPlayers[player] = info
        player.sendMessage("§a${if (priceType == "buy") "구매" else "판매"} 가격을 설정하기 위해 채팅으로 가격을 입력해주세요.")
    }

    @EventHandler
    fun onPlayerChat(event: AsyncPlayerChatEvent) {
        val player = event.player
        val message = event.message

        val editInfo = editingPlayers[player] ?: return

        // 가격 입력 유효성 검사
        val price = message.toDoubleOrNull()
        if (price == null || price < 0) {
            player.sendMessage("§c유효한 가격을 입력해주세요.")
            return
        }

        // 가격 설정
        shopManager.setPrice(editInfo.shopName, editInfo.slot, editInfo.priceType, price)
        player.sendMessage("§a${if (editInfo.priceType == "buy") "구매" else "판매"} 가격이 §f${price} 원 §a으로 설정되었습니다.")
        editingPlayers.remove(player)

        // 다음 아이템 가격 설정을 위해 GUI를 다시 엽니다.
        shopManager.openItemEditGUI(player, editInfo.shopName)
        event.isCancelled = true
    }
}