package com.lukehemmin.lukeVanilla.System.DiscordAuth

import com.lukehemmin.lukeVanilla.System.Database.Database
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerLoginEvent

class PlayerLoginListener(private val database: Database) : Listener {

    @EventHandler
    fun onPlayerLogin(event: PlayerLoginEvent) {
        val player = event.player
        val uuid = player.uniqueId.toString()

        val connection = database.getConnection()
        val checkAuth = connection.prepareStatement("SELECT IsAuth, AuthCode FROM Player_Auth WHERE UUID = ?")
        checkAuth.setString(1, uuid)
        val authResult = checkAuth.executeQuery()

        if (authResult.next()) {
            val isAuth = authResult.getInt("IsAuth")
            if (isAuth == 0) {
                val authCode = authResult.getString("AuthCode")
                val kickMessage = "서버에 접속하려면 디스코드에서 인증해야 합니다.\n\n" +
                        "디스코드 서버에 들어와 인증채널에 아래 인증코드를 입력하세요.\n\n" +
                        "인증코드: $authCode"
                event.disallow(PlayerLoginEvent.Result.KICK_OTHER, kickMessage)
            }
        }

        authResult.close()
        checkAuth.close()
        connection.close()
    }
}