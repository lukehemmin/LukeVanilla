package com.lukehemmin.lukeVanilla.System

import org.bukkit.plugin.java.JavaPlugin
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

/**
 * 서버 종료 직전 Velocity에 "OFFLINE_IMMINENT" 메시지를 전송하여
 * 프록시에서 플레이어를 미리 로비로 리디렉트하도록 알립니다.
 */
object VanillaShutdownNotifier {
    private const val CHANNEL = "luke:vanilla_status"

    /**
     * 플러그인 메시지 채널 등록 (onEnable 시 호출)
     */
    fun registerChannel(plugin: JavaPlugin) {
        plugin.server.messenger.registerOutgoingPluginChannel(plugin, CHANNEL)
    }    /**
     * 서버 종료 예고 메시지 전송 (onDisable 직전에 호출)
     */
    fun notifyShutdownImminent(plugin: JavaPlugin) {
        // 플러그인이 비활성화 중인 경우 메시지 전송 불가
        if (!plugin.isEnabled) {
            plugin.logger.warning("Plugin is disabled, cannot send shutdown notification")
            return
        }
        
        try {
            val baos = ByteArrayOutputStream()
            val outStream = DataOutputStream(baos)
            // 기록할 메시지
            outStream.writeUTF("OFFLINE_IMMINENT")
            val data = baos.toByteArray()

            // 온라인 플레이어에게 플러그인 메시지 전송
            plugin.server.onlinePlayers.forEach { player ->
                player.sendPluginMessage(plugin, CHANNEL, data)
            }
            plugin.logger.info("Sent OFFLINE_IMMINENT to Velocity for ${plugin.server.onlinePlayers.size} players")
        } catch (e: Exception) {
            plugin.logger.warning("Failed to send shutdown notification: ${e.message}")
        }
    }
}