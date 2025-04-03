package com.lukehemmin.lukeVanilla.System.Items.Halloween

import com.nexomc.nexo.api.NexoItems
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.ItemStack

data class ScrollTransformation(
    val targetItemId: String,
    val targetMaterial: Material
)

class hscroll : Listener {

    // 스크롤 아이템과 대응하는 변환 정보 매핑
    private val scrollTransformations: Map<String, ScrollTransformation> = mapOf(
        "h_sword_scroll" to ScrollTransformation("halloween_sword", Material.NETHERITE_SWORD),
        "h_pickaxe_scroll" to ScrollTransformation("halloween_pickaxe", Material.NETHERITE_PICKAXE),
        "h_axe_scroll" to ScrollTransformation("halloween_axe", Material.NETHERITE_AXE),
        "h_shovel_scroll" to ScrollTransformation("halloween_shovel", Material.NETHERITE_SHOVEL),
        "h_hoe_scroll" to ScrollTransformation("halloween_hoe", Material.NETHERITE_HOE),
        "h_bow_scroll" to ScrollTransformation("halloween_bow", Material.BOW),
        "h_rod_scroll" to ScrollTransformation("halloween_fishing_rod", Material.FISHING_ROD),
        "h_hammer_scroll" to ScrollTransformation("halloween_hammer", Material.MACE),
        "h_hat_scroll" to ScrollTransformation("halloween_hat", Material.LEATHER_HELMET),
        "h_scythe_scroll" to ScrollTransformation("halloween_scythe", Material.NETHERITE_SWORD),
        "h_spear_scroll" to ScrollTransformation("halloween_spear", Material.NETHERITE_SWORD)
        // 필요에 따라 다른 스크롤 추가 가능
    )

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val cursor: ItemStack? = event.cursor
        val clicked: ItemStack? = event.currentItem

        // 플레이어 확인
        val player = event.whoClicked
        if (player !is Player) return // 플레이어가 아닌 경우 무시

        // 스크롤 아이템 확인
        val scrollId = cursor?.let { NexoItems.idFromItem(it) } ?: return
        val transformation = scrollTransformations[scrollId] ?: return

        // 클릭한 아이템이 변환할 대상 마테리얼인지 확인
        if (clicked != null && clicked.type == transformation.targetMaterial) {
            // 클릭한 아이템이 nexo 아이템인지 확인
            if (NexoItems.idFromItem(clicked) != null) {
                // 클릭한 아이템이 nexo 아이템인 경우 변환하지 않음
                player.sendMessage("§c이 아이템에는 스크롤을 사용할 수 없습니다.")
                return
            }

            event.isCancelled = true // 기본 동작 취소

            // 기존 아이템의 인챈트 저장
            val enchantments = clicked.enchantments

            // 새로운 Nexo 아이템 생성
            val newNexoItem = NexoItems.itemFromId(transformation.targetItemId)?.build() ?: run {
                player.sendMessage("변환할 아이템을 찾을 수 없습니다.")
                return
            }

            // 기존 인첸트 복사
            for ((enchantment, level) in enchantments) {
                newNexoItem.addUnsafeEnchantment(enchantment, level)
            }

            // 클릭된 슬롯에 새로운 아이템 설정
            event.currentItem = newNexoItem

            if (cursor.amount > 1) {
                val newScroll = cursor.clone()
                newScroll.amount = cursor.amount - 1
                event.view.setCursor(newScroll)
            } else {
                event.view.setCursor(null)
            }

            player.updateInventory()
        }

//        // 스크롤 아이템 확인
//        if (cursor != null && OraxenItems.exists(cursor)) {
//            val scrollId = OraxenItems.getIdByItem(cursor)
//            val transformation = scrollTransformations[scrollId] ?: return
//
//            // 클릭한 아이템이 변환할 대상 마테리얼인지 확인
//            if (clicked != null && clicked.type == transformation.targetMaterial) {
//
//                // 클릭한 아이템이 Oraxen 아이템인지 확인
//                if (OraxenItems.exists(clicked)) {
//                    // 클릭한 아이템이 Oraxen 아이템인 경우 변환하지 않음
//                    player.sendMessage("&c이 아이템에는 스크롤을 사용할 수 없습니다.")
//                    return
//                }
//
//                // 기본 동작 취소
//                event.isCancelled = true
//
//                // 기존 아이템의 인챈트 저장
//                val enchantments = clicked.enchantments
//
//                // 새로운 Oraxen 아이템 생성
//                val newOraxenItem = OraxenItems.getItemById(transformation.targetItemId)?.build()
//                if (newOraxenItem == null) {
//                    // 변환할 아이템을 찾을 수 없는 경우 종료
//                    player.sendMessage("변환할 아이템을 찾을 수 없습니다.")
//                    return
//                }
//
//                // 기존 인챈트 복사
//                for ((enchantment, level) in enchantments) {
//                    newOraxenItem.addUnsafeEnchantment(enchantment, level)
//                }
//
//                // 클릭된 슬롯에 새로운 아이템 설정
//                event.currentItem = newOraxenItem
//
//                // 스크롤 수량 관리
//                if (cursor.amount > 1) {
//                    val newScroll = cursor.clone()
//                    newScroll.amount = cursor.amount - 1
//                    event.view.setCursor(newScroll)
//                } else {
//                    // 스크롤 하나만 있을 경우 제거
//                    event.view.setCursor(null)
//                }
//
//                // 인벤토리 업데이트
//                player.updateInventory()
//            }
//        }
    }
}