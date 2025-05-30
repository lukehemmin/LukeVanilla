package com.lukehemmin.lukeVanilla.System.Discord

import com.lukehemmin.lukeVanilla.Main
import org.bukkit.Bukkit
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

object ServerStatusProvider {
    private var isSurvivalStatusRequesting = false
    private var lastSurvivalStatus: Map<String, Any>? = null
    private var lastSurvivalStatusTime = 0L
    
    /**
     * 로비 및 야생 서버 상태를 함께 포함한 문자열 반환
     */
    fun getServerStatusString(): String {
        // 현재(로비) 서버 상태 정보 수집
        val localStatus = getLocalServerStatusString()
        
        // 야생 서버 상태 정보 수집 (비동기 요청)
        val mainPlugin = Main.getPlugin()
        val requester = ServerStatusRequester.getInstance(mainPlugin)
        
        // 야생 서버 상태가 최근에 요청되지 않았다면 신규 요청
        if (!isSurvivalStatusRequesting && 
            (lastSurvivalStatus == null || System.currentTimeMillis() - lastSurvivalStatusTime > 30000)) {
            
            isSurvivalStatusRequesting = true
            
            requester.requestSurvivalServerStatus().thenAccept { status ->
                lastSurvivalStatus = status
                lastSurvivalStatusTime = System.currentTimeMillis()
                isSurvivalStatusRequesting = false
            }
        }
        
        // 야생 서버 상태 문자열 생성 (캡시된 데이터 활용)
        val survivalStatus = if (lastSurvivalStatus != null) {
            val tps = lastSurvivalStatus?.get("tps") ?: "N/A"
            val mspt = lastSurvivalStatus?.get("mspt") ?: "N/A"
            val onlinePlayers = lastSurvivalStatus?.get("onlinePlayers") ?: 0
            val maxPlayers = lastSurvivalStatus?.get("maxPlayers") ?: 0
            
            "\n야생: TPS: $tps, MSPT: $mspt, Players: $onlinePlayers/$maxPlayers"
        } else {
            "\n야생: 상태 정보 로딩 중..."
        }
        
        // 로비 + 야생 서버 상태 통합
        return "로비: $localStatus$survivalStatus"
    }
    
    /**
     * 현재(로비) 서버의 상태만 수집
     */
    private fun getLocalServerStatusString(): String {
        // 현재 서버의 TPS, MSPT 조회
        val tps = Bukkit.getServer().let { server ->
            try {
                val method = server.javaClass.getMethod("getTPS")
                val tpsArr = method.invoke(server) as? DoubleArray
                tpsArr?.getOrNull(0)?.let { String.format("%.2f", it) } ?: "N/A"
            } catch (e: Exception) {
                "N/A"
            }
        }
        val mspt = Bukkit.getServer().let { server ->
            try {
                val method = server.javaClass.getMethod("getAverageTickTime")
                val msptVal = method.invoke(server) as? Double
                msptVal?.let { String.format("%.2f", it) } ?: "N/A"
            } catch (e: Exception) {
                "N/A"
            }
        }
        
        // 플레이어 수 정보
        val onlinePlayers = Bukkit.getOnlinePlayers().size
        val maxPlayers = Bukkit.getMaxPlayers()
        val ping = "0ms"
        
        return "TPS: ${tps}, MSPT: ${mspt}, Ping: ${ping}, Players: ${onlinePlayers}/${maxPlayers}"
    }
}
