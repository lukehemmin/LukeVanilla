package com.lukehemmin.lukeVanilla.velocity

import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.connection.DisconnectEvent
import com.velocitypowered.api.event.player.PlayerChooseInitialServerEvent
import com.velocitypowered.api.event.player.ServerConnectedEvent
import com.velocitypowered.api.proxy.ProxyServer
import org.slf4j.Logger

/**
 * Velocity 이벤트 리스너 클래스
 */
class EventListeners(
    private val server: ProxyServer,
    private val logger: Logger,
    private val serverMonitor: ServerMonitor,
    private val redirectManager: PlayerRedirectManager
) {

    /**
     * 초기 서버 선택 시 처리
     * 플레이어가 프록시에 최초 접속할 때 호출됨
     */
    @Subscribe
    fun onPlayerChooseInitialServer(event: PlayerChooseInitialServerEvent) {
        val player = event.player
        
        // vanilla 서버 상태에 따라 적절한 서버로 라우팅
        val targetServer = redirectManager.selectInitialServer(
            player.uniqueId, 
            player.username, 
            serverMonitor.isVanillaOnline()
        )
        
        if (targetServer != null) {
            event.setInitialServer(targetServer)
        }
    }
    
    /**
     * 서버 연결 완료 이벤트 처리
     * 플레이어가 서버에 성공적으로 연결된 후 호출됨
     */
    @Subscribe
    fun onServerConnected(event: ServerConnectedEvent) {
        val player = event.player
        val serverName = event.server.serverInfo.name
        
        // lobby 서버에 연결된 경우
        if (serverName.equals("lobby", ignoreCase = true)) {
            // 자동 리디렉션이 아닌 경우 명시적 선택으로 표시
            if (!redirectManager.isPlayerRedirectedToLobby(player.uniqueId)) {
                redirectManager.markPlayerExplicitlyChooseLobby(player.uniqueId)
                logger.debug("Player ${player.username} connected to lobby (not redirected)")
            }
        }
    }
    
    /**
     * 플레이어 연결 해제 이벤트 처리
     */
    @Subscribe
    fun onPlayerDisconnect(event: DisconnectEvent) {
        val player = event.player
        redirectManager.playerDisconnected(player.uniqueId)
        logger.debug("Player ${player.username} disconnected, removing from tracking")
    }
}