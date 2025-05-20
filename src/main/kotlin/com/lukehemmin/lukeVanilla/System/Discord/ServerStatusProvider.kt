package com.lukehemmin.lukeVanilla.System.Discord

import org.bukkit.Bukkit

object ServerStatusProvider {
    fun getServerStatusString(): String {
        // TPS, MSPT, Ping 등 실제 서버 상태 정보를 반환 (예시는 Paper API 기준)
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
        // Ping은 서버 전체 평균값을 구할 수 없으므로, 예시로 0으로 표기
        val ping = "0ms"
        return "${tps} tps  ${mspt} mspt  ping ${ping}"
    }
}
