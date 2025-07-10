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

class ExchangeMerchantGUI(private val plugin: Main) : Listener {

    private val mainGuiTitle = Component.text("교환 상인")
    private val silverGuiTitle = Component.text("교환 상인 - 은별작물 교환")
    private val goldenGuiTitle = Component.text("교환 상인 - 금별작물 교환")

    private val activeAnimators = mutableMapOf<UUID, BukkitTask>()
    private val animationIndexes = mutableMapOf<UUID, Triple<Int, Int, Int>>()

    private val silverStarItems = listOf("cabbage_silver_star", "chinese_cabbage_silver_star", "garlic_silver_star", "corn_silver_star", "pineapple_silver_star", "eggplant_silver_star")
    private val goldenStarItems = listOf("cabbage_golden_star", "chinese_cabbage_golden_star", "garlic_golden_star", "corn_golden_star", "pineapple_golden_star", "eggplant_golden_star")
    private val scrollItems = listOf("h_sword_scroll", "c_sword_scroll", "v_sword_scroll")

    private val silverExchangeMap = mapOf(
        11 to ("cabbage" to "cabbage_silver_star"),
        13 to ("chinese_cabbage" to "chinese_cabbage_silver_star"),
        15 to ("garlic" to "garlic_silver_star"),
        29 to ("corn" to "corn_silver_star"),
        31 to ("pineapple" to "pineapple_silver_star"),
        33 to ("eggplant" to "eggplant_silver_star")
    )
    
    private val goldenExchangeMap = mapOf(
        11 to ("cabbage_silver_star" to "cabbage_golden_star"),
        13 to ("chinese_cabbage_silver_star" to "chinese_cabbage_golden_star"),
        15 to ("garlic_silver_star" to "garlic_golden_star"),
        29 to ("corn_silver_star" to "corn_golden_star"),
        31 to ("pineapple_silver_star" to "pineapple_golden_star"),
        33 to ("eggplant_silver_star" to "eggplant_golden_star")
    )

    fun openMainGui(player: Player) {
        val inventory = Bukkit.createInventory(player, 45, mainGuiTitle)
        fillWithBlackGlass(inventory)
        player.openInventory(inventory)
        startAnimation(player, inventory)
    }

    private fun openSilverGui(player: Player) {
        val inventory = Bukkit.createInventory(player, 45, silverGuiTitle)
        fillWithBlackGlass(inventory)
        
        silverExchangeMap.forEach { (slot, exchangePair) ->
            val requiredItem = NexoItems.itemFromId(exchangePair.first)?.build() ?: return@forEach
            val rewardItem = NexoItems.itemFromId(exchangePair.second)?.build() ?: return@forEach

            rewardItem.editMeta { meta ->
                val lore = mutableListOf<Component>()
                lore.add(Component.text(" "))
                val requiredDisplayName = requiredItem.displayName().decoration(TextDecoration.ITALIC, false)
                val rewardDisplayName = rewardItem.displayName().decoration(TextDecoration.ITALIC, false)
                
                lore.add(Component.text("교환 재료: ", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false).append(requiredDisplayName.color(NamedTextColor.WHITE)).append(Component.text(" 128개")))
                lore.add(Component.text("지급 아이템: ", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false).append(rewardDisplayName.color(NamedTextColor.AQUA)))
                lore.add(Component.text(" "))
                lore.add(Component.text("[클릭하여 교환]", NamedTextColor.GREEN, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false))
                meta.lore(lore)
            }
            inventory.setItem(slot, rewardItem)
        }
        player.openInventory(inventory)
    }

    private fun openGoldenGui(player: Player) {
        val inventory = Bukkit.createInventory(player, 45, goldenGuiTitle)
        fillWithBlackGlass(inventory)
        
        goldenExchangeMap.forEach { (slot, exchangePair) ->
            val requiredItem = NexoItems.itemFromId(exchangePair.first)?.build() ?: return@forEach
            val rewardItem = NexoItems.itemFromId(exchangePair.second)?.build() ?: return@forEach

            rewardItem.editMeta { meta ->
                val lore = mutableListOf<Component>()
                lore.add(Component.text(" "))
                val requiredDisplayName = requiredItem.displayName().decoration(TextDecoration.ITALIC, false)
                val rewardDisplayName = rewardItem.displayName().decoration(TextDecoration.ITALIC, false)

                lore.add(Component.text("교환 재료: ", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false).append(requiredDisplayName.color(NamedTextColor.WHITE)).append(Component.text(" 64개")))
                lore.add(Component.text("지급 아이템: ", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false).append(rewardDisplayName.color(NamedTextColor.GOLD)))
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
            val (silverIndex, goldenIndex, scrollIndex) = animationIndexes[player.uniqueId] ?: return@Runnable

            inventory.setItem(20, createAnimatedItem(silverStarItems[silverIndex], "은별 작물 교환", "작물을 은별 작물로 교환할 수 있습니다."))
            inventory.setItem(22, createAnimatedItem(goldenStarItems[goldenIndex], "금별 작물 교환", "은별 작물을 금별 작물로 교환할 수 있습니다."))
            inventory.setItem(24, createAnimatedItem(scrollItems[scrollIndex], "커스텀 아이템 교환권 교환", "금별 작물을 커스텀 아이템 스크롤로 교환할 수 있습니다."))

            animationIndexes[player.uniqueId] = Triple(
                (silverIndex + 1) % silverStarItems.size,
                (goldenIndex + 1) % goldenStarItems.size,
                (scrollIndex + 1) % scrollItems.size
            )
        }, 0L, 40L) // 0 tick delay, 40 tick (2 second) period
    }
    
    private fun createAnimatedItem(nexoId: String, name: String, description: String): ItemStack? {
        val item = NexoItems.itemFromId(nexoId)?.build() ?: return null
        item.editMeta { meta ->
            val displayName = Component.text(name, NamedTextColor.WHITE, TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false)
            val lore = listOf(
                Component.text(description, NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false)
            )
            meta.displayName(displayName)
            meta.lore(lore)
        }
        return item
    }

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val clickedTitle = event.view.title()

        when (clickedTitle) {
            mainGuiTitle -> {
                event.isCancelled = true
                when (event.rawSlot) {
                    20 -> openSilverGui(player)
                    22 -> openGoldenGui(player)
                    24 -> {
                        player.sendMessage(Component.text("구현 준비중입니다.", NamedTextColor.YELLOW))
                        // TODO: 교환상점 - 커스텀 아이템 교환 GUI 구현해야함
                    }
                }
            }
            silverGuiTitle -> {
                event.isCancelled = true
                val exchangeInfo = silverExchangeMap[event.rawSlot] ?: return
                val (requiredId, rewardId) = exchangeInfo
                val requiredAmount = 128

                val rewardItem = NexoItems.itemFromId(rewardId)?.build() ?: return

                if (!playerHasItems(player, requiredId, requiredAmount)) {
                    player.sendMessage(Component.text("교환에 필요한 작물이 부족합니다.", NamedTextColor.RED))
                    return
                }

                if (!hasEnoughSpace(player, rewardItem)) {
                    player.sendMessage(Component.text("은별 작물을 받을 인벤토리 공간이 부족합니다.", NamedTextColor.RED))
                    return
                }

                removePlayerItems(player, requiredId, requiredAmount)
                player.inventory.addItem(rewardItem)
                player.sendMessage(Component.text("성공적으로 은별 작물로 교환했습니다!", NamedTextColor.GREEN))
            }
            goldenGuiTitle -> {
                event.isCancelled = true
                val exchangeInfo = goldenExchangeMap[event.rawSlot] ?: return
                val (requiredId, rewardId) = exchangeInfo
                val requiredAmount = 64

                val rewardItem = NexoItems.itemFromId(rewardId)?.build() ?: return
                
                if (!playerHasItems(player, requiredId, requiredAmount)) {
                    player.sendMessage(Component.text("교환에 필요한 은별 작물이 부족합니다.", NamedTextColor.RED))
                    return
                }

                if (!hasEnoughSpace(player, rewardItem)) {
                    player.sendMessage(Component.text("금별 작물을 받을 인벤토리 공간이 부족합니다.", NamedTextColor.RED))
                    return
                }

                removePlayerItems(player, requiredId, requiredAmount)
                player.inventory.addItem(rewardItem)
                player.sendMessage(Component.text("성공적으로 금별 작물로 교환했습니다!", NamedTextColor.GREEN))
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

    private fun hasEnoughSpace(player: Player, itemToAdd: ItemStack): Boolean {
        // Create a temporary inventory that mimics the player's inventory to safely simulate adding an item.
        val tempInventory = Bukkit.createInventory(null, 36)
        for (i in 0..35) {
            val item = player.inventory.storageContents[i]
            if (item != null) {
                tempInventory.setItem(i, item.clone())
            }
        }
        
        // If the map of remaining items is empty, it means everything could be added.
        return tempInventory.addItem(itemToAdd.clone()).isEmpty()
    }

    private fun fillWithBlackGlass(inventory: Inventory) {
        val blackGlass = ItemStack(Material.BLACK_STAINED_GLASS_PANE).apply {
            itemMeta = itemMeta.apply { displayName(Component.text(" ").decoration(TextDecoration.ITALIC, false)) }
        }
        for (i in 0 until inventory.size) {
            inventory.setItem(i, blackGlass)
        }
    }

    private fun playerHasItems(player: Player, nexoId: String, amount: Int): Boolean {
        var count = 0
        player.inventory.storageContents.forEach { item ->
            if (item != null && NexoItems.idFromItem(item) == nexoId) {
                count += item.amount
            }
        }
        return count >= amount
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
} 