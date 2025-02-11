package com.lukehemmin.lukeVanilla

import com.lukehemmin.lukeVanilla.Lobby.SnowMinigame
import com.lukehemmin.lukeVanilla.System.Command.*
import com.lukehemmin.lukeVanilla.System.Database.Database
import com.lukehemmin.lukeVanilla.System.Database.DatabaseInitializer
import com.lukehemmin.lukeVanilla.System.Discord.*
import com.lukehemmin.lukeVanilla.System.Economy.EconomyManager
import com.lukehemmin.lukeVanilla.System.Economy.MoneyCommand
import com.lukehemmin.lukeVanilla.System.Halloween.*
import com.lukehemmin.lukeVanilla.System.Items.*
import com.lukehemmin.lukeVanilla.System.NPC.NPCSitPreventer
import com.lukehemmin.lukeVanilla.System.ChatSystem.*
import com.lukehemmin.lukeVanilla.System.NoExplosionListener
import com.lukehemmin.lukeVanilla.System.Player_Join_And_Quit_Message_Listener
import org.bukkit.plugin.java.JavaPlugin
import java.util.concurrent.TimeUnit

class Main : JavaPlugin() {
    private lateinit var snowMinigame: SnowMinigame
    lateinit var database: Database
    private lateinit var serviceType: String
    private lateinit var nametagManager: NametagManager
    private lateinit var discordRoleManager: DiscordRoleManager
    lateinit var discordBot: DiscordBot // 추가된 라인
    private lateinit var itemRestoreLogger: ItemRestoreLogger
    lateinit var nextSeasonGUI: NextSeasonItemGUI
    lateinit var economyManager: EconomyManager
//    lateinit var shopManager: ShopManager
//    lateinit var shopPriceListener: ShopPriceListener
//    lateinit var shopManager: ShopManager
//    lateinit var priceEditManager: PriceEditManager // 추가

    override fun onEnable() {
        // DataBase Logic
        saveDefaultConfig()
        database = Database(config)
        val dbInitializer = DatabaseInitializer(database)
        dbInitializer.createTables()

        // Read service type from config
        serviceType = config.getString("service.type") ?: "Vanilla"

        // Discord Bot 초기화
        val discordToken = database.getSettingValue("DiscordToken")
        if (discordToken != null) {
            discordBot = DiscordBot()
            discordBot.start(discordToken)

            // DiscordRoleManager 초기화
            discordRoleManager = DiscordRoleManager(database, discordBot.jda)

            // DiscordAuth 초기화 및 리스너 등록
            val discordAuth = DiscordAuth(database, this)
            discordBot.jda.addEventListener(discordAuth)

            // DiscordLeave 초기화 및 리스너 등록
            val discordLeave = DiscordLeave(database, this, discordBot.jda)
            discordBot.jda.addEventListener(discordLeave) // 수정된 부분
            server.pluginManager.registerEvents(discordLeave, this)

            // DiscordVoiceChannelListener 초기화 및 리스너 등록
            discordBot.jda.addEventListener(DiscordVoiceChannelListener(this))

            // 티토커 채팅 리스너 등록
            discordBot.jda.addEventListener(TitokerChatListener(this))

            // ItemRestoreLogger 초기화
            itemRestoreLogger = ItemRestoreLogger(database, this, discordBot.jda)
        } else {
            logger.warning("데이터베이스에서 Discord 토큰을 찾을 수 없습니다.")
        }

        // Discord Bot 초기화 부분 아래에 추가
        discordBot.jda.addEventListener(
            SupportSystem(discordBot, database),
            SupportCaseListener(database, discordBot)
        )

        // 이벤트 리스너 등록
        server.pluginManager.registerEvents(PlayerLoginListener(database), this)
        server.pluginManager.registerEvents(Player_Join_And_Quit_Message_Listener(serviceType, this, database), this)
        server.pluginManager.registerEvents(PlayerJoinListener(this, database, discordRoleManager), this) // PlayerJoinListener에 DiscordRoleManager 전달

        // Player_Join_And_Quit_Message 갱신 스케줄러
        server.scheduler.runTaskTimer(this, Runnable {
            Player_Join_And_Quit_Message_Listener.updateMessages(database)
        }, 0L, 1200L) // 60초마다 실행 (1200 ticks)

        // Register SnowMinigame if serviceType is Lobby
        if (serviceType == "Lobby") {
            snowMinigame = SnowMinigame(this)
            server.pluginManager.registerEvents(snowMinigame, this)
        }

        // Nametag System
        nametagManager = NametagManager(this, database)
        val nametagCommand = NametagCommand(database, nametagManager)
        getCommand("nametag")?.setExecutor(nametagCommand)
        getCommand("delnametag")?.setExecutor(nametagCommand)

        // Item System
        getCommand("item")?.setExecutor(ItemCommand())
        server.pluginManager.registerEvents(DurabilityListener(this), this)
        server.pluginManager.registerEvents(EnchantmentLimitListener(), this)
        server.pluginManager.registerEvents(Halloween_Item(), this)
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

        server.pluginManager.registerEvents(HalloweenGUIListener(this), this)
        getCommand("할로윈")?.setExecutor(HalloweenCommand(this))
        getCommand("할로윈")?.tabCompleter = HalloweenCommandCompleter()

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

        // Christmas_sword 이벤트 리스너 등록
        Christmas_sword(this)

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

//        // 상점 시스템 초기화
//        shopManager = ShopManager(this, database, economyManager)
//
//        // 명령어 등록
//        getCommand("상점")?.setExecutor(ShopCommand(shopManager))
//
//        // 리스너 등록
//        server.pluginManager.registerEvents(ShopGUIListener(shopManager, ShopPriceListener(shopManager)), this)

        // GlobalChatManager 초기화
        //GlobalChatManager(this, database)

        // Plugin Logic
        logger.info("Plugin enabled")

        // NPCSitPreventer 등록
        server.pluginManager.registerEvents(NPCSitPreventer(this), this)
    }

    override fun onDisable() {
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