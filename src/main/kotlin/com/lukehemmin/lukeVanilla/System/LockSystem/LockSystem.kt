package com.lukehemmin.lukeVanilla.System.LockSystem

import com.lukehemmin.lukeVanilla.Main
import org.bukkit.plugin.PluginManager

class LockSystem(private val plugin: Main) {
    val blockLockManager = BlockLockManager(plugin)
    val lockListener = LockListener(plugin, blockLockManager)

    fun enable() {
        createLockTable()
        registerListeners()
    }

    private fun createLockTable() {
        plugin.database.createLockTable()
    }

    fun disable() {
        // TODO: 잠금 시스템 비활성화 로직 (필요한 경우)
    }

    private fun registerListeners() {
        val pluginManager = plugin.server.pluginManager
        pluginManager.registerEvents(lockListener, plugin)
    }
}
