package com.lukehemmin.lukeVanilla.velocity

import com.google.inject.Inject
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent
import com.velocitypowered.api.plugin.Plugin
import com.velocitypowered.api.plugin.annotation.DataDirectory
import com.velocitypowered.api.proxy.ProxyServer
import com.velocitypowered.api.proxy.messages.ChannelIdentifier
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier // Import 추가
import com.velocitypowered.api.command.CommandManager // 명령어 관리자 import
import com.velocitypowered.api.command.CommandMeta // 명령어 메타데이터 import
import org.slf4j.Logger
import java.nio.file.Path
import com.lukehemmin.lukeVanilla.velocity.PluginMessageListener

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
        registerCommands() // 명령어 등록 메소드 호출
        
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
        // 플러그인 메시지 리스너 초기화 및 채널 등록
        // 'of' 대신 'MinecraftChannelIdentifier.create' 사용
        val channel = MinecraftChannelIdentifier.create("luke", "vanilla_status")
        server.channelRegistrar.register(channel)
        // 수정: PluginMessageListener 생성자에 server 및 serverMonitor 추가, 인자 순서 조정
        val pluginMsgListener = PluginMessageListener(server, logger, redirectManager, serverMonitor)
        server.eventManager.register(this, pluginMsgListener)

        // 서버 상태 변경 콜백 설정 (새로운 방식으로 변경)
        serverMonitor.onServerStatusChange { (changedServer, isOnline) ->
            // 메인 스레드에서 온라인/오프라인 이벤트 처리
            server.scheduler.buildTask(this) {
                if (isOnline) {
                    // PlayerRedirectManager에 특정 서버가 온라인되었음을 알림
                    redirectManager.handleServerOnline(changedServer)
                } else {
                    // 특정 서버가 오프라인되었을 때의 처리
                    // 예: "vanilla" 서버가 오프라인되면 기존 로직대로 로비로 이동
                    if (changedServer.serverInfo.name.equals("vanilla", ignoreCase = true)) {
                        redirectManager.handleVanillaServerOffline()
                    }
                    // 다른 서버 오프라인 시 추가 로직이 필요하면 여기에 구현
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

    /**
     * 명령어 등록
     */
    private fun registerCommands() {
        val commandManager: CommandManager = server.commandManager

        // /로비서버 명령어 등록
        val lobbyCommandMeta: CommandMeta = commandManager.metaBuilder("로비서버")
            .aliases("lobby", "l") // 단축 명령어 설정 (선택 사항)
            .plugin(this)
            .build()
        commandManager.register(lobbyCommandMeta, LobbyCommand(server, logger, redirectManager))

        // /야생서버 명령어 등록
        val wildServerCommandMeta: CommandMeta = commandManager.metaBuilder("야생서버")
            .aliases("wild", "w", "vanilla") // 단축 명령어 설정 (선택 사항)
            .plugin(this)
            .build()
        // "야생서버"는 "vanilla" 서버로 가정합니다.
        commandManager.register(wildServerCommandMeta, WildServerCommand(server, logger, redirectManager))

        logger.info("Successfully registered commands: /로비서버, /야생서버")
    }
}