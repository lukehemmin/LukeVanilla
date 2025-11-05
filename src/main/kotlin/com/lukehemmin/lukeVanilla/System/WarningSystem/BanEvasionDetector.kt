package com.lukehemmin.lukeVanilla.System.WarningSystem

import com.lukehemmin.lukeVanilla.System.Database.Database
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.JDA
import org.bukkit.Bukkit
import org.bukkit.plugin.Plugin
import java.awt.Color
import java.text.SimpleDateFormat
import java.util.*
import java.util.logging.Level
import java.util.logging.Logger

/**
 * 차단 우회 시도 감지 및 처리 시스템
 *
 * 차단된 IP로 새 계정 인증 시 자동으로 감지하고 차단 처리합니다.
 */
class BanEvasionDetector(
    private val database: Database,
    private val jda: JDA,
    private val banManager: BanManager,
    private val plugin: Plugin
) {
    private val logger = Logger.getLogger(BanEvasionDetector::class.java.name)

    /**
     * 차단된 IP로 인증한 계정 정보
     */
    data class BannedAccountInfo(
        val uuid: String,
        val nickname: String,
        val discordId: String?,
        val ip: String,
        val banReason: String?,
        val bannedAt: String?
    )

    /**
     * 인증 완료 시 차단 우회 시도 확인
     *
     * @param newUuid 새로 인증된 마인크래프트 UUID
     * @param newNickname 새로 인증된 마인크래프트 닉네임
     * @param newDiscordId 새로 인증된 디스코드 ID
     * @param newDiscordName 새로 인증된 디스코드 이름#태그
     * @return 우회 시도 감지 여부
     */
    fun checkBanEvasion(
        newUuid: String,
        newNickname: String,
        newDiscordId: String,
        newDiscordName: String
    ): Boolean {
        try {
            // 1. 새 계정의 IP 조회
            val newAccountIp = getPlayerLatestIp(newUuid) ?: run {
                logger.warning("신규 계정 $newNickname ($newUuid)의 IP를 찾을 수 없습니다.")
                return false
            }

            // 2. 해당 IP가 차단된 IP인지 확인
            if (!isIpBanned(newAccountIp)) {
                // 차단된 IP가 아니면 정상
                return false
            }

            logger.info("차단된 IP ($newAccountIp)에서 인증 시도 감지: $newNickname ($newUuid)")

            // 3. 해당 IP로 차단된 기존 계정 조회
            val bannedAccounts = getBannedAccountsByIp(newAccountIp)
            if (bannedAccounts.isEmpty()) {
                logger.warning("차단된 IP $newAccountIp 이지만 해당 IP로 차단된 계정을 찾을 수 없습니다.")
                return false
            }

            // 가장 최근에 차단된 계정 선택
            val originalAccount = bannedAccounts.first()

            logger.warning("""
                ⚠️ 차단 우회 시도 감지!
                기존 계정: ${originalAccount.nickname} (${originalAccount.uuid})
                새 계정: $newNickname ($newUuid)
                공통 IP: $newAccountIp
            """.trimIndent())

            // 4. 새 계정과 디스코드 계정 차단
            processBanEvasion(
                originalAccount = originalAccount,
                newUuid = newUuid,
                newNickname = newNickname,
                newDiscordId = newDiscordId,
                newDiscordName = newDiscordName,
                sharedIp = newAccountIp
            )

            return true

        } catch (e: Exception) {
            logger.log(Level.SEVERE, "차단 우회 감지 중 오류 발생", e)
            return false
        }
    }

    /**
     * 차단 우회 시도 처리
     */
    private fun processBanEvasion(
        originalAccount: BannedAccountInfo,
        newUuid: String,
        newNickname: String,
        newDiscordId: String,
        newDiscordName: String,
        sharedIp: String
    ) {
        try {
            // 1. 새 마인크래프트 계정 차단 + 새 계정의 모든 IP 차단
            val banReason = "차단된 IP (${sharedIp})에서 우회 접속 시도 감지. 기존 차단 계정: ${originalAccount.nickname}"
            val uuid = UUID.fromString(newUuid)

            val banResult = banManager.banWithFullDetails(
                playerName = newNickname,
                playerUuid = uuid,
                reason = banReason,
                source = "차단 우회 감지 시스템"
            )

            if (!banResult.first) {
                logger.severe("신규 계정 $newNickname 차단 실패")
                return
            }

            logger.info("신규 계정 $newNickname 차단 완료. IP 차단: ${banResult.second}개")

            // 2. Banned_Account_Relations 테이블에 기록
            saveBanEvasionRecord(
                originalAccount = originalAccount,
                newUuid = newUuid,
                newNickname = newNickname,
                newDiscordId = newDiscordId,
                sharedIp = sharedIp,
                banReason = banReason
            )

            // 3. 관리자 알림 전송
            sendAdminNotifications(
                originalAccount = originalAccount,
                newUuid = newUuid,
                newNickname = newNickname,
                newDiscordId = newDiscordId,
                newDiscordName = newDiscordName,
                sharedIp = sharedIp,
                banResult = banResult
            )

        } catch (e: Exception) {
            logger.log(Level.SEVERE, "차단 우회 처리 중 오류 발생", e)
        }
    }

    /**
     * 플레이어의 최근 IP 조회
     */
    private fun getPlayerLatestIp(uuid: String): String? {
        return try {
            database.getConnection().use { connection ->
                val query = "SELECT Lastest_IP FROM Player_Data WHERE UUID = ?"
                connection.prepareStatement(query).use { statement ->
                    statement.setString(1, uuid)
                    statement.executeQuery().use { resultSet ->
                        if (resultSet.next()) {
                            resultSet.getString("Lastest_IP")
                        } else null
                    }
                }
            }
        } catch (e: Exception) {
            logger.log(Level.SEVERE, "플레이어 IP 조회 중 오류", e)
            null
        }
    }

    /**
     * IP가 차단되어 있는지 확인
     * Player_Data에서 IsBanned=1이고 해당 IP를 사용하는 계정이 있는지 확인
     */
    private fun isIpBanned(ip: String): Boolean {
        return try {
            database.getConnection().use { connection ->
                val query = """
                    SELECT COUNT(*) as count
                    FROM Player_Data pd
                    WHERE pd.IsBanned = 1
                      AND (pd.Lastest_IP = ?
                           OR pd.UUID IN (SELECT UUID FROM Connection_IP WHERE IP = ?))
                """.trimIndent()

                connection.prepareStatement(query).use { statement ->
                    statement.setString(1, ip)
                    statement.setString(2, ip)
                    statement.executeQuery().use { resultSet ->
                        if (resultSet.next()) {
                            return resultSet.getInt("count") > 0
                        }
                    }
                }
            }
            false
        } catch (e: Exception) {
            logger.log(Level.WARNING, "IP 차단 여부 확인 중 오류: $ip", e)
            false
        }
    }

    /**
     * 특정 IP로 차단된 모든 계정 조회
     */
    private fun getBannedAccountsByIp(ip: String): List<BannedAccountInfo> {
        val accounts = mutableListOf<BannedAccountInfo>()

        try {
            database.getConnection().use { connection ->
                // Connection_IP 테이블에서 해당 IP를 사용한 UUID 찾기
                val query = """
                    SELECT DISTINCT pd.UUID, pd.NickName, pd.DiscordID, pd.Lastest_IP
                    FROM Player_Data pd
                    WHERE pd.IsBanned = 1
                      AND (pd.Lastest_IP = ?
                           OR pd.UUID IN (SELECT UUID FROM Connection_IP WHERE IP = ?))
                    ORDER BY pd.UUID DESC
                """.trimIndent()

                connection.prepareStatement(query).use { statement ->
                    statement.setString(1, ip)
                    statement.setString(2, ip)
                    statement.executeQuery().use { resultSet ->
                        while (resultSet.next()) {
                            accounts.add(BannedAccountInfo(
                                uuid = resultSet.getString("UUID"),
                                nickname = resultSet.getString("NickName") ?: "Unknown",
                                discordId = resultSet.getString("DiscordID"),
                                ip = ip,
                                banReason = null,  // 필요시 추가 조회
                                bannedAt = null    // 필요시 추가 조회
                            ))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logger.log(Level.SEVERE, "차단된 계정 조회 중 오류", e)
        }

        return accounts
    }

    /**
     * 차단 우회 기록 저장
     */
    private fun saveBanEvasionRecord(
        originalAccount: BannedAccountInfo,
        newUuid: String,
        newNickname: String,
        newDiscordId: String,
        sharedIp: String,
        banReason: String
    ) {
        try {
            database.getConnection().use { connection ->
                val query = """
                    INSERT INTO Banned_Account_Relations
                    (original_uuid, original_nickname, original_discord_id, original_ban_reason,
                     new_uuid, new_nickname, new_discord_id, shared_ip, ban_reason)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent()

                connection.prepareStatement(query).use { statement ->
                    statement.setString(1, originalAccount.uuid)
                    statement.setString(2, originalAccount.nickname)
                    statement.setString(3, originalAccount.discordId)
                    statement.setString(4, originalAccount.banReason)
                    statement.setString(5, newUuid)
                    statement.setString(6, newNickname)
                    statement.setString(7, newDiscordId)
                    statement.setString(8, sharedIp)
                    statement.setString(9, banReason)
                    statement.executeUpdate()
                }

                logger.info("차단 우회 기록 저장 완료: $newNickname -> ${originalAccount.nickname}")
            }
        } catch (e: Exception) {
            logger.log(Level.SEVERE, "차단 우회 기록 저장 중 오류", e)
        }
    }

    /**
     * 관리자 알림 전송 (디스코드 + 게임)
     */
    private fun sendAdminNotifications(
        originalAccount: BannedAccountInfo,
        newUuid: String,
        newNickname: String,
        newDiscordId: String,
        newDiscordName: String,
        sharedIp: String,
        banResult: Triple<Boolean, Int, Boolean>
    ) {
        // 1. 디스코드 알림 (AdminChatChannel)
        sendDiscordNotification(
            originalAccount, newUuid, newNickname,
            newDiscordId, newDiscordName, sharedIp, banResult
        )

        // 2. 게임 내 알림 (온라인 관리자)
        sendIngameNotification(originalAccount, newNickname, sharedIp)
    }

    /**
     * 디스코드 알림 전송
     */
    private fun sendDiscordNotification(
        originalAccount: BannedAccountInfo,
        newUuid: String,
        newNickname: String,
        newDiscordId: String,
        newDiscordName: String,
        sharedIp: String,
        banResult: Triple<Boolean, Int, Boolean>
    ) {
        try {
            val adminChannelId = database.getSettingValue("AdminChatChannel") ?: run {
                logger.warning("AdminChatChannel 설정을 찾을 수 없습니다.")
                return
            }

            val channel = jda.getTextChannelById(adminChannelId) ?: run {
                logger.warning("AdminChatChannel을 찾을 수 없습니다: $adminChannelId")
                return
            }

            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
            val now = sdf.format(Date())

            val embed = EmbedBuilder().apply {
                setTitle("⚠️ 차단 우회 시도 감지!")
                setColor(Color.RED)
                setDescription("차단된 IP에서 새 계정 인증이 감지되어 자동으로 차단 처리되었습니다.")

                // 기존 차단 계정 정보
                addField(
                    "🔴 기존 차단 계정",
                    """
                    **마인크래프트:** ${originalAccount.nickname}
                    **UUID:** `${originalAccount.uuid}`
                    **디스코드:** ${originalAccount.discordId?.let { "<@$it>" } ?: "미등록"}
                    ${originalAccount.banReason?.let { "**차단 사유:** $it" } ?: ""}
                    """.trimIndent(),
                    false
                )

                // 우회 시도 계정 정보
                addField(
                    "🆕 우회 시도 계정",
                    """
                    **마인크래프트:** $newNickname
                    **UUID:** `$newUuid`
                    **디스코드:** <@$newDiscordId> ($newDiscordName)
                    **접속 IP:** `$sharedIp` ⚠️ (차단된 IP)
                    """.trimIndent(),
                    false
                )

                // 조치 결과
                val actionResult = StringBuilder()
                if (banResult.first) {
                    actionResult.append("✅ 새 마인크래프트 계정 차단 완료\n")
                    actionResult.append("✅ 차단된 IP: ${banResult.second}개\n")
                    if (banResult.third) {
                        actionResult.append("✅ 새 디스코드 계정 차단 완료\n")
                    } else {
                        actionResult.append("ℹ️ 디스코드 차단 불가 (DiscordID 미등록 또는 차단 실패)\n")
                    }
                } else {
                    actionResult.append("❌ 차단 처리 실패 - 수동 확인 필요\n")
                }

                addField("✅ 조치 완료", actionResult.toString(), false)

                setFooter("감지 일시: $now")
            }.build()

            channel.sendMessageEmbeds(embed).queue(
                { logger.info("디스코드 우회 감지 알림 전송 완료") },
                { error -> logger.log(Level.WARNING, "디스코드 알림 전송 실패", error) }
            )

        } catch (e: Exception) {
            logger.log(Level.SEVERE, "디스코드 알림 전송 중 오류", e)
        }
    }

    /**
     * 게임 내 알림 전송
     */
    private fun sendIngameNotification(
        originalAccount: BannedAccountInfo,
        newNickname: String,
        sharedIp: String
    ) {
        try {
            Bukkit.getScheduler().runTask(plugin, Runnable {
                val message = "§c⚠ §f차단 우회 감지: §e${originalAccount.nickname} §f-> §e$newNickname §7(IP: $sharedIp)"

                // 온라인 관리자들에게 알림
                Bukkit.getOnlinePlayers()
                    .filter { it.hasPermission("advancedwarnings.notify") }
                    .forEach { admin ->
                        admin.sendMessage(message)
                    }

                logger.info("게임 내 우회 감지 알림 전송 완료")
            })
        } catch (e: Exception) {
            logger.log(Level.SEVERE, "게임 내 알림 전송 중 오류", e)
        }
    }
}
