package com.lukehemmin.lukeVanilla.System.Discord

import com.google.gson.Gson
import com.lukehemmin.lukeVanilla.Main
import org.bukkit.entity.Player
import org.bukkit.plugin.messaging.PluginMessageListener
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

/**
 * 야생 서버에서 서버 상태 요청을 처리하는 리스너
 * - 로비 서버(AI)로부터 상태 요청을 받음
 * - 현재 서버 상태를 수집하여 응답
 */
class ServerStatusListener(private val plugin: Main) : PluginMessageListener {

    private val gson = Gson()
    
    companion object {
        private var instance: ServerStatusListener? = null
        
        fun getInstance(plugin: Main): ServerStatusListener {
            if (instance == null) {
                instance = ServerStatusListener(plugin)
            }
            return instance!!
        }
    }
    
    init {
        // 요청 메시지 채널 등록
        plugin.server.messenger.registerIncomingPluginChannel(
            plugin, 
            Main.CHANNEL_SERVER_STATUS_REQUEST, 
            this
        )
    }
    
    /**
     * 로비 서버로부터 상태 요청 메시지 처리
     */
    override fun onPluginMessageReceived(channel: String, player: Player, data: ByteArray) {
        if (channel != Main.CHANNEL_SERVER_STATUS_REQUEST) return
        
        try {
            // 메시지 내용 파싱
            val jsonRequest = String(data)
            val requestData = gson.fromJson(jsonRequest, Map::class.java) as? Map<String, Any> ?: mapOf()
            
            // 요청 ID 추출
            val requestId = (requestData["requestId"] as? Double)?.toLong() ?: 0
            
            plugin.logger.info("[서버 상태] 상태 요청 수신 (ID: $requestId)")
            
            // 현재 서버 상태 수집
            val serverStatus = collectServerStatus()
            
            // 응답에 요청 ID 추가
            val responseData = serverStatus.toMutableMap()
            responseData["requestId"] = requestId
            
            // 응답 전송
            sendStatusResponse(player, responseData)
            
        } catch (e: Exception) {
            plugin.logger.warning("[서버 상태] 요청 처리 중 오류: ${e.message}")
            e.printStackTrace()
        }
    }
    
    /**
     * 서버 상태 정보 수집 (TPS, MSPT, 플레이어 수 등)
     */
    private fun collectServerStatus(): Map<String, Any> {
        val result = mutableMapOf<String, Any>()
        
        try {
            // TPS 수집 (Paper API 활용)
            val serverClass = plugin.server.javaClass
            val tpsMethod = serverClass.getMethod("getTPS")
            val tpsArray = tpsMethod.invoke(plugin.server) as DoubleArray
            val tps = tpsArray[0] // 최근 1분 TPS
            
            // MSPT 수집 (Paper API 활용)
            val msptMethod = serverClass.getMethod("getAverageTickTime")
            val mspt = msptMethod.invoke(plugin.server) as Double
            
            // 플레이어 수 정보
            val onlinePlayers = plugin.server.onlinePlayers.size
            val maxPlayers = plugin.server.maxPlayers
            
            // 결과 맵에 저장
            result["tps"] = String.format("%.2f", tps)
            result["mspt"] = String.format("%.2f", mspt)
            result["onlinePlayers"] = onlinePlayers
            result["maxPlayers"] = maxPlayers
            result["serverName"] = plugin.config.getString("service.type") ?: "Unknown"
            result["timestamp"] = System.currentTimeMillis()
            
        } catch (e: Exception) {
            plugin.logger.warning("[서버 상태] 서버 상태 수집 중 오류: ${e.message}")
            e.printStackTrace()
            
            // 오류 시 기본값
            result["tps"] = "N/A"
            result["mspt"] = "N/A"
            result["onlinePlayers"] = plugin.server.onlinePlayers.size
            result["maxPlayers"] = plugin.server.maxPlayers
            result["serverName"] = plugin.config.getString("service.type") ?: "Unknown"
            result["timestamp"] = System.currentTimeMillis()
        }
        
        return result
    }
    
    /**
     * 수집한 서버 상태를 응답으로 전송
     */
    private fun sendStatusResponse(player: Player, responseData: Map<String, Any>) {
        try {
            // 응답 페이로드 생성
            val outputStream = ByteArrayOutputStream()
            val dataOutput = DataOutputStream(outputStream)
            val jsonResponse = gson.toJson(responseData)
            dataOutput.writeUTF(jsonResponse)
            
            // 플레이어를 통해 전송 (Velocity 프록시로 전달됨)
            player.sendPluginMessage(
                plugin,
                Main.CHANNEL_SERVER_STATUS_RESPONSE, 
                outputStream.toByteArray()
            )
            
            plugin.logger.info("[서버 상태] 상태 응답 전송 완료 (ID: ${responseData["requestId"]})")
            
        } catch (e: Exception) {
            plugin.logger.warning("[서버 상태] 응답 전송 중 오류: ${e.message}")
            e.printStackTrace()
        }
    }
}
