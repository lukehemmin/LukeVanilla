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

class TradeWorkbenchGUI(private val plugin: Main) : Listener {

    private val workbenchGuiTitlePrefix = "물품 교환: "

    private val workbenchSlots = setOf(
        0, 1, 2, 3,
        9, 10, 11, 12,
        18, 19, 20, 21,
        27, 28, 29, 30,
        36, 37, 38, 39,
        45, 46, 47, 48
    )
    private val dividerSlots = setOf(4, 13, 22, 31, 40)
    private val rewardSlot = 25
    private val statusSlot = 49

    private data class TradeInfo(
        val rewardItem: ItemStack,
        val requiredAmount: Int,
        val requiredItemName: String,
        val validMaterialIds: Set<String>
    )

    private val activeTrades = mutableMapOf<Player, TradeInfo>()

    fun open(player: Player, rewardItem: ItemStack, requiredAmount: Int, requiredItemName: String, validMaterialIds: Set<String>) {
        val prefixComponent = Component.text(workbenchGuiTitlePrefix, NamedTextColor.BLACK)
        val itemDisplayNameComponent = rewardItem.displayName().color(NamedTextColor.BLACK)
        val guiTitle = prefixComponent.append(itemDisplayNameComponent)
            .decoration(TextDecoration.ITALIC, false)

        val inventory = Bukkit.createInventory(player, 54, guiTitle)
        
        val grayPane = ItemStack(Material.GRAY_STAINED_GLASS_PANE).apply { editMeta { it.displayName(Component.text(" ")) } }
        val blackPane = ItemStack(Material.BLACK_STAINED_GLASS_PANE).apply { editMeta { it.displayName(Component.text(" ")) } }
        
        dividerSlots.forEach { inventory.setItem(it, grayPane) }
        (5..8).forEach { inventory.setItem(it, blackPane) }
        (14..17).forEach { inventory.setItem(it, blackPane) }
        inventory.setItem(23, blackPane)
        inventory.setItem(24, blackPane)
        inventory.setItem(26, blackPane)
        (32..35).forEach { inventory.setItem(it, blackPane) }
        (41..44).forEach { inventory.setItem(it, blackPane) }
        (50..53).forEach { inventory.setItem(it, blackPane) }

        inventory.setItem(rewardSlot, rewardItem.clone())
        
        val tradeInfo = TradeInfo(rewardItem, requiredAmount, requiredItemName, validMaterialIds)
        activeTrades[player] = tradeInfo

        updateWorkbenchStatus(inventory, tradeInfo, 0)
        player.openInventory(inventory)
    }

    @EventHandler
    fun onWorkbenchClick(event: InventoryClickEvent) {
        val view = event.view
        val title = view.title().examinableName()
        if (!title.contains(workbenchGuiTitlePrefix)) return

        val player = event.whoClicked as Player
        val tradeInfo = activeTrades[player] ?: return

        // Allow players to put items in/out of the workbench slots or their own inventory
        if (event.rawSlot in workbenchSlots || event.rawSlot >= view.topInventory.size) {
            plugin.server.scheduler.runTask(plugin, Runnable {
                val currentAmount = countMaterials(view.topInventory, tradeInfo.validMaterialIds)
                updateWorkbenchStatus(view.topInventory, tradeInfo, currentAmount)
            })
            return
        }
        
        event.isCancelled = true

        if (event.rawSlot == statusSlot) {
            val currentAmount = countMaterials(view.topInventory, tradeInfo.validMaterialIds)
            
            if (currentAmount == tradeInfo.requiredAmount) {
                if (hasEnoughSpace(player, tradeInfo.rewardItem)) {
                    clearWorkbenchSlots(view.topInventory)
                    player.inventory.addItem(tradeInfo.rewardItem.clone())
                    player.sendMessage(Component.text("성공적으로 아이템을 교환했습니다!", NamedTextColor.GREEN))
                    updateWorkbenchStatus(view.topInventory, tradeInfo, 0)
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
            activeTrades.remove(player)
        }
    }
    
    private fun countMaterials(inventory: Inventory, validMaterialIds: Set<String>): Int {
        return workbenchSlots.sumOf { inventory.getItem(it)?.takeIf { item -> NexoItems.idFromItem(item) in validMaterialIds }?.amount ?: 0 }
    }
    
    private fun clearWorkbenchSlots(inventory: Inventory) {
        workbenchSlots.forEach { inventory.setItem(it, null) }
    }

    private fun updateWorkbenchStatus(inventory: Inventory, tradeInfo: TradeInfo, currentAmount: Int) {
        val statusItem = ItemStack(Material.ANVIL).apply {
            editMeta {
                it.displayName(Component.text("거래 상태", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false))
                val lore = mutableListOf<Component>()
                lore.add(Component.text("필요한 재료: ${tradeInfo.requiredItemName} ${tradeInfo.requiredAmount}개", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false))
                lore.add(Component.text("현재 올린 재료: ${tradeInfo.requiredItemName} ${currentAmount}개", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false))
                it.lore(lore)
            }
        }
        inventory.setItem(40, statusItem)

        val actionButton: ItemStack
        when {
            currentAmount < tradeInfo.requiredAmount -> {
                actionButton = ItemStack(Material.RED_STAINED_GLASS_PANE).apply {
                    editMeta {
                        it.displayName(Component.text("재료 부족", NamedTextColor.RED, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false))
                        it.lore(listOf(Component.text("재료를 더 올려주세요.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)))
                    }
                }
            }
            currentAmount > tradeInfo.requiredAmount -> {
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