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
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.scheduler.BukkitTask
import java.util.UUID

class EquipmentMerchantGUI(
    private val plugin: Main,
    private val farmVillageManager: FarmVillageManager
) : Listener {

    private val mainGuiTitle = Component.text("장비 상인")
    private val wateringCanGuiTitle = Component.text("장비상인 - 물 뿌리개 교환")
    private val sprinklerGuiTitle = Component.text("장비상인 - 스프링클러 교환")

    private val activeAnimators = mutableMapOf<UUID, BukkitTask>()
    private val animationIndexes = mutableMapOf<UUID, Triple<Int, Int, Int>>()

    private val wateringCanItems = listOf("watering_can_2", "watering_can_3", "watering_can_4")
    private val sprinklerItems = listOf("sprinkler_3_item", "sprinkler_2_item", "sprinkler_1_item")
    private val farmingSupplyItems = listOf("dry_pot", "scarecrow")

    private val silverStarIds = setOf("cabbage_silver_star", "chinese_cabbage_silver_star", "garlic_silver_star", "corn_silver_star", "pineapple_silver_star", "eggplant_silver_star")
    private val wateringCanExchangeMap = mapOf(
        20 to Triple("watering_can_2", 16, "은별 작물"),
        22 to Triple("watering_can_3", 32, "은별 작물"),
        24 to Triple("watering_can_4", 64, "은별 작물")
    )
    private val goldenStarIds = setOf("cabbage_golden_star", "chinese_cabbage_golden_star", "garlic_golden_star", "corn_golden_star", "pineapple_golden_star", "eggplant_golden_star")
    private val sprinklerExchangeMap = mapOf(
        20 to Triple("sprinkler_3_item", 1, "금별 작물"),
        22 to Triple("sprinkler_2_item", 2, "금별 작물"),
        24 to Triple("sprinkler_1_item", 3, "금별 작물")
    )
    private val farmingSupplyExchangeMap = mapOf(
        20 to Triple("dry_pot", 1, "다이아몬드 블록"),
        24 to Triple("scarecrow", 1, "다이아몬드 블록")
    )

    private val farmingSupplySelectionGuiTitle = Component.text("농사 물품 선택")
    private val farmingSupplyMaterialIds = setOf("DIAMOND_BLOCK")


    fun open(player: Player) {
        val inventory = Bukkit.createInventory(player, 45, mainGuiTitle)
        fillWithBlackGlass(inventory)
        player.openInventory(inventory)
        startAnimation(player, inventory)
    }

    private fun openFarmingSupplySelection(player: Player) {
        val inventory = Bukkit.createInventory(player, 45, farmingSupplySelectionGuiTitle)
        fillWithBlackGlass(inventory)
        farmingSupplyExchangeMap.forEach { (slot, info) ->
            val (itemId, cost, requiredItemName) = info
            val item = NexoItems.itemFromId(itemId)?.build() ?: return@forEach
            item.editMeta {
                it.lore(listOf(
                    Component.text(" "),
                    Component.text("교환 재료: ", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                        .append(Component.text("$requiredItemName ${cost}개", NamedTextColor.WHITE)),
                    Component.text(" "),
                    Component.text("[클릭하여 교환 시작]", NamedTextColor.GREEN, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false)
                ))
            }
            inventory.setItem(slot, item)
        }
        player.openInventory(inventory)
    }

    private fun openWateringCanGui(player: Player) {
        val inventory = Bukkit.createInventory(player, 45, wateringCanGuiTitle)
        fillWithBlackGlass(inventory)
        
        wateringCanExchangeMap.forEach { (slot, info) ->
            val (itemId, requiredAmount, requiredItemName) = info
            val rewardItem = NexoItems.itemFromId(itemId)?.build() ?: return@forEach

            rewardItem.editMeta { meta ->
                val lore = mutableListOf<Component>()
                lore.add(Component.text(" "))
                val requiredText = Component.text("교환 재료: ", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                    .append(Component.text("아무 종류의 $requiredItemName ${requiredAmount}개", NamedTextColor.WHITE))
                val rewardText = Component.text("지급 아이템: ", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                    .append(rewardItem.displayName().color(NamedTextColor.AQUA))
                
                lore.add(requiredText)
                lore.add(rewardText)
                lore.add(Component.text(" "))
                lore.add(Component.text("[클릭하여 교환]", NamedTextColor.GREEN, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false))
                meta.lore(lore)
            }
            inventory.setItem(slot, rewardItem)
        }
        player.openInventory(inventory)
    }

    private fun openSprinklerGui(player: Player) {
        val inventory = Bukkit.createInventory(player, 45, sprinklerGuiTitle)
        fillWithBlackGlass(inventory)

        sprinklerExchangeMap.forEach { (slot, info) ->
            val (itemId, requiredAmount, requiredItemName) = info
            val rewardItem = NexoItems.itemFromId(itemId)?.build() ?: return@forEach

            rewardItem.editMeta { meta ->
                val lore = mutableListOf<Component>()
                lore.add(Component.text(" "))
                val requiredText = Component.text("교환 재료: ", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                    .append(Component.text("아무 종류의 $requiredItemName ${requiredAmount}개", NamedTextColor.WHITE))
                val rewardText = Component.text("지급 아이템: ", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                    .append(rewardItem.displayName().color(NamedTextColor.AQUA))

                lore.add(requiredText)
                lore.add(rewardText)
                lore.add(Component.text(" "))
                lore.add(Component.text("[클릭하여 교환]", NamedTextColor.GREEN, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false))
                meta.lore(lore)
            }
            inventory.setItem(slot, rewardItem)
        }
        player.openInventory(inventory)
    }

    private fun startAnimation(player: Player, inventory: Inventory) {
        animationIndexes[player.uniqueId] = Triple(0, 0, 0)
        activeAnimators[player.uniqueId] = plugin.server.scheduler.runTaskTimer(plugin, Runnable {
            val (wateringCanIndex, sprinklerIndex, supplyIndex) = animationIndexes[player.uniqueId] ?: return@Runnable

            inventory.setItem(20, createAnimatedItem(wateringCanItems[wateringCanIndex], "물 뿌리개 교환", "은별작물로 물뿌리개를 구매할 수 있습니다."))
            inventory.setItem(22, createAnimatedItem(sprinklerItems[sprinklerIndex], "스프링클러 교환", "금별작물로 스프링클러를 구매할 수 있습니다."))
            inventory.setItem(24, createAnimatedItem(farmingSupplyItems[supplyIndex], "농사 물품 교환", "다이아블럭으로 농사물품을 구매할 수 있습니다."))

            animationIndexes[player.uniqueId] = Triple(
                (wateringCanIndex + 1) % wateringCanItems.size,
                (sprinklerIndex + 1) % sprinklerItems.size,
                (supplyIndex + 1) % farmingSupplyItems.size
            )
        }, 0L, 40L)
    }

    private fun createAnimatedItem(nexoId: String, name: String, description: String): ItemStack? {
        val item = NexoItems.itemFromId(nexoId)?.build() ?: return null
        item.editMeta { meta ->
            val displayName = Component.text(name, NamedTextColor.WHITE, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false)
            val lore = listOf(Component.text(description, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false))
            meta.displayName(displayName)
            meta.lore(lore)
        }
        return item
    }

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val viewTitle = event.view.title()

        when (viewTitle) {
            mainGuiTitle -> {
                event.isCancelled = true
                when (event.rawSlot) {
                    20 -> openWateringCanGui(player)
                    22 -> openSprinklerGui(player)
                    24 -> openFarmingSupplySelection(player)
                }
            }
            wateringCanGuiTitle -> {
                event.isCancelled = true
                val exchangeInfo = wateringCanExchangeMap[event.rawSlot] ?: return
                val (rewardId, requiredAmount, requiredItemName) = exchangeInfo
                val rewardItem = NexoItems.itemFromId(rewardId)?.build() ?: return
                farmVillageManager.openTradeWorkbenchGUI(player, rewardItem, requiredAmount, requiredItemName, silverStarIds)
            }
            sprinklerGuiTitle -> {
                event.isCancelled = true
                val exchangeInfo = sprinklerExchangeMap[event.rawSlot] ?: return
                val (rewardId, requiredAmount, requiredItemName) = exchangeInfo
                val rewardItem = NexoItems.itemFromId(rewardId)?.build() ?: return
                farmVillageManager.openTradeWorkbenchGUI(player, rewardItem, requiredAmount, requiredItemName, goldenStarIds)
            }
            farmingSupplySelectionGuiTitle -> {
                event.isCancelled = true
                val exchangeInfo = farmingSupplyExchangeMap[event.rawSlot] ?: return
                val (rewardId, requiredAmount, requiredItemName) = exchangeInfo
                val rewardItem = NexoItems.itemFromId(rewardId)?.build() ?: return
                farmVillageManager.openTradeWorkbenchGUI(player, rewardItem, requiredAmount, requiredItemName, farmingSupplyMaterialIds)
            }
        }
    }

    @EventHandler
    fun onInventoryClose(event: InventoryCloseEvent) {
        if (event.view.title() == mainGuiTitle) {
            val player = event.player as? Player ?: return
            activeAnimators.remove(player.uniqueId)?.cancel()
            animationIndexes.remove(player.uniqueId)
        }
    }
    
    private fun fillWithBlackGlass(inventory: Inventory) {
        val blackGlass = ItemStack(Material.BLACK_STAINED_GLASS_PANE).apply {
            itemMeta = itemMeta.apply { displayName(Component.text(" ").decoration(TextDecoration.ITALIC, false)) }
        }
        for (i in 0 until inventory.size) {
            inventory.setItem(i, blackGlass)
        }
    }
    
    private fun findItemsInInventory(player: Player, itemIds: Set<String>, amount: Int): List<ItemStack>? {
        val foundItems = mutableListOf<ItemStack>()
        var collectedAmount = 0

        for (item in player.inventory.storageContents) {
            if (collectedAmount >= amount) break
            
            if (item != null) {
                val currentItemId = if (item.type.isItem) NexoItems.idFromItem(item) ?: item.type.name else null
                if (currentItemId != null && currentItemId in itemIds) {
                    val needed = amount - collectedAmount
                    val itemToTake = item.clone()
                    if (item.amount > needed) {
                        itemToTake.amount = needed
                        collectedAmount += needed
                    } else {
                        itemToTake.amount = item.amount
                        collectedAmount += item.amount
                    }
                    foundItems.add(itemToTake)
                }
            }
        }

        return if (collectedAmount >= amount) foundItems else null
    }

    private fun removePlayerItems(player: Player, nexoId: String, amount: Int) {
        var remaining = amount
        player.inventory.storageContents.forEach { item ->
            if (remaining > 0 && item != null && NexoItems.idFromItem(item) == nexoId) {
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
} 