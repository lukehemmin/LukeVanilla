package com.lukehemmin.lukeVanilla.System.Debug

import com.lukehemmin.lukeVanilla.Main
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.FileHandler
import java.util.logging.Level
import java.util.logging.Logger
import java.util.logging.SimpleFormatter

class DebugManager(private val plugin: Main) {
    private val debugStates = mutableMapOf<String, Boolean>()
    private val systemLoggers: MutableMap<String, Logger> = ConcurrentHashMap()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd")

    init {
        loadDebugConfig()
    }

    private fun loadDebugConfig() {
        plugin.config.getConfigurationSection("debug")?.let { section ->
            for (key in section.getKeys(false)) {
                val enabled = section.getBoolean(key)
                val normalized = key.lowercase()
                debugStates[normalized] = enabled
                if (enabled) {
                    ensureLogger(normalized)
                }
            }
        }
    }

    private fun ensureLogger(systemName: String): Logger {
        return systemLoggers.computeIfAbsent(systemName) { name ->
            val logger = Logger.getLogger("LukeVanilla-Debug-$name")
            logger.useParentHandlers = false // 콘솔로 전파하지 않음
            logger.level = Level.INFO

            // 로그 디렉토리 준비: <plugin data folder>/logs
            val logsDir = File(plugin.dataFolder, "logs")
            if (!logsDir.exists()) {
                logsDir.mkdirs()
            }

            // 파일명: debug-<system>-<yyyy-MM-dd>.log (일별 롤링)
            val dateStr = dateFormat.format(Date())
            val logFilePath = File(logsDir, "debug-${name}-$dateStr.log").absolutePath
            try {
                val handler = FileHandler(logFilePath, true)
                handler.formatter = SimpleFormatter()
                handler.level = Level.INFO
                logger.addHandler(handler)
            } catch (e: Exception) {
                // 파일 핸들러 초기화 실패 시 콘솔에만 경고
                plugin.logger.warning("[DebugManager] 파일 로거 초기화 실패 ($name): ${e.message}")
            }

            logger
        }
    }

    /**
     * 디버그 로그: config.yml의 debug.<system> 이 true일 때만 파일로 남김. (콘솔 출력 없음)
     */
    fun log(systemName: String, message: String) {
        val normalized = systemName.lowercase()
        if (debugStates[normalized] == true) {
            val logger = ensureLogger(normalized)
            logger.info("[$systemName] $message")
        }
    }
}