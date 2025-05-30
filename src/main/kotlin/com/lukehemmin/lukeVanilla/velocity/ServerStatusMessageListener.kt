package com.lukehemmin.lukeVanilla.velocity

import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.connection.PluginMessageEvent
import com.velocitypowered.api.proxy.ProxyServer
import com.velocitypowered.api.proxy.ServerConnection
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier
import java.util.logging.Logger

/**
 * Velocity 프록시 서버에서 서버 간 메시지 전달을 처리하는 리스너
 */
class ServerStatusMessageListener(
    private val server: ProxyServer,
    private val logger: Logger
) {
    
    @Subscribe
    fun onPluginMessage(event: PluginMessageEvent) {
        try {
            when (event.identifier) {
                // 로비 → 야생 서버 상태 요청 메시지 처리
                ServerStatusProxy.REQUEST_CHANNEL -> {
                    handleStatusRequest(event)
                }
                
                // 야생 → 로비 서버 상태 응답 메시지 처리 
                ServerStatusProxy.RESPONSE_CHANNEL -> {
                    handleStatusResponse(event)
                }
            }
        } catch (e: Exception) {
            logger.warning("[LukeVanilla] 서버 상태 메시지 처리 중 오류: ${e.message}")
            e.printStackTrace()
        }
    }
    
    /**
     * 로비 서버에서 야생 서버로 상태 요청 메시지 전달
     */
    private fun handleStatusRequest(event: PluginMessageEvent) {
        // 로비 서버에서 온 요청인지 확인 (특정 로비 서버만 허용하려면 여기서 체크)
        val source = event.source
        if (source !is ServerConnection) return
        
        // 메시지 페이로드 확인
        val data = event.data
        val targetServerName = ServerStatusProxy.SURVIVAL_SERVER_ID
        
        // 야생 서버 찾기
        val targetServer = server.getServer(targetServerName).orElse(null) ?: run {
            logger.warning("[LukeVanilla] 대상 서버를 찾을 수 없음: $targetServerName")
            return
        }
        
        // 요청 메시지 전달
        targetServer.sendPluginMessage(ServerStatusProxy.REQUEST_CHANNEL, data)
        logger.info("[LukeVanilla] 서버 상태 요청: ${source.serverInfo.name} -> $targetServerName")
    }
    
    /**
     * 야생 서버에서 로비 서버로 상태 응답 메시지 전달
     */
    private fun handleStatusResponse(event: PluginMessageEvent) {
        // 야생 서버에서 온 응답인지 확인
        val source = event.source
        if (source !is ServerConnection) return
        
        // 응답 데이터
        val data = event.data
        
        // 디버깅: 응답 내용 확인
        val responseStr = String(data)
        logger.info("[LukeVanilla] 서버 상태 응답: ${source.serverInfo.name} -> ${responseStr.take(50)}...")
        
        // 로비 서버로 전달 (모든 로비 서버에 브로드캐스트)
        server.allServers.forEach { serverInfo ->
            if (serverInfo.serverInfo.name != source.serverInfo.name) {
                val optServer = server.getServer(serverInfo.serverInfo.name)
                if (optServer.isPresent) {
                    optServer.get().sendPluginMessage(ServerStatusProxy.RESPONSE_CHANNEL, data)
                }
            }
        }
    }
}
