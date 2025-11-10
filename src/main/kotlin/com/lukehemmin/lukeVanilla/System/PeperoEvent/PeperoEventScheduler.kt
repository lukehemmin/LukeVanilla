package com.lukehemmin.lukeVanilla.System.PeperoEvent

import com.lukehemmin.lukeVanilla.Main
import com.lukehemmin.lukeVanilla.System.Database.Database
import com.lukehemmin.lukeVanilla.System.Discord.DiscordBot
import net.dv8tion.jda.api.EmbedBuilder
import org.bukkit.Bukkit
import java.awt.Color
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.logging.Logger

/**
 * 빼빼로 이벤트 스케줄러
 * - 11월 12일 12시에 익명 메시지 및 교환권 자동 발송
 */
class PeperoEventScheduler(
    private val plugin: Main,
    private val repository: PeperoEventRepository,
    private val database: Database,
    private val discordBot: DiscordBot,
    private val logger: Logger
) {

    private val messageSendDateTime = parseDateTime(
        plugin.config.getString("pepero_event.message_send_datetime", "2025-11-12 12:00:00")
    )
    private var hasExecuted = false

    init {
        startScheduler()
    }

    /**
     * 스케줄러 시작 (1분마다 체크)
     */
    private fun startScheduler() {
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, Runnable {
            checkAndExecute()
        }, 20L * 60L, 20L * 60L) // 1분마다 (60초 = 1200틱)
    }

    /**
     * 시간 체크 및 실행
     */
    private fun checkAndExecute() {
        if (hasExecuted) return

        val now = LocalDateTime.now()
        // 11월 12일 12시가 되었는지 확인 (1분 오차 허용)
        if (now.isAfter(messageSendDateTime) && now.isBefore(messageSendDateTime.plusMinutes(2))) {
            logger.info("[PeperoEventScheduler] 메시지 및 교환권 자동 발송 시작...")
            hasExecuted = true

            sendAnonymousMessages()
            sendVouchers()
        }
    }

    /**
     * 익명 메시지 자동 발송
     */
    private fun sendAnonymousMessages() {
        try {
            // 득표 상위권 조회
            val topVoters = repository.getTopVoters(100) // 충분히 많이 가져오기

            topVoters.forEach { voteResult ->
                // 디스코드 ID 조회
                val discordId = database.getDiscordIDByUUID(voteResult.playerUuid)
                if (discordId == null) {
                    logger.warning("[PeperoEventScheduler] 디스코드 ID 없음: ${voteResult.playerName}")
                    return@forEach
                }

                // 익명 메시지 조회
                val messages = repository.getAnonymousMessages(voteResult.playerUuid)

                // 디스코드 DM 발송
                sendDiscordDM(discordId, voteResult.playerName, voteResult.voteCount, messages)
            }

            logger.info("[PeperoEventScheduler] 익명 메시지 발송 완료")
        } catch (e: Exception) {
            logger.severe("[PeperoEventScheduler] 익명 메시지 발송 중 오류: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * 디스코드 DM 발송
     */
    private fun sendDiscordDM(discordId: String, playerName: String, voteCount: Int, messages: List<String>) {
        try {
            val jda = discordBot.jda
            val user = jda.retrieveUserById(discordId).complete() ?: return

            val privateChannel = user.openPrivateChannel().complete()

            // Embed 생성
            val embed = EmbedBuilder()
                .setTitle("🍫 빼빼로 데이 2025 결과")
                .setDescription("안녕하세요, **$playerName**님!")
                .setColor(Color.ORANGE)
                .addField("🏆 득표수", "${voteCount}표", false)

            if (messages.isNotEmpty()) {
                embed.addField("💌 익명 메시지", "총 ${messages.size}개의 메시지를 받으셨습니다!", false)
                messages.forEachIndexed { index, message ->
                    embed.addField("메시지 ${index + 1}", message, false)
                }
            } else {
                embed.addField("💌 익명 메시지", "받은 메시지가 없습니다.", false)
            }

            embed.setFooter("LukeVanilla 빼빼로 데이 이벤트")
                .setTimestamp(java.time.Instant.now())

            privateChannel.sendMessageEmbeds(embed.build()).queue(
                { logger.info("[PeperoEventScheduler] DM 발송 성공: $playerName") },
                { error -> logger.warning("[PeperoEventScheduler] DM 발송 실패: $playerName - ${error.message}") }
            )
        } catch (e: Exception) {
            logger.warning("[PeperoEventScheduler] DM 발송 중 오류: $discordId - ${e.message}")
        }
    }

    /**
     * 교환권 자동 발송
     */
    private fun sendVouchers() {
        try {
            // 득표 상위권 조회 (1표 이상, 득표순 정렬)
            val topVoters = repository.getTopVoters(100)
            val top3Ranks = getTop3Ranks(topVoters)

            // 상위 3위까지만 교환권 발송
            top3Ranks.forEach { voteResult ->
                // 디스코드 ID 조회
                val discordId = database.getDiscordIDByUUID(voteResult.playerUuid)
                if (discordId == null) {
                    logger.warning("[PeperoEventScheduler] 디스코드 ID 없음: ${voteResult.playerName}")
                    return@forEach
                }

                // 미발송 교환권 가져오기
                val vouchers = repository.getUnsentVouchers(1)
                if (vouchers.isEmpty()) {
                    logger.warning("[PeperoEventScheduler] 교환권 부족! 추가 교환권이 필요합니다.")
                    return@forEach
                }

                val voucher = vouchers[0]

                // 교환권 DM 발송 (이미지 URL 전달)
                sendVoucherDM(discordId, voteResult.playerName, voteResult.voteCount, voucher.voucherName, voucher.imageUrl)

                // 발송 기록
                repository.markVoucherAsSent(voucher.id, voteResult.playerUuid, discordId)
            }

            logger.info("[PeperoEventScheduler] 교환권 발송 완료")
        } catch (e: Exception) {
            logger.severe("[PeperoEventScheduler] 교환권 발송 중 오류: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * 상위 3위 계산 (동점자 모두 포함)
     */
    private fun getTop3Ranks(voters: List<PeperoEventRepository.VoteResult>): List<PeperoEventRepository.VoteResult> {
        if (voters.isEmpty()) return emptyList()

        val sortedVoters = voters.sortedByDescending { it.voteCount }
        val uniqueVoteCounts = sortedVoters.map { it.voteCount }.distinct()

        if (uniqueVoteCounts.size < 3) {
            // 3위 미만이면 모두 포함
            return sortedVoters.filter { it.voteCount >= 1 }
        }

        val thirdPlaceVoteCount = uniqueVoteCounts[2]
        return sortedVoters.filter { it.voteCount >= thirdPlaceVoteCount && it.voteCount >= 1 }
    }

    /**
     * 교환권 DM 발송 (PNG 이미지 URL 사용)
     */
    private fun sendVoucherDM(discordId: String, playerName: String, voteCount: Int, voucherName: String, imageUrl: String) {
        try {
            val jda = discordBot.jda
            val user = jda.retrieveUserById(discordId).complete() ?: return

            val privateChannel = user.openPrivateChannel().complete()

            val embed = EmbedBuilder()
                .setTitle("🎁 빼빼로 데이 CU 교환권")
                .setDescription("축하합니다, **$playerName**님!")
                .setColor(Color.GREEN)
                .addField("🏆 득표수", "${voteCount}표", false)
                .addField("🎟️ 교환권", voucherName, false)
                .setImage(imageUrl) // PNG 이미지 URL 설정
                .addField("📌 안내", "위 교환권 이미지를 CU 편의점에서 사용하실 수 있습니다.", false)
                .setFooter("LukeVanilla 빼빼로 데이 이벤트")
                .setTimestamp(java.time.Instant.now())

            privateChannel.sendMessageEmbeds(embed.build()).queue(
                { logger.info("[PeperoEventScheduler] 교환권 DM 발송 성공: $playerName") },
                { error -> logger.warning("[PeperoEventScheduler] 교환권 DM 발송 실패: $playerName - ${error.message}") }
            )
        } catch (e: Exception) {
            logger.warning("[PeperoEventScheduler] 교환권 DM 발송 중 오류: $discordId - ${e.message}")
        }
    }

    /**
     * 문자열을 LocalDateTime으로 파싱
     */
    private fun parseDateTime(dateTimeStr: String?): LocalDateTime {
        return try {
            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            LocalDateTime.parse(dateTimeStr, formatter)
        } catch (e: Exception) {
            logger.warning("[PeperoEventScheduler] 날짜 파싱 실패: $dateTimeStr")
            LocalDateTime.now().plusDays(1) // 기본값: 내일
        }
    }
}
