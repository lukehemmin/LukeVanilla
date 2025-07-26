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

class SoilReceiveGUI(
    private val plugin: Main,
    private val farmVillageManager: FarmVillageManager
) : Listener {

    private val guiTitle = Component.text("토양 받기")

    fun open(player: Player) {
        val inventory = Bukkit.createInventory(player, 45, guiTitle)
        fillWithBlackGlass(inventory)
        
        // 22번 슬롯에 dry_pot 배치
        val dryPot = NexoItems.itemFromId("dry_pot")?.build()
        if (dryPot != null) {
            // 플레이어의 현재 구매 이력 확인
            val currentPurchaseAmount = farmVillageManager.getCurrentPurchaseAmount(player, "dry_pot")
            
            dryPot.editMeta { meta ->
                val lore = mutableListOf<Component>()
                lore.add(Component.text(" "))
                lore.add(Component.text("토양 2개를 무료로 받을 수 있습니다.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false))
                lore.add(Component.text(" "))
                
                // 현재 상태에 따른 안내 메시지
                when (currentPurchaseAmount) {
                    30 -> {
                        lore.add(Component.text("✅ 토양을 받을 수 있습니다!", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false))
                        lore.add(Component.text("조건: 기존 구매 이력 30개", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false))
                        lore.add(Component.text(" "))
                        lore.add(Component.text("[클릭하여 받기]", NamedTextColor.GREEN, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false))
                    }
                    0 -> {
                        lore.add(Component.text("❌ 먼저 장비상인에서 구매가 필요합니다", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false))
                        lore.add(Component.text("조건: 물 빠진 화분 30개 구매", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false))
                        lore.add(Component.text(" "))
                        lore.add(Component.text("[구매 후 다시 오세요]", NamedTextColor.GRAY, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false))
                    }
                    in 32..Int.MAX_VALUE -> {
                        lore.add(Component.text("❌ 이미 토양을 받으셨습니다", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false))
                        lore.add(Component.text("받은 이력: ${currentPurchaseAmount}개", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false))
                        lore.add(Component.text(" "))
                        lore.add(Component.text("[이미 완료됨]", NamedTextColor.GRAY, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false))
                    }
                    else -> {
                        lore.add(Component.text("❌ 조건을 충족하지 않습니다", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false))
                        lore.add(Component.text("현재 구매: ${currentPurchaseAmount}개 / 필요: 30개", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false))
                        lore.add(Component.text(" "))
                        lore.add(Component.text("[조건 미충족]", NamedTextColor.GRAY, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false))
                    }
                }
                
                meta.lore(lore)
            }
            inventory.setItem(22, dryPot)
        }
        
        player.openInventory(inventory)
    }

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val viewTitle = event.view.title()

        if (viewTitle == guiTitle) {
            event.isCancelled = true
            
            if (event.rawSlot == 22) {
                handleSoilReceive(player)
            }
        }
    }

    private fun handleSoilReceive(player: Player) {
        // 1. DB에서 플레이어의 dry_pot 구매 이력 확인
        val currentPurchaseAmount = farmVillageManager.getCurrentPurchaseAmount(player, "dry_pot")
        
        if (currentPurchaseAmount != 30) {
            if (currentPurchaseAmount == 0) {
                player.sendMessage(Component.text("토양을 받으려면 먼저 장비상인에서 물 빠진 화분을 구매해야 합니다.", NamedTextColor.RED))
            } else if (currentPurchaseAmount >= 32) {
                player.sendMessage(Component.text("이미 토양을 받으셨습니다.", NamedTextColor.RED))
            } else {
                player.sendMessage(Component.text("아직 토양을 받을 조건이 충족되지 않았습니다.", NamedTextColor.RED))
            }
            return
        }

        // 2. 인벤토리 공간 확인
        val rewardItem = NexoItems.itemFromId("dry_pot")?.build()?.asQuantity(2) ?: return
        if (!hasEnoughSpace(player, rewardItem)) {
            player.sendMessage(Component.text("토양을 받을 인벤토리 공간이 부족합니다.", NamedTextColor.RED))
            return
        }

        // 3. 아이템 지급 및 DB 업데이트
        player.inventory.addItem(rewardItem)
        farmVillageManager.updatePlayerPurchaseAmount(player, "dry_pot", 32)
        player.sendMessage(Component.text("토양 2개를 받았습니다! 구매 이력이 업데이트되었습니다.", NamedTextColor.GREEN))
        
        // GUI 닫기
        player.closeInventory()
    }

    private fun fillWithBlackGlass(inventory: Inventory) {
        val blackGlass = ItemStack(Material.BLACK_STAINED_GLASS_PANE).apply {
            itemMeta = itemMeta.apply { displayName(Component.text(" ").decoration(TextDecoration.ITALIC, false)) }
        }
        for (i in 0 until inventory.size) {
            inventory.setItem(i, blackGlass)
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
}