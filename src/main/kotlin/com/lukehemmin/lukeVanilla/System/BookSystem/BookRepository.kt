package com.lukehemmin.lukeVanilla.System.BookSystem

import com.lukehemmin.lukeVanilla.System.Database.Database
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

/**
 * 책 시스템 데이터베이스 리포지토리
 */
class BookRepository(private val database: Database) {

    private val json = Json { 
        ignoreUnknownKeys = true
        prettyPrint = false
    }

    // ================================
    // 책 관련 메소드들
    // ================================

    /**
     * 새로운 책을 데이터베이스에 저장
     */
    fun saveBook(book: MinecraftBookInfo, playerUuid: String, season: String?): Long? {
        val bookContent = BookContent(
            pages = book.pages.mapIndexed { index, content ->
                BookPage(pageNumber = index + 1, content = content)
            }
        )

        database.getConnection().use { connection ->
            val sql = """
                INSERT INTO books (uuid, title, content, page_count, is_signed, season)
                VALUES (?, ?, ?, ?, ?, ?)
            """.trimIndent()

            val statement = connection.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)
            statement.setString(1, playerUuid)
            statement.setString(2, book.title)
            statement.setString(3, json.encodeToString(bookContent))
            statement.setInt(4, book.pages.size)
            statement.setBoolean(5, book.author != null) // 작가가 있으면 서명된 책
            statement.setString(6, season)

            val affectedRows = statement.executeUpdate()
            if (affectedRows > 0) {
                val generatedKeys = statement.generatedKeys
                if (generatedKeys.next()) {
                    return generatedKeys.getLong(1)
                }
            }
        }
        return null
    }

    /**
     * 마인크래프트 책 정보로 기존 책을 업데이트
     */
    fun updateExistingBook(bookId: Long, book: MinecraftBookInfo, playerUuid: String): Boolean {
        val bookContent = BookContent(
            pages = book.pages.mapIndexed { index, content ->
                BookPage(pageNumber = index + 1, content = content)
            }
        )

        database.getConnection().use { connection ->
            val sql = """
                UPDATE books 
                SET title = ?, content = ?, page_count = ?, is_signed = ?, updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND uuid = ?
            """.trimIndent()

            val statement = connection.prepareStatement(sql)
            statement.setString(1, book.title)
            statement.setString(2, json.encodeToString(bookContent))
            statement.setInt(3, book.pages.size)
            statement.setBoolean(4, book.author != null)
            statement.setLong(5, bookId)
            statement.setString(6, playerUuid)

            return statement.executeUpdate() > 0
        }
    }

    /**
     * 책 정보 업데이트
     */
    fun updateBook(bookId: Long, title: String, content: BookContent, playerUuid: String): Boolean {
        database.getConnection().use { connection ->
            val sql = """
                UPDATE books 
                SET title = ?, content = ?, page_count = ?, updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND uuid = ?
            """.trimIndent()

            val statement = connection.prepareStatement(sql)
            statement.setString(1, title)
            statement.setString(2, json.encodeToString(content))
            statement.setInt(3, content.pages.size)
            statement.setLong(4, bookId)
            statement.setString(5, playerUuid)

            return statement.executeUpdate() > 0
        }
    }

    /**
     * 책 공개 설정 변경
     */
    fun setBookPublic(bookId: Long, isPublic: Boolean, playerUuid: String): Boolean {
        database.getConnection().use { connection ->
            val sql = """
                UPDATE books 
                SET is_public = ?, updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND uuid = ?
            """.trimIndent()

            val statement = connection.prepareStatement(sql)
            statement.setBoolean(1, isPublic)
            statement.setLong(2, bookId)
            statement.setString(3, playerUuid)

            return statement.executeUpdate() > 0
        }
    }

    /**
     * 책 아카이브 설정
     */
    fun archiveBook(bookId: Long, playerUuid: String): Boolean {
        database.getConnection().use { connection ->
            val sql = """
                UPDATE books 
                SET is_archived = TRUE, updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND uuid = ?
            """.trimIndent()

            val statement = connection.prepareStatement(sql)
            statement.setLong(1, bookId)
            statement.setString(2, playerUuid)

            return statement.executeUpdate() > 0
        }
    }

    /**
     * 특정 플레이어의 책 목록 조회
     */
    fun getPlayerBooks(
        playerUuid: String, 
        page: Int = 1, 
        size: Int = 20,
        publicOnly: Boolean = false
    ): Pair<List<BookData>, Long> {
        val offset = (page - 1) * size
        
        database.getConnection().use { connection ->
            val publicFilter = if (publicOnly) "AND b.is_public = TRUE" else ""
            
            // 총 개수 조회
            val countSql = """
                SELECT COUNT(*) FROM books b
                WHERE b.uuid = ? $publicFilter
            """.trimIndent()
            
            val countStatement = connection.prepareStatement(countSql)
            countStatement.setString(1, playerUuid)
            val countResult = countStatement.executeQuery()
            val totalCount = if (countResult.next()) countResult.getLong(1) else 0L

            // 책 목록 조회 (플레이어 닉네임 포함) - 콜레이션 충돌 방지
            val sql = """
                SELECT b.*, pd.NickName as player_name 
                FROM books b
                LEFT JOIN Player_Data pd ON b.uuid COLLATE utf8mb4_general_ci = pd.UUID COLLATE utf8mb4_general_ci
                WHERE b.uuid = ? $publicFilter
                ORDER BY b.created_at DESC
                LIMIT ? OFFSET ?
            """.trimIndent()

            val statement = connection.prepareStatement(sql)
            statement.setString(1, playerUuid)
            statement.setInt(2, size)
            statement.setInt(3, offset)

            val result = statement.executeQuery()
            val books = mutableListOf<BookData>()

            while (result.next()) {
                books.add(mapResultSetToBookDataWithPlayerName(result))
            }

            return Pair(books, totalCount)
        }
    }

    /**
     * 공개된 책 목록 조회
     */
    fun getPublicBooks(
        page: Int = 1,
        size: Int = 20,
        searchQuery: String? = null,
        season: String? = null
    ): Pair<List<BookData>, Long> {
        val offset = (page - 1) * size
        
        database.getConnection().use { connection ->
            var whereClause = "WHERE is_public = TRUE"
            val params = mutableListOf<Any>()

            searchQuery?.let {
                whereClause += " AND (title LIKE ? OR content LIKE ?)"
                params.add("%$it%")
                params.add("%$it%")
            }

            season?.let {
                whereClause += " AND season = ?"
                params.add(it)
            }

            // 총 개수 조회
            val countSql = "SELECT COUNT(*) FROM books $whereClause"
            val countStatement = connection.prepareStatement(countSql)
            params.forEachIndexed { index, param ->
                when (param) {
                    is String -> countStatement.setString(index + 1, param)
                    is Int -> countStatement.setInt(index + 1, param)
                    is Long -> countStatement.setLong(index + 1, param)
                    is Boolean -> countStatement.setBoolean(index + 1, param)
                }
            }
            val countResult = countStatement.executeQuery()
            val totalCount = if (countResult.next()) countResult.getLong(1) else 0L

            // 책 목록 조회
            val sql = """
                SELECT * FROM books $whereClause
                ORDER BY created_at DESC
                LIMIT ? OFFSET ?
            """.trimIndent()

            val statement = connection.prepareStatement(sql)
            params.forEachIndexed { index, param ->
                when (param) {
                    is String -> statement.setString(index + 1, param)
                    is Int -> statement.setInt(index + 1, param)
                    is Long -> statement.setLong(index + 1, param)
                    is Boolean -> statement.setBoolean(index + 1, param)
                }
            }
            statement.setInt(params.size + 1, size)
            statement.setInt(params.size + 2, offset)

            val result = statement.executeQuery()
            val books = mutableListOf<BookData>()

            while (result.next()) {
                books.add(mapResultSetToBookData(result))
            }

            return Pair(books, totalCount)
        }
    }

    /**
     * 특정 책 조회
     */
    fun getBook(bookId: Long): BookData? {
        database.getConnection().use { connection ->
            val sql = "SELECT * FROM books WHERE id = ?"
            val statement = connection.prepareStatement(sql)
            statement.setLong(1, bookId)

            val result = statement.executeQuery()
            return if (result.next()) {
                mapResultSetToBookData(result)
            } else null
        }
    }

    /**
     * 플레이어가 특정 책의 소유자인지 확인
     */
    fun isBookOwner(bookId: Long, playerUuid: String): Boolean {
        database.getConnection().use { connection ->
            val sql = "SELECT COUNT(*) FROM books WHERE id = ? AND uuid = ?"
            val statement = connection.prepareStatement(sql)
            statement.setLong(1, bookId)
            statement.setString(2, playerUuid)

            val result = statement.executeQuery()
            return result.next() && result.getInt(1) > 0
        }
    }

    /**
     * 책 삭제
     */
    fun deleteBook(bookId: Long, playerUuid: String): Boolean {
        database.getConnection().use { connection ->
            val sql = "DELETE FROM books WHERE id = ? AND uuid = ?"
            val statement = connection.prepareStatement(sql)
            statement.setLong(1, bookId)
            statement.setString(2, playerUuid)

            return statement.executeUpdate() > 0
        }
    }

    // ================================
    // 세션 관련 메소드들
    // ================================

    /**
     * 새로운 세션 생성
     */
    fun createSession(session: BookSession): Boolean {
        database.getConnection().use { connection ->
            val sql = """
                INSERT INTO book_sessions (session_id, uuid, token, ip_address, user_agent, expires_at)
                VALUES (?, ?, ?, ?, ?, ?)
            """.trimIndent()

            val statement = connection.prepareStatement(sql)
            statement.setString(1, session.sessionId)
            statement.setString(2, session.uuid)
            statement.setString(3, session.token)
            statement.setString(4, session.ipAddress)
            statement.setString(5, session.userAgent)
            // LocalDateTime 문자열을 Timestamp로 변환
            val cleanedTimestamp = session.expiresAt
                .replace(" ", "T")          // 공백을 T로 변경
                .replace(Regex("\\.\\d+$"), "") // 마이크로초/나노초 제거 (.0, .123 등)
            val expiresDateTime = LocalDateTime.parse(cleanedTimestamp, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            statement.setTimestamp(6, Timestamp.valueOf(expiresDateTime))

            return statement.executeUpdate() > 0
        }
    }

    /**
     * 토큰으로 세션 조회
     */
    fun getSessionByToken(token: String): BookSession? {
        database.getConnection().use { connection ->
            val sql = """
                SELECT * FROM book_sessions 
                WHERE token = ? AND is_active = TRUE AND expires_at > NOW()
            """.trimIndent()

            val statement = connection.prepareStatement(sql)
            statement.setString(1, token)

            val result = statement.executeQuery()
            return if (result.next()) {
                mapResultSetToSession(result)
            } else null
        }
    }

    /**
     * 세션 업데이트 (마지막 사용 시간)
     */
    fun updateSessionLastUsed(sessionId: String): Boolean {
        database.getConnection().use { connection ->
            val sql = """
                UPDATE book_sessions 
                SET last_used_at = CURRENT_TIMESTAMP 
                WHERE session_id = ?
            """.trimIndent()

            val statement = connection.prepareStatement(sql)
            statement.setString(1, sessionId)

            return statement.executeUpdate() > 0
        }
    }

    /**
     * 세션 비활성화
     */
    fun deactivateSession(sessionId: String): Boolean {
        database.getConnection().use { connection ->
            val sql = "UPDATE book_sessions SET is_active = FALSE WHERE session_id = ?"
            val statement = connection.prepareStatement(sql)
            statement.setString(1, sessionId)

            return statement.executeUpdate() > 0
        }
    }

    /**
     * 플레이어의 모든 활성 세션 비활성화
     */
    fun deactivatePlayerSessions(playerUuid: String): Boolean {
        database.getConnection().use { connection ->
            val sql = "UPDATE book_sessions SET is_active = FALSE WHERE uuid = ?"
            val statement = connection.prepareStatement(sql)
            statement.setString(1, playerUuid)

            return statement.executeUpdate() > 0
        }
    }

    /**
     * 만료된 세션들 정리
     */
    fun cleanupExpiredSessions(): Int {
        database.getConnection().use { connection ->
            val sql = "DELETE FROM book_sessions WHERE expires_at < NOW()"
            val statement = connection.prepareStatement(sql)

            return statement.executeUpdate()
        }
    }

    // ================================
    // 유틸리티 메소드들
    // ================================

    /**
     * ResultSet을 BookData로 변환 (플레이어 이름 포함)
     */
    private fun mapResultSetToBookDataWithPlayerName(result: ResultSet): BookData {
        return BookData(
            id = result.getLong("id"),
            uuid = result.getString("uuid"),
            title = result.getString("title"),
            content = result.getString("content"),
            pageCount = result.getInt("page_count"),
            isSigned = result.getBoolean("is_signed"),
            isPublic = result.getBoolean("is_public"),
            isArchived = result.getBoolean("is_archived"),
            season = result.getString("season"),
            playerName = result.getString("player_name"), // 플레이어 닉네임 추가
            createdAt = result.getTimestamp("created_at").toString(),
            updatedAt = result.getTimestamp("updated_at").toString()
        )
    }

    /**
     * ResultSet을 BookData로 변환 (기본 - 플레이어 이름 없음)
     */
    private fun mapResultSetToBookData(result: ResultSet): BookData {
        return BookData(
            id = result.getLong("id"),
            uuid = result.getString("uuid"),
            title = result.getString("title"),
            content = result.getString("content"),
            pageCount = result.getInt("page_count"),
            isSigned = result.getBoolean("is_signed"),
            isPublic = result.getBoolean("is_public"),
            isArchived = result.getBoolean("is_archived"),
            season = result.getString("season"),
            playerName = null, // 플레이어 이름 없음
            createdAt = result.getTimestamp("created_at").toString(),
            updatedAt = result.getTimestamp("updated_at").toString()
        )
    }

    /**
     * ResultSet을 BookSession으로 변환
     */
    private fun mapResultSetToSession(result: ResultSet): BookSession {
        return BookSession(
            sessionId = result.getString("session_id"),
            uuid = result.getString("uuid"),
            token = result.getString("token"),
            ipAddress = result.getString("ip_address"),
            userAgent = result.getString("user_agent"),
            isActive = result.getBoolean("is_active"),
            createdAt = result.getTimestamp("created_at").toString(),
            expiresAt = result.getTimestamp("expires_at").toString(),
            lastUsedAt = result.getTimestamp("last_used_at").toString()
        )
    }

    /**
     * 시즌별 통계 조회
     */
    fun getBookStatsBySeason(): Map<String, Int> {
        database.getConnection().use { connection ->
            val sql = """
                SELECT season, COUNT(*) as count 
                FROM books 
                WHERE season IS NOT NULL 
                GROUP BY season 
                ORDER BY season
            """.trimIndent()

            val statement = connection.prepareStatement(sql)
            val result = statement.executeQuery()
            val stats = mutableMapOf<String, Int>()

            while (result.next()) {
                stats[result.getString("season")] = result.getInt("count")
            }

            return stats
        }
    }
}