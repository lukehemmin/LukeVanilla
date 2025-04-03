package com.lukehemmin.lukeVanilla.System.ChatSystem

import com.lukehemmin.lukeVanilla.System.ColorUtill.ColorUtil.translateColorCodes
import com.lukehemmin.lukeVanilla.System.ColorUtill.ColorUtil.translateHexColorCodes
import com.lukehemmin.lukeVanilla.System.Database.Database
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.AsyncPlayerChatEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scoreboard.Scoreboard
import org.bukkit.scoreboard.Team
import java.util.*

class NametagManager(private val plugin: JavaPlugin, private val database: Database) : Listener {
    private val scoreboard: Scoreboard = Bukkit.getScoreboardManager().mainScoreboard
    private val playerTeams: MutableMap<UUID, Team> = mutableMapOf()

    init {
        // 이벤트 등록
        plugin.server.pluginManager.registerEvents(this, plugin)

        // 1분마다 NameTag 값 새로고침
        plugin.server.scheduler.runTaskTimer(plugin, Runnable {
            refreshNametags()
        }, 0L, 1200L) // 60초마다 실행 (1200 ticks)
    }

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player
        val uuid = player.uniqueId
        val connection = database.getConnection()
        val statement = connection.prepareStatement("SELECT Tag FROM Player_NameTag WHERE UUID = ?")
        statement.setString(1, uuid.toString())
        val resultSet = statement.executeQuery()

        if (resultSet.next()) {
            val nameTag = resultSet.getString("Tag")
            updatePlayerNametag(player, nameTag)
        }

        resultSet.close()
        statement.close()
        connection.close()
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val player = event.player
        val team = playerTeams.remove(player.uniqueId)
        team?.unregister()
    }

    @EventHandler
    fun onPlayerChat(event: AsyncPlayerChatEvent) {
        val player = event.player
        val uuid = player.uniqueId
        val connection = database.getConnection()
        val statement = connection.prepareStatement("SELECT Tag FROM Player_NameTag WHERE UUID = ?")
        statement.setString(1, uuid.toString())
        val resultSet = statement.executeQuery()

        event.format = if (resultSet.next()) {
            val nameTag = resultSet.getString("Tag")
            if (nameTag.isNotBlank()) {
                "${nameTag.translateColorCodes().translateHexColorCodes()}§f ${player.name} : %2\$s"
            } else {
                "${player.name} : %2\$s"
            }
        } else {
            "${player.name} : %2\$s"
        }

        resultSet.close()
        statement.close()
        connection.close()
    }

    private fun refreshNametags() {
        for (player in Bukkit.getOnlinePlayers()) {
            val uuid = player.uniqueId
            val connection = database.getConnection()
            val statement = connection.prepareStatement("SELECT Tag FROM Player_NameTag WHERE UUID = ?")
            statement.setString(1, uuid.toString())
            val resultSet = statement.executeQuery()

            if (resultSet.next()) {
                val nameTag = resultSet.getString("Tag")
                updatePlayerNametag(player, nameTag)
            }

            resultSet.close()
            statement.close()
            connection.close()
        }
    }

    fun updatePlayerNametag(player: Player, nameTag: String) {
        val team = playerTeams.getOrPut(player.uniqueId) {
            val newTeam = scoreboard.getTeam(player.uniqueId.toString())
                ?: scoreboard.registerNewTeam(player.uniqueId.toString())
            newTeam.addEntry(player.name)
            newTeam
        }
        val translatedNameTag = nameTag.translateColorCodes().translateHexColorCodes()
        team.prefix = if (nameTag.isBlank()) "" else "$translatedNameTag "
        player.setPlayerListName(if (nameTag.isBlank()) player.name else "$translatedNameTag§f ${player.name}")
    }
}