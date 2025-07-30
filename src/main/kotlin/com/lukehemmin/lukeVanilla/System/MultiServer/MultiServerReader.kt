package com.lukehemmin.lukeVanilla.System.MultiServer

import com.lukehemmin.lukeVanilla.Main
import com.lukehemmin.lukeVanilla.System.Database.Database
import org.bukkit.Bukkit

/**
 * 로비 서버에서 실행되는 멀티서버 정보 조회 시스템
 * - 야생 서버의 실시간 상태 조회
 * - 전체 서버의 온라인 플레이어 목록 조회
 * - 플레이어별 현재 서버 위치 확인
 */
class MultiServerReader(
    private val plugin: Main,
    private val database: Database
) {

    companion object {
        const val LOBBY_SERVER = "lobby"
        const val VANILLA_SERVER = "vanilla"
    }

    /**
     * 통합 서버 상태 문자열 반환
     * - 로비 서버 + 야생 서버 상태를 함께 표시
     */
    fun getIntegratedServerStatus(): String {
        val lobbyStatus = getLocalServerStatus()
        val vanillaStatus = getVanillaServerStatus()
        
        return "로비: $lobbyStatus\n야생: $vanillaStatus"
    }

    /**
     * 현재(로비) 서버의 상태 조회
     */
    private fun getLocalServerStatus(): String {
        val tps = getCurrentTPS()
        val mspt = getCurrentMSPT()
        val onlinePlayers = Bukkit.getOnlinePlayers().size
        val maxPlayers = Bukkit.getMaxPlayers()
        
        return "TPS: $tps, MSPT: $mspt, Ping: 0ms, Players: $onlinePlayers/$maxPlayers"
    }

    /**
     * 야생 서버의 상태 조회 (DB 기반)
     */
    private fun getVanillaServerStatus(): String {
        return try {
            val heartbeat = database.getServerHeartbeat(VANILLA_SERVER)
            
            if (heartbeat != null) {
                val tps = String.format("%.2f", heartbeat.tps)
                val mspt = String.format("%.2f", heartbeat.mspt)
                "TPS: $tps, MSPT: $mspt, Players: ${heartbeat.onlinePlayers}/${heartbeat.maxPlayers}"
            } else {
                "TPS: N/A, MSPT: N/A, Players: 0/0 (오프라인 또는 응답 없음)"
            }
        } catch (e: Exception) {
            plugin.logger.warning("[MultiServerReader] 야생 서버 상태 조회 실패: ${e.message}")
            "TPS: N/A, MSPT: N/A, Players: 0/0 (조회 실패)"
        }
    }

    /**
     * 야생 서버의 상세 상태 정보 조회
     */
    fun getVanillaServerHeartbeat(): Database.ServerHeartbeat? {
        return try {
            database.getServerHeartbeat(VANILLA_SERVER)
        } catch (e: Exception) {
            plugin.logger.warning("[MultiServerReader] 야생 서버 상세 상태 조회 실패: ${e.message}")
            null
        }
    }

    /**
     * 특정 서버의 온라인 플레이어 목록 조회
     */
    fun getOnlinePlayersFromServer(serverName: String): List<Database.OnlinePlayerInfo> {
        return try {
            database.getOnlinePlayersFromServer(serverName)
        } catch (e: Exception) {
            plugin.logger.warning("[MultiServerReader] 서버 '$serverName' 온라인 플레이어 조회 실패: ${e.message}")
            emptyList()
        }
    }

    /**
     * 전체 서버의 온라인 플레이어 목록 조회
     */
    fun getAllOnlinePlayers(): Map<String, List<Database.OnlinePlayerInfo>> {
        val result = mutableMapOf<String, List<Database.OnlinePlayerInfo>>()
        
        try {
            // 로비 서버의 온라인 플레이어
            val lobbyPlayers = database.getOnlinePlayersFromServer(LOBBY_SERVER)
            if (lobbyPlayers.isNotEmpty()) {
                result[LOBBY_SERVER] = lobbyPlayers
            }
            
            // 야생 서버의 온라인 플레이어
            val vanillaPlayers = database.getOnlinePlayersFromServer(VANILLA_SERVER)
            if (vanillaPlayers.isNotEmpty()) {
                result[VANILLA_SERVER] = vanillaPlayers
            }
        } catch (e: Exception) {
            plugin.logger.warning("[MultiServerReader] 전체 온라인 플레이어 조회 실패: ${e.message}")
        }
        
        return result
    }

    /**
     * 플레이어가 현재 어느 서버에 접속중인지 확인
     */
    fun getPlayerCurrentServer(playerUuid: String): String? {
        return try {
            database.getPlayerCurrentServer(playerUuid)
        } catch (e: Exception) {
            plugin.logger.warning("[MultiServerReader] 플레이어 '$playerUuid' 현재 서버 조회 실패: ${e.message}")
            null
        }
    }

    /**
     * 플레이어가 어느 서버든 접속중인지 확인
     */
    fun isPlayerOnlineAnywhere(playerUuid: String): Boolean {
        return try {
            database.isPlayerOnlineInAnyServer(playerUuid)
        } catch (e: Exception) {
            plugin.logger.warning("[MultiServerReader] 플레이어 '$playerUuid' 온라인 상태 확인 실패: ${e.message}")
            false
        }
    }

    /**
     * 플레이어 이름으로 현재 온라인 상태 및 서버 조회
     */
    fun getPlayerStatusByName(playerName: String): PlayerStatus? {
        return try {
            // 먼저 Player_Data에서 UUID 조회
            val playerData = database.getPlayerDataByNickname(playerName)
            if (playerData != null) {
                val currentServer = database.getPlayerCurrentServer(playerData.uuid)
                val onlinePlayers = if (currentServer != null) {
                    database.getOnlinePlayersFromServer(currentServer)
                        .find { it.playerUuid == playerData.uuid }
                } else null
                
                return PlayerStatus(
                    playerName = playerData.nickname,
                    playerUuid = playerData.uuid,
                    isOnline = currentServer != null,
                    currentServer = currentServer,
                    locationInfo = onlinePlayers?.let {
                        LocationInfo(
                            world = it.locationWorld ?: "unknown",
                            x = it.locationX,
                            y = it.locationY,
                            z = it.locationZ
                        )
                    }
                )
            }
            null
        } catch (e: Exception) {
            plugin.logger.warning("[MultiServerReader] 플레이어 '$playerName' 상태 조회 실패: ${e.message}")
            null
        }
    }

    /**
     * 온라인 플레이어 수 총합 조회
     */
    fun getTotalOnlinePlayersCount(): PlayerCount {
        return try {
            val lobbyPlayers = database.getOnlinePlayersFromServer(LOBBY_SERVER).size
            val vanillaPlayers = database.getOnlinePlayersFromServer(VANILLA_SERVER).size
            
            PlayerCount(
                lobby = lobbyPlayers,
                vanilla = vanillaPlayers,
                total = lobbyPlayers + vanillaPlayers
            )
        } catch (e: Exception) {
            plugin.logger.warning("[MultiServerReader] 총 온라인 플레이어 수 조회 실패: ${e.message}")
            PlayerCount(0, 0, 0)
        }
    }

    /**
     * 현재 서버의 TPS 조회
     */
    private fun getCurrentTPS(): String {
        return try {
            val server = Bukkit.getServer()
            val method = server.javaClass.getMethod("getTPS")
            val tpsArray = method.invoke(server) as DoubleArray
            String.format("%.2f", tpsArray[0])
        } catch (e: Exception) {
            "20.00"
        }
    }

    /**
     * 현재 서버의 MSPT 조회
     */
    private fun getCurrentMSPT(): String {
        return try {
            val server = Bukkit.getServer()
            val method = server.javaClass.getMethod("getAverageTickTime")
            val mspt = method.invoke(server) as Double
            String.format("%.2f", mspt)
        } catch (e: Exception) {
            "0.00"
        }
    }

    /**
     * 서버 상태 요약 정보 조회
     */
    fun getServerStatusSummary(): ServerStatusSummary {
        val lobbyHeartbeat = try {
            Database.ServerHeartbeat(
                serverName = LOBBY_SERVER,
                tps = getCurrentTPS().toDouble(),
                mspt = getCurrentMSPT().toDouble(),
                onlinePlayers = Bukkit.getOnlinePlayers().size,
                maxPlayers = Bukkit.getMaxPlayers(),
                serverStatus = "online",
                lastUpdate = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            null
        }
        
        val vanillaHeartbeat = getVanillaServerHeartbeat()
        val playerCount = getTotalOnlinePlayersCount()
        
        return ServerStatusSummary(
            lobbyStatus = lobbyHeartbeat,
            vanillaStatus = vanillaHeartbeat,
            totalPlayers = playerCount
        )
    }

    // 데이터 클래스들
    data class PlayerStatus(
        val playerName: String,
        val playerUuid: String,
        val isOnline: Boolean,
        val currentServer: String?,
        val locationInfo: LocationInfo?
    )

    data class LocationInfo(
        val world: String,
        val x: Double,
        val y: Double,
        val z: Double
    ) {
        fun getFormattedLocation(): String {
            return "$world (${x.toInt()}, ${y.toInt()}, ${z.toInt()})"
        }
    }

    data class PlayerCount(
        val lobby: Int,
        val vanilla: Int,
        val total: Int
    )

    data class ServerStatusSummary(
        val lobbyStatus: Database.ServerHeartbeat?,
        val vanillaStatus: Database.ServerHeartbeat?,
        val totalPlayers: PlayerCount
    )
}