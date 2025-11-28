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
    private val exchangeMerchantGUI: ExchangeMerchantGUI,
    private val equipmentMerchantGUI: EquipmentMerchantGUI,
    private val soilReceiveGUI: SoilReceiveGUI,
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
            "seed_merchant" -> openSeedMerchantGUI(player)
            "exchange_merchant" -> openExchangeMerchantGUI(player)
            "equipment_merchant" -> openEquipmentMerchantGUI(player)
            "soil_receive_merchant" -> openSoilReceiveGUI(player)
            else -> {
                player.sendMessage(Component.text("이 상점은 아직 준비중입니다.", NamedTextColor.GRAY))
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
     * 씨앗 상인 GUI 열기
     */
    fun openSeedMerchantGUI(player: Player) {
        seedMerchantGUI.open(player)
    }

    /**
     * 교환 상인 GUI 열기
     */
    fun openExchangeMerchantGUI(player: Player) {
        exchangeMerchantGUI.openMainGui(player)
    }

    /**
     * 장비 상인 GUI 열기
     */
    fun openEquipmentMerchantGUI(player: Player) {
        equipmentMerchantGUI.open(player)
    }

    /**
     * 토양받기 상인 GUI 열기
     */
    fun openSoilReceiveGUI(player: Player) {
        soilReceiveGUI.open(player)
    }
}
