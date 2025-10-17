package com.lukehemmin.lukeVanilla.System.WarningSystem

import com.lukehemmin.lukeVanilla.System.Database.Database
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.User
import org.bukkit.BanList
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.logging.Level
import java.util.logging.Logger

/**
 * 경고 시스템과 연동된 차단 관리 클래스
 * 인게임 차단과 디스코드 차단을 동기화하는 기능 제공
 */
class BanManager(private val database: Database, private val jda: JDA) {
    private val logger = Logger.getLogger(BanManager::class.java.name)

    /**
     * 플레이어를 인게임에서 차단 및 디스코드에서도 차단
     * 
     * @param uuid 차단할 플레이어 UUID
     * @param reason 차단 사유
     * @param source 차단 출처 (예: 경고 5회, 관리자 직접 차단 등)
     * @return 차단 성공 여부
     */
    fun banPlayer(uuid: UUID, reason: String, source: String): Boolean {
        try {
            database.getConnection().use { connection ->
                connection.autoCommit = false

                try {
                    // 1. Player_Data 테이블의 IsBanned 컬럼을 1로 변경
                    val updateBanQuery = "UPDATE Player_Data SET IsBanned = 1 WHERE UUID = ?"
                    connection.prepareStatement(updateBanQuery).use { statement ->
                        statement.setString(1, uuid.toString())
                        statement.executeUpdate()
                    }

                    // 2. 플레이어 정보 조회 (닉네임, 디스코드 ID, 최근 IP)
                    val selectPlayerQuery = "SELECT NickName, DiscordID, Lastest_IP FROM Player_Data WHERE UUID = ?"
                    var nickname: String? = null
                    var discordId: String? = null
                    var latestIp: String? = null

                    connection.prepareStatement(selectPlayerQuery).use { statement ->
                        statement.setString(1, uuid.toString())
                        statement.executeQuery().use { resultSet ->
                            if (resultSet.next()) {
                                nickname = resultSet.getString("NickName")
                                discordId = resultSet.getString("DiscordID")
                                latestIp = resultSet.getString("Lastest_IP")
                            }
                        }
                    }

                    // 3. Connection_IP 테이블에서 해당 UUID와 연결된 모든 IP 목록 조회
                    val selectIpsQuery = "SELECT DISTINCT IP FROM Connection_IP WHERE UUID = ?"
                    val ipList = mutableListOf<String>()
                    if (latestIp != null) {
                        ipList.add(latestIp!!)
                    }

                    connection.prepareStatement(selectIpsQuery).use { statement ->
                        statement.setString(1, uuid.toString())
                        statement.executeQuery().use { resultSet ->
                            while (resultSet.next()) {
                                val ip = resultSet.getString("IP")
                                if (!ipList.contains(ip)) {
                                    ipList.add(ip)
                                }
                            }
                        }
                    }

                    // 트랜잭션 커밋
                    connection.commit()

                    // 4. 인게임에서 플레이어 차단 (IP 포함)
                    var banSuccess = false
                    if (nickname != null) {
                        try {
                            val server = Bukkit.getServer()
                            val consoleCommandSender = Bukkit.getConsoleSender()
                            val offlinePlayer = Bukkit.getOfflinePlayer(uuid)

                            // 플레이어 이름으로 차단 (오프라인 플레이어도 작동)
                            val banCommand = "ban ${offlinePlayer.name ?: nickname} $reason (Source: $source)"
                            server.dispatchCommand(consoleCommandSender, banCommand)
                            logger.info("플레이어 ${nickname}님 차단 명령 실행. 사유: $reason")
                            banSuccess = true

                            // IP 기반 차단
                            for (ip in ipList) {
                                val ipBanReason = "UUID $uuid ($nickname) 차단 연동: $reason"
                                val banIpCommand = "ban-ip $ip $ipBanReason"
                                server.dispatchCommand(consoleCommandSender, banIpCommand)
                                logger.info("IP 주소 $ip 차단 명령 실행")
                            }
                        } catch (e: Exception) {
                            logger.log(Level.SEVERE, "차단 명령 실행 중 오류 발생", e)
                            banSuccess = false
                        }

                        // 온라인 상태라면 강제 퇴장
                        val player = Bukkit.getPlayer(uuid)
                        player?.kick(net.kyori.adventure.text.Component.text("당신은 차단되었습니다. 사유: $reason"))
                    }

                    // 5. 디스코드에서 사용자 차단 및 DM 전송
                    if (discordId != null) {
                        banUserFromDiscord(discordId, reason, nickname)
                    }

                    // 인게임 차단이 성공했으면 true 반환
                    return banSuccess
                } catch (e: Exception) {
                    connection.rollback()
                    logger.log(Level.SEVERE, "플레이어 차단 처리 중 오류 발생", e)
                    return false
                }
            }
        } catch (e: Exception) {
            logger.log(Level.SEVERE, "데이터베이스 연결 중 오류 발생", e)
            return false
        }
    }

    /**
     * 디스코드 ID로 플레이어를 차단
     * 
     * @param discordId 차단할 디스코드 ID
     * @param reason 차단 사유
     * @return 차단 성공 여부
     */
    fun banPlayerByDiscordId(discordId: String, reason: String): Boolean {
        try {
            database.getConnection().use { connection ->
                // 디스코드 ID로 UUID 조회
                val selectUuidQuery = "SELECT UUID FROM Player_Data WHERE DiscordID = ?"
                var uuid: UUID? = null

                connection.prepareStatement(selectUuidQuery).use { statement ->
                    statement.setString(1, discordId)
                    statement.executeQuery().use { resultSet ->
                        if (resultSet.next()) {
                            uuid = UUID.fromString(resultSet.getString("UUID"))
                        }
                    }
                }

                // UUID가 있으면 해당 플레이어 차단
                if (uuid != null) {
                    return banPlayer(uuid!!, reason, "디스코드 차단 연동")
                }
            }
        } catch (e: Exception) {
            logger.log(Level.SEVERE, "디스코드 ID로 플레이어 차단 중 오류 발생", e)
        }
        return false
    }

    /**
     * 디스코드에서 사용자 차단 및 DM 전송
     * 
     * @param discordId 차단할 디스코드 ID
     * @param reason 차단 사유
     * @param nickname 인게임 닉네임 (표시용)
     * @return 차단 성공 여부
     */
    fun banUserFromDiscord(discordId: String, reason: String, nickname: String?): Boolean {
        try {
            // 1. 서버 ID 조회
            var serverId: String? = null
            database.getConnection().use { connection ->
                val query = "SELECT Value FROM Settings WHERE Type = 'DiscordServerID'"
                connection.prepareStatement(query).use { statement ->
                    statement.executeQuery().use { resultSet ->
                        if (resultSet.next()) {
                            serverId = resultSet.getString("Value")
                        }
                    }
                }
            }

            if (serverId == null) {
                logger.log(Level.WARNING, "디스코드 서버 ID가 설정되지 않아 디스코드 차단을 진행할 수 없습니다.")
                return false
            }

            // 2. 서버 및 사용자 객체 얻기
            val guild: Guild? = jda.getGuildById(serverId!!)
            val user: User? = jda.retrieveUserById(discordId).complete()

            if (guild == null) {
                logger.log(Level.WARNING, "디스코드 서버를 찾을 수 없습니다. ID: $serverId")
                return false
            }

            if (user == null) {
                logger.log(Level.WARNING, "디스코드 사용자를 찾을 수 없습니다. ID: $discordId")
                return false
            }

            // 3. DM 전송 (동기 처리)
            val dmMessage = StringBuilder()
                .append("안녕하세요, 루크바닐라 서버에서 알립니다.\n\n")
                .append("귀하의 계정이 서버에서 차단되었습니다.\n")
                .append("인게임 닉네임: ${nickname ?: "알 수 없음"}\n")
                .append("차단 사유: $reason\n\n")
                .append("이의가 있으신 경우 공식 디스코드 서버의 문의 채널을 통해 문의해주세요.")
                .toString()

            try {
                val privateChannel = user.openPrivateChannel().complete()
                privateChannel.sendMessage(dmMessage).complete()
                logger.info("디스코드 사용자 ${user.name}에게 차단 DM을 성공적으로 전송했습니다.")
            } catch (e: Exception) {
                logger.log(Level.WARNING, "디스코드 사용자 ${user.name}에게 DM 전송 실패: ${e.message}", e)
                // DM 전송 실패는 차단 실패로 간주하지 않음 (DM 차단 설정 가능)
            }

            // 4. 디스코드 서버에서 차단 (동기 처리)
            try {
                guild.ban(user, 0, TimeUnit.SECONDS)
                    .reason("인게임 연동 차단: $reason")
                    .complete()  // 동기 처리로 차단 완료 대기
                logger.info("디스코드 사용자 ${user.name}을(를) 서버에서 차단했습니다.")
                return true
            } catch (e: Exception) {
                logger.log(Level.SEVERE, "디스코드 사용자 ${user.name} 차단 실패: ${e.message}", e)
                return false
            }
        } catch (e: Exception) {
            logger.log(Level.SEVERE, "디스코드 사용자 차단 처리 중 오류 발생", e)
            return false
        }
    }
    
    /**
     * 유저 정보를 바탕으로 인게임과 디스코드에서 동시에 차단
     * 
     * @param playerName 플레이어 닉네임
     * @param playerUuid 플레이어 UUID
     * @param reason 차단 사유
     * @param source 차단 출처 (ex: 경고 시스템 차단, 관리자 차단 등)
     * @return 차단 처리 결과 (성공 여부, 차단된 IP 수, 디스코드 차단 여부)
     */
    fun banWithFullDetails(playerName: String, playerUuid: UUID, reason: String, source: String): Triple<Boolean, Int, Boolean> {
        try {
            database.getConnection().use { connection ->
                connection.autoCommit = false
                
                try {
                    // 1. Player_Data 테이블의 IsBanned 컬럼을 1로 변경
                    val updateBanQuery = "UPDATE Player_Data SET IsBanned = 1 WHERE UUID = ?"
                    connection.prepareStatement(updateBanQuery).use { statement ->
                        statement.setString(1, playerUuid.toString())
                        statement.executeUpdate()
                    }
                    
                    // 2. 플레이어 정보 조회 (디스코드 ID, 최근 IP)
                    val selectPlayerQuery = "SELECT DiscordID, Lastest_IP FROM Player_Data WHERE UUID = ?"
                    var discordId: String? = null
                    var latestIp: String? = null
                    
                    connection.prepareStatement(selectPlayerQuery).use { statement ->
                        statement.setString(1, playerUuid.toString())
                        statement.executeQuery().use { resultSet ->
                            if (resultSet.next()) {
                                discordId = resultSet.getString("DiscordID")
                                latestIp = resultSet.getString("Lastest_IP")
                            }
                        }
                    }
                    
                    // 3. 모든 연결된 IP 목록 조회
                    val ipList = mutableListOf<String>()
                    if (!latestIp.isNullOrBlank()) {
                        ipList.add(latestIp!!)
                    }
                    
                    val selectIpsQuery = "SELECT DISTINCT IP FROM Connection_IP WHERE UUID = ?"
                    connection.prepareStatement(selectIpsQuery).use { statement ->
                        statement.setString(1, playerUuid.toString())
                        statement.executeQuery().use { resultSet ->
                            while (resultSet.next()) {
                                val ip = resultSet.getString("IP")
                                if (!ipList.contains(ip)) {
                                    ipList.add(ip)
                                }
                            }
                        }
                    }
                    
                    // 트랜잭션 커밋
                    connection.commit()

                    // 4. 인게임에서 플레이어 차단 (IP 포함)
                    var inGameBanSuccess = false
                    if (playerName.isNotBlank()) {
                        try {
                            val server = Bukkit.getServer()
                            val consoleCommandSender = Bukkit.getConsoleSender()
                            val offlinePlayer = Bukkit.getOfflinePlayer(playerUuid)

                            // 플레이어 이름으로 차단 (오프라인 플레이어도 작동)
                            val banCommand = "ban ${offlinePlayer.name ?: playerName} $reason (Source: $source)"
                            server.dispatchCommand(consoleCommandSender, banCommand)
                            logger.info("플레이어 ${playerName}님 차단 명령 실행. 사유: $reason")
                            inGameBanSuccess = true

                            // IP 기반 차단
                            for (ip in ipList) {
                                val ipBanReason = "UUID $playerUuid ($playerName) 차단 연동: $reason"
                                val banIpCommand = "ban-ip $ip $ipBanReason"
                                server.dispatchCommand(consoleCommandSender, banIpCommand)
                                logger.info("IP 주소 $ip 차단 명령 실행")
                            }
                        } catch (e: Exception) {
                            logger.log(Level.SEVERE, "차단 명령 실행 중 오류 발생", e)
                            inGameBanSuccess = false
                        }

                        // 온라인 상태라면 강제 퇴장
                        val player = Bukkit.getPlayer(playerUuid)
                        player?.kick(net.kyori.adventure.text.Component.text("당신은 차단되었습니다. 사유: $reason"))
                    }
                    
                    // 5. 디스코드에서 사용자 차단 및 DM 전송
                    var discordBanned = false
                    if (!discordId.isNullOrBlank()) {
                        discordBanned = banUserFromDiscord(discordId!!, reason, playerName)
                    }

                    // 인게임 차단 성공 여부와 함께 반환
                    return Triple(inGameBanSuccess, ipList.size, discordBanned)
                } catch (e: Exception) {
                    connection.rollback()
                    logger.log(Level.SEVERE, "플레이어 차단 처리 중 오류 발생", e)
                    return Triple(false, 0, false)
                }
            }
        } catch (e: Exception) {
            logger.log(Level.SEVERE, "데이터베이스 연결 중 오류 발생", e)
            return Triple(false, 0, false)
        }
    }

    /**
     * 디스코드 차단 이벤트를 처리하여 인게임에도 차단 적용
     * 
     * @param discordId 차단된 디스코드 ID
     * @param reason 차단 사유
     */
    fun handleDiscordBan(discordId: String, reason: String) {
        banPlayerByDiscordId(discordId, reason)
    }
}
