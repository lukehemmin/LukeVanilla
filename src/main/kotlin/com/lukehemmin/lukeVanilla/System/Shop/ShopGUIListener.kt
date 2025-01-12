// ShopGUIListener.kt
package com.lukehemmin.lukeVanilla.System.Shop

import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.inventory.ItemStack

class ShopGUIListener(
    private val shopManager: ShopManager,
    private val priceListener: ShopPriceListener
) : Listener {

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val inventory = event.view.topInventory ?: return
        val clickedItem: ItemStack? = event.currentItem
        if (clickedItem == null || !clickedItem.hasItemMeta()) return

        val title = event.view.title
        if (title.contains("아이템 설정")) {
            // Allow players to place or remove items freely in the Item Setting GUI
            // Do not cancel the event to allow item placement
            // However, prevent duplication by handling it appropriately if needed
        } else if (title.contains("가격 설정")) {
            event.isCancelled = true
            handlePriceSettingClick(player, event.slot, clickedItem, event.click, title)
        } else if (title.contains("상점")) {
            event.isCancelled = true
            handlePlayerShopClick(player, event.slot, clickedItem, title)
        }
    }

    @EventHandler
    fun onInventoryClose(event: InventoryCloseEvent) {
        val player = event.player as? Player ?: return
        val inventory = event.inventory
        val title = event.view.title

        if (title.contains("아이템 설정")) {
            // 색상 코드를 제거하여 정확한 상점 이름을 추출
            val shopName = ChatColor.stripColor(title.replace(" 아이템 설정", "").trim()) ?: return
            val shop = shopManager.getShop(shopName) ?: return

            // 기존 아이템의 가격 정보를 임시 저장
            val priceMap = mutableMapOf<Int, Pair<Double?, Double?>>()
            shop.items.forEach { (slot, item) ->
                priceMap[slot] = Pair(item.buyPrice, item.sellPrice)
            }

            // 기존 아이템 제거
            shop.items.clear()

            // 새로운 아이템 저장 (기존 가격 정보 유지)
            for (slot in 0 until inventory.size) {
                val item = inventory.getItem(slot)
                if (item != null && item.type != Material.GRAY_STAINED_GLASS_PANE && item.type != Material.AIR) {
                    val (buyPrice, sellPrice) = priceMap[slot] ?: Pair(null, null)
                    val shopItem = ShopItem(
                        slot = slot,
                        item = item.clone(),
                        buyPrice = buyPrice,
                        sellPrice = sellPrice
                    )
                    shop.items[slot] = shopItem
                }
            }

            // 데이터베이스에 저장
            shopManager.saveShopItems(shop)

            // 저장 확인 메시지
            player.sendMessage("${ChatColor.GREEN}${shop.name} 상점의 아이템이 저장되었습니다.")
            player.sendMessage("${ChatColor.GRAY}저장된 아이템 수: ${shop.items.size}")
        }
    }

    private fun handlePriceSettingClick(
        player: Player,
        slot: Int,
        item: ItemStack,
        clickType: org.bukkit.event.inventory.ClickType,
        title: String
    ) {
        // 색상 코드를 제거하여 정확한 상점 이름을 추출
        val shopName = ChatColor.stripColor(inventoryTitleWithoutSuffix(title, " 가격 설정")) ?: return
        val shop = shopManager.getShop(shopName) ?: return
        val shopItem = shop.items[slot] ?: return

        if (clickType.isLeftClick) {
            // 구매 가격 설정
            priceListener.startPriceSetting(player, shop, slot, true)
            player.sendMessage("${ChatColor.GREEN}구매 가격을 설정하기 위해 채팅에 입력해주세요.")
        } else if (clickType.isRightClick) {
            // 판매 가격 설정
            priceListener.startPriceSetting(player, shop, slot, false)
            player.sendMessage("${ChatColor.GREEN}판매 가격을 설정하기 위해 채팅에 입력해주세요.")
        }
    }


    private fun handlePlayerShopClick(player: Player, slot: Int, item: ItemStack, title: String) {
        val shopName = inventoryTitleWithoutSuffix(title, " 상점")
        val shop = shopManager.getShop(shopName) ?: return
        val shopItem = shop.items[slot] ?: return

        if (shopItem.buyPrice != null) {
            // 구매 로직 구현
            if (shopManager.economyManager.removeBalance(player, shopItem.buyPrice!!)) {
                player.inventory.addItem(shopItem.item)
                player.sendMessage("${ChatColor.GREEN}${shopItem.item.type.name}을(를) 구매했습니다.")
            } else {
                player.sendMessage("${ChatColor.RED}소지금이 부족합니다.")
            }
        }

        if (shopItem.sellPrice != null) {
            // 판매 로직 구현
            player.sendMessage("${ChatColor.YELLOW}판매 기능은 아직 구현되지 않았습니다.")
            // TODO: 판매 로직 추가
        }
    }

    // 문자열 처리 함수를 수정하여 null safety 보장
    private fun inventoryTitleWithoutSuffix(title: String, suffix: String): String {
        return ChatColor.stripColor(title.replace(suffix, "").trim()) ?: title
    }
}
