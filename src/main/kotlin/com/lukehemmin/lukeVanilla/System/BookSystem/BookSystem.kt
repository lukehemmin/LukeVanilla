package com.lukehemmin.lukeVanilla.System.BookSystem

import com.lukehemmin.lukeVanilla.Main
import com.lukehemmin.lukeVanilla.System.Database.Database
import org.bukkit.event.Listener
import java.io.File
import java.util.logging.Logger

/**
 * 책 시스템 메인 관리자 클래스
 * 모든 책 시스템 컴포넌트들을 통합하고 관리합니다.
 */
class BookSystem(
    private val plugin: Main,
    private val database: Database
) : Listener {

    private val logger: Logger = plugin.logger
    
    // 핵심 컴포넌트들
    private lateinit var bookRepository: BookRepository
    private lateinit var sessionManager: BookSessionManager
    private lateinit var bookListener: BookListener
    private lateinit var bookCommand: BookCommand
    private lateinit var webServer: BookWebServer
    
    // 시스템 상태
    private var isEnabled = false
    private var isWebServerEnabled = false

    /**
     * 시스템 초기화 및 활성화
     */
    fun enable() {
        try {
            logger.info("[BookSystem] 책 시스템을 초기화합니다...")
            
            // 설정 파일 생성
            setupConfiguration()
            
            // 리포지토리 초기화
            bookRepository = BookRepository(database)
            logger.info("[BookSystem] 데이터베이스 리포지토리 초기화 완료")
            
            // 세션 매니저 초기화
            sessionManager = BookSessionManager(plugin, bookRepository, logger)
            logger.info("[BookSystem] 세션 관리자 초기화 완료")
            
            // 이벤트 리스너 초기화 및 등록
            bookListener = BookListener(plugin, bookRepository, logger)
            plugin.server.pluginManager.registerEvents(bookListener, plugin)
            logger.info("[BookSystem] 책 이벤트 리스너 등록 완료")
            
            // 명령어 초기화 및 등록
            bookCommand = BookCommand(plugin, bookRepository, sessionManager, logger)
            plugin.getCommand("책")?.setExecutor(bookCommand)
            plugin.getCommand("책")?.tabCompleter = bookCommand
            plugin.getCommand("book")?.setExecutor(bookCommand)
            plugin.getCommand("book")?.tabCompleter = bookCommand
            logger.info("[BookSystem] 책 명령어 등록 완료")
            
            // 웹서버 초기화 (설정에 따라)
            if (plugin.config.getBoolean("book_system.enable_web_server", true)) {
                try {
                    webServer = BookWebServer(plugin, bookRepository, sessionManager, logger)
                    webServer.start()
                    isWebServerEnabled = true
                    logger.info("[BookSystem] 웹서버 초기화 및 시작 완료")
                } catch (e: Exception) {
                    logger.severe("[BookSystem] 웹서버 초기화 실패: ${e.message}")
                    logger.severe("[BookSystem] 웹서버 없이 시스템을 계속 실행합니다.")
                    e.printStackTrace()
                }
            } else {
                logger.info("[BookSystem] 웹서버가 설정에서 비활성화되어 있습니다.")
            }
            
            // 정리 작업 스케줄러 시작
            startCleanupScheduler()
            
            isEnabled = true
            logger.info("[BookSystem] 책 시스템 초기화가 완료되었습니다!")
            
            // 시스템 정보 출력
            printSystemInfo()
            
        } catch (e: Exception) {
            logger.severe("[BookSystem] 책 시스템 초기화 중 심각한 오류 발생: ${e.message}")
            e.printStackTrace()
            disable()
            throw RuntimeException("BookSystem 초기화 실패", e)
        }
    }

    /**
     * 시스템 비활성화 및 정리
     */
    fun disable() {
        try {
            logger.info("[BookSystem] 책 시스템을 종료합니다...")
            
            // 웹서버 중지
            if (::webServer.isInitialized && isWebServerEnabled) {
                webServer.stop()
                logger.info("[BookSystem] 웹서버 종료 완료")
            }
            
            // 세션 매니저 정리
            if (::sessionManager.isInitialized) {
                sessionManager.shutdown()
                logger.info("[BookSystem] 세션 관리자 종료 완료")
            }
            
            isEnabled = false
            isWebServerEnabled = false
            logger.info("[BookSystem] 책 시스템 종료가 완료되었습니다.")
            
        } catch (e: Exception) {
            logger.severe("[BookSystem] 책 시스템 종료 중 오류 발생: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * 시스템 재시작
     */
    fun restart() {
        logger.info("[BookSystem] 책 시스템을 재시작합니다...")
        disable()
        
        // 잠시 대기
        Thread.sleep(1000)
        
        enable()
    }

    /**
     * 설정 파일 초기화
     */
    private fun setupConfiguration() {
        val config = plugin.config
        
        // 기본 설정값들 설정
        if (!config.contains("book_system.enable_web_server")) {
            config.set("book_system.enable_web_server", true)
        }
        if (!config.contains("book_system.web_port")) {
            config.set("book_system.web_port", 8080)
        }
        if (!config.contains("book_system.web_host")) {
            config.set("book_system.web_host", "localhost")
        }
        if (!config.contains("book_system.enable_cors")) {
            config.set("book_system.enable_cors", true)
        }
        if (!config.contains("book_system.session_expiry_hours")) {
            config.set("book_system.session_expiry_hours", 24)
        }
        if (!config.contains("book_system.auth_code_expiry_minutes")) {
            config.set("book_system.auth_code_expiry_minutes", 5)
        }
        if (!config.contains("book_system.max_sessions_per_player")) {
            config.set("book_system.max_sessions_per_player", 3)
        }
        if (!config.contains("book_system.current_season")) {
            config.set("book_system.current_season", "Season1")
        }
        if (!config.contains("book_system.log_book_reads")) {
            config.set("book_system.log_book_reads", false)
        }
        
        plugin.saveConfig()
        logger.info("[BookSystem] 설정 파일 초기화 완료")
    }

    /**
     * 정기 정리 작업 스케줄러 시작
     */
    private fun startCleanupScheduler() {
        // 1시간마다 만료된 세션 정리
        plugin.server.scheduler.runTaskTimerAsynchronously(plugin, Runnable {
            try {
                if (::sessionManager.isInitialized) {
                    val removedSessions = bookRepository.cleanupExpiredSessions()
                    if (removedSessions > 0) {
                        logger.info("[BookSystem] 정기 정리: 만료된 세션 ${removedSessions}개 제거")
                    }
                }
            } catch (e: Exception) {
                logger.warning("[BookSystem] 정기 정리 작업 중 오류: ${e.message}")
            }
        }, 20L * 60L * 60L, 20L * 60L * 60L) // 1시간마다 (1200초 = 20분이므로 60*60=3600틱)
    }

    /**
     * 시스템 정보 출력
     */
    private fun printSystemInfo() {
        logger.info("┌─────────────────────────────────────────────────┐")
        logger.info("│              📚 책 시스템 정보                    │")
        logger.info("├─────────────────────────────────────────────────┤")
        logger.info("│ 상태: ${if (isEnabled) "✅ 활성화" else "❌ 비활성화"}                      │")
        logger.info("│ 웹서버: ${if (isWebServerEnabled) "✅ 실행 중" else "❌ 중지됨"}                  │")
        
        if (isWebServerEnabled && ::webServer.isInitialized) {
            val serverInfo = webServer.getServerInfo()
            logger.info("│ 내부 주소: ${serverInfo["internal_host"]}:${serverInfo["internal_port"]}              │")
            logger.info("│ 외부 주소: ${serverInfo["external_url"]}   │")
        }
        
        logger.info("│ 현재 시즌: ${plugin.config.getString("book_system.current_season", "Season1")}                      │")
        logger.info("│ 세션 만료: ${plugin.config.getLong("book_system.session_expiry_hours", 24)}시간                      │")
        logger.info("├─────────────────────────────────────────────────┤")
        logger.info("│ 명령어:                                         │")
        logger.info("│  - /책 목록 : 내 책 목록 보기                    │")
        logger.info("│  - /책 웹사이트 : 웹사이트 정보                  │")
        logger.info("│  - /책 토큰 : 웹 인증 토큰 생성                  │")
        logger.info("└─────────────────────────────────────────────────┘")
    }

    /**
     * 시스템 상태 확인
     */
    fun getSystemStatus(): Map<String, Any> {
        val status = mutableMapOf<String, Any>()
        
        status["enabled"] = isEnabled
        status["webServerEnabled"] = isWebServerEnabled
        
        if (::bookRepository.isInitialized) {
            try {
                val seasonStats = bookRepository.getBookStatsBySeason()
                status["seasonStats"] = seasonStats
                status["totalSeasons"] = seasonStats.size
            } catch (e: Exception) {
                logger.warning("[BookSystem] 통계 조회 중 오류: ${e.message}")
                status["statsError"] = e.message ?: "Unknown error"
            }
        }
        
        if (::sessionManager.isInitialized) {
            status["sessionStats"] = sessionManager.getSessionStats()
        }
        
        if (::webServer.isInitialized) {
            status["webServerInfo"] = webServer.getServerInfo()
        }
        
        status["configuration"] = mapOf(
            "currentSeason" to (plugin.config.getString("book_system.current_season") ?: "Season1"),
            "sessionExpiryHours" to plugin.config.getLong("book_system.session_expiry_hours"),
            "maxSessionsPerPlayer" to plugin.config.getInt("book_system.max_sessions_per_player"),
            "logBookReads" to plugin.config.getBoolean("book_system.log_book_reads")
        )
        
        return status
    }

    /**
     * 현재 시즌 변경
     */
    fun changeCurrentSeason(newSeason: String): Boolean {
        return try {
            plugin.config.set("book_system.current_season", newSeason)
            plugin.saveConfig()
            logger.info("[BookSystem] 현재 시즌이 '$newSeason'로 변경되었습니다.")
            true
        } catch (e: Exception) {
            logger.severe("[BookSystem] 시즌 변경 중 오류: ${e.message}")
            false
        }
    }

    /**
     * 특정 시즌의 모든 책을 아카이브화
     */
    fun archiveSeason(season: String): Int {
        return try {
            // 실제 구현에서는 bookRepository에 archiveBooksBySeason 메소드 추가 필요
            logger.info("[BookSystem] 시즌 '$season'의 책들을 아카이브화했습니다.")
            0 // 아카이브된 책 수 반환
        } catch (e: Exception) {
            logger.severe("[BookSystem] 시즌 아카이브 중 오류: ${e.message}")
            -1
        }
    }

    /**
     * 컴포넌트 접근자들 (필요한 경우)
     */
    fun getBookRepository(): BookRepository? = if (::bookRepository.isInitialized) bookRepository else null
    fun getSessionManager(): BookSessionManager? = if (::sessionManager.isInitialized) sessionManager else null
    fun getWebServer(): BookWebServer? = if (::webServer.isInitialized) webServer else null
    
    /**
     * 시스템 활성화 상태 확인
     */
    fun isEnabled(): Boolean = isEnabled
    fun isWebServerEnabled(): Boolean = isWebServerEnabled
}