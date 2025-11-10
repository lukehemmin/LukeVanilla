package com.lukehemmin.lukeVanilla.System.PeperoEvent

import com.lukehemmin.lukeVanilla.Main
import com.lukehemmin.lukeVanilla.System.Database.Database
import com.lukehemmin.lukeVanilla.System.Discord.DiscordBot
import org.bukkit.command.CommandExecutor
import org.bukkit.event.Listener
import java.util.logging.Logger

/**
 * 빼빼로 이벤트 메인 시스템
 * 모든 빼빼로 이벤트 컴포넌트들을 통합하고 관리합니다.
 */
class PeperoEvent(
    private val plugin: Main,
    private val database: Database,
    private val discordBot: DiscordBot
) : Listener {

    private val logger: Logger = plugin.logger

    // 핵심 컴포넌트들
    private lateinit var repository: PeperoEventRepository
    private lateinit var webServer: PeperoEventWebServer
    private lateinit var gui: PeperoEventGUI
    private lateinit var listener: PeperoEventListener
    private lateinit var command: PeperoEventCommand
    private lateinit var scheduler: PeperoEventScheduler

    private var isEnabled = false
    private var isWebServerEnabled = false

    /**
     * 시스템 초기화 및 활성화
     */
    fun enable() {
        try {
            logger.info("[PeperoEvent] 빼빼로 이벤트 시스템을 초기화합니다...")

            // Repository 초기화
            repository = PeperoEventRepository(database)
            logger.info("[PeperoEvent] Repository 초기화 완료")

            // GUI 초기화
            gui = PeperoEventGUI(plugin, repository, logger)
            logger.info("[PeperoEvent] GUI 초기화 완료")

            // Listener 초기화
            listener = PeperoEventListener(plugin, repository, gui, logger)
            logger.info("[PeperoEvent] Listener 초기화 완료")

            // Command 초기화 및 등록
            command = PeperoEventCommand(plugin, repository, gui, logger)
            plugin.getCommand("빼빼로관리")?.setExecutor(command)
            plugin.getCommand("빼빼로관리")?.tabCompleter = command

            // /빼빼로받기 명령어 등록
            plugin.getCommand("빼빼로받기")?.setExecutor(CommandExecutor { sender, _, _, _ ->
                command.handlePeperoReceive(sender)
            })
            logger.info("[PeperoEvent] Command 초기화 완료")

            // Scheduler 초기화
            scheduler = PeperoEventScheduler(plugin, repository, database, discordBot, logger)
            logger.info("[PeperoEvent] Scheduler 초기화 완료")

            // 웹서버 초기화 (설정에 따라)
            if (plugin.config.getBoolean("pepero_event.enable_web_server", true)) {
                try {
                    webServer = PeperoEventWebServer(plugin, repository, logger)
                    webServer.start()
                    isWebServerEnabled = true
                    logger.info("[PeperoEvent] 웹서버 초기화 및 시작 완료")
                } catch (e: Exception) {
                    logger.severe("[PeperoEvent] 웹서버 초기화 실패: ${e.message}")
                    e.printStackTrace()
                }
            } else {
                logger.info("[PeperoEvent] 웹서버가 설정에서 비활성화되어 있습니다.")
            }

            isEnabled = true
            logger.info("[PeperoEvent] 빼빼로 이벤트 시스템 초기화 완료!")
            printSystemInfo()

        } catch (e: Exception) {
            logger.severe("[PeperoEvent] 시스템 초기화 중 오류 발생: ${e.message}")
            e.printStackTrace()
            disable()
            throw RuntimeException("PeperoEvent 초기화 실패", e)
        }
    }

    /**
     * 시스템 비활성화 및 정리
     */
    fun disable() {
        try {
            logger.info("[PeperoEvent] 빼빼로 이벤트 시스템을 종료합니다...")

            // 웹서버 중지
            if (::webServer.isInitialized && isWebServerEnabled) {
                webServer.stop()
                logger.info("[PeperoEvent] 웹서버 종료 완료")
            }

            isEnabled = false
            isWebServerEnabled = false
            logger.info("[PeperoEvent] 시스템 종료 완료")

        } catch (e: Exception) {
            logger.severe("[PeperoEvent] 시스템 종료 중 오류 발생: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * 시스템 정보 출력
     */
    private fun printSystemInfo() {
        logger.info("┌─────────────────────────────────────────────────┐")
        logger.info("│           🍫 빼빼로 이벤트 시스템 정보            │")
        logger.info("├─────────────────────────────────────────────────┤")
        logger.info("│ 상태: ${if (isEnabled) "✅ 활성화" else "❌ 비활성화"}                      │")
        logger.info("│ 웹서버: ${if (isWebServerEnabled) "✅ 실행 중" else "❌ 중지됨"}                  │")

        if (isWebServerEnabled && ::webServer.isInitialized) {
            val port = plugin.config.getInt("pepero_event.web_port", 9696)
            val host = plugin.config.getString("pepero_event.web_host", "0.0.0.0")
            val externalDomain = plugin.config.getString("pepero_event.external_domain", "peperoday2025.lukehemmin.com")
            val externalProtocol = plugin.config.getString("pepero_event.external_protocol", "https")
            logger.info("│ 내부 주소: $host:$port                           │")
            logger.info("│ 외부 주소: $externalProtocol://$externalDomain         │")
        }

        logger.info("│                                                 │")
        logger.info("│ 📅 이벤트 기간: 11월 9일 ~ 11월 11일               │")
        logger.info("│ 🎁 아이템 지급: 11월 11일                        │")
        logger.info("│ 💌 메시지 발송: 11월 12일 12시                    │")
        logger.info("├─────────────────────────────────────────────────┤")
        logger.info("│ 명령어:                                         │")
        logger.info("│  - /빼빼로받기 : 빼빼로 아이템 받기               │")
        logger.info("│  - /빼빼로관리 : 관리자 명령어                    │")
        logger.info("└─────────────────────────────────────────────────┘")
    }

    /**
     * 시스템 상태 확인
     */
    fun isEnabled(): Boolean = isEnabled
    fun isWebServerEnabled(): Boolean = isWebServerEnabled

    /**
     * 컴포넌트 접근자
     */
    fun getRepository(): PeperoEventRepository? = if (::repository.isInitialized) repository else null
    fun getWebServer(): PeperoEventWebServer? = if (::webServer.isInitialized) webServer else null
    fun getGUI(): PeperoEventGUI? = if (::gui.isInitialized) gui else null
}
