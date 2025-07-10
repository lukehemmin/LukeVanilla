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
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack

class FarmingSupplyGUI(private val plugin: Main) : Listener {

    private val selectionGuiTitle = Component.text("농사 물품 선택")
    private val workbenchGuiTitlePrefix = "물품 교환: "

    private val tradeableItems = mapOf(
        20 to ("dry_pot" to 1),
        24 to ("scarecrow" to 1)
    )
    private val workbenchSlots = setOf(
        0, 1, 2, 3, 9, 10, 11, 12, 18, 19, 20, 21, 27, 28, 29, 30, 36, 37, 38, 39
    )
    private val dividerSlots = setOf(4, 13, 22, 31, 40)
    private val rewardSlot = 25
    private val statusSlot = 49

    fun openSelection(player: Player) {
        val inventory = Bukkit.createInventory(player, 45, selectionGuiTitle)
        tradeableItems.forEach { (slot, pair) ->
            val (itemId, cost) = pair
            val item = NexoItems.itemFromId(itemId)?.build() ?: return@forEach
            item.editMeta {
                it.lore(listOf(
                    Component.text(" "),
                    Component.text("교환 재료: ", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                        .append(Component.text("다이아몬드 블록 ${cost}개", NamedTextColor.WHITE)),
                    Component.text(" "),
                    Component.text("[클릭하여 교환 시작]", NamedTextColor.GREEN, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false)
                ))
            }
            inventory.setItem(slot, item)
        }
        player.openInventory(inventory)
    }

    private fun openWorkbench(player: Player, rewardItem: ItemStack, requiredAmount: Int) {
        val guiTitle = Component.text("$workbenchGuiTitlePrefix${rewardItem.displayName().examinableName()}").color(NamedTextColor.BLACK)
        val inventory = Bukkit.createInventory(player, 54, guiTitle)
        
        val grayPane = ItemStack(Material.GRAY_STAINED_GLASS_PANE).apply { editMeta { it.displayName(Component.text(" ")) } }
        val blackPane = ItemStack(Material.BLACK_STAINED_GLASS_PANE).apply { editMeta { it.displayName(Component.text(" ")) } }
        
        dividerSlots.forEach { inventory.setItem(it, grayPane) }
        (0 until inventory.size).filter { it !in workbenchSlots && it !in dividerSlots && it != rewardSlot && it != statusSlot }
            .forEach { inventory.setItem(it, blackPane) }

        inventory.setItem(rewardSlot, rewardItem.clone())
        updateWorkbenchStatus(inventory, requiredAmount, 0)
        player.openInventory(inventory)
    }
    
    @EventHandler
    fun onSelectionClick(event: InventoryClickEvent) {
        if (event.view.title() != selectionGuiTitle) return
        event.isCancelled = true
        val player = event.whoClicked as? Player ?: return

        val tradeInfo = tradeableItems[event.rawSlot] ?: return
        val (itemId, cost) = tradeInfo
        val rewardItem = NexoItems.itemFromId(itemId)?.build() ?: return
        
        openWorkbench(player, rewardItem, cost)
    }

    @EventHandler
    fun onWorkbenchClick(event: InventoryClickEvent) {
        val view = event.view
        val title = view.title()
        if (!title.examinableName().contains(workbenchGuiTitlePrefix)) return

        val player = event.whoClicked as Player

        // Allow players to put items in/out of the workbench slots
        if (event.click == ClickType.DOUBLE_CLICK || (event.rawSlot in workbenchSlots || event.rawSlot >= view.topInventory.size)) {
            plugin.server.scheduler.runTask(plugin, Runnable {
                val rewardItem = view.getItem(rewardSlot) ?: return@Runnable
                val tradeInfo = tradeableItems.values.find { NexoItems.idFromItem(rewardItem) == it.first } ?: return@Runnable
                val requiredAmount = tradeInfo.second
                val currentAmount = countMaterials(view.topInventory)
                updateWorkbenchStatus(view.topInventory, requiredAmount, currentAmount)
            })
            return
        }
        
        event.isCancelled = true

        if (event.rawSlot == statusSlot) {
            val rewardItem = view.getItem(rewardSlot)?.clone() ?: return
            val tradeInfo = tradeableItems.values.find { NexoItems.idFromItem(rewardItem) == it.first } ?: return
            val requiredAmount = tradeInfo.second
            val currentAmount = countMaterials(view.topInventory)
            
            if (currentAmount == requiredAmount) {
                if (hasEnoughSpace(player, rewardItem)) {
                    clearWorkbenchSlots(view.topInventory)
                    player.inventory.addItem(rewardItem)
                    player.sendMessage(Component.text("성공적으로 아이템을 교환했습니다!", NamedTextColor.GREEN))
                    updateWorkbenchStatus(view.topInventory, requiredAmount, 0)
                } else {
                    player.sendMessage(Component.text("보상 아이템을 받을 인벤토리 공간이 부족합니다.", NamedTextColor.RED))
                }
            }
        }
    }

    @EventHandler
    fun onWorkbenchClose(event: InventoryCloseEvent) {
        if (event.view.title().examinableName().contains(workbenchGuiTitlePrefix)) {
            val player = event.player as Player
            val inventory = event.inventory
            workbenchSlots.forEach { slot ->
                inventory.getItem(slot)?.let { player.inventory.addItem(it) }
            }
        }
    }
    
    private fun countMaterials(inventory: Inventory): Int {
        return workbenchSlots.sumOf { inventory.getItem(it)?.takeIf { it.type == Material.DIAMOND_BLOCK }?.amount ?: 0 }
    }
    
    private fun clearWorkbenchSlots(inventory: Inventory) {
        workbenchSlots.forEach { inventory.setItem(it, null) }
    }

    private fun updateWorkbenchStatus(inventory: Inventory, requiredAmount: Int, currentAmount: Int) {
        val statusItem = ItemStack(Material.ANVIL).apply {
            editMeta {
                it.displayName(Component.text("거래 상태", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false))
                val lore = mutableListOf<Component>()
                lore.add(Component.text("필요한 재료: 다이아몬드 블록 ${requiredAmount}개", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false))
                lore.add(Component.text("현재 올린 재료: 다이아몬드 블록 ${currentAmount}개", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false))
                it.lore(lore)
            }
        }
        inventory.setItem(40, statusItem)

        val actionButton: ItemStack
        when {
            currentAmount < requiredAmount -> {
                actionButton = ItemStack(Material.RED_STAINED_GLASS_PANE).apply {
                    editMeta {
                        it.displayName(Component.text("재료 부족", NamedTextColor.RED, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false))
                        it.lore(listOf(Component.text("재료를 더 올려주세요.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)))
                    }
                }
            }
            currentAmount > requiredAmount -> {
                actionButton = ItemStack(Material.RED_STAINED_GLASS_PANE).apply {
                    editMeta {
                        it.displayName(Component.text("재료가 너무 많아요", NamedTextColor.RED, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false))
                        it.lore(listOf(Component.text("필요한 재료만 남기고 빼주세요.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)))
                    }
                }
            }
            else -> { // currentAmount == requiredAmount
                actionButton = ItemStack(Material.GREEN_STAINED_GLASS_PANE).apply {
                    editMeta {
                        it.displayName(Component.text("거래 가능!", NamedTextColor.GREEN, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false))
                        it.lore(listOf(Component.text("클릭하여 교환을 완료합니다.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)))
                    }
                }
            }
        }
        inventory.setItem(statusSlot, actionButton)
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
} 