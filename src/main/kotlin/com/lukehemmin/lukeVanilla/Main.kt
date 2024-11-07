package com.lukehemmin.lukeVanilla

import com.lukehemmin.lukeVanilla.System.Database.Database
import com.lukehemmin.lukeVanilla.System.Database.DatabaseInitializer
import com.lukehemmin.lukeVanilla.System.DiscordAuth.PlayerJoinListener
import com.lukehemmin.lukeVanilla.System.DiscordAuth.PlayerLoginListener
import com.lukehemmin.lukeVanilla.System.Items.DurabilityListener
import com.lukehemmin.lukeVanilla.System.Items.EnchantmentLimitListener
import com.lukehemmin.lukeVanilla.System.Items.Halloween_Item
import com.lukehemmin.lukeVanilla.System.Items.ItemCommand
import com.lukehemmin.lukeVanilla.System.NameTag.NametagCommand
import com.lukehemmin.lukeVanilla.System.NameTag.NametagManager
import com.lukehemmin.lukeVanilla.System.Player_Join_And_Quit_Message_Listener
import com.lukehemmin.lukeVanlia.commands.mapcommand
import com.lukehemmin.lukeVanlia.lobby.SnowMinigame
import com.lukehemmin.lukeVanlia.velocity.infomessage
import org.bukkit.plugin.java.JavaPlugin

class Main : JavaPlugin() {
    private lateinit var snowMinigame: SnowMinigame
    lateinit var database: Database
    private lateinit var serviceType: String
    private lateinit var nametagManager: NametagManager


    override fun onEnable() {
        // DataBase Logic
        saveDefaultConfig()
        database = Database(config)
        val dbInitializer = DatabaseInitializer(database)
        dbInitializer.createTables()

        // Read service type from config
        serviceType = config.getString("service.type") ?: "Vanilla"

        // 이벤트 리스너 등록
        server.pluginManager.registerEvents(PlayerLoginListener(database), this)
        server.pluginManager.registerEvents(PlayerJoinListener(database), this)
        server.pluginManager.registerEvents(Player_Join_And_Quit_Message_Listener(serviceType, this, database), this)

        // Player_Join_And_Quit_Message 갱신 스케줄러
        server.scheduler.runTaskTimer(this, Runnable {
            Player_Join_And_Quit_Message_Listener.updateMessages(database)
        }, 0L, 1200L) // 60초마다 실행 (1200 ticks)

        // Register SnowMinigame if serviceType is Lobby
        if (serviceType == "Lobby") {
            snowMinigame = SnowMinigame(this)
            server.pluginManager.registerEvents(snowMinigame, this)
        }

        // DiscordAuth Logic
        server.pluginManager.registerEvents(PlayerJoinListener(database), this)

        // Nametag System
        nametagManager = NametagManager(this, database)
        getCommand("nametag")?.setExecutor(NametagCommand(database, nametagManager))

        // Item System
        getCommand("item")?.setExecutor(ItemCommand())
        server.pluginManager.registerEvents(DurabilityListener(this), this)
        server.pluginManager.registerEvents(EnchantmentLimitListener(), this)
        server.pluginManager.registerEvents(Halloween_Item(), this)

        // Command System
        getCommand("infomessage")?.setExecutor(infomessage())
        getCommand("wleh")?.setExecutor(mapcommand())
        getCommand("지도")?.setExecutor(mapcommand())

        // Plugin Logic
        logger.info("Plugin enabled")
    }

    override fun onDisable() {
        // Plugin shutdown logic
        logger.info("Plugin disabled")
    }
}

