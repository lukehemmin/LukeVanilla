package com.lukehemmin.lukeVanilla

import com.lukehemmin.lukeVanilla.System.Database.Database
import com.lukehemmin.lukeVanilla.System.Database.DatabaseInitializer
import com.lukehemmin.lukeVanilla.System.Player_Join_And_Quit_Message_Listener
import org.bukkit.plugin.java.JavaPlugin

class Main : JavaPlugin() {
    lateinit var database: Database
    private lateinit var serviceType: String

    override fun onEnable() {
        // DataBase Logic
        saveDefaultConfig()
        database = Database(config)
        val dbInitializer = DatabaseInitializer(database)
        dbInitializer.createTables()

        // Read service type from config
        serviceType = config.getString("service.type") ?: "Vanilla"

        server.pluginManager.registerEvents(Player_Join_And_Quit_Message_Listener(serviceType, this), this)

        // Plugin Logic
        logger.info("Plugin enabled")
    }

    override fun onDisable() {
        // Plugin shutdown logic
        logger.info("Plugin disabled")
    }
}
