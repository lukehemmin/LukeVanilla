package com.lukehemmin.lukeVanilla.System.Discord

import com.lukehemmin.lukeVanilla.System.Database.Database
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerLoginEvent
import java.sql.SQLException

class PlayerLoginListener(private val database: Database) : Listener {
    @EventHandler
    fun onPlayerLogin(event: PlayerLoginEvent) {
        val player = event.player
        val uuid = player.uniqueId.toString()
        val nickname = player.name

        // 행이 없을 경우 생성하고 AuthCode 반환
        val authCode = database.ensurePlayerAuth(uuid)

        val ipAddress = event.address.hostAddress
        
        database.getConnection().use { connection ->
            // Player_Data 테이블에서 UUID로 행을 찾음
            connection.prepareStatement("SELECT * FROM Player_Data WHERE UUID = ?").use { checkPlayerData ->
                checkPlayerData.setString(1, uuid)
                checkPlayerData.executeQuery().use { playerDataResult ->
                    if (playerDataResult.next()) {
                        // 기존 행이 있으면 NickName과 Lastest_IP를 업데이트
                        connection.prepareStatement("UPDATE Player_Data SET NickName = ?, Lastest_IP = ? WHERE UUID = ?").use { updateData ->
                            updateData.setString(1, nickname)
                            updateData.setString(2, ipAddress)
                            updateData.setString(3, uuid)
                            updateData.executeUpdate()
                        }
                        
                        // 마지막 접속 IP와 현재 IP를 비교하여 다른 경우에만 Connection_IP 테이블에 추가
                        val lastIp = playerDataResult.getString("Lastest_IP")
                        if (lastIp != ipAddress) {
                            // 기존과 다른 IP인 경우에만 새 레코드 추가
                            recordNewIpConnection(connection, uuid, nickname, ipAddress)
                        }
                    } else {
                        // 행이 없으면 새로운 행을 추가 (아이피 주소 포함)
                        connection.prepareStatement(
                            "INSERT INTO Player_Data (UUID, NickName, DiscordID, Lastest_IP) VALUES (?, ?, ?, ?)"
                        ).use { insertPlayerData ->
                            insertPlayerData.setString(1, uuid)
                            insertPlayerData.setString(2, nickname)
                            insertPlayerData.setString(3, "") // DiscordID 빈칸
                            insertPlayerData.setString(4, ipAddress) // IP 주소 추가
                            insertPlayerData.executeUpdate()
                        }
                        
                        // 신규 유저이므로 무조건 IP 기록 추가
                        recordNewIpConnection(connection, uuid, nickname, ipAddress)
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
    
    /**
     * Connection_IP 테이블에 새로운 IP 접속 기록을 추가합니다.
     * 
     * @param connection 데이터베이스 연결 객체
     * @param uuid 플레이어 UUID
     * @param nickname 플레이어 닉네임
     * @param ipAddress 접속한 IP 주소
     */
    private fun recordNewIpConnection(connection: java.sql.Connection, uuid: String, nickname: String, ipAddress: String) {
        try {
            connection.prepareStatement(
                "INSERT INTO Connection_IP (UUID, NickName, IP) VALUES (?, ?, ?)"
            ).use { insertIpRecord ->
                insertIpRecord.setString(1, uuid)
                insertIpRecord.setString(2, nickname)
                insertIpRecord.setString(3, ipAddress)
                insertIpRecord.executeUpdate()
            }
        } catch (e: SQLException) {
            // 로그 기록이 실패해도 유저 접속은 막지 않기 위해 예외 처리
            System.err.println("IP 접속 기록 저장 중 오류 발생: ${e.message}")
        }
    }
}