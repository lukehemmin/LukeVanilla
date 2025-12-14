package com.lukehemmin.lukeVanilla.System.ScrollRoulette

import com.lukehemmin.lukeVanilla.System.Database.Database
import org.bukkit.plugin.java.JavaPlugin

/**
 * 스크롤 룰렛 시스템
 * - 시스템 초기화 및 정리
 * - Manager, Listener, Command 등록
 */
class ScrollRouletteSystem(
    private val plugin: JavaPlugin,
    private val database: Database
) {
    lateinit var manager: ScrollRouletteManager
        private set
    
    private lateinit var listener: ScrollRouletteListener
    private lateinit var command: ScrollRouletteCommand

    /**
     * 시스템 초기화
     */
    fun initialize() {
        plugin.logger.info("[ScrollRoulette] 스크롤 룰렛 시스템 초기화 중...")

        // Manager 초기화
        manager = ScrollRouletteManager(plugin, database)

        // Listener 등록
        listener = ScrollRouletteListener(plugin, manager)
        plugin.server.pluginManager.registerEvents(listener, plugin)

        // Command 등록
        command = ScrollRouletteCommand(plugin, manager)
        plugin.getCommand("스크롤룰렛")?.setExecutor(command)
        plugin.getCommand("스크롤룰렛")?.tabCompleter = command
        
        // 영어 별칭도 등록
        plugin.getCommand("scrollroulette")?.setExecutor(command)
        plugin.getCommand("scrollroulette")?.tabCompleter = command

        plugin.logger.info("[ScrollRoulette] 스크롤 룰렛 시스템 초기화 완료!")
    }

    /**
     * 시스템 정리 (플러그인 비활성화 시)
     */
    fun shutdown() {
        plugin.logger.info("[ScrollRoulette] 스크롤 룰렛 시스템 종료 중...")
        
        // 모든 활성 세션 정리
        listener.cleanup()
        
        plugin.logger.info("[ScrollRoulette] 스크롤 룰렛 시스템 종료 완료!")
    }

    /**
     * 설정 리로드
     */
    fun reload() {
        manager.reload()
    }
}
