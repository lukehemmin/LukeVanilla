package com.lukehemmin.lukeVanilla.System.FleaMarket

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.event.player.AsyncPlayerChatEvent
import org.bukkit.event.player.PlayerQuitEvent
import com.nexomc.nexo.api.NexoItems
import org.bukkit.inventory.ItemStack
import java.text.SimpleDateFormat
import java.util.*

/**
 * 플리마켓 GUI
 */
class FleaMarketGUI(
    private val service: FleaMarketService
) : Listener {
    
    companion object {
        private const val MARKET_TITLE = "§6§l플리마켓"
        private const val HISTORY_TITLE = "§6§l거래 내역"
        private const val MY_ITEMS_TITLE = "§6§l내 상품"
        private const val INVENTORY_SIZE = 54  // 6줄
        private const val ITEMS_PER_PAGE = 45
    }
    
    // GUI를 연 플레이어 추적 (플레이어 UUID -> GUI 상태)
    // 외부에서 접근 가능하도록 public getter 제공 (수정은 불가능하게)
    private val guiStates = mutableMapOf<UUID, GuiState>()
    
    fun getGuiStates(): Map<UUID, GuiState> = guiStates.toMap()
    
    // 검색 대기 중인 플레이어 (UUID -> 이전 GUI 상태)
    private val searchWaiters = mutableMapOf<UUID, SearchWaitState>()
    data class SearchWaitState(
        val prevState: GuiState,
        val startedAt: Long,
        val timeoutTask: org.bukkit.scheduler.BukkitTask
    )
    
    data class GuiState(
        val type: GuiType,
        val page: Int = 1,
        val sortType: SortType = SortType.LATEST,
        val searchQuery: String? = null,
        val filterType: MarketTransactionType? = null // 거래 내역 필터용
    )
    
    enum class GuiType {
        MARKET_MAIN,
        TRANSACTION_HISTORY,
        MY_ITEMS
    }
    
    enum class SortType(val displayName: String) {
        LATEST("최신순"),
        OLDEST("오래된순"),
        PRICE_HIGH("가격 높은순"),
        PRICE_LOW("가격 낮은순");
        
        fun next(): SortType {
            val values = values()
            val nextOrdinal = (this.ordinal + 1) % values.size
            return values[nextOrdinal]
        }
    }
    
    /**
     * 모든 시청자의 GUI 갱신 (현재 페이지/정렬 유지)
     */
    fun refreshAllViewers() {
        guiStates.forEach { (uuid, state) ->
            val player = Bukkit.getPlayer(uuid)
            if (player != null && player.isOnline) {
                when (state.type) {
                    GuiType.MARKET_MAIN -> openMarket(player, state.page, state.sortType, state.searchQuery)
                    GuiType.MY_ITEMS -> openMyItems(player, state.page)
                    // 거래 내역은 실시간 갱신이 덜 중요하므로 제외하거나 필요시 추가
                    GuiType.TRANSACTION_HISTORY -> {} 
                }
            }
        }
    }

    /**
     * 마켓 메인 GUI 열기
     */
    fun openMarket(player: Player, page: Int = 1, sortType: SortType = SortType.LATEST, searchQuery: String? = null) {
        val inventory = Bukkit.createInventory(null, INVENTORY_SIZE, MARKET_TITLE)
        
        // 아이템 목록 가져오기 및 필터링/정렬
        var items = service.getAllItems()
        
        // 검색 필터
        if (searchQuery != null) {
            items = items.filter { item ->
                try {
                    val itemStack = ItemSerializer.deserialize(item.itemData)
                    val itemName = ItemSerializer.getDisplayName(itemStack)
                    matchesSearch(itemName, searchQuery)
                } catch (e: Exception) {
                    false
                }
            }
        }
        
        // 정렬
        items = when (sortType) {
            SortType.LATEST -> items.sortedByDescending { it.registeredAt }
            SortType.OLDEST -> items.sortedBy { it.registeredAt }
            SortType.PRICE_HIGH -> items.sortedByDescending { it.price }
            SortType.PRICE_LOW -> items.sortedBy { it.price }
        }
        
        val totalItems = items.size
        val totalPages = (totalItems + ITEMS_PER_PAGE - 1) / ITEMS_PER_PAGE
        val currentPage = if (page < 1) 1 else if (page > totalPages && totalPages > 0) totalPages else page
        
        val startIndex = (currentPage - 1) * ITEMS_PER_PAGE
        val endIndex = minOf(startIndex + ITEMS_PER_PAGE, totalItems)
        
        // 아이템 표시
        for (i in startIndex until endIndex) {
            val item = items[i]
            val slot = i - startIndex
            
            try {
                val itemStack = ItemSerializer.deserialize(item.itemData)
                val meta = itemStack.itemMeta
                
                if (meta != null) {
                    val lore = mutableListOf<String>()
                    lore.add("§7━━━━━━━━━━━━━━━━━━━━")
                    lore.add("§e판매자: §f${item.sellerName}")
                    lore.add("§e가격: §f${item.price.toLong()}원")
                    lore.add("§e등록일: §f${formatDate(item.registeredAt)}")
                    lore.add("§7━━━━━━━━━━━━━━━━━━━━")
                    
                    if (item.sellerUuid == player.uniqueId) {
                        lore.add("§a우클릭: §f회수")
                    } else {
                        lore.add("§a좌클릭: §f구매")
                    }
                    
                    // 기존 lore 보존
                    if (meta.hasLore()) {
                        lore.add("§7")
                        lore.add("§7§o[아이템 설명]")
                        meta.lore?.forEach { lore.add(it) }
                    }
                    
                    meta.lore = lore
                    itemStack.itemMeta = meta
                }
                
                inventory.setItem(slot, itemStack)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        // 하단 메뉴 버튼들
        // 45: 새로고침, 46: 내 상품, 47: 거래 내역
        inventory.setItem(45, createButton(Material.PAPER, "§a새로고침", listOf("§7클릭하여 목록을 갱신합니다"), "refresh"))
        inventory.setItem(46, createButton(Material.PAPER, "§e내 상품", listOf("§7내가 등록한 아이템을 확인합니다"), "home"))
        inventory.setItem(47, createButton(Material.PAPER, "§6거래 내역", listOf("§7거래 내역을 확인합니다"), "notify"))
        
        // 48: 이전 페이지, 49: 페이지 정보, 50: 다음 페이지
        if (currentPage > 1) {
            inventory.setItem(48, createButton(Material.PAPER, "§e이전 페이지", listOf("§7이전 페이지로 이동합니다"), "arrow_left"))
        } else {
            inventory.setItem(48, createButton(Material.GRAY_DYE, "§7이전 페이지", listOf("§7이전 페이지가 없습니다"), "arrow_left"))
        }
        
        inventory.setItem(49, createButton(Material.PAPER, "§e페이지 $currentPage / ${if (totalPages == 0) 1 else totalPages}", listOf("§7현재 페이지 정보입니다"), "info"))
        
        if (currentPage < totalPages) {
            inventory.setItem(50, createButton(Material.PAPER, "§e다음 페이지", listOf("§7다음 페이지로 이동합니다"), "arrow_right"))
        } else {
            inventory.setItem(50, createButton(Material.GRAY_DYE, "§7다음 페이지", listOf("§7다음 페이지가 없습니다"), "arrow_right"))
        }
        
        // 51: 정렬 방식, 52: 검색, 53: 닫기
        inventory.setItem(51, createButton(Material.PAPER, "§b정렬: ${sortType.displayName}", listOf("§7클릭하여 정렬 방식을 변경합니다"), "settings"))
        
        val searchLore = if (searchQuery != null) listOf("§7현재 검색어: §f$searchQuery", "§7클릭하여 검색어를 변경합니다", "§7우클릭하여 검색을 초기화합니다") else listOf("§7클릭하여 아이템 이름을 검색합니다")
        inventory.setItem(52, createButton(Material.PAPER, "§b검색", searchLore, "search"))
        
        inventory.setItem(53, createButton(Material.PAPER, "§c닫기", listOf("§7GUI를 닫습니다"), "cross"))
        
        player.openInventory(inventory)
        guiStates[player.uniqueId] = GuiState(GuiType.MARKET_MAIN, currentPage, sortType, searchQuery)
    }
    
    /**
     * 거래 내역 GUI 열기
     */
    fun openTransactionHistory(player: Player, page: Int = 1, filterType: MarketTransactionType? = null) {
        val title = if (filterType == null) HISTORY_TITLE else "$HISTORY_TITLE - ${getTypeKorean(filterType)}"
        val inventory = Bukkit.createInventory(null, INVENTORY_SIZE, title)
        
        // 거래 내역 가져오기
        val logs = if (filterType == null) {
            service.getPlayerLogs(player.uniqueId, 1000) // 충분히 많이 가져옴
        } else {
            service.getPlayerLogsByType(player.uniqueId, filterType, 1000)
        }
        
        val totalItems = logs.size
        val totalPages = (totalItems + ITEMS_PER_PAGE - 1) / ITEMS_PER_PAGE
        val currentPage = if (page < 1) 1 else if (page > totalPages && totalPages > 0) totalPages else page
        
        val startIndex = (currentPage - 1) * ITEMS_PER_PAGE
        val endIndex = minOf(startIndex + ITEMS_PER_PAGE, totalItems)
        
        // 거래 내역 표시
        for (i in startIndex until endIndex) {
            val log = logs[i]
            val slot = i - startIndex
            
            // 아이템 데이터가 있으면 실제 아이템 표시, 없으면 아이콘 사용
            val icon = try {
                if (log.itemData != null) {
                    val item = ItemSerializer.deserialize(log.itemData)
                    // GUI용 아이콘이므로 수량은 1로 설정
                    item.amount = 1
                    item
                } else {
                    when (log.transactionType) {
                        MarketTransactionType.REGISTER -> ItemStack(Material.PAPER)
                        MarketTransactionType.SELL -> ItemStack(Material.EMERALD)
                        MarketTransactionType.BUY -> ItemStack(Material.DIAMOND)
                        MarketTransactionType.WITHDRAW -> ItemStack(Material.CHEST)
                    }
                }
            } catch (e: Exception) {
                ItemStack(Material.BARRIER)
            }
            
            val typeText = when (log.transactionType) {
                MarketTransactionType.REGISTER -> "§e등록"
                MarketTransactionType.SELL -> "§a판매"
                MarketTransactionType.BUY -> "§b구매"
                MarketTransactionType.WITHDRAW -> "§7회수"
            }
            
            val meta = icon.itemMeta
            if (meta != null) {
                // 아이템 이름으로 표시 (기존 코드 대신 itemStack의 DisplayName 사용)
                val displayName = if (meta.hasDisplayName()) {
                    meta.displayName
                } else {
                    // 번역된 이름 가져오기 시도 또는 타입명 사용
                    // 여기서는 로그에 저장된 itemName을 우선 사용하고, 없으면 타입명 사용
                    log.itemName
                }
                
                meta.setDisplayName("§f$displayName")
                val lore = mutableListOf<String>()
                lore.add("§7━━━━━━━━━━━━━━━━━━━━")
                lore.add("§e거래 유형: $typeText")
                lore.add("§e가격: §f${log.price.toLong()}원")
                
                if (log.counterpartName != null) {
                    lore.add("§e상대방: §f${log.counterpartName}")
                }
                
                lore.add("§e거래 시간: §f${formatDate(log.transactionAt)}")
                lore.add("§7━━━━━━━━━━━━━━━━━━━━")
                
                // 기존 아이템 로어가 있다면 추가 (구분선 아래에)
                if (meta.hasLore()) {
                    lore.add("§7§o[아이템 정보]")
                    meta.lore?.forEach { lore.add(it) }
                }
                
                meta.lore = lore
                icon.itemMeta = meta
            }
            
            inventory.setItem(slot, icon)
        }
        
        // 하단 메뉴
        // 45: 새로고침, 46: 판매 내역, 47: 구매 내역
        inventory.setItem(45, createButton(Material.PAPER, "§a새로고침", listOf("§7클릭하여 목록을 갱신합니다"), "refresh"))
        inventory.setItem(46, createButton(Material.EMERALD, "§a판매 내역", listOf("§7판매한 아이템만 표시합니다")))
        inventory.setItem(47, createButton(Material.DIAMOND, "§b구매 내역", listOf("§7구매한 아이템만 표시합니다")))
        
        // 48: 이전 페이지, 49: 페이지 정보, 50: 다음 페이지
        if (currentPage > 1) {
            inventory.setItem(48, createButton(Material.PAPER, "§e이전 페이지", listOf("§7이전 페이지로 이동합니다"), "arrow_left"))
        } else {
            inventory.setItem(48, createButton(Material.GRAY_DYE, "§7이전 페이지", listOf("§7이전 페이지가 없습니다"), "arrow_left"))
        }
        
        inventory.setItem(49, createButton(Material.PAPER, "§e페이지 $currentPage / ${if (totalPages == 0) 1 else totalPages}", listOf("§7현재 페이지 정보입니다"), "info"))
        
        if (currentPage < totalPages) {
            inventory.setItem(50, createButton(Material.PAPER, "§e다음 페이지", listOf("§7다음 페이지로 이동합니다"), "arrow_right"))
        } else {
            inventory.setItem(50, createButton(Material.GRAY_DYE, "§7다음 페이지", listOf("§7다음 페이지가 없습니다"), "arrow_right"))
        }
        
        // 51: 회수 내역, 52: 전체 보기, 53: 뒤로가기
        inventory.setItem(51, createButton(Material.CHEST, "§7회수 내역", listOf("§7회수한 아이템만 표시합니다")))
        inventory.setItem(52, createButton(Material.PAPER, "§e전체 보기", listOf("§7모든 거래 내역을 표시합니다"), "check"))
        inventory.setItem(53, createButton(Material.PAPER, "§c뒤로가기", listOf("§7마켓으로 돌아갑니다"), "arrow_left"))
        
        player.openInventory(inventory)
        guiStates[player.uniqueId] = GuiState(GuiType.TRANSACTION_HISTORY, currentPage, SortType.LATEST, null, filterType)
    }
    
    /**
     * 내 상품만 보기
     */
    private fun openMyItems(player: Player, page: Int = 1) {
        val inventory = Bukkit.createInventory(null, INVENTORY_SIZE, MY_ITEMS_TITLE)
        
        val items = service.getItemsBySeller(player.uniqueId)
        
        val totalItems = items.size
        val totalPages = (totalItems + ITEMS_PER_PAGE - 1) / ITEMS_PER_PAGE
        val currentPage = if (page < 1) 1 else if (page > totalPages && totalPages > 0) totalPages else page
        
        val startIndex = (currentPage - 1) * ITEMS_PER_PAGE
        val endIndex = minOf(startIndex + ITEMS_PER_PAGE, totalItems)
        
        for (i in startIndex until endIndex) {
            val item = items[i]
            val slot = i - startIndex
            
            try {
                val itemStack = ItemSerializer.deserialize(item.itemData)
                val meta = itemStack.itemMeta
                
                if (meta != null) {
                    val lore = mutableListOf<String>()
                    lore.add("§7━━━━━━━━━━━━━━━━━━━━")
                    lore.add("§e가격: §f${item.price.toLong()}원")
                    lore.add("§e등록일: §f${formatDate(item.registeredAt)}")
                    lore.add("§7━━━━━━━━━━━━━━━━━━━━")
                    lore.add("§a우클릭: §f회수")
                    
                    if (meta.hasLore()) {
                        lore.add("§7")
                        lore.add("§7§o[아이템 설명]")
                        meta.lore?.forEach { lore.add(it) }
                    }
                    
                    meta.lore = lore
                    itemStack.itemMeta = meta
                }
                
                inventory.setItem(slot, itemStack)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        // 하단 메뉴
        // 45: 새로고침
        inventory.setItem(45, createButton(Material.PAPER, "§a새로고침", listOf("§7클릭하여 목록을 갱신합니다"), "refresh"))
        
        // 48: 이전 페이지, 49: 페이지 정보, 50: 다음 페이지
        if (currentPage > 1) {
            inventory.setItem(48, createButton(Material.PAPER, "§e이전 페이지", listOf("§7이전 페이지로 이동합니다"), "arrow_left"))
        } else {
            inventory.setItem(48, createButton(Material.GRAY_DYE, "§7이전 페이지", listOf("§7이전 페이지가 없습니다"), "arrow_left"))
        }
        
        inventory.setItem(49, createButton(Material.PAPER, "§e페이지 $currentPage / ${if (totalPages == 0) 1 else totalPages}", listOf("§7현재 페이지 정보입니다"), "info"))
        
        if (currentPage < totalPages) {
            inventory.setItem(50, createButton(Material.PAPER, "§e다음 페이지", listOf("§7다음 페이지로 이동합니다"), "arrow_right"))
        } else {
            inventory.setItem(50, createButton(Material.GRAY_DYE, "§7다음 페이지", listOf("§7다음 페이지가 없습니다"), "arrow_right"))
        }
        
        // 53: 뒤로가기
        inventory.setItem(53, createButton(Material.PAPER, "§c뒤로가기", listOf("§7마켓으로 돌아갑니다"), "arrow_left"))
        
        player.openInventory(inventory)
        guiStates[player.uniqueId] = GuiState(GuiType.MY_ITEMS, currentPage)
    }
    
    /**
     * 인벤토리 클릭 이벤트 처리
     */
    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val state = guiStates[player.uniqueId] ?: return
        
        event.isCancelled = true  // 아이템 이동 방지
        
        val clickedItem = event.currentItem ?: return
        if (clickedItem.type == Material.AIR) return
        
        val slot = event.slot
        
        when (state.type) {
            GuiType.MARKET_MAIN -> handleMarketClick(player, slot, event.isLeftClick, event.isRightClick, state)
            GuiType.TRANSACTION_HISTORY -> handleHistoryClick(player, slot, state)
            GuiType.MY_ITEMS -> handleMyItemsClick(player, slot, event.isLeftClick, state)
        }
    }
    
    @EventHandler
    fun onInventoryDrag(event: InventoryDragEvent) {
        val player = event.whoClicked as? Player ?: return
        if (!guiStates.containsKey(player.uniqueId)) return
        event.isCancelled = true
    }
    
    /**
     * 마켓 GUI 클릭 처리
     */
    private fun handleMarketClick(player: Player, slot: Int, isLeftClick: Boolean, isRightClick: Boolean, state: GuiState) {
        when (slot) {
            45 -> { // 새로고침
                openMarket(player, state.page, state.sortType, state.searchQuery)
            }
            46 -> { // 내 상품
                openMyItems(player)
            }
            47 -> { // 거래 내역
                openTransactionHistory(player)
            }
            48 -> { // 이전 페이지
                if (state.page > 1) {
                    openMarket(player, state.page - 1, state.sortType, state.searchQuery)
                }
            }
            49 -> { // 페이지 정보 (동작 없음)
            }
            50 -> { // 다음 페이지
                // 다음 페이지가 있는지 확인하는 로직은 openMarket 내에서 처리됨 (간단하게는 그냥 호출해도 됨)
                openMarket(player, state.page + 1, state.sortType, state.searchQuery)
            }
            51 -> { // 정렬 방식
                val nextSort = state.sortType.next()
                openMarket(player, state.page, nextSort, state.searchQuery)
            }
            52 -> { // 검색
                if (isRightClick) {
                    openMarket(player, 1, state.sortType, null)
                } else {
                    player.closeInventory()
                    val uuid = player.uniqueId
                    val startedAt = System.currentTimeMillis()
                    player.sendMessage("§a검색어를 채팅창에 입력해주세요. (60초 내 입력, 취소하려면 '취소')")
                    val task = Bukkit.getScheduler().runTaskLater(service.plugin, Runnable {
                        val waiter = searchWaiters[uuid]
                        if (waiter != null && waiter.startedAt == startedAt) {
                            val p = Bukkit.getPlayer(uuid)
                            searchWaiters.remove(uuid)
                            if (p != null && p.isOnline) {
                                p.sendMessage("§c검색 시간이 초과되었습니다.")
                                openMarket(p, waiter.prevState.page, waiter.prevState.sortType, waiter.prevState.searchQuery)
                            }
                        }
                    }, 20L * 60)
                    searchWaiters[uuid] = SearchWaitState(state, startedAt, task)
                }
            }
            53 -> { // 닫기
                player.closeInventory()
                guiStates.remove(player.uniqueId)
            }
            in 0..44 -> { // 아이템 클릭
                // 현재 화면에 표시된 아이템 목록을 다시 계산해서 클릭된 아이템 찾기
                var items = service.getAllItems()
                
                if (state.searchQuery != null) {
                    items = items.filter { item ->
                        try {
                            val itemStack = ItemSerializer.deserialize(item.itemData)
                            val itemName = ItemSerializer.getDisplayName(itemStack)
                            matchesSearch(itemName, state.searchQuery)
                        } catch (e: Exception) {
                            false
                        }
                    }
                }
                
                items = when (state.sortType) {
                    SortType.LATEST -> items.sortedByDescending { it.registeredAt }
                    SortType.OLDEST -> items.sortedBy { it.registeredAt }
                    SortType.PRICE_HIGH -> items.sortedByDescending { it.price }
                    SortType.PRICE_LOW -> items.sortedBy { it.price }
                }
                
                val startIndex = (state.page - 1) * ITEMS_PER_PAGE
                val itemIndex = startIndex + slot
                
                if (itemIndex < items.size) {
                    val item = items[itemIndex]
                    
                    if (item.sellerUuid == player.uniqueId && !isLeftClick) {
                        // 본인 아이템 우클릭 -> 회수
                        service.withdrawItem(player, item.id)
                        // 목록 갱신
                        openMarket(player, state.page, state.sortType, state.searchQuery)
                    } else if (item.sellerUuid != player.uniqueId && isLeftClick) {
                        // 타인 아이템 좌클릭 -> 구매
                        if (service.purchaseItem(player, item.id)) {
                            // 구매 성공 시 목록 갱신
                            openMarket(player, state.page, state.sortType, state.searchQuery)
                        }
                    }
                }
            }
        }
    }
    
    /**
     * 거래 내역 GUI 클릭 처리
     */
    private fun handleHistoryClick(player: Player, slot: Int, state: GuiState) {
        when (slot) {
            45 -> { // 새로고침
                openTransactionHistory(player, state.page, state.filterType)
            }
            46 -> { // 판매 내역
                openTransactionHistory(player, 1, MarketTransactionType.SELL)
            }
            47 -> { // 구매 내역
                openTransactionHistory(player, 1, MarketTransactionType.BUY)
            }
            48 -> { // 이전 페이지
                if (state.page > 1) {
                    openTransactionHistory(player, state.page - 1, state.filterType)
                }
            }
            50 -> { // 다음 페이지
                openTransactionHistory(player, state.page + 1, state.filterType)
            }
            51 -> { // 회수 내역
                openTransactionHistory(player, 1, MarketTransactionType.WITHDRAW)
            }
            52 -> { // 전체 보기
                openTransactionHistory(player, 1, null)
            }
            53 -> { // 뒤로가기
                openMarket(player)
            }
        }
    }
    
    /**
     * 내 상품 GUI 클릭 처리
     */
    private fun handleMyItemsClick(player: Player, slot: Int, isLeftClick: Boolean, state: GuiState) {
        when (slot) {
            45 -> { // 새로고침
                openMyItems(player, state.page)
            }
            48 -> { // 이전 페이지
                if (state.page > 1) {
                    openMyItems(player, state.page - 1)
                }
            }
            50 -> { // 다음 페이지
                openMyItems(player, state.page + 1)
            }
            53 -> { // 뒤로가기
                openMarket(player)
            }
            in 0..44 -> { // 아이템 클릭 (회수)
                val items = service.getItemsBySeller(player.uniqueId)
                val startIndex = (state.page - 1) * ITEMS_PER_PAGE
                val itemIndex = startIndex + slot
                
                if (itemIndex < items.size) {
                    val item = items[itemIndex]
                    if (!isLeftClick) { // 우클릭 회수
                        service.withdrawItem(player, item.id)
                        openMyItems(player, state.page)
                    }
                }
            }
        }
    }
    
    /**
     * 채팅 입력 처리 (검색)
     */
    @EventHandler
    fun onPlayerChat(event: AsyncPlayerChatEvent) {
        val player = event.player
        if (!searchWaiters.containsKey(player.uniqueId)) return
        
        event.isCancelled = true
        val message = event.message
        val waiter = searchWaiters.remove(player.uniqueId) ?: return
        val previousState = waiter.prevState
        waiter.timeoutTask.cancel()
        
        Bukkit.getScheduler().runTask(service.plugin, Runnable {
            if (message.equals("취소", ignoreCase = true)) {
                player.sendMessage("§c검색이 취소되었습니다.")
                openMarket(player, previousState.page, previousState.sortType, previousState.searchQuery)
            } else {
                player.sendMessage("§a'$message' 검색 결과입니다.")
                openMarket(player, 1, previousState.sortType, message)
            }
        })
    }
    
    /**
     * GUI 버튼 생성 (Nexo 아이템 지원)
     */
    private fun createButton(material: Material, name: String, lore: List<String>, nexoItemId: String? = null): ItemStack {
        val item = if (nexoItemId != null) {
            NexoItems.itemFromId(nexoItemId)?.build() ?: ItemStack(material)
        } else {
            ItemStack(material)
        }
        
        val meta = item.itemMeta
        if (meta != null) {
            meta.setDisplayName(name)
            meta.lore = lore
            item.itemMeta = meta
        }
        
        return item
    }
    
    /**
     * 날짜 포맷팅
     */
    private fun formatDate(timestamp: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm")
        return sdf.format(Date(timestamp))
    }
    
    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val uuid = event.player.uniqueId
        val waiter = searchWaiters.remove(uuid)
        if (waiter != null) {
            waiter.timeoutTask.cancel()
        }
    }
    
    private fun matchesSearch(itemName: String, query: String): Boolean {
        val baseTokens = query.split(Regex("\\s+")).map { it.trim() }.filter { it.isNotEmpty() }
        val extraTokens = baseTokens.flatMap { it.split("의") }.map { it.trim() }.filter { it.isNotEmpty() }
        val tokens = (baseTokens + extraTokens).distinct()
        return tokens.any { token -> itemName.contains(token, ignoreCase = true) }
    }
    
    /**
     * 거래 유형 한글명
     */
    private fun getTypeKorean(type: MarketTransactionType): String {
        return when (type) {
            MarketTransactionType.REGISTER -> "등록"
            MarketTransactionType.SELL -> "판매"
            MarketTransactionType.BUY -> "구매"
            MarketTransactionType.WITHDRAW -> "회수"
        }
    }
    
    /**
     * 플레이어가 인벤토리를 닫을 때 추적 제거
     */
    @EventHandler
    fun onInventoryClose(event: org.bukkit.event.inventory.InventoryCloseEvent) {
        val player = event.player as? Player ?: return
        val uuid = player.uniqueId
        
        Bukkit.getScheduler().runTask(service.plugin, Runnable {
            val currentTitle = PlainTextComponentSerializer.plainText().serialize(player.openInventory.title())
            val isFleaOpen = currentTitle.contains("플리마켓") || currentTitle.contains("거래 내역") || currentTitle.contains("내 상품")
            if (!isFleaOpen && !searchWaiters.containsKey(uuid)) {
                guiStates.remove(uuid)
            }
        })
    }
}
