package com.lukehemmin.lukeVanilla.System.BookSystem

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import java.util.logging.Logger

/**
 * 책 시스템 웹 API 엔드포인트들
 */
class BookAPI(
    private val bookRepository: BookRepository,
    private val sessionManager: BookSessionManager,
    private val logger: Logger
) {

    private val json = Json { 
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    /**
     * API 라우팅 설정
     */
    fun configureRouting(routing: Routing) {
        routing {
            route("/api") {
                // 인증이 필요없는 엔드포인트
                post("/auth") { call.handleAuth() }
                get("/books/public") { call.handlePublicBooks() }
                get("/books/public/{id}") { call.handlePublicBook() }
                get("/stats") { call.handlePublicStats() }

                // 인증이 필요한 엔드포인트
                authenticate("book-auth") {
                    route("/user") {
                        get("/books") { call.handleUserBooks() }
                        get("/books/{id}") { call.handleUserBook() }
                        put("/books/{id}") { call.handleUpdateBook() }
                        delete("/books/{id}") { call.handleDeleteBook() }
                        post("/books/{id}/public") { call.handleSetBookPublic() }
                        post("/books/{id}/private") { call.handleSetBookPrivate() }
                        get("/profile") { call.handleUserProfile() }
                        post("/logout") { call.handleLogout() }
                    }
                }
            }
        }
    }

    /**
     * 인증 처리
     */
    private suspend fun ApplicationCall.handleAuth() {
        try {
            val authCodeRequest = receive<Map<String, String>>()
            val authCode = authCodeRequest["authCode"]

            if (authCode.isNullOrBlank()) {
                respond(HttpStatusCode.BadRequest, ApiResponse<AuthToken>(
                    success = false,
                    error = "인증 코드가 필요합니다."
                ))
                return
            }

            val clientIp = request.headers["X-Forwarded-For"] 
                ?: request.headers["X-Real-IP"]
                ?: request.local.remoteHost
            val userAgent = request.headers["User-Agent"]

            val authToken = sessionManager.authenticateWithCode(authCode, clientIp, userAgent)

            if (authToken != null) {
                respond(HttpStatusCode.OK, ApiResponse(
                    success = true,
                    data = authToken,
                    message = "인증에 성공했습니다."
                ))
                logger.info("[BookAPI] 웹 인증 성공: IP=$clientIp")
            } else {
                respond(HttpStatusCode.Unauthorized, ApiResponse<AuthToken>(
                    success = false,
                    error = "유효하지 않거나 만료된 인증 코드입니다."
                ))
            }
        } catch (e: Exception) {
            logger.severe("[BookAPI] 인증 처리 중 예외 발생: ${e.message}")
            respond(HttpStatusCode.InternalServerError, ApiResponse<AuthToken>(
                success = false,
                error = "서버 내부 오류가 발생했습니다."
            ))
        }
    }

    /**
     * 공개 책 목록 조회
     */
    private suspend fun ApplicationCall.handlePublicBooks() {
        try {
            val page = request.queryParameters["page"]?.toIntOrNull() ?: 1
            val size = request.queryParameters["size"]?.toIntOrNull() ?: 20
            val search = request.queryParameters["search"]
            val season = request.queryParameters["season"]

            val (books, totalCount) = bookRepository.getPublicBooks(page, size, search, season)
            val totalPages = ((totalCount - 1) / size + 1).toInt()

            val response = BookListResponse(
                books = books,
                pagination = Pagination(page, size, totalCount, totalPages)
            )

            respond(HttpStatusCode.OK, ApiResponse(
                success = true,
                data = response
            ))
        } catch (e: Exception) {
            logger.severe("[BookAPI] 공개 책 목록 조회 중 예외 발생: ${e.message}")
            respond(HttpStatusCode.InternalServerError, ApiResponse<BookListResponse>(
                success = false,
                error = "서버 내부 오류가 발생했습니다."
            ))
        }
    }

    /**
     * 특정 공개 책 조회
     */
    private suspend fun ApplicationCall.handlePublicBook() {
        try {
            val bookId = parameters["id"]?.toLongOrNull()
            if (bookId == null) {
                respond(HttpStatusCode.BadRequest, ApiResponse<BookData>(
                    success = false,
                    error = "올바른 책 ID가 필요합니다."
                ))
                return
            }

            val book = bookRepository.getBook(bookId)
            if (book == null || !book.isPublic) {
                respond(HttpStatusCode.NotFound, ApiResponse<BookData>(
                    success = false,
                    error = "존재하지 않거나 비공개 책입니다."
                ))
                return
            }

            respond(HttpStatusCode.OK, ApiResponse(
                success = true,
                data = book
            ))
        } catch (e: Exception) {
            logger.severe("[BookAPI] 공개 책 조회 중 예외 발생: ${e.message}")
            respond(HttpStatusCode.InternalServerError, ApiResponse<BookData>(
                success = false,
                error = "서버 내부 오류가 발생했습니다."
            ))
        }
    }

    /**
     * 사용자 책 목록 조회 (인증 필요)
     */
    private suspend fun ApplicationCall.handleUserBooks() {
        try {
            val session = getUserSession() ?: return
            val page = request.queryParameters["page"]?.toIntOrNull() ?: 1
            val size = request.queryParameters["size"]?.toIntOrNull() ?: 20

            val (books, totalCount) = bookRepository.getPlayerBooks(session.uuid, page, size)
            val totalPages = ((totalCount - 1) / size + 1).toInt()

            val response = BookListResponse(
                books = books,
                pagination = Pagination(page, size, totalCount, totalPages)
            )

            respond(HttpStatusCode.OK, ApiResponse(
                success = true,
                data = response
            ))
        } catch (e: Exception) {
            logger.severe("[BookAPI] 사용자 책 목록 조회 중 예외 발생: ${e.message}")
            respond(HttpStatusCode.InternalServerError, ApiResponse<BookListResponse>(
                success = false,
                error = "서버 내부 오류가 발생했습니다."
            ))
        }
    }

    /**
     * 특정 사용자 책 조회 (인증 필요)
     */
    private suspend fun ApplicationCall.handleUserBook() {
        try {
            val session = getUserSession() ?: return
            val bookId = parameters["id"]?.toLongOrNull()
            
            if (bookId == null) {
                respond(HttpStatusCode.BadRequest, ApiResponse<BookData>(
                    success = false,
                    error = "올바른 책 ID가 필요합니다."
                ))
                return
            }

            val book = bookRepository.getBook(bookId)
            if (book == null) {
                respond(HttpStatusCode.NotFound, ApiResponse<BookData>(
                    success = false,
                    error = "존재하지 않는 책입니다."
                ))
                return
            }

            // 소유자이거나 공개 책인 경우에만 조회 가능
            if (book.uuid != session.uuid && !book.isPublic) {
                respond(HttpStatusCode.Forbidden, ApiResponse<BookData>(
                    success = false,
                    error = "접근 권한이 없습니다."
                ))
                return
            }

            respond(HttpStatusCode.OK, ApiResponse(
                success = true,
                data = book
            ))
        } catch (e: Exception) {
            logger.severe("[BookAPI] 사용자 책 조회 중 예외 발생: ${e.message}")
            respond(HttpStatusCode.InternalServerError, ApiResponse<BookData>(
                success = false,
                error = "서버 내부 오류가 발생했습니다."
            ))
        }
    }

    /**
     * 책 수정 (인증 필요)
     */
    private suspend fun ApplicationCall.handleUpdateBook() {
        try {
            val session = getUserSession() ?: return
            val bookId = parameters["id"]?.toLongOrNull()
            
            if (bookId == null) {
                respond(HttpStatusCode.BadRequest, ApiResponse<String>(
                    success = false,
                    error = "올바른 책 ID가 필요합니다."
                ))
                return
            }

            if (!bookRepository.isBookOwner(bookId, session.uuid)) {
                respond(HttpStatusCode.Forbidden, ApiResponse<String>(
                    success = false,
                    error = "해당 책의 소유자가 아닙니다."
                ))
                return
            }

            val updateRequest = receive<BookUpdateRequest>()
            
            // 책 내용을 BookContent 형태로 변환
            val bookContent = try {
                json.decodeFromString<BookContent>(updateRequest.content)
            } catch (e: Exception) {
                respond(HttpStatusCode.BadRequest, ApiResponse<String>(
                    success = false,
                    error = "올바르지 않은 책 내용 형식입니다."
                ))
                return
            }

            val success = bookRepository.updateBook(bookId, updateRequest.title, bookContent, session.uuid)
            
            if (success) {
                // 공개 설정도 함께 업데이트
                bookRepository.setBookPublic(bookId, updateRequest.isPublic, session.uuid)
                
                respond(HttpStatusCode.OK, ApiResponse<String>(
                    success = true,
                    message = "책이 성공적으로 수정되었습니다."
                ))
            } else {
                respond(HttpStatusCode.InternalServerError, ApiResponse<String>(
                    success = false,
                    error = "책 수정에 실패했습니다."
                ))
            }
        } catch (e: Exception) {
            logger.severe("[BookAPI] 책 수정 중 예외 발생: ${e.message}")
            respond(HttpStatusCode.InternalServerError, ApiResponse<String>(
                success = false,
                error = "서버 내부 오류가 발생했습니다."
            ))
        }
    }

    /**
     * 책 삭제 (인증 필요)
     */
    private suspend fun ApplicationCall.handleDeleteBook() {
        try {
            val session = getUserSession() ?: return
            val bookId = parameters["id"]?.toLongOrNull()
            
            if (bookId == null) {
                respond(HttpStatusCode.BadRequest, ApiResponse<String>(
                    success = false,
                    error = "올바른 책 ID가 필요합니다."
                ))
                return
            }

            // 책 정보 먼저 확인 (서명된 책인지 체크)
            val book = bookRepository.getBook(bookId)
            if (book == null || book.uuid != session.uuid) {
                respond(HttpStatusCode.NotFound, ApiResponse<String>(
                    success = false,
                    error = "해당 책의 소유자가 아니거나 존재하지 않는 책입니다."
                ))
                return
            }

            // 서명된 책은 삭제 불가
            if (book.isSigned) {
                respond(HttpStatusCode.BadRequest, ApiResponse<String>(
                    success = false,
                    error = "서명된 책은 삭제할 수 없습니다."
                ))
                return
            }

            val success = bookRepository.deleteBook(bookId, session.uuid)
            
            if (success) {
                respond(HttpStatusCode.OK, ApiResponse<String>(
                    success = true,
                    message = "책이 성공적으로 삭제되었습니다."
                ))
            } else {
                respond(HttpStatusCode.NotFound, ApiResponse<String>(
                    success = false,
                    error = "해당 책의 소유자가 아니거나 존재하지 않는 책입니다."
                ))
            }
        } catch (e: Exception) {
            logger.severe("[BookAPI] 책 삭제 중 예외 발생: ${e.message}")
            respond(HttpStatusCode.InternalServerError, ApiResponse<String>(
                success = false,
                error = "서버 내부 오류가 발생했습니다."
            ))
        }
    }

    /**
     * 책 공개 설정 (인증 필요)
     */
    private suspend fun ApplicationCall.handleSetBookPublic() {
        try {
            val session = getUserSession() ?: return
            val bookId = parameters["id"]?.toLongOrNull()
            
            if (bookId == null) {
                respond(HttpStatusCode.BadRequest, ApiResponse<String>(
                    success = false,
                    error = "올바른 책 ID가 필요합니다."
                ))
                return
            }

            val success = bookRepository.setBookPublic(bookId, true, session.uuid)
            
            if (success) {
                respond(HttpStatusCode.OK, ApiResponse<String>(
                    success = true,
                    message = "책이 공개로 설정되었습니다."
                ))
            } else {
                respond(HttpStatusCode.NotFound, ApiResponse<String>(
                    success = false,
                    error = "해당 책의 소유자가 아니거나 존재하지 않는 책입니다."
                ))
            }
        } catch (e: Exception) {
            logger.severe("[BookAPI] 책 공개 설정 중 예외 발생: ${e.message}")
            respond(HttpStatusCode.InternalServerError, ApiResponse<String>(
                success = false,
                error = "서버 내부 오류가 발생했습니다."
            ))
        }
    }

    /**
     * 책 비공개 설정 (인증 필요)
     */
    private suspend fun ApplicationCall.handleSetBookPrivate() {
        try {
            val session = getUserSession() ?: return
            val bookId = parameters["id"]?.toLongOrNull()
            
            if (bookId == null) {
                respond(HttpStatusCode.BadRequest, ApiResponse<String>(
                    success = false,
                    error = "올바른 책 ID가 필요합니다."
                ))
                return
            }

            val success = bookRepository.setBookPublic(bookId, false, session.uuid)
            
            if (success) {
                respond(HttpStatusCode.OK, ApiResponse<String>(
                    success = true,
                    message = "책이 비공개로 설정되었습니다."
                ))
            } else {
                respond(HttpStatusCode.NotFound, ApiResponse<String>(
                    success = false,
                    error = "해당 책의 소유자가 아니거나 존재하지 않는 책입니다."
                ))
            }
        } catch (e: Exception) {
            logger.severe("[BookAPI] 책 비공개 설정 중 예외 발생: ${e.message}")
            respond(HttpStatusCode.InternalServerError, ApiResponse<String>(
                success = false,
                error = "서버 내부 오류가 발생했습니다."
            ))
        }
    }

    /**
     * 사용자 프로필 조회 (인증 필요)
     */
    private suspend fun ApplicationCall.handleUserProfile() {
        try {
            val session = getUserSession() ?: return

            val (books, totalCount) = bookRepository.getPlayerBooks(session.uuid, 1, 1)
            val (publicBooks, publicCount) = bookRepository.getPlayerBooks(session.uuid, 1, 1, true)
            
            val profile = mapOf(
                "uuid" to session.uuid,
                "totalBooks" to totalCount,
                "publicBooks" to publicCount,
                "privateBooks" to (totalCount - publicCount),
                "sessionInfo" to mapOf(
                    "createdAt" to session.createdAt,
                    "expiresAt" to session.expiresAt,
                    "lastUsedAt" to session.lastUsedAt
                )
            )

            respond(HttpStatusCode.OK, ApiResponse(
                success = true,
                data = profile
            ))
        } catch (e: Exception) {
            logger.severe("[BookAPI] 사용자 프로필 조회 중 예외 발생: ${e.message}")
            respond(HttpStatusCode.InternalServerError, ApiResponse<Map<String, Any>>(
                success = false,
                error = "서버 내부 오류가 발생했습니다."
            ))
        }
    }

    /**
     * 로그아웃 (인증 필요)
     */
    private suspend fun ApplicationCall.handleLogout() {
        try {
            val session = getUserSession() ?: return
            
            sessionManager.invalidateSession(session.token)
            
            respond(HttpStatusCode.OK, ApiResponse<String>(
                success = true,
                message = "성공적으로 로그아웃되었습니다."
            ))
        } catch (e: Exception) {
            logger.severe("[BookAPI] 로그아웃 중 예외 발생: ${e.message}")
            respond(HttpStatusCode.InternalServerError, ApiResponse<String>(
                success = false,
                error = "서버 내부 오류가 발생했습니다."
            ))
        }
    }

    /**
     * 공개 통계 조회
     */
    private suspend fun ApplicationCall.handlePublicStats() {
        try {
            val seasonStats = bookRepository.getBookStatsBySeason().mapValues { it.value.toLong() }
            val (_, totalPublicBooks) = bookRepository.getPublicBooks(1, 1)
            
            val statsResponse = PublicStatsResponse(
                totalPublicBooks = totalPublicBooks,
                seasonStats = seasonStats
            )

            respond(HttpStatusCode.OK, ApiResponse(
                success = true,
                data = statsResponse
            ))
        } catch (e: Exception) {
            logger.severe("[BookAPI] 공개 통계 조회 중 예외 발생: ${e.message}")
            respond(HttpStatusCode.InternalServerError, ApiResponse<PublicStatsResponse>(
                success = false,
                error = "서버 내부 오류가 발생했습니다."
            ))
        }
    }

    /**
     * 현재 사용자 세션 가져오기
     */
    private suspend fun ApplicationCall.getUserSession(): BookSession? {
        val principal = authentication.principal<BookSessionPrincipal>()
        if (principal == null) {
            respond(HttpStatusCode.Unauthorized, ApiResponse<String>(
                success = false,
                error = "인증이 필요합니다."
            ))
            return null
        }
        return principal.session
    }

    /**
     * 인증 주체 클래스
     */
    data class BookSessionPrincipal(val session: BookSession) : Principal
}