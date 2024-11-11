package com.lukehemmin.lukeVanilla.System.Discord

import com.lukehemmin.lukeVanilla.System.Database.Database
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.plugin.java.JavaPlugin

class PlayerJoinListener(private val plugin: JavaPlugin, private val database: Database, private val discordRoleManager: DiscordRoleManager) : Listener {

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player
        val uuid = player.uniqueId.toString()
        val nickname = player.name

        // Runnable을 명시적으로 사용
        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            discordRoleManager.checkAndGrantAuthRole(player)
        }, 20L)

        database.getConnection().use { connection ->
            // Player_Data 테이블에서 UUID로 행을 찾음
            connection.prepareStatement("SELECT * FROM Player_Data WHERE UUID = ?").use { checkPlayerData ->
                checkPlayerData.setString(1, uuid)
                checkPlayerData.executeQuery().use { playerDataResult ->
                    if (playerDataResult.next()) {
                        // 기존 행이 있으면 NickName을 업데이트
                        connection.prepareStatement("UPDATE Player_Data SET NickName = ? WHERE UUID = ?").use { updateNickName ->
                            updateNickName.setString(1, nickname)
                            updateNickName.setString(2, uuid)
                            updateNickName.executeUpdate()
                        }
                    } else {
                        // 행이 없으면 새로운 행을 추가
                        connection.prepareStatement(
                            "INSERT INTO Player_Data (UUID, NickName, DiscordID) VALUES (?, ?, ?)"
                        ).use { insertPlayerData ->
                            insertPlayerData.setString(1, uuid)
                            insertPlayerData.setString(2, nickname)
                            insertPlayerData.setString(3, "") // DiscordID 빈칸
                            insertPlayerData.executeUpdate()
                        }
                    }
                }
            }
        }
    }
}