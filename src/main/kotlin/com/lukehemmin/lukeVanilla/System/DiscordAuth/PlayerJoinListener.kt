package com.lukehemmin.lukeVanilla.System.DiscordAuth

import com.lukehemmin.lukeVanilla.System.Database.Database
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent

class PlayerJoinListener(private val database: Database) : Listener {

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player
        val uuid = player.uniqueId.toString()
        val nickname = player.name

        val connection = database.getConnection()

        // Player_Data 테이블에서 UUID로 행을 찾음
        val checkPlayerData = connection.prepareStatement("SELECT * FROM Player_Data WHERE UUID = ?")
        checkPlayerData.setString(1, uuid)
        val playerDataResult = checkPlayerData.executeQuery()

        // 행이 없으면 새로운 행을 추가
        if (!playerDataResult.next()) {
            // Player_Data 테이블에 행 추가
            val insertPlayerData = connection.prepareStatement(
                "INSERT INTO Player_Data (UUID, NickName, DiscordID) VALUES (?, ?, ?)"
            )
            insertPlayerData.setString(1, uuid)
            insertPlayerData.setString(2, nickname)
            insertPlayerData.setString(3, "") // DiscordID 빈칸
            insertPlayerData.executeUpdate()
            insertPlayerData.close()

            // Player_Auth 테이블에 행 추가
            val authCode = generateAuthCode()
            val insertPlayerAuth = connection.prepareStatement(
                "INSERT INTO Player_Auth (UUID, IsAuth, AuthCode, IsFirst) VALUES (?, ?, ?, ?)"
            )
            insertPlayerAuth.setString(1, uuid)
            insertPlayerAuth.setInt(2, 0) // IsAuth 0
            insertPlayerAuth.setString(3, authCode)
            insertPlayerAuth.setInt(4, 1) // IsFirst 1
            insertPlayerAuth.executeUpdate()
            insertPlayerAuth.close()

            // Player_NameTag 테이블에 행 추가
            val insertPlayerNameTag = connection.prepareStatement(
                "INSERT INTO Player_NameTag (UUID, Tag) VALUES (?, ?)"
            )
            insertPlayerNameTag.setString(1, uuid)
            insertPlayerNameTag.setString(2, "") // Tag 빈칸
            insertPlayerNameTag.executeUpdate()
            insertPlayerNameTag.close()
        }

        playerDataResult.close()
        checkPlayerData.close()
        connection.close()
    }

    // 대문자 A~Z와 숫자를 포함한 6자리 코드 생성
    private fun generateAuthCode(): String {
        val chars = ('A'..'Z') + ('0'..'9')
        return (1..6)
            .map { chars.random() }
            .joinToString("")
    }
}