package com.lukehemmin.lukeVanilla.System.Economy

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class EconomyService(private val repository: EconomyRepository) {

    // 메모리 캐시 (UUID -> Balance)
    private val balanceCache = ConcurrentHashMap<UUID, Double>()

    // 플레이어 접속 시 데이터 로드
    fun loadPlayer(player: Player) {
        repository.getBalanceAsync(player.uniqueId).thenAccept { balance ->
            balanceCache[player.uniqueId] = balance
        }
    }

    // 플레이어 접속 종료 시 데이터 언로드 (저장은 실시간으로 하므로 메모리 해제만)
    fun unloadPlayer(player: Player) {
        balanceCache.remove(player.uniqueId)
    }

    // 잔액 조회 (캐시 우선 -> 없으면 DB 조회)
    fun getBalance(player: Player): Double {
        return balanceCache[player.uniqueId] ?: 0.0
    }
    
    // 오프라인 플레이어 잔액 조회 (비동기 권장)
    fun getOfflineBalance(uuid: UUID): Double {
        // 캐시에 있으면 반환, 없으면 동기적으로 DB 조회 (주의: 메인 스레드에서 호출 시 블로킹 가능성 있음)
        // 안전을 위해 캐시에 없으면 0.0을 반환하고 비동기로 로드하는 것이 좋으나,
        // 정확성을 위해 여기서는 repository를 통해 가져오도록 설계 (추후 비동기 API로 확장 필요)
        return balanceCache[uuid] ?: repository.getBalanceAsync(uuid).join()
    }

    // [입금] 돈 지급
    fun deposit(player: Player, amount: Double, type: TransactionType, description: String) {
        if (amount <= 0) return

        val uuid = player.uniqueId
        val currentBalance = getBalance(player)
        val newBalance = currentBalance + amount

        // 1. 캐시 업데이트
        balanceCache[uuid] = newBalance

        // 2. DB 업데이트 (비동기)
        repository.updateBalance(uuid, newBalance)

        // 3. 로그 기록 (비동기)
        repository.insertLog(uuid, type, amount, newBalance, null, description)
    }

    // [입금] 오프라인 플레이어 돈 지급 (UUID 직접 사용)
    fun depositOffline(uuid: UUID, amount: Double, type: TransactionType, relatedUuid: UUID?, description: String) {
        if (amount <= 0) return

        val currentBalance = getOfflineBalance(uuid)
        val newBalance = currentBalance + amount

        // 1. 캐시 업데이트
        balanceCache[uuid] = newBalance

        // 2. DB 업데이트 (비동기)
        repository.updateBalance(uuid, newBalance)

        // 3. 로그 기록 (비동기)
        repository.insertLog(uuid, type, amount, newBalance, relatedUuid, description)
    }

    // [출금] 돈 차감
    fun withdraw(player: Player, amount: Double, type: TransactionType, description: String): Boolean {
        if (amount <= 0) return false

        val uuid = player.uniqueId
        val currentBalance = getBalance(player)

        if (currentBalance < amount) {
            return false // 잔액 부족
        }

        val newBalance = currentBalance - amount

        // 1. 캐시 업데이트
        balanceCache[uuid] = newBalance

        // 2. DB 업데이트 (비동기)
        repository.updateBalance(uuid, newBalance)

        // 3. 로그 기록 (비동기) - 음수로 기록하지 않고 양수로 기록하되 타입으로 구분하거나,
        // 여기서는 amount를 음수로 기록하여 변동을 명확히 함.
        repository.insertLog(uuid, type, -amount, newBalance, null, description)

        return true
    }

    // [송금] 플레이어 간 이체
    fun transfer(sender: Player, receiver: Player, amount: Double, description: String): Boolean {
        if (sender.uniqueId == receiver.uniqueId) return false
        if (amount <= 0) return false

        val senderUuid = sender.uniqueId
        val receiverUuid = receiver.uniqueId

        // 동기화 블록을 사용하여 송금 도중 잔액이 변하는 것을 방지 (간단한 락)
        synchronized(senderUuid) {
            val senderBalance = getBalance(sender)
            if (senderBalance < amount) return false

            val receiverBalance = getBalance(receiver)

            val newSenderBalance = senderBalance - amount
            val newReceiverBalance = receiverBalance + amount

            // 1. 캐시 업데이트
            balanceCache[senderUuid] = newSenderBalance
            balanceCache[receiverUuid] = newReceiverBalance

            // 2. DB 업데이트
            repository.updateBalance(senderUuid, newSenderBalance)
            repository.updateBalance(receiverUuid, newReceiverBalance)

            // 3. 로그 기록 (양쪽 모두 기록)
            // 보낸 사람 로그
            repository.insertLog(senderUuid, TransactionType.SEND, -amount, newSenderBalance, receiverUuid, description)
            // 받은 사람 로그
            repository.insertLog(receiverUuid, TransactionType.RECEIVE, amount, newReceiverBalance, senderUuid, description)

            return true
        }
    }

    // 최근 거래 내역 조회
    fun getRecentLogs(player: Player): java.util.concurrent.CompletableFuture<List<EconomyLog>> {
        return repository.getRecentLogsAsync(player.uniqueId)
    }
}
