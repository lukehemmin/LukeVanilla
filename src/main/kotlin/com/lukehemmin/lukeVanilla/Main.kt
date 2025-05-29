package com.lukehemmin.lukeVanilla

import com.lukehemmin.lukeVanilla.Lobby.SnowMinigame
import com.lukehemmin.lukeVanilla.Lobby.SnowGameCommand
import com.lukehemmin.lukeVanilla.System.AntiVPN
import com.lukehemmin.lukeVanilla.System.Command.*
import com.lukehemmin.lukeVanilla.System.Database.Database
import com.lukehemmin.lukeVanilla.System.Database.DatabaseInitializer
import com.lukehemmin.lukeVanilla.System.Discord.*
import com.lukehemmin.lukeVanilla.System.Economy.EconomyManager
import com.lukehemmin.lukeVanilla.System.Economy.MoneyCommand
// 할로윈 임포트
import com.lukehemmin.lukeVanilla.System.Items.Halloween.hscroll
import com.lukehemmin.lukeVanilla.System.Items.*
import com.lukehemmin.lukeVanilla.System.Items.UpgradeItem
// import com.lukehemmin.lukeVanilla.System.Items.CustomItemSystem.*
import com.lukehemmin.lukeVanilla.System.Items.ItemSeasonSystem.*
import com.lukehemmin.lukeVanilla.System.Items.ItemSeasonSystem.ItemCommand
import com.lukehemmin.lukeVanilla.System.NPC.NPCSitPreventer
import com.lukehemmin.lukeVanilla.System.ChatSystem.*
import com.lukehemmin.lukeVanilla.System.Items.CustomItemSystem.*
import com.lukehemmin.lukeVanilla.System.LockSystem.LockSystem
import com.lukehemmin.lukeVanilla.System.NexoCraftingRestriction
import com.lukehemmin.lukeVanilla.System.NoExplosionListener
import com.lukehemmin.lukeVanilla.System.Player_Join_And_Quit_Message_Listener
import com.lukehemmin.lukeVanilla.System.Items.StatsSystem.StatsSystem
import com.lukehemmin.lukeVanilla.System.Items.StatsSystem.ItemStatsCommand
import com.lukehemmin.lukeVanilla.System.VanillaShutdownNotifier
import com.lukehemmin.lukeVanilla.System.WarningSystem.WarningCommand
import com.lukehemmin.lukeVanilla.System.WarningSystem.WarningService
import org.bukkit.plugin.java.JavaPlugin
import java.util.concurrent.TimeUnit
import java.sql.Connection 
import java.sql.DriverManager 

class Main : JavaPlugin() {
    lateinit var database: Database
    private lateinit var serviceType: String
    private lateinit var nametagManager: NametagManager
    private lateinit var discordRoleManager: DiscordRoleManager
    lateinit var discordBot: DiscordBot 
    private lateinit var itemRestoreLogger: ItemRestoreLogger
    lateinit var nextSeasonGUI: NextSeasonItemGUI
    private val nexoCraftingRestriction = NexoCraftingRestriction(this)
    lateinit var economyManager: EconomyManager
    lateinit var lockSystem: LockSystem
    lateinit var statsSystem: StatsSystem
    lateinit var snowMinigame: SnowMinigame
//    lateinit var shopManager: ShopManager
//    lateinit var shopPriceListener: ShopPriceListener
//    lateinit var shopManager: ShopManager
//    lateinit var priceEditManager: PriceEditManager 

    // AdminAssistant에 데이터베이스 연결을 제공하는 함수
    // 주의: 이 함수는 호출될 때마다 새로운 DB 연결을 생성합니다.
    // 실제 운영 환경에서는 커넥션 풀 사용을 고려해야 합니다.
    private fun provideDbConnection(): Connection {
        val host = config.getString("database.host", "localhost")
        val port = config.getInt("database.port", 3306)
        val dbName = config.getString("database.name", "lukevanilla")
        val user = config.getString("database.user", "root")
        val password = config.getString("database.password", "")
        // MySQL JDBC URL 예시입니다. 다른 DB 사용 시 수정 필요.
        // JDBC 드라이버가 클래스패스에 있는지 확인하세요 (예: build.gradle에 mysql-connector-java 의존성 추가).
        val jdbcUrl = "jdbc:mysql://$host:$port/$dbName?autoReconnect=true&useSSL=false&allowPublicKeyRetrieval=true"

        // 드라이버 로딩 (최초 한 번만 필요, Database 클래스에서 이미 처리했을 수 있음)
        // try { Class.forName("com.mysql.cj.jdbc.Driver") } catch (e: ClassNotFoundException) { logger.severe("MySQL JDBC 드라이버를 찾을 수 없습니다!"); throw RuntimeException(e) }
        return DriverManager.getConnection(jdbcUrl, user, password)
    }

    /**
     * 현재 실행 중인 서버를 보다 쉬운 방법으로 식별하기 위한 함수
     */
    fun getServerType(): String {
        return serviceType
    }
    
    /**
     * 서버 간 통신을 위한 플러그인 메시지 채널 등록
     */
    private fun registerPluginMessageChannels() {
        // 서버 통신을 위한 채널 등록
        server.messenger.registerOutgoingPluginChannel(this, CHANNEL_SERVER_STATUS_REQUEST)
        server.messenger.registerOutgoingPluginChannel(this, CHANNEL_SERVER_STATUS_RESPONSE)
        
        try {
            // 서버 타입에 따라 리스너 등록
            if (serviceType == "Lobby") {
                // 로비 서버: 야생 서버에 상태 요청 & 응답 처리
                val requester = com.lukehemmin.lukeVanilla.System.Discord.ServerStatusRequester.getInstance(this)
                server.messenger.registerIncomingPluginChannel(this, CHANNEL_SERVER_STATUS_RESPONSE, requester)
                logger.info("[서버 통신] 로비 서버 모드로 메시지 채널 초기화")
            } else {
                // 야생 서버: 로비 서버의 요청에 응답
                val listener = com.lukehemmin.lukeVanilla.System.Discord.ServerStatusListener.getInstance(this)
                server.messenger.registerIncomingPluginChannel(this, CHANNEL_SERVER_STATUS_REQUEST, listener)
                logger.info("[서버 통신] 야생 서버 모드로 메시지 채널 초기화")
            }
        } catch (e: Exception) {
            logger.warning("[서버 통신] 리스너 등록 오류: ${e.message}")
            e.printStackTrace()
        }
    }

    companion object {
        @JvmStatic
        fun getPlugin(): Main {
            return getPlugin(Main::class.java)
        }
        
        // 서버 간 통신을 위한 채널 상수
        const val CHANNEL_SERVER_STATUS_REQUEST = "lukevanilla:serverstatus_request"
        const val CHANNEL_SERVER_STATUS_RESPONSE = "lukevanilla:serverstatus_response"
    }
    
    override fun onEnable() {
        // DataBase Logic
        saveDefaultConfig()
        database = Database(this, config)
        val dbInitializer = DatabaseInitializer(database)
        dbInitializer.createTables()

        // Read service type from config
        serviceType = config.getString("service.type") ?: "Vanilla"
        
        // 서버 간 메시지 채널 등록 (서버 상태 요청/응답)
        try {
            registerPluginMessageChannels()
            logger.info("[서버 통신] 플러그인 메시지 채널 등록 완료")
        } catch (e: Exception) {
            logger.warning("[서버 통신] 플러그인 메시지 채널 등록 중 오류: ${e.message}")
            e.printStackTrace()
        }

        // Discord Bot 초기화
        val discordToken = database.getSettingValue("DiscordToken")
        val openAiApiKey = database.getSettingValue("OpenAI_API_Token") // 키 이름 AdminAssistant.kt와 일치

        if (discordToken != null) {
            discordBot = DiscordBot()
            discordBot.start(discordToken)

            // DiscordRoleManager 초기화
            discordRoleManager = DiscordRoleManager(database, discordBot.jda)

            // DiscordVoiceChannelListener 초기화 및 리스너 등록
            discordBot.jda.addEventListener(DiscordVoiceChannelListener(this))

            // ItemRestoreLogger 초기화
            itemRestoreLogger = ItemRestoreLogger(database, this, discordBot.jda)

            // DiscordLeave 초기화 및 리스너 등록 (모든 서버 공통)
            val discordLeave = DiscordLeave(database, this, discordBot.jda)
            discordBot.jda.addEventListener(discordLeave)
            server.pluginManager.registerEvents(discordLeave, this)
            
            // 티토커 채팅 리스너 등록 (모든 서버 공통)
            discordBot.jda.addEventListener(TitokerChatListener(this))
            
            // 서비스 타입이 "Lobby"가 아닐 경우에만 인증코드 처리 시스템 초기화
            if (serviceType != "Lobby") {
                // DiscordAuth 초기화 및 리스너 등록 (인증코드 처리만 로비에서 비활성화)
                val discordAuth = DiscordAuth(database, this)
                discordBot.jda.addEventListener(discordAuth)
            }

            if (serviceType == "Lobby") {
                // 서비스 타입이 "Lobby"인 경우에만 관리자 어시스턴트 초기화
                if (openAiApiKey != null) {
                    // WarningService 인스턴스 생성
                    val warningService = WarningService(database, discordBot.jda)
                    
                    val adminAssistant = AdminAssistant(
                        dbConnectionProvider = ::provideDbConnection,
                        openAIApiKey = openAiApiKey, // API 키를 생성자에 전달
                        database = database,
                        warningService = warningService
                    )
                    discordBot.jda.addEventListener(adminAssistant)
                    logger.info("[AdminAssistant] 로비 서버에서 관리자 어시스턴트 초기화 완료.")
                } else {
                    logger.warning("[AdminAssistant] OpenAI API 키를 찾을 수 없어 관리자 어시스턴트를 초기화할 수 없습니다. 데이터베이스 'Settings' 테이블에서 'OpenAI_ApiKey' 값을 확인해주세요.")
                }
            }
        } else {
            logger.warning("데이터베이스에서 Discord 토큰을 찾을 수 없습니다. Discord 봇 관련 기능이 제한됩니다.")
            if (openAiApiKey == null) { 
                logger.warning("[AdminAssistant] OpenAI API 키도 찾을 수 없습니다. 관리자 어시스턴트 기능이 비활성화됩니다.")
            }
        }

        // Discord Bot 초기화 부분 아래에 추가
        // 고객지원 시스템은 로비 서버에서만 실행
        if (serviceType == "Lobby") {
            logger.info("로비 서버에서 고객지원 시스템을 초기화합니다.")
            discordBot.jda.addEventListener(
                SupportSystem(discordBot, database),
                SupportCaseListener(database, discordBot)
            )
            // 고객지원 채널 설정 초기화 (로비 서버에서만 수행)
            val supportSystem = SupportSystem(discordBot, database)
            supportSystem.setupSupportChannel()
        } else {
            logger.info("${serviceType} 서버에서는 고객지원 시스템이 비활성화됩니다.")
        }

        // 로비 서버일 때만 눈싸움 미니게임 초기화
        if (serviceType == "Lobby") {
            // 눈싸움 미니게임 초기화 및 인스턴스 저장
            snowMinigame = SnowMinigame(this)
            // 눈싸움 관리 명령어 등록
            getCommand("snowgame")?.setExecutor(SnowGameCommand(snowMinigame))
            logger.info("눈싸움 미니게임이 초기화되었습니다.")
        }

        // 이벤트 리스너 등록
        server.pluginManager.registerEvents(PlayerLoginListener(database), this)
        server.pluginManager.registerEvents(Player_Join_And_Quit_Message_Listener(serviceType, this, database), this)
        server.pluginManager.registerEvents(PlayerJoinListener(this, database, discordRoleManager), this) 

        // Player_Join_And_Quit_Message 갱신 스케줄러
        server.scheduler.runTaskTimer(this, Runnable {
            Player_Join_And_Quit_Message_Listener.updateMessages(database)
        }, 0L, 1200L) 

        // Nametag System
        nametagManager = NametagManager(this, database)
        val nametagCommand = NametagCommand(database, nametagManager)
        getCommand("nametag")?.setExecutor(nametagCommand)
        getCommand("delnametag")?.setExecutor(nametagCommand)

        // 아이템 시스템 초기화
        // 아래에서 생성할 itemReceiveSystem 인스턴스를 사용하므로 여기서는 생성하지 않음
        // 영어 명령어 등록은 아래에서 통합
        server.pluginManager.registerEvents(DurabilityListener(this), this)
        server.pluginManager.registerEvents(EnchantmentLimitListener(), this)
        
        // 통합 이벤트 아이템 시스템 초기화
//        val eventItemCommand = EventItemCommand(this)
        // 아이템 명령어 등록 (ItemSeasonSystem)
//        getCommand("아이템")?.setExecutor(eventItemCommand) 
//        getCommand("아이템")?.tabCompleter = EventItemCommandCompleter()

        // 아이템 GUI 리스너 등록 (CustomItemSystem)
        // server.pluginManager.registerEvents(EventItemGUIListener(this, eventItemCommand.getEventItemSystem()), this)
        // server.pluginManager.registerEvents(eventItemCommand.getEventItemSystem(), this)
        
        // 기존 할로윈 코드 주석 처리
        // server.pluginManager.registerEvents(Halloween_Item(), this)
        // server.pluginManager.registerEvents(HalloweenGUIListener(this), this)
        // getCommand("할로윈")?.setExecutor(HalloweenCommand(this))
        // getCommand("할로윈")?.tabCompleter = HalloweenCommandCompleter()
        
        server.pluginManager.registerEvents(TransparentFrame(), this)
        server.pluginManager.registerEvents(OraxenItem_Placecancel(), this)
        server.pluginManager.registerEvents(hscroll(), this)

        // Command System
        getCommand("infomessage")?.setExecutor(infomessage())
        getCommand("wleh")?.setExecutor(mapcommand())
        getCommand("지도")?.setExecutor(mapcommand())
        getCommand("투명액자")?.setExecutor(TransparentFrameCommand())

        getCommand("pl")?.setExecutor(plcommandcancel())
        getCommand("plugins")?.setExecutor(plcommandcancel())
        getCommand("lukeplugininfo")?.setExecutor(plcommandcancel())

        // ItemRestoreCommand 초기화 부분 수정
        val itemRestoreCommand = ItemRestoreCommand(itemRestoreLogger)
        getCommand("아이템복구")?.setExecutor(itemRestoreCommand)
        server.pluginManager.registerEvents(itemRestoreCommand, this)

        // 티토커 메시지 명령어 등록
        getCommand("티토커메시지")?.setExecutor(TitokerMessageCommand(this))
        getCommand("티토커메시지")?.tabCompleter = TitokerCommandCompleter()

        // RefreshMessagesCommand 등록
        getCommand("refreshmessages")?.setExecutor(RefreshMessagesCommand(this))

        // NoExplosionListener 초기화 및 등록
        val listener = NoExplosionListener(this)
        server.pluginManager.registerEvents(listener, this)

        // NextSeasonItemGUI 부분 다음 시즌 가져갈 아이템
        nextSeasonGUI = NextSeasonItemGUI(this, database)
        getCommand("openNextSeasonGUI")?.setExecutor(nextSeasonGUI)

        // 돈 이코노미 시스템
        economyManager = EconomyManager(database)
        getCommand("돈")?.setExecutor(MoneyCommand(economyManager))
        getCommand("ehs")?.setExecutor(MoneyCommand(economyManager))

        // Reload 명령어 등록
        getCommand("lukereload")?.setExecutor(ReloadCommand(this))

        server.pluginManager.registerEvents(LevelStick(), this)
        server.pluginManager.registerEvents(Scroll(), this)

        // 관리자 채팅 시스템 등록
        val adminChatManager = AdminChatManager(this)
        getCommand("관리자채팅")?.setExecutor(adminChatManager)
        AdminChatSync(this)

//        getCommand("발렌타인방패받기")?.setExecutor(ValentineShieldCommand(this))

        // GlobalChatManager 초기화
        //GlobalChatManager(this, database)

//        // NexoCraftingRestriction 초기화
//        server.pluginManager.registerEvents(nexoCraftingRestriction, this)
//        getCommand("craftallow")?.setExecutor(CraftAllowCommand(nexoCraftingRestriction))

        // VPN 방지 실행
        server.pluginManager.registerEvents(AntiVPN(this), this)

        // LockSystem 활성화
//        lockSystem = LockSystem(this)
//        lockSystem.enable()

        // StatsSystem 초기화
        statsSystem = StatsSystem(this)
        
        // 아이템 통계 명령어 등록
        getCommand("아이템정보")?.setExecutor(ItemStatsCommand(this))

        // 아이템 업그레이드 시스템 초기화
        UpgradeItem(this)
        
        // 아이템 시즌 시스템 설정
        // ItemReceiveSystem 인스턴스 생성 및 이벤트 리스너로 등록
        val itemReceiveSystem = ItemReceiveSystem()
        itemReceiveSystem.plugin = this 
        itemReceiveSystem.database = database 
        server.pluginManager.registerEvents(itemReceiveSystem, this) 
        
        // 경고 시스템 초기화
        val warningCommand = WarningCommand(database, discordBot.jda)
        getCommand("경고")?.setExecutor(warningCommand)
        getCommand("경고")?.tabCompleter = warningCommand
        getCommand("warn")?.setExecutor(warningCommand)
        getCommand("warn")?.tabCompleter = warningCommand
        
        // ItemCommand에 단일 ItemReceiveSystem 인스턴스 전달
        val itemSeasonSystemCommand = ItemCommand(itemReceiveSystem)
        getCommand("아이템")?.setExecutor(itemSeasonSystemCommand) 
        getCommand("아이템")?.tabCompleter = itemSeasonSystemCommand 
        getCommand("item")?.setExecutor(itemSeasonSystemCommand) 
        getCommand("item")?.tabCompleter = itemSeasonSystemCommand 
        
        // Christmas_sword 이벤트 리스너 등록 (UpgradeItem으로 통합)
        // Christmas_sword(this)

        // Plugin Logic
        logger.info("Plugin enabled")

        // NPCSitPreventer 등록
        server.pluginManager.registerEvents(NPCSitPreventer(this), this)

        // 플러그인 메시지 채널 등록
        VanillaShutdownNotifier.registerChannel(this)
    }

    // 이름을 다르게 하여 충돌 방지
    fun getLockSystemInstance(): LockSystem {
        return lockSystem
    }

    override fun onDisable() {
        // 서버 종료 직전 프록시에 오프라인 임박 메시지 전송
        VanillaShutdownNotifier.notifyShutdownImminent(this)
        try {
            // Discord 봇 종료를 먼저 실행
            if (::discordBot.isInitialized) {
                // 모든 리스너 제거
                discordBot.jda.registeredListeners.forEach {
                    discordBot.jda.removeEventListener(it)
                }

                // JDA 인스턴스 종료를 기다림
                discordBot.jda.shutdown()
                // 최대 5초 동안 종료 대기
                if (!discordBot.jda.awaitShutdown(5, TimeUnit.SECONDS)) {
                    logger.warning("Discord 봇이 정상적으로 종료되지 않았습니다.")
                }
            }
        } catch (e: Exception) {
            logger.severe("Discord 봇 종료 중 오류 발생: ${e.message}")
        } finally {
            // 데이터베이스 종료
            if (::database.isInitialized) {
                database.close()
            }

            logger.info("Plugin disabled")
        }
    }
}
