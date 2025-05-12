package com.lukehemmin.lukeVanilla.velocity

import com.velocitypowered.api.proxy.ProxyServer
import com.velocitypowered.api.proxy.server.RegisteredServer
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.slf4j.Logger
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.ArrayList

/**
 * 플레이어의 서버 간 자동 리디렉션을 관리하는 클래스
 */
class PlayerRedirectManager(
    private val server: ProxyServer,
    private val logger: Logger
) {
    // 오프라인 상태의 vanilla 서버로 인해 lobby로 자동 리디렉션된 플레이어들
    private val redirectedToLobbyPlayers = ConcurrentHashMap<UUID, Boolean>()
    private val explicitlyChooseLobbyPlayers = ConcurrentHashMap<UUID, Boolean>()
    private val awaitingReconnectPlayers = ConcurrentHashMap<UUID, RegisteredServer>() // 추가: 재연결 대기 플레이어 (UUID, 이전 서버)

    /**
     * 플레이어가 명시적으로 lobby를 선택했는지 표시
     */
    fun markPlayerExplicitlyChooseLobby(playerId: UUID) {
        explicitlyChooseLobbyPlayers[playerId] = true
        redirectedToLobbyPlayers.remove(playerId)
        logger.debug("Player $playerId explicitly chose lobby")
    }
    
    /**
     * 플레이어가 자동으로 lobby로 리디렉션되었음을 표시
     */
    fun markPlayerRedirectedToLobby(playerId: UUID) {
        // 이미 명시적으로 lobby를 선택한 경우는 리디렉션 목록에 추가하지 않음
        if (!explicitlyChooseLobbyPlayers.containsKey(playerId)) {
            redirectedToLobbyPlayers[playerId] = true
            logger.debug("Player $playerId was redirected to lobby due to vanilla being offline")
        }
    }
    
    /**
     * 플레이어가 명시적으로 lobby를 선택했는지 확인
     */
    fun isPlayerExplicitlyChooseLobby(playerId: UUID): Boolean {
        return explicitlyChooseLobbyPlayers.containsKey(playerId)
    }
    
    /**
     * 플레이어가 자동으로 lobby로 리디렉션되었는지 확인
     */
    fun isPlayerRedirectedToLobby(playerId: UUID): Boolean {
        return redirectedToLobbyPlayers.containsKey(playerId)
    }
    
    /**
     * 플레이어가 서버에서 나갈 때 데이터 정리
     */
    fun playerDisconnected(playerId: UUID) {
        redirectedToLobbyPlayers.remove(playerId)
        explicitlyChooseLobbyPlayers.remove(playerId)
        awaitingReconnectPlayers.remove(playerId) // 추가
        logger.debug("Player $playerId disconnected, removed from all tracking lists.")
    }

    fun markPlayerAwaitingReconnect(playerId: UUID, previousServer: RegisteredServer) {
        awaitingReconnectPlayers[playerId] = previousServer
        redirectedToLobbyPlayers.remove(playerId)
        explicitlyChooseLobbyPlayers.remove(playerId)
        logger.info("Player $playerId marked as awaiting reconnect from ${previousServer.serverInfo.name}")
    }

    fun isPlayerAwaitingReconnect(playerId: UUID): Boolean {
        return awaitingReconnectPlayers.containsKey(playerId)
    }

    fun getAwaitingReconnectServerInfo(playerId: UUID): RegisteredServer? {
        return awaitingReconnectPlayers[playerId]
    }

    /**
     * 특정 서버가 온라인이 되었을 때 재연결 대기 중이거나 로비로 리디렉션된 플레이어들을 처리
     */
    fun handleServerOnline(onlineServer: RegisteredServer) {
        val serverName = onlineServer.serverInfo.name
        logger.info("Handling $serverName server online event.")

        // 1. 재연결 대기 중인 플레이어 처리
        val playersToReconnect = awaitingReconnectPlayers.filter { it.value.serverInfo.name.equals(serverName, ignoreCase = true) }
        if (playersToReconnect.isNotEmpty()) {
            logger.info("Attempting to reconnect ${playersToReconnect.size} players to $serverName.")
            for ((playerId, previousServer) in playersToReconnect) { // previousServer는 onlineServer와 동일해야 함
                val playerOptional = server.getPlayer(playerId)
                if (playerOptional.isPresent) {
                    val player = playerOptional.get()
                    player.createConnectionRequest(onlineServer).fireAndForget()
                    player.sendMessage(Component.text("${onlineServer.serverInfo.name} 서버가 다시 온라인입니다. 자동으로 연결합니다.").color(NamedTextColor.GREEN))
                    logger.info("Auto-connecting ${player.username} (awaiting reconnect) to ${onlineServer.serverInfo.name}.")
                    awaitingReconnectPlayers.remove(playerId)
                } else {
                    awaitingReconnectPlayers.remove(playerId)
                    logger.info("Player $playerId for $serverName reconnect is offline. Removed from list.")
                }
            }
        } else {
            logger.info("No players awaiting direct reconnect to $serverName.")
        }

        // 2. "vanilla" 서버가 온라인된 경우, 로비로 자동 리디렉션되었던 플레이어 처리
        //    (재연결 대기 중인 플레이어는 위에서 이미 처리되었으므로 중복 처리 방지)
        if (serverName.equals("vanilla", ignoreCase = true)) {
            handleRedirectedToLobbyPlayers(onlineServer)
        }
    }

    private fun handleRedirectedToLobbyPlayers(vanillaServer: RegisteredServer) {
        if (redirectedToLobbyPlayers.isEmpty()) return

        logger.info("Handling vanilla server online event for players previously redirected to lobby.")
        val playersToMove = ArrayList(redirectedToLobbyPlayers.keys) // 동시성 문제 방지를 위해 복사

        for (playerId in playersToMove) {
            if (isPlayerAwaitingReconnect(playerId)) continue // 이미 재연결 처리되었거나 대기 중이면 건너뜀

            val playerOptional = server.getPlayer(playerId)
            if (playerOptional.isPresent) {
                val player = playerOptional.get()
                val currentServer = player.currentServer
                if (currentServer.isPresent &&
                    currentServer.get().serverInfo.name.equals("lobby", ignoreCase = true) &&
                    !explicitlyChooseLobbyPlayers.containsKey(player.uniqueId)) {

                    player.createConnectionRequest(vanillaServer).fireAndForget()
                    player.sendMessage(Component.text("Vanilla 서버가 다시 온라인입니다. 자동으로 연결합니다.").color(NamedTextColor.GREEN))
                    logger.info("Auto-connecting ${player.username} (from lobby redirect) to vanilla server.")
                    redirectedToLobbyPlayers.remove(playerId)
                }
            } else {
                redirectedToLobbyPlayers.remove(playerId)
            }
        }
    }
    
    /**
     * Vanilla 서버가 오프라인이 되려 할 때 (정상 종료 시) 플레이어들을 미리 lobby로 이동
     */
    fun handleVanillaServerOffline() {
        val vanillaServer = server.getServer("vanilla")
        val lobbyServer = server.getServer("lobby")
        if (vanillaServer.isEmpty || lobbyServer.isEmpty) return
        logger.info("Handling vanilla server offline event: moving players to lobby")
        vanillaServer.get().playersConnected.forEach { player ->
            val playerId = player.uniqueId
            // 명시적으로 lobby를 선택한 경우에는 이동하지 않음
            if (!explicitlyChooseLobbyPlayers.containsKey(playerId)) {
                // 이동 전에 자동 리디렉션으로 표시
                markPlayerRedirectedToLobby(playerId)
                player.createConnectionRequest(lobbyServer.get()).fireAndForget()
                player.sendMessage(Component.text("Vanilla 서버가 종료 중입니다. 임시로 lobby로 이동합니다."))
                logger.info("Auto-redirecting ${player.username} to lobby due to vanilla shutdown")
            }
        }
    }
    
    /**
     * 초기 서버 선택 - vanilla가 온라인이면 vanilla로, 오프라인이면 lobby로
     */
    fun selectInitialServer(playerId: UUID, playerName: String, isVanillaOnline: Boolean): RegisteredServer? {
        // 1. 재연결 대기 중인 플레이어가 재접속한 경우
        if (isPlayerAwaitingReconnect(playerId)) {
            val previousServer = awaitingReconnectPlayers[playerId] // non-null due to isPlayerAwaitingReconnect check
            if (previousServer != null) { // Null-safety, though theoretically not needed
                 // 이전 서버가 vanilla이고 현재 온라인 상태이면 바로 그곳으로 보냄
                if (previousServer.serverInfo.name.equals("vanilla", ignoreCase = true) && isVanillaOnline) {
                    logger.info("Reconnecting player $playerName (was awaiting) directly to vanilla server (now online).")
                    // 바로 연결하므로 대기 목록에서 제거. 메시지는 연결 성공 시 서버에서.
                    awaitingReconnectPlayers.remove(playerId)
                    return previousServer
                } else {
                    // 이전 서버가 vanilla가 아니거나, vanilla이지만 아직 오프라인인 경우
                    // 일단 로비로 보내고, 해당 서버가 온라인되면 handleServerOnline에서 처리됨.
                    val lobby = server.getServer("lobby")
                    if (lobby.isPresent) {
                        logger.info("Player $playerName (was awaiting ${previousServer.serverInfo.name}) is connecting. Sending to lobby temporarily.")
                        return lobby.get()
                    }
                }
            }
        }

        // 2. 일반적인 초기 서버 선택 로직
        if (isVanillaOnline) {
            val vanillaServer = server.getServer("vanilla")
            if (vanillaServer.isPresent) {
                logger.info("Connecting player $playerName to vanilla server (online).")
                return vanillaServer.get()
            }
        }
        
        // Vanilla가 오프라인이거나 접속 불가능한 경우 로비로
        val lobbyServer = server.getServer("lobby")
        if (lobbyServer.isPresent) {
            logger.info("Redirecting player $playerName to lobby (vanilla offline or unavailable).")
            // 이 플레이어가 재연결 대기 상태가 아니라면, 로비로 자동 리디렉션된 것으로 표시
            if (!isPlayerAwaitingReconnect(playerId)) {
                 markPlayerRedirectedToLobby(playerId)
            }
            return lobbyServer.get()
        }
        
        // 최후의 수단: 사용 가능한 첫 번째 서버
        logger.warn("No specific initial server for $playerName. Trying first available server.")
        return server.allServers.firstOrNull()
    }
}