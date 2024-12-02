package com.lukehemmin.lukeVanilla.System.Items

import io.th0rgal.oraxen.api.OraxenItems
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockPlaceEvent

class OraxenItem_Placecancel : Listener {
    // 설치 불가능한 아이템 ID 목록
    private val nonPlaceableItems = setOf(
        // 동물 모자
        "capybara_hat",
        "dog_hat",
        "duck_hat",
        "elephant_hat",
        "frog_hat",
        "lion_hat",
        "panda_hat",
        "pig_hat",
        "red_panda_hat",
        // 커스텀 모자
        "dolphin_hat",
        "dragonantler",
        "headset",
        "axolotl_hat",
        "snowmanhat",
        // 커스텀 아이템
        "scroll",
        // 할로윈 아이템
        "halloween_hat",
        "halloween_chest",
        "halloween_key",
        // 크리스마스 아이템
        "merry_christmas_head",
        "merry_christmas_wings",
        "merry_christmas_key",
        // 크리스마스 장식 아이템
        "gingerbread_man_item",
        "reindeer_item",
        "santa_item",
        "nutcracker_item",
        "snowman_item",
        "elf_item",
        "holly_leaf",
        "xmas_wreath",
        "xmas_tree",
        "xmas_bell",
        "candy_cane_red",
        "candy_cane_green",
        "xmas_sock_red",
        "xmas_sock_yellow",
        "xmas_sock_green",
        "star_white",
        "star_red",
        "star_yellow",
        "star_green",
        "moon_white",
        "moon_red",
        "moon_yellow",
        "moon_green",
        "ball_white",
        "ball_red",
        "ball_yellow",
        "ball_green",
        "gift_red_small",
        "gift_red_tall",
        "gift_red_large",
        "gift_green_small",
        "gift_green_tall",
        "gift_green_large"
    )

    @EventHandler
    fun onBlockPlace(event: BlockPlaceEvent) {
        val item = event.itemInHand

        // Oraxen 아이템인지 확인
        if (OraxenItems.exists(item)) {
            // 아이템의 ID 가져오기
            val oraxenId = OraxenItems.getIdByItem(item)

            // 설치 불가능 목록에 있는지 확인
            if (oraxenId in nonPlaceableItems) {
                event.isCancelled = true
            }
        }
    }
}