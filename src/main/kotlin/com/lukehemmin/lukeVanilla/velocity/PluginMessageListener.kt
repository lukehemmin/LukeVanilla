package com.lukehemmin.lukeVanilla.velocity

import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.connection.PluginMessageEvent
import com.velocitypowered.api.proxy.messages.ChannelIdentifier
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier // Import 추가
import org.slf4j.Logger
import java.io.ByteArrayInputStream
import java.io.DataInputStream

/**
 * Bukkit/Paper 서버에서 전송된 OFFLINE_IMMINENT/ONLINE 플러그인 메시지를 받아
 * PlayerRedirectManager를 통해 자동 리디렉션을 처리합니다.
 */
class PluginMessageListener(
    private val redirectManager: PlayerRedirectManager,
    private val logger: Logger
) {
    // 네임스페이스와 ID를 분리하여 ChannelIdentifier 생성
    private val channel: ChannelIdentifier = MinecraftChannelIdentifier.create("luke", "vanilla_status") // 'of' 대신 'MinecraftChannelIdentifier.create' 사용

    @Subscribe
    fun onPluginMessage(event: PluginMessageEvent) {
        if (event.identifier != channel) return // 'identifier()' 대신 프로퍼티 접근 및 '!=' 사용

        val bytes = event.data // 'as ByteBuf' 캐스팅 제거 및 'data()' 대신 프로퍼티 접근
        val message = DataInputStream(ByteArrayInputStream(bytes)).readUTF()
        // 로깅 시 파라미터 전달 및 프로퍼티 접근
        logger.info("Received plugin message '{}' on channel {}", message, channel.id) // 'id()' 대신 프로퍼티 접근

        when (message) {
            "OFFLINE_IMMINENT" -> redirectManager.handleVanillaServerOffline()
            "ONLINE" -> redirectManager.handleVanillaServerOnline()
            // 로깅 시 파라미터 전달
            else -> logger.warn("Unknown vanilla_status message: {}", message)
        }
    }
}