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

        // 행이 없을 경우 생성하고 AuthCode 반환
        val authCode = database.ensurePlayerAuth(uuid)

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