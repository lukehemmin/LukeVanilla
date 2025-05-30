package com.lukehemmin.lukeVanilla.System.Discord

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.lukehemmin.lukeVanilla.Main
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.messaging.PluginMessageListener
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.concurrent.CompletableFuture

/**
 * 로비 서버에서 야생 서버의 상태를 요청하는 역할
 * - AI가 서버 상태를 조회할 때, 야생 서버에 PluginMessage를 보내 실시간 상태 요청
 * - 응답을 기다렸다가 결과 반환
 */
class ServerStatusRequester(private val plugin: Main) : PluginMessageListener {

    private val gson = Gson()
    private var pendingRequests = mutableMapOf<Long, CompletableFuture<Map<String, Any>>>()
    
    companion object {
        private var instance: ServerStatusRequester? = null
        
        fun getInstance(plugin: Main): ServerStatusRequester {
            if (instance == null) {
                instance = ServerStatusRequester(plugin)
            }
            return instance!!
        }
    }
    
    init {
        // 응답 메시지 채널 등록
        plugin.server.messenger.registerIncomingPluginChannel(
            plugin, 
            Main.CHANNEL_SERVER_STATUS_RESPONSE, 
            this
        )
    }
    
    /**
     * 야생 서버의 상태를 요청하고 결과를 기다림
     * @return 서버 상태 정보를 담은 Future 객체
     */
    fun requestSurvivalServerStatus(): CompletableFuture<Map<String, Any>> {
        val requestId = System.currentTimeMillis()
        val future = CompletableFuture<Map<String, Any>>()
        pendingRequests[requestId] = future
        
        // 타임아웃 설정 (3초 후 응답 없으면 기본값 반환)
        Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, Runnable {
            if (!future.isDone) {
                val fallbackData = mapOf(
                    "tps" to "N/A",
                    "mspt" to "N/A",
                    "onlinePlayers" to 0,
                    "maxPlayers" to 0,
                    "serverName" to "야생 서버",
                    "error" to "응답 시간 초과"
                )
                future.complete(fallbackData)
                pendingRequests.remove(requestId)
                plugin.logger.warning("[서버 상태] 야생 서버 상태 요청 시간 초과")
            }
        }, 60L) // 3초 (20틱/초)
        
        // 요청 전송
        sendStatusRequest(requestId)
        return future
    }
    
    /**
     * 야생 서버에 상태 요청 메시지 전송
     */
    private fun sendStatusRequest(requestId: Long) {
        try {
            // 플레이어를 통해 메시지 전송 (Velocity 프록시를 통과)
            val players = Bukkit.getOnlinePlayers()
            if (players.isEmpty()) {
                plugin.logger.warning("[서버 상태] 온라인 플레이어가 없어 요청을 보낼 수 없습니다")
                failRequest(requestId, "온라인 플레이어 없음")
                return
            }
            
            // 요청 메시지 만들기
            val outputStream = ByteArrayOutputStream()
            val dataOutput = DataOutputStream(outputStream)
            val requestData = mapOf(
                "requestId" to requestId,
                "type" to "status_request",
                "timestamp" to System.currentTimeMillis()
            )
            dataOutput.writeUTF(gson.toJson(requestData))
            
            // 첫 번째 플레이어를 통해 요청 전송
            val firstPlayer = players.first()
            firstPlayer.sendPluginMessage(
                plugin,
                Main.CHANNEL_SERVER_STATUS_REQUEST, 
                outputStream.toByteArray()
            )
            
            plugin.logger.info("[서버 상태] 야생 서버에 상태 요청 전송 (ID: $requestId)")
            
        } catch (e: Exception) {
            plugin.logger.warning("[서버 상태] 요청 전송 중 오류: ${e.message}")
            failRequest(requestId, "요청 전송 오류: ${e.message}")
        }
    }
    
    /**
     * 야생 서버로부터 응답 메시지 처리
     */
    override fun onPluginMessageReceived(channel: String, player: Player, data: ByteArray) {
        if (channel != Main.CHANNEL_SERVER_STATUS_RESPONSE) return
        
        try {
            // 메시지 내용 파싱
            val jsonResponse = String(data)
            val responseData = gson.fromJson(jsonResponse, Map::class.java) as? Map<String, Any> ?: mapOf()
            
            // 요청 ID 확인
            val requestId = (responseData["requestId"] as? Double)?.toLong() ?: 0
            
            // 대기 중인 요청 처리
            pendingRequests[requestId]?.let { future ->
                future.complete(responseData)
                pendingRequests.remove(requestId)
                plugin.logger.info("[서버 상태] 야생 서버 응답 수신 (ID: $requestId)")
            }
            
        } catch (e: JsonSyntaxException) {
            plugin.logger.warning("[서버 상태] 응답 파싱 오류: ${e.message}")
        } catch (e: Exception) {
            plugin.logger.warning("[서버 상태] 응답 처리 중 오류: ${e.message}")
        }
    }
    
    /**
     * 요청 실패 처리
     */
    private fun failRequest(requestId: Long, reason: String) {
        pendingRequests[requestId]?.let { future ->
            val fallbackData = mapOf(
                "tps" to "N/A",
                "mspt" to "N/A",
                "onlinePlayers" to 0,
                "maxPlayers" to 0,
                "serverName" to "야생 서버",
                "error" to reason
            )
            future.complete(fallbackData)
            pendingRequests.remove(requestId)
        }
    }
}
