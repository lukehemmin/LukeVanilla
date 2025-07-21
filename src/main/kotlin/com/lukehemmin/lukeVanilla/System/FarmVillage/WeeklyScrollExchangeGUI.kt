package com.lukehemmin.lukeVanilla.System.FarmVillage

import com.lukehemmin.lukeVanilla.Main
import com.nexomc.nexo.api.NexoItems
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import java.util.*

class WeeklyScrollExchangeGUI(
    private val plugin: Main,
    private val farmVillageData: FarmVillageData,
    private val weeklyScrollRotationSystem: WeeklyScrollRotationSystem
) : Listener {
    
    private var farmVillageManager: FarmVillageManager? = null
    
    fun setFarmVillageManager(manager: FarmVillageManager) {
        this.farmVillageManager = manager
    }
    
    // GUI를 연 주차를 저장하여 실시간 검증에 사용
    private val playerGuiOpenWeeks = mutableMapOf<UUID, String>()
    
    // 네비게이션용 PersistentDataContainer 키
    private val navKey = NamespacedKey(plugin, "weekly_scroll_nav")
    private val scrollKey = NamespacedKey(plugin, "weekly_scroll_item")
    
    // 금별작물 아이템 ID들
    private val goldenStarIds = setOf(
        "cabbage_golden_star", "chinese_cabbage_golden_star", "garlic_golden_star",
        "corn_golden_star", "pineapple_golden_star", "eggplant_golden_star"
    )
    
    // 교환 비용 (금별작물 개수)
    private val EXCHANGE_COST = 64

    /**
     * 주간 스크롤 교환 GUI 열기
     */
    fun openGUI(player: Player, page: Int = 1) {
        openWeeklyScrollExchangeGUI(player, page)
    }
    
    private fun openWeeklyScrollExchangeGUI(player: Player, page: Int = 1) {
        val currentWeek = weeklyScrollRotationSystem.getCurrentWeekString()
        val currentRotation = weeklyScrollRotationSystem.getCurrentRotation()
        
        // 이미 구매했는지 확인
        val alreadyPurchased = farmVillageData.hasPlayerPurchasedThisWeek(
            player.uniqueId, currentWeek
        )
        
        // 실시간 검증을 위해 GUI를 연 주차 저장
        playerGuiOpenWeeks[player.uniqueId] = currentWeek
        
        // 페이지 정보 계산
        val totalPages = getTotalPages(currentRotation)
        val validPage = page.coerceIn(1, totalPages)
        
        // GUI 생성 (5x9 = 45슬롯)
        val guiTitle = if (totalPages > 1) {
            "커스텀 아이템 교환권 교환 - ${currentRotation.displayName} - ${validPage}페이지"
        } else {
            "커스텀 아이템 교환권 교환 - ${currentRotation.displayName}"
        }
        val gui = Bukkit.createInventory(null, 45, Component.text(guiTitle))
        
        // 배경 아이템 설정
        fillWithSeasonBackground(gui, currentRotation.themeColor)
        
        // 스크롤 아이템 배치 (격자 형태 - 페이지별)
        arrangeScrollItems(gui, currentRotation, alreadyPurchased, validPage)
        
        // 정보 아이템 배치
        arrangeInfoItems(gui, currentRotation, currentWeek, alreadyPurchased)
        
        // 네비게이션 버튼 추가 (페이지가 2개 이상일 때만)
        if (totalPages > 1) {
            addNavigationButtons(gui, validPage, totalPages, currentRotation.seasonName)
        } else {
            // 1페이지인 경우 닫기 버튼 추가
            addCloseButton(gui)
        }
        
        // GUI 열기
        player.openInventory(gui)
    }
    
    /**
     * 배경 채우기 - 색상 유리판
     * 시즌별 배경 색상으로 채우기
     */
    private fun fillWithSeasonBackground(gui: Inventory, themeColor: String) {
        val glassMaterial = when (themeColor) {
            "ORANGE" -> Material.ORANGE_STAINED_GLASS_PANE
            "GREEN" -> Material.GREEN_STAINED_GLASS_PANE
            "PINK" -> Material.PINK_STAINED_GLASS_PANE
            else -> Material.BLACK_STAINED_GLASS_PANE
        }
        
        val backgroundItem = ItemStack(glassMaterial).apply {
            editMeta { meta ->
                meta.displayName(Component.text(" ").decoration(TextDecoration.ITALIC, false))
            }
        }
        
        for (i in 0 until gui.size) {
            gui.setItem(i, backgroundItem)
        }
    }
    
    /**
     * 총 페이지 수 계산
     */
    private fun getTotalPages(rotation: ScrollRotationWeek): Int {
        val scrollCount = rotation.scrollIds.size
        val itemsPerPage = 11 // 격자 슬롯 수
        return (scrollCount + itemsPerPage - 1) / itemsPerPage
    }
    
    /**
     * 페이지별 스크롤 ID 목록 가져오기
     */
    private fun getScrollsForPage(rotation: ScrollRotationWeek, page: Int): List<String> {
        val scrollIds = rotation.scrollIds
        val itemsPerPage = 11
        val startIndex = (page - 1) * itemsPerPage
        val endIndex = minOf(startIndex + itemsPerPage, scrollIds.size)
        
        return if (startIndex < scrollIds.size) {
            scrollIds.subList(startIndex, endIndex)
        } else {
            emptyList()
        }
    }
    
    /**
     * 스크롤 아이템들 배치 (ItemReceiveSystem 스타일 - 페이지네이션 지원)
     * 2행: 10, 12, 14, 16
     * 3행: 20, 22, 24
     * 4행: 28, 30, 32, 34
     */
    private fun arrangeScrollItems(gui: Inventory, rotation: ScrollRotationWeek, alreadyPurchased: Boolean, page: Int = 1) {
        val pageScrollIds = getScrollsForPage(rotation, page)
        
        // ItemReceiveSystem과 동일한 격자 슬롯 위치
        val gridSlots = listOf(
            10, 12, 14, 16,  // 2행
            20, 22, 24,      // 3행
            28, 30, 32, 34   // 4행
        )
        
        for ((index, scrollId) in pageScrollIds.withIndex()) {
            if (index >= gridSlots.size) break // 사용 가능한 슬롯 수를 초과하면 중단
            
            val slot = gridSlots[index]
            val scrollItem = createScrollDisplayItem(scrollId, alreadyPurchased)
            if (scrollItem != null) {
                gui.setItem(slot, scrollItem)
            }
        }
    }
    
    /**
     * 스크롤 표시 아이템 생성
     */
    private fun createScrollDisplayItem(scrollId: String, alreadyPurchased: Boolean): ItemStack? {
        val scrollItem = NexoItems.itemFromId(scrollId)?.build() ?: return null
        
        if (alreadyPurchased) {
            return createPurchasedScrollItem(scrollId)
        }
        
        scrollItem.editMeta { meta ->
            // PersistentDataContainer에 scrollId 저장
            meta.persistentDataContainer.set(scrollKey, PersistentDataType.STRING, scrollId)
            
            // 기존 displayName을 그대로 유지하되, null이면 원본 아이템의 displayName 사용
            val originalDisplayName = meta.displayName() ?: scrollItem.displayName()
            if (originalDisplayName != null) {
                meta.displayName(originalDisplayName.color(NamedTextColor.WHITE).decoration(TextDecoration.BOLD, true).decoration(TextDecoration.ITALIC, false))
            }
            
            val lore = mutableListOf<Component>()
            lore.add(Component.text(""))
            lore.add(Component.text("클릭하여 교환", NamedTextColor.GREEN, TextDecoration.BOLD))
            lore.add(Component.text("비용: 금별작물 ${EXCHANGE_COST}개", NamedTextColor.GOLD, TextDecoration.BOLD))
            meta.lore((meta.lore() ?: mutableListOf()) + lore)
        }
        
        return scrollItem
    }
    
    /**
     * 정보 아이템들 배치
     */
    private fun arrangeInfoItems(gui: Inventory, rotation: ScrollRotationWeek, weekString: String, alreadyPurchased: Boolean) {
        // 시즌 정보 (왼쪽 상단)
        val seasonInfo = ItemStack(Material.PAPER).apply {
            editMeta { meta ->
                meta.displayName(Component.text("${rotation.displayName} 시즌", NamedTextColor.GOLD, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false))
                val lore = mutableListOf<Component>()
                lore.add(Component.text("현재 주차: $weekString", NamedTextColor.AQUA, TextDecoration.BOLD))
                lore.add(Component.text(""))
                lore.add(Component.text("이번주 교환 가능:", NamedTextColor.YELLOW, TextDecoration.BOLD))
                lore.add(Component.text("• ${rotation.displayName} 스크롤들", NamedTextColor.WHITE, TextDecoration.BOLD))
                lore.add(Component.text("• 총 ${rotation.scrollIds.size}종류", NamedTextColor.GRAY, TextDecoration.BOLD))
                
                meta.lore(lore)
            }
        }
        gui.setItem(0, seasonInfo)
        
        // 다음 로테이션 정보 (중앙 상단)
        val nextRotationInfo = ItemStack(Material.CLOCK).apply {
            editMeta { meta ->
                meta.displayName(Component.text("다음 로테이션", NamedTextColor.YELLOW, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false))
                val nextRotation = weeklyScrollRotationSystem.getNextRotation()
                val timeUntilNext = weeklyScrollRotationSystem.getTimeUntilNextRotation()
                val lore = mutableListOf<Component>()
                lore.add(Component.text("다음 시즌: ${nextRotation.displayName}", NamedTextColor.GREEN, TextDecoration.BOLD))
                lore.add(Component.text("변경까지: $timeUntilNext", NamedTextColor.GRAY, TextDecoration.BOLD))
                lore.add(Component.text(""))
                lore.add(Component.text("💡 매주 월요일 00시에 자동 변경", NamedTextColor.AQUA, TextDecoration.BOLD))
                
                meta.lore(lore)
            }
        }
        gui.setItem(4, nextRotationInfo)
        
        // 구매 상태 정보 (오른쪽 상단)
        val purchaseStatus = if (alreadyPurchased) {
            ItemStack(Material.RED_DYE).apply {
                editMeta { meta ->
                    meta.displayName(Component.text("구매 완료", NamedTextColor.RED, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false))
                    meta.lore(listOf(
                        Component.text("✅ 이번 주 구매 완료", NamedTextColor.GRAY, TextDecoration.BOLD),
                        Component.text(""),
                        Component.text("다음 구매 가능:", NamedTextColor.YELLOW, TextDecoration.BOLD),
                        Component.text("다음 주 월요일 00시", NamedTextColor.GREEN, TextDecoration.BOLD)
                    ))
                }
            }
        } else {
            ItemStack(Material.GREEN_DYE).apply {
                editMeta { meta ->
                    meta.displayName(Component.text("구매 가능", NamedTextColor.GREEN, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false))
                    meta.lore(listOf(
                        Component.text("💰 구매 비용:", NamedTextColor.YELLOW, TextDecoration.BOLD),
                        Component.text("금별작물 ${EXCHANGE_COST}개", NamedTextColor.GOLD, TextDecoration.BOLD),
                        Component.text(""),
                        Component.text("📋 구매 제한:", NamedTextColor.YELLOW, TextDecoration.BOLD),
                        Component.text("주당 1개만 구매 가능", NamedTextColor.GRAY, TextDecoration.BOLD)
                    ))
                }
            }
        }
        gui.setItem(8, purchaseStatus)
        
        // 하단 닫기 버튼 (5행 중앙)
        val closeButton = ItemStack(Material.BARRIER).apply {
            editMeta { meta ->
                meta.displayName(Component.text("닫기", NamedTextColor.WHITE, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false))
                meta.lore(listOf(
                    Component.text("클릭하여 GUI를 닫습니다.", NamedTextColor.GRAY, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false)
                ))
            }
        }
        gui.setItem(40, closeButton) // 5행 중앙 (9*4 + 4 = 40)
    }
    
    /**
     * 스크롤 아이템 생성 (구매 완료)
     */
    private fun createPurchasedScrollItem(scrollId: String): ItemStack {
        val scrollItem = NexoItems.itemFromId(scrollId)?.build() ?: return ItemStack(Material.AIR)
        
        scrollItem.editMeta { meta ->
            // 원본 아이템 이름을 가져와서 구매 완료 상태와 함께 표시
            val originalDisplayName = meta.displayName() ?: scrollItem.displayName()
            val displayText = if (originalDisplayName != null) {
                Component.text("구매 완료 - ", NamedTextColor.RED, TextDecoration.BOLD)
                    .append(originalDisplayName.color(NamedTextColor.GRAY))
            } else {
                Component.text("구매 완료", NamedTextColor.RED, TextDecoration.BOLD)
            }
            
            meta.displayName(displayText.decoration(TextDecoration.ITALIC, false))
            meta.lore(listOf(
                Component.text("이번 주에 이미 구매하셨습니다.", NamedTextColor.GRAY, TextDecoration.BOLD),
                Component.text("다음 주 월요일에 새로운 시즌으로 교체됩니다.", NamedTextColor.GRAY, TextDecoration.BOLD)
            ))
        }
        
        return scrollItem
    }
    
    /**
     * 스크롤 아이템 생성 (구매 가능)
     */
    private fun createAvailableScrollItem(scrollId: String): ItemStack {
        val scrollItem = NexoItems.itemFromId(scrollId)?.build() ?: return ItemStack(Material.AIR)
        
        scrollItem.editMeta { meta ->
            // 기존 displayName을 유지하되, 없으면 원본 아이템의 displayName 사용
            val originalDisplayName = meta.displayName() ?: scrollItem.displayName()
            if (originalDisplayName != null) {
                meta.displayName(originalDisplayName.color(NamedTextColor.WHITE).decoration(TextDecoration.BOLD, true).decoration(TextDecoration.ITALIC, false))
            }
            
            meta.lore(listOf(
                Component.text("비용: 금별작물 ${EXCHANGE_COST}개", NamedTextColor.YELLOW, TextDecoration.BOLD),
                Component.text("클릭하여 교환", NamedTextColor.GREEN, TextDecoration.BOLD)
            ))
        }
        
        return scrollItem
    }
    
    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val clickedInventory = event.clickedInventory ?: return
        val title = event.view.title()
        
        // 주간 스크롤 교환 GUI가 아닌 경우 무시
        if (!title.toString().contains("커스텀 아이템 교환권 교환 -")) {
            return
        }
        
        // 항상 클릭 취소
        event.isCancelled = true
        
        val clickedItem = event.currentItem ?: return
        val meta = clickedItem.itemMeta ?: return
        
        // 네비게이션 버튼 처리를 우선 처리 (실시간 검증 없이)
        val navAction = meta.persistentDataContainer.get(navKey, PersistentDataType.STRING)
        if (navAction != null) {
            handleNavigation(player, navAction)
            return
        }
        
        // 실시간 주차 검증 (스크롤 아이템 처리 시에만)
        val guiOpenWeek = playerGuiOpenWeeks[player.uniqueId]
        val currentWeek = weeklyScrollRotationSystem.getCurrentWeekString()
        
        // null 곋스 체크 추가 (초기화 안된 상태 방지)
        if (guiOpenWeek != null && guiOpenWeek != currentWeek) {
            // 주차가 바뀌! 구매 차단
            player.sendMessage(Component.text("주차가 변경되어 구매할 수 없습니다. GUI를 다시 열어주세요.", NamedTextColor.RED))
            player.closeInventory()
            return
        }
        
        // 스크롤 아이템 처리 확인 
        val scrollId = meta.persistentDataContainer.get(scrollKey, PersistentDataType.STRING)
        if (scrollId != null) {
            handleScrollClick(player, clickedItem, event.rawSlot, currentWeek, scrollId)
            return
        }
        
        // 클릭한 슬롯 확인 (5x9 레이아웃)
        when (event.rawSlot) {
            40 -> {
                // 닫기 버튼 또는 페이지 정보 (5행 중앙)
                player.closeInventory()
                return
            }
            39, 41 -> {
                // 네비게이션 버튼 슬롯 (이미 위에서 처리됨)
                return
            }
            
            in listOf(10, 12, 14, 16, 20, 22, 24, 28, 30, 32, 34) -> {
                // 스크롤 슬롯 (격자 형태) - PersistentDataContainer가 없는 경우 폴백
                handleScrollClick(player, clickedItem, event.rawSlot, currentWeek)
            }
        }
    }
    
    /**
     * 스크롤 클릭 처리 (PersistentDataContainer로 scrollId 전달)
     */
    private fun handleScrollClick(player: Player, clickedItem: ItemStack, slot: Int, currentWeek: String, scrollId: String) {
        // 이미 구매한 경우 차단
        if (farmVillageData.hasPlayerPurchasedThisWeek(player.uniqueId, currentWeek)) {
            player.sendMessage(Component.text("이번 주에 이미 스크롤을 구매하셨습니다!", NamedTextColor.RED))
            return
        }
        
        // 스크롤 ID 유효성 검증
        if (!isValidScrollId(scrollId)) {
            return
        }
        
        // 교환 수행
        performScrollExchange(player, scrollId, currentWeek)
    }
    
    /**
     * 스크롤 클릭 처리 (폴백 - 기존 방식)
     */
    private fun handleScrollClick(player: Player, clickedItem: ItemStack, slot: Int, currentWeek: String) {
        // 이미 구매한 경우 차단
        if (farmVillageData.hasPlayerPurchasedThisWeek(player.uniqueId, currentWeek)) {
            player.sendMessage(Component.text("이번 주에 이미 스크롤을 구매하셨습니다!", NamedTextColor.RED))
            return
        }
        
        // 빨간 유리판 클릭 시 차단
        if (clickedItem.type == Material.RED_STAINED_GLASS_PANE) {
            return
        }
        
        // 스크롤 아이템인지 확인
        val scrollId = NexoItems.idFromItem(clickedItem)
        if (scrollId == null || !isValidScrollId(scrollId)) {
            return
        }
        
        // 교환 수행
        performScrollExchange(player, scrollId, currentWeek)
    }
    
    /**
     * 스크롤 교환 실행 (거래 확인 창과 함께)
     */
    private fun performScrollExchange(player: Player, scrollId: String, currentWeek: String) {
        // FarmVillageManager 확인
        if (farmVillageManager == null) {
            player.sendMessage(Component.text("거래 시스템이 초기화되지 않았습니다. 서버를 재시작해주세요.", NamedTextColor.RED))
            return
        }
        
        // 1. 교환에 필요한 금별작물 찾기
        val costItems = findGoldenStarItemsInInventory(player, EXCHANGE_COST)
        if (costItems == null) {
            player.sendMessage(Component.text("교환에 필요한 금별 작물이 부족합니다. (필요: ${EXCHANGE_COST}개)", NamedTextColor.RED))
            return
        }
        
        // 2. 인벤토리 공간 확인
        val scrollItem = NexoItems.itemFromId(scrollId)?.build() ?: return
        if (!hasEnoughSpace(player, scrollItem)) {
            player.sendMessage(Component.text("스크롤을 받을 인벤토리 공간이 부족합니다.", NamedTextColor.RED))
            return
        }
        
        val currentRotation = weeklyScrollRotationSystem.getCurrentRotation()
        
        // 3. 거래 확인 창용 비용 표시 아이템 생성
        val costDisplayItem = ItemStack(Material.CHEST).apply {
            editMeta {
                it.displayName(Component.text("금별 작물 ${EXCHANGE_COST}개", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false))
                val loreLines = costItems.map { item ->
                    Component.text("- ", NamedTextColor.GRAY)
                        .append(item.displayName())
                        .append(Component.text(" x${item.amount}", NamedTextColor.GRAY))
                        .decoration(TextDecoration.ITALIC, false)
                }
                it.lore(loreLines)
            }
        }
        
        // 4. 거래 확인 시 실행될 콜백 함수
        val onConfirm = {
            executeScrollTrade(player, scrollId, currentWeek, currentRotation, costItems, scrollItem)
        }
        
        // 5. 거래 확인 창 열기
        farmVillageManager!!.openTradeConfirmationGUI(player, scrollItem.clone(), costDisplayItem, onConfirm)
    }
    
    /**
     * 실제 스크롤 교환 실행 (확인 후)
     */
    private fun executeScrollTrade(
        player: Player, 
        scrollId: String, 
        currentWeek: String, 
        currentRotation: ScrollRotationWeek, 
        costItems: List<ItemStack>, 
        scrollItem: ItemStack
    ) {
        // 재료 제거
        costItems.forEach { item -> player.inventory.removeItem(item) }
        
        // 스크롤 지급
        player.inventory.addItem(scrollItem)
        
        // DB에 구매 기록
        val success = farmVillageData.recordWeeklyScrollPurchase(
            player.uniqueId, 
            currentWeek, 
            scrollId, 
            currentRotation.seasonName
        )
        
        if (success) {
            player.sendMessage(Component.text("${currentRotation.displayName} 스크롤을 성공적으로 교환했습니다!", NamedTextColor.GREEN))
        } else {
            // 실패 시 롤백
            player.inventory.removeItem(scrollItem)
            costItems.forEach { item -> player.inventory.addItem(item) }
            player.sendMessage(Component.text("교환 처리 중 오류가 발생했습니다. 다시 시도해주세요.", NamedTextColor.RED))
        }
    }
    
    @EventHandler 
    fun onInventoryClose(event: InventoryCloseEvent) {
        val player = event.player as? Player ?: return
        val title = event.view.title()
        
        if (title.toString().contains("커스텀 아이템 교환권 교환 -")) {
            playerGuiOpenWeeks.remove(player.uniqueId)
        }
    }
    
    // ===== 유틸리티 메서드들 =====
    
    /**
     * 플레이어 인벤토리에서 금별작물을 필요한 개수만큼 찾기
     */
    private fun findGoldenStarItemsInInventory(player: Player, requiredAmount: Int): List<ItemStack>? {
        val foundItems = mutableListOf<ItemStack>()
        var remainingAmount = requiredAmount
        
        player.inventory.storageContents.forEach { item ->
            if (remainingAmount <= 0) return@forEach
            
            if (item != null && NexoItems.idFromItem(item) in goldenStarIds) {
                val amountToTake = minOf(item.amount, remainingAmount)
                val itemCopy = item.clone()
                itemCopy.amount = amountToTake
                foundItems.add(itemCopy)
                remainingAmount -= amountToTake
            }
        }
        
        return if (remainingAmount <= 0) foundItems else null
    }
    
    private fun isValidScrollId(scrollId: String): Boolean {
        val currentRotation = weeklyScrollRotationSystem.getCurrentRotation()
        return scrollId in currentRotation.scrollIds
    }
    
    private fun playerHasGoldenStarCrops(player: Player, amount: Int): Boolean {
        var count = 0
        player.inventory.storageContents.forEach { item ->
            if (item != null && NexoItems.idFromItem(item) in goldenStarIds) {
                count += item.amount
            }
        }
        return count >= amount
    }
    
    private fun removeGoldenStarCrops(player: Player, amount: Int) {
        var remaining = amount
        player.inventory.storageContents.forEach { item ->
            if (remaining > 0 && item != null && NexoItems.idFromItem(item) in goldenStarIds) {
                if (item.amount <= remaining) {
                    remaining -= item.amount
                    item.amount = 0
                } else {
                    item.amount -= remaining
                    remaining = 0
                }
            }
        }
    }
    
    private fun hasEnoughSpace(player: Player, itemToAdd: ItemStack): Boolean {
        val tempInventory = Bukkit.createInventory(null, 36)
        for (i in 0..35) {
            val item = player.inventory.storageContents[i]
            if (item != null) {
                tempInventory.setItem(i, item.clone())
            }
        }
        return tempInventory.addItem(itemToAdd.clone()).isEmpty()
    }
    
    private fun findGoldenStarItem(): ItemStack? {
        return goldenStarIds.firstNotNullOfOrNull { id ->
            NexoItems.itemFromId(id)?.build()
        }
    }
    
    // ===== 페이지네이션 관련 메서드들 =====
    
    /**
     * 네비게이션 버튼 추가 (ItemReceiveSystem 스타일)
     */
    private fun addNavigationButtons(gui: Inventory, currentPage: Int, totalPages: Int, seasonName: String) {
        // 이전 페이지 버튼 (39번 슬롯)
        if (currentPage > 1) {
            val prevButton = ItemStack(Material.ARROW).apply {
                editMeta { meta ->
                    meta.displayName(Component.text("이전 페이지", NamedTextColor.GREEN, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false))
                    meta.persistentDataContainer.set(navKey, PersistentDataType.STRING, "prev_$seasonName")
                }
            }
            gui.setItem(39, prevButton)
        }
        
        // 페이지 정보 (40번 슬롯)
        val pageDisplay = ItemStack(Material.PAPER).apply {
            editMeta { meta ->
                meta.displayName(Component.text("${currentPage}페이지 / ${totalPages}페이지", NamedTextColor.YELLOW, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false))
                meta.lore(listOf(
                    Component.text(""), 
                    Component.text("페이지 이동은 화살표 버튼을 이용해주세요.", NamedTextColor.GRAY, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false)
                ))
            }
        }
        gui.setItem(40, pageDisplay)
        
        // 다음 페이지 버튼 (41번 슬롯)
        if (currentPage < totalPages) {
            val nextButton = ItemStack(Material.ARROW).apply {
                editMeta { meta ->
                    meta.displayName(Component.text("다음 페이지", NamedTextColor.GREEN, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false))
                    meta.persistentDataContainer.set(navKey, PersistentDataType.STRING, "next_$seasonName")
                }
            }
            gui.setItem(41, nextButton)
        }
    }
    
    /**
     * 닫기 버튼 추가 (1페이지인 경우)
     */
    private fun addCloseButton(gui: Inventory) {
        val closeButton = ItemStack(Material.BARRIER).apply {
            editMeta { meta ->
                meta.displayName(Component.text("닫기", NamedTextColor.WHITE, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false))
                meta.lore(listOf(
                    Component.text("클릭하여 GUI를 닫습니다.", NamedTextColor.GRAY, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false)
                ))
            }
        }
        gui.setItem(40, closeButton)
    }
    
    /**
     * 네비게이션 처리
     */
    private fun handleNavigation(player: Player, navAction: String) {
        val parts = navAction.split("_")
        if (parts.size != 2) return
        
        val action = parts[0]
        val seasonName = parts[1]
        
        val currentRotation = weeklyScrollRotationSystem.getCurrentRotation()
        val currentWeek = weeklyScrollRotationSystem.getCurrentWeekString()
        
        // 🔥 네비게이션 처리 전에 현재 주차로 명시적 업데이트 (타이밍 문제 방지)
        playerGuiOpenWeeks[player.uniqueId] = currentWeek
        
        if (currentRotation.seasonName != seasonName) {
            player.sendMessage(Component.text("시즌이 변경되어 GUI를 새로고침합니다.", NamedTextColor.YELLOW))
            openGUI(player)
            return
        }
        
        val totalPages = getTotalPages(currentRotation)
        val currentTitle = player.openInventory.title().toString()
        val currentPage = extractCurrentPage(currentTitle)
        
        val newPage = when (action) {
            "prev" -> maxOf(1, currentPage - 1)
            "next" -> minOf(totalPages, currentPage + 1)
            else -> currentPage
        }
        
        if (newPage != currentPage) {
            openGUI(player, newPage)
        }
    }
    
    /**
     * 현재 페이지 번호 추출
     */
    private fun extractCurrentPage(title: String): Int {
        val regex = "(\\d+)페이지".toRegex()
        val matchResult = regex.find(title)
        return matchResult?.groupValues?.get(1)?.toIntOrNull() ?: 1
    }
}
