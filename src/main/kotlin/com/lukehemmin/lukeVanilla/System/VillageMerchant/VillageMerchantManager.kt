package com.lukehemmin.lukeVanilla.System.VillageMerchant

import com.lukehemmin.lukeVanilla.Main
import com.lukehemmin.lukeVanilla.System.FarmVillage.*
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
    private val soilReceiveGUI: SoilReceiveGUI
) {
    
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
        return data.saveNPCMerchantAsync(shopId, npcId)
    }

    /**
     * NPC 상인 삭제 (비동기)
     */
    fun removeNPCMerchantAsync(shopId: String): CompletableFuture<Boolean> {
        return data.removeNPCMerchantAsync(shopId)
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
