package com.lukehemmin.lukeVanilla.System.FarmVillage

import com.lukehemmin.lukeVanilla.Main
import com.nexomc.nexo.api.NexoItems
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import kotlin.math.min

class SeedMerchantGUI(
    private val plugin: Main,
    private val farmVillageManager: FarmVillageManager
) : Listener {

    private val inventoryTitle = "씨앗 상인"
    private val seedSlotMap = mapOf(
        11 to "cabbage_seeds",
        13 to "chinese_cabbage_seeds",
        15 to "garlic_seeds",
        29 to "corn_seeds",
        31 to "pineapple_seeds",
        33 to "eggplant_seeds"
    )

    fun open(player: Player) {
        val inventory = Bukkit.createInventory(player, 45, Component.text(inventoryTitle))
        updateGuiItems(player, inventory)
        player.openInventory(inventory)
    }
    
    private fun updateGuiItems(player: Player, inventory: Inventory) {
        val blackGlass = ItemStack(Material.BLACK_STAINED_GLASS_PANE).apply {
            itemMeta = itemMeta.apply { displayName(Component.text(" ")) }
        }
        for (i in 0 until inventory.size) {
            inventory.setItem(i, blackGlass)
        }

        seedSlotMap.forEach { (slot, seedId) ->
            val seedItem = NexoItems.itemFromId(seedId)?.build() ?: return@forEach
            val remainingAmount = farmVillageManager.getRemainingDailyTradeAmount(player, seedId)
            
            seedItem.editMeta { meta ->
                val lore = mutableListOf<Component>()
                lore.add(Component.text("▷ 기본(클릭):", NamedTextColor.GRAY).append(Component.text(" 다이아 1개 → 씨앗 4개", NamedTextColor.WHITE)))
                lore.add(Component.text("▷ 대량(Shift+클릭):", NamedTextColor.GRAY).append(Component.text(" 다이아 8개 → 씨앗 32개", NamedTextColor.WHITE)))
                lore.add(Component.text(" "))
                if (remainingAmount > 0) {
                    lore.add(Component.text("[교환 가능]", NamedTextColor.GREEN, TextDecoration.BOLD))
                    lore.add(Component.text("오늘 남은 교환 개수: ${remainingAmount}개", NamedTextColor.GRAY))
                } else {
                    lore.add(Component.text("[오늘 교환 완료]", NamedTextColor.RED, TextDecoration.BOLD))
                    lore.add(Component.text("오늘 남은 교환 개수: 0개", NamedTextColor.GRAY))
                }
                meta.lore(lore)
            }
            inventory.setItem(slot, seedItem)
        }
    }

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        if (event.view.title() != Component.text(inventoryTitle)) return

        event.isCancelled = true
        val player = event.whoClicked as? Player ?: return
        
        val seedId = seedSlotMap[event.rawSlot] ?: return

        // 1. 일일 남은 교환 개수 확인
        val remainingAmount = farmVillageManager.getRemainingDailyTradeAmount(player, seedId)
        if (remainingAmount <= 0) {
            player.sendMessage(Component.text("이 씨앗은 오늘 교환 가능한 개수를 모두 소진했습니다.", NamedTextColor.RED))
            return
        }

        val isShiftClick = event.isShiftClick
        val cost = if (isShiftClick) 8 else 1
        val amountToTrade = if (isShiftClick) 32 else 4

        if (amountToTrade > remainingAmount) {
            player.sendMessage(Component.text("오늘 교환 가능한 개수(${remainingAmount}개)를 초과할 수 없습니다.", NamedTextColor.RED))
            return
        }

        // 2. 재료(다이아몬드) 확인
        val diamond = ItemStack(Material.DIAMOND)
        if (!player.inventory.containsAtLeast(diamond, cost)) {
            player.sendMessage(Component.text("교환에 필요한 다이아몬드가 ${cost}개 필요합니다.", NamedTextColor.RED))
            return
        }

        val seedItem = NexoItems.itemFromId(seedId)?.build() ?: return
        seedItem.amount = amountToTrade

        // 3. 인벤토리 공간 확인
        if (!hasEnoughSpace(player, seedItem)) {
            player.sendMessage(Component.text("씨앗을 받을 인벤토리 공간이 부족합니다.", NamedTextColor.RED))
            return
        }
        
        // 모든 확인 절차 통과, 거래 실행
        player.inventory.removeItem(diamond.asQuantity(cost))
        player.inventory.addItem(seedItem)
        farmVillageManager.recordSeedTrade(player, seedId, amountToTrade)
        
        player.sendMessage(Component.text("씨앗 ${amountToTrade}개를 성공적으로 교환했습니다!", NamedTextColor.GREEN))
        updateGuiItems(player, event.inventory)
    }

    private fun hasEnoughSpace(player: Player, itemToAdd: ItemStack): Boolean {
        var remaining = itemToAdd.amount
        val storage = player.inventory.storageContents
        
        // Check for existing stacks to fill up
        for (item in storage) {
            if (remaining <= 0) break
            if (item != null && item.isSimilar(itemToAdd)) {
                val canAdd = item.maxStackSize - item.amount
                remaining -= canAdd
            }
        }
        
        // Check for empty slots
        if (remaining > 0) {
            for (item in storage) {
                if (remaining <= 0) break
                if (item == null || item.type == Material.AIR) {
                    remaining -= itemToAdd.maxStackSize
                }
            }
        }
        
        return remaining <= 0
    }
} 