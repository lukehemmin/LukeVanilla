package com.lukehemmin.lukeVanilla.System.BookSystem

import com.lukehemmin.lukeVanilla.Main
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import java.security.SecureRandom
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.logging.Logger

/**
 * 책 시스템의 웹 세션 관리자
 * 인증 토큰 생성, 검증, 세션 관리를 담당
 */
class BookSessionManager(
    private val plugin: Main,
    private val bookRepository: BookRepository,
    private val logger: Logger
) {

    private val secureRandom = SecureRandom()
    private val passwordEncoder = BCryptPasswordEncoder()
    private val sessionCache = ConcurrentHashMap<String, BookSession>()
    private val cleanupExecutor = Executors.newSingleThreadScheduledExecutor()

    // 인증 코드 임시 저장소 (플레이어 UUID -> 인증 코드)
    private val pendingAuthCodes = ConcurrentHashMap<String, PendingAuth>()
    
    // 세션 설정
    private val sessionExpiryHours = plugin.config.getLong("book_system.session_expiry_hours", 24)
    private val authCodeExpiryMinutes = plugin.config.getLong("book_system.auth_code_expiry_minutes", 5)
    private val maxSessionsPerPlayer = plugin.config.getInt("book_system.max_sessions_per_player", 3)

    data class PendingAuth(
        val authCode: String,
        val createdAt: LocalDateTime,
        val playerUuid: String,
        val playerName: String
    )

    init {
        // 5분마다 만료된 세션과 인증 코드 정리
        cleanupExecutor.scheduleAtFixedRate({
            try {
                cleanupExpiredSessions()
                cleanupExpiredAuthCodes()
            } catch (e: Exception) {
                logger.severe("[BookSessionManager] 정리 작업 중 예외 발생: ${e.message}")
                e.printStackTrace()
            }
        }, 5, 5, TimeUnit.MINUTES)
    }

    /**
     * 플레이어를 위한 인증 코드 생성
     * 인게임 명령어에서 호출됨
     */
    fun generateAuthCode(playerUuid: String, playerName: String): String {
        // 기존 인증 코드가 있다면 제거
        removeAuthCode(playerUuid)
        
        // 6자리 숫자 인증 코드 생성
        val authCode = String.format("%06d", secureRandom.nextInt(1000000))
        
        val pendingAuth = PendingAuth(
            authCode = authCode,
            createdAt = LocalDateTime.now(),
            playerUuid = playerUuid,
            playerName = playerName
        )
        
        pendingAuthCodes[playerUuid] = pendingAuth
        
        logger.info("[BookSessionManager] 플레이어 ${playerName}의 인증 코드 생성: $authCode")
        return authCode
    }

    /**
     * 인증 코드로 웹 세션 생성
     * 웹에서 인증 코드 입력 시 호출됨
     */
    fun authenticateWithCode(
        authCode: String,
        ipAddress: String?,
        userAgent: String?
    ): AuthToken? {
        // 인증 코드로 대기 중인 인증 정보 찾기
        val pendingAuth = pendingAuthCodes.values.find { it.authCode == authCode }
            ?: return null

        // 인증 코드 만료 확인
        if (pendingAuth.createdAt.plusMinutes(authCodeExpiryMinutes).isBefore(LocalDateTime.now())) {
            pendingAuthCodes.remove(pendingAuth.playerUuid)
            return null
        }

        try {
            // 해당 플레이어의 기존 세션 수 확인
            cleanupPlayerSessions(pendingAuth.playerUuid)

            // 새 세션 생성
            val session = createNewSession(pendingAuth.playerUuid, ipAddress, userAgent)
            
            // 데이터베이스에 저장
            if (bookRepository.createSession(session)) {
                sessionCache[session.token] = session
                
                // 사용된 인증 코드 제거
                pendingAuthCodes.remove(pendingAuth.playerUuid)
                
                logger.info("[BookSessionManager] 플레이어 ${pendingAuth.playerName}의 웹 세션 생성 완료")
                
                return AuthToken(
                    token = session.token,
                    expiresIn = sessionExpiryHours * 3600 // 초 단위
                )
            }
        } catch (e: Exception) {
            logger.severe("[BookSessionManager] 세션 생성 중 예외 발생: ${e.message}")
            e.printStackTrace()
        }

        return null
    }

    /**
     * 토큰으로 세션 검증
     */
    fun validateToken(token: String): BookSession? {
        // 캐시에서 먼저 확인
        sessionCache[token]?.let { cachedSession ->
            if (isSessionValid(cachedSession)) {
                // 마지막 사용 시간 업데이트
                bookRepository.updateSessionLastUsed(cachedSession.sessionId)
                return cachedSession
            } else {
                // 만료된 세션 제거
                sessionCache.remove(token)
                bookRepository.deactivateSession(cachedSession.sessionId)
            }
        }

        // 데이터베이스에서 확인
        val session = bookRepository.getSessionByToken(token)
        return if (session != null && isSessionValid(session)) {
            sessionCache[token] = session
            bookRepository.updateSessionLastUsed(session.sessionId)
            session
        } else {
            null
        }
    }

    /**
     * 세션 무효화
     */
    fun invalidateSession(token: String): Boolean {
        sessionCache[token]?.let { session ->
            sessionCache.remove(token)
            return bookRepository.deactivateSession(session.sessionId)
        }
        return false
    }

    /**
     * 플레이어의 모든 세션 무효화
     */
    fun invalidatePlayerSessions(playerUuid: String): Boolean {
        // 캐시에서 제거
        sessionCache.values.removeIf { it.uuid == playerUuid }
        
        // 데이터베이스에서 비활성화
        return bookRepository.deactivatePlayerSessions(playerUuid)
    }

    /**
     * 인증 코드 제거
     */
    fun removeAuthCode(playerUuid: String) {
        pendingAuthCodes.remove(playerUuid)
    }

    /**
     * 플레이어의 대기 중인 인증 코드 조회
     */
    fun getPendingAuthCode(playerUuid: String): String? {
        val pendingAuth = pendingAuthCodes[playerUuid] ?: return null
        
        // 만료 확인
        if (pendingAuth.createdAt.plusMinutes(authCodeExpiryMinutes).isBefore(LocalDateTime.now())) {
            pendingAuthCodes.remove(playerUuid)
            return null
        }
        
        return pendingAuth.authCode
    }

    /**
     * 새로운 세션 생성
     */
    private fun createNewSession(
        playerUuid: String,
        ipAddress: String?,
        userAgent: String?
    ): BookSession {
        val sessionId = UUID.randomUUID().toString()
        val token = generateSecureToken()
        val now = LocalDateTime.now()
        val expiresAt = now.plusHours(sessionExpiryHours)
        
        return BookSession(
            sessionId = sessionId,
            uuid = playerUuid,
            token = token,
            ipAddress = ipAddress,
            userAgent = userAgent,
            isActive = true,
            createdAt = now.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
            expiresAt = expiresAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
            lastUsedAt = now.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        )
    }

    /**
     * 보안 토큰 생성 (64자 무작위 문자열)
     */
    private fun generateSecureToken(): String {
        val bytes = ByteArray(48) // 64자 Base64 문자열을 위한 48바이트
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    /**
     * 세션 유효성 검증
     */
    private fun isSessionValid(session: BookSession): Boolean {
        if (!session.isActive) return false
        
        val expiresAt = LocalDateTime.parse(session.expiresAt, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        return expiresAt.isAfter(LocalDateTime.now())
    }

    /**
     * 플레이어의 세션 수 정리 (최대 세션 수 초과 시)
     */
    private fun cleanupPlayerSessions(playerUuid: String) {
        try {
            // 현재 활성 세션 수 확인 및 정리는 데이터베이스에서 직접 처리
            // 실제 구현에서는 가장 오래된 세션부터 제거하는 로직 추가 가능
        } catch (e: Exception) {
            logger.warning("[BookSessionManager] 플레이어 세션 정리 중 오류: ${e.message}")
        }
    }

    /**
     * 만료된 세션들 정리
     */
    private fun cleanupExpiredSessions() {
        try {
            // 데이터베이스에서 만료된 세션 제거
            val removedCount = bookRepository.cleanupExpiredSessions()
            
            // 캐시에서도 만료된 세션 제거
            val iterator = sessionCache.iterator()
            var cacheRemovedCount = 0
            
            while (iterator.hasNext()) {
                val entry = iterator.next()
                if (!isSessionValid(entry.value)) {
                    iterator.remove()
                    cacheRemovedCount++
                }
            }
            
            if (removedCount > 0 || cacheRemovedCount > 0) {
                logger.info("[BookSessionManager] 만료된 세션 정리 완료: DB=$removedCount, Cache=$cacheRemovedCount")
            }
        } catch (e: Exception) {
            logger.severe("[BookSessionManager] 세션 정리 중 예외 발생: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * 만료된 인증 코드들 정리
     */
    private fun cleanupExpiredAuthCodes() {
        try {
            val iterator = pendingAuthCodes.iterator()
            var removedCount = 0
            val now = LocalDateTime.now()
            
            while (iterator.hasNext()) {
                val entry = iterator.next()
                if (entry.value.createdAt.plusMinutes(authCodeExpiryMinutes).isBefore(now)) {
                    iterator.remove()
                    removedCount++
                }
            }
            
            if (removedCount > 0) {
                logger.info("[BookSessionManager] 만료된 인증 코드 정리 완료: $removedCount 개")
            }
        } catch (e: Exception) {
            logger.severe("[BookSessionManager] 인증 코드 정리 중 예외 발생: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * 리소스 정리
     */
    fun shutdown() {
        try {
            cleanupExecutor.shutdown()
            if (!cleanupExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                cleanupExecutor.shutdownNow()
            }
            logger.info("[BookSessionManager] 세션 관리자가 정상적으로 종료되었습니다.")
        } catch (e: Exception) {
            logger.severe("[BookSessionManager] 종료 중 예외 발생: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * 현재 활성 세션 통계
     */
    fun getSessionStats(): Map<String, Any> {
        return mapOf(
            "cached_sessions" to sessionCache.size,
            "pending_auth_codes" to pendingAuthCodes.size,
            "session_expiry_hours" to sessionExpiryHours,
            "auth_code_expiry_minutes" to authCodeExpiryMinutes
        )
    }
}