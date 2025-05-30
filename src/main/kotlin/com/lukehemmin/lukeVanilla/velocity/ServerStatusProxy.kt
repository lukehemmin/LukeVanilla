package com.lukehemmin.lukeVanilla.velocity

import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent
import com.velocitypowered.api.plugin.Plugin
import com.velocitypowered.api.proxy.ProxyServer
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier
import java.util.logging.Logger
import javax.inject.Inject

/**
 * Velocity 프록시 플러그인 - 서버 상태 메시지 중계 역할
 * 로비 서버에서 요청한 서버 상태 메시지를 야생 서버로 전달하고,
 * 야생 서버의 응답을 로비 서버로 전달합니다.
 */
@Plugin(
    id = "lukevanilla-serverstatus",
    name = "LukeVanilla ServerStatus Proxy",
    version = "1.0",
    description = "서버 간 상태 정보 중계 플러그인",
    authors = ["LukeHemmin"]
)
class ServerStatusProxy @Inject constructor(
    private val server: ProxyServer,
    private val logger: Logger
) {
    companion object {
        // 서버 간 통신 채널
        val REQUEST_CHANNEL = MinecraftChannelIdentifier.create("lukevanilla", "serverstatus_request")
        val RESPONSE_CHANNEL = MinecraftChannelIdentifier.create("lukevanilla", "serverstatus_response")
        
        // 야생 서버 식별자 (config에서 가져오거나 하드코딩)
        const val SURVIVAL_SERVER_ID = "survival"
    }

    @Subscribe
    fun onProxyInitialize(event: ProxyInitializeEvent) {
        // 채널 등록
        server.channelRegistrar.register(REQUEST_CHANNEL, RESPONSE_CHANNEL)
        
        // 메시지 리스너 등록
        server.eventManager.register(this, ServerStatusMessageListener(server, logger))
        
        logger.info("[LukeVanilla] 서버 상태 프록시 초기화 완료")
    }
}
