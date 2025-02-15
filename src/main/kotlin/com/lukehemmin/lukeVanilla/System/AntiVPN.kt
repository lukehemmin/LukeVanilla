package com.lukehemmin.lukeVanilla.System

import com.google.gson.JsonParser
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.AsyncPlayerPreLoginEvent
import org.bukkit.plugin.java.JavaPlugin
import java.io.InputStreamReader
import java.net.URL

class AntiVPN(private val plugin: JavaPlugin) : Listener {

    @EventHandler
    fun onPlayerPreLogin(event: AsyncPlayerPreLoginEvent){
        val ipAddress = event.address.hostAddress

        try {
            val url = URL("http://ip-api.com/json/$ipAddress?fields=proxy")
            val connection = url.openConnection()
            connection.connectTimeout = 5000 // 5초 타임아웃

            InputStreamReader(connection.getInputStream()).use { reader ->
                val jsonElement = JsonParser.parseReader(reader)
                val jsonObject = jsonElement.asJsonObject

                if (jsonObject.get("proxy").asBoolean) {
                    event.disallow(
                        AsyncPlayerPreLoginEvent.Result.KICK_OTHER, "VPN을 사용하고 계시네요. VPN 접속을 해제한 후 다시 접속해주세요."
                    )
                    plugin.logger.info("${event.name}님이 VPN을 사용하여 접속을 시도했습니다. (${ipAddress})")
                } else {
                    // plugin.logger.info("${event.name}님이 VPN을 사용하지 않고 접속을 시도했습니다. (${ipAddress})")
                }
            }
        } catch (e: Exception) {
            plugin.logger.warning("VPN 접속 여부 확인 중 오류가 발생했습니다. (${ipAddress})")
            plugin.logger.warning("API 오류: ${e.message}")
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, "VPN 접속 여부를 확인하는 중 오류가 발생했습니다. 다시 접속해주세요.")
        }
    }
}