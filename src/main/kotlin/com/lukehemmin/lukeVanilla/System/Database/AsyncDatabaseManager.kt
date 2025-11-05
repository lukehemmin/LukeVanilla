package com.lukehemmin.lukeVanilla.System.Database

import com.lukehemmin.lukeVanilla.Main
import org.bukkit.Bukkit
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * 비동기 데이터베이스 작업 매니저
 * 서버 멈춤 방지 및 DB 과부하 방지를 위한 최적화된 DB 액세스 제공
 */
class AsyncDatabaseManager(
    private val plugin: Main,
    private val database: Database
) {

    // 비동기 작업용 스레드 풀
    private val executorService: ExecutorService = Executors.newFixedThreadPool(
        4, // 최대 4개 DB 연결 동시 사용
        ThreadFactory { runnable ->
            Thread(runnable, "LukeVanilla-DB-Worker").apply {
                isDaemon = true
                priority = Thread.NORM_PRIORITY - 1
            }
        }
    )

    // 대기 중인 작업 수 모니터링
    private val pendingOperations = AtomicInteger(0)
    private val totalOperations = AtomicLong(0)
    private val failedOperations = AtomicLong(0)

    // Circuit Breaker 패턴
    private var circuitBreakerOpen = false
    private var lastFailureTime = 0L
    private val circuitBreakerTimeoutMs = 30000L // 30초
    private val maxPendingOperations = 50 // 최대 대기 작업 수

    // 캐시 (간단한 메모리 캐시)
    private val cache = ConcurrentHashMap<String, CachedResult>()
    private val cacheExpiryMs = 5000L // 5초 캐시

    data class CachedResult(
        val data: Any?,
        val timestamp: Long
    ) {
        fun isExpired(): Boolean = System.currentTimeMillis() - timestamp > 5000L
    }

    /**
     * 비동기 쿼리 실행 (결과 반환)
     */
    fun <T> executeQueryAsync(
        query: String,
        params: List<Any?> = emptyList(),
        cacheKey: String? = null,
        mapper: (ResultSet) -> T?,
        onSuccess: (T?) -> Unit = {},
        onFailure: (Exception) -> Unit = {}
    ): CompletableFuture<T?> {

        // 캐시 확인
        cacheKey?.let { key ->
            cache[key]?.let { cached ->
                if (!cached.isExpired()) {
                    @Suppress("UNCHECKED_CAST")
                    val result = cached.data as? T
                    onSuccess(result)
                    return CompletableFuture.completedFuture(result)
                } else {
                    cache.remove(key)
                }
            }
        }

        // Circuit Breaker 체크
        if (isCircuitBreakerOpen()) {
            val exception = RuntimeException("Database circuit breaker is open")
            onFailure(exception)
            return CompletableFuture.failedFuture(exception)
        }

        // 대기 작업 수 체크
        if (pendingOperations.get() >= maxPendingOperations) {
            val exception = RuntimeException("Too many pending database operations")
            onFailure(exception)
            return CompletableFuture.failedFuture(exception)
        }

        val future = CompletableFuture<T?>()

        executorService.submit {
            pendingOperations.incrementAndGet()
            totalOperations.incrementAndGet()

            try {
                val result = executeQuerySafe(query, params, mapper)

                // 캐시에 저장
                cacheKey?.let { key ->
                    cache[key] = CachedResult(result, System.currentTimeMillis())
                }

                // 메인 스레드에서 콜백 실행
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    onSuccess(result)
                    future.complete(result)
                })

            } catch (e: Exception) {
                failedOperations.incrementAndGet()
                handleDatabaseError(e)

                Bukkit.getScheduler().runTask(plugin, Runnable {
                    onFailure(e)
                    future.completeExceptionally(e)
                })
            } finally {
                pendingOperations.decrementAndGet()
            }
        }

        return future
    }

    /**
     * 비동기 업데이트 실행 (INSERT, UPDATE, DELETE)
     */
    fun executeUpdateAsync(
        query: String,
        params: List<Any?> = emptyList(),
        onSuccess: (Boolean) -> Unit = {},
        onFailure: (Exception) -> Unit = {}
    ): CompletableFuture<Boolean> {

        // Circuit Breaker 체크
        if (isCircuitBreakerOpen()) {
            val exception = RuntimeException("Database circuit breaker is open")
            onFailure(exception)
            return CompletableFuture.failedFuture(exception)
        }

        // 대기 작업 수 체크
        if (pendingOperations.get() >= maxPendingOperations) {
            val exception = RuntimeException("Too many pending database operations")
            onFailure(exception)
            return CompletableFuture.failedFuture(exception)
        }

        val future = CompletableFuture<Boolean>()

        executorService.submit {
            pendingOperations.incrementAndGet()
            totalOperations.incrementAndGet()

            try {
                val success = executeUpdateSafe(query, params)

                Bukkit.getScheduler().runTask(plugin, Runnable {
                    onSuccess(success)
                    future.complete(success)
                })

            } catch (e: Exception) {
                failedOperations.incrementAndGet()
                handleDatabaseError(e)

                Bukkit.getScheduler().runTask(plugin, Runnable {
                    onFailure(e)
                    future.completeExceptionally(e)
                })
            } finally {
                pendingOperations.decrementAndGet()
            }
        }

        return future
    }

    /**
     * 배치 업데이트 실행 (여러 쿼리를 한 번에)
     */
    fun executeBatchAsync(
        queries: List<Pair<String, List<Any?>>>,
        onSuccess: (List<Boolean>) -> Unit = {},
        onFailure: (Exception) -> Unit = {}
    ): CompletableFuture<List<Boolean>> {

        if (isCircuitBreakerOpen()) {
            val exception = RuntimeException("Database circuit breaker is open")
            onFailure(exception)
            return CompletableFuture.failedFuture(exception)
        }

        val future = CompletableFuture<List<Boolean>>()

        executorService.submit {
            pendingOperations.incrementAndGet()
            totalOperations.incrementAndGet()

            try {
                val results = mutableListOf<Boolean>()

                database.getConnection().use { connection ->
                    connection.autoCommit = false

                    for ((query, params) in queries) {
                        connection.prepareStatement(query).use { statement ->
                            params.forEachIndexed { index, param ->
                                statement.setObject(index + 1, param)
                            }
                            results.add(statement.executeUpdate() > 0)
                        }
                    }

                    connection.commit()
                }

                Bukkit.getScheduler().runTask(plugin, Runnable {
                    onSuccess(results)
                    future.complete(results)
                })

            } catch (e: Exception) {
                failedOperations.incrementAndGet()
                handleDatabaseError(e)

                Bukkit.getScheduler().runTask(plugin, Runnable {
                    onFailure(e)
                    future.completeExceptionally(e)
                })
            } finally {
                pendingOperations.decrementAndGet()
            }
        }

        return future
    }

    /**
     * 안전한 쿼리 실행 (타임아웃 포함)
     */
    private fun <T> executeQuerySafe(
        query: String,
        params: List<Any?>,
        mapper: (ResultSet) -> T?
    ): T? {
        return database.getConnection().use { connection ->
            connection.prepareStatement(query).use { statement ->
                // 쿼리 타임아웃 설정 (5초)
                statement.queryTimeout = 5

                params.forEachIndexed { index, param ->
                    statement.setObject(index + 1, param)
                }

                statement.executeQuery().use { resultSet ->
                    if (resultSet.next()) mapper(resultSet) else null
                }
            }
        }
    }

    /**
     * 안전한 업데이트 실행 (타임아웃 포함)
     */
    private fun executeUpdateSafe(query: String, params: List<Any?>): Boolean {
        return database.getConnection().use { connection ->
            connection.prepareStatement(query).use { statement ->
                // 쿼리 타임아웃 설정 (5초)
                statement.queryTimeout = 5

                params.forEachIndexed { index, param ->
                    statement.setObject(index + 1, param)
                }

                statement.executeUpdate() > 0
            }
        }
    }

    /**
     * Circuit Breaker 상태 확인
     */
    private fun isCircuitBreakerOpen(): Boolean {
        if (circuitBreakerOpen) {
            if (System.currentTimeMillis() - lastFailureTime > circuitBreakerTimeoutMs) {
                circuitBreakerOpen = false
                plugin.logger.info("[AsyncDatabaseManager] Circuit breaker 복구됨")
                return false
            }
            return true
        }
        return false
    }

    /**
     * 데이터베이스 오류 처리
     */
    private fun handleDatabaseError(e: Exception) {
        plugin.logger.warning("[AsyncDatabaseManager] DB 오류: ${e.message}")

        // 연속적인 실패 시 Circuit Breaker 활성화
        val recentFailureRate = failedOperations.get().toDouble() / maxOf(totalOperations.get(), 1)
        if (recentFailureRate > 0.5 && totalOperations.get() > 10) {
            circuitBreakerOpen = true
            lastFailureTime = System.currentTimeMillis()
            plugin.logger.warning("[AsyncDatabaseManager] Circuit breaker 활성화됨 (실패율: ${String.format("%.2f", recentFailureRate * 100)}%)")
        }
    }

    /**
     * 캐시 정리
     */
    fun cleanupCache() {
        val iterator = cache.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.value.isExpired()) {
                iterator.remove()
            }
        }
    }

    /**
     * 통계 정보 반환
     */
    fun getStats(): Map<String, Any> {
        return mapOf(
            "pendingOperations" to pendingOperations.get(),
            "totalOperations" to totalOperations.get(),
            "failedOperations" to failedOperations.get(),
            "circuitBreakerOpen" to circuitBreakerOpen,
            "cacheSize" to cache.size,
            "activeThreads" to (executorService as ThreadPoolExecutor).activeCount,
            "queueSize" to (executorService as ThreadPoolExecutor).queue.size
        )
    }

    /**
     * 종료 시 정리
     */
    fun shutdown() {
        plugin.logger.info("[AsyncDatabaseManager] 종료 중...")

        executorService.shutdown()
        try {
            if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                executorService.shutdownNow()
                plugin.logger.warning("[AsyncDatabaseManager] 강제 종료됨")
            }
        } catch (e: InterruptedException) {
            executorService.shutdownNow()
        }

        cache.clear()
        plugin.logger.info("[AsyncDatabaseManager] 종료 완료")
    }
}