package com.lukehemmin.lukeVanilla.velocity

import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.connection.DisconnectEvent
import com.velocitypowered.api.event.player.KickedFromServerEvent
import com.velocitypowered.api.event.player.PlayerChooseInitialServerEvent
import com.velocitypowered.api.event.player.ServerConnectedEvent
import com.velocitypowered.api.proxy.Player
import com.velocitypowered.api.proxy.ProxyServer
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
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
            serverMonitor.isServerOnline("vanilla") // 수정됨
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
     * 플레이어 연결 해제 이벤트 처리 (프록시에서 나갈 때)
     */
    @Subscribe
    fun onPlayerDisconnect(event: DisconnectEvent) {
        val player = event.player
        redirectManager.playerDisconnected(player.uniqueId) // 모든 목록에서 제거
        logger.info("Player ${player.username} disconnected from proxy. Cleaned up state.")
    }

    /**
     * 서버로부터 연결이 끊겼을 때 (킥 당했을 때) 처리
     */
    @Subscribe
    fun onKickedFromServer(event: KickedFromServerEvent) {
        val player = event.player
        val kickedFromServer = event.server
        val reason = event.reasonComponent.orElse(Component.text("알 수 없는 이유"))

        logger.info("Player ${player.username} was kicked from ${kickedFromServer.serverInfo.name}. Reason: ${Component.text().append(reason).content()}")

        // 플레이어가 vanilla 서버에 있었고, 서버가 갑자기 다운된 경우 (정상적인 종료 메시지가 아닌 경우)
        // 또는 플레이어가 재연결을 시도해야 하는 다른 서버에서 튕겼을 경우
        if (kickedFromServer.serverInfo.name.equals("vanilla", ignoreCase = true) ||
            redirectManager.isPlayerAwaitingReconnect(player.uniqueId) && player.currentServer.map { it.serverInfo }.orElse(null) == kickedFromServer.serverInfo) {

            // 서버가 오프라인 상태인지 확인 (ServerMonitor를 통해)
            val isServerActuallyOffline = !serverMonitor.isServerOnline(kickedFromServer.serverInfo.name)

            if (isServerActuallyOffline || event.kickReasonAllowsStayConnected()) {
                 // 플레이어를 프록시에 유지하고 재연결 시도 메시지 표시
                event.result = KickedFromServerEvent.DisconnectPlayer.create(
                    Component.text("서버와의 연결이 끊겼습니다. 잠시 후 자동으로 재연결을 시도합니다...\n이유: ").append(reason).color(NamedTextColor.RED)
                )
                redirectManager.markPlayerAwaitingReconnect(player.uniqueId, kickedFromServer)
                logger.info("Player ${player.username} will stay on proxy, awaiting reconnect to ${kickedFromServer.serverInfo.name}.")
                // 여기서 타이틀이나 액션바 메시지를 주기적으로 보내는 로직을 추가할 수 있습니다. (별도 클래스 또는 PlayerRedirectManager에서 관리)
                sendReconnectingMessageTask(player, kickedFromServer.serverInfo.name)

            } else {
                // 서버는 온라인이지만 다른 이유로 킥 (예: 밴, 다른 곳에서 로그인)
                // 이 경우 일반적인 킥으로 처리 (로비로 보내거나, 기본 킥 메시지 유지)
                val lobby = server.getServer("lobby")
                if (lobby.isPresent) {
                    event.result = KickedFromServerEvent.RedirectPlayer.create(lobby.get(), reason)
                    logger.info("Player ${player.username} kicked from ${kickedFromServer.serverInfo.name}, redirecting to lobby. Reason: ${Component.text().append(reason).content()}")
                } else {
                    // 로비 서버가 없으면 기본 킥 처리 (프록시에서 연결 해제)
                    event.result = KickedFromServerEvent.DisconnectPlayer.create(
                        Component.text("서버에서 연결이 끊겼습니다: ").append(reason).append(Component.text("\n로비 서버를 찾을 수 없어 연결을 종료합니다.")).color(NamedTextColor.RED)
                    )
                    logger.warn("Player ${player.username} kicked from ${kickedFromServer.serverInfo.name}, no lobby server found. Disconnecting.")
                }
            }
        } else {
            // vanilla 서버가 아니거나, 재연결 대기 상태가 아닌 다른 서버에서 킥된 경우, 기본 로비로 리디렉션
            val lobby = server.getServer("lobby")
            if (lobby.isPresent && event.player.currentServer.map { it.serverInfo.name }.orElse("") != "lobby") {
                event.result = KickedFromServerEvent.RedirectPlayer.create(lobby.get(), reason)
                logger.info("Player ${player.username} kicked from ${kickedFromServer.serverInfo.name}, redirecting to lobby.")
            } else {
                 // 이미 로비에 있거나 로비가 없는 경우, 기본 킥 처리
                event.result = KickedFromServerEvent.DisconnectPlayer.create(reason)
                 logger.info("Player ${player.username} kicked from ${kickedFromServer.serverInfo.name}. Default kick action. Reason: ${Component.text().append(reason).content()}")
            }
        }
    }

    private fun sendReconnectingMessageTask(player: Player, targetServerName: String) {
        // TODO: 이 태스크를 관리하고, 플레이어가 재연결되거나 연결이 끊어지면 취소해야 합니다.
        // 현재는 간단하게 메시지만 반복적으로 보냅니다. 실제 운영 환경에서는 Task 객체를 저장하고 관리해야 합니다.
        server.scheduler.buildTask(server, Runnable {
            if (player.isActive && redirectManager.isPlayerAwaitingReconnect(player.uniqueId)) {
                val awaitingServerInfo = redirectManager.getAwaitingReconnectServerInfo(player.uniqueId)
                if (awaitingServerInfo != null && awaitingServerInfo.serverInfo.name.equals(targetServerName, ignoreCase = true)) {
                    player.sendActionBar(Component.text("$targetServerName 서버에 다시 연결 중입니다...").color(NamedTextColor.YELLOW))
                } else if (awaitingServerInfo == null) {
                    // 더 이상 재연결 대기 상태가 아니면 이 태스크는 중지되어야 하지만,
                    // 여기서는 간단히 로그만 남기고, 실제로는 태스크 취소 로직이 필요합니다.
                    logger.debug("Player ${player.username} is no longer awaiting reconnect, task for $targetServerName should be cancelled.")
                    // 이 태스크를 취소하는 로직이 필요. (예: task.cancel())
                    // 지금은 이 Runnable 내에서 직접 취소할 수 없으므로, 외부에서 관리해야 함.
                }
            }
        }).repeat(2L, java.util.concurrent.TimeUnit.SECONDS).schedule()
        // 이 태스크는 플레이어가 성공적으로 재연결되거나 프록시를 떠날 때 취소되어야 합니다.
        // PlayerRedirectManager에서 재연결 성공 시 또는 playerDisconnected 시 이 태스크를 관리/취소하는 로직이 필요합니다.
        // 지금은 간단히 반복 메시지만 보냅니다. 실제 구현에서는 Task ID를 저장하고 관리해야 합니다.
        logger.debug("Scheduled reconnecting message for ${player.username} for server $targetServerName")
    }
}