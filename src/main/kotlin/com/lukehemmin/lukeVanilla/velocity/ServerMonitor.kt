package com.lukehemmin.lukeVanilla.velocity

import com.velocitypowered.api.proxy.ProxyServer
import com.velocitypowered.api.proxy.server.RegisteredServer
import com.velocitypowered.api.scheduler.ScheduledTask
import org.slf4j.Logger
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.function.Consumer

/**
 * Velocity 서버에서 백엔드 서버 상태를 모니터링하는 클래스
 */
class ServerMonitor(
    private val server: ProxyServer,
    private val logger: Logger,
    private val plugin: Any
) {
    private val serverMonitorTasks = ConcurrentHashMap<String, ScheduledTask>()
    private val serverStatus = ConcurrentHashMap<String, Boolean>() // 서버 이름, 온라인 여부
    private var onServerStatusChangeCallback: Consumer<Pair<RegisteredServer, Boolean>> = Consumer { } // 서버 정보, 온라인 여부

    // 모니터링할 서버 목록 (우선은 vanilla만)
    private val monitoredServers = listOf("vanilla")

    /**
     * 모니터링 시작
     */
    fun start(intervalSeconds: Long = 10) {
        monitoredServers.forEach { serverName ->
            val task = server.scheduler.buildTask(plugin) {
                try {
                    val registeredServerOptional = server.getServer(serverName)
                    if (registeredServerOptional.isEmpty) {
                        logger.warn("$serverName server is not registered in Velocity!")
                        updateStatus(serverName, false, null)
                        return@buildTask
                    }
                    val registeredServer = registeredServerOptional.get()

                    registeredServer.ping().thenAccept { _ ->
                        updateStatus(serverName, true, registeredServer)
                    }.exceptionally { throwable ->
                        val wasOnline = serverStatus[serverName] ?: false
                        updateStatus(serverName, false, registeredServer)

                        if (wasOnline) {
                            logger.warn("$serverName server is now OFFLINE! ${throwable.message}")
                        }
                        null
                    }
                } catch (e: Exception) {
                    logger.error("Error while monitoring $serverName server status", e)
                    val rs = server.getServer(serverName).orElse(null)
                    updateStatus(serverName, false, rs)
                }
            }.repeat(intervalSeconds, TimeUnit.SECONDS).schedule()
            serverMonitorTasks[serverName] = task
            logger.info("Started monitoring for $serverName server.")
        }
    }

    /**
     * 모니터링 중지
     */
    fun stop() {
        serverMonitorTasks.values.forEach { it.cancel() }
        serverMonitorTasks.clear()
        logger.info("Stopped all server monitoring tasks.")
    }

    /**
     * 특정 서버 상태 업데이트 및 콜백 호출
     */
    private fun updateStatus(serverName: String, online: Boolean, registeredServer: RegisteredServer?) {
        val previousStatus = serverStatus.getOrDefault(serverName, !online) // 이전 상태가 없으면 현재와 반대로 설정하여 변경 감지
        serverStatus[serverName] = online

        if (previousStatus != online) {
            if (online) {
                logger.info("$serverName server is now ONLINE!")
            } else {
                logger.info("$serverName server is now OFFLINE!")
            }
            // RegisteredServer가 null이 아닐 때만 콜백 호출 (서버가 아예 존재하지 않는 경우는 제외)
            registeredServer?.let {
                onServerStatusChangeCallback.accept(Pair(it, online))
            }
        }
    }

    /**
     * 현재 특정 서버 상태 반환
     */
    fun isServerOnline(serverName: String): Boolean {
        return serverStatus.getOrDefault(serverName, false)
    }
    
    /**
     * 모든 모니터링 대상 서버의 현재 상태 반환
     */
    fun getAllServerStatuses(): Map<String, Boolean> {
        return serverStatus.toMap()
    }

    /**
     * 서버 상태 변경시 실행할 콜백 설정
     */
    fun onServerStatusChange(callback: Consumer<Pair<RegisteredServer, Boolean>>) {
        onServerStatusChangeCallback = callback
    }
}