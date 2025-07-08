package com.lukehemmin.lukeVanilla.System.MyLand

import com.lukehemmin.lukeVanilla.Main
import com.lukehemmin.lukeVanilla.System.Database.Database

class PrivateLandSystem(private val plugin: Main, private val database: Database) {

    private val landManager = LandManager(plugin, database)
    private val landProtectionListener = LandProtectionListener(landManager)
    private val landCommand = LandCommand(landManager)

    fun getLandManager(): LandManager {
        return landManager
    }

    fun enable() {
        landManager.loadConfig()
        landManager.loadClaimsFromDatabase()
        plugin.server.pluginManager.registerEvents(landProtectionListener, plugin)
        plugin.getCommand("땅")?.setExecutor(landCommand)
        plugin.getCommand("땅")?.tabCompleter = landCommand
        plugin.logger.info("[MyLand] 개인 땅 시스템이 활성화되었습니다.")
    }

    fun disable() {
        // Cleanup logic if needed
        plugin.logger.info("[MyLand] 개인 땅 시스템이 비활성화되었습니다.")
    }
} 