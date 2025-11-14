package com.lukehemmin.lukeVanilla.System.PeperoGifticon

import com.lukehemmin.lukeVanilla.System.Database.Database
import java.sql.Timestamp
import java.time.LocalDateTime

/**
 * 빼빼로 기프티콘 보상 시스템 Repository
 */
class PeperoGifticonRepository(private val database: Database) {

    /**
     * 수령 대상자 데이터 클래스
     */
    data class Recipient(
        val uuid: String,
        val playerName: String,
        val discordId: String?,
        val hasReceived: Boolean,
        val gifticonType: String?,
        val receivedAt: LocalDateTime?,
        val addedAt: LocalDateTime,
        val addedBy: String?
    )

    /**
     * 기프티콘 코드 데이터 클래스
     */
    data class GifticonCode(
        val id: Long,
        val gifticonType: String,
        val imageUrl: String,
        val isUsed: Boolean,
        val usedByUuid: String?,
        val usedByDiscordId: String?,
        val usedAt: LocalDateTime?,
        val addedAt: LocalDateTime
    )

    /**
     * 닉네임으로 플레이어 UUID와 DiscordID 조회 (Player_Data 테이블)
     */
    fun getPlayerInfoByNickname(nickname: String): Pair<String, String?>? {
        return try {
            database.getConnection().use { connection ->
                val query = "SELECT UUID, DiscordID FROM Player_Data WHERE NickName = ?"
                connection.prepareStatement(query).use { statement ->
                    statement.setString(1, nickname)
                    statement.executeQuery().use { rs ->
                        if (rs.next()) {
                            val uuid = rs.getString("UUID")
                            val discordId = rs.getString("DiscordID")
                            Pair(uuid, discordId)
                        } else {
                            null
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 수령 대상자 추가
     */
    fun addRecipient(uuid: String, playerName: String, discordId: String?, addedBy: String): Boolean {
        return try {
            database.getConnection().use { connection ->
                val query = """
                    INSERT INTO pepero_gifticon_recipients
                    (uuid, player_name, discord_id, has_received, added_by)
                    VALUES (?, ?, ?, FALSE, ?)
                    ON DUPLICATE KEY UPDATE
                    player_name = VALUES(player_name),
                    discord_id = VALUES(discord_id),
                    added_by = VALUES(added_by)
                """.trimIndent()

                connection.prepareStatement(query).use { statement ->
                    statement.setString(1, uuid)
                    statement.setString(2, playerName)
                    statement.setString(3, discordId)
                    statement.setString(4, addedBy)
                    statement.executeUpdate() > 0
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * 수령 대상자인지 확인
     */
    fun isRecipient(uuid: String): Boolean {
        return try {
            database.getConnection().use { connection ->
                val query = "SELECT 1 FROM pepero_gifticon_recipients WHERE uuid = ?"
                connection.prepareStatement(query).use { statement ->
                    statement.setString(1, uuid)
                    statement.executeQuery().use { rs ->
                        rs.next()
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * 이미 기프티콘을 받았는지 확인
     */
    fun hasReceivedGifticon(uuid: String): Boolean {
        return try {
            database.getConnection().use { connection ->
                val query = "SELECT has_received FROM pepero_gifticon_recipients WHERE uuid = ?"
                connection.prepareStatement(query).use { statement ->
                    statement.setString(1, uuid)
                    statement.executeQuery().use { rs ->
                        if (rs.next()) {
                            rs.getBoolean("has_received")
                        } else {
                            false
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Discord ID로 수령 대상자 조회
     */
    fun getRecipientByDiscordId(discordId: String): Recipient? {
        return try {
            database.getConnection().use { connection ->
                val query = "SELECT * FROM pepero_gifticon_recipients WHERE discord_id = ?"
                connection.prepareStatement(query).use { statement ->
                    statement.setString(1, discordId)
                    statement.executeQuery().use { rs ->
                        if (rs.next()) {
                            Recipient(
                                uuid = rs.getString("uuid"),
                                playerName = rs.getString("player_name"),
                                discordId = rs.getString("discord_id"),
                                hasReceived = rs.getBoolean("has_received"),
                                gifticonType = rs.getString("gifticon_type"),
                                receivedAt = rs.getTimestamp("received_at")?.toLocalDateTime(),
                                addedAt = rs.getTimestamp("added_at").toLocalDateTime(),
                                addedBy = rs.getString("added_by")
                            )
                        } else {
                            null
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 사용 가능한 기프티콘 코드 가져오기 (동시성 제어 포함)
     */
    fun getAvailableGifticonCode(gifticonType: String): GifticonCode? {
        return try {
            database.getConnection().use { connection ->
                connection.autoCommit = false

                try {
                    // FOR UPDATE로 행 잠금
                    val selectQuery = """
                        SELECT * FROM pepero_gifticon_codes
                        WHERE gifticon_type = ? AND is_used = FALSE
                        ORDER BY id ASC
                        LIMIT 1
                        FOR UPDATE
                    """.trimIndent()

                    val code = connection.prepareStatement(selectQuery).use { statement ->
                        statement.setString(1, gifticonType)
                        statement.executeQuery().use { rs ->
                            if (rs.next()) {
                                GifticonCode(
                                    id = rs.getLong("id"),
                                    gifticonType = rs.getString("gifticon_type"),
                                    imageUrl = rs.getString("image_url"),
                                    isUsed = rs.getBoolean("is_used"),
                                    usedByUuid = rs.getString("used_by_uuid"),
                                    usedByDiscordId = rs.getString("used_by_discord_id"),
                                    usedAt = rs.getTimestamp("used_at")?.toLocalDateTime(),
                                    addedAt = rs.getTimestamp("added_at").toLocalDateTime()
                                )
                            } else {
                                null
                            }
                        }
                    }

                    connection.commit()
                    code
                } catch (e: Exception) {
                    connection.rollback()
                    throw e
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 기프티콘 사용 처리 (트랜잭션)
     */
    fun markGifticonAsUsed(
        codeId: Long,
        uuid: String,
        discordId: String,
        gifticonType: String
    ): Boolean {
        return try {
            database.getConnection().use { connection ->
                connection.autoCommit = false

                try {
                    // 1. 기프티콘 코드를 사용됨으로 표시
                    val updateCodeQuery = """
                        UPDATE pepero_gifticon_codes
                        SET is_used = TRUE,
                            used_by_uuid = ?,
                            used_by_discord_id = ?,
                            used_at = ?
                        WHERE id = ? AND is_used = FALSE
                    """.trimIndent()

                    val codeUpdated = connection.prepareStatement(updateCodeQuery).use { statement ->
                        statement.setString(1, uuid)
                        statement.setString(2, discordId)
                        statement.setTimestamp(3, Timestamp.valueOf(LocalDateTime.now()))
                        statement.setLong(4, codeId)
                        statement.executeUpdate() > 0
                    }

                    if (!codeUpdated) {
                        connection.rollback()
                        return false
                    }

                    // 2. 수령자 정보 업데이트
                    val updateRecipientQuery = """
                        UPDATE pepero_gifticon_recipients
                        SET has_received = TRUE,
                            gifticon_type = ?,
                            received_at = ?
                        WHERE uuid = ? AND has_received = FALSE
                    """.trimIndent()

                    val recipientUpdated = connection.prepareStatement(updateRecipientQuery).use { statement ->
                        statement.setString(1, gifticonType)
                        statement.setTimestamp(2, Timestamp.valueOf(LocalDateTime.now()))
                        statement.setString(3, uuid)
                        statement.executeUpdate() > 0
                    }

                    if (!recipientUpdated) {
                        connection.rollback()
                        return false
                    }

                    connection.commit()
                    true
                } catch (e: Exception) {
                    connection.rollback()
                    throw e
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * 기프티콘 코드 등록
     */
    fun addGifticonCode(gifticonType: String, imageUrl: String): Boolean {
        return try {
            database.getConnection().use { connection ->
                val query = """
                    INSERT INTO pepero_gifticon_codes
                    (gifticon_type, image_url, is_used)
                    VALUES (?, ?, FALSE)
                """.trimIndent()

                connection.prepareStatement(query).use { statement ->
                    statement.setString(1, gifticonType)
                    statement.setString(2, imageUrl)
                    statement.executeUpdate() > 0
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * 특정 종류의 남은 기프티콘 개수 조회
     */
    fun getAvailableGifticonCount(gifticonType: String): Int {
        return try {
            database.getConnection().use { connection ->
                val query = """
                    SELECT COUNT(*) as count
                    FROM pepero_gifticon_codes
                    WHERE gifticon_type = ? AND is_used = FALSE
                """.trimIndent()

                connection.prepareStatement(query).use { statement ->
                    statement.setString(1, gifticonType)
                    statement.executeQuery().use { rs ->
                        if (rs.next()) {
                            rs.getInt("count")
                        } else {
                            0
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            0
        }
    }

    /**
     * 모든 수령 대상자 목록 조회
     */
    fun getAllRecipients(): List<Recipient> {
        return try {
            database.getConnection().use { connection ->
                val query = "SELECT * FROM pepero_gifticon_recipients ORDER BY added_at DESC"
                connection.prepareStatement(query).use { statement ->
                    statement.executeQuery().use { rs ->
                        val recipients = mutableListOf<Recipient>()
                        while (rs.next()) {
                            recipients.add(
                                Recipient(
                                    uuid = rs.getString("uuid"),
                                    playerName = rs.getString("player_name"),
                                    discordId = rs.getString("discord_id"),
                                    hasReceived = rs.getBoolean("has_received"),
                                    gifticonType = rs.getString("gifticon_type"),
                                    receivedAt = rs.getTimestamp("received_at")?.toLocalDateTime(),
                                    addedAt = rs.getTimestamp("added_at").toLocalDateTime(),
                                    addedBy = rs.getString("added_by")
                                )
                            )
                        }
                        recipients
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * 수령 대상자 제거
     */
    fun removeRecipient(uuid: String): Boolean {
        return try {
            database.getConnection().use { connection ->
                val query = "DELETE FROM pepero_gifticon_recipients WHERE uuid = ?"
                connection.prepareStatement(query).use { statement ->
                    statement.setString(1, uuid)
                    statement.executeUpdate() > 0
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * 수령 상태 초기화 (잘못 받은 경우)
     */
    fun resetRecipientStatus(uuid: String): Boolean {
        return try {
            database.getConnection().use { connection ->
                val query = """
                    UPDATE pepero_gifticon_recipients
                    SET has_received = FALSE,
                        gifticon_type = NULL,
                        received_at = NULL
                    WHERE uuid = ?
                """.trimIndent()

                connection.prepareStatement(query).use { statement ->
                    statement.setString(1, uuid)
                    statement.executeUpdate() > 0
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
