package com.lukehemmin.lukeVanilla.System.FarmVillage

import com.lukehemmin.lukeVanilla.Main
import com.lukehemmin.lukeVanilla.System.VillageMerchant.VillageMerchantData
import com.lukehemmin.lukeVanilla.System.VillageMerchant.SeedItem
import com.lukehemmin.lukeVanilla.System.Economy.TransactionType
import com.nexomc.nexo.api.NexoItems
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
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

    fun setVillageMerchantData(data: VillageMerchantData) {
        this.villageMerchantData = data
    }

    fun open(player: Player, page: Int = 1) {
        if (villageMerchantData == null) {
            player.sendMessage(Component.text("상점 데이터를 불러올 수 없습니다. (시스템 초기화 중)", NamedTextColor.RED))
            return
        }

        // 비동기로 아이템 로드
        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            val items = villageMerchantData!!.getSeedMerchantItems()
            
            plugin.server.scheduler.runTask(plugin, Runnable {
                openGUI(player, items, page)
            })
        })
    }

    private fun openGUI(player: Player, items: List<SeedItem>, page: Int) {
        val totalPages = maxOf(1, (items.size + itemsPerPage - 1) / itemsPerPage)
        val currentPage = page.coerceIn(1, totalPages)
        playerPages[player.uniqueId] = currentPage

        val inv = Bukkit.createInventory(null, 54, Component.text("씨앗 상인 (페이지 $currentPage/$totalPages)"))

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

    private fun createDisplayItem(seedItem: SeedItem): ItemStack {
        // 1. Nexo 아이템 확인
        val nexoBuilder = NexoItems.itemFromId(seedItem.itemId)
        val item = if (nexoBuilder != null) {
            nexoBuilder.build()
        } else {
            // 2. 바닐라 아이템 확인
            val material = Material.getMaterial(seedItem.itemId)
            if (material != null) {
                ItemStack(material)
            } else {
                // 3. 아이템을 찾을 수 없는 경우
                plugin.logger.warning("[SeedMerchant] 알 수 없는 아이템 ID: ${seedItem.itemId}")
                ItemStack(Material.BARRIER).apply {
                    editMeta { it.displayName(Component.text("알 수 없는 아이템: ${seedItem.itemId}", NamedTextColor.RED)) }
                }
            }
        }
        
        val meta = item.itemMeta
        val lore = meta.lore() ?: mutableListOf()
        lore.add(Component.text(""))
        lore.add(Component.text("가격: ${String.format("%,.0f", seedItem.price)}원", NamedTextColor.GOLD))
        lore.add(Component.text(""))
        lore.add(Component.text("좌클릭: 1개 구매", NamedTextColor.YELLOW))
        lore.add(Component.text("Shift + 좌클릭: 64개 구매", NamedTextColor.YELLOW))
        meta.lore(lore)
        item.itemMeta = meta
        return item
    }

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val view = event.view
        val title = view.title()
        
        val titleString = PlainTextComponentSerializer.plainText().serialize(title)
        if (!titleString.contains("씨앗 상인")) return

        event.isCancelled = true
        val clickedItem = event.currentItem ?: return
        if (clickedItem.type == Material.AIR || clickedItem.type == Material.GRAY_STAINED_GLASS_PANE) return

        val slot = event.slot
        val currentPage = playerPages[player.uniqueId] ?: 1

        // 페이지 이동
        if (slot == 48) { // 이전
             open(player, currentPage - 1)
             return
        }
        if (slot == 50) { // 다음
             open(player, currentPage + 1)
             return
        }

        // 아이템 구매
        if (itemSlots.contains(slot)) {
            handlePurchase(player, clickedItem, event.isShiftClick)
        }
    }
    
    private fun handlePurchase(player: Player, displayItem: ItemStack, isShiftClick: Boolean) {
        if (villageMerchantData == null) return
        
        // 비동기로 데이터 가져와서 처리
        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            val items = villageMerchantData!!.getSeedMerchantItems()
            
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
                plugin.server.scheduler.runTask(plugin, Runnable {
                    processTransaction(player, matchedItem, isShiftClick)
                })
            } else {
                // 아이템을 찾지 못했을 경우
                plugin.logger.warning("[SeedMerchant] 구매 시도 중 아이템 매칭 실패. Display: ${nexoId ?: displayItem.type.name}")
                player.sendMessage(Component.text("상점 데이터와 일치하는 아이템을 찾을 수 없습니다.", NamedTextColor.RED))
            }
        })
    }

    private fun processTransaction(player: Player, itemData: SeedItem, isShiftClick: Boolean) {
        val amount = if (isShiftClick) 64 else 1
        val totalPrice = itemData.price * amount
        
        val economy = plugin.economyManager
        if (economy.getBalance(player) >= totalPrice) {
            if (economy.withdraw(player, totalPrice, TransactionType.SHOP_BUY, "씨앗 상인 구매: ${itemData.itemId} x$amount")) {
                val itemToGive = NexoItems.itemFromId(itemData.itemId)?.build() 
                    ?: ItemStack(Material.getMaterial(itemData.itemId) ?: Material.STONE)
                itemToGive.amount = amount
                
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
}
