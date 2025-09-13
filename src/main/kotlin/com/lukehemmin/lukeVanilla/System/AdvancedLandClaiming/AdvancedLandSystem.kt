package com.lukehemmin.lukeVanilla.System.AdvancedLandClaiming

import com.lukehemmin.lukeVanilla.Main
import com.lukehemmin.lukeVanilla.System.Database.Database
import com.lukehemmin.lukeVanilla.System.Debug.DebugManager
import com.lukehemmin.lukeVanilla.System.PlayTime.PlayTimeManager
import com.lukehemmin.lukeVanilla.System.MyLand.LandCommand

class AdvancedLandSystem(
    private val plugin: Main,
    private val database: Database,
    private val debugManager: DebugManager,
    private val playTimeManager: PlayTimeManager
) {

    private lateinit var advancedLandManager: AdvancedLandManager
    private lateinit var advancedLandProtectionListener: AdvancedLandProtectionListener

    /**
     * AdvancedLandClaiming 시스템을 초기화합니다.
     */
    fun enable() {
        try {
            // AdvancedLandManager 초기화
            advancedLandManager = AdvancedLandManager(plugin, database, debugManager, playTimeManager)
            debugManager.log("AdvancedLandClaiming", "AdvancedLandManager 초기화 완료")
            
            // 보호 리스너 초기화 및 등록
            advancedLandProtectionListener = AdvancedLandProtectionListener(advancedLandManager)
            plugin.server.pluginManager.registerEvents(advancedLandProtectionListener, plugin)
            debugManager.log("AdvancedLandClaiming", "보호 리스너 등록 완료")
            
            plugin.logger.info("[AdvancedLandClaiming] 고급 토지 클레이밍 시스템이 활성화되었습니다.")
            
        } catch (e: Exception) {
            plugin.logger.severe("[AdvancedLandClaiming] 시스템 초기화 중 오류 발생: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * 기존 LandCommand에 AdvancedLandManager를 주입합니다.
     * 이 메서드는 MyLand 시스템이 초기화된 후에 호출되어야 합니다.
     */
    fun integrateWithLandCommand(landCommand: LandCommand) {
        if (::advancedLandManager.isInitialized) {
            landCommand.setAdvancedLandManager(advancedLandManager)
            debugManager.log("AdvancedLandClaiming", "LandCommand와 통합 완료")
        } else {
            plugin.logger.warning("[AdvancedLandClaiming] AdvancedLandManager가 초기화되지 않았습니다. integrateWithLandCommand 호출을 연기하세요.")
        }
    }

    /**
     * AdvancedLandManager를 반환합니다.
     */
    fun getAdvancedLandManager(): AdvancedLandManager? {
        return if (::advancedLandManager.isInitialized) {
            advancedLandManager
        } else {
            null
        }
    }

    /**
     * 시스템을 비활성화합니다.
     */
    fun disable() {
        try {
            plugin.logger.info("[AdvancedLandClaiming] 고급 토지 클레이밍 시스템이 비활성화되었습니다.")
        } catch (e: Exception) {
            plugin.logger.severe("[AdvancedLandClaiming] 시스템 비활성화 중 오류 발생: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * 시스템이 초기화되었는지 확인합니다.
     */
    fun isInitialized(): Boolean {
        return ::advancedLandManager.isInitialized && ::advancedLandProtectionListener.isInitialized
    }

    /**
     * 시스템 상태 정보를 반환합니다.
     */
    fun getSystemStatus(): Map<String, Any> {
        return if (isInitialized()) {
            mapOf(
                "status" to "활성화됨",
                "totalClaims" to "데이터 로딩 필요", // 추후 구현
                "playTimeIntegration" to "연동됨",
                "protectionListener" to "등록됨"
            )
        } else {
            mapOf(
                "status" to "초기화되지 않음",
                "error" to "시스템이 아직 초기화되지 않았습니다."
            )
        }
    }
}