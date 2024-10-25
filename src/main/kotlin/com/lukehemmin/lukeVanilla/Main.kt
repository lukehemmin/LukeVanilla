package com.lukehemmin.lukeVanilla

import com.lukehemmin.lukeVanilla.System.Database.Database
import com.lukehemmin.lukeVanilla.System.Database.DatabaseInitializer
import org.bukkit.plugin.java.JavaPlugin

class Main : JavaPlugin() {
    lateinit var database: Database

    override fun onEnable() {
        // DataBase Logic
        saveDefaultConfig()
        database = Database(config)
        val dbInitializer = DatabaseInitializer(database)
        dbInitializer.createTables()

        // Plugin Logic
        logger.info("Plugin enabled")
    }

    override fun onDisable() {
        // Plugin shutdown logic
        logger.info("Plugin disabled")
    }
}
