package com.lukehemmin.lukeVanilla.System.PlayTime

import com.lukehemmin.lukeVanilla.Main
import com.lukehemmin.lukeVanilla.System.Debug.DebugManager
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitTask
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * PlayTime 시스템의 비즈니스 로직을 담당하는 매니저 클래스
 */
class PlayTimeManager(
    val plugin: Main,
    private val playTimeData: PlayTimeData,
    private val debugManager: DebugManager
) {
    
    // 현재 세션의 플레이어들의 세션 시작 시간을 메모리에 캐시
    private val sessionStartTimes = ConcurrentHashMap<UUID, Long>()
    
    // 주기적 저장 태스크
    private var autoSaveTask: BukkitTask? = null
    
    // 자동 저장 간격 (초) - 기본 5분
    private val autoSaveIntervalSeconds = 300L
    
    /**
     * 플레이어가 서버에 접속했을 때 호출
     * @param player 접속한 플레이어
     */
    fun onPlayerJoin(player: Player) {
        val currentTime = getCurrentTimeMillis()
        sessionStartTimes[player.uniqueId] = currentTime
        
        // 데이터베이스 세션 시작 시간 저장을 비동기로 처리
        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            try {
                playTimeData.setSessionStartTime(player.uniqueId, currentTime)
                debugManager.log("PlayTime", "[ASYNC] ${player.name} 세션 시작 시간 DB 저장 완료")
            } catch (e: Exception) {
                plugin.logger.warning("[PlayTime] ${player.name} 세션 시작 시간 저장 실패: ${e.message}")
            }
        })
        
        debugManager.log("PlayTime", "${player.name} 플레이어가 접속했습니다. 세션 시작: ${formatTimestamp(currentTime)}")
    }
    
    /**
     * 플레이어가 서버에서 나갔을 때 호출
     * @param player 나간 플레이어
     */
    fun onPlayerQuit(player: Player) {
        val sessionStartTime = sessionStartTimes.remove(player.uniqueId)
        if (sessionStartTime != null) {
            val currentTime = getCurrentTimeMillis()
            val sessionDurationMs = currentTime - sessionStartTime
            val sessionDurationSeconds = millisToSeconds(sessionDurationMs)
            
            debugManager.log("PlayTime", "${player.name} 플레이어가 나갔습니다. 마지막 세션: ${formatDuration(sessionDurationMs)}")
            
            // DB 업데이트를 비동기로 처리 (메인 스레드 블로킹 방지)
            plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
                try {
                    // 현재 세션 동안만의 시간을 더함 (자동 저장으로 이미 저장된 부분 제외)
                    val previousPlayTime = playTimeData.getTotalPlayTime(player.uniqueId)
                    val newTotalPlayTime = previousPlayTime + sessionDurationSeconds
                    
                    // 데이터베이스 업데이트 (세션 시작 시간은 null로 설정)
                    val success = playTimeData.updatePlayTimeInfo(player.uniqueId, newTotalPlayTime, null)
                    
                    if (success) {
                        debugManager.log("PlayTime", "[ASYNC] ${player.name} 최종 플레이타임 저장 완료: ${formatDuration(newTotalPlayTime * 1000)}")
                    } else {
                        plugin.logger.warning("[PlayTime] ${player.name} 최종 플레이타임 저장 실패")
                    }
                } catch (e: Exception) {
                    plugin.logger.severe("[PlayTime] ${player.name} 퇴장 처리 중 오류: ${e.message}")
                    e.printStackTrace()
                }
            })
        } else {
            debugManager.log("PlayTime", "${player.name} 플레이어 퇴장 - 세션 정보 없음 (이미 처리됨 또는 오류)")
        }
    }
    
    /**
     * 플레이어의 현재 총 플레이타임을 가져옵니다 (현재 세션 포함)
     * @param player 플레이어
     * @return 총 플레이타임(초)
     */
    fun getCurrentTotalPlayTime(player: Player): Long {
        val savedPlayTime = playTimeData.getTotalPlayTime(player.uniqueId)
        val sessionStartTime = sessionStartTimes[player.uniqueId]
        
        return if (sessionStartTime != null) {
            val currentSessionTimeMs = getCurrentTimeMillis() - sessionStartTime
            val currentSessionTimeSeconds = millisToSeconds(currentSessionTimeMs)
            savedPlayTime + currentSessionTimeSeconds
        } else {
            savedPlayTime
        }
    }
    
    /**
     * 플레이어의 저장된 총 플레이타임을 가져옵니다 (현재 세션 제외)
     * @param playerUuid 플레이어 UUID
     * @return 저장된 총 플레이타임(초)
     */
    fun getSavedTotalPlayTime(playerUuid: UUID): Long {
        return playTimeData.getTotalPlayTime(playerUuid)
    }
    
    /**
     * 플레이어의 현재 세션 시간을 가져옵니다
     * @param player 플레이어
     * @return 현재 세션 시간(초), 세션이 없으면 0
     */
    fun getCurrentSessionTime(player: Player): Long {
        val sessionStartTime = sessionStartTimes[player.uniqueId]
        return if (sessionStartTime != null) {
            val sessionDurationMs = getCurrentTimeMillis() - sessionStartTime
            millisToSeconds(sessionDurationMs)
        } else {
            0L
        }
    }
    
    /**
     * 플레이어가 현재 온라인인지 확인
     * @param playerUuid 플레이어 UUID
     * @return 온라인 여부
     */
    fun isPlayerOnline(playerUuid: UUID): Boolean {
        return sessionStartTimes.containsKey(playerUuid)
    }
    
    /**
     * 플레이타임을 일, 시간, 분, 초로 포맷팅합니다
     * @param totalSeconds 총 초
     * @return 포맷된 문자열
     */
    fun formatPlayTime(totalSeconds: Long): String {
        val days = TimeUnit.SECONDS.toDays(totalSeconds)
        val hours = TimeUnit.SECONDS.toHours(totalSeconds) % 24
        val minutes = TimeUnit.SECONDS.toMinutes(totalSeconds) % 60
        val seconds = totalSeconds % 60
        
        return buildString {
            if (days > 0) append("${days}일 ")
            if (hours > 0) append("${hours}시간 ")
            if (minutes > 0) append("${minutes}분 ")
            if (seconds > 0 || isEmpty()) append("${seconds}초")
        }.trim()
    }
    
    /**
     * 플레이타임이 특정 시간 이상인지 확인
     * @param player 플레이어
     * @param requiredDays 필요한 일수
     * @return 조건 만족 여부
     */
    fun hasPlayedForDays(player: Player, requiredDays: Int): Boolean {
        val totalPlayTime = getCurrentTotalPlayTime(player)
        val requiredSeconds = TimeUnit.DAYS.toSeconds(requiredDays.toLong())
        return totalPlayTime >= requiredSeconds
    }
    
    /**
     * 플레이타임이 특정 시간 이상인지 확인 (UUID 버전)
     * @param playerUuid 플레이어 UUID
     * @param requiredDays 필요한 일수
     * @return 조건 만족 여부
     */
    fun hasPlayedForDays(playerUuid: UUID, requiredDays: Int): Boolean {
        val totalPlayTime = getSavedTotalPlayTime(playerUuid)
        val requiredSeconds = TimeUnit.DAYS.toSeconds(requiredDays.toLong())
        return totalPlayTime >= requiredSeconds
    }
    
    /**
     * 플레이타임이 7일 미만인지 확인 (신규 플레이어)
     * @param player 플레이어
     * @return 신규 플레이어 여부
     */
    fun isNewPlayer(player: Player): Boolean {
        return !hasPlayedForDays(player, 7)
    }
    
    /**
     * 플레이타임이 7일 미만인지 확인 (UUID 버전)
     * @param playerUuid 플레이어 UUID
     * @return 신규 플레이어 여부
     */
    fun isNewPlayer(playerUuid: UUID): Boolean {
        return !hasPlayedForDays(playerUuid, 7)
    }
    
    /**
     * 자동 저장 기능을 시작합니다
     */
    fun startAutoSave() {
        stopAutoSave() // 기존 태스크가 있으면 중단
        
        autoSaveTask = plugin.server.scheduler.runTaskTimer(plugin, Runnable {
            autoSavePlayTime()
        }, autoSaveIntervalSeconds * 20L, autoSaveIntervalSeconds * 20L) // 20틱 = 1초
        
        debugManager.log("PlayTime", "자동 저장 기능이 시작되었습니다. (${autoSaveIntervalSeconds}초 간격)")
    }
    
    /**
     * 자동 저장 기능을 중단합니다
     */
    fun stopAutoSave() {
        autoSaveTask?.cancel()
        autoSaveTask = null
        debugManager.log("PlayTime", "자동 저장 기능이 중단되었습니다.")
    }
    
    /**
     * 모든 온라인 플레이어들의 플레이타임을 자동 저장합니다 (세션은 유지)
     */
    private fun autoSavePlayTime() {
        if (sessionStartTimes.isEmpty()) {
            debugManager.log("PlayTime", "자동 저장 - 온라인 플레이어 없음")
            return
        }
        
        val currentTime = getCurrentTimeMillis()
        val sessionsToUpdate = sessionStartTimes.toMap() // 복사본 생성하여 동시 수정 방지
        val totalPlayers = sessionsToUpdate.size
        
        debugManager.log("PlayTime", "자동 저장 시작: ${totalPlayers}명의 플레이타임 저장 중...")
        
        // DB 작업을 비동기로 처리
        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            var successCount = 0
            var errorCount = 0
            
            try {
                sessionsToUpdate.forEach { (playerUuid, sessionStartTime) ->
                    try {
                        val sessionDurationSeconds = millisToSeconds(currentTime - sessionStartTime)
                        val previousPlayTime = playTimeData.getTotalPlayTime(playerUuid)
                        val newTotalPlayTime = previousPlayTime + sessionDurationSeconds
                        
                        // 플레이타임 업데이트하고 세션 시작 시간도 현재 시간으로 리셋
                        if (playTimeData.updatePlayTimeInfo(playerUuid, newTotalPlayTime, currentTime)) {
                            // 메인 스레드에서 세션 시작 시간 리셋 (플레이어가 여전히 온라인인 경우만)
                            plugin.server.scheduler.runTask(plugin, Runnable {
                                // 플레이어가 여전히 온라인이고 세션이 존재하는 경우만 업데이트
                                if (sessionStartTimes.containsKey(playerUuid) && plugin.server.getPlayer(playerUuid) != null) {
                                    sessionStartTimes[playerUuid] = currentTime
                                }
                            })
                            successCount++
                            
                            val playerName = plugin.server.getPlayer(playerUuid)?.name ?: "Unknown"
                            debugManager.log("PlayTime", "[AUTO-SAVE] $playerName: +${formatDuration(sessionDurationSeconds * 1000)} (총 ${formatDuration(newTotalPlayTime * 1000)})")
                        } else {
                            errorCount++
                            val playerName = plugin.server.getPlayer(playerUuid)?.name ?: "Unknown($playerUuid)"
                            plugin.logger.warning("[PlayTime] 자동 저장 실패: $playerName")
                        }
                    } catch (e: Exception) {
                        errorCount++
                        val playerName = plugin.server.getPlayer(playerUuid)?.name ?: "Unknown($playerUuid)"
                        plugin.logger.warning("[PlayTime] $playerName 자동 저장 중 오류: ${e.message}")
                    }
                }
                
                // 자동 저장 완료 로그
                val logMessage = "[ASYNC] 자동 저장 완료: 성공 ${successCount}명, 실패 ${errorCount}명 / 총 ${totalPlayers}명"
                if (errorCount > 0) {
                    plugin.logger.warning(logMessage)
                } else {
                    debugManager.log("PlayTime", logMessage)
                }
                
            } catch (e: Exception) {
                plugin.logger.severe("[PlayTime] 자동 저장 중 심각한 오류: ${e.message}")
                e.printStackTrace()
            }
        })
    }
    
    /**
     * 모든 온라인 플레이어들의 플레이타임을 저장합니다 (서버 종료 시 사용)
     */
    fun saveAllOnlinePlayersPlayTime() {
        debugManager.log("PlayTime", "모든 온라인 플레이어의 플레이타임을 저장합니다...")
        
        var savedCount = 0
        sessionStartTimes.forEach { (playerUuid, sessionStartTime) ->
            val currentTime = getCurrentTimeMillis()
            val sessionDurationSeconds = millisToSeconds(currentTime - sessionStartTime)
            
            val previousPlayTime = playTimeData.getTotalPlayTime(playerUuid)
            val newTotalPlayTime = previousPlayTime + sessionDurationSeconds
            
            if (playTimeData.updatePlayTimeInfo(playerUuid, newTotalPlayTime, null)) {
                savedCount++
            }
        }
        
        sessionStartTimes.clear()
        debugManager.log("PlayTime", "${savedCount}명의 플레이어 플레이타임을 저장했습니다.")
    }
    
    
    /**
     * 플레이타임 정보를 가져옵니다
     * @param playerUuid 플레이어 UUID
     * @return PlayTimeInfo 또는 null
     */
    fun getPlayTimeInfo(playerUuid: UUID): PlayTimeInfo? {
        return playTimeData.getPlayTimeInfo(playerUuid)
    }
    
    /**
     * 모든 플레이타임 정보를 가져옵니다 (관리자용)
     * @return 플레이타임 정보 리스트
     */
    fun getAllPlayTimeInfo(): List<PlayTimeInfo> {
        return playTimeData.getAllPlayTimeInfo()
    }
    
    /**
     * 상위 N명의 플레이타임 정보를 가져옵니다 (최적화된 쿼리 사용)
     * @param limit 조회할 상위 플레이어 수
     * @return 상위 플레이타임 정보 리스트
     */
    fun getTopPlayTimeInfo(limit: Int): List<PlayTimeInfo> {
        return playTimeData.getTopPlayTimeInfo(limit)
    }
    
    /**
     * 특정 플레이타임 이상인 플레이어 수를 조회합니다
     * @param requiredDays 필요한 일수
     * @return 플레이어 수
     */
    fun getPlayerCountAboveDays(requiredDays: Int): Int {
        val requiredSeconds = TimeUnit.DAYS.toSeconds(requiredDays.toLong())
        return playTimeData.getPlayerCountAbovePlayTime(requiredSeconds)
    }
    
    /**
     * 밀리초를 사용자 친화적인 형태로 포맷팅합니다
     * @param millis 밀리초
     * @return 포맷된 문자열
     */
    private fun formatDuration(millis: Long): String {
        val seconds = millis / 1000
        return formatPlayTime(seconds)
    }
    
    /**
     * 타임스탬프를 사용자 친화적인 형태로 포맷팅합니다
     * @param timestamp 타임스탬프 (밀리초)
     * @return 포맷된 문자열
     */
    private fun formatTimestamp(timestamp: Long): String {
        val date = java.util.Date(timestamp)
        val formatter = java.text.SimpleDateFormat("MM-dd HH:mm:ss")
        return formatter.format(date)
    }
    
    /**
     * 현재 시간을 밀리초로 반환합니다 (시간 단위 통일)
     * @return 현재 시간 (밀리초)
     */
    private fun getCurrentTimeMillis(): Long = System.currentTimeMillis()
    
    /**
     * 밀리초를 초로 변환합니다 (시간 단위 통일)
     * @param millis 밀리초
     * @return 초
     */
    private fun millisToSeconds(millis: Long): Long = millis / 1000
    
    /**
     * 초를 밀리초로 변환합니다 (시간 단위 통일)
     * @param seconds 초
     * @return 밀리초
     */
    private fun secondsToMillis(seconds: Long): Long = seconds * 1000
}