package com.lukehemmin.lukeVanilla.System.Items.CustomItemSystem

import com.nexomc.nexo.api.NexoItems
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
        "enderdragonhat",
        "harrypotterglasses",
        "sproutcap",
        // 커스텀 아이템
        "creaking_bark",
        "goldtingedjade",
        "bluecrystal",
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
        "gift_green_large",
        // 발렌타인 아이템
        "valentine_head",
        // 스크롤
        "scroll",
        "h_scroll",
        "halloween_scroll",
        "new_halloween_scroll",
        "h_sword_scroll",
        "h_pickaxe_scroll",
        "h_axe_scroll",
        "h_shovel_scroll",
        "h_hoe_scroll",
        "h_bow_scroll",
        "h_rod_scroll",
        "h_hammer_scroll",
        "h_hat_scroll",
        "h_scythe_scroll",
        "h_spear_scroll",
        "christmas_scroll",
        "c_sword_scroll",
        "c_pickaxe_scroll",
        "c_axe_scroll",
        "c_shovel_scroll",
        "c_hoe_scroll",
        "c_bow_scroll",
        "c_crossbow_scroll",
        "c_fishing_rod_scroll",
        "c_hammer_scroll",
        "c_shield_scroll",
        "c_head_scroll",
        "c_helmet_scroll",
        "c_chestplate_scroll",
        "c_leggings_scroll",
        "c_boots_scroll",
        "v_sword_scroll",
        "v_pickaxe_scroll",
        "v_axe_scroll",
        "v_shovel_scroll",
        "v_hoe_scroll",
        "v_bow_scroll",
        "v_crossbow_scroll",
        "v_fishing_rod_scroll",
        "v_hammer_scroll",
        "v_helmet_scroll",
        "v_chestplate_scroll",
        "v_leggings_scroll",
        "v_boots_scroll",
        "v_head_scroll",
        "v_shield_scroll",
        "valentine_scroll"
    )

    @EventHandler
    fun onBlockPlace(event: BlockPlaceEvent) {
        val item = event.itemInHand

        // Nexo 아이템 ID 확인
        val nexoId = NexoItems.idFromItem(item) ?: return

        // 설치 불가능 목록에 있는지 확인
        if (nexoId in nonPlaceableItems) {
            event.isCancelled = true
        }
    }
}