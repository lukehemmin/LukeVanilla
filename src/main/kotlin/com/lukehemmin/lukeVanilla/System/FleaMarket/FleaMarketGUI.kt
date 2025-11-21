package com.lukehemmin.lukeVanilla.System.FleaMarket

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
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
        private const val INVENTORY_SIZE = 54  // 6줄
    }
    
    // GUI를 연 플레이어 추적 (플레이어 UUID -> GUI 타입)
    private val openGuis = mutableMapOf<UUID, GuiType>()
    
    enum class GuiType {
        MARKET_MAIN,
        TRANSACTION_HISTORY
    }
    
    /**
     * 마켓 메인 GUI 열기
     */
    fun openMarket(player: Player) {
        val inventory = Bukkit.createInventory(null, INVENTORY_SIZE, MARKET_TITLE)
        
        // 마켓 아이템 표시
        val items = service.getAllItems()
        var slot = 0
        
        for (item in items) {
            if (slot >= 45) break  // 0-44 슬롯만 사용
            
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
                slot++
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        // 하단 메뉴 버튼들
        inventory.setItem(45, createButton(Material.LIME_DYE, "§a새로고침", listOf("§7클릭하여 목록을 갱신합니다")))
        inventory.setItem(46, createButton(Material.CHEST, "§e내 상품", listOf("§7내가 등록한 아이템을 확인합니다")))
        inventory.setItem(47, createButton(Material.BOOK, "§6거래 내역", listOf("§7거래 내역을 확인합니다")))
        inventory.setItem(49, createButton(Material.COMPARATOR, "§b정렬 방식", listOf("§7가격순/최신순")))
        inventory.setItem(53, createButton(Material.BARRIER, "§c닫기", listOf("§7GUI를 닫습니다")))
        
        player.openInventory(inventory)
        openGuis[player.uniqueId] = GuiType.MARKET_MAIN
    }
    
    /**
     * 거래 내역 GUI 열기
     */
    fun openTransactionHistory(player: Player) {
        val inventory = Bukkit.createInventory(null, INVENTORY_SIZE, HISTORY_TITLE)
        
        // 거래 내역 표시
        val logs = service.getPlayerLogs(player.uniqueId, 45)
        var slot = 0
        
        for (log in logs) {
            if (slot >= 45) break
            
            val icon = when (log.transactionType) {
                MarketTransactionType.REGISTER -> Material.PAPER
                MarketTransactionType.SELL -> Material.EMERALD
                MarketTransactionType.BUY -> Material.DIAMOND
                MarketTransactionType.WITHDRAW -> Material.CHEST
            }
            
            val typeText = when (log.transactionType) {
                MarketTransactionType.REGISTER -> "§e등록"
                MarketTransactionType.SELL -> "§a판매"
                MarketTransactionType.BUY -> "§b구매"
                MarketTransactionType.WITHDRAW -> "§7회수"
            }
            
            val lore = mutableListOf<String>()
            lore.add("§7━━━━━━━━━━━━━━━━━━━━")
            lore.add("§e거래 유형: $typeText")
            lore.add("§e아이템: §f${log.itemName}")
            lore.add("§e가격: §f${log.price.toLong()}원")
            
            if (log.counterpartName != null) {
                lore.add("§e상대방: §f${log.counterpartName}")
            }
            
            lore.add("§e거래 시간: §f${formatDate(log.transactionAt)}")
            lore.add("§7━━━━━━━━━━━━━━━━━━━━")
            
            inventory.setItem(slot, createButton(icon, "§f${log.itemName}", lore))
            slot++
        }
        
        // 하단 메뉴
        inventory.setItem(45, createButton(Material.LIME_DYE, "§a새로고침", listOf("§7클릭하여 목록을 갱신합니다")))
        inventory.setItem(46, createButton(Material.EMERALD, "§a판매 내역", listOf("§7판매한 아이템만 표시합니다")))
        inventory.setItem(47, createButton(Material.DIAMOND, "§b구매 내역", listOf("§7구매한 아이템만 표시합니다")))
        inventory.setItem(48, createButton(Material.CHEST, "§7회수 내역", listOf("§7회수한 아이템만 표시합니다")))
        inventory.setItem(49, createButton(Material.PAPER, "§e전체 보기", listOf("§7모든 거래 내역을 표시합니다")))
        inventory.setItem(53, createButton(Material.ARROW, "§c뒤로가기", listOf("§7마켓으로 돌아갑니다")))
        
        player.openInventory(inventory)
        openGuis[player.uniqueId] = GuiType.TRANSACTION_HISTORY
    }
    
    /**
     * 인벤토리 클릭 이벤트 처리
     */
    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val guiType = openGuis[player.uniqueId] ?: return
        
        event.isCancelled = true  // 아이템 이동 방지
        
        val clickedItem = event.currentItem ?: return
        if (clickedItem.type == Material.AIR) return
        
        val slot = event.slot
        
        when (guiType) {
            GuiType.MARKET_MAIN -> handleMarketClick(player, slot, event.isLeftClick)
            GuiType.TRANSACTION_HISTORY -> handleHistoryClick(player, slot)
        }
    }
    
    /**
     * 마켓 GUI 클릭 처리
     */
    private fun handleMarketClick(player: Player, slot: Int, isLeftClick: Boolean) {
        when (slot) {
            45 -> {
                // 새로고침
                player.closeInventory()
                openMarket(player)
            }
            46 -> {
                // 내 상품
                player.closeInventory()
                openMyItems(player)
            }
            47 -> {
                // 거래 내역
                player.closeInventory()
                openTransactionHistory(player)
            }
            49 -> {
                // 정렬 (TODO: 구현 필요)
                player.sendMessage("§e정렬 기능은 추후 업데이트 예정입니다.")
            }
            53 -> {
                // 닫기
                player.closeInventory()
                openGuis.remove(player.uniqueId)
            }
            in 0..44 -> {
                // 아이템 클릭
                val items = service.getAllItems()
                if (slot >= items.size) return
                
                val item = items[slot]
                
                if (item.sellerUuid == player.uniqueId && !isLeftClick) {
                    // 본인 아이템 우클릭 -> 회수
                    player.closeInventory()
                    service.withdrawItem(player, item.id)
                    openGuis.remove(player.uniqueId)
                } else if (item.sellerUuid != player.uniqueId && isLeftClick) {
                    // 타인 아이템 좌클릭 -> 구매
                    player.closeInventory()
                    service.purchaseItem(player, item.id)
                    openGuis.remove(player.uniqueId)
                }
            }
        }
   }
    
    /**
     * 거래 내역 GUI 클릭 처리
     */
    private fun handleHistoryClick(player: Player, slot: Int) {
        when (slot) {
            45 -> {
                // 새로고침
                player.closeInventory()
                openTransactionHistory(player)
            }
            46 -> {
                // 판매 내역
                player.closeInventory()
                openFilteredHistory(player, MarketTransactionType.SELL)
            }
            47 -> {
                // 구매 내역
                player.closeInventory()
                openFilteredHistory(player, MarketTransactionType.BUY)
            }
            48 -> {
                // 회수 내역
                player.closeInventory()
                openFilteredHistory(player, MarketTransactionType.WITHDRAW)
            }
            49 -> {
                // 전체 보기
                player.closeInventory()
                openTransactionHistory(player)
            }
            53 -> {
                // 뒤로가기
                player.closeInventory()
                openMarket(player)
            }
        }
    }
    
    /**
     * 내 상품만 보기
     */
    private fun openMyItems(player: Player) {
        val inventory = Bukkit.createInventory(null, INVENTORY_SIZE, "§6§l내 상품")
        
        val items = service.getItemsBySeller(player.uniqueId)
        var slot = 0
        
        for (item in items) {
            if (slot >= 45) break
            
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
                slot++
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        inventory.setItem(53, createButton(Material.ARROW, "§c뒤로가기", listOf("§7마켓으로 돌아갑니다")))
        
        player.openInventory(inventory)
        openGuis[player.uniqueId] = GuiType.MARKET_MAIN
    }
    
    /**
     * 필터링된 거래 내역 보기
     */
    private fun openFilteredHistory(player: Player, type: MarketTransactionType) {
        val inventory = Bukkit.createInventory(null, INVENTORY_SIZE, "§6§l거래 내역 - ${getTypeKorean(type)}")
        
        val logs = service.getPlayerLogsByType(player.uniqueId, type, 45)
        var slot = 0
        
        for (log in logs) {
            if (slot >= 45) break
            
            val icon = when (type) {
                MarketTransactionType.REGISTER -> Material.PAPER
                MarketTransactionType.SELL -> Material.EMERALD
                MarketTransactionType.BUY -> Material.DIAMOND
                MarketTransactionType.WITHDRAW -> Material.CHEST
            }
            
            val lore = mutableListOf<String>()
            lore.add("§7━━━━━━━━━━━━━━━━━━━━")
            lore.add("§e아이템: §f${log.itemName}")
            lore.add("§e가격: §f${log.price.toLong()}원")
            
            if (log.counterpartName != null) {
                lore.add("§e상대방: §f${log.counterpartName}")
            }
            
            lore.add("§e거래 시간: §f${formatDate(log.transactionAt)}")
            lore.add("§7━━━━━━━━━━━━━━━━━━━━")
            
            inventory.setItem(slot, createButton(icon, "§f${log.itemName}", lore))
            slot++
        }
        
        inventory.setItem(53, createButton(Material.ARROW, "§c뒤로가기", listOf("§7전체 내역으로 돌아갑니다")))
        
        player.openInventory(inventory)
        openGuis[player.uniqueId] = GuiType.TRANSACTION_HISTORY
    }
    
    /**
     * GUI 버튼 생성
     */
    private fun createButton(material: Material, name: String, lore: List<String>): ItemStack {
        val item = ItemStack(material)
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
    fun onInventoryClose(player: Player) {
        openGuis.remove(player.uniqueId)
    }
}
