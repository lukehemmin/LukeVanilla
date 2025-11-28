package com.lukehemmin.lukeVanilla.System.FarmVillage

import com.lukehemmin.lukeVanilla.Main
import com.lukehemmin.lukeVanilla.System.VillageMerchant.VillageMerchantData
import com.lukehemmin.lukeVanilla.System.VillageMerchant.MerchantItem
import com.lukehemmin.lukeVanilla.System.VillageMerchant.SeedItem
import com.lukehemmin.lukeVanilla.System.Economy.TransactionType
import com.nexomc.nexo.api.NexoItems
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import java.util.UUID

class SeedMerchantGUI(
    private val plugin: Main,
    private val farmVillageManager: FarmVillageManager
) : Listener {

    private var villageMerchantData: VillageMerchantData? = null
    private val itemSlots = listOf(10, 12, 14, 16, 28, 30, 32, 34)
    private val itemsPerPage = itemSlots.size
    private val playerPages = mutableMapOf<UUID, Int>()
    // 플레이어가 현재 보고 있는 상점 타입 저장 (UUID -> ShopType)
    private val playerShopTypes = mutableMapOf<UUID, String>()
    private val playerShopTitles = mutableMapOf<UUID, String>()

    fun setVillageMerchantData(data: VillageMerchantData) {
        this.villageMerchantData = data
    }

    fun open(player: Player, shopType: String, shopTitle: String, page: Int = 1) {
        if (villageMerchantData == null) {
            player.sendMessage(Component.text("상점 데이터를 불러올 수 없습니다. (시스템 초기화 중)", NamedTextColor.RED))
            return
        }
        
        // 상점 정보 저장
        playerShopTypes[player.uniqueId] = shopType
        playerShopTitles[player.uniqueId] = shopTitle

        // 비동기로 아이템 로드
        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            try {
                val items = villageMerchantData!!.getMerchantItems(shopType)
                
                plugin.server.scheduler.runTask(plugin, Runnable {
                    openGUI(player, items, shopTitle, page)
                })
            } catch (e: Exception) {
                plugin.logger.severe("[VillageShop] Failed to load items for $shopType: ${e.message}")
                plugin.server.scheduler.runTask(plugin, Runnable {
                    player.sendMessage(Component.text("상점 데이터를 불러오는 중 오류가 발생했습니다.", NamedTextColor.RED))
                })
            }
        })
    }

    private fun openGUI(player: Player, items: List<MerchantItem>, title: String, page: Int) {
        val totalPages = maxOf(1, (items.size + itemsPerPage - 1) / itemsPerPage)
        val currentPage = page.coerceIn(1, totalPages)
        playerPages[player.uniqueId] = currentPage

        val inv = Bukkit.createInventory(null, 54, Component.text("$title (페이지 $currentPage/$totalPages)"))

        // 배경 설정 (유리판)
        // 모든 슬롯을 유리판으로 초기화하므로, 아이템이 배치되지 않은 빈 공간은 자동으로 유리판이 유지됩니다.
        val glassPane = ItemStack(Material.GRAY_STAINED_GLASS_PANE)
        val meta = glassPane.itemMeta
        meta.displayName(Component.text(" "))
        glassPane.itemMeta = meta

        for (i in 0 until 54) {
            inv.setItem(i, glassPane)
        }

        // 아이템 배치
        val startIndex = (currentPage - 1) * itemsPerPage
        val endIndex = minOf(startIndex + itemsPerPage, items.size)

        for (i in startIndex until endIndex) {
            val itemData = items[i]
            val slotIndex = i - startIndex
            if (slotIndex < itemSlots.size) {
                val slot = itemSlots[slotIndex]
                val displayItem = createDisplayItem(itemData)
                inv.setItem(slot, displayItem)
            }
        }

        // 페이지 네비게이션
        if (currentPage > 1) {
            val prevBtn = ItemStack(Material.ARROW)
            val prevMeta = prevBtn.itemMeta
            prevMeta.displayName(Component.text("이전 페이지", NamedTextColor.YELLOW))
            prevBtn.itemMeta = prevMeta
            inv.setItem(48, prevBtn)
        }

        val pageInfo = ItemStack(Material.PAPER)
        val pageMeta = pageInfo.itemMeta
        pageMeta.displayName(Component.text("페이지 $currentPage / $totalPages", NamedTextColor.WHITE))
        pageInfo.itemMeta = pageMeta
        inv.setItem(49, pageInfo)

        if (currentPage < totalPages) {
            val nextBtn = ItemStack(Material.ARROW)
            val nextMeta = nextBtn.itemMeta
            nextMeta.displayName(Component.text("다음 페이지", NamedTextColor.YELLOW))
            nextBtn.itemMeta = nextMeta
            inv.setItem(50, nextBtn)
        }

        player.openInventory(inv)
    }

    private fun createDisplayItem(itemData: MerchantItem): ItemStack {
        // 1. Nexo 아이템 확인
        val nexoBuilder = NexoItems.itemFromId(itemData.itemId)
        val item = if (nexoBuilder != null) {
            nexoBuilder.build()
        } else {
            // 2. 바닐라 아이템 확인
            val material = Material.getMaterial(itemData.itemId)
            if (material != null) {
                ItemStack(material)
            } else {
                // 3. 아이템을 찾을 수 없는 경우
                plugin.logger.warning("[VillageShop] 알 수 없는 아이템 ID: ${itemData.itemId}")
                ItemStack(Material.BARRIER).apply {
                    editMeta { it.displayName(Component.text("알 수 없는 아이템: ${itemData.itemId}", NamedTextColor.RED)) }
                }
            }
        }
        
        val meta = item.itemMeta
        
        // Hide item flags
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS)
        
        val lore = meta.lore() ?: mutableListOf()
        lore.add(Component.text("").decoration(TextDecoration.ITALIC, false))
        
        // 가격 정보 표시
        if (itemData.canBuy) {
            lore.add(Component.text("구매가: ", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false)
                .append(Component.text("${String.format("%,.0f", itemData.buyPrice)}원", NamedTextColor.RED)
                    .decoration(TextDecoration.ITALIC, false)))
        }
        if (itemData.canSell) {
            lore.add(Component.text("판매가: ", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false)
                .append(Component.text("${String.format("%,.0f", itemData.sellPrice)}원", NamedTextColor.GREEN)
                    .decoration(TextDecoration.ITALIC, false)))
        }
        
        // 구분선 (취소선 사용) - 조작 안내가 있을 때만 표시
        if (itemData.canBuy || itemData.canSell) {
            lore.add(Component.text("").decoration(TextDecoration.ITALIC, false))
            lore.add(Component.text("                    ", NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.STRIKETHROUGH, true)
                .decoration(TextDecoration.ITALIC, false))
        }
        
        // 조작 안내 표시
        if (itemData.canBuy && itemData.canSell) {
            lore.add(Component.text("[", NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false)
                .append(Component.text("좌클릭", NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false))
                .append(Component.text("] ", NamedTextColor.GOLD)
                    .decoration(TextDecoration.ITALIC, false))
                .append(Component.text("구매 ", NamedTextColor.WHITE)
                    .decoration(TextDecoration.ITALIC, false))
                .append(Component.text("(Shift: 64개)", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false)))
            lore.add(Component.text("[", NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false)
                .append(Component.text("우클릭", NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false))
                .append(Component.text("] ", NamedTextColor.GOLD)
                    .decoration(TextDecoration.ITALIC, false))
                .append(Component.text("판매 ", NamedTextColor.WHITE)
                    .decoration(TextDecoration.ITALIC, false))
                .append(Component.text("(Shift: 64개)", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false)))
        } else if (itemData.canBuy) {
            lore.add(Component.text("[", NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false)
                .append(Component.text("좌클릭", NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false))
                .append(Component.text("] ", NamedTextColor.GOLD)
                    .decoration(TextDecoration.ITALIC, false))
                .append(Component.text("구매 ", NamedTextColor.WHITE)
                    .decoration(TextDecoration.ITALIC, false))
                .append(Component.text("(Shift: 64개)", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false)))
        } else if (itemData.canSell) {
            lore.add(Component.text("[", NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false)
                .append(Component.text("우클릭", NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false))
                .append(Component.text("] ", NamedTextColor.GOLD)
                    .decoration(TextDecoration.ITALIC, false))
                .append(Component.text("판매 ", NamedTextColor.WHITE)
                    .decoration(TextDecoration.ITALIC, false))
                .append(Component.text("(Shift: 64개)", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false)))
        }
        
        meta.lore(lore)
        item.itemMeta = meta
        return item
    }

    @EventHandler
    fun onInventoryClose(event: org.bukkit.event.inventory.InventoryCloseEvent) {
        playerPages.remove(event.player.uniqueId)
        playerShopTypes.remove(event.player.uniqueId)
        playerShopTitles.remove(event.player.uniqueId)
    }

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val view = event.view
        val title = view.title()
        
        // 상점 제목 확인 (저장된 타이틀로 확인하거나, 포괄적으로 확인)
        val currentShopTitle = playerShopTitles[player.uniqueId]
        val titleString = PlainTextComponentSerializer.plainText().serialize(title)
        
        // 타이틀이 없거나 일치하지 않으면 무시 (단, 페이지 번호 때문에 포함 관계로 확인)
        // "상인" 이라는 단어가 포함되어 있으면 우리 GUI라고 가정 (약간 위험할 수 있으나 기존 "씨앗 상인" 로직 따름)
        // 더 안전하게는 playerShopTypes에 값이 있는지 확인
        if (playerShopTypes[player.uniqueId] == null) return

        event.isCancelled = true
        val clickedItem = event.currentItem ?: return
        if (clickedItem.type == Material.AIR || clickedItem.type == Material.GRAY_STAINED_GLASS_PANE) return

        val slot = event.slot
        val currentPage = playerPages[player.uniqueId] ?: 1
        val currentShopType = playerShopTypes[player.uniqueId] ?: return
        val currentTitle = playerShopTitles[player.uniqueId] ?: "상점"

        // 페이지 이동
        if (slot == 48) { // 이전
             open(player, currentShopType, currentTitle, currentPage - 1)
             return
        }
        if (slot == 50) { // 다음
             open(player, currentShopType, currentTitle, currentPage + 1)
             return
        }

        // 아이템 구매/판매
        if (itemSlots.contains(slot)) {
            val isLeftClick = event.isLeftClick
            val isRightClick = event.isRightClick
            handleTransaction(player, clickedItem, event.isShiftClick, isLeftClick, isRightClick, currentShopType)
        }
    }
    
    private enum class TransactionAction {
        BUY, SELL
    }
    
    private fun handleTransaction(player: Player, displayItem: ItemStack, isShiftClick: Boolean, isLeftClick: Boolean, isRightClick: Boolean, shopType: String) {
        if (villageMerchantData == null) return
        
        val action = when {
            isLeftClick -> TransactionAction.BUY
            isRightClick -> TransactionAction.SELL
            else -> return // 다른 클릭은 무시
        }
        
        // 비동기로 데이터 가져와서 처리
        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            try {
                val items = villageMerchantData!!.getMerchantItems(shopType)
                
                // 클릭한 아이템이 Nexo 아이템인지 확인
                val nexoId = NexoItems.idFromItem(displayItem)
                
                val matchedItem = items.find { dbItem ->
                    if (nexoId != null) {
                        // Nexo 아이템인 경우 ID로 비교
                        dbItem.itemId == nexoId
                    } else {
                        // 바닐라 아이템인 경우 Type 이름으로 비교
                        dbItem.itemId == displayItem.type.name
                    }
                }
                
                if (matchedItem != null) {
                    // 권한 확인
                    val canProceed = when (action) {
                        TransactionAction.BUY -> matchedItem.canBuy
                        TransactionAction.SELL -> matchedItem.canSell
                    }
                    
                    if (!canProceed) {
                        plugin.server.scheduler.runTask(plugin, Runnable {
                            val actionText = if (action == TransactionAction.BUY) "구매" else "판매"
                            player.sendMessage(Component.text("이 아이템은 ${actionText}할 수 없습니다.", NamedTextColor.RED))
                        })
                        return@Runnable
                    }
                    
                    plugin.server.scheduler.runTask(plugin, Runnable {
                        processTransaction(player, matchedItem, isShiftClick, action, shopType)
                    })
                } else {
                    // 아이템을 찾지 못했을 경우
                    plugin.logger.warning("[VillageShop] 거래 시도 중 아이템 매칭 실패. Display: ${nexoId ?: displayItem.type.name}")
                    plugin.server.scheduler.runTask(plugin, Runnable {
                        player.sendMessage(Component.text("상점 데이터와 일치하는 아이템을 찾을 수 없습니다.", NamedTextColor.RED))
                    })
                }
            } catch (e: Exception) {
                plugin.logger.severe("[VillageShop] Error during transaction: ${e.message}")
                plugin.server.scheduler.runTask(plugin, Runnable {
                    player.sendMessage(Component.text("거래 처리 중 오류가 발생했습니다.", NamedTextColor.RED))
                })
            }
        })
    }

    private fun processTransaction(player: Player, itemData: MerchantItem, isShiftClick: Boolean, action: TransactionAction, shopType: String) {
        val economy = plugin.economyManager
        if (economy == null) {
            player.sendMessage(Component.text("경제 시스템이 로드되지 않았습니다.", NamedTextColor.RED))
            return
        }

        val amount = if (isShiftClick) 64 else 1

        when (action) {
            TransactionAction.BUY -> processBuy(player, itemData, amount, economy, shopType)
            TransactionAction.SELL -> processSell(player, itemData, amount, economy, shopType)
        }
    }

    private fun processBuy(player: Player, itemData: MerchantItem, amount: Int, economy: com.lukehemmin.lukeVanilla.System.Economy.EconomyManager, shopType: String) {
        // 가격 유효성 검사
        if (itemData.buyPrice <= 0 || !itemData.buyPrice.isFinite()) {
            player.sendMessage(Component.text("상품 정보에 오류가 있어 구매할 수 없습니다.", NamedTextColor.RED))
            plugin.logger.warning("[SeedMerchant] Invalid buy price detected for item ${itemData.itemId}: ${itemData.buyPrice}")
            return
        }

        val totalPrice = itemData.buyPrice * amount

        if (!totalPrice.isFinite() || totalPrice <= 0) {
            player.sendMessage(Component.text("결제 금액 계산 중 오류가 발생했습니다.", NamedTextColor.RED))
            return
        }

        // 아이템 미리 확인 (결제 전)
        val itemToGive = NexoItems.itemFromId(itemData.itemId)?.build() 
            ?: Material.getMaterial(itemData.itemId)?.let { ItemStack(it) }

        if (itemToGive == null) {
            player.sendMessage(Component.text("아이템 데이터를 찾을 수 없어 구매를 취소했습니다.", NamedTextColor.RED))
            plugin.logger.warning("[SeedMerchant] Unknown item ID during transaction: ${itemData.itemId}")
            return
        }
        itemToGive.amount = amount

        if (economy.getBalance(player) >= totalPrice) {
            if (economy.withdraw(player, totalPrice, TransactionType.SHOP_BUY, "$shopType 구매: ${itemData.itemId} x$amount")) {
                val leftover = player.inventory.addItem(itemToGive)
                if (leftover.isNotEmpty()) {
                    player.sendMessage(Component.text("인벤토리가 가득 차서 일부 아이템이 땅에 떨어졌습니다.", NamedTextColor.YELLOW))
                    leftover.values.forEach { player.world.dropItemNaturally(player.location, it) }
                }
                
                player.sendMessage(Component.text("${String.format("%,.0f", totalPrice)}원을 지불하고 아이템을 구매했습니다.", NamedTextColor.GREEN))
            } else {
                player.sendMessage(Component.text("오류가 발생했습니다.", NamedTextColor.RED))
            }
        } else {
            player.sendMessage(Component.text("돈이 부족합니다.", NamedTextColor.RED))
        }
    }

    private fun processSell(player: Player, itemData: MerchantItem, amount: Int, economy: com.lukehemmin.lukeVanilla.System.Economy.EconomyManager, shopType: String) {
        // 가격 유효성 검사
        if (itemData.sellPrice <= 0 || !itemData.sellPrice.isFinite()) {
            player.sendMessage(Component.text("판매 가격 정보에 오류가 있습니다.", NamedTextColor.RED))
            plugin.logger.warning("[SeedMerchant] Invalid sell price detected for item ${itemData.itemId}: ${itemData.sellPrice}")
            return
        }

        // 플레이어 인벤토리에서 아이템 찾기
        val inventory = player.inventory
        
        // Nexo 아이템인지 확인
        val nexoBuilder = NexoItems.itemFromId(itemData.itemId)
        val isNexoItem = nexoBuilder != null
        
        // 먼저 플레이어가 가진 아이템 개수 확인
        var availableCount = 0
        for (item in inventory.contents) {
            if (item == null || item.type == Material.AIR) continue
            
            val matches = if (isNexoItem) {
                // Nexo 아이템: ID로 비교
                NexoItems.idFromItem(item) == itemData.itemId
            } else {
                // 바닐라 아이템: Material 이름으로 비교
                item.type.name == itemData.itemId
            }
            
            if (matches) {
                availableCount += item.amount
            }
        }
        
        // 실제 판매할 수량 결정
        val isShiftClick = amount == 64
        val amountToSell = if (isShiftClick) {
            minOf(64, availableCount)
        } else {
            1
        }
        
        // 판매할 아이템이 없는 경우
        if (amountToSell == 0 || availableCount < amountToSell) {
            player.sendMessage(Component.text("판매할 아이템이 없습니다.", NamedTextColor.RED))
            return
        }
        
        // 가격 계산
        val totalPrice = itemData.sellPrice * amountToSell

        if (!totalPrice.isFinite() || totalPrice <= 0) {
            player.sendMessage(Component.text("판매 금액 계산 중 오류가 발생했습니다.", NamedTextColor.RED))
            return
        }
        
        // 아이템 제거
        var remainingAmount = amountToSell
        for (item in inventory.contents) {
            if (remainingAmount <= 0) break
            if (item == null || item.type == Material.AIR) continue
            
            val matches = if (isNexoItem) {
                NexoItems.idFromItem(item) == itemData.itemId
            } else {
                item.type.name == itemData.itemId
            }
            
            if (matches) {
                val removeAmount = minOf(remainingAmount, item.amount)
                item.amount -= removeAmount
                remainingAmount -= removeAmount
            }
        }
        
        // 돈 지급
        economy.deposit(player, totalPrice, TransactionType.SHOP_SELL, "$shopType 판매: ${itemData.itemId} x$amountToSell")
        player.sendMessage(Component.text("${String.format("%,.0f", totalPrice)}원을 받고 아이템 ${amountToSell}개를 판매했습니다.", NamedTextColor.GREEN))
    }
}
