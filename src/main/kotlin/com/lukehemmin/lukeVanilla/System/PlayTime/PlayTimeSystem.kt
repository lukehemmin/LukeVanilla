package com.lukehemmin.lukeVanilla.System.PlayTime

import com.lukehemmin.lukeVanilla.Main
import com.lukehemmin.lukeVanilla.System.Database.Database
import com.lukehemmin.lukeVanilla.System.Debug.DebugManager

/**
 * PlayTime 시스템의 메인 진입점 클래스
 */
class PlayTimeSystem(
    private val plugin: Main,
    private val database: Database,
    private val debugManager: DebugManager
) {
    
    private lateinit var playTimeData: PlayTimeData
    private lateinit var playTimeManager: PlayTimeManager
    private lateinit var playTimeListener: PlayTimeListener
    private lateinit var playTimeCommand: PlayTimeCommand
    
    /**
     * PlayTime 시스템을 활성화합니다
     */
    fun enable() {
        // 데이터 계층 초기화
        playTimeData = PlayTimeData(database)
        
        // 매니저 계층 초기화
        playTimeManager = PlayTimeManager(plugin, playTimeData, debugManager)
        
        // 이벤트 리스너 등록
        playTimeListener = PlayTimeListener(playTimeManager)
        plugin.server.pluginManager.registerEvents(playTimeListener, plugin)
        
        // 명령어 등록
        playTimeCommand = PlayTimeCommand(playTimeManager)
        plugin.getCommand("플레이타임")?.setExecutor(playTimeCommand)
        plugin.getCommand("플레이타임")?.tabCompleter = playTimeCommand
        
        // 기존 온라인 플레이어들의 세션 시작 시간 설정 (서버 재시작 시)
        plugin.server.onlinePlayers.forEach { player ->
            playTimeManager.onPlayerJoin(player)
        }
        
        // 자동 저장 기능 시작
        playTimeManager.startAutoSave()
        
        plugin.logger.info("[PlayTime] 플레이타임 시스템이 활성화되었습니다.")
    }
    
    /**
     * PlayTime 시스템을 비활성화합니다
     */
    fun disable() {
        // 자동 저장 기능 중단
        if (::playTimeManager.isInitialized) {
            playTimeManager.stopAutoSave()
            // 모든 온라인 플레이어의 플레이타임 최종 저장
            playTimeManager.saveAllOnlinePlayersPlayTime()
        }
        
        plugin.logger.info("[PlayTime] 플레이타임 시스템이 비활성화되었습니다.")
    }
    
    /**
     * PlayTimeManager 인스턴스를 반환합니다
     * @return PlayTimeManager
     */
    fun getPlayTimeManager(): PlayTimeManager {
        return playTimeManager
    }
}