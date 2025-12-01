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
                    // Upsert (Insert or Update)를 사용하여 원자적 처리
                    val upsertQuery = """
                        INSERT INTO Player_Data (UUID, NickName, DiscordID)
                        VALUES (?, ?, ?)
                        ON DUPLICATE KEY UPDATE NickName = ?
                    """
                    connection.prepareStatement(upsertQuery).use { stmt ->
                        stmt.setString(1, uuid)
                        stmt.setString(2, nickname)
                        stmt.setString(3, "") // 신규 생성 시 기본값
                        stmt.setString(4, nickname) // 존재 시 업데이트할 닉네임
                        stmt.executeUpdate()
                    }
                }
            } catch (e: Exception) {
                plugin.logger.warning("플레이어 접속 DB 처리 중 오류 발생: ${e.message}")
            }
        })
    }
}