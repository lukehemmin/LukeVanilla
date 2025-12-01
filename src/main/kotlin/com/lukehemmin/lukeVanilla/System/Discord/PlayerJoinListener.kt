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

        // 비동기 작업으로 변경하여 메인 스레드 블로킹 방지
        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
            // Discord 권한 확인 및 부여
            try {
                discordRoleManager.checkAndGrantAuthRole(java.util.UUID.fromString(uuid), nickname)
            } catch (e: Exception) {
                plugin.logger.warning("Discord 권한 부여 중 오류 발생: ${e.message}")
            }

            // DB 작업
            try {
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
            } catch (e: Exception) {
                plugin.logger.warning("플레이어 접속 DB 처리 중 오류 발생: ${e.message}")
            }
        })
    }
}