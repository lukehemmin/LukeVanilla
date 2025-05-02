package com.lukehemmin.lukeVanilla.velocity

import com.google.inject.Inject
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent
import com.velocitypowered.api.plugin.Plugin
import com.velocitypowered.api.plugin.annotation.DataDirectory
import com.velocitypowered.api.proxy.ProxyServer
import org.slf4j.Logger
import java.nio.file.Path

@Plugin(
    id = "lukevanilla",
    name = "LukeVanilla Velocity",
    version = "1.0-SNAPSHOT",
    description = "LukeVanilla Velocity plugin",
    authors = ["lukehemmin"]
)
class VelocityMain @Inject constructor(
    private val server: ProxyServer,
    private val logger: Logger,
    @DataDirectory private val dataDirectory: Path
) {
    // 클래스 인스턴스
    private lateinit var serverMonitor: ServerMonitor
    private lateinit var redirectManager: PlayerRedirectManager
    private lateinit var eventListeners: EventListeners

    @Subscribe
    fun onProxyInitialization(event: ProxyInitializeEvent) {
        logger.info("LukeVanilla Velocity plugin has been enabled!")
        
        // 인스턴스 초기화
        initializeComponents()
        
        // 서버 상태 모니터링 시작
        startMonitoring()
        
        // 이벤트 리스너 등록
        registerEventListeners()
        
        logger.info("Automatic server redirection system initialized")
    }
    
    @Subscribe
    fun onProxyShutdown(event: ProxyShutdownEvent) {
        // 서버 종료 시 모니터링 중지
        if (::serverMonitor.isInitialized) {
            serverMonitor.stop()
        }
        logger.info("LukeVanilla Velocity plugin has been disabled!")
    }
    
    /**
     * 컴포넌트 초기화
     */
    private fun initializeComponents() {
        // 서버 모니터 초기화
        serverMonitor = ServerMonitor(server, logger, this)
        
        // 플레이어 리디렉션 매니저 초기화
        redirectManager = PlayerRedirectManager(server, logger)
        
        // 이벤트 리스너 초기화
        eventListeners = EventListeners(server, logger, serverMonitor, redirectManager)
        
        // Vanilla 서버 상태 변경 콜백 설정
        serverMonitor.onVanillaStatusChange { isOnline ->
            // 메인 스레드에서 온라인/오프라인 이벤트 처리
            server.scheduler.buildTask(this) {
                if (isOnline) {
                    redirectManager.handleVanillaServerOnline()
                } else {
                    redirectManager.handleVanillaServerOffline()
                }
            }.schedule()
        }
    }
    
    /**
     * 서버 모니터링 시작
     */
    private fun startMonitoring() {
        serverMonitor.start(10) // 10초 간격으로 모니터링
    }
    
    /**
     * 이벤트 리스너 등록
     */
    private fun registerEventListeners() {
        server.eventManager.register(this, eventListeners)
    }
}