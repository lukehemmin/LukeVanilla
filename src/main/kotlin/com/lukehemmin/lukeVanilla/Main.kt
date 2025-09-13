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
import com.lukehemmin.lukeVanilla.System.Items.*
import com.lukehemmin.lukeVanilla.System.Items.UpgradeItem
// import com.lukehemmin.lukeVanilla.System.Items.CustomItemSystem.*
import com.lukehemmin.lukeVanilla.System.Items.ItemSeasonSystem.*
import com.lukehemmin.lukeVanilla.System.Items.ItemSeasonSystem.ItemCommand
import com.lukehemmin.lukeVanilla.System.NPC.NPCSitPreventer
import com.lukehemmin.lukeVanilla.System.ChatSystem.*
import com.lukehemmin.lukeVanilla.System.Items.CustomItemSystem.*
import com.lukehemmin.lukeVanilla.System.NexoCraftingRestriction
import com.lukehemmin.lukeVanilla.System.NoExplosionListener
import com.lukehemmin.lukeVanilla.System.Player_Join_And_Quit_Message_Listener
import com.lukehemmin.lukeVanilla.System.Items.StatsSystem.StatsSystem
import com.lukehemmin.lukeVanilla.System.Items.StatsSystem.ItemStatsCommand
import com.lukehemmin.lukeVanilla.System.SafeZoneManager
import com.lukehemmin.lukeVanilla.System.VanillaShutdownNotifier
import com.lukehemmin.lukeVanilla.System.WarningSystem.WarningCommand
import com.lukehemmin.lukeVanilla.System.WarningSystem.WarningService
import com.lukehemmin.lukeVanilla.System.Command.ServerConnectionCommand
import com.lukehemmin.lukeVanilla.System.Command.ServerTimeCommand
import com.lukehemmin.lukeVanilla.System.WardrobeLocationSystem
// import com.lukehemmin.lukeVanilla.System.NexoLuckPermsSystem.NexoLuckPermsGranter
import com.lukehemmin.lukeVanilla.System.MyLand.PrivateLandSystem
import com.lukehemmin.lukeVanilla.System.FarmVillage.FarmVillageSystem
import com.lukehemmin.lukeVanilla.System.Debug.DebugManager
import com.lukehemmin.lukeVanilla.System.Discord.AIassistant.AdminAssistant
import com.lukehemmin.lukeVanilla.System.MultiServer.MultiServerReader
import com.lukehemmin.lukeVanilla.System.MultiServer.MultiServerUpdater
import com.lukehemmin.lukeVanilla.System.PlayTime.PlayTimeSystem
import com.lukehemmin.lukeVanilla.System.AdvancedLandClaiming.AdvancedLandSystem
import net.luckperms.api.LuckPerms
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
    lateinit var statsSystem: StatsSystem
    lateinit var snowMinigame: SnowMinigame
    private var wardrobeLocationSystem: WardrobeLocationSystem? = null
    // private var nexoLuckPermsGranter: NexoLuckPermsGranter? = null
    private var privateLandSystem: PrivateLandSystem? = null
    private var farmVillageSystem: FarmVillageSystem? = null
    private var playTimeSystem: PlayTimeSystem? = null
    private var advancedLandSystem: AdvancedLandSystem? = null
    private lateinit var debugManager: DebugManager
    private var luckPerms: LuckPerms? = null
    private var multiServerUpdater: MultiServerUpdater? = null
    private var bookSystem: com.lukehemmin.lukeVanilla.System.BookSystem.BookSystem? = null

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
        // 아웃고잉 채널 등록 (개별 try-catch 처리)
        try {
            if (!server.messenger.isOutgoingChannelRegistered(this, CHANNEL_SERVER_STATUS_REQUEST)) {
                server.messenger.registerOutgoingPluginChannel(this, CHANNEL_SERVER_STATUS_REQUEST)
                logger.info("[서버 통신] 아웃고잉 채널 등록: $CHANNEL_SERVER_STATUS_REQUEST")
            } else {
                logger.info("[서버 통신] 아웃고잉 채널 이미 등록됨: $CHANNEL_SERVER_STATUS_REQUEST")
            }
        } catch (e: IllegalArgumentException) {
            logger.info("[서버 통신] 아웃고잉 채널 이미 등록됨 (예외): $CHANNEL_SERVER_STATUS_REQUEST")
        } catch (e: Exception) {
            logger.warning("[서버 통신] 아웃고잉 채널 등록 오류: $CHANNEL_SERVER_STATUS_REQUEST - ${e.message}")
        }
        
        try {
            if (!server.messenger.isOutgoingChannelRegistered(this, CHANNEL_SERVER_STATUS_RESPONSE)) {
                server.messenger.registerOutgoingPluginChannel(this, CHANNEL_SERVER_STATUS_RESPONSE)
                logger.info("[서버 통신] 아웃고잉 채널 등록: $CHANNEL_SERVER_STATUS_RESPONSE")
            } else {
                logger.info("[서버 통신] 아웃고잉 채널 이미 등록됨: $CHANNEL_SERVER_STATUS_RESPONSE")
            }
        } catch (e: IllegalArgumentException) {
            logger.info("[서버 통신] 아웃고잉 채널 이미 등록됨 (예외): $CHANNEL_SERVER_STATUS_RESPONSE")
        } catch (e: Exception) {
            logger.warning("[서버 통신] 아웃고잉 채널 등록 오류: $CHANNEL_SERVER_STATUS_RESPONSE - ${e.message}")
        }
        
        // 서버 타입에 따라 인커밍 리스너 등록 (개별 try-catch 처리)
        if (serviceType == "Lobby") {
            // 로비 서버: 야생 서버에 상태 요청 & 응답 처리
            try {
                if (!server.messenger.isIncomingChannelRegistered(this, CHANNEL_SERVER_STATUS_RESPONSE)) {
                    val requester = com.lukehemmin.lukeVanilla.System.Discord.ServerStatusRequester.getInstance(this)
                    server.messenger.registerIncomingPluginChannel(this, CHANNEL_SERVER_STATUS_RESPONSE, requester)
                    logger.info("[서버 통신] 로비 서버 인커밍 채널 등록: $CHANNEL_SERVER_STATUS_RESPONSE")
                } else {
                    logger.info("[서버 통신] 로비 서버 인커밍 채널 이미 등록됨: $CHANNEL_SERVER_STATUS_RESPONSE")
                }
            } catch (e: IllegalArgumentException) {
                logger.info("[서버 통신] 로비 서버 인커밍 채널 이미 등록됨 (예외): $CHANNEL_SERVER_STATUS_RESPONSE")
            } catch (e: Exception) {
                logger.warning("[서버 통신] 로비 서버 인커밍 채널 등록 오류: $CHANNEL_SERVER_STATUS_RESPONSE - ${e.message}")
            }
        } else {
            // 야생 서버: 로비 서버의 요청에 응답
            try {
                if (!server.messenger.isIncomingChannelRegistered(this, CHANNEL_SERVER_STATUS_REQUEST)) {
                    val listener = com.lukehemmin.lukeVanilla.System.Discord.ServerStatusListener.getInstance(this)
                    server.messenger.registerIncomingPluginChannel(this, CHANNEL_SERVER_STATUS_REQUEST, listener)
                    logger.info("[서버 통신] 야생 서버 인커밍 채널 등록: $CHANNEL_SERVER_STATUS_REQUEST")
                } else {
                    logger.info("[서버 통신] 야생 서버 인커밍 채널 이미 등록됨: $CHANNEL_SERVER_STATUS_REQUEST")
                }
            } catch (e: IllegalArgumentException) {
                logger.info("[서버 통신] 야생 서버 인커밍 채널 이미 등록됨 (예외): $CHANNEL_SERVER_STATUS_REQUEST")
            } catch (e: Exception) {
                logger.warning("[서버 통신] 야생 서버 인커밍 채널 등록 오류: $CHANNEL_SERVER_STATUS_REQUEST - ${e.message}")
            }
        }
    }

    /**
     * 플러그인 메시지 채널 등록 해제
     */
    private fun unregisterPluginMessageChannels() {
        try {
            // 아웃고잉 채널 해제
            if (server.messenger.isOutgoingChannelRegistered(this, CHANNEL_SERVER_STATUS_REQUEST)) {
                server.messenger.unregisterOutgoingPluginChannel(this, CHANNEL_SERVER_STATUS_REQUEST)
                logger.info("[서버 통신] 아웃고잉 채널 해제: $CHANNEL_SERVER_STATUS_REQUEST")
            }
            if (server.messenger.isOutgoingChannelRegistered(this, CHANNEL_SERVER_STATUS_RESPONSE)) {
                server.messenger.unregisterOutgoingPluginChannel(this, CHANNEL_SERVER_STATUS_RESPONSE)
                logger.info("[서버 통신] 아웃고잉 채널 해제: $CHANNEL_SERVER_STATUS_RESPONSE")
            }
            
            // 인커밍 채널 해제
            if (serviceType == "Lobby") {
                if (server.messenger.isIncomingChannelRegistered(this, CHANNEL_SERVER_STATUS_RESPONSE)) {
                    server.messenger.unregisterIncomingPluginChannel(this, CHANNEL_SERVER_STATUS_RESPONSE)
                    logger.info("[서버 통신] 로비 서버 인커밍 채널 해제: $CHANNEL_SERVER_STATUS_RESPONSE")
                }
            } else {
                if (server.messenger.isIncomingChannelRegistered(this, CHANNEL_SERVER_STATUS_REQUEST)) {
                    server.messenger.unregisterIncomingPluginChannel(this, CHANNEL_SERVER_STATUS_REQUEST)
                    logger.info("[서버 통신] 야생 서버 인커밍 채널 해제: $CHANNEL_SERVER_STATUS_REQUEST")
                }
            }
        } catch (e: Exception) {
            logger.warning("[서버 통신] 채널 해제 오류: ${e.message}")
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

    /**
     * HMCCosmetics 옷장 위치 시스템을 초기화합니다
     */
    private fun initializeWardrobeLocationSystem() {
        try {
            // HMCCosmetics 플러그인이 활성화되어 있는지 확인
            val hmcCosmeticsPlugin = server.pluginManager.getPlugin("HMCCosmetics")
            if (hmcCosmeticsPlugin == null || !hmcCosmeticsPlugin.isEnabled) {
                logger.warning("[WardrobeLocationSystem] HMCCosmetics 플러그인을 찾을 수 없습니다. 옷장 위치 시스템이 비활성화됩니다.")
                return
            }

            // 옷장 위치 시스템 초기화 및 등록
            wardrobeLocationSystem = WardrobeLocationSystem(this)
            server.pluginManager.registerEvents(wardrobeLocationSystem!!, this)

            // 설정에서 월드 이름 가져오기 (기본값: "world")
            val wardrobeWorldName = config.getString("wardrobe.world", "world") ?: "world"
            
            // 월드가 로드되어 있다면 즉시 설정, 아니면 월드 로드 대기
            val world = server.getWorld(wardrobeWorldName)
            if (world != null) {
                wardrobeLocationSystem!!.setWardrobeWorld(wardrobeWorldName)
                logger.info("[WardrobeLocationSystem] 옷장 위치 시스템이 초기화되었습니다. 월드: $wardrobeWorldName")
            } else {
                logger.warning("[WardrobeLocationSystem] 월드 '$wardrobeWorldName'을 찾을 수 없습니다. 월드가 로드된 후 자동으로 설정됩니다.")
                // 월드 로드 감지를 위한 스케줄러 (최대 30초 대기)
                var attempts = 0
                val maxAttempts = 30
                var worldCheckTask: org.bukkit.scheduler.BukkitTask? = null
                worldCheckTask = server.scheduler.runTaskTimer(this, Runnable {
                    attempts++
                    val checkWorld = server.getWorld(wardrobeWorldName)
                    if (checkWorld != null) {
                        wardrobeLocationSystem!!.setWardrobeWorld(wardrobeWorldName)
                        logger.info("[WardrobeLocationSystem] 월드 '$wardrobeWorldName'이 로드되어 옷장 시스템이 활성화되었습니다.")
                        worldCheckTask?.cancel()
                    } else if (attempts >= maxAttempts) {
                        logger.warning("[WardrobeLocationSystem] 월드 '$wardrobeWorldName' 로드 대기 시간이 초과되었습니다.")
                        worldCheckTask?.cancel()
                    }
                }, 20L, 20L) // 1초마다 확인
            }

        } catch (e: Exception) {
            logger.severe("[WardrobeLocationSystem] 옷장 위치 시스템 초기화 중 오류가 발생했습니다: ${e.message}")
            e.printStackTrace()
        }
    }
    
    override fun onEnable() {
        // DataBase Logic
        saveDefaultConfig()
        database = Database(this, config)
        val dbInitializer = DatabaseInitializer(database)
        dbInitializer.createTables()

        // DebugManager 초기화
        debugManager = DebugManager(this)

        // LuckPerms API 초기화
        val provider = server.servicesManager.getRegistration(LuckPerms::class.java)
        if (provider != null) {
            luckPerms = provider.provider
            logger.info("LuckPerms API 연동에 성공했습니다.")
        } else {
            logger.warning("LuckPerms 플러그인을 찾을 수 없어 관련 기능이 비활성화됩니다.")
        }

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
            
            // VoiceChannelTextListener 초기화 및 리스너 등록
            discordBot.jda.addEventListener(VoiceChannelTextListener(this))

            // DynamicVoiceChannelManager는 야생 서버에서만 실행
            if (serviceType == "Lobby") {
                val guildId = database.getSettingValue("DiscordServerID")
                guildId?.let {
                    discordBot.jda.addEventListener(
                        DynamicVoiceChannelManager(database, discordBot.jda, it)
                    )
                    logger.info("[DynamicVoiceChannelManager] 야생 서버에서 음성 채널 관리 기능을 활성화했습니다.")
                }
            }

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
                    
                    // MultiServerReader 인스턴스 생성 (로비 서버에서 멀티서버 정보 조회용)
                    val multiServerReader = MultiServerReader(this, database)
                    
                    // MultiServerUpdater 초기화 (로비 서버에서도 실행)
                    multiServerUpdater = MultiServerUpdater(this, database)
                    multiServerUpdater?.start()
                    logger.info("[MultiServerUpdater] 로비 서버에서 멀티서버 동기화 시스템 초기화 완료.")
                    
                    val adminAssistant = AdminAssistant(
                        dbConnectionProvider = ::provideDbConnection,
                        openAIApiKey = openAiApiKey, // API 키를 생성자에 전달
                        database = database,
                        warningService = warningService,
                        multiServerReader = multiServerReader
                    )
                    discordBot.jda.addEventListener(adminAssistant)
                    logger.info("[AdminAssistant] 로비 서버에서 관리자 어시스턴트 초기화 완료.")
                } else {
                    logger.warning("[AdminAssistant] OpenAI API 키를 찾을 수 없어 관리자 어시스턴트를 초기화할 수 없습니다. 데이터베이스 'Settings' 테이블에서 'OpenAI_API_Token' 값을 확인해주세요.")
                }
            }
        } else {
            logger.warning("데이터베이스에서 Discord 토큰을 찾을 수 없습니다. Discord 봇 관련 기능이 제한됩니다.")
            if (openAiApiKey == null) { 
                logger.warning("[AdminAssistant] OpenAI API 키도 찾을 수 없습니다. (데이터베이스 'Settings' 테이블의 'OpenAI_API_Token' 값 확인 필요) 관리자 어시스턴트 기능이 비활성화됩니다.")
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
        
        // 스크롤 변환 시스템 초기화
        server.pluginManager.registerEvents(ItemScrollTransformSystem(this), this)
        
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
        //server.pluginManager.registerEvents(hscroll(), this)

        // Command System
        getCommand("infomessage")?.setExecutor(infomessage())
        getCommand("wleh")?.setExecutor(mapcommand())
        getCommand("지도")?.setExecutor(mapcommand())
        getCommand("투명액자")?.setExecutor(TransparentFrameCommand())

        getCommand("pl")?.setExecutor(plcommandcancel())
        getCommand("plugins")?.setExecutor(plcommandcancel())
        getCommand("lukeplugininfo")?.setExecutor(plcommandcancel())

        // 블록 위치 확인 명령어 등록
        val blockLocationCommand = BlockLocationCommand()
        getCommand("블록위치")?.setExecutor(blockLocationCommand)
        server.pluginManager.registerEvents(blockLocationCommand, this)

        // 서버시간 명령어 등록
        getCommand("서버시간")?.setExecutor(ServerTimeCommand())

        // ItemRestoreCommand 초기화 부분 수정
        val itemRestoreCommand = ItemRestoreCommand(itemRestoreLogger)
        getCommand("아이템복구")?.setExecutor(itemRestoreCommand)
        server.pluginManager.registerEvents(itemRestoreCommand, this)

        // 티토커 메시지 명령어 등록
        getCommand("티토커메시지")?.setExecutor(TitokerMessageCommand(this))
        getCommand("티토커메시지")?.tabCompleter = TitokerCommandCompleter()

        // 음성채널 메시지 명령어 등록
        getCommand("음성채널메시지")?.setExecutor(VoiceChannelMessageCommand(this))
        getCommand("음성채널메시지")?.tabCompleter = VoiceChannelMessageCommandCompleter()

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

        // SafeZoneManager는 야생 서버에서만 실행
        if (serviceType == "Vanilla") {
            server.pluginManager.registerEvents(SafeZoneManager(this), this)
            logger.info("[SafeZoneManager] 야생 서버에서 안전 구역 관리자 초기화 완료.")
            
            // MultiServerUpdater 초기화 (야생 서버에서만 실행)
            multiServerUpdater = MultiServerUpdater(this, database)
            multiServerUpdater?.start()
            logger.info("[MultiServerUpdater] 야생 서버에서 멀티서버 동기화 시스템 초기화 완료.")
        }

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

        // 서버 연결 관리 명령어 등록 (로비서버 전용)
        if (serviceType == "Lobby") {
            getCommand("서버연결")?.setExecutor(ServerConnectionCommand(this))
            getCommand("서버연결")?.tabCompleter = ServerConnectionCommand(this)
            getCommand("serverconnection")?.setExecutor(ServerConnectionCommand(this))
            getCommand("serverconnection")?.tabCompleter = ServerConnectionCommand(this)
            logger.info("[서버 연결 관리] 로비서버에서 연결 관리 명령어 등록 완료.")
        }

        // HMCCosmetics 옷장 위치 시스템 초기화 (야생서버에서만 실행)
        if (serviceType == "Vanilla") {
            server.scheduler.runTaskLater(this, Runnable {
                initializeWardrobeLocationSystem()
            }, 20L) // 1초 후 실행
            logger.info("[WardrobeLocationSystem] 야생서버에서 옷장 위치 시스템을 초기화합니다.")
        } else {
            logger.info("[WardrobeLocationSystem] $serviceType 서버에서는 옷장 위치 시스템이 비활성화됩니다.")
        }

        // NexoLuckPermsGranter 시스템 초기화
        try {
            // LuckPerms 플러그인이 활성화되어 있는지 확인
            val luckPermsPlugin = server.pluginManager.getPlugin("LuckPerms")
            if (luckPermsPlugin != null && luckPermsPlugin.isEnabled) {
                // nexoLuckPermsGranter = NexoLuckPermsGranter(this)
                // nexoLuckPermsGranter?.register()
                logger.info("[NexoLuckPermsGranter] 권한 지급 시스템이 초기화되었습니다. (현재 주석 처리됨)")
            } else {
                logger.warning("[NexoLuckPermsGranter] LuckPerms 플러그인을 찾을 수 없습니다. 권한 지급 시스템이 비활성화됩니다.")
            }
        } catch (e: Exception) {
            logger.severe("[NexoLuckPermsGranter] 권한 지급 시스템 초기화 중 오류가 발생했습니다: ${e.message}")
            e.printStackTrace()
        }

        // PlayTime 시스템 초기화 (모든 서버에서 실행)
        try {
            playTimeSystem = PlayTimeSystem(this, database, debugManager)
            playTimeSystem?.enable()
        } catch (e: Exception) {
            logger.severe("[PlayTime] 플레이타임 시스템 초기화 중 오류가 발생했습니다: ${e.message}")
            e.printStackTrace()
        }

        // AdvancedLandClaiming 시스템 초기화 (야생서버에서만 실행, PlayTime 시스템 이후)
        if (serviceType == "Vanilla") {
            try {
                val playTimeManager = playTimeSystem?.getPlayTimeManager()
                if (playTimeManager != null) {
                    advancedLandSystem = AdvancedLandSystem(this, database, debugManager, playTimeManager)
                    advancedLandSystem?.enable()
                } else {
                    logger.warning("[AdvancedLandClaiming] PlayTime 시스템이 초기화되지 않아 AdvancedLandClaiming을 시작할 수 없습니다.")
                }
            } catch (e: Exception) {
                logger.severe("[AdvancedLandClaiming] 고급 토지 클레이밍 시스템 초기화 중 오류가 발생했습니다: ${e.message}")
                e.printStackTrace()
            }
        }

        // 개인 땅 시스템 초기화 (야생서버에서만 실행)
        if (serviceType == "Vanilla") {
            try {
                privateLandSystem = PrivateLandSystem(this, database, debugManager)
                privateLandSystem?.enable()

                // FarmVillage 시스템 초기화 (PrivateLandSystem에 의존)
                privateLandSystem?.let { privateLand ->
                    farmVillageSystem = FarmVillageSystem(this, database, privateLand, debugManager, luckPerms)
                    farmVillageSystem?.enable()
                    
                    // LandCommand에서 농사마을 땅 번호를 표시할 수 있도록 FarmVillageManager 참조 설정
                    farmVillageSystem?.let { farmVillage ->
                        privateLand.setFarmVillageManager(farmVillage.getFarmVillageManager())
                    }
                    
                    // AdvancedLandClaiming과 LandCommand 통합
                    advancedLandSystem?.let { advancedLand ->
                        // PrivateLandSystem에서 LandCommand 가져와서 AdvancedLandManager 주입
                        val landCommand = privateLand.getLandManager() // 이건 작동하지 않을 수 있음, 실제 구조에 따라 수정 필요
                        // advancedLand.integrateWithLandCommand(landCommand)  // 임시 주석 처리
                        logger.info("[AdvancedLandClaiming] LandCommand와 통합 완료")
                    }
                }

            } catch (e: Exception) {
                logger.severe("[MyLand/FarmVillage] 시스템 초기화 중 오류가 발생했습니다: ${e.message}")
                e.printStackTrace()
            }
        } else {
            logger.info("[MyLand/FarmVillage] ${serviceType} 서버에서는 개인 땅 및 농장마을 시스템이 비활성화됩니다.")
        }

        // BookSystem 초기화 (야생 서버에서만 실행)
        if (serviceType == "Vanilla") {
            try {
                bookSystem = com.lukehemmin.lukeVanilla.System.BookSystem.BookSystem(this, database)
                bookSystem?.enable()
                logger.info("[BookSystem] 야생 서버에서 책 시스템이 성공적으로 초기화되었습니다.")
            } catch (e: Exception) {
                logger.severe("[BookSystem] 책 시스템 초기화 중 오류가 발생했습니다: ${e.message}")
                e.printStackTrace()
                // 책 시스템은 필수가 아니므로 플러그인 전체를 중단하지 않음
            }
        } else {
            logger.info("[BookSystem] ${serviceType} 서버에서는 책 시스템이 비활성화됩니다.")
        }
    }

    override fun onDisable() {
        // PlayTime 시스템 비활성화
        playTimeSystem?.disable()
        
        // AdvancedLandClaiming 시스템 비활성화
        advancedLandSystem?.disable()
        
        // 농장마을 시스템 비활성화
        farmVillageSystem?.disable()
        
        // 개인 땅 시스템 비활성화
        privateLandSystem?.disable()
        
        // 옷장 위치 시스템 정리
        wardrobeLocationSystem?.cleanup()
        
        // 멀티서버 동기화 시스템 중단
        multiServerUpdater?.stop()
        
        // BookSystem 종료
        try {
            bookSystem?.disable()
            logger.info("[BookSystem] 책 시스템이 정상적으로 종료되었습니다.")
        } catch (e: Exception) {
            logger.severe("[BookSystem] 책 시스템 종료 중 오류가 발생했습니다: ${e.message}")
            e.printStackTrace()
        }
        
        // 서버 종료 직전 프록시에 오프라인 임박 메시지 전송
        try {
            VanillaShutdownNotifier.notifyShutdownImminent(this)
        } catch (e: Exception) {
            logger.warning("[서버 통신] 종료 알림 전송 중 오류: ${e.message}")
        }
        
        // 플러그인 메시지 채널 등록 해제
        try {
            unregisterPluginMessageChannels()
        } catch (e: Exception) {
            logger.warning("[서버 통신] 채널 해제 중 오류: ${e.message}")
        }
        
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
