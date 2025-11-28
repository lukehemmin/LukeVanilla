package com.lukehemmin.lukeVanilla.System.VillageMerchant

import com.lukehemmin.lukeVanilla.Main
import com.lukehemmin.lukeVanilla.System.FarmVillage.*
import com.lukehemmin.lukeVanilla.System.NPC.NPCInteractionRouter
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.entity.Player
import java.util.concurrent.CompletableFuture

/**
 * 마을 상인 시스템 메인 관리자
 * 농사마을에서 독립되어 어디서든 사용 가능
 * 교환 제한 없이 돈으로만 거래하는 단순한 시스템
 * 비동기 DB 호출을 지원하여 서버 성능 최적화
 */
class VillageMerchantManager(
    private val plugin: Main,
    private val data: VillageMerchantData,
    private val seedMerchantGUI: SeedMerchantGUI,
    private val npcRouter: NPCInteractionRouter
) {
    
    init {
        // 서버 시작 시 기존 상인들을 라우터에 등록
        loadAndRegisterMerchants()
    }

    private fun loadAndRegisterMerchants() {
        getAllNPCMerchantsAsync().thenAccept { merchants ->
            merchants.forEach { merchant ->
                npcRouter.register(merchant.npcId) { player ->
                    openShopGUI(player, merchant.shopId)
                }
            }
            plugin.logger.info("[VillageMerchant] ${merchants.size}개의 상인 NPC를 라우터에 등록했습니다.")
        }
    }
    
    /**
     * 상점 ID에 따라 적절한 GUI를 엽니다.
     */
    fun openShopGUI(player: Player, shopId: String) {
        when (shopId) {
            "seed_merchant" -> seedMerchantGUI.open(player, "seed_merchant", "씨앗 상인")
            "crop_sell_merchant" -> seedMerchantGUI.open(player, "crop_sell_merchant", "농산물 판매 상인")
            "fertilizer_merchant" -> seedMerchantGUI.open(player, "fertilizer_merchant", "비료 상인")
            "soil_goods_merchant" -> seedMerchantGUI.open(player, "soil_goods_merchant", "토양 및 물품 상인")
            else -> {
                player.sendMessage(Component.text("알 수 없는 상점 타입입니다: $shopId", NamedTextColor.RED))
            }
        }
    }
    
    /**
     * NPC ID로 상점 ID 조회 (동기 - 이벤트 리스너용)
     */
    fun getShopIdByNPC(npcId: Int): String? {
        return data.getShopIdByNPC(npcId)
    }

    /**
     * NPC ID로 상점 ID 조회 (비동기)
     */
    fun getShopIdByNPCAsync(npcId: Int): CompletableFuture<String?> {
        return data.getShopIdByNPCAsync(npcId)
    }

    /**
     * 상점 ID로 NPC ID 조회 (비동기)
     */
    fun getNPCIdByShopIdAsync(shopId: String): CompletableFuture<Int?> {
        return data.getNPCIdByShopIdAsync(shopId)
    }

    /**
     * NPC 상인 등록 (비동기)
     */
    fun setNPCMerchantAsync(shopId: String, npcId: Int): CompletableFuture<Boolean> {
        return data.saveNPCMerchantAsync(shopId, npcId).thenApply { success ->
            if (success) {
                // 라우터에 등록 (성공 시 즉시 반영)
                npcRouter.register(npcId) { player ->
                    openShopGUI(player, shopId)
                }
            }
            success
        }
    }

    /**
     * NPC 상인 삭제 (비동기)
     */
    fun removeNPCMerchantAsync(shopId: String): CompletableFuture<Boolean> {
        // 먼저 해당 shopId를 가진 NPC ID를 조회해야 함
        return data.getNPCIdByShopIdAsync(shopId).thenCompose { npcId ->
            if (npcId != null) {
                data.removeNPCMerchantAsync(shopId).thenApply { success ->
                    if (success) {
                        // 라우터에서 해제
                        npcRouter.unregister(npcId)
                    }
                    success
                }
            } else {
                CompletableFuture.completedFuture(false)
            }
        }
    }

    /**
     * 모든 NPC 상인 조회 (동기 - 초기 로드용)
     */
    fun getAllNPCMerchants(): List<NPCMerchant> {
        return data.getAllNPCMerchants()
    }

    /**
     * 모든 NPC 상인 조회 (비동기)
     */
    fun getAllNPCMerchantsAsync(): CompletableFuture<List<NPCMerchant>> {
        return data.getAllNPCMerchantsAsync()
    }

    /**
     * 시스템 리로드
     * - 캐시 초기화
     * - 상인 목록 재등록
     */
    fun reload() {
        data.clearCache()
        loadAndRegisterMerchants()
        plugin.logger.info("[VillageMerchant] 시스템이 리로드되었습니다. (캐시 초기화 완료)")
    }

    /**
     * 씨앗 상인 GUI 열기 (레거시 지원)
     */
    fun openSeedMerchantGUI(player: Player) {
        seedMerchantGUI.open(player, "seed_merchant", "씨앗 상인")
    }

    /*
    // 사용하지 않는 GUI 메서드 제거
    fun openExchangeMerchantGUI(player: Player) { ... }
    fun openEquipmentMerchantGUI(player: Player) { ... }
    fun openSoilReceiveGUI(player: Player) { ... }
    */
}
