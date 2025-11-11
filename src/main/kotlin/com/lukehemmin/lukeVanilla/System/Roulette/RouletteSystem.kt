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
    private lateinit var nexoListener: RouletteNexoListener
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

            // Nexo Listener 초기화 및 등록
            nexoListener = RouletteNexoListener(plugin, manager, economyManager)
            plugin.server.pluginManager.registerEvents(nexoListener, plugin)
            plugin.logger.info("[Roulette] Nexo Listener 등록 완료")

            // Command 초기화 및 등록
            command = RouletteCommand(plugin, manager)
            val rouletteCommand = plugin.getCommand("룰렛")
            if (rouletteCommand != null) {
                rouletteCommand.setExecutor(command)
                rouletteCommand.tabCompleter = command
                plugin.logger.info("[Roulette] 명령어 등록 완료")
            } else {
                plugin.logger.severe("[Roulette] 명령어 '룰렛'을 찾을 수 없습니다! plugin.yml을 확인하세요.")
            }

            // 초기 설정 확인
            val roulettes = manager.getAllRoulettes()
            if (roulettes.isNotEmpty()) {
                plugin.logger.info("[Roulette] 시스템이 성공적으로 초기화되었습니다.")
                plugin.logger.info("[Roulette] - 등록된 룰렛: ${roulettes.size}개")
                roulettes.forEach { roulette ->
                    val itemCount = manager.getItems(roulette.id).size
                    val npcMappings = manager.getAllNPCMappings().count { it.value == roulette.id }
                    val nexoMappings = manager.getAllNexoMappings().count { it.value == roulette.id }
                    plugin.logger.info("[Roulette]   · ${roulette.rouletteName}: 비용 ${roulette.costAmount}원, 아이템 ${itemCount}개, NPC ${npcMappings}개, Nexo ${nexoMappings}개")
                }
            } else {
                plugin.logger.warning("[Roulette] 등록된 룰렛이 없습니다. /룰렛 생성 명령어로 룰렛을 만드세요.")
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
            // 활성 GUI 정리 (NPC) - 초기화 확인
            if (::npcListener.isInitialized) {
                npcListener.cleanup()
            }

            // 활성 GUI 정리 (Nexo) - 초기화 확인
            if (::nexoListener.isInitialized) {
                nexoListener.cleanup()
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
     * Nexo Listener 가져오기
     */
    fun getNexoListener(): RouletteNexoListener = nexoListener

    /**
     * Command 가져오기
     */
    fun getCommand(): RouletteCommand = command
}
