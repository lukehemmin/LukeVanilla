package com.lukehemmin.lukeVanilla.System.PeperoEvent

import com.lukehemmin.lukeVanilla.System.Database.Database
import java.security.MessageDigest
import java.sql.Timestamp
import java.time.LocalDateTime
import java.util.*

/**
 * 빼빼로 이벤트 데이터베이스 리포지토리
 */
class PeperoEventRepository(private val database: Database) {

    companion object {
        // 쿼리 문자열 상수 (재사용으로 성능 개선)
        private const val QUERY_CHECK_PARTICIPATED =
            "SELECT COUNT(*) as count FROM pepero_event_participation WHERE voter_uuid = ? AND token_used = TRUE"

        private const val QUERY_CHECK_RECEIVED_ITEM =
            "SELECT received FROM pepero_item_receive WHERE player_uuid = ?"

        private const val QUERY_CREATE_TOKEN =
            "INSERT INTO pepero_event_participation (voter_uuid, voter_name, voted_uuid, voted_name, one_time_token, token_used) VALUES (?, ?, '', '', ?, FALSE)"

        private const val QUERY_GET_PARTICIPATION =
            "SELECT voter_uuid, voter_name, voted_uuid, voted_name, anonymous_message, token_used, participated_at FROM pepero_event_participation WHERE one_time_token = ?"

        private const val QUERY_SUBMIT_VOTE =
            "UPDATE pepero_event_participation SET voted_uuid = ?, voted_name = ?, anonymous_message = ?, token_used = TRUE, participated_at = NOW() WHERE one_time_token = ? AND token_used = FALSE"

        private const val QUERY_INCREMENT_VOTE =
            "INSERT INTO pepero_event_votes (player_uuid, player_name, vote_count) VALUES (?, ?, 1) ON DUPLICATE KEY UPDATE vote_count = vote_count + 1"

        private const val QUERY_SEARCH_PLAYERS =
            "SELECT DISTINCT pd.UUID, pd.NickName, pn.Tag FROM Player_Data pd LEFT JOIN Player_NameTag pn ON pd.UUID = pn.UUID WHERE pd.NickName LIKE ? OR pn.Tag LIKE ? LIMIT 20"

        private const val QUERY_RECORD_ITEM =
            "INSERT INTO pepero_item_receive (player_uuid, player_name, pepero_type, received, received_at) VALUES (?, ?, ?, TRUE, NOW()) ON DUPLICATE KEY UPDATE received = TRUE, pepero_type = ?, received_at = NOW()"

        private const val QUERY_GET_TOP_VOTERS =
            "SELECT player_uuid, player_name, vote_count FROM pepero_event_votes WHERE vote_count >= 1 ORDER BY vote_count DESC, last_updated ASC LIMIT ?"

        private const val QUERY_GET_ANONYMOUS_MESSAGES =
            "SELECT anonymous_message FROM pepero_event_participation WHERE voted_uuid = ? AND token_used = TRUE AND anonymous_message IS NOT NULL AND anonymous_message != ''"

        private const val QUERY_ADD_VOUCHER =
            "INSERT INTO pepero_gift_vouchers (voucher_name, image_url) VALUES (?, ?)"

        private const val QUERY_GET_UNSENT_VOUCHERS =
            "SELECT id, voucher_name, image_url FROM pepero_gift_vouchers WHERE sent = FALSE ORDER BY added_at ASC LIMIT ?"

        private const val QUERY_MARK_VOUCHER_SENT =
            "UPDATE pepero_gift_vouchers SET sent = TRUE, sent_to_uuid = ?, sent_to_discord_id = ?, sent_at = NOW() WHERE id = ?"

        private const val QUERY_COUNT_UNSENT_VOUCHERS =
            "SELECT COUNT(*) as count FROM pepero_gift_vouchers WHERE sent = FALSE"
    }

    /**
     * 원타임 토큰 생성
     */
    fun generateOneTimeToken(playerUuid: String): String {
        val random = UUID.randomUUID().toString()
        val combined = "$playerUuid-$random-${System.currentTimeMillis()}"
        val md = MessageDigest.getInstance("SHA-256")
        val hash = md.digest(combined.toByteArray())
        return hash.joinToString("") { "%02x".format(it) }
    }

    /**
     * 웹 이벤트 참여 여부 확인
     */
    fun hasParticipated(playerUuid: String): Boolean {
        database.getConnection().use { connection ->
            connection.prepareStatement(QUERY_CHECK_PARTICIPATED).use { statement ->
                statement.setString(1, playerUuid)
                statement.executeQuery().use { rs ->
                    if (rs.next()) {
                        return rs.getInt("count") > 0
                    }
                }
            }
        }
        return false
    }

    /**
     * 아이템 수령 여부 확인
     */
    fun hasReceivedItem(playerUuid: String): Boolean {
        database.getConnection().use { connection ->
            connection.prepareStatement(QUERY_CHECK_RECEIVED_ITEM).use { statement ->
                statement.setString(1, playerUuid)
                statement.executeQuery().use { rs ->
                    if (rs.next()) {
                        return rs.getBoolean("received")
                    }
                }
            }
        }
        return false
    }

    /**
     * 원타임 토큰 저장 (투표 전 토큰 생성)
     */
    fun createOneTimeToken(voterUuid: String, voterName: String): String {
        val token = generateOneTimeToken(voterUuid)
        database.getConnection().use { connection ->
            connection.prepareStatement(QUERY_CREATE_TOKEN).use { statement ->
                statement.setString(1, voterUuid)
                statement.setString(2, voterName)
                statement.setString(3, token)
                statement.executeUpdate()
            }
        }
        return token
    }

    /**
     * 토큰으로 참여 기록 조회
     */
    fun getParticipationByToken(token: String): ParticipationRecord? {
        database.getConnection().use { connection ->
            connection.prepareStatement(QUERY_GET_PARTICIPATION).use { statement ->
                statement.setString(1, token)
                statement.executeQuery().use { rs ->
                    if (rs.next()) {
                        return ParticipationRecord(
                            voterUuid = rs.getString("voter_uuid"),
                            voterName = rs.getString("voter_name"),
                            votedUuid = rs.getString("voted_uuid") ?: "",
                            votedName = rs.getString("voted_name") ?: "",
                            anonymousMessage = rs.getString("anonymous_message"),
                            tokenUsed = rs.getBoolean("token_used"),
                            participatedAt = rs.getTimestamp("participated_at")?.toLocalDateTime()
                        )
                    }
                }
            }
        }
        return null
    }

    /**
     * 투표 제출 (토큰 사용하여 투표 완료)
     */
    fun submitVote(token: String, votedUuid: String, votedName: String, anonymousMessage: String?): Boolean {
        database.getConnection().use { connection ->
            connection.prepareStatement(QUERY_SUBMIT_VOTE).use { statement ->
                statement.setString(1, votedUuid)
                statement.setString(2, votedName)
                statement.setString(3, anonymousMessage)
                statement.setString(4, token)
                val updated = statement.executeUpdate()

                // 득표 집계 업데이트
                if (updated > 0) {
                    incrementVoteCount(votedUuid, votedName)
                    return true
                }
            }
        }
        return false
    }

    /**
     * 득표수 증가
     */
    private fun incrementVoteCount(playerUuid: String, playerName: String) {
        database.getConnection().use { connection ->
            connection.prepareStatement(QUERY_INCREMENT_VOTE).use { statement ->
                statement.setString(1, playerUuid)
                statement.setString(2, playerName)
                statement.executeUpdate()
            }
        }
    }

    /**
     * 플레이어 검색 (닉네임 또는 칭호)
     */
    fun searchPlayers(keyword: String): List<PlayerSearchResult> {
        val results = mutableListOf<PlayerSearchResult>()
        database.getConnection().use { connection ->
            connection.prepareStatement(QUERY_SEARCH_PLAYERS).use { statement ->
                val searchPattern = "%$keyword%"
                statement.setString(1, searchPattern)
                statement.setString(2, searchPattern)
                statement.executeQuery().use { rs ->
                    while (rs.next()) {
                        results.add(PlayerSearchResult(
                            uuid = rs.getString("UUID"),
                            nickname = rs.getString("NickName"),
                            tag = rs.getString("Tag")
                        ))
                    }
                }
            }
        }
        return results
    }

    /**
     * 빼빼로 아이템 수령 기록
     */
    fun recordItemReceive(playerUuid: String, playerName: String, peperoType: String): Boolean {
        database.getConnection().use { connection ->
            connection.prepareStatement(QUERY_RECORD_ITEM).use { statement ->
                statement.setString(1, playerUuid)
                statement.setString(2, playerName)
                statement.setString(3, peperoType)
                statement.setString(4, peperoType)
                return statement.executeUpdate() > 0
            }
        }
    }

    /**
     * 상위 득표자 조회 (1표 이상)
     */
    fun getTopVoters(limit: Int = 10): List<VoteResult> {
        val results = mutableListOf<VoteResult>()
        database.getConnection().use { connection ->
            connection.prepareStatement(QUERY_GET_TOP_VOTERS).use { statement ->
                statement.setInt(1, limit)
                statement.executeQuery().use { rs ->
                    while (rs.next()) {
                        results.add(VoteResult(
                            playerUuid = rs.getString("player_uuid"),
                            playerName = rs.getString("player_name"),
                            voteCount = rs.getInt("vote_count")
                        ))
                    }
                }
            }
        }
        return results
    }

    /**
     * 특정 플레이어가 받은 익명 메시지 조회
     */
    fun getAnonymousMessages(playerUuid: String): List<String> {
        val messages = mutableListOf<String>()
        database.getConnection().use { connection ->
            connection.prepareStatement(QUERY_GET_ANONYMOUS_MESSAGES).use { statement ->
                statement.setString(1, playerUuid)
                statement.executeQuery().use { rs ->
                    while (rs.next()) {
                        messages.add(rs.getString("anonymous_message"))
                    }
                }
            }
        }
        return messages
    }

    /**
     * 교환권 추가 (이미지 URL 포함)
     */
    fun addVoucher(voucherName: String, imageUrl: String): Boolean {
        database.getConnection().use { connection ->
            connection.prepareStatement(QUERY_ADD_VOUCHER).use { statement ->
                statement.setString(1, voucherName)
                statement.setString(2, imageUrl)
                return statement.executeUpdate() > 0
            }
        }
    }

    /**
     * 미발송 교환권 조회
     */
    fun getUnsentVouchers(limit: Int): List<Voucher> {
        val vouchers = mutableListOf<Voucher>()
        database.getConnection().use { connection ->
            connection.prepareStatement(QUERY_GET_UNSENT_VOUCHERS).use { statement ->
                statement.setInt(1, limit)
                statement.executeQuery().use { rs ->
                    while (rs.next()) {
                        vouchers.add(Voucher(
                            id = rs.getLong("id"),
                            voucherName = rs.getString("voucher_name"),
                            imageUrl = rs.getString("image_url")
                        ))
                    }
                }
            }
        }
        return vouchers
    }

    /**
     * 교환권 발송 기록
     */
    fun markVoucherAsSent(voucherId: Long, playerUuid: String, discordId: String): Boolean {
        database.getConnection().use { connection ->
            connection.prepareStatement(QUERY_MARK_VOUCHER_SENT).use { statement ->
                statement.setString(1, playerUuid)
                statement.setString(2, discordId)
                statement.setLong(3, voucherId)
                return statement.executeUpdate() > 0
            }
        }
    }

    /**
     * 미발송 교환권 개수 조회
     */
    fun getUnsentVoucherCount(): Int {
        database.getConnection().use { connection ->
            connection.prepareStatement(QUERY_COUNT_UNSENT_VOUCHERS).use { statement ->
                statement.executeQuery().use { rs ->
                    if (rs.next()) {
                        return rs.getInt("count")
                    }
                }
            }
        }
        return 0
    }

    // 데이터 클래스들
    data class ParticipationRecord(
        val voterUuid: String,
        val voterName: String,
        val votedUuid: String,
        val votedName: String,
        val anonymousMessage: String?,
        val tokenUsed: Boolean,
        val participatedAt: LocalDateTime?
    )

    data class PlayerSearchResult(
        val uuid: String,
        val nickname: String,
        val tag: String?
    )

    data class VoteResult(
        val playerUuid: String,
        val playerName: String,
        val voteCount: Int
    )

    data class Voucher(
        val id: Long,
        val voucherName: String,
        val imageUrl: String
    )
}
