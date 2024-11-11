package com.lukehemmin.lukeVanilla.System.Discord

import com.lukehemmin.lukeVanilla.System.Database.Database
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerLoginEvent

class PlayerLoginListener(private val database: Database) : Listener {
    @EventHandler
    fun onPlayerLogin(event: PlayerLoginEvent) {
        val player = event.player
        val uuid = player.uniqueId.toString()
        val nickname = player.name

        // 행이 없을 경우 생성하고 AuthCode 반환
        val authCode = database.ensurePlayerAuth(uuid)

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

        database.getConnection().use { connection ->
            connection.prepareStatement("SELECT IsAuth FROM Player_Auth WHERE UUID = ?").use { checkAuth ->
                checkAuth.setString(1, uuid)
                checkAuth.executeQuery().use { authResult ->
                    if (authResult.next()) {
                        val isAuth = authResult.getInt("IsAuth")
                        if (isAuth == 0) {
                            val kickMessage = "서버에 접속하려면 디스코드에서 인증해야 합니다.\n\n" +
                                    "디스코드 서버에 들어와 인증채널에 아래 인증코드를 입력하세요.\n\n" +
                                    "인증코드: $authCode"
                            event.disallow(PlayerLoginEvent.Result.KICK_OTHER, kickMessage)
                        }
                    }
                }
            }
        }
    }
}