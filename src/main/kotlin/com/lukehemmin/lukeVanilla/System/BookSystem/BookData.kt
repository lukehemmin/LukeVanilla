package com.lukehemmin.lukeVanilla.System.BookSystem

import kotlinx.serialization.Serializable
import java.sql.Timestamp
import java.util.*

/**
 * 책 데이터 모델
 */
@Serializable
data class BookData(
    val id: Long = 0,
    val uuid: String,
    val title: String,
    val content: String, // JSON 형태의 페이지별 내용
    val pageCount: Int = 1,
    val isSigned: Boolean = false,
    val isPublic: Boolean = false,
    val isArchived: Boolean = false,
    val season: String? = null,
    val playerName: String? = null, // 플레이어 닉네임
    val createdAt: String = "",
    val updatedAt: String = ""
)

/**
 * 책 페이지 데이터
 */
@Serializable
data class BookPage(
    val pageNumber: Int,
    val content: String
)

/**
 * 책 내용 (전체 페이지)
 */
@Serializable
data class BookContent(
    val pages: List<BookPage>
)

/**
 * 웹 세션 데이터 모델
 */
@Serializable
data class BookSession(
    val sessionId: String,
    val uuid: String,
    val token: String,
    val ipAddress: String? = null,
    val userAgent: String? = null,
    val isActive: Boolean = true,
    val createdAt: String = "",
    val expiresAt: String = "",
    val lastUsedAt: String = ""
)

/**
 * 인증 토큰 정보
 */
@Serializable
data class AuthToken(
    val token: String,
    val expiresIn: Long // 만료까지 남은 시간 (초)
)

/**
 * 웹 API 응답 형태
 */
@Serializable
data class ApiResponse<T>(
    val success: Boolean,
    val data: T? = null,
    val message: String? = null,
    val error: String? = null
)

/**
 * 책 목록 요청 파라미터
 */
@Serializable
data class BookListRequest(
    val page: Int = 1,
    val size: Int = 20,
    val publicOnly: Boolean = false,
    val searchQuery: String? = null,
    val season: String? = null
)

/**
 * 책 생성/수정 요청
 */
@Serializable
data class BookUpdateRequest(
    val title: String,
    val content: String,
    val isPublic: Boolean = false
)

/**
 * 페이지네이션 정보
 */
@Serializable
data class Pagination(
    val page: Int,
    val size: Int,
    val total: Long,
    val totalPages: Int
)

/**
 * 책 목록 응답
 */
@Serializable
data class BookListResponse(
    val books: List<BookData>,
    val pagination: Pagination
)

/**
 * 마인크래프트 책 아이템 정보
 */
data class MinecraftBookInfo(
    val title: String,
    val pages: List<String>,
    val author: String?,
    val generation: Int? = null // 책의 세대 (원본=0, 사본=1, 사본의 사본=2, 낡은 책=3)
)

/**
 * 공개 통계 응답
 */
@Serializable
data class PublicStatsResponse(
    val totalPublicBooks: Long,
    val seasonStats: Map<String, Long>
)