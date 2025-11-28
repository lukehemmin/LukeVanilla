package com.lukehemmin.lukeVanilla.System.VillageMerchant

import com.lukehemmin.lukeVanilla.Main
import com.lukehemmin.lukeVanilla.System.Database.Database
import com.lukehemmin.lukeVanilla.System.NPC.NPCInteractionRouter
import com.lukehemmin.lukeVanilla.System.FarmVillage.*

/**
 * 마을 상인 시스템
 * 농사마을에서 독립된 NPC 상인 시스템
 * 어디서든 사용 가능하며 지역 제한이 없음
 */
class VillageMerchantSystem(
    private val plugin: Main,
    private val database: Database,
    private val farmVillageManager: com.lukehemmin.lukeVanilla.System.FarmVillage.FarmVillageManager,
    private val npcRouter: NPCInteractionRouter
) {
    
    private lateinit var data: VillageMerchantData
    private lateinit var manager: VillageMerchantManager
    private lateinit var listener: VillageMerchantListener
    private lateinit var command: VillageMerchantCommand

    fun enable() {
        plugin.logger.info("[VillageMerchant] 마을 상인 시스템 초기화 중...")

        // 데이터 레이어 초기화 (plugin 전달)
        data = VillageMerchantData(plugin, database)

        // GUI 인스턴스 가져오기 (FarmVillage에서 공유)
        val seedMerchantGUI = farmVillageManager.seedMerchantGUI
        seedMerchantGUI.setVillageMerchantData(data)
        
        val exchangeMerchantGUI = farmVillageManager.exchangeMerchantGUI
        val equipmentMerchantGUI = farmVillageManager.equipmentMerchantGUI
        val soilReceiveGUI = farmVillageManager.soilReceiveGUI

        // Manager 초기화
        manager = VillageMerchantManager(
            plugin,
            data,
            seedMerchantGUI,
            exchangeMerchantGUI,
            equipmentMerchantGUI,
            soilReceiveGUI,
            npcRouter
        )

        // 리스너 등록
        // NPCInteractionRouter 사용으로 인해 기존 리스너 비활성화
        // listener = VillageMerchantListener(manager)
        // plugin.server.pluginManager.registerEvents(listener, plugin)

        // 명령어 등록
        command = VillageMerchantCommand(plugin, manager)
        plugin.getCommand("농사상점")?.setExecutor(command)
        plugin.getCommand("농사상점")?.tabCompleter = command

        plugin.logger.info("[VillageMerchant] 마을 상인 시스템 초기화 완료!")
    }

    fun disable() {
        plugin.logger.info("[VillageMerchant] 마을 상인 시스템 종료 중...")
        // 추가 정리 작업이 필요하면 여기에 작성
        plugin.logger.info("[VillageMerchant] 마을 상인 시스템 종료 완료!")
    }

    /**
     * Manager 인스턴스 반환
     */
    fun getManager(): VillageMerchantManager {
        return manager
    }
}
