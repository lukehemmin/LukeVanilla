package com.lukehemmin.lukeVanilla.System.Debug

import com.lukehemmin.lukeVanilla.Main

class DebugManager(private val plugin: Main) {
    private val debugStates = mutableMapOf<String, Boolean>()

    init {
        loadDebugConfig()
    }

    private fun loadDebugConfig() {
        plugin.config.getConfigurationSection("debug")?.let { section ->
            for (key in section.getKeys(false)) {
                debugStates[key.lowercase()] = section.getBoolean(key)
                if (section.getBoolean(key)) {
                    plugin.logger.info("[DebugManager] ${key} 시스템에 대한 디버그 모드가 활성화되었습니다.")
                }
            }
        }
    }

    fun log(systemName: String, message: String) {
        if (debugStates[systemName.lowercase()] == true) {
            plugin.logger.info("[DEBUG - $systemName] $message")
        }
    }
} 