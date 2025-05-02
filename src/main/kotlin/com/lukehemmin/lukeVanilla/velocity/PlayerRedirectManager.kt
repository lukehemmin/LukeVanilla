package com.lukehemmin.lukeVanilla.velocity

import com.velocitypowered.api.proxy.ProxyServer
import com.velocitypowered.api.proxy.server.RegisteredServer
import net.kyori.adventure.text.Component
import org.slf4j.Logger
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 플레이어의 서버 간 자동 리디렉션을 관리하는 클래스
 */
class PlayerRedirectManager(
    private val server: ProxyServer,
    private val logger: Logger
) {
    // 오프라인 상태의 vanilla 서버로 인해 lobby로 자동 리디렉션된 플레이어들
    private val redirectedToLobbyPlayers = ConcurrentHashMap<UUID, Boolean>()
    
    // 명시적으로 lobby를 선택한 플레이어들
    private val explicitlyChooseLobbyPlayers = ConcurrentHashMap<UUID, Boolean>()
    
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
    }
    
    /**
     * Vanilla 서버가 온라인이 되었을 때 리디렉션된 플레이어들을 자동으로 이동
     */
    fun handleVanillaServerOnline() {
        // 리디렉션된 플레이어가 없으면 처리하지 않음
        if (redirectedToLobbyPlayers.isEmpty()) return
        
        val vanillaServer = server.getServer("vanilla")
        if (vanillaServer.isEmpty) return
        
        logger.info("Handling vanilla server online event for redirected players")
        
        for (entry in redirectedToLobbyPlayers.keys) {
            val playerId = entry
            val playerOptional = server.getPlayer(playerId)
            
            if (playerOptional.isPresent) {
                val player = playerOptional.get()
                
                // 플레이어가 현재 "lobby"에 있고, 명시적으로 lobby를 선택하지 않은 경우에만 이동
                val currentServer = player.currentServer
                if (currentServer.isPresent && 
                    currentServer.get().serverInfo.name.equals("lobby", ignoreCase = true) && 
                    !explicitlyChooseLobbyPlayers.containsKey(player.uniqueId)) {
                    
                    player.createConnectionRequest(vanillaServer.get()).fireAndForget()
                    player.sendMessage(Component.text("Vanilla 서버가 다시 온라인입니다. 자동으로 연결합니다."))
                    logger.info("Auto-connecting ${player.username} to vanilla server as it's back online")
                }
            }
        }
        
        // 리디렉션 목록 초기화
        redirectedToLobbyPlayers.clear()
    }
    
    /**
     * Vanilla 서버가 오프라인이 되려 할 때 리디렉션된 플레이어들을 미리 lobby로 이동
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
        if (isVanillaOnline) {
            val vanillaServer = server.getServer("vanilla")
            if (vanillaServer.isPresent) {
                logger.info("Connecting player $playerName to vanilla server (online)")
                return vanillaServer.get()
            }
        } else {
            val lobbyServer = server.getServer("lobby")
            if (lobbyServer.isPresent) {
                logger.info("Redirecting player $playerName to lobby as vanilla is offline")
                markPlayerRedirectedToLobby(playerId)
                return lobbyServer.get()
            }
        }
        
        // 기본 서버를 찾을 수 없는 경우
        return server.allServers.firstOrNull()
    }
}