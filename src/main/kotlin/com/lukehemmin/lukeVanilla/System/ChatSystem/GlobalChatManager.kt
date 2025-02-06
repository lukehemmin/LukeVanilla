package com.lukehemmin.lukeVanilla.System.ChatSystem

import com.lukehemmin.lukeVanilla.System.ColorUtill.ColorUtil.translateColorCodes
import com.lukehemmin.lukeVanilla.System.ColorUtill.ColorUtil.translateHexColorCodes
import com.lukehemmin.lukeVanilla.System.Database.Database
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.AsyncPlayerChatEvent
import org.bukkit.plugin.java.JavaPlugin
import org.json.simple.JSONObject
import org.json.simple.parser.JSONParser
import java.io.*
import java.net.Socket
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class GlobalChatManager(
    private val plugin: JavaPlugin,
    private val database: Database
) : Listener {

    private val serverName = plugin.config.getString("server.name") ?: "Unknown"
    private val recentMessages = ConcurrentHashMap<UUID, MutableList<RecentMessage>>()
    private var socket: Socket? = null
    private var writer: BufferedWriter? = null
    private var reader: BufferedReader? = null
    private val BROKER_HOST = "218.55.36.251"
    private val BROKER_PORT = 9978

    data class RecentMessage(
        val content: String,
        val timestamp: Long
    )

    init {
        plugin.server.pluginManager.registerEvents(this, plugin)
        connectToBroker()
        startMessageReceiver()
    }

    private fun connectToBroker() {
        try {
            socket = Socket(BROKER_HOST, BROKER_PORT)
            writer = BufferedWriter(OutputStreamWriter(socket!!.getOutputStream()))
            reader = BufferedReader(InputStreamReader(socket!!.getInputStream()))

            // 서버 정보 전송
            val serverInfo = JSONObject().apply {
                put("server_name", serverName)
            }
            writer?.write(serverInfo.toString() + "\n")
            writer?.flush()

            plugin.logger.info("중계 서버에 연결됨")
        } catch (e: Exception) {
            plugin.logger.warning("중계 서버 연결 실패: ${e.message}")
            scheduleReconnect()
        }
    }

    private fun scheduleReconnect() {
        Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, Runnable {
            if (socket?.isConnected != true) {
                connectToBroker()
            }
        }, 100L) // 5초 후 재시도
    }

    private fun startMessageReceiver() {
        Thread {
            while (plugin.isEnabled) {
                try {
                    val line = reader?.readLine()
                    if (line == null) {
                        if (plugin.isEnabled) {
                            scheduleReconnect()
                        }
                        break
                    }

                    val jsonObject = JSONParser().parse(line) as JSONObject
                    val fromServer = jsonObject["server"] as String
                    if (fromServer != serverName) {
                        val playerName = jsonObject["player"] as String
                        val chatMessage = jsonObject["message"] as String
                        val nameTag = (jsonObject["nameTag"] as? String ?: "")
                            .translateColorCodes()
                            .translateHexColorCodes()  // 받은 메시지의 네임태그에 색상 적용

                        Bukkit.getScheduler().runTask(plugin, Runnable {
                            val prefix = if (nameTag.isBlank()) "" else "$nameTag "
                            Bukkit.broadcastMessage("§7[${fromServer}] ${prefix}§f${playerName} : ${chatMessage}")
                        })
                    }
                } catch (e: Exception) {
                    if (plugin.isEnabled) {
                        plugin.logger.warning("메시지 수신 오류: ${e.message}")
                        scheduleReconnect()
                    }
                    break
                }
            }
        }.start()
    }

    @EventHandler(priority = EventPriority.NORMAL)
    fun onPlayerChat(event: AsyncPlayerChatEvent) {
        val player = event.player
        val message = event.message

        if (isSpamming(player.uniqueId, message)) {
            event.isCancelled = true
            player.sendMessage("§c같은 메시지를 너무 자주 보내고 있습니다. 잠시 후 다시 시도해주세요.")
            return
        }

        val nameTag = getPlayerNameTag(player.uniqueId, database.getConnection())

        val jsonObject = JSONObject().apply {
            put("server", serverName)
            put("player", player.name)
            put("message", message)
            put("nameTag", nameTag)  // 원본 네임태그 전송 (색상 코드 포함)
        }

        try {
            writer?.write(jsonObject.toString() + "\n")
            writer?.flush()
        } catch (e: Exception) {
            plugin.logger.warning("메시지 전송 오류: ${e.message}")
            scheduleReconnect()
        }

        val prefix = if (nameTag.isBlank()) ""
        else "${nameTag.translateColorCodes().translateHexColorCodes()} "
        event.format = "§7[${serverName}] ${prefix}§f%s : %s"
    }

    private fun isSpamming(uuid: UUID, message: String): Boolean {
        val currentTime = System.currentTimeMillis()
        val messages = recentMessages.computeIfAbsent(uuid) { mutableListOf() }
        messages.removeIf { currentTime - it.timestamp > 3000 }
        val sameMessageCount = messages.count { it.content == message }
        messages.add(RecentMessage(message, currentTime))
        return sameMessageCount >= 2
    }

    private fun getPlayerNameTag(uuid: UUID, connection: java.sql.Connection): String {
        connection.use { conn ->
            conn.prepareStatement("SELECT Tag FROM Player_NameTag WHERE UUID = ?").use { statement ->
                statement.setString(1, uuid.toString())
                statement.executeQuery().use { resultSet ->
                    return if (resultSet.next()) resultSet.getString("Tag") else ""
                }
            }
        }
    }
}