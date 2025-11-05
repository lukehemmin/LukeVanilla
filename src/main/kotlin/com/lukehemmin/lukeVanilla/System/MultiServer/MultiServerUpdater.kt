package com.lukehemmin.lukeVanilla.System.MultiServer

import com.google.gson.Gson
import com.lukehemmin.lukeVanilla.Main
import com.lukehemmin.lukeVanilla.System.Database.Database
import org.bukkit.BanList
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.scheduler.BukkitRunnable
import java.util.concurrent.ConcurrentHashMap
import java.util.Date

/**
 * 야생 서버에서 실행되는 멀티서버 동기화 시스템
 * - 30초마다 서버 상태를 DB에 업데이트
 * - 플레이어 접속/로그아웃 시 온라인 목록 동기화
 * - 교차 서버 명령어 처리 (밴, 킥 등)
 */
class MultiServerUpdater(
    private val plugin: Main,
    private val database: Database
) : Listener {

    private val gson = Gson()
    private var heartbeatTask: BukkitRunnable? = null
    private var commandProcessorTask: BukkitRunnable? = null
    private val onlinePlayersCache = ConcurrentHashMap<String, Long>() // UUID -> join timestamp
    
    companion object {
        const val SERVER_NAME = "vanilla" // 이 서버의 이름
        const val HEARTBEAT_INTERVAL = 30 * 20L // 30초 (틱 단위)
        const val COMMAND_CHECK_INTERVAL = 10 * 20L // 10초 (틱 단위)
    }

    /**
     * 멀티서버 동기화 시스템 시작
     */
    fun start() {
        plugin.logger.info("[MultiServerUpdater] 멀티서버 동기화 시스템을 시작합니다...")
        
        // 이벤트 리스너 등록
        Bukkit.getPluginManager().registerEvents(this, plugin)
        
        // 서버 상태 업데이트 작업 시작 (30초마다)
        startHeartbeatTask()
        
        // 교차 서버 명령어 처리 작업 시작 (10초마다)
        startCommandProcessorTask()
        
        // 현재 온라인 플레이어들을 DB에 등록
        registerCurrentOnlinePlayers()
        
        plugin.logger.info("[MultiServerUpdater] 멀티서버 동기화 시스템이 시작되었습니다.")
    }

    /**
     * 멀티서버 동기화 시스템 중단
     */
    fun stop() {
        plugin.logger.info("[MultiServerUpdater] 멀티서버 동기화 시스템을 중단합니다...")
        
        // 작업들 중단
        heartbeatTask?.cancel()
        commandProcessorTask?.cancel()
        
        // 서버 상태를 'offline'으로 업데이트
        updateServerStatus("offline")
        
        // 모든 온라인 플레이어 제거
        clearAllOnlinePlayers()
        
        plugin.logger.info("[MultiServerUpdater] 멀티서버 동기화 시스템이 중단되었습니다.")
    }

    /**
     * 서버 상태 업데이트 작업 시작
     */
    private fun startHeartbeatTask() {
        heartbeatTask = object : BukkitRunnable() {
            override fun run() {
                try {
                    updateServerHeartbeat()
                } catch (e: Exception) {
                    plugin.logger.warning("[MultiServerUpdater] 서버 상태 업데이트 중 오류: ${e.message}")
                    e.printStackTrace()
                }
            }
        }
        heartbeatTask?.runTaskTimerAsynchronously(plugin, 0L, HEARTBEAT_INTERVAL)
    }

    /**
     * 교차 서버 명령어 처리 작업 시작
     */
    private fun startCommandProcessorTask() {
        commandProcessorTask = object : BukkitRunnable() {
            override fun run() {
                try {
                    processCrossServerCommands()
                } catch (e: Exception) {
                    plugin.logger.warning("[MultiServerUpdater] 교차 서버 명령어 처리 중 오류: ${e.message}")
                    e.printStackTrace()
                }
            }
        }
        commandProcessorTask?.runTaskTimerAsynchronously(plugin, 20L, COMMAND_CHECK_INTERVAL)
    }

    /**
     * 서버 상태를 DB에 업데이트
     */
    private fun updateServerHeartbeat() {
        val tps = getCurrentTPS()
        val mspt = getCurrentMSPT()
        val onlinePlayers = Bukkit.getOnlinePlayers().size
        val maxPlayers = Bukkit.getMaxPlayers()
        
        try {
            database.updateServerHeartbeat(SERVER_NAME, tps, mspt, onlinePlayers, maxPlayers)
            plugin.logger.info("[MultiServerUpdater] 서버 상태 업데이트: TPS=$tps, MSPT=$mspt, Players=$onlinePlayers/$maxPlayers")
        } catch (e: Exception) {
            plugin.logger.warning("[MultiServerUpdater] 서버 상태 업데이트 실패: ${e.message}")
        }
    }

    /**
     * 현재 TPS 조회
     */
    private fun getCurrentTPS(): Double {
        return try {
            val server = Bukkit.getServer()
            val method = server.javaClass.getMethod("getTPS")
            val tpsArray = method.invoke(server) as DoubleArray
            tpsArray[0] // 최근 1분 TPS
        } catch (e: Exception) {
            20.0 // 기본값
        }
    }

    /**
     * 현재 MSPT 조회
     */
    private fun getCurrentMSPT(): Double {
        return try {
            val server = Bukkit.getServer()
            val method = server.javaClass.getMethod("getAverageTickTime")
            method.invoke(server) as Double
        } catch (e: Exception) {
            0.0 // 기본값
        }
    }

    /**
     * 서버 상태만 업데이트 (시작/중단 시 사용)
     */
    private fun updateServerStatus(status: String) {
        try {
            val tps = if (status == "online") getCurrentTPS() else 0.0
            val mspt = if (status == "online") getCurrentMSPT() else 0.0
            val onlinePlayers = if (status == "online") Bukkit.getOnlinePlayers().size else 0
            val maxPlayers = Bukkit.getMaxPlayers()
            
            database.updateServerHeartbeat(SERVER_NAME, tps, mspt, onlinePlayers, maxPlayers)
        } catch (e: Exception) {
            plugin.logger.warning("[MultiServerUpdater] 서버 상태 업데이트 실패: ${e.message}")
        }
    }

    /**
     * 현재 온라인 플레이어들을 DB에 등록
     */
    private fun registerCurrentOnlinePlayers() {
        Bukkit.getOnlinePlayers().forEach { player ->
            addOnlinePlayer(player)
        }
    }

    /**
     * 모든 온라인 플레이어를 DB에서 제거
     */
    private fun clearAllOnlinePlayers() {
        onlinePlayersCache.keys.forEach { playerUuid ->
            try {
                database.removeOnlinePlayer(SERVER_NAME, playerUuid)
            } catch (e: Exception) {
                plugin.logger.warning("[MultiServerUpdater] 플레이어 제거 실패: $playerUuid")
            }
        }
        onlinePlayersCache.clear()
    }

    /**
     * 플레이어 접속 시 이벤트 처리
     */
    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player
        addOnlinePlayer(player)
        plugin.logger.info("[MultiServerUpdater] 플레이어 접속: ${player.name}")
    }

    /**
     * 플레이어 로그아웃 시 이벤트 처리
     */
    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val player = event.player
        removeOnlinePlayer(player)
        plugin.logger.info("[MultiServerUpdater] 플레이어 로그아웃: ${player.name}")
    }

    /**
     * 온라인 플레이어를 DB에 추가
     */
    private fun addOnlinePlayer(player: Player) {
        try {
            val uuid = player.uniqueId.toString()
            val location = player.location
            
            database.updateOnlinePlayer(
                serverName = SERVER_NAME,
                playerUuid = uuid,
                playerName = player.name,
                playerDisplayName = player.displayName,
                locationWorld = location.world?.name,
                locationX = location.x,
                locationY = location.y,
                locationZ = location.z
            )
            
            onlinePlayersCache[uuid] = System.currentTimeMillis()
        } catch (e: Exception) {
            plugin.logger.warning("[MultiServerUpdater] 플레이어 추가 실패: ${player.name} - ${e.message}")
        }
    }

    /**
     * 온라인 플레이어를 DB에서 제거
     */
    private fun removeOnlinePlayer(player: Player) {
        try {
            val uuid = player.uniqueId.toString()
            database.removeOnlinePlayer(SERVER_NAME, uuid)
            onlinePlayersCache.remove(uuid)
        } catch (e: Exception) {
            plugin.logger.warning("[MultiServerUpdater] 플레이어 제거 실패: ${player.name} - ${e.message}")
        }
    }

    /**
     * 대기중인 교차 서버 명령어들을 처리
     */
    private fun processCrossServerCommands() {
        try {
            val pendingCommands = database.getPendingCrossServerCommands(SERVER_NAME)
            
            if (pendingCommands.isNotEmpty()) {
                plugin.logger.info("[MultiServerUpdater] 처리할 교차 서버 명령어 ${pendingCommands.size}개 발견")
            }
            
            pendingCommands.forEach { command ->
                processCommand(command)
            }
        } catch (e: Exception) {
            plugin.logger.warning("[MultiServerUpdater] 교차 서버 명령어 처리 중 오류: ${e.message}")
        }
    }

    /**
     * 개별 교차 서버 명령어 처리
     */
    private fun processCommand(command: Database.CrossServerCommand) {
        try {
            plugin.logger.info("[MultiServerUpdater] 명령어 처리 시작: ${command.commandType} for ${command.targetPlayerName}")
            
            val commandData = gson.fromJson(command.commandData, Map::class.java) as Map<String, Any>
            val success = when (command.commandType.lowercase()) {
                "ban" -> processBanCommand(command, commandData)
                "unban" -> processUnbanCommand(command, commandData)
                "kick" -> processKickCommand(command, commandData)
                "warning" -> processWarningCommand(command, commandData)
                "broadcast" -> processBroadcastCommand(command, commandData)
                else -> {
                    plugin.logger.warning("[MultiServerUpdater] 알 수 없는 명령어 타입: ${command.commandType}")
                    false
                }
            }
            
            // 명령어 실행 결과 업데이트
            database.markCrossServerCommandExecuted(
                commandId = command.id,
                success = success,
                errorMessage = if (!success) "명령어 실행 실패" else null
            )
            
        } catch (e: Exception) {
            plugin.logger.warning("[MultiServerUpdater] 명령어 처리 실패: ${e.message}")
            database.markCrossServerCommandExecuted(
                commandId = command.id,
                success = false,
                errorMessage = "예외 발생: ${e.message}"
            )
        }
    }

    /**
     * 밴 명령어 처리
     */
    private fun processBanCommand(command: Database.CrossServerCommand, commandData: Map<String, Any>): Boolean {
        return try {
            val reason = commandData["reason"] as? String ?: "관리자에 의한 밴"
            val duration = commandData["duration"] as? String // "permanent" 또는 기간
            
            Bukkit.getScheduler().runTask(plugin, Runnable {
                // 플레이어가 온라인이면 킥
                val player = Bukkit.getPlayer(java.util.UUID.fromString(command.targetPlayerUuid))
                player?.kickPlayer("§c서버에서 밴되었습니다.\n§7사유: $reason")
                
                // 실제 밴 처리 - BanList API 사용
                try {
                    val banList: org.bukkit.BanList<org.bukkit.OfflinePlayer> = Bukkit.getBanList(org.bukkit.BanList.Type.PROFILE)
                    val expiration = if (duration == "permanent") null else {
                        // 임시 밴인 경우 만료 시간 계산 (예: "7d", "1h" 등)
                        calculateBanExpiration(duration)
                    }
                    
                    // 플레이어 이름으로 OfflinePlayer 객체 가져오기
                    val offlinePlayer = Bukkit.getOfflinePlayer(command.targetPlayerName)
                    banList.addBan(offlinePlayer, reason, expiration, command.issuedBy)
                    plugin.logger.info("[MultiServerUpdater] 밴 처리 완료: ${command.targetPlayerName} - 사유: $reason")
                } catch (e: Exception) {
                    plugin.logger.warning("[MultiServerUpdater] BanList API 오류: ${e.message}")
                }
            })
            
            plugin.logger.info("[MultiServerUpdater] 밴 처리 완료: ${command.targetPlayerName}")
            true
        } catch (e: Exception) {
            plugin.logger.warning("[MultiServerUpdater] 밴 처리 실패: ${e.message}")
            false
        }
    }

    /**
     * 언밴 명령어 처리
     */
    private fun processUnbanCommand(command: Database.CrossServerCommand, commandData: Map<String, Any>): Boolean {
        return try {
            Bukkit.getScheduler().runTask(plugin, Runnable {
                // 실제 언밴 처리 - BanList API 사용
                try {
                    val banList: org.bukkit.BanList<org.bukkit.OfflinePlayer> = Bukkit.getBanList(org.bukkit.BanList.Type.PROFILE)
                    
                    // 플레이어 이름으로 OfflinePlayer 객체 가져오기
                    val offlinePlayer = Bukkit.getOfflinePlayer(command.targetPlayerName)
                    banList.pardon(offlinePlayer)
                    plugin.logger.info("[MultiServerUpdater] 언밴 처리 완료: ${command.targetPlayerName}")
                } catch (e: Exception) {
                    plugin.logger.warning("[MultiServerUpdater] BanList API 오류 (언밴): ${e.message}")
                }
            })
            
            plugin.logger.info("[MultiServerUpdater] 언밴 처리 완료: ${command.targetPlayerName}")
            true
        } catch (e: Exception) {
            plugin.logger.warning("[MultiServerUpdater] 언밴 처리 실패: ${e.message}")
            false
        }
    }

    /**
     * 킥 명령어 처리
     */
    private fun processKickCommand(command: Database.CrossServerCommand, commandData: Map<String, Any>): Boolean {
        return try {
            val reason = commandData["reason"] as? String ?: "관리자에 의한 킥"
            
            Bukkit.getScheduler().runTask(plugin, Runnable {
                val player = Bukkit.getPlayer(java.util.UUID.fromString(command.targetPlayerUuid))
                if (player != null && player.isOnline) {
                    player.kickPlayer("§c서버에서 킥되었습니다.\n§7사유: $reason")
                    plugin.logger.info("[MultiServerUpdater] 킥 처리 완료: ${command.targetPlayerName}")
                } else {
                    plugin.logger.info("[MultiServerUpdater] 킥 대상 플레이어가 오프라인: ${command.targetPlayerName}")
                }
            })
            
            true
        } catch (e: Exception) {
            plugin.logger.warning("[MultiServerUpdater] 킥 처리 실패: ${e.message}")
            false
        }
    }

    /**
     * 경고 명령어 처리 (실제로는 별도 처리 불필요, 로그만 남김)
     */
    private fun processWarningCommand(command: Database.CrossServerCommand, commandData: Map<String, Any>): Boolean {
        plugin.logger.info("[MultiServerUpdater] 경고 명령어 확인: ${command.targetPlayerName}")
        return true // 경고는 이미 DB에 저장되어 있으므로 성공으로 처리
    }

    /**
     * 전체 공지 명령어 처리
     */
    private fun processBroadcastCommand(command: Database.CrossServerCommand, commandData: Map<String, Any>): Boolean {
        return try {
            val message = commandData["message"] as? String ?: return false
            val prefix = commandData["prefix"] as? String ?: "§c[공지]"

            Bukkit.getScheduler().runTask(plugin, Runnable {
                // 전체 채팅에 공지 메시지 전송
                Bukkit.broadcastMessage("$prefix $message")
                plugin.logger.info("[MultiServerUpdater] 전체 공지 전송 완료: $message")
            })

            true
        } catch (e: Exception) {
            plugin.logger.warning("[MultiServerUpdater] 전체 공지 처리 실패: ${e.message}")
            false
        }
    }

    /**
     * 밴 기간 문자열을 Date 객체로 변환
     * @param duration "7d", "1h", "30m" 등의 문자열
     * @return 만료 시각의 Date 객체 또는 null (영구 밴)
     */
    private fun calculateBanExpiration(duration: String?): Date? {
        if (duration.isNullOrEmpty() || duration == "permanent") {
            return null // 영구 밴
        }

        try {
            val now = System.currentTimeMillis()
            val regex = Regex("(\\d+)([dhms])")
            val matchResult = regex.find(duration.lowercase())

            if (matchResult != null) {
                val amount = matchResult.groupValues[1].toLong()
                val unit = matchResult.groupValues[2]

                val millis = when (unit) {
                    "s" -> amount * 1000 // 초
                    "m" -> amount * 60 * 1000 // 분
                    "h" -> amount * 60 * 60 * 1000 // 시간
                    "d" -> amount * 24 * 60 * 60 * 1000 // 일
                    else -> 0
                }

                return if (millis > 0) Date(now + millis) else null
            }
        } catch (e: Exception) {
            plugin.logger.warning("[MultiServerUpdater] 밴 기간 파싱 오류: $duration - ${e.message}")
        }

        return null // 파싱 실패 시 영구 밴으로 처리
    }
}