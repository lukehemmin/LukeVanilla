package com.lukehemmin.lukeVanilla.System.Roulette

import com.lukehemmin.lukeVanilla.System.Database.Database
import com.lukehemmin.lukeVanilla.System.Economy.EconomyManager
import org.bukkit.plugin.java.JavaPlugin

/**
 * 룰렛 시스템 메인 클래스
 * - 모든 룰렛 컴포넌트 초기화 및 관리
 */
class RouletteSystem(
    private val plugin: JavaPlugin,
    private val database: Database,
    private val economyManager: EconomyManager
) {
    private lateinit var manager: RouletteManager
    private lateinit var npcListener: RouletteNPCListener
    private lateinit var command: RouletteCommand

    /**
     * 시스템 활성화
     */
    fun enable() {
        try {
            // Manager 초기화
            manager = RouletteManager(plugin, database)
            plugin.logger.info("[Roulette] RouletteManager 초기화 완료")

            // NPC Listener 초기화 및 등록
            npcListener = RouletteNPCListener(plugin, manager, economyManager)
            plugin.server.pluginManager.registerEvents(npcListener, plugin)
            plugin.logger.info("[Roulette] NPC Listener 등록 완료")

            // Command 초기화 및 등록
            command = RouletteCommand(plugin, manager)
            plugin.getCommand("룰렛설정")?.setExecutor(command)
            plugin.getCommand("룰렛설정")?.tabCompleter = command
            plugin.logger.info("[Roulette] 명령어 등록 완료")

            // 초기 설정 확인
            val config = manager.getConfig()
            if (config != null) {
                plugin.logger.info("[Roulette] 시스템이 성공적으로 초기화되었습니다.")
                plugin.logger.info("[Roulette] - 활성화: ${if (manager.isEnabled()) "예" else "아니오"}")
                plugin.logger.info("[Roulette] - NPC ID: ${manager.getNpcId() ?: "미설정"}")
                plugin.logger.info("[Roulette] - 비용: ${config.costAmount}원")
                plugin.logger.info("[Roulette] - 등록된 아이템: ${manager.getItems().size}개")
            } else {
                plugin.logger.warning("[Roulette] 설정을 불러오지 못했습니다. DB 연결을 확인해주세요.")
            }

        } catch (e: Exception) {
            plugin.logger.severe("[Roulette] 시스템 초기화 중 오류가 발생했습니다: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * 시스템 비활성화
     */
    fun disable() {
        try {
            // 활성 GUI 정리
            plugin.server.onlinePlayers.forEach { player ->
                npcListener.removeActiveGUI(player)
            }

            plugin.logger.info("[Roulette] 룰렛 시스템이 비활성화되었습니다.")
        } catch (e: Exception) {
            plugin.logger.warning("[Roulette] 시스템 비활성화 중 오류가 발생했습니다: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Manager 가져오기
     */
    fun getManager(): RouletteManager = manager

    /**
     * NPC Listener 가져오기
     */
    fun getNPCListener(): RouletteNPCListener = npcListener

    /**
     * Command 가져오기
     */
    fun getCommand(): RouletteCommand = command
}
