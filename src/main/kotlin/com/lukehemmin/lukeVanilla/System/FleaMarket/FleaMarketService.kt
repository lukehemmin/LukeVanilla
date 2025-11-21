package com.lukehemmin.lukeVanilla.System.FleaMarket

import com.lukehemmin.lukeVanilla.System.Economy.EconomyManager
import com.lukehemmin.lukeVanilla.System.Economy.TransactionType
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 플리마켓 비즈니스 로직 Service
 */
class FleaMarketService(
    private val repository: FleaMarketRepository,
    private val economyManager: EconomyManager
) {
    
    // 메모리 캐시 (아이템 ID -> MarketItem)
    private val itemCache = ConcurrentHashMap<Int, MarketItem>()
    
    companion object {
        const val MAX_ITEMS_PER_PLAYER = 10  // 플레이어당 최대 등록 개수
        const val MIN_PRICE = 1.0            // 최소 가격
        const val MAX_PRICE = 1000000000.0   // 최대 가격 (10억)
    }
    
    /**
     * 서버 시작 시 캐시 로드
     */
    fun loadCache() {
        repository.getAllItemsAsync().thenAccept { items ->
            itemCache.clear()
            items.forEach { item ->
                itemCache[item.id] = item
            }
            println("[플리마켓] ${items.size}개의 아이템을 캐시에 로드했습니다.")
        }
    }
    
    /**
     * 아이템 등록
     */
    fun registerItem(seller: Player, itemStack: ItemStack, price: Double): Boolean {
        // 1. 가격 유효성 검증
        if (price < MIN_PRICE || price > MAX_PRICE) {
            seller.sendMessage("§c가격은 ${MIN_PRICE.toInt()}원 ~ ${MAX_PRICE.toLong()}원 사이여야 합니다.")
            return false
        }
        
        // 2. 등록 개수 제한 확인
        val currentItems = itemCache.values.count { it.sellerUuid == seller.uniqueId }
        if (currentItems >= MAX_ITEMS_PER_PLAYER) {
            seller.sendMessage("§c최대 ${MAX_ITEMS_PER_PLAYER}개까지만 등록할 수 있습니다.")
            return false
        }
        
        // 3. 아이템 직렬화
        val itemData = try {
            ItemSerializer.serialize(itemStack)
        } catch (e: Exception) {
            seller.sendMessage("§c아이템 등록에 실패했습니다.")
            e.printStackTrace()
            return false
        }
        
        // 4. 아이템 이름 추출
        val itemName = if (itemStack.hasItemMeta() && itemStack.itemMeta?.hasDisplayName() == true) {
            itemStack.itemMeta?.displayName ?: itemStack.type.name
        } else {
            itemStack.type.name
        }
        
        // 5. DB에 저장
        val marketItem = MarketItem(
            id = -1, // AUTO_INCREMENT
            sellerUuid = seller.uniqueId,
            sellerName = seller.name,
            itemData = itemData,
            price = price,
            registeredAt = System.currentTimeMillis()
        )
        
        repository.insertItem(marketItem).thenAccept { generatedId ->
            if (generatedId > 0) {
                // 6. 캐시에 추가
                val savedItem = marketItem.copy(id = generatedId)
                itemCache[generatedId] = savedItem
                
                // 7. 플레이어 인벤토리에서 아이템 제거
                seller.inventory.setItemInMainHand(null)
                
                // 8. 등록 로그 기록
                val log = MarketLog(
                    id = -1,
                    playerUuid = seller.uniqueId,
                    playerName = seller.name,
                    transactionType = MarketTransactionType.REGISTER,
                    itemName = itemName,
                    itemData = itemData,
                    price = price,
                    counterpartUuid = null,
                    counterpartName = null,
                    transactionAt = System.currentTimeMillis(),
                    isNotified = false
                )
                repository.insertLog(log)
                
                // 9. 성공 메시지
                seller.sendMessage("§a§l[플리마켓] §f$itemName §a을(를) §f${price.toLong()}원§a에 등록했습니다!")
            } else {
                seller.sendMessage("§c아이템 등록에 실패했습니다.")
            }
        }
        
        return true
    }
    
    /**
     * 아이템 구매
     */
    fun purchaseItem(buyer: Player, itemId: Int): Boolean {
        // 1. 아이템 존재 여부 확인
        val item = itemCache[itemId]
        if (item == null) {
            buyer.sendMessage("§c해당 아이템을 찾을 수 없습니다. (이미 판매되었을 수 있습니다)")
            return false
        }
        
        // 2. 자기 자신이 등록한 아이템인지 확인
        if (item.sellerUuid == buyer.uniqueId) {
            buyer.sendMessage("§c자신이 등록한 아이템은 구매할 수 없습니다. 회수 기능을 이용하세요.")
            return false
        }
        
        // 3. 구매자 잔액 확인
        val buyerBalance = economyManager.getBalance(buyer)
        if (buyerBalance < item.price) {
            buyer.sendMessage("§c잔액이 부족합니다. (필요: ${item.price.toLong()}원, 보유: ${buyerBalance.toLong()}원)")
            return false
        }
        
        // 4. 인벤토리 여유 공간 확인
        if (buyer.inventory.firstEmpty() == -1) {
            buyer.sendMessage("§c인벤토리에 빈 공간이 없습니다.")
            return false
        }
        
        // 5. 아이템 역직렬화
        val itemStack = try {
            ItemSerializer.deserialize(item.itemData)
        } catch (e: Exception) {
            buyer.sendMessage("§c아이템을 불러오는 데 실패했습니다.")
            e.printStackTrace()
            return false
        }
        
        val itemName = if (itemStack.hasItemMeta() && itemStack.itemMeta?.hasDisplayName() == true) {
            itemStack.itemMeta?.displayName ?: itemStack.type.name
        } else {
            itemStack.type.name
        }
        
        // 6. 트랜잭션 시작 (itemCache 전체를 lock)
        synchronized(itemCache) {
            // 다시 한번 아이템 존재 확인 (동시성 체크)
            if (!itemCache.containsKey(itemId)) {
                buyer.sendMessage("§c해당 아이템을 찾을 수 없습니다. (이미 판매되었을 수 있습니다)")
                return false
            }
            
            // 구매자 돈 차감
            val withdrawSuccess = economyManager.withdraw(
                buyer,
                item.price,
                TransactionType.MARKET_BUY,
                "플리마켓 구매: ${item.sellerName}의 $itemName"
            )
            
            if (!withdrawSuccess) {
                buyer.sendMessage("§c결제에 실패했습니다.")
                return false
            }
            
            // 판매자 돈 지급 (오프라인 처리 가능)
            // 판매자가 온라인이면 deposit 사용, 오프라인이면 depositOffline 사용
            val seller = Bukkit.getPlayer(item.sellerUuid)
            if (seller != null && seller.isOnline) {
                // 온라인: deposit 메서드 사용
                economyManager.deposit(
                    seller,
                    item.price,
                    TransactionType.MARKET_SELL,
                    "플리마켓 판매: ${buyer.name}에게 $itemName"
                )
            } else {
                // 오프라인: depositOffline 메서드 사용
                economyManager.service.depositOffline(
                    item.sellerUuid,
                    item.price,
                    TransactionType.MARKET_SELL,
                    buyer.uniqueId,
                    "플리마켓 판매: ${buyer.name}에게 $itemName"
                )
            }
            
            // DB에서 아이템 삭제
            repository.deleteItem(itemId)
            
            // 캐시에서 제거
            itemCache.remove(itemId)
            
            // 구매자 거래 로그 기록
            val buyerLog = MarketLog(
                id = -1,
                playerUuid = buyer.uniqueId,
                playerName = buyer.name,
                transactionType = MarketTransactionType.BUY,
                itemName = itemName,
                itemData = item.itemData,
                price = item.price,
                counterpartUuid = item.sellerUuid,
                counterpartName = item.sellerName,
                transactionAt = System.currentTimeMillis(),
                isNotified = false
            )
            repository.insertLog(buyerLog)
            
            // 판매자 거래 로그 기록 (is_notified = 0)
            val sellerLog = MarketLog(
                id = -1,
                playerUuid = item.sellerUuid,
                playerName = item.sellerName,
                transactionType = MarketTransactionType.SELL,
                itemName = itemName,
                itemData = item.itemData,
                price = item.price,
                counterpartUuid = buyer.uniqueId,
                counterpartName = buyer.name,
                transactionAt = System.currentTimeMillis(),
                isNotified = false  // 초기값은 미확인
            )
            repository.insertLog(sellerLog)
            
            // 구매자 인벤토리에 아이템 지급
            buyer.inventory.addItem(itemStack)
            
            // 판매 알림 처리
            if (seller != null && seller.isOnline) {
                // 온라인: 즉시 알림 전송
                sendInstantSaleNotification(seller, itemName, buyer.name, item.price)
                // 알림 확인 처리 (is_notified = 1)
                repository.markSalesAsNotifiedAsync(item.sellerUuid)
            }
            // 오프라인: is_notified = 0 유지 (다음 접속 시 표시)
            
            // 완료 메시지
            buyer.sendMessage("§a§l[플리마켓] §f$itemName §a을(를) §f${item.price.toLong()}원§a에 구매했습니다!")
            
            if (seller != null && seller.isOnline) {
                seller.sendMessage("§a§l[플리마켓] §f$itemName §a이(가) §f${buyer.name}§a님에게 §f${item.price.toLong()}원§a에 판매되었습니다!")
            }
        }
        
        return true
    }
    
    /**
     * 아이템 회수
     */
    fun withdrawItem(seller: Player, itemId: Int): Boolean {
        // 1. 아이템 존재 여부 확인
        val item = itemCache[itemId]
        if (item == null) {
            seller.sendMessage("§c해당 아이템을 찾을 수 없습니다.")
            return false
        }
        
        // 2. 본인이 등록한 아이템인지 확인
        if (item.sellerUuid != seller.uniqueId) {
            seller.sendMessage("§c본인이 등록한 아이템만 회수할 수 있습니다.")
            return false
        }
        
        // 3. 인벤토리 여유 공간 확인
        if (seller.inventory.firstEmpty() == -1) {
            seller.sendMessage("§c인벤토리에 빈 공간이 없습니다.")
            return false
        }
        
        // 4. 아이템 역직렬화
        val itemStack = try {
            ItemSerializer.deserialize(item.itemData)
        } catch (e: Exception) {
            seller.sendMessage("§c아이템을 불러오는 데 실패했습니다.")
            e.printStackTrace()
            return false
        }
        
        val itemName = if (itemStack.hasItemMeta() && itemStack.itemMeta?.hasDisplayName() == true) {
            itemStack.itemMeta?.displayName ?: itemStack.type.name
        } else {
            itemStack.type.name
        }
        
        // 5. DB에서 아이템 삭제
        repository.deleteItem(itemId)
        
        // 6. 캐시에서 제거
        itemCache.remove(itemId)
        
        // 7. 플레이어 인벤토리에 반환
        seller.inventory.addItem(itemStack)
        
        // 8. 회수 로그 기록
        val log = MarketLog(
            id = -1,
            playerUuid = seller.uniqueId,
            playerName = seller.name,
            transactionType = MarketTransactionType.WITHDRAW,
            itemName = itemName,
            itemData = item.itemData,
            price = item.price,
            counterpartUuid = null,
            counterpartName = null,
            transactionAt = System.currentTimeMillis(),
            isNotified = false
        )
        repository.insertLog(log)
        
        // 9. 성공 메시지
        seller.sendMessage("§a§l[플리마켓] §f$itemName §a을(를) 회수했습니다!")
        
        return true
    }
    
    /**
     * 모든 아이템 조회
     */
    fun getAllItems(): List<MarketItem> {
        return itemCache.values.sortedByDescending { it.registeredAt }
    }
    
    /**
     * 판매자별 아이템 조회
     */
    fun getItemsBySeller(uuid: UUID): List<MarketItem> {
        return itemCache.values.filter { it.sellerUuid == uuid }
            .sortedByDescending { it.registeredAt }
    }
    
    /**
     * 거래 내역 조회
     */
    fun getPlayerLogs(uuid: UUID, limit: Int = 50): List<MarketLog> {
        return repository.getPlayerLogsAsync(uuid, limit).join()
    }
    
    /**
     * 거래 유형별 내역 조회
     */
    fun getPlayerLogsByType(uuid: UUID, type: MarketTransactionType, limit: Int = 50): List<MarketLog> {
        return repository.getPlayerLogsByTypeAsync(uuid, type, limit).join()
    }
    
    /**
     * 미확인 판매 내역 조회
     */
    fun getUnnotifiedSales(uuid: UUID): List<MarketLog> {
        return repository.getUnnotifiedSalesAsync(uuid).join()
    }
    
    /**
     * 판매 알림 확인 처리
     */
    fun markSalesAsNotified(uuid: UUID) {
        repository.markSalesAsNotifiedAsync(uuid)
    }
    
    /**
     * 온라인 플레이어에게 즉시 판매 알림 전송
     */
    fun sendInstantSaleNotification(seller: Player, itemName: String, buyerName: String, price: Double) {
        seller.sendMessage("")
        seller.sendMessage("§6§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        seller.sendMessage("§a§l               [플리마켓 판매 알림]")
        seller.sendMessage("§6§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        seller.sendMessage("  §a§l[판매완료] §f$itemName§a이(가) §f$buyerName§a님에게 §f${price.toLong()}원§a에 판매되었습니다!")
        seller.sendMessage("§6§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        seller.sendMessage("")
    }
}
