package com.lukehemmin.lukeVanilla.System.DiscordAuth

import com.lukehemmin.lukeVanilla.System.Database.Database
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import java.util.*
import kotlin.random.Random

class PlayerJoinListener(private val database: Database) : Listener {

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player
        val uuid = player.uniqueId.toString()
        val nickname = player.name

        // Player_Data 테이블에서 UUID로 행을 찾음
        val connection = database.getConnection()
        val statement = connection.prepareStatement("SELECT * FROM Player_Data WHERE UUID = ?")
        statement.setString(1, uuid)
        val resultSet = statement.executeQuery()

        // 행이 없으면 새로운 행을 추가
        if (!resultSet.next()) {
            val authCode = generateAuthCode()
            val insertStatement = connection.prepareStatement(
                "INSERT INTO Player_Data (UUID, NickName, NameTag, DiscordID, IsAuth, AuthCode, First_Join) VALUES (?, ?, ?, ?, ?, ?, ?)"
            )
            insertStatement.setString(1, uuid)
            insertStatement.setString(2, nickname)
            insertStatement.setString(3, "") // NameTag 빈칸
            insertStatement.setString(4, "") // DiscordID 빈칸
            insertStatement.setInt(5, 0) // IsAuth 0
            insertStatement.setString(6, authCode) // AuthCode 6자리 코드
            insertStatement.setInt(7, 1) // First_Join 1
            insertStatement.executeUpdate()
        }

        resultSet.close()
        statement.close()
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