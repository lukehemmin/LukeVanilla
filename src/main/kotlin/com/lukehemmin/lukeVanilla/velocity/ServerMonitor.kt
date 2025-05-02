package com.lukehemmin.lukeVanilla.velocity

import com.velocitypowered.api.proxy.ProxyServer
import com.velocitypowered.api.scheduler.ScheduledTask
import org.slf4j.Logger
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
    private lateinit var serverMonitorTask: ScheduledTask
    private var isVanillaOnline: Boolean = false
    
    // vanilla 서버 상태 변경 콜백
    private var onVanillaOnlineCallback: Consumer<Boolean> = Consumer { }
    
    /**
     * 모니터링 시작
     */
    fun start(intervalSeconds: Long = 10) {
        val vanillaServerName = "vanilla" // vanilla 서버 이름
        
        serverMonitorTask = server.scheduler.buildTask(plugin) {
            try {
                // 서버 존재 여부 확인
                val vanillaServer = server.getServer(vanillaServerName)
                if (vanillaServer.isEmpty) {
                    logger.warn("Vanilla server is not registered in Velocity!")
                    updateStatus(false)
                    return@buildTask
                }
                
                // 서버 ping 시도
                val pingFuture = vanillaServer.get().ping()
                pingFuture.thenAccept { pingResult ->
                    updateStatus(true)
                }.exceptionally { throwable ->
                    val wasOnline = isVanillaOnline
                    updateStatus(false)
                    
                    if (wasOnline) {
                        logger.warn("Vanilla server is now OFFLINE! ${throwable.message}")
                    }
                    null
                }
            } catch (e: Exception) {
                logger.error("Error while monitoring server status", e)
                updateStatus(false)
            }
        }.repeat(intervalSeconds, TimeUnit.SECONDS).schedule()
    }
    
    /**
     * 모니터링 중지
     */
    fun stop() {
        if (::serverMonitorTask.isInitialized) {
            serverMonitorTask.cancel()
        }
    }
    
    /**
     * vanilla 서버 상태 업데이트 및 콜백 호출
     */
    private fun updateStatus(online: Boolean) {
        val previous = isVanillaOnline
        isVanillaOnline = online
        
        // 상태가 변경되었을 경우 콜백 실행
        if (previous != online) {
            if (online) {
                logger.info("Vanilla server is now ONLINE!")
            } else {
                logger.info("Vanilla server is now OFFLINE!")
            }
            onVanillaOnlineCallback.accept(online)
        }
    }
    
    /**
     * 현재 vanilla 서버 상태 반환
     */
    fun isVanillaOnline(): Boolean {
        return isVanillaOnline
    }
    
    /**
     * vanilla 서버 상태 변경시 실행할 콜백 설정
     */
    fun onVanillaStatusChange(callback: Consumer<Boolean>) {
        onVanillaOnlineCallback = callback
    }
}